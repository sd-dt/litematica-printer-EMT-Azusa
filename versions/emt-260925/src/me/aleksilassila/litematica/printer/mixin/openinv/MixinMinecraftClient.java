package me.aleksilassila.litematica.printer.mixin.openinv;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.CloudStoreUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Minecraft.class})
public abstract class MixinMinecraftClient {
   @Shadow
   public LocalPlayer player;
   @Shadow
   @Nullable
   public ClientLevel level;

   @Inject(
      method = {"setScreenAndShow"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void setScreen(@Nullable Screen screen, CallbackInfo ci) {
      if (InventoryUtils.shouldPreserveAutomatedQuickShulkerScreenOnClose(screen)) {
         ci.cancel();
      } else {
         if (ModUtils.closeScreen > 0 && screen instanceof AbstractContainerScreen) {
            ModUtils.closeScreen--;
            ci.cancel();
         }
      }
   }

   @WrapOperation(
      method = {"pickBlockOrEntity"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handlePickItemFromBlock(Lnet/minecraft/core/BlockPos;Z)V"
      )}
   )
   private void doItemPick(MultiPlayerGameMode instance, BlockPos pos, boolean b, Operation<Void> original) {
      if (this.level == null) {
         original.call(new Object[]{instance, pos, b});
      } else {
         WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
         Item item;
         if (schematic != null && LitematicaUtils.isSchematicBlock(pos)) {
            BlockState schematicState = LitematicaUtils.getSchematicBlockState(pos);
            item = schematicState != null && !schematicState.isAir() ? schematicState.getBlock().asItem() : Items.AIR;
         } else {
            item = this.level.getBlockState(pos).getBlock().asItem();
         }

         boolean forceCloudStore = Configs.Special.PRINT_CLOUD_STORE_MIDDLE_CLICK_FORCE.getBooleanValue();
         boolean itemIsShulker = me.aleksilassila.litematica.printer.utils.InventoryUtils.isShulkerItem(item);
         boolean inMain;
         if (itemIsShulker) {
            inMain = this.player.inventoryMenu.slots.stream().anyMatch(slot -> {
               ItemStack stack = slot.getItem();
               return stack.getItem().equals(item) && me.aleksilassila.litematica.printer.utils.InventoryUtils.isEmptyShulker(stack);
            });
         } else {
            inMain = me.aleksilassila.litematica.printer.utils.InventoryUtils.countMatchingMainInventory(this.player, stack -> stack.is(item)) > 0;
         }

         boolean inShulkers = me.aleksilassila.litematica.printer.utils.InventoryUtils.countAvailableIncludingShulkers(this.player, item) > 0;
         if (!this.player.getAbilities().instabuild) {
            if (!inMain && inShulkers && Configs.Core.QUICK_SHULKER.getBooleanValue()) {
               InventoryUtils.addQuickShulkerDemand(item);
               InventoryUtils.switchItem();
               return;
            }

            if (!inShulkers
               && (forceCloudStore || Configs.Special.PRINT_CLOUD_STORE_MANUAL_REFILL.getBooleanValue())
               && item != Items.AIR
               && ModUtils.isCloudStoreLoaded()) {
               CloudStoreUtils.tryRequestRefillImmediate(this.player, item, Configs.Special.PRINT_CLOUD_STORE_REFILL_AMOUNT.getIntegerValue());
            }
         }

         original.call(new Object[]{instance, pos, b});
      }
   }
}
