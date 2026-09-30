package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.render.schematic.BlockModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.FluidModelRendererSchematic;
import fi.dy.masa.litematica.render.schematic.IBlockOutputSchematic;
import fi.dy.masa.litematica.render.schematic.WorldRendererSchematic;
import me.aleksilassila.litematica.printer.printer.RenderOnlyBlockCache;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer.Output;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {WorldRendererSchematic.class},
   remap = false
)
public class MixinWorldRendererSchematic {
   @Inject(
      method = {"renderBlock"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void printer$filterRenderBlock(
      BlockModelRendererSchematic modelRenderer,
      BlockAndTintGetter world,
      BlockState state,
      BlockPos pos,
      Vec3 cameraPos,
      IBlockOutputSchematic output,
      CallbackInfoReturnable<Boolean> cir
   ) {
      if (!RenderOnlyBlockCache.shouldRender(state)) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"renderFluid"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void printer$filterRenderFluid(
      FluidModelRendererSchematic fluidRenderer,
      BlockAndTintGetter world,
      BlockState state,
      FluidState fluidState,
      BlockPos pos,
      Output output,
      float partialTick,
      CallbackInfoReturnable<Boolean> cir
   ) {
      if (!RenderOnlyBlockCache.shouldRender(state)) {
         cir.setReturnValue(false);
      }
   }
}
