package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.SoulFireBlock;

public class FireGuide extends Guide {
   public FireGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      return Result.success(new Action().setSides(this.findFireDirection()).setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE).setRequiresSupport());
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!getProperty(this.requiredState, FireBlock.AGE).equals(getProperty(this.currentState, FireBlock.AGE))) {
         return Result.SKIP;
      } else {
         return this.requiredBlock instanceof SoulFireBlock ? Result.SKIP : Result.PASS;
      }
   }

   private Direction findFireDirection() {
      for (Direction direction : Direction.values()) {
         if (direction != Direction.DOWN) {
            Object value = BlockUtils.getPropertyByName(this.requiredState, direction.name());
            if (value instanceof Boolean && (Boolean)value) {
               return direction;
            }
         }
      }

      return Direction.DOWN;
   }
}
