package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.CloudStoreUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(
   targets = {"fi.dy.masa.litematica.util.WorldUtils"}
)
public class MixinWorldUtils {
   @WrapOperation(
      method = {"doSchematicWorldPickBlock"},
      at = {@At(
         value = "INVOKE",
         target = "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"
      )}
   )
   private static void schematicWorldPickBlock(ItemStack stack, BlockPos pos, Level schematicWorld, Minecraft mc, Operation<Void> original) {
      original.call(new Object[]{stack, pos, schematicWorld, mc});
      if (mc.player != null && !stack.isEmpty() && stack.getItem() != Items.AIR && !mc.player.getAbilities().instabuild) {
         Item item = stack.getItem();
         boolean itemIsShulker = InventoryUtils.isShulkerItem(item);
         boolean inMain;
         if (itemIsShulker) {
            inMain = mc.player.inventoryMenu.slots.stream().anyMatch(slot -> {
               ItemStack slotStack = slot.getItem();
               return slotStack.getItem().equals(item) && InventoryUtils.isEmptyShulker(slotStack);
            });
         } else {
            inMain = InventoryUtils.countMatchingMainInventory(mc.player, candidate -> candidate.is(item)) > 0;
         }

         boolean inShulkers = InventoryUtils.countAvailableIncludingShulkers(mc.player, item) > 0;
         if (!inMain && inShulkers && Configs.Core.QUICK_SHULKER.getBooleanValue()) {
            me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(item);
            me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.switchItem();
         } else {
            if (!inShulkers
               && (Configs.Special.PRINT_CLOUD_STORE_MIDDLE_CLICK_FORCE.getBooleanValue() || Configs.Special.PRINT_CLOUD_STORE_MANUAL_REFILL.getBooleanValue())
               && ModUtils.isCloudStoreLoaded()) {
               CloudStoreUtils.tryRequestRefillImmediate(mc.player, item, Configs.Special.PRINT_CLOUD_STORE_REFILL_AMOUNT.getIntegerValue());
            }
         }
      }
   }
}
