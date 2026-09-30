package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.ComparatorBlock;

public class ComparatorGuide extends Guide {
   public ComparatorGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return !getProperty(this.requiredState, ComparatorBlock.MODE).equals(getProperty(this.currentState, ComparatorBlock.MODE))
         ? Result.success(new ClickAction())
         : Result.SKIP;
   }
}
