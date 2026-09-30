package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.EndRodBlock;
import net.minecraft.world.level.block.state.BlockState;

public class RodGuide extends Guide {
   public RodGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, EndRodBlock.FACING).orElseThrow();
      if (this.requiredBlock instanceof EndRodBlock) {
         BlockPos forwardPos = this.blockPos.relative(facing);
         BlockState forwardState = this.level.getBlockState(forwardPos);
         if (forwardState.is(this.requiredBlock) && getProperty(forwardState, EndRodBlock.FACING).orElseThrow() == facing.getOpposite()) {
            return Result.success(new Action().setSides(facing));
         }

         BlockState forwardSchematic = LitematicaUtils.getSchematicBlockState(forwardPos);
         if (forwardSchematic != null
            && forwardSchematic.is(this.requiredBlock)
            && ConfigUtils.isPositionInSelectionRange(this.client.player, forwardPos, Configs.Print.PRINT_SELECTION_TYPE)
            && getProperty(forwardSchematic, EndRodBlock.FACING).orElseThrow() == facing) {
            if (statesEqual(forwardSchematic, forwardState)) {
               return Result.success(new Action().setSides(facing.getOpposite()));
            }

            return Result.SKIP;
         }
      }

      return Result.success(new Action().setSides(facing.getOpposite()));
   }
}
