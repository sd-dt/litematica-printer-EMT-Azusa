package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SkullGuide extends Guide {
   public SkullGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, BlockStateProperties.FACING).orElse(null);
      if (this.requiredBlock instanceof SkullBlock) {
         int rotation = getProperty(this.requiredState, SkullBlock.ROTATION).orElseThrow();
         return Result.success(new Action().setSides(Direction.DOWN).setLookRotation(BlockUtils.getOppositeRotation(rotation)).setRequiresSupport());
      } else {
         return this.requiredBlock instanceof WallSkullBlock && facing != null
            ? Result.success(new Action().setSides(facing.getOpposite()).setLookDirection(facing.getOpposite()).setRequiresSupport())
            : Result.SKIP;
      }
   }
}
