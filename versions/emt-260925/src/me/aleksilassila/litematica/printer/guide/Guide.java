package me.aleksilassila.litematica.printer.guide;

import fi.dy.masa.litematica.world.WorldSchematic;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.DecoratedPotBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

public abstract class Guide extends BlockStateUtils {
   protected final SchematicBlockContext context;
   public final Minecraft client;
   public final ClientLevel level;
   public final WorldSchematic schematic;
   public final BlockPos blockPos;
   public final BlockState currentState;
   public final BlockState requiredState;
   protected final Block currentBlock;
   protected final Block requiredBlock;

   public Guide(SchematicBlockContext context) {
      this.context = context;
      this.client = context.client;
      this.level = context.level;
      this.schematic = context.schematic;
      this.blockPos = context.blockPos;
      this.currentBlock = context.currentState.getBlock();
      this.requiredBlock = context.requiredState.getBlock();
      this.currentState = context.currentState;
      this.requiredState = context.requiredState;
   }

   public final Result buildAction(BlockMatchResult state) {
      if (state == BlockMatchResult.CORRECT) {
         return this.onBuildActionCorrect(state);
      } else {
         if (state == BlockMatchResult.MISSING) {
            if (!BlockStateUtils.isWaterBlock(this.requiredState) && !this.requiredState.canSurvive(this.level, this.blockPos)) {
               return Result.PASS;
            }

            if (this.requiredState.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
               && this.requiredState.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
               return Result.PASS;
            }
         }

         if (BlockStateUtils.requiresWaterToPlace(this.requiredBlock) && !BlockStateUtils.hasSourceWaterFluid(this.level.getBlockState(this.blockPos))) {
            return Result.PASS;
         } else {
            Result result = this.onBuildAction(state);
            if (result.passToNext() && !result.skipOtherGuide()) {
               return switch (state) {
                  case MISSING -> this.onBuildActionMissingBlock(state);
                  case WRONG_BLOCK -> this.onBuildActionWrongBlock(state);
                  case WRONG_STATE -> this.onBuildActionWrongState(state);
                  default -> Result.PASS;
               };
            } else {
               return result;
            }
         }
      }
   }

   protected boolean canExecute() {
      return true;
   }

   protected Result onBuildAction(BlockMatchResult state) {
      return Result.PASS;
   }

   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      return Result.PASS;
   }

   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      return Result.PASS;
   }

   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.PASS;
   }

   protected Result onBuildActionCorrect(BlockMatchResult state) {
      return Result.PASS;
   }

   protected Action buildTargetStateContext(Action action) {
      Direction facing = getProperty(this.requiredState, BlockStateProperties.FACING)
         .or(() -> getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_FACING))
         .or(() -> getProperty(this.requiredState, BlockStateProperties.VERTICAL_DIRECTION))
         .or(() -> getProperty(this.requiredState, BlockStateProperties.FACING_HOPPER))
         .orElse(null);
      Axis axis = getProperty(this.requiredState, BlockStateProperties.AXIS)
         .or(() -> getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_AXIS))
         .orElse(null);
      Half half = (Half)getProperty(this.requiredState, BlockStateProperties.HALF).orElse(null);
      AttachFace attachFace = (AttachFace)getProperty(this.requiredState, BlockStateProperties.ATTACH_FACE).orElse(null);
      if (this.requiredBlock instanceof FaceAttachedHorizontalDirectionalBlock && facing != null && attachFace != null) {
         Direction sidePitch = attachFace == AttachFace.CEILING ? Direction.UP : (attachFace == AttachFace.FLOOR ? Direction.DOWN : facing);
         Direction clickSide = attachFace == AttachFace.WALL ? facing : facing.getOpposite();
         return action.setSides(clickSide).setLookDirection(clickSide.getOpposite(), sidePitch).setNeedWaitModifyLook();
      } else {
         if (axis != null) {
            action.setSides(axis);
         }

         if (facing != null && axis == null) {
            if (this.requiredBlock instanceof HorizontalDirectionalBlock
               || this.requiredBlock instanceof StonecutterBlock
               || this.requiredBlock instanceof FlowerBedBlock) {
               action.setLookDirection(facing.getOpposite());
            }

            if (this.requiredBlock instanceof BaseEntityBlock) {
               if (this.requiredState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                  Direction entityFacing = facing;
                  if (this.requiredBlock instanceof DecoratedPotBlock || this.requiredBlock instanceof CampfireBlock) {
                     entityFacing = facing.getOpposite();
                  }

                  action.setSides(entityFacing).setLookDirection(entityFacing.getOpposite());
               }

               if (this.requiredState.hasProperty(BlockStateProperties.FACING)) {
                  Direction entityFacing = facing;
                  if (this.requiredBlock instanceof ShulkerBoxBlock) {
                     entityFacing = facing.getOpposite();
                     action.setShift();
                  }

                  if (this.requiredBlock instanceof BarrelBlock || this.requiredBlock instanceof DispenserBlock) {
                     action.setNeedWaitModifyLook();
                  }

                  action.setSides(entityFacing).setLookDirection(entityFacing.getOpposite());
               }
            }

            if (this.requiredBlock instanceof ObserverBlock || this.requiredBlock instanceof StairBlock || this.requiredBlock instanceof FenceGateBlock) {
               action.setLookDirection(facing);
            } else if (!(this.requiredBlock instanceof HorizontalDirectionalBlock) && !(this.requiredBlock instanceof BaseEntityBlock)) {
               action.setLookDirection(facing.getOpposite());
            }
         }

         if (half != null && facing == null) {
            action.setSides(half == Half.BOTTOM ? Direction.DOWN : Direction.UP);
         }

         return action;
      }
   }
}
