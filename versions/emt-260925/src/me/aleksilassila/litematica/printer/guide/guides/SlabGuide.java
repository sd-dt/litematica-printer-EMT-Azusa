package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Plane;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

public class SlabGuide extends Guide {
   private final SlabType slabType = (SlabType)getProperty(this.requiredState, SlabBlock.TYPE).orElseThrow();

   public SlabGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      if (this.slabType == SlabType.DOUBLE && state == BlockMatchResult.WRONG_STATE) {
         return Result.SKIP;
      } else if (this.slabType == SlabType.DOUBLE && state == BlockMatchResult.MISSING) {
         Map<Direction, Vec3> slabSides = BlockUtils.getSlabSides(this.level, this.blockPos, SlabType.BOTTOM);
         return Result.success(new Action().setSides(slabSides));
      } else {
         Map<Direction, Vec3> sides = new HashMap<>();
         Direction half;
         if (this.slabType == SlabType.TOP) {
            half = Direction.UP;
         } else if (this.slabType == SlabType.BOTTOM) {
            half = Direction.DOWN;
         } else {
            half = Direction.DOWN;
         }

         sides.put(half, Vec3.ZERO);

         for (Direction side : Plane.HORIZONTAL) {
            BlockPos neighborPos = this.blockPos.relative(side);
            BlockState neighborState = this.level.getBlockState(neighborPos);
            if (neighborState.hasProperty(SlabBlock.TYPE)) {
               SlabType neighborType = getProperty(neighborState, SlabBlock.TYPE).orElse(SlabType.BOTTOM);
               if (neighborType != SlabType.DOUBLE && neighborType != this.slabType) {
                  continue;
               }
            }

            sides.put(side, Vec3.atLowerCornerOf(BlockUtils.getVector(half)).scale(0.25));
         }

         return Result.success(new Action().setSides(sides));
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (this.slabType == SlabType.DOUBLE && this.currentState.hasProperty(SlabBlock.TYPE)) {
         SlabType current = getProperty(this.currentState, SlabBlock.TYPE).orElse(SlabType.BOTTOM);
         Direction clickFace = current == SlabType.BOTTOM ? Direction.UP : Direction.DOWN;
         return Result.success(new ClickAction().setSides(new Direction[]{clickFace}).setItem(this.requiredBlock.asItem()));
      } else {
         return Result.SKIP;
      }
   }
}
