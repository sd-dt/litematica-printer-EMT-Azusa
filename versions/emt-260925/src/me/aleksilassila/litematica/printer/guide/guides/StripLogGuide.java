package me.aleksilassila.litematica.printer.guide.guides;

import java.util.Map;
import java.util.Map.Entry;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.fabricmc.fabric.mixin.content.registry.AxeItemAccessor;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class StripLogGuide extends Guide {
   private static final Map<Block, Block> STRIPPED_LOGS = AxeItemAccessor.getStrippables();

   public StripLogGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Axis axis = (Axis)getProperty(this.requiredState, BlockStateProperties.AXIS).orElse(null);
      if (axis == null) {
         return Result.PASS;
      } else {
         Action action = new Action().setSides(axis);
         if (Configs.Print.STRIP_LOGS.getBooleanValue()) {
            for (Entry<Block, Block> entry : STRIPPED_LOGS.entrySet()) {
               if (this.requiredBlock == entry.getValue()) {
                  action.setItems(entry.getValue().asItem(), entry.getKey().asItem());
                  return Result.success(action);
               }
            }
         }

         return Result.success(action);
      }
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      Block stripped = STRIPPED_LOGS.get(this.currentBlock);
      return stripped != null && stripped == this.requiredBlock ? Result.success(new ClickAction().setItems(Reference.AXE_ITEMS)) : Result.PASS;
   }
}
