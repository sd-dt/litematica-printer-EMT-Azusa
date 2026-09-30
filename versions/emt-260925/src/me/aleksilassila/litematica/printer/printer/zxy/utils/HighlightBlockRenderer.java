package me.aleksilassila.litematica.printer.printer.zxy.utils;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import fi.dy.masa.malilib.config.options.ConfigColor;
import fi.dy.masa.malilib.event.RenderEventHandler;
import fi.dy.masa.malilib.interfaces.IRenderer;
import fi.dy.masa.malilib.render.MaLiLibPipelines;
import fi.dy.masa.malilib.render.RenderContext;
import fi.dy.masa.malilib.render.RenderUtils;
import fi.dy.masa.malilib.util.data.Color4f;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import me.aleksilassila.litematica.printer.config.Configs;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.Disconnect;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class HighlightBlockRenderer implements IRenderer {
   public static HighlightBlockRenderer instance = new HighlightBlockRenderer();
   public static Map<String, HighlightBlockRenderer.HighlightTheProject> highlightTheProjectMap = new HashMap<>();
   public static String threadName = "litematica-printer-render";
   public static boolean shaderIng = false;
   public static List<String> clearList = new LinkedList<>();
   public static Map<String, Set<BlockPos>> setMap = new HashMap<>();

   public static void createHighlightBlockList(String id, ConfigColor color4f) {
      if (highlightTheProjectMap.get(id) == null) {
         highlightTheProjectMap.put(id, new HighlightBlockRenderer.HighlightTheProject(color4f, new LinkedHashSet<>()));
      }
   }

   public static Set<BlockPos> getHighlightBlockPosList(String id) {
      return highlightTheProjectMap.get(id) != null ? highlightTheProjectMap.get(id).pos() : null;
   }

   public static void clear(String id) {
      if (!clearList.contains(id)) {
         clearList.add(id);
      }
   }

   public static void setPos(String id, Set<BlockPos> posSet) {
      HighlightBlockRenderer.HighlightTheProject highlightTheProject = highlightTheProjectMap.get(id);
      if (highlightTheProject != null && posSet != null) {
         setMap.put(id, posSet);
      }
   }

   public static void init() {
      RenderEventHandler.getInstance().registerWorldLastRenderer(instance);
      ClientPlayConnectionEvents.DISCONNECT.register((Disconnect)(handler, client1) -> {
         for (Entry<String, HighlightBlockRenderer.HighlightTheProject> stringHighlightTheProjectEntry : highlightTheProjectMap.entrySet()) {
            stringHighlightTheProjectEntry.getValue().pos.clear();
         }
      });
   }

   public void test3(Matrix4fc matrices, Color4f color4f, Set<BlockPos> posSet) {
      RenderSystem.setShaderFog(RenderSystem.getShaderFog());
      RenderContext ctx = new RenderContext(() -> threadName, MaLiLibPipelines.POSITION_COLOR_TRANSLUCENT, 0);
      BufferBuilder buffer = ctx.getBuilder();
      Vec3 camPos = RenderUtils.camPos();
      int renderDistance = Configs.Special.SYNC_HIGHLIGHT_RENDER_DISTANCE.getIntegerValue();
      double maxDistSq = (double)renderDistance * (double)renderDistance;

      for (BlockPos pos : posSet) {
         if (renderDistance > 0) {
            double dx = (double)pos.getX() + 0.5 - camPos.x;
            double dy = (double)pos.getY() + 0.5 - camPos.y;
            double dz = (double)pos.getZ() + 0.5 - camPos.z;
            if (dx * dx + dy * dy + dz * dz > maxDistSq) {
               continue;
            }
         }

         RenderUtils.renderAreaSidesBatched(pos, pos, color4f, 0.002, buffer);
      }

      try {
         if (buffer != null) {
            MeshData meshData = buffer.buildOrThrow();
            ctx.upload(meshData, true);
            ctx.startResorting(meshData, ctx.createVertexSorter(RenderUtils.camPos()));
            meshData.close();
            ctx.drawPost();
            ctx.close();
         }
      } catch (Exception var19) {
      }

      RenderSystem.setShaderFog(RenderSystem.getShaderFog());
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
      setMap.forEach((k, v) -> {
         HighlightBlockRenderer.HighlightTheProject highlightTheProjectx = highlightTheProjectMap.get(k);
         if (highlightTheProjectx != null) {
            highlightTheProjectx.pos.clear();
            highlightTheProjectx.pos.addAll(v);
         }
      });
      setMap.clear();

      for (String string : clearList) {
         HighlightBlockRenderer.HighlightTheProject highlightTheProject = highlightTheProjectMap.get(string);
         if (highlightTheProject != null) {
            highlightTheProject.pos.clear();
         }
      }

      clearList.clear();
      shaderIng = true;
      highlightTheProjectMap.entrySet().stream().forEach(stringHighlightTheProjectEntry -> {
         String key = stringHighlightTheProjectEntry.getKey();
         HighlightBlockRenderer.HighlightTheProject value = stringHighlightTheProjectEntry.getValue();
         Color4f color = value.color4f.getColor();
         this.test3(matrices, color, value.pos);
      });
      shaderIng = false;
   }

   public static record HighlightTheProject(ConfigColor color4f, Set<BlockPos> pos) {
   }
}
