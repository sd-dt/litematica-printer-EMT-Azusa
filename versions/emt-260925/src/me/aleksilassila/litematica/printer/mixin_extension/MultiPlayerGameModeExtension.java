package me.aleksilassila.litematica.printer.mixin_extension;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;

public interface MultiPlayerGameModeExtension {
   InteractionResult litematica_printer$useItemOn(boolean var1, InteractionHand var2, BlockHitResult var3);

   default BlockBreakResult litematica_printer$continueDestroyBlock(boolean localPrediction, BlockPos blockPos, Direction direction) {
      return this.litematica_printer$continueDestroyBlock(localPrediction, blockPos, direction, false);
   }

   default BlockBreakResult litematica_printer$continueDestroyBlock(
      boolean localPrediction, BlockPos blockPos, Direction direction, boolean forceDelayedDestroy
   ) {
      return this.litematica_printer$continueDestroyBlock(localPrediction, blockPos, direction, forceDelayedDestroy, true);
   }

   BlockBreakResult litematica_printer$continueDestroyBlock(boolean var1, BlockPos var2, Direction var3, boolean var4, boolean var5);

   default BlockBreakResult litematica_printer$continueDestroyBlockForMine(BlockPos blockPos, Direction direction) {
      return this.litematica_printer$continueDestroyBlockForMine(blockPos, direction, true);
   }

   default BlockBreakResult litematica_printer$continueDestroyBlockForMine(BlockPos blockPos, Direction direction, boolean allowToolSwitch) {
      return this.litematica_printer$continueDestroyBlock(false, blockPos, direction, false, allowToolSwitch);
   }

   default boolean litematica_printer$isPendingDelayedDestroy(BlockPos blockPos) {
      return false;
   }

   default void litematica_printer$resetRuntime() {
   }

   void litematica_printer$startPrediction(MultiPlayerGameModeExtension.PredictiveAction var1);

   BlockPos litematica_printer$destroyBlockPos();

   boolean litematica_printer$isDestroying();

   @FunctionalInterface
   public interface PredictiveAction {
      Packet<ServerGamePacketListener> predict(int var1);
   }
}
