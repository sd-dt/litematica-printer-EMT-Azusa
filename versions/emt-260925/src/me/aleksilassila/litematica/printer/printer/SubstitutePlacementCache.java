package me.aleksilassila.litematica.printer.printer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.IdentifierUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public final class SubstitutePlacementCache {
   private static boolean enabled;
   private static List<String> source = List.of();
   private static final Map<Block, List<Block>> substitutesByTarget = new HashMap<>();

   private SubstitutePlacementCache() {
   }

   public static boolean active() {
      refresh();
      return enabled && !substitutesByTarget.isEmpty();
   }

   public static boolean hasSubstitutes(Block target) {
      refresh();
      return substitutesByTarget.containsKey(target);
   }

   public static List<Block> getSubstitutes(Block target) {
      refresh();
      return substitutesByTarget.getOrDefault(target, List.of());
   }

   public static boolean isSubstituteOf(Block target, Block candidate) {
      refresh();
      return substitutesByTarget.getOrDefault(target, List.of()).contains(candidate);
   }

   private static void refresh() {
      boolean en = Configs.Print.SUBSTITUTE_PLACEMENT.getBooleanValue();
      List<String> cur = Configs.Print.SUBSTITUTE_LIST.getStrings();
      if (en != enabled || !cur.equals(source)) {
         enabled = en;
         source = List.copyOf(cur);
         substitutesByTarget.clear();
         if (en) {
            for (String line : cur) {
               parseLine(line);
            }
         }
      }
   }

   private static void parseLine(String line) {
      if (line != null && !line.isBlank()) {
         String normalized = line.replace('，', ',').replace('；', ';').replace('：', ':');

         for (String rule : normalized.split(";")) {
            rule = rule.trim();
            if (!rule.isEmpty()) {
               int sep = rule.lastIndexOf(58);
               if (sep > 0 && sep != rule.length() - 1) {
                  String targetToken = rule.substring(sep + 1).trim();
                  Block target = resolveStrict(targetToken);
                  if (target != null && target != Blocks.AIR) {
                     Set<Block> subs = new LinkedHashSet<>();
                     boolean valid = true;

                     for (String token : rule.substring(0, sep).split(",")) {
                        Block sub = resolveStrict(token.trim());
                        if (sub == null || sub == Blocks.AIR || sub == target) {
                           valid = false;
                           break;
                        }

                        subs.add(sub);
                     }

                     if (valid && !subs.isEmpty()) {
                        List<Block> merged = new ArrayList<>(substitutesByTarget.getOrDefault(target, List.of()));

                        for (Block sub : subs) {
                           if (!merged.contains(sub)) {
                              merged.add(sub);
                           }
                        }

                        substitutesByTarget.put(target, merged);
                     }
                  }
               }
            }
         }
      }
   }

   private static Block resolveStrict(String token) {
      if (token != null && !token.isEmpty()) {
         if (token.contains(":")) {
            try {
               Identifier id = IdentifierUtils.of(token);
               return BlockUtils.getBlock(id);
            } catch (Exception var7) {
               return null;
            }
         } else {
            Block byPath = null;
            int pathHits = 0;

            for (Block block : BuiltInRegistries.BLOCK) {
               if (BuiltInRegistries.BLOCK.getKey(block).getPath().equals(token)) {
                  byPath = block;
                  pathHits++;
               }
            }

            if (pathHits == 1) {
               return byPath;
            } else if (pathHits > 1) {
               return null;
            } else {
               Block byName = null;
               int nameHits = 0;

               for (Block blockx : BuiltInRegistries.BLOCK) {
                  if (blockx.getName().getString().equals(token)) {
                     byName = blockx;
                     nameHits++;
                  }
               }

               return nameHits == 1 ? byName : null;
            }
         }
      } else {
         return null;
      }
   }
}
