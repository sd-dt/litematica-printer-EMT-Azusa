package me.aleksilassila.litematica.printer.mixin.printer.mc;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.SchematicStateCache;
import me.aleksilassila.litematica.printer.utils.PacketUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ClientLevel.class})
public abstract class MixinClientLevel implements PacketUtils.SequenceExtension {
   @Final
   @Shadow
   private BlockStatePredictionHandler blockStatePredictionHandler;

   @Override
   public int litematica_printer3$getSequence() {
      BlockStatePredictionHandler pendingUpdateManager = this.blockStatePredictionHandler;

      int var2;
      try {
         var2 = pendingUpdateManager.currentSequence();
      } catch (Throwable var5) {
         if (pendingUpdateManager != null) {
            try {
               pendingUpdateManager.close();
            } catch (Throwable var4) {
               var5.addSuppressed(var4);
            }
         }

         throw var5;
      }

      if (pendingUpdateManager != null) {
         pendingUpdateManager.close();
      }

      return var2;
   }

   @Inject(
      method = {"playLocalSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void suppressPrinterPlacementSound(Entity entity, SoundEvent sound, SoundSource source, float volume, float pitch, CallbackInfo ci) {
      if (source == SoundSource.BLOCKS && ActionManager.INSTANCE.isPrintInteractionActive() && !Configs.Print.PRINT_SOUND.getBooleanValue()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},
      at = {@At("RETURN")}
   )
   private void litematica_printer$onWorldSetBlock(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
      if (cir.getReturnValueZ()) {
         if (!((Object)this instanceof WorldSchematic)) {
            SchematicStateCache.INSTANCE.onWorldBlockChanged(pos);
         }
      }
   }
}
