package me.aleksilassila.litematica.printer.enums;

import java.util.HashSet;
import java.util.Set;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public enum BlockPrintState {
   MISSING_BLOCK,
   ERROR_BLOCK,
   ERROR_BLOCK_STATE,
   CORRECT;

   public static BlockPrintState get(BlockState requiredState, BlockState currentState, Property<?>... propertiesToIgnore) {
      Set<String> replaceSet = new HashSet<>(Configs.Print.REPLACEABLE_LIST.getStrings());
      if (requiredState.equals(currentState)) {
         return CORRECT;
      } else if (requiredState.getBlock().equals(currentState.getBlock())) {
         return !BlockUtils.statesEqualIgnoreProperties(requiredState, currentState, propertiesToIgnore) ? ERROR_BLOCK_STATE : CORRECT;
      } else if (requiredState.isAir()
         || requiredState.is(Blocks.AIR)
         || requiredState.is(Blocks.CAVE_AIR)
         || currentState.is(Blocks.VOID_AIR)
         || !currentState.isAir() && !currentState.is(Blocks.AIR) && !currentState.is(Blocks.CAVE_AIR) && !currentState.is(Blocks.VOID_AIR)) {
         return Configs.Print.PRINT_REPLACE.getBooleanValue()
               && replaceSet.stream()
                  .anyMatch(string -> !PinYinSearchUtils.matchName(string, requiredState) && PinYinSearchUtils.matchName(string, currentState))
               && !requiredState.isAir()
            ? MISSING_BLOCK
            : ERROR_BLOCK;
      } else {
         return MISSING_BLOCK;
      }
   }

   public static BlockPrintState get(SchematicBlockContext context, Property<?>... propertiesToIgnore) {
      return get(context.requiredState, context.currentState, propertiesToIgnore);
   }

   public static BlockPrintState get(BlockPos pos, Property<?>... propertiesToIgnore) {
      BlockState requiredState = LitematicaUtils.getSchematicBlockState(pos);
      BlockState currentState = Minecraft.getInstance().level.getBlockState(pos);
      return requiredState == null ? null : get(requiredState, currentState, propertiesToIgnore);
   }
}
