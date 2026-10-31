package me.aleksilassila.litematica.printer.printer.zxy.inventory;

import java.util.HashSet;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.EatUtils;
import me.aleksilassila.litematica.printer.utils.HandRestockShulkerCompat;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity.AnimationStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

public class InventoryUtils {
   private static final int AUTOMATED_QUICK_SHULKER_SCREEN_PROTECTION_TIMEOUT_TICKS = 40;
   private static final long QUICK_SHULKER_SEARCH_TIMEOUT_NANOS = 1000000000L;
   /**
    * 开盒后等服务器把容器界面发过来的超时（3 秒）：超时说明这次开盒没成功，必须复位，
    * 否则 isOpenHandler 会一直卡住（打印机停摆，而且之后玩家自己开的容器会被误当成"我们开的盒"去搬运）。
    */
   private static final long QUICK_SHULKER_OPEN_TIMEOUT_NANOS = 3000000000L;
   /**
    * 背包满（快捷潜影盒取不出东西）之后的退避 tick 数（5 秒）：不再反复开盒打断打印。
    */
   private static final int QUICK_SHULKER_FULL_BACKOFF_TICKS = 100;
   /**
    * 开盒点击没被服务端响应（盒子没开、容器也没来）时的重试间隔与次数。
    * 第一次点击可能被服务端插件/反作弊吞掉或赶上服务端卡顿，单次点击就判失败在实测里表现为「经常打开失败」。
    */
   private static final long QUICK_SHULKER_OPEN_RETRY_INTERVAL_NANOS = 500000000L;
   private static final int QUICK_SHULKER_OPEN_MAX_ATTEMPTS = 3;
   /**
    * 开盒失败后的退避 tick 数（2 秒）：短时间内别反复开盒，
    * 免得连续失败刷屏、每次都把打印打断 3 秒。
    */
   private static final int QUICK_SHULKER_OPEN_FAIL_BACKOFF_TICKS = 40;
   private static int shulkerCooldown = 0;
   private static int quickShulkerOpenAttempts;
   private static long quickShulkerNextAttemptNanos;
   private static int quickShulkerOpenFailBackoffTicks;
   private static long quickShulkerSearchDeadlineNanos;
   private static long quickShulkerOpenDeadlineNanos;
   private static int quickShulkerFullBackoffTicks;
   private static int automatedQuickShulkerScreenProtectionTimeout;
   private static boolean automatedQuickShulkerScreenProtection;
   private static boolean automatedQuickShulkerOpened;
   private static boolean preserveAutomatedQuickShulkerScreenOnClose;
   private static Screen protectedScreen;
   private static final Minecraft client = Minecraft.getInstance();
   public static HashSet<Item> lastNeedItemList = new HashSet<>();
   public static boolean isOpenHandler = false;
   static int shulkerBoxSlot = -1;
   private static int quickShulkerEmptySlots;
   private static boolean quickShulkerHasNonShulkerItem;

   public static boolean isInventory(Level world, BlockPos pos) {
      return fi.dy.masa.malilib.util.InventoryUtils.getInventory(world, pos) != null;
   }

