package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.PortalShape;

public class NetherPortalGuide extends Guide {
   public NetherPortalGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Axis requiredAxis = getProperty(this.requiredState, NetherPortalBlock.AXIS).orElse(Axis.X);
      boolean canCreatePortal = PortalShape.findEmptyPortalShape(this.level, this.blockPos, requiredAxis).isPresent();
      return canCreatePortal ? Result.success(new Action().setItems(Items.FLINT_AND_STEEL, Items.FIRE_CHARGE).setRequiresSupport()) : Result.SKIP;
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.SKIP;
   }
}
