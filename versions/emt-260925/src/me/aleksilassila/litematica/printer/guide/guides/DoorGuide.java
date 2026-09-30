package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class DoorGuide extends Guide {
   @Nullable
   private final DoorHingeSide doorHinge = (DoorHingeSide)getProperty(this.requiredState, BlockStateProperties.DOOR_HINGE).orElse(null);
   @Nullable
   private final DoubleBlockHalf doubleBlockHalf = (DoubleBlockHalf)getProperty(this.requiredState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null);

   public DoorGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, DoorBlock.FACING).orElse(null);
      if (facing == null || this.doorHinge == null || this.doubleBlockHalf == null) {
         return Result.PASS;
      } else if (this.doubleBlockHalf == DoubleBlockHalf.UPPER) {
         return Result.PASS;
      } else {
         BlockPos upperPos = this.blockPos.above();
         Direction hingeSide = facing.getCounterClockWise();
         double offset = this.doorHinge == DoorHingeSide.RIGHT ? 0.25 : -0.25;
         Vec3 hingeVec = facing.getAxis() == Axis.X ? new Vec3(0.0, 0.0, offset) : new Vec3(offset, 0.0, 0.0);
         Map<Direction, Vec3> sides = new HashMap<>();
         sides.put(hingeSide, Vec3.ZERO);
         sides.put(Direction.DOWN, hingeVec);
         sides.put(facing, hingeVec);
         Direction left = facing.getCounterClockWise();
         Direction right = facing.getClockWise();
         BlockState leftState = this.level.getBlockState(this.blockPos.relative(left));
         BlockState leftUpperState = this.level.getBlockState(upperPos.relative(left));
         BlockState rightState = this.level.getBlockState(this.blockPos.relative(right));
         BlockState rightUpperState = this.level.getBlockState(upperPos.relative(right));
         int occupancy = (leftState.isCollisionShapeFullBlock(this.level, this.blockPos.relative(left)) ? -1 : 0)
            + (leftUpperState.isCollisionShapeFullBlock(this.level, upperPos.relative(left)) ? -1 : 0)
            + (rightState.isCollisionShapeFullBlock(this.level, this.blockPos.relative(right)) ? 1 : 0)
            + (rightUpperState.isCollisionShapeFullBlock(this.level, upperPos.relative(right)) ? 1 : 0);
         boolean isLeftDoor = leftState.getBlock() instanceof DoorBlock
            && getProperty(leftState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null) == DoubleBlockHalf.LOWER;
         boolean isRightDoor = rightState.getBlock() instanceof DoorBlock
            && getProperty(rightState, BlockStateProperties.DOUBLE_BLOCK_HALF).orElse(null) == DoubleBlockHalf.LOWER;
         boolean canPlace = this.doorHinge == DoorHingeSide.RIGHT && (isLeftDoor && !isRightDoor || occupancy > 0)
            || this.doorHinge == DoorHingeSide.LEFT && (isRightDoor && !isLeftDoor || occupancy < 0)
            || occupancy == 0 && isLeftDoor == isRightDoor;
         return Result.resultIf(canPlace, new Action().setSides(sides).setLookDirection(facing).setRequiresSupport());
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!this.requiredState.is(Blocks.IRON_DOOR) && !this.requiredState.is(Blocks.IRON_TRAPDOOR)) {
         return !getProperty(this.requiredState, BlockStateProperties.OPEN).equals(getProperty(this.currentState, BlockStateProperties.OPEN))
            ? Result.success(new ClickAction())
            : Result.SKIP;
      } else {
         return Result.SKIP;
      }
   }
}
