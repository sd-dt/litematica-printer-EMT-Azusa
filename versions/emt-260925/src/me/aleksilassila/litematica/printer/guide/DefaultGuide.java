package me.aleksilassila.litematica.printer.guide;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import net.minecraft.world.level.block.LiquidBlock;

public class DefaultGuide extends Guide {
   public DefaultGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      return Result.success(this.buildTargetStateContext(new Action()));
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      boolean printBreakWrongBlock = Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue();
      boolean printBreakExtraBlock = Configs.Print.BREAK_EXTRA_BLOCK.getBooleanValue();
      if ((printBreakWrongBlock || printBreakExtraBlock)
         && BreakUtils.canBreakBlock(this.blockPos)
         && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
         if (printBreakWrongBlock && !this.requiredState.isAir()) {
            BreakUtils.INSTANCE.add(this.context);
         } else if (printBreakExtraBlock && this.requiredState.isAir() && !(this.currentState.getBlock() instanceof LiquidBlock)) {
            BreakUtils.INSTANCE.add(this.context);
         }
      }

      return Result.PASS;
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
         && Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue()
         && BlockStateUtils.hasFixableStateDifference(this.requiredState, this.currentState)
         && BreakUtils.canBreakBlock(this.blockPos)
         && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
         BreakUtils.INSTANCE.add(this.context);
      }

      return Result.PASS;
   }
}
