package me.aleksilassila.litematica.printer.guide.guides;

import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.CandleBlock;

public class CandleGuide extends Guide {
   public CandleGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      int requiredCandles = getProperty(this.requiredState, CandleBlock.CANDLES).orElseThrow();
      int currentCandles = getProperty(this.currentState, CandleBlock.CANDLES).orElseThrow();
      boolean requiredLit = getProperty(this.requiredState, CandleBlock.LIT).orElseThrow();
      boolean currentLit = getProperty(this.currentState, CandleBlock.LIT).orElseThrow();
      if (currentCandles < requiredCandles) {
         return Result.success(new ClickAction().setItem(this.requiredBlock.asItem()));
      } else if (!currentLit && requiredLit) {
         return Result.success(new ClickAction().setItems(new Item[]{Items.FLINT_AND_STEEL, Items.FIRE_CHARGE}));
      } else {
         return currentLit && !requiredLit ? Result.success(new ClickAction()) : Result.SKIP;
      }
   }
}
