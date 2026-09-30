package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;

public class FlowerPotGuide extends Guide {
   public FlowerPotGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      return Result.success(new Action().setItem(Items.FLOWER_POT));
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      if (this.requiredBlock instanceof FlowerPotBlock potBlock) {
         Block content = potBlock.getPotted();
         if (content != Blocks.AIR) {
            return Result.success(new ClickAction().setItem(content.asItem()));
         }
      }

      if (Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
         && BreakUtils.canBreakBlock(this.blockPos)
         && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
         BreakUtils.INSTANCE.add(this.context);
      }

      return Result.SKIP;
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.SKIP;
   }
}
