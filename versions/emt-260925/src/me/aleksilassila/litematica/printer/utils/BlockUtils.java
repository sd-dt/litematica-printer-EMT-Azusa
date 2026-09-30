package me.aleksilassila.litematica.printer.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CoralPlantBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SeagrassBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

public class BlockUtils {
   @NotNull
   public static final Minecraft client = Minecraft.getInstance();
   private static final BooleanProperty wallUpProperty = WallBlock.UP;
   private static final EnumProperty<WallSide> wallNorthProperty = WallBlock.NORTH;
   private static final EnumProperty<WallSide> wallSouthProperty = WallBlock.SOUTH;
   private static final EnumProperty<WallSide> wallWestProperty = WallBlock.WEST;
   private static final EnumProperty<WallSide> wallEastProperty = WallBlock.EAST;
   private static final float YAW_MIN = -180.0F;
   private static final float YAW_MAX = 180.0F;
   private static final int ROTATION_MIN = 0;
   private static final int ROTATION_MAX = 15;
   private static final float ROTATION_TO_YAW_FACTOR = 22.5F;
   public static Direction[] horizontalDirections = new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
   private static int lastOpenedShulkerSlot = -1;
   private static long lastOpenedShulkerTimeMillis = -1L;

   public static boolean isReplaceable(BlockState blockState) {
      return blockState.canBeReplaced();
   }

   @NotNull
   public static Block getBlock(Identifier blockId) {
      return (Block)BuiltInRegistries.BLOCK.getValue(blockId);
   }

   public static Identifier getKey(Block block) {
      return BuiltInRegistries.BLOCK.getKey(block);
   }

