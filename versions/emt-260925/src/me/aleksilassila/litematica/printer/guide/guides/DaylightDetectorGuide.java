package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.level.block.DaylightDetectorBlock;

public class DaylightDetectorGuide extends Guide {
   public DaylightDetectorGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      int requiredPower = getProperty(this.requiredState, DaylightDetectorBlock.POWER).orElseThrow();
      int currentPower = getProperty(this.currentState, DaylightDetectorBlock.POWER).orElseThrow();
      boolean requiredInverted = getProperty(this.requiredState, DaylightDetectorBlock.INVERTED).orElseThrow();
      boolean currentInverted = getProperty(this.currentState, DaylightDetectorBlock.INVERTED).orElseThrow();
      if (requiredPower != currentPower) {
         return Result.SKIP;
      } else {
         return requiredInverted != currentInverted ? Result.success(new ClickAction()) : Result.SKIP;
      }
   }
}
