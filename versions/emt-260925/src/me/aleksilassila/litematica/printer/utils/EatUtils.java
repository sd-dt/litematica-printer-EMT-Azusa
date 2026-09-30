package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.EatMode;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.printer.zxy.utils.ZxyUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

public final class EatUtils {
   private static final int EAT_HOTBAR_SLOT = 8;
   private static final long SHULKER_RETRY_TICKS = 100L;
   private static final long SCAN_RETRY_TICKS = 10L;
   private static final int START_MAX_RETRIES = 5;
   private static final int RESUME_MAX_RETRIES = 40;
   private static final Minecraft client = Minecraft.getInstance();
   private static EatUtils.State state = EatUtils.State.IDLE;
   private static long nextActionTick;
   private static long nextScanTick;
   private static long shulkerRetryTick;
   private static long fetchDeadlineTick;
   private static InteractionHand eatHand = InteractionHand.MAIN_HAND;
   private static int originalSelectedSlot = -1;
   private static int foodHotbarSlot = -1;
   private static int swappedInvSlot = -1;
   private static int preEatStackCount;
   private static Item preEatItem;
   private static int preEatHunger;
   private static float lastHealth;
   private static float lastAbsorption;
   private static boolean usingStarted;
   private static boolean keyUseWasDown;
   private static int startRetries;
   private static int resumeRetries;

   private EatUtils() {
   }

   public static boolean isBusy() {
      return state != EatUtils.State.IDLE;
   }

   public static void tick(Minecraft mc) {
      LocalPlayer player = mc.player;
      if (player != null && mc.level != null && mc.gameMode != null) {
         switch (state) {
            case IDLE:
               tryStart(mc, player);
               break;
            case FETCH:
               tickFetch(player);
               break;
            case EATING:
               tickEating(mc, player);
               break;
            case RESTORE:
               restore(player);
         }
      } else {
         if (state != EatUtils.State.IDLE) {
            hardReset();
         }
      }
   }

