package me.aleksilassila.litematica.printer.enums;

import java.util.IdentityHashMap;
import java.util.List;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public enum BlockMatchResult {
   MISSING,
   WRONG_BLOCK,
   WRONG_STATE,
   CORRECT;

   private static final IdentityHashMap<BlockState, Boolean> COVER_LIST_CACHE = new IdentityHashMap<>();
   private static List<String> coverListSnapshot = List.of();

   public static BlockMatchResult compare(SchematicBlockContext context, Property<?>... propertiesToIgnore) {
      return propertiesToIgnore.length == 0 ? compare(context.requiredState, context.currentState) : compareWithIgnoredProperties(context, propertiesToIgnore);
   }

   public static BlockMatchResult compare(BlockState requiredState, BlockState currentState) {
      if (requiredState.equals(currentState)) {
         return CORRECT;
      } else if (requiredState.getBlock().equals(currentState.getBlock())) {
         return BlockStateUtils.statesEqualIgnoreProperties(requiredState, currentState) ? CORRECT : WRONG_STATE;
      } else if (currentState.isAir()) {
         return MISSING;
      } else if (requiredState.isAir() || !BlockStateUtils.isReplaceable(currentState)) {
         return WRONG_BLOCK;
      } else {
         return Configs.Print.PRINT_REPLACE.getBooleanValue() && matchesCoverList(currentState) && !matchesCoverList(requiredState) ? MISSING : WRONG_BLOCK;
      }
   }

   public static boolean isCorrect(BlockState requiredState, BlockState currentState) {
      if (requiredState.equals(currentState)) {
         return true;
      } else {
         return requiredState.getBlock().equals(currentState.getBlock()) ? BlockStateUtils.statesEqualIgnoreProperties(requiredState, currentState) : false;
      }
   }

   private static boolean matchesCoverList(BlockState state) {
      List<String> list = Configs.Print.REPLACEABLE_LIST.getStrings();
      if (list != coverListSnapshot) {
         coverListSnapshot = List.copyOf(list);
         COVER_LIST_CACHE.clear();
      }

      Boolean cached = COVER_LIST_CACHE.get(state);
      if (cached == null) {
         cached = list.stream().anyMatch(string -> PinYinSearchUtils.matchName(string, state));
         COVER_LIST_CACHE.put(state, cached);
      }

      return cached;
   }

   private static BlockMatchResult compareWithIgnoredProperties(SchematicBlockContext context, Property<?>[] propertiesToIgnore) {
      if (context.requiredState.equals(context.currentState)) {
         return CORRECT;
      } else if (context.requiredState.getBlock().equals(context.currentState.getBlock())) {
         return BlockStateUtils.statesEqualIgnoreProperties(context.requiredState, context.currentState, propertiesToIgnore) ? CORRECT : WRONG_STATE;
      } else if (context.currentState.isAir()) {
         return MISSING;
      } else if (context.requiredState.isAir() || !BlockStateUtils.isReplaceable(context.currentState)) {
         return WRONG_BLOCK;
      } else {
         return Configs.Print.PRINT_REPLACE.getBooleanValue() && matchesCoverList(context.currentState) && !matchesCoverList(context.requiredState)
            ? MISSING
            : WRONG_BLOCK;
      }
   }
}
