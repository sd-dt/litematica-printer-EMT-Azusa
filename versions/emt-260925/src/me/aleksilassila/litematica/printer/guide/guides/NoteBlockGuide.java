package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.NoteBlock;

public class NoteBlockGuide extends Guide {
   public NoteBlockGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Configs.Print.NOTE_BLOCK_TUNING.getBooleanValue()
            && !getProperty(this.requiredState, NoteBlock.NOTE).equals(getProperty(this.currentState, NoteBlock.NOTE))
         ? Result.success(new ClickAction())
         : Result.SKIP;
   }
}
