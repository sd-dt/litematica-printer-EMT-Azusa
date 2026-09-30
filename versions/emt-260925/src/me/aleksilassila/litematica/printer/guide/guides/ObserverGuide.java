package me.aleksilassila.litematica.printer.guide.guides;

import java.util.ArrayList;
import java.util.List;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.state.properties.Property;

public class ObserverGuide extends Guide {
   public ObserverGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, ObserverBlock.FACING).orElseThrow();
      if (!Configs.Print.SAFELY_OBSERVER.getBooleanValue()) {
         return Result.success(new Action().setLookDirection(facing).setNeedWaitModifyLook());
      } else {
         SchematicBlockContext input = this.context.offset(facing);
         if (!LitematicaUtils.isSchematicBlock(input.blockPos)) {
            return Result.success(placementAction(facing));
         } else {
            List<Property<?>> inputPropertiesToIgnore = new ArrayList<>();
            BlockMatchResult inputState = BlockMatchResult.compare(input, inputPropertiesToIgnore.toArray(new Property[0]));
            return inputState != BlockMatchResult.CORRECT ? Result.SKIP : Result.success(placementAction(facing));
         }
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.SKIP;
   }

   private static Action placementAction(Direction facing) {
      return new Action().setLookDirection(facing).setNeedWaitModifyLook();
   }
}
