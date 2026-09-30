package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.options.ConfigInteger;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jetbrains.annotations.Nullable;

public class CloudStoreUtils {
   private static final String CLOUD_STORE_CLASS = "com.cloudstore.client.CloudStoreClient";
   private static final String REQUEST_CLASS = "com.cloudstore.client.api.CloudStoreModels$WithdrawRequest";
   private static final String ITEM_CLASS = "com.cloudstore.client.api.CloudStoreModels$Item";
   private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
      Thread thread = new Thread(r, "cloud-store-refill");
      thread.setDaemon(true);
      return thread;
   });
   private static long pendingUntilMillis = 0L;
   private static final Set<Item> orderedItems = new HashSet<>();
   private static long autoOrderSubmittedAt = 0L;
   private static long orderToken = 0L;
   private static final Set<Item> manualOrdered = new HashSet<>();
   private static final long REFILL_COOLDOWN_MS = 30000L;
   private static final long REQUEST_TIMEOUT_MS = 10000L;

   private CloudStoreUtils() {
   }

   public static synchronized boolean isRefillInCooldown() {
      return System.currentTimeMillis() < pendingUntilMillis;
   }

   public static boolean handleScrollAmount(double yOffset) {
      if (yOffset == 0.0) {
         return false;
      } else {
         Minecraft client = Minecraft.getInstance();
         if (client.gui.screen() != null || client.player == null) {
            return false;
         } else if (!ModUtils.isCloudStoreLoaded()) {
            return false;
         } else if (!Configs.Hotkeys.REFILL_AMOUNT_ADJUST.getKeybind().isKeybindHeld()) {
            return false;
         } else {
            int delta = yOffset > 0.0 ? 1 : -1;
            if (Configs.Special.REFILL_SCROLL_REVERSE.getBooleanValue()) {
               delta = -delta;
            }

            ConfigInteger config = Configs.Special.PRINT_CLOUD_STORE_REFILL_AMOUNT;
            int value = Math.max(config.getMinIntegerValue(), Math.min(config.getMaxIntegerValue(), config.getIntegerValue() + delta));
            config.setIntegerValue(value);
            MessageUtils.setOverlayMessage("[打印机] 云仓库单次取货数量: " + value);
            return true;
         }
      }
   }

   public static void showRefillAmountOverlay() {
      MessageUtils.setOverlayMessage("[打印机] 云仓库单次取货数量: " + Configs.Special.PRINT_CLOUD_STORE_REFILL_AMOUNT.getIntegerValue());
   }

   public static boolean tryRequestRefillMany(LocalPlayer player, Collection<Item> missingItems, int amount) {
      if (player != null && missingItems != null && !missingItems.isEmpty() && amount > 0) {
         List<Item> snapshot;
         long token;
         synchronized (CloudStoreUtils.class) {
            if (isRefillInCooldown()) {
               return false;
            }

            snapshot = new ArrayList<>(missingItems);
            orderedItems.addAll(snapshot);
            autoOrderSubmittedAt = System.currentTimeMillis();
            token = ++orderToken;
            pendingUntilMillis = System.currentTimeMillis() + 30000L;
         }

         String playerName = player.getName().getString();
         EXECUTOR.execute(() -> runRefill(snapshot, playerName, amount, false, token));
         MessageUtils.setOverlayMessage("[打印机] 已提交补货请求：" + snapshot.size() + " 种材料，每种 x" + amount);
         return true;
      } else {
         return false;
      }
   }

   public static boolean tryRequestRefillImmediate(LocalPlayer player, Item item, int amount) {
      if (player != null && item != null && item != Items.AIR && amount > 0) {
         synchronized (CloudStoreUtils.class) {
            manualOrdered.add(item);
         }

         List<Item> snapshot = new ArrayList<>();
         snapshot.add(item);
         String playerName = player.getName().getString();
         EXECUTOR.execute(() -> runRefill(snapshot, playerName, amount, true, 0L));
         MessageUtils.setOverlayMessage("[打印机] 已提交取货请求：" + item.getName(ItemStack.EMPTY).getString() + " x" + amount);
         return true;
      } else {
         return false;
      }
   }

   public static synchronized void tickArrivalCheck(@Nullable LocalPlayer player) {
      if (player != null) {
         boolean arrived = false;
         Iterator<Item> manualIterator = manualOrdered.iterator();

         while (manualIterator.hasNext()) {
            Item item = manualIterator.next();
            if (InventoryUtils.countMatchingMainInventory(player, stack -> stack.is(item)) > 0) {
               manualIterator.remove();
               arrived = true;
            }
         }

         Iterator<Item> iterator = orderedItems.iterator();

         while (iterator.hasNext()) {
            Item item = iterator.next();
            if (InventoryUtils.countMatchingMainInventory(player, stack -> stack.is(item)) > 0) {
               iterator.remove();
               arrived = true;
            }
         }

         if (!orderedItems.isEmpty() && autoOrderSubmittedAt > 0L && System.currentTimeMillis() - autoOrderSubmittedAt > getOrderStaleThresholdMs()) {
            orderedItems.clear();
         }

         if (arrived) {
            if (isRefillInCooldown()) {
               pendingUntilMillis = 0L;
            }

            if (orderedItems.isEmpty()) {
               MessageUtils.setOverlayMessage("[打印机] 已收到云仓库材料，补货冷却结束");
            } else {
               MessageUtils.setOverlayMessage("[打印机] 已收到部分云仓库材料");
            }
         }
      }
   }

   private static long getOrderStaleThresholdMs() {
      long threshold = 30000L;

      try {
         threshold = Math.max(threshold, (long)Configs.Special.PRINT_CLOUD_STORE_REFILL_COOLDOWN.getIntegerValue() * 1000L);
      } catch (Throwable var3) {
      }

      return threshold + 60000L;
   }

   private static void runRefill(List<Item> missingItems, String playerName, int amount, boolean manual, long token) {
      String message = null;
      boolean success = false;

      try {
         Object api = getApi();
         if (api == null) {
            message = "云仓库未加载，无法自动补充材料";
            return;
         }

         String ownerUuid = null;
         String sessionCookie = readPrivateField(api, "sessionCookie");
         if (sessionCookie != null && !sessionCookie.isBlank()) {
            Object cachedMe = callCacheMethod("me", getCacheEndpoint());
            if (cachedMe != null) {
               ownerUuid = readField(cachedMe, "defaultOwnerUuid");
            }
         }

         if (ownerUuid == null || ownerUuid.isBlank()) {
            Object me = await(api, "connect");
            ownerUuid = readField(me, "defaultOwnerUuid");
         }

         if (ownerUuid == null || ownerUuid.isBlank()) {
            message = "云仓库未登录或没有可用仓库";
            return;
         }

         Object cachedInventory = callCacheMethod("inventory", getCacheEndpoint(), ownerUuid);
         List<?> cachedItems = cachedInventory == null ? null : readList(cachedInventory, "items");
         if (cachedItems == null) {
            message = "云仓库库存缓存为空，请先打开云仓库界面刷新缓存";
            return;
         }

         List<Object> requests = new ArrayList<>();
         List<String> missingIds = new ArrayList<>();

         for (Item missing : missingItems) {
            String missingId = BuiltInRegistries.ITEM.getKey(missing).toString();
            missingIds.add(missingId);
            Object matched = findItem(cachedItems, missingId);
            if (matched != null) {
               requests.add(newWithdrawRequest(matched, amount));
            }
         }

         if (!requests.isEmpty()) {
            if (requests.size() < missingItems.size()) {
               message = "部分材料云仓库缺货，仅提交有货的 " + requests.size() + " 种";
            }

            Object response = withdrawMany(api, ownerUuid, requests, playerName);
            Boolean ok = readBoolean(response, "ok");
            if (Boolean.TRUE.equals(ok)) {
               message = message == null ? "已从云仓库申请取货 " + requests.size() + " 种材料，每种 x" + amount : message + "，每种 x" + amount;
               success = true;
            } else {
               message = (message == null ? "云仓库取货失败" : message + "失败") + "：" + readField(response, "error");
            }

            return;
         }

         message = "云仓库中没有这些材料：" + String.join("、", missingIds);
      } catch (Throwable var49) {
         message = "云仓库取货失败：" + safeMessage(var49);
         return;
      } finally {
         String finalMessage = message;
         if (finalMessage != null) {
            Minecraft.getInstance().execute(() -> MessageUtils.setOverlayMessage("[打印机] " + finalMessage));
         }

         if (manual) {
            if (!success) {
               synchronized (CloudStoreUtils.class) {
                  manualOrdered.removeAll(missingItems);
               }
            }

            return;
         }

         long cooldown;
         if (success) {
            cooldown = (long)Configs.Special.PRINT_CLOUD_STORE_REFILL_COOLDOWN.getIntegerValue() * 1000L;
         } else {
            cooldown = 30000L;
         }

         synchronized (CloudStoreUtils.class) {
            if (orderToken == token && (!success || !orderedItems.isEmpty())) {
               pendingUntilMillis = System.currentTimeMillis() + cooldown;
            }
         }
      }
   }

   @Nullable
   private static Object findItem(List<?> items, String itemId) {
      if (items == null) {
         return null;
      } else {
         for (Object entry : items) {
            if (entry != null
               && itemId.equals(readField(entry, "itemId"))
               && readLong(entry, "amount") + readLong(entry, "boxedAmount") > 0L
               && matchesShulkerExpectation(entry, itemId)) {
               return entry;
            }
         }

         for (Object entryx : items) {
            if (entryx != null) {
               Object inner = findItem(readList(entryx, "shulkerContents"), itemId);
               if (inner != null) {
                  return inner;
               }
            }
         }

         return null;
      }
   }

   private static boolean matchesShulkerExpectation(Object entry, String itemId) {
      if (!isShulkerId(itemId)) {
         return true;
      } else {
         List<?> contents = readList(entry, "shulkerContents");
         return readLong(entry, "boxedAmount") == 0L && (contents == null || contents.isEmpty());
      }
   }

   private static boolean isShulkerId(String itemId) {
      try {
         Item item = (Item)BuiltInRegistries.ITEM.getValue(Identifier.parse(itemId));
         return item != null && item != Items.AIR && Block.byItem(item) instanceof ShulkerBoxBlock;
      } catch (Throwable var2) {
         return false;
      }
   }

   private static Object newWithdrawRequest(Object matchedItem, int amount) throws Exception {
      Class<?> itemClass = Class.forName("com.cloudstore.client.api.CloudStoreModels$Item");
      Class<?> requestClass = Class.forName("com.cloudstore.client.api.CloudStoreModels$WithdrawRequest");
      Constructor<?> constructor = requestClass.getConstructor(itemClass, long.class);
      return constructor.newInstance(matchedItem, (long)amount);
   }

   private static Object withdrawMany(Object api, String ownerUuid, List<?> requests, String playerName) throws Exception {
      Method method = null;

      for (Method candidate : api.getClass().getMethods()) {
         if (candidate.getName().equals("withdrawMany") && candidate.getParameterCount() == 3) {
            method = candidate;
            break;
         }
      }

      if (method == null) {
         throw new IllegalStateException("cloud-store 方法 withdrawMany 不存在");
      } else if (method.invoke(api, ownerUuid, requests, playerName) instanceof CompletableFuture<?> completableFuture) {
         return completableFuture.get(10000L, TimeUnit.MILLISECONDS);
      } else {
         throw new IllegalStateException("cloud-store 方法 withdrawMany 未返回异步结果");
      }
   }

   @Nullable
   private static Object getApi() throws ReflectiveOperationException {
      Class<?> clazz = Class.forName("com.cloudstore.client.CloudStoreClient");
      Field field = clazz.getField("api");
      return field.get(null);
   }

   private static String getCacheEndpoint() {
      try {
         Class<?> clazz = Class.forName("com.cloudstore.client.CloudStoreClient");
         Object config = clazz.getField("config").get(null);
         String value = readField(config, "apiBaseUrl");
         if (value != null && !value.isBlank()) {
            String result = value.trim();
            if (!result.startsWith("http://") && !result.startsWith("https://")) {
               result = "http://" + result;
            }

            while (result.endsWith("/")) {
               result = result.substring(0, result.length() - 1);
            }

            return result;
         } else {
            return "http://127.0.0.1:8787";
         }
      } catch (Throwable var4) {
         return "http://127.0.0.1:8787";
      }
   }

   @Nullable
   private static Object callCacheMethod(String methodName, Object... args) {
      try {
         Class<?> clazz = Class.forName("com.cloudstore.client.CloudStoreClient");
         Object cache = clazz.getField("cache").get(null);
         if (cache == null) {
            return null;
         } else {
            Method method = null;

            for (Method candidate : cache.getClass().getMethods()) {
               if (candidate.getName().equals(methodName) && candidate.getParameterCount() == args.length) {
                  method = candidate;
                  break;
               }
            }

            if (method == null) {
               return null;
            } else {
               Object result = method.invoke(cache, args);
               return result instanceof List ? (List)result : result;
            }
         }
      } catch (Throwable var9) {
         return null;
      }
   }

   @Nullable
   private static String readPrivateField(Object target, String name) {
      try {
         Field field = target.getClass().getDeclaredField(name);
         field.setAccessible(true);
         Object value = field.get(target);
         return value == null ? null : String.valueOf(value);
      } catch (ReflectiveOperationException var4) {
         return null;
      }
   }

   private static Object await(Object api, String methodName, Object... args) throws Exception {
      Method method = null;

      for (Method candidate : api.getClass().getMethods()) {
         if (candidate.getName().equals(methodName) && candidate.getParameterCount() == args.length) {
            method = candidate;
            break;
         }
      }

      if (method == null) {
         throw new IllegalStateException("cloud-store 方法 " + methodName + " 不存在");
      } else if (method.invoke(api, args) instanceof CompletableFuture<?> completableFuture) {
         return completableFuture.get(10000L, TimeUnit.MILLISECONDS);
      } else {
         throw new IllegalStateException("cloud-store 方法 " + methodName + " 未返回异步结果");
      }
   }

   @Nullable
   private static List<?> readList(Object target, String name) {
      try {
         Field field = target.getClass().getField(name);
         return field.get(target) instanceof List<?> list ? list : null;
      } catch (ReflectiveOperationException var5) {
         return null;
      }
   }

   @Nullable
   private static String readField(Object target, String name) {
      try {
         Field field = target.getClass().getField(name);
         Object value = field.get(target);
         return value == null ? null : String.valueOf(value);
      } catch (ReflectiveOperationException var4) {
         return null;
      }
   }

   private static long readLong(Object target, String name) {
      try {
         Field field = target.getClass().getField(name);
         return field.get(target) instanceof Number number ? number.longValue() : 0L;
      } catch (ReflectiveOperationException var5) {
         return 0L;
      }
   }

   @Nullable
   private static Boolean readBoolean(Object target, String name) {
      try {
         Field field = target.getClass().getField(name);
         return field.get(target) instanceof Boolean bool ? bool : null;
      } catch (ReflectiveOperationException var5) {
         return null;
      }
   }

   private static String safeMessage(Throwable error) {
      Throwable current = error;

      while (current.getCause() != null && current.getCause() != current) {
         current = current.getCause();
      }

      String message = current.getMessage();
      return message != null && !message.isBlank() ? message : current.getClass().getSimpleName();
   }
}
