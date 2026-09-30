package me.aleksilassila.litematica.printer.guide.guides;

import java.util.Optional;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public class CropsGuide extends Guide {
   public CropsGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      String blockKey = BlockUtils.getKeyString(this.requiredBlock);
      if (blockKey.contains("pumpkin")) {
         return Result.success(new Action().setItem(Items.PUMPKIN_SEEDS).setRequiresSupport());
      } else {
         return blockKey.contains("melon") ? Result.success(new Action().setItem(Items.MELON_SEEDS).setRequiresSupport()) : Result.success(new Action());
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!Configs.Print.BONEMEAL_CROPS.getBooleanValue()) {
         return Result.PASS;
      } else {
         Direction facing = (Direction)getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_FACING).orElse(null);
         if (!(this.requiredBlock instanceof StemBlock) && !(this.requiredBlock instanceof AttachedStemBlock)) {
            if (this.currentBlock == this.requiredBlock && InventoryUtils.playerHasAccessToItem(this.client.player, Items.BONE_MEAL)) {
               IntegerProperty ageProp;
               int maxAge;
               if (this.requiredBlock instanceof BeetrootBlock) {
                  ageProp = BeetrootBlock.AGE;
                  maxAge = 3;
               } else {
                  if (!(this.requiredBlock instanceof CropBlock cropBlock)) {
                     return Result.SKIP;
                  }

                  ageProp = CropBlock.AGE;
                  maxAge = cropBlock.getMaxAge();
               }

               int requiredAge = getProperty(this.requiredState, ageProp).orElse(0);
               int currentAge = getProperty(this.currentState, ageProp).orElse(0);
               if (requiredAge == maxAge && currentAge < maxAge) {
                  return Result.success(
                     new ClickAction()
                        .setItem(Items.BONE_MEAL)
                        .setConsumeEffectiveExecution(false)
                        .setClickRepeatCount(Configs.Print.BONEMEAL_CROPS_CLICKS.getIntegerValue())
                        .setCooldownTicksOverride(1)
                  );
               }
            }

            return Result.SKIP;
         } else {
            return facing != null
                  && this.currentState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                  && !getProperty(this.currentState, BlockStateProperties.HORIZONTAL_FACING).equals(Optional.of(facing))
               ? Result.PASS
               : Result.SKIP;
         }
      }
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      String requiredKey = BlockUtils.getKeyString(this.requiredBlock);
      String currentKey = BlockUtils.getKeyString(this.currentBlock);
      boolean wrongStem = requiredKey.contains("pumpkin_stem") && !currentKey.contains("pumpkin_stem")
         || requiredKey.contains("melon_stem") && !currentKey.contains("melon_stem");
      if (wrongStem
         && Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue()
         && BreakUtils.canBreakBlock(this.blockPos)
         && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
         BreakUtils.INSTANCE.add(this.context);
         return Result.SKIP;
      } else {
         return Result.PASS;
      }
   }
}
