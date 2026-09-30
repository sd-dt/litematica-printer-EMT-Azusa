package me.aleksilassila.litematica.printer.config.builder;

import fi.dy.masa.malilib.config.options.ConfigBoolean;
import me.aleksilassila.litematica.printer.I18n;

public class BooleanConfigBuilder extends BaseConfigBuilder<ConfigBoolean, BooleanConfigBuilder> {
   private boolean defaultValue = false;

   public BooleanConfigBuilder(I18n i18n) {
      super(i18n);
   }

   public BooleanConfigBuilder(String translateKey) {
      this(I18n.of(translateKey));
   }

   public BooleanConfigBuilder defaultValue(boolean value) {
      this.defaultValue = value;
      return this;
   }

   public ConfigBoolean build() {
      ConfigBoolean config = new ConfigBoolean(this.i18n.getNameKey(), this.defaultValue, this.descKey);
      return this.buildExtension(config);
   }
}
