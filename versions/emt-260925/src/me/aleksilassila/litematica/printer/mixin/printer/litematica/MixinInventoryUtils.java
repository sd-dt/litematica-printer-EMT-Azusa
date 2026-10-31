package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.util.InventoryUtils;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

@Mixin({InventoryUtils.class})
public class MixinInventoryUtils {
   @Overwrite
   private static int getPickBlockTargetSlot(Player player) {
      if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
         return -1;
      } else {
         if (InventoryUtilsAccessor.getNextPickSlotIndex() >= InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().size()) {
            InventoryUtilsAccessor.setNextPickSlotIndex(0);
         }

         for (int i = 0; i < InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().size(); i++) {
            int slotNum = InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().get(InventoryUtilsAccessor.getNextPickSlotIndex());
            InventoryUtilsAccessor.setNextPickSlotIndex(InventoryUtilsAccessor.getNextPickSlotIndex() + 1);
            if (InventoryUtilsAccessor.getNextPickSlotIndex() >= InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().size()) {
               InventoryUtilsAccessor.setNextPickSlotIndex(0);
            }

            if (InventoryUtilsAccessor.canPickToSlot(player.getInventory(), slotNum)) {
               return slotNum;
            }
         }

         // 所有可选取槽位都被工具/可损耗物品占满：尝试把一个工具挪进背包空槽腾位
         int freedSlot = me.aleksilassila.litematica.printer.utils.InventoryUtils.tryFreePickBlockSlot(player);
         if (freedSlot >= 0) {
            return freedSlot;
         }

         return -1;
      }
   }
}
