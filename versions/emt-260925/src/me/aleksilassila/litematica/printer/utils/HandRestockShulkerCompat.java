package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class HandRestockShulkerCompat {
   private static final int OFFHAND_INVENTORY_SLOT = 40;
   private static Item pendingItem;
   private static int pendingTargetInventorySlot = -1;
   private static final ItemStack[] prevSlots = new ItemStack[41];
   private static final int DROP_SUPPRESS_TICKS = 3;
   private static long lastLocalDropTick = Long.MIN_VALUE;

   private HandRestockShulkerCompat() {
   }

   /**
    * 自动补货是否允许工作。
    * <p>
    * 2026-09-26 修复（用户报告「潜影盒取货会把所有东西自动取出」）：
    * 这里以前只看「快捷潜影盒 + 快捷潜影盒-自动补货」两个开关，**不看打印机总开关**。
    * 而 {@code detectPassiveConsumption} 是由 {@code LocalPlayer.tick} → {@code InventoryUtils.tick()}
    * 每 tick 无条件驱动的，于是**打印机没开**、玩家正常游玩时，每消耗掉一样东西
    * （自己放方块、吃东西、射箭、丢东西……）都会自动开盒、把整叠搬进背包 —— 潜影盒被一点点搬空。
    * 现在要求「核心 → 工作开关」也是开的：只在打印机真正干活时补货。
    */
   private static boolean autoRestockAllowed() {
      return Configs.Core.WORK_SWITCH.getBooleanValue()
         && Configs.Core.HAND_RESTOCK_SHULKER_COMPAT.getBooleanValue()
         && Configs.Core.QUICK_SHULKER.getBooleanValue();
   }

   public static void onHandStackConsumed(Player player, InteractionHand hand, ItemStack before, ItemStack after) {
      if (player instanceof LocalPlayer localPlayer && before != null && !before.isEmpty()) {
         Item item = before.getItem();
         boolean shrunk = after == null || after.isEmpty() || after.is(item) && after.getCount() < before.getCount();
         if (item == Items.FIREWORK_ROCKET && !player.isCreative()) {
            tryRestockFromShulker(localPlayer, hand, item, 1);
         } else if (shrunk) {
            tryRestockFromShulker(localPlayer, hand, item, 0);
         }

         return;
      }
   }

   private static void tryRestockFromShulker(LocalPlayer localPlayer, InteractionHand hand, Item item, int predictedDepletion) {
      if (autoRestockAllowed()) {
         if (!me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.isOpenHandler && !InventoryUtils.hasRecentlyOpenedShulker(localPlayer)) {
            int targetSlot = hand == InteractionHand.OFF_HAND ? 40 : localPlayer.getInventory().getSelectedSlot();
            tryRestockFromShulker(localPlayer, item, predictedDepletion, targetSlot);
         }
      }
   }

   private static void tryRestockFromShulker(LocalPlayer localPlayer, Item item, int predictedDepletion, int targetSlot) {
      if (autoRestockAllowed()) {
         if (!me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.isOpenHandler && !InventoryUtils.hasRecentlyOpenedShulker(localPlayer)) {
            if (targetSlot >= 0) {
               if (countMainInventoryIncludingOffhand(localPlayer, item) - predictedDepletion <= 0
                  && InventoryUtils.countAvailableIncludingShulkers(localPlayer, item) > 0) {
                  recordPendingReturn(targetSlot, item);
                  me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(item);
                  me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.switchItem();
               }
            }
         }
      }
   }

   private static int countMainInventoryIncludingOffhand(LocalPlayer player, Item item) {
      int count = InventoryUtils.countMatchingMainInventory(player, s -> s.is(item));
      ItemStack offhand = player.getInventory().getItem(40);
      if (!offhand.isEmpty() && offhand.is(item)) {
         count += offhand.getCount();
      }

      return count;
   }

   private static void recordPendingReturn(int inventorySlot, Item item) {
      pendingItem = item;
      pendingTargetInventorySlot = inventorySlot;
   }

   public static void onQuickShulkerTransferFinished(Player player) {
      Item item = pendingItem;
      int targetInventorySlot = pendingTargetInventorySlot;
      clearPendingReturn();
      if (item != null && targetInventorySlot >= 0 && player instanceof LocalPlayer localPlayer) {
         if (player.getInventory().getItem(targetInventorySlot).isEmpty()) {
            moveStackToInventorySlot(localPlayer, item, targetInventorySlot);
         }
      }
   }

   public static void markLocalDrop() {
      Minecraft client = Minecraft.getInstance();
      if (client.player != null) {
         lastLocalDropTick = currentTick(client.player);
      }
   }

   public static void clientTick(LocalPlayer player) {
      detectPassiveConsumption(player);
   }

   private static void detectPassiveConsumption(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      boolean quiet = player.isAlive() && player.containerMenu.equals(player.inventoryMenu) && player.inventoryMenu.getCarried().isEmpty();
      // 打印机总开关没开时不做被动补货扫描（快照照常更新，避免开机瞬间误判）
      if (quiet && autoRestockAllowed()) {
         long tick = currentTick(player);
         boolean dropRecent = lastLocalDropTick != Long.MIN_VALUE && tick >= lastLocalDropTick && tick - lastLocalDropTick <= 3L;

         for (int slot = 0; slot <= 40 && !dropRecent; slot++) {
            if (slot <= 35 || slot == 40) {
               ItemStack before = prevSlots[slot];
               if (before != null && !before.isEmpty() && inventory.getItem(slot).isEmpty()) {
                  Item item = before.getItem();
                  if (countItem(prevSlots, item) > countItem(inventory, item)) {
                     tryRestockFromShulker(player, item, 0, slot);
                     break;
                  }
               }
            }
         }
      }

      for (int slotx = 0; slotx <= 40; slotx++) {
         if (slotx <= 35 || slotx == 40) {
            prevSlots[slotx] = inventory.getItem(slotx).copy();
         }
      }
   }

   private static int countItem(ItemStack[] slots, Item item) {
      int count = 0;

      for (int slot = 0; slot <= 40; slot++) {
         if (slot <= 35 || slot == 40) {
            ItemStack stack = slots[slot];
            if (stack != null && !stack.isEmpty() && stack.is(item)) {
               count += stack.getCount();
            }
         }
      }

      return count;
   }

   private static int countItem(Inventory inventory, Item item) {
      int count = 0;

      for (int slot = 0; slot <= 40; slot++) {
         if (slot <= 35 || slot == 40) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.is(item)) {
               count += stack.getCount();
            }
         }
      }

      return count;
   }

   public static void clearPendingReturn() {
      pendingItem = null;
      pendingTargetInventorySlot = -1;
   }

   private static void moveStackToInventorySlot(LocalPlayer player, Item item, int targetInventorySlot) {
      Minecraft client = Minecraft.getInstance();
      if (client.gameMode != null) {
         AbstractContainerMenu menu = player.containerMenu;
         Inventory inventory = player.getInventory();
         int sourceInventorySlot = -1;
         int size = Math.min(36, inventory.getContainerSize());

         for (int slot = 0; slot < size; slot++) {
            if (slot != targetInventorySlot) {
               ItemStack stack = inventory.getItem(slot);
               if (!stack.isEmpty() && stack.is(item)) {
                  sourceInventorySlot = slot;
                  break;
               }
            }
         }

         if (sourceInventorySlot < 0 && targetInventorySlot != 40 && !inventory.getItem(40).isEmpty() && inventory.getItem(40).is(item)) {
            sourceInventorySlot = 40;
         }

         if (sourceInventorySlot >= 0) {
            int sourceMenuSlot = findMenuSlotForInventorySlot(menu, player, sourceInventorySlot);
            if (sourceMenuSlot >= 0) {
               if (targetInventorySlot == 40) {
                  client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 40, ContainerInput.SWAP, player);
               } else {
                  int targetMenuSlot = findMenuSlotForInventorySlot(menu, player, targetInventorySlot);
                  if (targetMenuSlot >= 0) {
                     client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, player);
                     client.gameMode.handleContainerInput(menu.containerId, targetMenuSlot, 0, ContainerInput.PICKUP, player);
                     if (!menu.getCarried().isEmpty()) {
                        client.gameMode.handleContainerInput(menu.containerId, sourceMenuSlot, 0, ContainerInput.PICKUP, player);
                     }
                  }
               }
            }
         }
      }
   }

   private static int findMenuSlotForInventorySlot(AbstractContainerMenu menu, LocalPlayer player, int inventorySlot) {
      for (int i = 0; i < menu.slots.size(); i++) {
         Slot slot = (Slot)menu.slots.get(i);
         if (slot.container == player.getInventory() && slot.getContainerSlot() == inventorySlot) {
            return i;
         }
      }

      return -1;
   }

   private static long currentTick(Player player) {
      return player.level() == null ? 0L : player.level().getGameTime();
   }
}
