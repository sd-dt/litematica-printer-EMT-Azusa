package me.aleksilassila.litematica.printer.utils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public record BlockNbtRule(String blockMatcher, List<BlockNbtRule.Condition> conditions) {
   private static final String PREFIX = "@nbt:";
   private static final Gson GSON = new Gson();

   public BlockNbtRule(String blockMatcher, List<BlockNbtRule.Condition> conditions) {
      blockMatcher = blockMatcher == null ? "" : blockMatcher;
      conditions = List.copyOf(
         (conditions == null ? List.<BlockNbtRule.Condition>of() : conditions)
            .stream()
            .filter(condition -> condition != null && condition.path() != null && condition.path().startsWith("state.") && !condition.path().isBlank())
            .toList()
      );
      this.blockMatcher = blockMatcher;
      this.conditions = conditions;
   }

   public static BlockNbtRule parse(String value) {
      if (value != null && value.startsWith("@nbt:")) {
         try {
            String json = new String(Base64.getUrlDecoder().decode(value.substring("@nbt:".length())), StandardCharsets.UTF_8);
            BlockNbtRule.Stored stored = (BlockNbtRule.Stored)GSON.fromJson(json, BlockNbtRule.Stored.class);
            List<BlockNbtRule.Condition> conditions = new ArrayList<>();
            if (stored != null && stored.conditions != null) {
               for (BlockNbtRule.Condition condition : stored.conditions) {
                  if (condition != null && condition.path != null && !condition.path.isBlank()) {
                     conditions.add(condition);
                  }
               }
            }

            return new BlockNbtRule(stored == null ? "" : stored.block, conditions);
         } catch (JsonSyntaxException | IllegalArgumentException var6) {
            return new BlockNbtRule(value, List.of());
         }
      } else {
         return new BlockNbtRule(value, List.of());
      }
   }

   public String encode() {
      return this.conditions.isEmpty()
         ? this.blockMatcher
         : "@nbt:"
            + Base64.getUrlEncoder()
               .withoutPadding()
               .encodeToString(GSON.toJson(new BlockNbtRule.Stored(this.blockMatcher, this.conditions)).getBytes(StandardCharsets.UTF_8));
   }

   public boolean matchesState(BlockState state) {
      for (BlockNbtRule.Condition condition : this.conditions) {
         if (condition.path().startsWith("state.")) {
            String propertyName = condition.path().substring("state.".length());
            Property<?> property = state.getBlock().getStateDefinition().getProperty(propertyName);
            if (property == null || !matchesPropertyValue(state, property, condition.value())) {
               return false;
            }
         }
      }

      return true;
   }

   private static <T extends Comparable<T>> boolean matchesPropertyValue(BlockState state, Property<T> property, String expected) {
      String actual = property.getName(state.getValue(property));
      return actual.equals(expected == null ? "" : expected.trim());
   }

   public static record Condition(String path, String value) {
   }

   private static record Stored(String block, List<BlockNbtRule.Condition> conditions) {
   }
}
