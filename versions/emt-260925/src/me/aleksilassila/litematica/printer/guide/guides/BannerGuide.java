package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallTorchBlock;

public class BannerGuide extends Guide {
   public BannerGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, WallTorchBlock.FACING).orElse(null);
      if (this.requiredBlock instanceof BannerBlock) {
         int rotation = getProperty(this.requiredState, BannerBlock.ROTATION).orElseThrow();
         return Result.success(new Action().setSides(Direction.DOWN).setLookRotation(rotation).setRequiresSupport());
      } else {
         return this.requiredBlock instanceof WallBannerBlock && facing != null
            ? Result.success(new Action().setSides(facing.getOpposite()).setLookDirection(facing.getOpposite()).setRequiresSupport())
            : Result.SKIP;
      }
   }
}
