package me.aleksilassila.litematica.printer.utils;

import java.util.Optional;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.KelpBlock;
import net.minecraft.world.level.block.KelpPlantBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class BlockStateUtils extends BlockUtils {
   private static final BooleanProperty wallUpProperty = BlockStateProperties.UP;
   private static final EnumProperty<WallSide> wallNorthProperty = BlockStateProperties.NORTH_WALL;
   private static final EnumProperty<WallSide> wallSouthProperty = BlockStateProperties.SOUTH_WALL;
   private static final EnumProperty<WallSide> wallWestProperty = BlockStateProperties.WEST_WALL;
   private static final EnumProperty<WallSide> wallEastProperty = BlockStateProperties.EAST_WALL;
   private static final BooleanProperty northProperty = BlockStateProperties.NORTH;
   private static final BooleanProperty southProperty = BlockStateProperties.SOUTH;
   private static final BooleanProperty westProperty = BlockStateProperties.WEST;
   private static final BooleanProperty eastProperty = BlockStateProperties.EAST;
   private static final Set<String> DYNAMIC_STATE_PROPERTIES = Set.of(
      "powered",
      "lit",
      "extended",
      "locked",
      "enabled",
      "triggered",
      "inverted",
      "power",
      "level",
      "sculk_sensor_phase",
      "shrieking",
      "can_summon",
      "tilt",
      "unstable",
      "attached",
      "disarmed",
      "age",
      "stage",
      "leaves",
      "honey_level",
      "charges",
      "bites",
      "moisture",
      "snowy",
      "north",
      "east",
      "south",
      "west",
      "up",
      "down",
      "distance",
      "bottom",
      "has_book"
   );

   public static boolean isColumnLoaded(ClientLevel level, int chunkX, int chunkZ) {
      return level.getChunkSource().hasChunk(chunkX, chunkZ);
   }

   public static boolean statesEqualIgnoreProperties(BlockState state1, BlockState state2, Property<?>... propertiesToIgnore) {
      if (state1.getBlock() != state2.getBlock()) {
         return false;
      } else {
         label46:
         for (Property<?> property : state1.getProperties()) {
            if (property != BlockStateProperties.WATERLOGGED || state1.getBlock() instanceof CoralPlantBlock) {
               for (Property<?> ignoredProperty : propertiesToIgnore) {
                  if (property == ignoredProperty) {
                     continue label46;
                  }
               }

               try {
                  if (!state1.getValue(property).equals(state2.getValue(property))) {
                     return false;
                  }
               } catch (Exception var9) {
                  return false;
               }
            }
         }

         return true;
      }
   }

   public static boolean hasFixableStateDifference(BlockState state1, BlockState state2) {
      if (state1.getBlock() != state2.getBlock()) {
         return true;
      } else {
         for (Property<?> property : state1.getProperties()) {
            if ((property != BlockStateProperties.WATERLOGGED || state1.getBlock() instanceof CoralPlantBlock)
               && !DYNAMIC_STATE_PROPERTIES.contains(property.getName())) {
               try {
                  if (!state1.getValue(property).equals(state2.getValue(property))) {
                     return true;
                  }
               } catch (Exception var5) {
                  return true;
               }
            }
         }

         return false;
      }
   }

   public static <T extends Comparable<T>> Optional<T> getProperty(BlockState blockState, Property<T> property) {
      return blockState.hasProperty(property) ? Optional.of((T)blockState.getValue(property)) : Optional.empty();
   }

   public static boolean statesEqual(BlockState state1, BlockState state2) {
      return statesEqualIgnoreProperties(state1, state2);
   }

   protected static boolean canBeClicked(Level world, BlockPos pos) {
      return getOutlineShape(world, pos) != Shapes.empty();
   }

   private static VoxelShape getOutlineShape(Level level, BlockPos pos) {
      return level.getBlockState(pos).getShape(level, pos);
   }

   private static VoxelShape getOutlineShape(BlockState state, Level level, BlockPos pos) {
      return state.getShape(level, pos);
   }

   public static Optional<Property<?>> getWallFacingProperty(Direction wallFacing) {
      switch (wallFacing) {
         case UP:
            return Optional.of(wallUpProperty);
         case NORTH:
            return Optional.of(wallNorthProperty);
         case SOUTH:
            return Optional.of(wallSouthProperty);
         case WEST:
            return Optional.of(wallWestProperty);
         case EAST:
            return Optional.of(wallEastProperty);
         default:
            return Optional.empty();
      }
   }

   public static Optional<Property<?>> getCrossCollisionBlock(Direction wallFacing) {
      switch (wallFacing) {
         case NORTH:
            return Optional.of(northProperty);
         case SOUTH:
            return Optional.of(southProperty);
         case WEST:
            return Optional.of(westProperty);
         case EAST:
            return Optional.of(eastProperty);
         default:
            return Optional.empty();
      }
   }

   public static boolean isWaterBlock(BlockState blockState) {
      return blockState.is(Blocks.WATER) && (Integer)blockState.getValue(LiquidBlock.LEVEL) == 0
         || blockState.hasProperty(BlockStateProperties.WATERLOGGED) && (Boolean)blockState.getValue(BlockStateProperties.WATERLOGGED)
         || blockState.getBlock() instanceof BubbleColumnBlock;
   }

   public static boolean hasSourceWaterFluid(BlockState blockState) {
      FluidState fluidState = blockState.getFluidState();
      return fluidState.is(FluidTags.WATER) && fluidState.isSource();
   }

   private static boolean isSourceWaterOrBubbleColumn(BlockState blockState) {
      return !hasSourceWaterFluid(blockState) ? false : blockState.is(Blocks.WATER) || blockState.getBlock() instanceof BubbleColumnBlock;
   }

   public static boolean requiresWaterToPlace(Block block) {
      return block instanceof SeagrassBlock || block instanceof KelpBlock || block instanceof KelpPlantBlock;
   }

   public static boolean isCorrectWaterLevel(BlockState requiredState, BlockState currentState) {
      if (requiredState.is(Blocks.WATER)) {
         return currentState.is(Blocks.WATER)
            ? ((Integer)currentState.getValue(LiquidBlock.LEVEL)).equals(requiredState.getValue(LiquidBlock.LEVEL))
            : (Integer)requiredState.getValue(LiquidBlock.LEVEL) == 0
               && currentState.getBlock() instanceof BubbleColumnBlock
               && hasSourceWaterFluid(currentState);
      } else {
         return requiredState.getBlock() instanceof BubbleColumnBlock ? isSourceWaterOrBubbleColumn(currentState) : isSourceWaterOrBubbleColumn(currentState);
      }
   }
}
