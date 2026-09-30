package me.aleksilassila.litematica.printer.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import java.util.Arrays;
import me.aleksilassila.litematica.printer.I18n;

public interface ConfigOptionListEntry<T extends Enum<T> & ConfigOptionListEntry<T>> extends IConfigOptionListEntry {
   I18n getI18n();

   default String getStringValue() {
      return this.getI18n().getSimpleKey();
   }

   default String getDisplayName() {
      return this.getI18n().getConfigName().getString();
   }

   default IConfigOptionListEntry cycle(boolean forward) {
      if (this instanceof Enum<?> enumInstance) {
         Enum<?>[] enumConstants = enumInstance.getDeclaringClass().getEnumConstants();
         int ordinal = enumInstance.ordinal();
         int nextIndex = forward
            ? (ordinal + 1) % enumConstants.length
            : (ordinal - 1 + enumConstants.length) % enumConstants.length;
         return (IConfigOptionListEntry)enumConstants[nextIndex];
      } else {
         throw new IllegalStateException("ConfigOptionListEntry 只支持枚举实现！");
      }
   }

   default T fromString(String name) {
      if (this instanceof Enum<?> enumInstance) {
         Class<T> enumClass = (Class<T>)enumInstance.getDeclaringClass();
         return Arrays.<T>stream(enumClass.getEnumConstants())
            .filter(enumEntry -> enumEntry.getStringValue().equalsIgnoreCase(name))
            .findFirst()
            .orElse(enumClass.getEnumConstants()[0]);
      } else {
         throw new IllegalStateException("ConfigOptionListEntry 仅支持枚举实现！");
      }
   }

   static <T extends Enum<T> & ConfigOptionListEntry<T>> T fromStringStatic(Class<T> enumClass, String name) {
      return Arrays.<T>stream(enumClass.getEnumConstants())
         .filter(enumEntry -> enumEntry.getStringValue().equalsIgnoreCase(name))
         .findFirst()
         .orElse(enumClass.getEnumConstants()[0]);
   }
}
