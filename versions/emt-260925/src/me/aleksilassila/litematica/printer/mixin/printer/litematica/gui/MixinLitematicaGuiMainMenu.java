package me.aleksilassila.litematica.printer.mixin.printer.litematica.gui;

import fi.dy.masa.litematica.gui.GuiMainMenu;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import me.aleksilassila.litematica.printer.gui.ButtonListenerChangeMenu;
import me.aleksilassila.litematica.printer.gui.ButtonType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {GuiMainMenu.class},
   remap = false
)
public abstract class MixinLitematicaGuiMainMenu extends GuiBase {
   @Invoker(
      remap = false
   )
   public abstract int callGetButtonWidth();

   @Inject(
      method = {"initGui"},
      at = {@At("RETURN")},
      remap = false
   )
   public void initGui(CallbackInfo ci) {
      int width = this.callGetButtonWidth();
      int x = width + 12 + 20;
      int y = 52;
      this.createPrinterSettingsButton(x, y, width, ButtonType.PRINTER_SETTINGS);
   }

   @Unique
   private ButtonGeneric createPrinterSettingsButton(int x, int y, int width, ButtonType type) {
      ButtonGeneric button = new ButtonGeneric(x, y, width, 20, type.getDisplayName(), new String[0]);
      button.setEnabled(true);
      this.addButton(button, new ButtonListenerChangeMenu(type, this));
      return button;
   }
}
