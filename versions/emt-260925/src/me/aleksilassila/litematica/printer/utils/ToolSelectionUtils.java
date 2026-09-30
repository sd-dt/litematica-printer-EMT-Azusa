package me.aleksilassila.litematica.printer.utils;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;

public final class ToolSelectionUtils {
   private ToolSelectionUtils() {
   }

   public static boolean prefersSilkTouchForDrops(BlockState state) {
      if (state == null) {
         return false;
      } else {
         String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
         return path.equals("glass") || path.equals("glass_pane") || path.endsWith("_stained_glass") || path.endsWith("_stained_glass_pane");
      }
   }

   public static boolean hasSilkTouch(ItemStack stack) {
      if (stack != null && !stack.isEmpty()) {
         for (Holder<Enchantment> enchantment : stack.getEnchantments().keySet()) {
            Optional<ResourceKey<Enchantment>> enchantmentKey = enchantment.unwrapKey();
            if (enchantmentKey.isPresent() && enchantmentKey.get() == Enchantments.SILK_TOUCH) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }
}
