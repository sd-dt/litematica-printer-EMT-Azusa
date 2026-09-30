package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.RedstoneSide;

public class RedstoneWireGuide extends Guide {
   public RedstoneWireGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      RedstoneSide rNorth = getProperty(this.requiredState, BlockStateProperties.NORTH_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide rEast = getProperty(this.requiredState, BlockStateProperties.EAST_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide rSouth = getProperty(this.requiredState, BlockStateProperties.SOUTH_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide rWest = getProperty(this.requiredState, BlockStateProperties.WEST_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide cNorth = getProperty(this.currentState, BlockStateProperties.NORTH_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide cEast = getProperty(this.currentState, BlockStateProperties.EAST_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide cSouth = getProperty(this.currentState, BlockStateProperties.SOUTH_REDSTONE).orElse(RedstoneSide.NONE);
      RedstoneSide cWest = getProperty(this.currentState, BlockStateProperties.WEST_REDSTONE).orElse(RedstoneSide.NONE);
      boolean allNoneRequired = rNorth == RedstoneSide.NONE && rSouth == RedstoneSide.NONE && rEast == RedstoneSide.NONE && rWest == RedstoneSide.NONE;
      boolean allSideCurrent = cNorth == RedstoneSide.SIDE && cSouth == RedstoneSide.SIDE && cEast == RedstoneSide.SIDE && cWest == RedstoneSide.SIDE;
      return allNoneRequired && allSideCurrent ? Result.success(new ClickAction().setItem(Items.AIR)) : Result.SKIP;
   }
}
