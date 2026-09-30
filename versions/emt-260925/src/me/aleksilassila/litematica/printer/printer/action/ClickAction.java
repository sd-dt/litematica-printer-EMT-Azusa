package me.aleksilassila.litematica.printer.printer.action;

import java.util.List;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClickAction extends Action {
   @Override
   public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player) {
      return this.queueAction(blockPos, side, useShift, player, null);
   }

   @Override
   public Action queueAction(@NotNull BlockPos blockPos, @NotNull Direction side, boolean useShift, @NotNull LocalPlayer player, @Nullable Item[] expectedItems) {
      ActionManager.INSTANCE
         .queueClick(
            blockPos, side, this.getSides().getOrDefault(side, Vec3.ZERO), false, this.clickRepeatCount, expectedItems, ActionManager.ActionSource.PRINT
         );
      return this;
   }

   @Nullable
   @Override
   public Item[] getRequiredItems(Block backup) {
      return this.clickItems;
   }

   @Nullable
   @Override
   public Direction getValidSide(ClientLevel world, BlockPos pos) {
      List<Direction> orderedSides = this.getOrderedSides();
      return orderedSides.isEmpty() ? null : orderedSides.get(0);
   }
}
