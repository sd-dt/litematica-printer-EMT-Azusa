package me.aleksilassila.litematica.printer.gui;

import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import java.util.Objects;
import net.minecraft.client.gui.screens.Screen;

public record ButtonListenerChangeMenu(ButtonType type, Screen parent) implements IButtonActionListener {
   public void actionPerformedWithButton(ButtonBase arg0, int arg1) {
      if (Objects.requireNonNull(this.type) == ButtonType.PRINTER_SETTINGS) {
         GuiBase.openGui(new ConfigUi());
      }
   }
}
