package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CampfireBlock;

public class CampfireGuide extends Guide {
   public CampfireGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      boolean requiredLit = getProperty(this.requiredState, CampfireBlock.LIT).orElseThrow();
      boolean currentLit = getProperty(this.currentState, CampfireBlock.LIT).orElseThrow();
      if (!requiredLit && currentLit) {
         return Result.success(new ClickAction().setItems(Reference.SHOVEL_ITEMS).setSides(Direction.UP));
      } else {
         return requiredLit && !currentLit ? Result.success(new ClickAction().setItems(new Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE})) : Result.SKIP;
      }
   }
}