   public static String getKeyString(Block block) {
      return getKey(block).toString();
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

   public static <T extends Comparable<T>> Optional<T> getProperty(BlockState blockState, Property<T> property) {
      return blockState.hasProperty(property) ? Optional.of((T)blockState.getValue(property)) : Optional.empty();
   }

   public static Comparable<?> getPropertyByName(BlockState state, String name) {
      for (Property<?> prop : state.getProperties()) {
         if (prop.getName().equalsIgnoreCase(name)) {
            return state.getValue(prop);
         }
      }

      return null;
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

   public static boolean isWaterBlock(BlockState blockState) {
      return blockState.is(Blocks.WATER) && (Integer)blockState.getValue(LiquidBlock.LEVEL) == 0
         || blockState.getProperties().contains(BlockStateProperties.WATERLOGGED) && (Boolean)blockState.getValue(BlockStateProperties.WATERLOGGED)
         || blockState.getBlock() instanceof BubbleColumnBlock
         || blockState.getBlock() instanceof SeagrassBlock;
   }

   public static boolean isCorrectWaterLevel(BlockState requiredState, BlockState currentState) {
      if (!currentState.is(Blocks.WATER)) {
         return false;
      } else {
         return requiredState.is(Blocks.WATER) && ((Integer)currentState.getValue(LiquidBlock.LEVEL)).equals(requiredState.getValue(LiquidBlock.LEVEL))
            ? true
            : (Integer)currentState.getValue(LiquidBlock.LEVEL) == 0;
      }
   }

   public static float getRequiredYaw(Direction playerShouldBeFacing) {
      return playerShouldBeFacing != null && playerShouldBeFacing.getAxis().isHorizontal() ? playerShouldBeFacing.toYRot() : 0.0F;
   }

   public static float getRequiredPitch(Direction playerShouldBeFacing) {
      if (playerShouldBeFacing != null && playerShouldBeFacing.getAxis().isVertical()) {
         return playerShouldBeFacing == Direction.DOWN ? 90.0F : -90.0F;
      } else {
         return 0.0F;
      }
   }

   public static Vec3i getVector(Direction direction) {
      return direction.getUnitVec3i();
   }

   public static Direction[] orderedByNearest(float yaw, float pitch) {
      double pitchRad = (double)pitch * (Math.PI / 180.0);
      double yawRad = (double)(-yaw) * (Math.PI / 180.0);
      float sinPitch = (float)Math.sin(pitchRad);
      float cosPitch = (float)Math.cos(pitchRad);
      float sinYaw = (float)Math.sin(yawRad);
      float cosYaw = (float)Math.cos(yawRad);
      boolean isEastFacing = sinYaw > 0.0F;
      boolean isUpFacing = sinPitch < 0.0F;
      boolean isSouthFacing = cosYaw > 0.0F;
      float eastWestMagnitude = isEastFacing ? sinYaw : -sinYaw;
      float upDownMagnitude = isUpFacing ? -sinPitch : sinPitch;
      float northSouthMagnitude = isSouthFacing ? cosYaw : -cosYaw;
      float adjustedX = eastWestMagnitude * cosPitch;
      float adjustedZ = northSouthMagnitude * cosPitch;
      Direction primaryXDirection = isEastFacing ? Direction.EAST : Direction.WEST;
      Direction primaryYDirection = isUpFacing ? Direction.UP : Direction.DOWN;
      Direction primaryZDirection = isSouthFacing ? Direction.SOUTH : Direction.NORTH;
      if (eastWestMagnitude > northSouthMagnitude) {
         if (upDownMagnitude > adjustedX) {
            return makeDirectionArray(primaryYDirection, primaryXDirection, primaryZDirection);
         } else {
            return adjustedZ > upDownMagnitude
               ? makeDirectionArray(primaryXDirection, primaryZDirection, primaryYDirection)
               : makeDirectionArray(primaryXDirection, primaryYDirection, primaryZDirection);
         }
      } else if (upDownMagnitude > adjustedZ) {
         return makeDirectionArray(primaryYDirection, primaryZDirection, primaryXDirection);
      } else {
         return adjustedX > upDownMagnitude
            ? makeDirectionArray(primaryZDirection, primaryXDirection, primaryYDirection)
            : makeDirectionArray(primaryZDirection, primaryYDirection, primaryXDirection);
      }
   }

   private static Direction[] makeDirectionArray(Direction dir1, Direction dir2, Direction dir3) {
      return new Direction[]{dir1, dir2, dir3, dir3.getOpposite(), dir2.getOpposite(), dir1.getOpposite()};
   }

   public static Direction getHorizontalDirection(float yaw) {
      return Direction.fromYRot((double)yaw);
   }

   public static float rotationToPlayerYaw(int rotation) {
      rotation = clampRotation(rotation);
      float blockFrontYaw = (float)rotation * 22.5F;
      float playerLookYaw = blockFrontYaw + 180.0F;
      return normalizeYaw(playerLookYaw);
   }

   public static int getOppositeRotation(int rotation) {
      rotation = clampRotation(rotation);
      float playerYaw = rotationToPlayerYaw(rotation);
      float oppositeYaw = getOppositeYaw(playerYaw);
      float normalizedYaw = oppositeYaw < 0.0F ? oppositeYaw + 360.0F : oppositeYaw;
      float blockFrontYaw = normalizedYaw - 180.0F;
      if (blockFrontYaw < 0.0F) {
         blockFrontYaw += 360.0F;
      }

      int oppositeRotation = Math.round(blockFrontYaw / 22.5F);
      return clampRotation(oppositeRotation);
   }

   public static float getOppositeYaw(float playerLookYaw) {
      playerLookYaw = normalizeYaw(playerLookYaw);
      float oppositeYaw = playerLookYaw + 180.0F;
      return normalizeYaw(oppositeYaw);
   }

   private static float normalizeYaw(float yaw) {
      yaw %= 360.0F;
      if (yaw > 180.0F) {
         yaw -= 360.0F;
      } else if (yaw < -180.0F) {
         yaw += 360.0F;
      }

      return yaw;
   }

   private static int clampRotation(int rotation) {
      rotation %= 16;
      if (rotation < 0) {
         rotation += 16;
      }

      return rotation;
   }

   public static void openShulker(ItemStack stack, int shulkerBoxSlot) {
      lastOpenedShulkerSlot = shulkerBoxSlot;
      lastOpenedShulkerTimeMillis = System.currentTimeMillis();
      me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.beginAutomatedQuickShulkerScreenProtection();
      client.gameMode.handleContainerInput(client.player.containerMenu.containerId, shulkerBoxSlot, 1, ContainerInput.PICKUP, client.player);
   }

   public static boolean isShulkerRecentlyOpened(int slot) {
      return slot == lastOpenedShulkerSlot && lastOpenedShulkerTimeMillis >= 0L ? System.currentTimeMillis() - lastOpenedShulkerTimeMillis < 200L : false;
   }

   public static boolean canBeClicked(ClientLevel world, BlockPos pos) {
      return getOutlineShape(world, pos) != Shapes.empty();
   }

   public static VoxelShape getOutlineShape(ClientLevel world, BlockPos pos) {
      return world.getBlockState(pos).getShape(world, pos);
   }

   public static Map<Direction, Vec3> getSlabSides(Level world, BlockPos pos, SlabType requiredHalf) {
      if (requiredHalf == SlabType.DOUBLE) {
         requiredHalf = SlabType.BOTTOM;
      }

      Direction requiredDir = requiredHalf == SlabType.TOP ? Direction.UP : Direction.DOWN;
      Map<Direction, Vec3> sides = new HashMap<>();
      sides.put(requiredDir, new Vec3(0.0, 0.0, 0.0));
      if (world.getBlockState(pos).hasProperty(SlabBlock.TYPE)) {
         sides.put(requiredDir.getOpposite(), Vec3.atLowerCornerOf(getVector(requiredDir)).scale(0.5));
      }

      for (Direction side : horizontalDirections) {
         BlockState neighborCurrentState = world.getBlockState(pos.relative(side));
         if (!neighborCurrentState.hasProperty(SlabBlock.TYPE)
            || neighborCurrentState.getValue(SlabBlock.TYPE) == SlabType.DOUBLE
            || neighborCurrentState.getValue(SlabBlock.TYPE) == requiredHalf) {
            sides.put(side, Vec3.atLowerCornerOf(getVector(requiredDir)).scale(0.25));
         }
      }

      return sides;
   }
}
