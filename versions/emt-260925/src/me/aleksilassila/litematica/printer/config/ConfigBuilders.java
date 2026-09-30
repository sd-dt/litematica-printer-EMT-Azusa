package me.aleksilassila.litematica.printer.config;

import me.aleksilassila.litematica.printer.config.builder.BooleanConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.BooleanHotkeyConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.ColorConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.DoubleConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.HotkeyConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.IntegerConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.OptionListConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.StringConfigBuilder;
import me.aleksilassila.litematica.printer.config.builder.StringListConfigBuilder;

public class ConfigBuilders {
   public static BooleanHotkeyConfigBuilder booleanHotkey(String translateKey) {
      return new BooleanHotkeyConfigBuilder(translateKey);
   }

   public static BooleanConfigBuilder bool(String translateKey) {
      return new BooleanConfigBuilder(translateKey);
   }

   public static HotkeyConfigBuilder hotkey(String translateKey) {
      return new HotkeyConfigBuilder(translateKey);
   }

   public static IntegerConfigBuilder integer(String translateKey) {
      return new IntegerConfigBuilder(translateKey);
   }

   public static DoubleConfigBuilder doubleValue(String translateKey) {
      return new DoubleConfigBuilder(translateKey);
   }

   public static StringListConfigBuilder stringList(String translateKey) {
      return new StringListConfigBuilder(translateKey);
   }

   public static StringConfigBuilder string(String translateKey) {
      return new StringConfigBuilder(translateKey);
   }

   public static OptionListConfigBuilder optionList(String translateKey) {
      return new OptionListConfigBuilder(translateKey);
   }

   public static ColorConfigBuilder color(String translateKey) {
      return new ColorConfigBuilder(translateKey);
   }
}
