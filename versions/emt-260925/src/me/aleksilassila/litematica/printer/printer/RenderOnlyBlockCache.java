package me.aleksilassila.litematica.printer.printer;

import fi.dy.masa.litematica.render.LitematicaRenderer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.IdentifierUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public final class RenderOnlyBlockCache {
   private static boolean enabled;
   private static List<String> source = List.of();
   private static final Set<Block> blocks = new HashSet<>();
   private static int changeCounter;
   private static int lastSeenCounter;
   private static boolean initialized;

   private RenderOnlyBlockCache() {
   }

   public static boolean shouldRender(BlockState state) {
      refresh();
      return !enabled ? true : state != null && blocks.contains(state.getBlock());
   }

   public static void tickPendingReload() {
      if (changeCounter != lastSeenCounter) {
         lastSeenCounter = changeCounter;
         if (Minecraft.getInstance().level != null) {
            LitematicaRenderer.getInstance().loadRenderers(null);
         }
      }
   }

   private static void refresh() {
      boolean en = Configs.Special.RENDER_ONLY_BLOCKS.getBooleanValue();
      List<String> cur = Configs.Special.RENDER_ONLY_BLOCK_LIST.getStrings();
      if (en != enabled || !cur.equals(source)) {
         apply(en, cur);
      }
   }

   private static void apply(boolean en, List<String> cur) {
      boolean first = !initialized;
      initialized = true;
      enabled = en;
      source = List.copyOf(cur);
      blocks.clear();
      if (en) {
         for (String line : cur) {
            if (line != null && !line.isBlank()) {
               String normalized = line.replace('，', ',').replace('；', ';').replace('：', ':');

               for (String token : normalized.split("[,;]")) {
                  Block block = resolveStrict(token.trim());
                  if (block != null && block != Blocks.AIR) {
                     blocks.add(block);
                  }
               }
            }
         }
      }

      if (!first) {
         changeCounter++;
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
