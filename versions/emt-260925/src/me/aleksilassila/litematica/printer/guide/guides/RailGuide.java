package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RailShape;

public class RailGuide extends Guide {
   private static final int MAX_REPAIR_ATTEMPTS = 3;
   private static final int PENDING_REPAIR_TICKS = 40;
   private static final Map<RailGuide.RailRepairKey, Integer> repairAttempts = new HashMap<>();
   private static final Map<RailGuide.RailRepairKey, Long> pendingRepairs = new HashMap<>();

   public RailGuide(SchematicBlockContext context) {
      super(context);
   }

   public static void clearRepairState() {
      repairAttempts.clear();
      pendingRepairs.clear();
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      this.clearPendingRepairState(this.blockPos);
      Optional<RailShape> railShape = this.getRailShape(this.requiredState);
      if (railShape.isEmpty()) {
         return Result.PASS;
      } else {
         RailShape shape = railShape.get();
         // 含水铁轨：水还没到位就先别放（SKIP = 本轮不处理，等下一轮扫描）
         if (this.isWaterloggedRequired() && !this.isSourceWater(this.blockPos)) {
            return Result.SKIP;
         }

         // 风水：弯道 / 坡道必须等邻接铁轨就位，直轨可以直接靠"看的方向"定朝向
         boolean straight = shape == RailShape.EAST_WEST || shape == RailShape.NORTH_SOUTH;
         if (!straight && !this.railConnectionsReady(shape)) {
            return Result.SKIP;
         }

         Action action = new Action();
         switch (shape) {
            case EAST_WEST:
            case ASCENDING_EAST:
               action.setLookDirection(Direction.EAST);
               break;
            case NORTH_SOUTH:
            case ASCENDING_NORTH:
               action.setLookDirection(Direction.NORTH);
               break;
            case ASCENDING_WEST:
               action.setLookDirection(Direction.WEST);
               break;
            case ASCENDING_SOUTH:
               action.setLookDirection(Direction.SOUTH);
         }

         return Result.success(action);
      }
   }

   /** 投影要求这一格是含水铁轨吗 */
      private boolean isWaterloggedRequired() {
      return this.requiredState.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean)this.requiredState.getValue(BlockStateProperties.WATERLOGGED);
   }

   /** 这一格现在是不是水源（放铁轨时会被吸进轨道里，得到含水铁轨） */
      private boolean isSourceWater(BlockPos pos) {
      return this.level.getBlockState(pos).getFluidState().isSource();
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!Configs.Print.REPAIR_RAIL_SHAPE.getBooleanValue()) {
         return Result.SKIP;
      } else {
         Optional<RailShape> requiredShape = this.getRailShape(this.requiredState);
         Optional<RailShape> currentShape = this.getRailShape(this.currentState);
         if (!requiredShape.isEmpty() && !currentShape.isEmpty() && !requiredShape.equals(currentShape)) {
            RailGuide.RailRepairKey key = this.repairKey(requiredShape.get());
            if (this.railConnectionsReady(requiredShape.get())
               && !this.isRepairPending(key)
               && repairAttempts.getOrDefault(key, 0) < 3
               && BreakUtils.canBreakBlock(this.blockPos)
               && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState)) {
               repairAttempts.put(key, repairAttempts.getOrDefault(key, 0) + 1);
               pendingRepairs.put(key, this.level.getGameTime());
               BreakUtils.INSTANCE.add(key.pos());
               return Result.SKIP;
            } else {
               return Result.SKIP;
            }
         } else {
            return Result.SKIP;
         }
      }
   }

   @Override
   protected Result onBuildActionCorrect(BlockMatchResult state) {
      this.clearPendingRepairState(this.blockPos);
      return Result.PASS;
   }

   private Optional<RailShape> getRailShape(BlockState state) {
      return getProperty(state, BlockStateProperties.RAIL_SHAPE).or(() -> getProperty(state, BlockStateProperties.RAIL_SHAPE_STRAIGHT));
   }

   private boolean isRepairPending(RailGuide.RailRepairKey key) {
      Long startedAt = pendingRepairs.get(key);
      if (startedAt == null) {
         return false;
      } else if (this.level.getGameTime() - startedAt < 40L) {
         return true;
      } else {
         pendingRepairs.remove(key);
         return false;
      }
   }

   private void clearRepairState(BlockPos pos) {
      ResourceKey<Level> dimension = this.level.dimension();
      repairAttempts.keySet().removeIf(key -> key.matches(dimension, pos));
      pendingRepairs.keySet().removeIf(key -> key.matches(dimension, pos));
   }

   private void clearPendingRepairState(BlockPos pos) {
      ResourceKey<Level> dimension = this.level.dimension();
      pendingRepairs.keySet().removeIf(key -> key.matches(dimension, pos));
   }

   private RailGuide.RailRepairKey repairKey(RailShape shape) {
      return new RailGuide.RailRepairKey(this.level.dimension(), this.blockPos.immutable(), shape);
   }

   private boolean railConnectionsReady(RailShape shape) {
      return switch (shape) {
         case EAST_WEST -> this.connectionReady(this.blockPos.west()) && this.connectionReady(this.blockPos.east());
         case ASCENDING_EAST -> this.connectionReady(this.blockPos.west()) && this.connectionReady(this.blockPos.east().above());
         case NORTH_SOUTH -> this.connectionReady(this.blockPos.north()) && this.connectionReady(this.blockPos.south());
         case ASCENDING_NORTH -> this.connectionReady(this.blockPos.north().above()) && this.connectionReady(this.blockPos.south());
         case ASCENDING_WEST -> this.connectionReady(this.blockPos.west().above()) && this.connectionReady(this.blockPos.east());
         case ASCENDING_SOUTH -> this.connectionReady(this.blockPos.north()) && this.connectionReady(this.blockPos.south().above());
         case SOUTH_EAST -> this.connectionReady(this.blockPos.south()) && this.connectionReady(this.blockPos.east());
         case SOUTH_WEST -> this.connectionReady(this.blockPos.south()) && this.connectionReady(this.blockPos.west());
         case NORTH_WEST -> this.connectionReady(this.blockPos.north()) && this.connectionReady(this.blockPos.west());
         case NORTH_EAST -> this.connectionReady(this.blockPos.north()) && this.connectionReady(this.blockPos.east());
         default -> throw new MatchException(null, null);
      };
   }

   private boolean connectionReady(BlockPos pos) {
      BlockState schematicState = LitematicaUtils.getSchematicBlockState(pos);
      return schematicState == null || !BaseRailBlock.isRail(schematicState) || BaseRailBlock.isRail(this.level.getBlockState(pos));
   }

   private static record RailRepairKey(ResourceKey<Level> dimension, BlockPos pos, RailShape requiredShape) {
      private boolean matches(ResourceKey<Level> dimension, BlockPos pos) {
         return this.dimension.equals(dimension) && this.pos.equals(pos);
      }
   }
}
