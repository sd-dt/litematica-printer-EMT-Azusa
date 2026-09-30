package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class FenceGateGuide extends Guide {
   public FenceGateGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, FenceGateBlock.FACING).orElse(null);
      return Result.success(new Action().setLookDirection(facing));
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, FenceGateBlock.FACING).orElseThrow();
      Direction currentFacing = (Direction)getProperty(this.currentState, BlockStateProperties.HORIZONTAL_FACING).orElse(null);
      boolean openMismatch = getProperty(this.requiredState, BlockStateProperties.OPEN)
         .map(open -> !open.equals(getProperty(this.currentState, BlockStateProperties.OPEN).orElse(null)))
         .orElse(false);
      return facing.getOpposite() != currentFacing && !openMismatch
         ? Result.SKIP
         : Result.success(new ClickAction().setSides(new Direction[]{facing.getOpposite()}).setLookDirection(facing));
   }
}
