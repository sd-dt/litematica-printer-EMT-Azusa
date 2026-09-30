package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HopperBlock;

public class HopperGuide extends Guide {
   public HopperGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction hopperFacing = (Direction)getProperty(this.requiredState, HopperBlock.FACING).orElseThrow();
      return Result.success(new Action().setSides(hopperFacing));
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      Direction requiredFacing = (Direction)getProperty(this.requiredState, HopperBlock.FACING).orElse(null);
      Direction currentFacing = (Direction)getProperty(this.currentState, HopperBlock.FACING).orElse(null);
      if (requiredFacing != null
         && currentFacing != requiredFacing
         && Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
         && Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue()
         && BreakUtils.canBreakBlock(this.blockPos)
         && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
         BreakUtils.INSTANCE.add(this.context);
      }

      return Result.SKIP;
   }
}
