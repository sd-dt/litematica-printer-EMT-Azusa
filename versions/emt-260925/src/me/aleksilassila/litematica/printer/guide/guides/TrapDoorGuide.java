package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;

public class TrapDoorGuide extends Guide {
   public TrapDoorGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, TrapDoorBlock.FACING).orElse(null);
      Half half = getProperty(this.requiredState, TrapDoorBlock.HALF).orElse(Half.BOTTOM);
      if (facing == null) {
         return Result.SKIP;
      } else {
         Direction side = half == Half.TOP ? Direction.UP : Direction.DOWN;
         return Result.success(new Action().setSides(side).setLookDirection(facing.getOpposite()));
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (this.requiredState.is(Blocks.IRON_TRAPDOOR)) {
         return Result.SKIP;
      } else {
         return !getProperty(this.requiredState, TrapDoorBlock.OPEN).equals(getProperty(this.currentState, BlockStateProperties.OPEN))
            ? Result.success(new ClickAction())
            : Result.SKIP;
      }
   }
}
