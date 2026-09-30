package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.TwistingVinesPlantBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;

public class ClimbingPlantGuide extends Guide {
   public ClimbingPlantGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      if (this.requiredBlock instanceof BigDripleafStemBlock) {
         return Result.success(new Action().setItem(Items.BIG_DRIPLEAF));
      } else if (this.requiredBlock instanceof CaveVinesBlock || this.requiredBlock instanceof CaveVinesPlantBlock) {
         return Result.success(new Action().setItem(Items.GLOW_BERRIES).setRequiresSupport());
      } else if (this.requiredBlock instanceof WeepingVinesBlock || this.requiredBlock instanceof WeepingVinesPlantBlock) {
         return Result.success(new Action().setItem(Items.WEEPING_VINES).setRequiresSupport());
      } else {
         return !(this.requiredBlock instanceof TwistingVinesBlock) && !(this.requiredBlock instanceof TwistingVinesPlantBlock)
            ? Result.SKIP
            : Result.success(new Action().setItem(Items.TWISTING_VINES).setRequiresSupport());
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.SKIP;
   }
}
