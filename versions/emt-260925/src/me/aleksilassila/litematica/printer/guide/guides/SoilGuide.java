package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.Reference;
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
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.FarmlandBlock;

public class SoilGuide extends Guide {
   public SoilGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      if (this.requiredBlock instanceof FarmlandBlock) {
         return Result.success(new Action().setItems(Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT));
      } else {
         return this.requiredBlock instanceof DirtPathBlock
            ? Result.success(new Action().setItems(Items.DIRT, Items.GRASS_BLOCK, Items.COARSE_DIRT, Items.ROOTED_DIRT, Items.MYCELIUM, Items.PODZOL))
            : Result.SKIP;
      }
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      if (this.requiredBlock instanceof FarmlandBlock) {
         Block[] soilBlocks = new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.DIRT_PATH, Blocks.COARSE_DIRT};

         for (Block soilBlock : soilBlocks) {
            if (this.currentBlock.equals(soilBlock)) {
               return Result.success(new ClickAction().setItems(Reference.HOE_ITEMS));
            }
         }
      }

      if (this.requiredBlock instanceof DirtPathBlock) {
         Block[] soilBlocks = new Block[]{Blocks.GRASS_BLOCK, Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT, Blocks.MYCELIUM, Blocks.PODZOL};

         for (Block soilBlockx : soilBlocks) {
            if (this.currentBlock.equals(soilBlockx)) {
               return Result.success(new ClickAction().setItems(Reference.SHOVEL_ITEMS));
            }
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
