package me.aleksilassila.litematica.printer.config.builder;

import com.google.common.collect.ImmutableList;
import fi.dy.masa.malilib.config.options.ConfigStringList;
import java.util.List;
import java.util.stream.Stream;
import me.aleksilassila.litematica.printer.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class StringListConfigBuilder extends BaseConfigBuilder<ConfigStringList, StringListConfigBuilder> {
   private ImmutableList<String> defaultValue = ImmutableList.of();

   public StringListConfigBuilder(I18n i18n) {
      super(i18n);
   }

   public StringListConfigBuilder(String translateKey) {
      this(I18n.of(translateKey));
   }

   public StringListConfigBuilder defaultValue(ImmutableList<String> value) {
      this.defaultValue = value;
      return this;
   }

   public StringListConfigBuilder defaultValue(List<?> value) {
      this.defaultValue = this.convertToImmutableStringList(value.stream());
      return this;
   }

   public StringListConfigBuilder defaultValue(Stream<?> value) {
      this.defaultValue = this.convertToImmutableStringList(value);
      return this;
   }

   public StringListConfigBuilder defaultValue(Object... value) {
      this.defaultValue = this.convertToImmutableStringList(Stream.of(value));
      return this;
   }

   private ImmutableList<String> convertToImmutableStringList(Stream<?> stream) {
      return stream.map(this::convertObjectToString).filter(s -> s != null && !s.isEmpty()).collect(ImmutableList.toImmutableList());
   }

   private String convertObjectToString(Object obj) {
      if (obj == null) {
         return "";
      } else if (obj instanceof BlockState blockState) {
         return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
      } else if (obj instanceof Block block) {
         return BuiltInRegistries.BLOCK.getKey(block).toString();
      } else if (obj instanceof ItemStack itemStack) {
         return BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString();
      } else if (obj instanceof ItemLike itemLike) {
         Item item = itemLike.asItem();
         return BuiltInRegistries.ITEM.getKey(item).toString();
      } else {
         return obj.toString();
      }
   }

   public ConfigStringList build() {
      ConfigStringList config = new ConfigStringList(this.i18n.getNameKey(), this.defaultValue, this.descKey);
      return this.buildExtension(config);
   }
}