   private static void tryStart(Minecraft mc, LocalPlayer player) {
      EatMode eatMode = (EatMode)Configs.Special.EAT.getOptionListValue();
      if (eatMode != EatMode.OFF) {
         if (eatMode != EatMode.PRINTER_ONLY || ConfigUtils.isPrinterEnable()) {
            if (screenAllowsAutoEat(mc.gui.screen()) && player.isAlive() && player.containerMenu.equals(player.inventoryMenu)) {
               GameType mode = mc.gameMode.getPlayerMode();
               if (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE) {
                  long now = ClientPlayerTickManager.getCurrentHandlerTime();
                  if (now >= nextActionTick && now >= nextScanTick) {
                     if (player.getFoodData().getFoodLevel() <= Configs.Special.EAT_HUNGER_THRESHOLD.getIntegerValue()) {
                        if (!player.isUsingItem() && !mc.options.keyAttack.isDown() && !mc.options.keyUse.isDown()) {
                           if (!me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.isOpenHandler
                              && me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.lastNeedItemList.isEmpty()
                              && !BreakUtils.INSTANCE.isNeedHandle()
                              && ZxyUtils.num == 0) {
                              if (foodScore(InventoryUtils.getOffhandStack(player)) > 0.0) {
                                 beginEating(player, -1, -1, InteractionHand.OFF_HAND);
                              } else {
                                 int hotbar = findBestFood(player, 0, 9);
                                 if (hotbar >= 0) {
                                    beginEating(player, hotbar, -1, InteractionHand.MAIN_HAND);
                                 } else {
                                    int backpack = findBestFood(player, 9, 36);
                                    if (backpack >= 0) {
                                       swapWithHotbarEnd(player, backpack);
                                       beginEating(player, 8, backpack, InteractionHand.MAIN_HAND);
                                    } else {
                                       if (now >= shulkerRetryTick) {
                                          shulkerRetryTick = now + 100L;
                                          if (Configs.Core.QUICK_SHULKER.getBooleanValue()) {
                                             Item food = findBestFoodInShulkers(player);
                                             if (food != null) {
                                                me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(food);
                                                fetchDeadlineTick = now + 100L;
                                                state = EatUtils.State.FETCH;
                                                MessageUtils.setOverlayMessage(Component.literal("§e从快捷潜影盒取食物…"));
                                                return;
                                             }
                                          }
                                       }

                                       nextScanTick = now + 10L;
                                    }
                                 }
                              }
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void tickFetch(LocalPlayer player) {
      boolean finished = !me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.isOpenHandler
         && me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.lastNeedItemList.isEmpty();
      if (finished || ClientPlayerTickManager.getCurrentHandlerTime() >= fetchDeadlineTick) {
         state = EatUtils.State.IDLE;
      }
   }

   private static void beginEating(LocalPlayer player, int hotbarSlot, int swappedFrom, InteractionHand hand) {
      eatHand = hand;
      originalSelectedSlot = hand == InteractionHand.MAIN_HAND ? InventoryUtils.getSelectedSlot(player.getInventory()) : -1;
      foodHotbarSlot = hand == InteractionHand.MAIN_HAND ? hotbarSlot : -1;
      swappedInvSlot = hand == InteractionHand.MAIN_HAND ? swappedFrom : -1;
      preEatStackCount = player.getItemInHand(hand).getCount();
      preEatItem = player.getItemInHand(hand).getItem();
      preEatHunger = player.getFoodData().getFoodLevel();
      lastHealth = player.getHealth();
      lastAbsorption = player.getAbsorptionAmount();
      keyUseWasDown = client.options.keyUse.isDown();
      usingStarted = false;
      startRetries = 0;
      resumeRetries = 0;
      if (hand == InteractionHand.MAIN_HAND) {
         InventoryUtils.setHotbarSlot(hotbarSlot, player.getInventory());
      }

      state = EatUtils.State.EATING;
      MessageUtils.setOverlayMessage(Component.literal("§f正在吃: §a" + player.getItemInHand(hand).getHoverName().getString()));
   }

   private static void tickEating(Minecraft mc, LocalPlayer player) {
      long hurtCooldownSeconds = (long)Configs.Special.EAT_HURT_CANCEL_COOLDOWN.getIntegerValue();
      boolean hurt = hurtCooldownSeconds > 0L && player.getHealth() + player.getAbsorptionAmount() < lastHealth + lastAbsorption - 1.0E-5F;
      lastHealth = player.getHealth();
      lastAbsorption = player.getAbsorptionAmount();
      boolean playerActed = !screenAllowsAutoEat(mc.gui.screen())
         || mc.options.keyAttack.isDown()
         || !player.containerMenu.equals(player.inventoryMenu)
         || eatHand == InteractionHand.MAIN_HAND && InventoryUtils.getSelectedSlot(player.getInventory()) != foodHotbarSlot;
      if (!hurt && !playerActed) {
         if (!player.isUsingItem()) {
            if (!usingStarted) {
               client.gameMode.useItem(player, eatHand);
               if (player.isUsingItem()) {
                  usingStarted = true;
                  client.options.keyUse.setDown(true);
               } else if (++startRetries >= 5) {
                  cancel(player, 60L);
               }
            } else if (foodConsumed(player)) {
               restore(player);
            } else if (++resumeRetries <= 40) {
               client.gameMode.useItem(player, eatHand);
               client.options.keyUse.setDown(true);
            } else {
               cancel(player, 60L);
            }
         } else {
            usingStarted = true;
            client.options.keyUse.setDown(true);
         }
      } else {
         cancel(player, hurt ? hurtCooldownSeconds * 20L : 20L);
         if (hurt) {
            MessageUtils.setOverlayMessage(Component.literal("§c受伤打断，" + hurtCooldownSeconds + " 秒冷却"));
         }
      }
   }

   private static void cancel(LocalPlayer player, long cooldownTicks) {
      client.options.keyUse.setDown(keyUseWasDown);
      if (player.isUsingItem()) {
         player.releaseUsingItem();
      }

      nextActionTick = ClientPlayerTickManager.getCurrentHandlerTime() + cooldownTicks;
      state = EatUtils.State.RESTORE;
   }

   private static void restore(LocalPlayer player) {
      client.options.keyUse.setDown(keyUseWasDown);
      if (player.isUsingItem()) {
         player.releaseUsingItem();
      }

      if (swappedInvSlot >= 0) {
         swapWithHotbarEnd(player, swappedInvSlot);
         swappedInvSlot = -1;
      }

      if (originalSelectedSlot >= 0) {
         InventoryUtils.setHotbarSlot(originalSelectedSlot, player.getInventory());
      }

      originalSelectedSlot = -1;
      foodHotbarSlot = -1;
      eatHand = InteractionHand.MAIN_HAND;
      state = EatUtils.State.IDLE;
   }

   private static void hardReset() {
      state = EatUtils.State.IDLE;
      client.options.keyUse.setDown(false);
      originalSelectedSlot = -1;
      foodHotbarSlot = -1;
      eatHand = InteractionHand.MAIN_HAND;
      swappedInvSlot = -1;
      usingStarted = false;
      startRetries = 0;
   }

   private static boolean screenAllowsAutoEat(Screen screen) {
      return screen == null || screen instanceof ChatScreen || screen instanceof PauseScreen;
   }

   private static boolean foodConsumed(LocalPlayer player) {
      ItemStack hand = player.getItemInHand(eatHand);
      return hand.getCount() < preEatStackCount || hand.getItem() != preEatItem || player.getFoodData().getFoodLevel() > preEatHunger;
   }

   private static int findBestFood(LocalPlayer player, int from, int to) {
      int best = -1;
      double bestScore = -1.0;

      for (int i = from; i < to; i++) {
         double score = foodScore(player.getInventory().getItem(i));
         if (score > bestScore) {
            bestScore = score;
            best = i;
         }
      }

      return best;
   }

   private static Item findBestFoodInShulkers(LocalPlayer player) {
      Item best = null;
      double bestScore = -1.0;

      for (int i = 9; i < 36; i++) {
         ItemStack box = player.getInventory().getItem(i);
         if (!box.isEmpty() && box.getCount() == 1 && BuiltInRegistries.ITEM.getKey(box.getItem()).toString().contains("shulker_box")) {
            for (ItemStack stack : fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(box, -1)) {
               double score = foodScore(stack);
               if (score > bestScore) {
                  bestScore = score;
                  best = stack.getItem();
               }
            }
         }
      }

      return best;
   }

   private static double foodScore(ItemStack stack) {
      if (stack.isEmpty()) {
         return -1.0;
      } else {
         FoodProperties food = (FoodProperties)stack.get(DataComponents.FOOD);
         return food != null && !isBlacklisted(stack) ? (double)food.nutrition() * 1000.0 + (double)food.saturation() : -1.0;
      }
   }

   private static boolean isBlacklisted(ItemStack stack) {
      Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
      String path = key.getPath();
      String id = key.toString();
      String name = stack.getHoverName().getString();

      for (String entry : Configs.Special.EAT_BLACKLIST.getStrings()) {
         String e = entry.trim();
         if (!e.isEmpty() && (e.equals(path) || e.equalsIgnoreCase(id) || e.equals(name))) {
            return true;
         }
      }

      return false;
   }

   private static void swapWithHotbarEnd(LocalPlayer player, int invSlot) {
      if (client.gameMode != null) {
         client.gameMode.handleContainerInput(player.inventoryMenu.containerId, invSlot, 8, ContainerInput.SWAP, player);
      }
   }

   private static enum State {
      IDLE,
      FETCH,
      EATING,
      RESTORE;
   }
}
