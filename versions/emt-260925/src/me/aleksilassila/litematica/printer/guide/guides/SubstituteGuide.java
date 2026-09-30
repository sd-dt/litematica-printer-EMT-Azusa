package me.aleksilassila.litematica.printer.guide.guides;

import java.util.ArrayList;
import java.util.List;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.SubstitutePlacementCache;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SubstituteGuide extends Guide {
   public SubstituteGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected boolean canExecute() {
      return SubstitutePlacementCache.active() && SubstitutePlacementCache.hasSubstitutes(this.requiredBlock);
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      return Configs.Print.SUBSTITUTE_PLACEMENT.getBooleanValue() && SubstitutePlacementCache.isSubstituteOf(this.requiredBlock, this.currentBlock)
         ? Result.SKIP
         : Result.PASS;
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      List<Item> items = new ArrayList<>();
      items.add(this.requiredBlock.asItem());

      for (Block substitute : SubstitutePlacementCache.getSubstitutes(this.requiredBlock)) {
         Item item = substitute.asItem();
         if (item != Items.AIR && !items.contains(item)) {
            items.add(item);
         }
      }

      Action action = this.buildTargetStateContext(new Action());
      action.setItems(items.toArray(new Item[0]));
      Identifier targetId = BlockUtils.getKey(this.requiredBlock);
      if (targetId.getPath().contains("coral") && !targetId.getPath().endsWith("_block")) {
         getProperty(this.requiredState, BlockStateProperties.HORIZONTAL_FACING).ifPresent(facing -> action.setSides(facing.getOpposite()));
         action.setRequiresSupport();
      }

      return Result.success(action);
   }
}
