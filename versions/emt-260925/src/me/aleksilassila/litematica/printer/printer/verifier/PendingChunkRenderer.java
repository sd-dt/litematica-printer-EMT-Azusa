package me.aleksilassila.litematica.printer.printer.verifier;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.litematica.config.Configs.InfoOverlays;
import fi.dy.masa.litematica.config.Configs.Visuals;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.verifier.SchematicVerifier;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.SchematicPlacementAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class PendingChunkRenderer implements IRenderer {
   private static final int MAX_BOXES_PER_FRAME = 16384;
   private static final Color4f GREEN = Color4f.fromColor(5635925, 1.0F);
   private static final float LINE_WIDTH = 2.0F;
   private static PendingChunkRenderer instance;

   public static void init() {
      if (instance == null) {
         instance = new PendingChunkRenderer();
         RenderEventHandler.getInstance().registerWorldLastRenderer(instance);
      }
   }

   public void onRenderWorldLast(
      RenderTarget fb,
      Matrix4fc matrices,
      CameraRenderState cameraState,
      Frustum culling,
      RenderBuffers buffers,
      GpuBufferSlice terrainFog,
      Vector4f fogColor,
      ProfilerFiller profiler
   ) {
      this.render();
   }

   private void render() {
      if (Configs.Core.VERIFIER_OPTIMIZED.getBooleanValue()) {
         if (InfoOverlays.VERIFIER_OVERLAY_ENABLED.getBooleanValue() && Visuals.ENABLE_RENDERING.getBooleanValue()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.level != null) {
               int totalBoxes = 0;

               for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicsPlacements()) {
                  SchematicVerifier verifier = ((SchematicPlacementAccessor)placement).printer$getVerifier();
                  if (verifier instanceof OptimizedSchematicVerifier) {
                     OptimizedSchematicVerifier optimized = (OptimizedSchematicVerifier)verifier;
                     if (optimized.isScanStarted() && !optimized.isFinished() && optimized.isBoundToCurrentDimension()) {
                        long[] snapshot = optimized.getPendingSnapshot();
                        if (snapshot.length != 0) {
                           totalBoxes += this.renderPlacement(optimized, snapshot, mc);
                           if (totalBoxes >= 16384) {
                              break;
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private int renderPlacement(OptimizedSchematicVerifier verifier, long[] snapshot, Minecraft mc) {
      int yMin = verifier.getRenderMinY();
      int yMax = verifier.getRenderMaxY();
      int cap = Math.min(16384, snapshot.length);
      Vec3 cam = RenderUtils.camPos();
      RenderContext ctx = new RenderContext(() -> "litematica_printer:pending_chunks", MaLiLibPipelines.DEBUG_LINES_MASA_SIMPLE_NO_DEPTH_NO_CULL, 0);

      try {
         BufferBuilder buffer = ctx.getBuilder();

         for (int i = 0; i < cap; i++) {
            long key = snapshot[i];
            float x0 = (float)(OptimizedSchematicVerifier.chunkX(key) << 4) - (float)cam.x;
            float z0 = (float)(OptimizedSchematicVerifier.chunkZ(key) << 4) - (float)cam.z;
            float x1 = x0 + 16.0F;
            float z1 = z0 + 16.0F;
            float y0 = (float)yMin - (float)cam.y;
            float y1 = (float)yMax - (float)cam.y;
            RenderUtils.drawBoxAllEdgesBatchedLines(x0, y0, z0, x1, y1, z1, GREEN, 2.0F, buffer);
         }

         MeshData mesh = buffer.build();
         ctx.draw(mesh, false, true);
         mesh.close();
      } catch (Exception var22) {
         Reference.LOGGER.error("Pending chunk rendering failed: {}", var22.getLocalizedMessage());
      } finally {
         ctx.reset();
      }

      return cap;
   }
}
