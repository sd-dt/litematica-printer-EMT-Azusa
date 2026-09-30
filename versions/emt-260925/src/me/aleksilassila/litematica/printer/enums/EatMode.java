package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

public enum EatMode implements ConfigOptionListEntry<EatMode> {
   OFF("eatMode.off"),
   PRINTER_ONLY("eatMode.printerOnly"),
   ANYTIME("eatMode.anytime");

   private final I18n i18n;

   private EatMode(String translateKey) {
      this.i18n = I18n.of(translateKey);
   }

   @Override
   public I18n getI18n() {
      return this.i18n;
   }
}
