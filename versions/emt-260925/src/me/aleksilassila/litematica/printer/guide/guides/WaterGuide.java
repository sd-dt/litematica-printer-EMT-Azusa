package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class WaterGuide extends Guide {
   public WaterGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected boolean canExecute() {
      return BlockStateUtils.isWaterBlock(this.requiredState);
   }

   @Override
   protected Result onBuildAction(BlockMatchResult state) {
      if (state == BlockMatchResult.WRONG_BLOCK) {
         return Result.PASS;
      } else {
         return this.isWaterloggedTarget() ? Result.PASS : Result.SKIP;
      }
   }

   @Override
   protected Result onBuildActionCorrect(BlockMatchResult state) {
      return this.isWaterloggedTarget() ? Result.PASS : Result.SKIP;
   }

   private boolean isWaterloggedTarget() {
      return this.requiredState.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean)this.requiredState.getValue(BlockStateProperties.WATERLOGGED);
   }
}
