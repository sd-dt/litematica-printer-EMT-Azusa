package me.aleksilassila.litematica.printer.mixin.openinv;

import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 26.2 起 Minecraft.setScreen 移到了 Gui.setScreen，这里单独挂一个 mixin（逻辑与原来一致） */
@Mixin({Gui.class})
public abstract class MixinGuiScreenGuard {
   @Inject(
      method = {"setScreen"},
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
}
