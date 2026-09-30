package me.aleksilassila.litematica.printer.guide.guides;

import java.util.Optional;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;

public class CauldronGuide extends Guide {
   public CauldronGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!this.requiredState.is(Blocks.WATER_CAULDRON)) {
         return Result.SKIP;
      } else {
         Optional<Integer> currentLevel = getProperty(this.currentState, LayeredCauldronBlock.LEVEL);
         Optional<Integer> requiredLevel = getProperty(this.requiredState, LayeredCauldronBlock.LEVEL);
         if (!currentLevel.isEmpty() && !requiredLevel.isEmpty()) {
            if (currentLevel.get() > requiredLevel.get() && InventoryUtils.playerHasAccessToItem(this.client.player, Items.GLASS_BOTTLE)) {
               return Result.success(new ClickAction().setItem(Items.GLASS_BOTTLE));
            } else {
               return currentLevel.get() < requiredLevel.get() ? this.buildWaterFillAction(requiredLevel.get()) : Result.SKIP;
            }
         } else {
            return Result.SKIP;
         }
      }
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      if (this.requiredState.is(Blocks.WATER_CAULDRON) && this.currentState.is(Blocks.CAULDRON)) {
         int requiredLevel = getProperty(this.requiredState, LayeredCauldronBlock.LEVEL).orElse(1);
         return this.buildWaterFillAction(requiredLevel);
      } else if (this.requiredState.is(Blocks.CAULDRON)
         && this.currentState.is(Blocks.WATER_CAULDRON)
         && InventoryUtils.playerHasAccessToItem(this.client.player, Items.GLASS_BOTTLE)) {
         return Result.success(new ClickAction().setItem(Items.GLASS_BOTTLE));
      } else {
         if (Configs.Print.FILL_CAULDRON.getBooleanValue()) {
            Result fillResult = this.buildFillOrScoopAction();
            if (fillResult != null) {
               return fillResult;
            }
         }

         if (isCauldronFamily(this.currentState)) {
            return Result.SKIP;
         } else {
            if (Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue() && BreakUtils.canBreakBlock(this.blockPos)) {
               BreakUtils.INSTANCE.add(this.context);
            }

            return Result.SKIP;
         }
      }
   }

   private Result buildFillOrScoopAction() {
      if (this.requiredState.is(Blocks.LAVA_CAULDRON)) {
         return this.currentState.is(Blocks.CAULDRON) && InventoryUtils.playerHasAccessToItem(this.client.player, Items.LAVA_BUCKET)
            ? Result.success(new ClickAction().setItem(Items.LAVA_BUCKET))
            : this.buildScoopAction();
      } else if (this.requiredState.is(Blocks.POWDER_SNOW_CAULDRON)) {
         return this.currentState.is(Blocks.CAULDRON) && InventoryUtils.playerHasAccessToItem(this.client.player, Items.POWDER_SNOW_BUCKET)
            ? Result.success(new ClickAction().setItem(Items.POWDER_SNOW_BUCKET))
            : this.buildScoopAction();
      } else {
         return !this.requiredState.is(Blocks.CAULDRON) && !this.requiredState.is(Blocks.WATER_CAULDRON) ? null : this.buildScoopAction();
      }
   }

   private Result buildScoopAction() {
      if (this.currentState.is(Blocks.WATER_CAULDRON) && InventoryUtils.playerHasAccessToItem(this.client.player, Items.GLASS_BOTTLE)) {
         return Result.success(new ClickAction().setItem(Items.GLASS_BOTTLE));
      } else {
         return (this.currentState.is(Blocks.LAVA_CAULDRON) || this.currentState.is(Blocks.POWDER_SNOW_CAULDRON))
               && InventoryUtils.playerHasAccessToItem(this.client.player, Items.BUCKET)
            ? Result.success(new ClickAction().setItem(Items.BUCKET))
            : null;
      }
   }

   private static boolean isCauldronFamily(BlockState state) {
      return state.getBlock() instanceof AbstractCauldronBlock;
   }

   private Result buildWaterFillAction(int requiredLevel) {
      if (requiredLevel == 3 && InventoryUtils.playerHasItemInInventory(this.client.player, Items.WATER_BUCKET)) {
         return Result.success(new ClickAction().setItem(Items.WATER_BUCKET));
      } else {
         ItemStack waterPotion = InventoryUtils.createWaterPotionStack();
         return InventoryUtils.playerHasAccessToMatchingStack(this.client.player, waterPotion, InventoryUtils::isWaterPotion)
            ? Result.success(
               new ClickAction().setItem(Items.POTION).setRequiredStackPredicate(InventoryUtils::isWaterPotion).setRequiredCreativeStack(waterPotion)
            )
            : Result.SKIP;
      }
   }
}
