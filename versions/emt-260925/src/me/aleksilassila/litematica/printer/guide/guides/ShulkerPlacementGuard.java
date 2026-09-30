package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.printer.PrintTaskController;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class ShulkerPlacementGuard {
   public static final ShulkerPlacementGuard INSTANCE = new ShulkerPlacementGuard();
   private static final int CONFIRM_TICKS = 5;
   private final Map<Long, ShulkerPlacementGuard.ConfirmEntry> pending = new HashMap<>();
   private final Map<Long, Long> readyMap = new HashMap<>();
   private long readyMapTick = -1L;

   private ShulkerPlacementGuard() {
   }

   public boolean isReady(BlockPos pos) {
      return pos != null && this.readyMap.containsKey(pos.asLong());
   }

   private void refreshReadyTick(long tick) {
      if (this.readyMapTick != tick) {
         this.readyMapTick = tick;
         this.readyMap.clear();
      }
   }

   public ShulkerPlacementGuard.GuardResult evaluate(SchematicBlockContext ctx) {
      Minecraft minecraft = Minecraft.getInstance();
      LocalPlayer player = minecraft.player;
      ClientLevel level = ctx.level;
      long key = ctx.blockPos.asLong();
      if (player != null && level != null) {
         long tick = level.getGameTime();
         this.refreshReadyTick(tick);
         if (PrintTaskController.INSTANCE.hasPendingOrdinaryInRange(true)) {
            return ShulkerPlacementGuard.GuardResult.WAIT_OTHER_BLOCKS;
         } else if (!player.containerMenu.equals(player.inventoryMenu)) {
            player.closeContainer();
            return ShulkerPlacementGuard.GuardResult.WAIT_CLOSE;
         } else {
            ShulkerPlacementGuard.ConfirmEntry entry = this.pending.get(key);
            if (entry == null) {
               int slot = this.findEmptyShulkerSlot(player);
               if (slot == -1) {
                  return ShulkerPlacementGuard.GuardResult.NO_EMPTY;
               } else {
                  this.pending.put(key, new ShulkerPlacementGuard.ConfirmEntry(slot, tick));
                  return ShulkerPlacementGuard.GuardResult.WAIT_CONFIRM;
               }
            } else if (tick - entry.tick < 5L) {
               return ShulkerPlacementGuard.GuardResult.WAIT_CONFIRM;
            } else {
               this.pending.remove(key);
               ItemStack stack = player.getInventory().getItem(entry.slot);
               if (stack.isEmpty() || !InventoryUtils.isEmptyShulker(stack)) {
                  int slot = this.findEmptyShulkerSlot(player);
                  if (slot == -1) {
                     return ShulkerPlacementGuard.GuardResult.NO_EMPTY;
                  } else {
                     this.pending.put(key, new ShulkerPlacementGuard.ConfirmEntry(slot, tick));
                     return ShulkerPlacementGuard.GuardResult.WAIT_CONFIRM;
                  }
               } else if (!InventoryUtils.setPickedItemToHand(entry.slot, stack, minecraft)) {
                  return ShulkerPlacementGuard.GuardResult.NO_EMPTY;
               } else {
                  this.readyMap.put(key, tick);
                  return ShulkerPlacementGuard.GuardResult.READY;
               }
            }
         }
      } else {
         return ShulkerPlacementGuard.GuardResult.NO_EMPTY;
      }
   }

   private int findEmptyShulkerSlot(LocalPlayer player) {
      Inventory inventory = player.getInventory();

      for (int i = 0; i < inventory.getContainerSize(); i++) {
         ItemStack itemStack = inventory.getItem(i);
         if (!itemStack.isEmpty()
            && InventoryUtils.isShulkerItem(itemStack.getItem())
            && !BlockUtils.isShulkerRecentlyOpened(i)
            && InventoryUtils.isEmptyShulker(itemStack)) {
            return i;
         }
      }

      return -1;
   }

   private static class ConfirmEntry {
      final int slot;
      final long tick;

      ConfirmEntry(int slot, long tick) {
         this.slot = slot;
         this.tick = tick;
      }
   }

   public static enum GuardResult {
      WAIT_OTHER_BLOCKS,
      WAIT_CLOSE,
      WAIT_CONFIRM,
      READY,
      NO_EMPTY;
   }
}
