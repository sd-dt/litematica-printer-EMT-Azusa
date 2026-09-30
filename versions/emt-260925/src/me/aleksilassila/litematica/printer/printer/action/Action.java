package me.aleksilassila.litematica.printer.printer.action;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Predicate;
import lombok.Generated;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.DefaultPlaceDirectionType;
import me.aleksilassila.litematica.printer.interfaces.Implementation;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Action {
   private static final Direction[] DEFAULT_SIDE_ORDER = new Direction[]{
      Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN
   };
   protected Map<Direction, Vec3> sides;
   protected boolean customSides;
   @Nullable
   protected PlayerLook playerLook = null;
   @Nullable
   protected Item[] clickItems;
   protected boolean requiresSupport = false;
   protected boolean clickLiquidSupport = false;
   @Nullable
   protected Direction fixedSide = null;
   @Nullable
   protected Boolean shift = null;
   protected boolean consumeEffectiveExecution = true;
   protected int cooldownTicksOverride = -1;
   protected int clickRepeatCount = 1;
   protected Boolean needWaitModifyLook = false;
   protected boolean waitForHorizontalLook = true;
   @Nullable
   protected Predicate<ItemStack> requiredStackPredicate;
   @Nullable
   protected ItemStack requiredCreativeStack;
   protected ActionManager.ActionSource actionSource = ActionManager.ActionSource.GENERIC;

   public Action setActionSource(ActionManager.ActionSource actionSource) {
      this.actionSource = actionSource;
      return this;
   }

   public boolean isDirectional() {
      return this.playerLook != null || this.customSides || this.fixedSide != null;
   }

   public Action() {
      this.sides = createDefaultSides();
      this.customSides = false;
   }

   public Action setLookRotation(int lookRotation) {
      this.playerLook = new PlayerLook(lookRotation);
      return this;
   }

   public Action setLookRotation(int lookRotation, float pitch) {
      this.playerLook = new PlayerLook(BlockUtils.rotationToPlayerYaw(lookRotation), pitch);
      return this;
   }

   public Action setLookDirection(Direction lookDirection) {
      this.playerLook = new PlayerLook(lookDirection);
      return this;
   }

   public Action setLookDirection(Direction lookDirectionYaw, Direction lookDirectionPitch) {
      this.playerLook = new PlayerLook(lookDirectionYaw, lookDirectionPitch);
      return this;
   }

   public Action setNeedWaitModifyLook(boolean needWaitModifyLook) {
      this.needWaitModifyLook = needWaitModifyLook;
      return this;
   }

   public Action setNeedWaitModifyLook() {
      return this.setNeedWaitModifyLook(true);
   }

   public Action setWaitForHorizontalLook(boolean waitForHorizontalLook) {
      this.waitForHorizontalLook = waitForHorizontalLook;
      return this;
   }

   @Nullable
   public Item[] getRequiredItems(Block backup) {
      return this.clickItems == null ? new Item[]{backup.asItem()} : this.clickItems;
   }

   @NotNull
   public Map<Direction, Vec3> getSides() {
      if (this.sides == null) {
         this.sides = createDefaultSides();
      }

      return this.sides;
   }

   @NotNull
   protected List<Direction> getOrderedSides() {
      return new ArrayList<>(this.getSides().keySet());
   }

   public Action setSides(Axis... axis) {
      Map<Direction, Vec3> sides = new LinkedHashMap<>();

      for (Axis a : axis) {
         for (Direction d : DEFAULT_SIDE_ORDER) {
            if (d.getAxis() == a) {
               sides.put(d, new Vec3(0.0, 0.0, 0.0));
            }
         }
      }

      this.sides = sides;
      this.customSides = true;
      return this;
   }

   public Action setSides(Map<Direction, Vec3> sides) {
      this.sides = copySidesInDefaultOrder(sides);
      this.customSides = true;
      return this;
   }

   public Action setSides(Direction side, Vec3 offset) {
      this.sides = new LinkedHashMap<>();
      this.sides.put(side, offset);
      this.customSides = true;
      return this;
   }

   public Action setSides(Direction... directions) {
      Map<Direction, Vec3> sides = new LinkedHashMap<>();

      for (Direction d : directions) {
         sides.put(d, new Vec3(0.0, 0.0, 0.0));
      }

      this.sides = sides;
      this.customSides = true;
      return this;
   }

   @Nullable
   public Direction getValidSide(ClientLevel world, BlockPos pos) {
      if (this.fixedSide != null) {
         return this.fixedSide;
      } else {
         if (!this.customSides && this.playerLook == null) {
            Direction forcedDirection = getForcedDirection();
            if (forcedDirection != null) {
               return forcedDirection;
            }
         }

         List<Direction> orderedSides = this.getOrderedSides();
         if (Configs.Print.PLACE_IN_AIR.getBooleanValue() && !this.requiresSupport) {
            return orderedSides.isEmpty() ? null : orderedSides.get(0);
         } else {
            Direction firstValidSide = null;
            BlockState currentState = world.getBlockState(pos);

            for (Direction side : orderedSides) {
               BlockPos neighborPos = pos.relative(side);
               BlockState neighborState = world.getBlockState(neighborPos);
               boolean liquidSupport = this.clickLiquidSupport && !neighborState.getFluidState().isEmpty();
               boolean clickable = BlockUtils.canBeClicked(world, neighborPos) || liquidSupport;
               boolean replaceable = BlockUtils.isReplaceable(neighborState) && !liquidSupport;
               if (clickable && !replaceable) {
                  if (firstValidSide == null) {
                     firstValidSide = side;
                  }

                  if (!Implementation.isInteractive(neighborState.getBlock()) && currentState.canSurvive(world, pos)) {
                     return side;
                  }
               }
            }

            return firstValidSide;
         }
      }
   }

   @Nullable
   private static Direction getForcedDirection() {
      DefaultPlaceDirectionType forcedDirection = (DefaultPlaceDirectionType)Configs.Print.PLACE_DEFAULT_DIRECTION.getOptionListValue();
      return forcedDirection != null ? forcedDirection.toDirection() : null;
   }

   public Action setItem(Item item) {
      return this.setItems(item);
   }

   public Action setItems(Item... items) {
      this.clickItems = items;
      return this;
   }

   public Action setRequiresSupport(boolean requiresSupport) {
      this.requiresSupport = requiresSupport;
      return this;
   }

   public boolean requiresSupport() {
      return this.requiresSupport;
   }

   public Action setRequiresSupport() {
      return this.setRequiresSupport(true);
   }

   public Action setClickLiquidSupport(boolean clickLiquidSupport) {
      this.clickLiquidSupport = clickLiquidSupport;
      return this;
   }

   public Action setClickLiquidSupport() {
      return this.setClickLiquidSupport(true);
   }

   public Action setFixedSide(@Nullable Direction fixedSide) {
      this.fixedSide = fixedSide;
      return this;
   }

   public Action setShift(boolean useShift) {
      this.shift = useShift;
      return this;
   }

   public Action setShift() {
      return this.setShift(true);
   }

   public Action setRequiredStackPredicate(@Nullable Predicate<ItemStack> requiredStackPredicate) {
      this.requiredStackPredicate = requiredStackPredicate;
      return this;
   }

   public Action setRequiredCreativeStack(@Nullable ItemStack requiredCreativeStack) {
      this.requiredCreativeStack = requiredCreativeStack == null ? null : requiredCreativeStack.copy();
      return this;
   }

   public Action setConsumeEffectiveExecution(boolean consumeEffectiveExecution) {
      this.consumeEffectiveExecution = consumeEffectiveExecution;
      return this;
   }

   public Action setCooldownTicksOverride(int cooldownTicksOverride) {
      this.cooldownTicksOverride = cooldownTicksOverride;
      return this;
   }

   public Action setClickRepeatCount(int clickRepeatCount) {
      this.clickRepeatCount = Math.max(1, clickRepeatCount);
      return this;
   }

   public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player) {
      return this.queueAction(blockPos, side, useShift, player, null);
   }

   public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player, @Nullable Item[] expectedItems) {
      if (Configs.Print.PLACE_IN_AIR.getBooleanValue() && !this.requiresSupport) {
         ActionManager.INSTANCE
            .queueClick(
               blockPos,
               side.getOpposite(),
               this.getSides().getOrDefault(side, Vec3.ZERO),
               useShift,
               this.clickRepeatCount,
               expectedItems,
               this.actionSource,
               true
            );
      } else {
         ActionManager.INSTANCE
            .queueClick(
               blockPos.relative(side),
               side.getOpposite(),
               this.getSides().getOrDefault(side, Vec3.ZERO),
               useShift,
               this.clickRepeatCount,
               expectedItems,
               this.actionSource
            );
      }

      return this;
   }

   @NotNull
   private static Map<Direction, Vec3> createDefaultSides() {
      Map<Direction, Vec3> sides = new LinkedHashMap<>();

      for (Direction direction : DEFAULT_SIDE_ORDER) {
         sides.put(direction, Vec3.ZERO);
      }

      return sides;
   }

   @NotNull
   private static Map<Direction, Vec3> copySidesInDefaultOrder(@NotNull Map<Direction, Vec3> source) {
      Map<Direction, Vec3> ordered = new LinkedHashMap<>();

      for (Direction direction : DEFAULT_SIDE_ORDER) {
         if (source.containsKey(direction)) {
            ordered.put(direction, source.get(direction));
         }
      }

      for (Entry<Direction, Vec3> entry : source.entrySet()) {
         ordered.putIfAbsent(entry.getKey(), entry.getValue());
      }

      return ordered;
   }

   @Nullable
   @Generated
   public PlayerLook getPlayerLook() {
      return this.playerLook;
   }

   @Nullable
   @Generated
   public Boolean getShift() {
      return this.shift;
   }

   @Generated
   public boolean isConsumeEffectiveExecution() {
      return this.consumeEffectiveExecution;
   }

   @Generated
   public int getCooldownTicksOverride() {
      return this.cooldownTicksOverride;
   }

   @Generated
   public int getClickRepeatCount() {
      return this.clickRepeatCount;
   }

   @Generated
   public Boolean getNeedWaitModifyLook() {
      return this.needWaitModifyLook;
   }

   @Generated
   public boolean isWaitForHorizontalLook() {
      return this.waitForHorizontalLook;
   }

   @Nullable
   @Generated
   public Predicate<ItemStack> getRequiredStackPredicate() {
      return this.requiredStackPredicate;
   }

   @Nullable
   @Generated
   public ItemStack getRequiredCreativeStack() {
      return this.requiredCreativeStack;
   }

   @Generated
   public ActionManager.ActionSource getActionSource() {
      return this.actionSource;
   }
}
