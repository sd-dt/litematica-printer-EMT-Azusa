package me.aleksilassila.litematica.printer.config.builder;

import fi.dy.masa.malilib.config.options.ConfigString;
import me.aleksilassila.litematica.printer.I18n;

public class StringConfigBuilder extends BaseConfigBuilder<ConfigString, StringConfigBuilder> {
   private String defaultValue = "";

   public StringConfigBuilder(I18n i18n) {
      super(i18n);
   }

   public StringConfigBuilder(String translateKey) {
      this(I18n.of(translateKey));
   }

   public StringConfigBuilder defaultValue(String value) {
      this.defaultValue = value;
      return this;
   }

   public ConfigString build() {
      ConfigString config = new ConfigString(this.i18n.getNameKey(), this.defaultValue, this.descKey);
      return this.buildExtension(config);
   }
}
