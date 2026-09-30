package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LilyPadBlock;

public class LilyPadGuide extends Guide {
   public LilyPadGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected boolean canExecute() {
      return this.requiredBlock instanceof LilyPadBlock;
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      return Result.success(new Action().setSides(Direction.DOWN).setFixedSide(Direction.DOWN).setRequiresSupport());
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      return Result.PASS;
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.PASS;
   }
}