   public static boolean canOpenInv(BlockPos pos) {
      if (client.level != null) {
         BlockState blockState = client.level.getBlockState(pos);
         BlockEntity blockEntity = client.level.getBlockEntity(pos);
         boolean isInventory = isInventory(client.level, pos);

         try {
            if (isInventory && blockState.getMenuProvider(client.level, pos) == null) {
               return false;
            } else {
               if (blockEntity instanceof ShulkerBoxBlockEntity entity
                  && !client.level
                     .noCollision(
                        Shulker.getProgressDeltaAabb(1.0F, (Direction)blockState.getValue(BlockStateProperties.FACING), 0.0F, 0.5F, Vec3.atBottomCenterOf(pos))
                           .move(pos)
                           .deflate(1.0E-6)
                     )
                  && entity.getAnimationStatus() == AnimationStatus.CLOSED) {
                  return false;
               }

               return isInventory;
            }
         } catch (Exception var5) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static void addQuickShulkerDemand(Item item) {
      if (lastNeedItemList.isEmpty() && Configs.Core.QUICK_SHULKER.getBooleanValue()) {
         quickShulkerSearchDeadlineNanos = System.nanoTime() + 1000000000L;
      }

      lastNeedItemList.add(item);
   }

   public static boolean shouldSuppressContainerScreen() {
      LocalPlayer player = client.player;
      if (!automatedQuickShulkerScreenProtection || player == null || !isOpenHandler) {
         return false;
      }

      // ★ 不能只靠 containerMenu 判断：打开容器的数据包到达时，菜单对象可能比屏幕晚一拍才挂到
      //   player 上，单看 containerMenu 会"有概率"漏判 —— 快捷潜影盒的容器界面真的弹出来，
      //   画面里就飘着「潜影盒」标题（用户反馈的"三个字飘在半空中"）。
      //   保护窗口（beginAutomatedQuickShulkerScreenProtection 后 40 tick）内一律按自动开盒处理。
      return automatedQuickShulkerScreenProtectionTimeout > 0 || !player.containerMenu.equals(player.inventoryMenu);
   }

   public static void beginAutomatedQuickShulkerScreenProtection() {
      automatedQuickShulkerScreenProtection = true;
      automatedQuickShulkerOpened = false;
      automatedQuickShulkerScreenProtectionTimeout = 40;
      preserveAutomatedQuickShulkerScreenOnClose = false;
      protectedScreen = client.gui.screen();
   }

   public static boolean shouldPreserveAutomatedQuickShulkerScreenOnClose(Screen screen) {
      if (automatedQuickShulkerScreenProtection && preserveAutomatedQuickShulkerScreenOnClose && screen == null) {
         preserveAutomatedQuickShulkerScreenOnClose = false;
         automatedQuickShulkerScreenProtection = false;
         automatedQuickShulkerScreenProtectionTimeout = 0;
         protectedScreen = null;
         return true;
      } else {
         return false;
      }
   }

   public static void closeAutomatedQuickShulkerContainer(LocalPlayer player) {
      if (!automatedQuickShulkerOpened) {
         clearAutomatedQuickShulkerScreenProtection();
      } else if (!automatedQuickShulkerScreenProtection) {
         player.closeContainer();
         automatedQuickShulkerOpened = false;
      } else {
         preserveAutomatedQuickShulkerScreenOnClose = protectedScreen != null;
         player.closeContainer();
         automatedQuickShulkerScreenProtection = false;
         automatedQuickShulkerOpened = false;
         automatedQuickShulkerScreenProtectionTimeout = 0;
         preserveAutomatedQuickShulkerScreenOnClose = false;
         protectedScreen = null;
      }
   }

   public static void clearAutomatedQuickShulkerScreenProtection() {
      automatedQuickShulkerScreenProtection = false;
      automatedQuickShulkerOpened = false;
      automatedQuickShulkerScreenProtectionTimeout = 0;
      preserveAutomatedQuickShulkerScreenOnClose = false;
      protectedScreen = null;
   }

   public static boolean switchItem() {
      // 背包满退避中就先别再开盒了 —— 每次开盒都会打断打印（tick 循环整段跳过），而背包没地方放，取出也白取。
      if (quickShulkerFullBackoffTicks > 0 || quickShulkerOpenFailBackoffTicks > 0) {
         return false;
      }

      if (!lastNeedItemList.isEmpty() && !isOpenHandler) {
         LocalPlayer player = client.player;
         if (!player.containerMenu.equals(player.inventoryMenu)) {
            return false;
         }

         if (Configs.Core.QUICK_SHULKER.getBooleanValue() && openShulker(lastNeedItemList)) {
            return true;
         }
      }

      return false;
   }

   public static void switchInv() {
      LocalPlayer player = Minecraft.getInstance().player;
      AbstractContainerMenu sc = player.containerMenu;
      if (!sc.equals(player.inventoryMenu)) {
         NonNullList<Slot> slots = sc.slots;
         int maxStacks = Configs.Core.QUICK_SHULKER_MAX_STACKS.getIntegerValue();
         int allowedStacks = quickShulkerEmptySlots > 0 ? Math.min(maxStacks, quickShulkerEmptySlots) : (quickShulkerHasNonShulkerItem ? 1 : 0);
         if (allowedStacks == 0) {
            MessageUtils.setOverlayMessage(I18n.INVENTORY_BACKPACK_FULL.getName());
            quickShulkerFullBackoffTicks = QUICK_SHULKER_FULL_BACKOFF_TICKS;
            finishQuickShulkerTransfer(player);
         } else {
            int movedStacks = 0;

            for (Item item : lastNeedItemList) {
               for (int y = 0; y < ((Slot)slots.get(0)).container.getContainerSize() && movedStacks < allowedStacks; y++) {
                  ItemStack source = ((Slot)slots.get(y)).getItem();
                  if (source.getItem().equals(item)) {
                     try {
                        if (quickShulkerEmptySlots > 0) {
                           client.gameMode.handleContainerInput(sc.containerId, y, 0, ContainerInput.QUICK_MOVE, player);
                           movedStacks++;
                        } else {
                           if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
                              break;
                           }

                           // 背包满：把盒里这一格与快捷栏某一格「交换」取出来（潜影盒不能套潜影盒，所以必须避开潜影盒槽）
                           int c = quickShulkerSwapTargetSlot(player);
                           if (c == -1) {
                              MessageUtils.setOverlayMessage(I18n.INVENTORY_BACKPACK_FULL.getName());
                              quickShulkerFullBackoffTicks = QUICK_SHULKER_FULL_BACKOFF_TICKS;
                              break;
                           } else {
                              fi.dy.masa.malilib.util.InventoryUtils.swapSlots(sc, y, c);
                              me.aleksilassila.litematica.printer.utils.InventoryUtils.setSelectedSlot(player.getInventory(), c);
                              movedStacks++;
                           }
                        }
                     } catch (Exception var11) {
                        System.out.println("切换物品异常");
                     }
                  }
               }
            }

            finishQuickShulkerTransfer(player);
         }
      }
   }

   private static int quickShulkerSwapTargetSlot(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int selected = me.aleksilassila.litematica.printer.utils.InventoryUtils.getSelectedSlot(inventory);
      if (isQuickShulkerSwapCandidate(inventory, selected)) {
         return selected;
      }

      for (int slot = 0; slot < 9; slot++) {
         if (slot != selected && isQuickShulkerSwapCandidate(inventory, slot)) {
            return slot;
         }
      }

      return -1;
   }


   private static boolean isQuickShulkerSwapCandidate(Inventory inventory, int hotbarSlot) {
      if (hotbarSlot < 0 || hotbarSlot > 8) {
         return false;
      }

      ItemStack stack = inventory.getItem(hotbarSlot);
      return !stack.isEmpty() && !BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("shulker_box");
   }


   private static void finishQuickShulkerTransfer(LocalPlayer player) {
      shulkerBoxSlot = -1;
      quickShulkerEmptySlots = 0;
      quickShulkerHasNonShulkerItem = false;
      lastNeedItemList = new HashSet<>();
      quickShulkerSearchDeadlineNanos = 0L;
      quickShulkerOpenDeadlineNanos = 0L;
      quickShulkerOpenAttempts = 0;
      quickShulkerNextAttemptNanos = 0L;
      isOpenHandler = false;
      HandRestockShulkerCompat.onQuickShulkerTransferFinished(player);
      AbstractContainerMenu currentMenu = player.containerMenu;
      if (!currentMenu.equals(player.inventoryMenu)) {
         closeAutomatedQuickShulkerContainer(player);
      } else {
         clearAutomatedQuickShulkerScreenProtection();
      }
   }

   private static void snapshotQuickShulkerInventory(Inventory inventory) {
      quickShulkerEmptySlots = 0;
      quickShulkerHasNonShulkerItem = false;

      for (int slot = 0; slot < Math.min(36, inventory.getContainerSize()); slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.isEmpty()) {
            quickShulkerEmptySlots++;
         } else if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("shulker_box")) {
            quickShulkerHasNonShulkerItem = true;
         }
      }
   }

   private static boolean openShulker(HashSet<Item> items) {
      if (shulkerCooldown > 0) {
         return false;
      }

      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null) {
         return false;
      }

      // 只在自己背包界面里开盒；光标上还挂着东西时点盒子会先把光标物品放下/换位，必须等光标清空
      if (!player.containerMenu.equals(player.inventoryMenu) || !player.containerMenu.getCarried().isEmpty()) {
         return false;
      }

      int slot = findShulkerSlotContaining(items);
      if (slot < 0) {
         return false;
      }

      try {
         quickShulkerOpenAttempts = 1;
         quickShulkerOpenDeadlineNanos = System.nanoTime() + QUICK_SHULKER_OPEN_TIMEOUT_NANOS;
         return sendOpenShulkerClick(player, player.inventoryMenu.slots.get(slot).getItem(), slot);
      } catch (Exception var9) {
         return false;
      }
   }

   /** 在背包（含快捷栏/副手）里找一盒装着所需物品的潜影盒；找不到返回 -1 */
   private static int findShulkerSlotContaining(HashSet<Item> items) {
      LocalPlayer player = client.player;
      if (player == null || player.inventoryMenu == null) {
         return -1;
      }

      AbstractContainerMenu menu = player.inventoryMenu;
      for (int i = 9; i < menu.slots.size(); i++) {
         ItemStack stack = menu.slots.get(i).getItem();
         if (!isQuickShulkerOpenTarget(stack)) {
            continue;
         }

         NonNullList<ItemStack> stored = fi.dy.masa.malilib.util.InventoryUtils.getStoredItems(stack, -1);
         if (stored.stream().anyMatch(s1 -> items.contains(s1.getItem()))) {
            return i;
         }
      }

      return -1;
   }

   private static boolean isQuickShulkerOpenTarget(ItemStack stack) {
      return !stack.isEmpty() && stack.getCount() == 1 && BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().contains("shulker_box");
   }

   /**
    * 发一次开盒点击（服务端「潜影盒右键开启」插件据此打开容器）。
    * 每次重发都要重新取当前槽里的盒子：上一次点击可能已经被服务端处理过（槽位内容会变）。
    */
   private static boolean sendOpenShulkerClick(LocalPlayer player, ItemStack stack, int slot) {
      shulkerBoxSlot = slot;
      snapshotQuickShulkerInventory(player.getInventory());
      BlockUtils.openShulker(stack, slot);
      automatedQuickShulkerOpened = true;
      isOpenHandler = true;
      shulkerCooldown = Configs.Core.QUICK_SHULKER_COOLDOWN.getIntegerValue();
      quickShulkerNextAttemptNanos = System.nanoTime() + QUICK_SHULKER_OPEN_RETRY_INTERVAL_NANOS;
      return true;
   }

   /**
    * 开盒点击后迟迟等不到容器：在超时前按固定间隔重发。只在「容器还没挂上来」时重试，
    * 否则点击会发到别的 containerId（服务端会直接丢弃，还可能误伤别的容器）。
    */
   private static void retryQuickShulkerOpenIfNeeded() {
      if (!isOpenHandler || quickShulkerOpenDeadlineNanos == 0L || lastNeedItemList.isEmpty()) {
         return;
      }

      if (quickShulkerOpenAttempts >= QUICK_SHULKER_OPEN_MAX_ATTEMPTS || System.nanoTime() < quickShulkerNextAttemptNanos) {
         return;
      }

      LocalPlayer player = client.player;
      if (player == null || client.gameMode == null) {
         return;
      }

      if (!player.containerMenu.equals(player.inventoryMenu) || !player.containerMenu.getCarried().isEmpty()) {
         return;
      }

      int slot = findShulkerSlotContaining(lastNeedItemList);
      if (slot < 0) {
         return;
      }

      quickShulkerOpenAttempts++;
      try {
         sendOpenShulkerClick(player, player.inventoryMenu.slots.get(slot).getItem(), slot);
      } catch (Exception ignored) {
      }
   }

   private static void abandonExpiredQuickShulkerSearch() {
      if (quickShulkerSearchDeadlineNanos != 0L && !isOpenHandler && System.nanoTime() >= quickShulkerSearchDeadlineNanos) {
         LocalPlayer player = client.player;
         if (player != null) {
            finishQuickShulkerTransfer(player);
         } else {
            lastNeedItemList = new HashSet<>();
            quickShulkerSearchDeadlineNanos = 0L;
            quickShulkerOpenDeadlineNanos = 0L;
            shulkerBoxSlot = -1;
            quickShulkerEmptySlots = 0;
            quickShulkerHasNonShulkerItem = false;
            isOpenHandler = false;
            clearAutomatedQuickShulkerScreenProtection();
         }
      }
   }

   private static void abandonFailedQuickShulkerOpen() {
      if (isOpenHandler && quickShulkerOpenDeadlineNanos != 0L && System.nanoTime() >= quickShulkerOpenDeadlineNanos) {
         LocalPlayer player = client.player;
         if (player != null) {
            // 容器是在超过等待窗口之后才挂上来的（服务端慢/重试点击生效得晚）：
            // 直接按正常取货流程搬运，不要再报「打开失败」并把界面丢掉。
            if (!player.containerMenu.equals(player.inventoryMenu)) {
               switchInv();
               return;
            }

            MessageUtils.setOverlayMessage(I18n.INVENTORY_SHULKER_OPEN_FAILED.getName());
            quickShulkerOpenFailBackoffTicks = QUICK_SHULKER_OPEN_FAIL_BACKOFF_TICKS;
            finishQuickShulkerTransfer(player);
         } else {
            lastNeedItemList = new HashSet<>();
            quickShulkerSearchDeadlineNanos = 0L;
            quickShulkerOpenDeadlineNanos = 0L;
            shulkerBoxSlot = -1;
            quickShulkerEmptySlots = 0;
            quickShulkerHasNonShulkerItem = false;
            isOpenHandler = false;
            clearAutomatedQuickShulkerScreenProtection();
         }
      }
   }


   public static void tick() {
      // 开盒超时复位与背包满退避必须无条件跑（进食期间也要能自愈）
      abandonFailedQuickShulkerOpen();
      if (quickShulkerFullBackoffTicks > 0) {
         quickShulkerFullBackoffTicks--;
      }

      if (quickShulkerOpenFailBackoffTicks > 0) {
         quickShulkerOpenFailBackoffTicks--;
      }

      if (!EatUtils.isBusy()) {
         if (client.player != null) {
            HandRestockShulkerCompat.clientTick(client.player);
         }

         retryQuickShulkerOpenIfNeeded();
         abandonExpiredQuickShulkerSearch();
         if (shulkerCooldown > 0) {
            shulkerCooldown--;
         }

         if (automatedQuickShulkerScreenProtectionTimeout > 0 && --automatedQuickShulkerScreenProtectionTimeout == 0) {
            clearAutomatedQuickShulkerScreenProtection();
         }
      }
   }
}
