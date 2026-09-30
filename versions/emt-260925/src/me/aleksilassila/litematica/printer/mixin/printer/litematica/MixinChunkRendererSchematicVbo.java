package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.render.schematic.ChunkMeshDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDataSchematic;
import fi.dy.masa.litematica.render.schematic.ChunkRenderDispatcherBuffers;
import fi.dy.masa.litematica.render.schematic.ChunkRendererSchematicVbo;
import fi.dy.masa.litematica.util.OverlayType;
import me.aleksilassila.litematica.printer.printer.RenderOnlyBlockCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
   value = {ChunkRendererSchematicVbo.class},
   remap = false
)
public class MixinChunkRendererSchematicVbo {
   @Inject(
      method = {"addBlockEntity"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void printer$filterAddBlockEntity(BlockState state, BlockPos pos, ChunkMeshDataSchematic chunkMeshData, CallbackInfo ci) {
      if (!RenderOnlyBlockCache.shouldRender(state)) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void printer$filterRenderOverlay(
      OverlayType type,
      BlockPos pos,
      BlockState state,
      boolean missing,
      ChunkRenderDataSchematic data,
      ChunkMeshDataSchematic chunkMeshData,
      ChunkRenderDispatcherBuffers pack,
      CallbackInfo ci
   ) {
      if (!RenderOnlyBlockCache.shouldRender(state) && type == OverlayType.MISSING) {
         ci.cancel();
      }
   }
}
