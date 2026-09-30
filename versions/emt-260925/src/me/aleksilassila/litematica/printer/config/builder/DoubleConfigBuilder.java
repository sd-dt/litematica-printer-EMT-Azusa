package me.aleksilassila.litematica.printer.config.builder;

import fi.dy.masa.malilib.config.options.ConfigDouble;
import me.aleksilassila.litematica.printer.I18n;

public class DoubleConfigBuilder extends BaseConfigBuilder<ConfigDouble, DoubleConfigBuilder> {
   private double defaultValue = 0.0;
   private double minValue = -Double.MAX_VALUE;
   private double maxValue = Double.MAX_VALUE;
   private boolean useSlider = false;

   public DoubleConfigBuilder(I18n i18n) {
      super(i18n);
   }

   public DoubleConfigBuilder(String translateKey) {
      this(I18n.of(translateKey));
   }

   public DoubleConfigBuilder defaultValue(double value) {
      this.defaultValue = value;
      return this;
   }

   public DoubleConfigBuilder range(double min, double max) {
      return this.min(min).max(max);
   }

   public DoubleConfigBuilder min(double min) {
      this.minValue = min;
      return this;
   }

   public DoubleConfigBuilder max(double max) {
      this.maxValue = max;
      return this;
   }

   public DoubleConfigBuilder useSlider(boolean useSlider) {
      this.useSlider = useSlider;
      return this;
   }

   public ConfigDouble build() {
      ConfigDouble config = new ConfigDouble(this.i18n.getNameKey(), this.defaultValue, this.minValue, this.maxValue, this.useSlider, this.descKey);
      return this.buildExtension(config);
   }
}
