package me.aleksilassila.litematica.printer;

import lombok.Generated;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class I18n {
   public static final I18n MESSAGE_TOGGLED = of("message.toggled");
   public static final I18n MESSAGE_VALUE_OFF = of("message.value.off");
   public static final I18n MESSAGE_VALUE_ON = of("message.value.on");
   public static final I18n AUTO_DISABLE_NOTICE = of("auto_disable_notice");
   public static final I18n FREE_NOTICE = of("free_notice");
   public static final I18n PRINTER_TAGLINE = of("printer_tagline");
   public static final I18n BEDROCK_CREATIVE_MODE = of("bedrock.creative_mode");
   public static final I18n BEDROCK_MOD_MISSING = of("bedrock.mod_missing");
   public static final I18n INVENTORY_SYNC_CANCELLED = of("inventory.sync.cancelled");
   public static final I18n INVENTORY_SYNC_COMPLETE = of("inventory.sync.complete");
   public static final I18n INVENTORY_SYNC_CONTAINER_CANNOT_OPEN = of("inventory.sync.container_cannot_open");
   public static final I18n INVENTORY_SYNC_NOT_CONTAINER = of("inventory.sync.not_container");
   public static final I18n INVENTORY_SYNC_TOO_FAR = of("inventory.sync.too_far");
   public static final I18n INVENTORY_SYNC_REMAINING = of("inventory.sync.remaining");
   public static final I18n INVENTORY_BACKPACK_FULL = of("inventory.backpack_full");
   public static final I18n INVENTORY_SHULKER_PRESELECT = of("inventory.shulker_preselect");
   public static final I18n INVENTORY_SHULKER_OPEN_FAILED = of("inventory.shulker_open_failed");
   public static final I18n BREWINGSTAND_LOWER = of("brewingstand.lower");
   public static final I18n BREWINGSTAND_RAISE = of("brewingstand.raise");
   public static final I18n BLOCK_NO_SUPPORT = of("block.no_support");
   public static final I18n BLOCK_MISMATCH = of("block.mismatch");
   private static final String PREFIX_CONFIG = "config";
   private static final String PREFIX_COMMENT = "desc";
   @Nullable
   private final String prefix;
   private final String nameKey;
   private final String withPrefixNameKey;
   private final String descKey;
   private final String configNameKey;
   private final String configDescKey;

   private I18n(@Nullable String prefix, String nameKey) {
      this.prefix = prefix;
      this.nameKey = nameKey;
      this.withPrefixNameKey = prefix == null ? nameKey : prefix + "." + nameKey;
      this.descKey = this.withPrefixNameKey + ".desc";
      String configNameKey = prefix == null ? "config" : prefix + ".config";
      this.configNameKey = configNameKey + "." + nameKey;
      this.configDescKey = configNameKey + "." + nameKey + ".desc";
   }

   public static I18n of(@Nullable String prefix, String key) {
      return new I18n(prefix, key);
   }

   public static I18n of(String key) {
      return new I18n("litematica_printer", key);
   }

   public MutableComponent getName() {
      return MessageUtils.translatable(this.withPrefixNameKey);
   }

   public MutableComponent getName(Object... objects) {
      return MessageUtils.translatable(this.withPrefixNameKey, objects);
   }

   public MutableComponent getDesc() {
      return MessageUtils.translatable(this.descKey);
   }

   public MutableComponent getDesc(Object... objects) {
      return MessageUtils.translatable(this.descKey, objects);
   }

   public MutableComponent getConfigName() {
      return MessageUtils.translatable(this.configNameKey);
   }

   public MutableComponent getConfigName(Object... objects) {
      return MessageUtils.translatable(this.configNameKey, objects);
   }

   public MutableComponent getConfigDesc() {
      return MessageUtils.translatable(this.configDescKey);
   }

   public MutableComponent getConfigDesc(Object... objects) {
      return MessageUtils.translatable(this.configDescKey, objects);
   }

   public String getSimpleKey() {
      if (this.nameKey != null && !this.nameKey.isEmpty()) {
         int lastDotIndex = this.nameKey.lastIndexOf(46);
         if (lastDotIndex == -1) {
            return this.nameKey;
         } else {
            return lastDotIndex == this.nameKey.length() - 1 ? "" : this.nameKey.substring(lastDotIndex + 1);
         }
      } else {
         return this.nameKey == null ? "" : this.nameKey;
      }
   }

   @Nullable
   @Generated
   public String getPrefix() {
      return this.prefix;
   }

   @Generated
   public String getNameKey() {
      return this.nameKey;
   }

   @Generated
   public String getWithPrefixNameKey() {
      return this.withPrefixNameKey;
   }

   @Generated
   public String getDescKey() {
      return this.descKey;
   }

   @Generated
   public String getConfigNameKey() {
      return this.configNameKey;
   }

   @Generated
   public String getConfigDescKey() {
      return this.configDescKey;
   }
}
