package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.RepeaterBlock;

public class RepeaterGuide extends Guide {
   public RepeaterGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return !getProperty(this.requiredState, RepeaterBlock.DELAY).equals(getProperty(this.currentState, RepeaterBlock.DELAY))
         ? Result.success(new ClickAction())
         : Result.SKIP;
   }
}
