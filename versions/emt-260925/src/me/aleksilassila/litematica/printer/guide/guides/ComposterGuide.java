package me.aleksilassila.litematica.printer.guide.guides;

import java.util.ArrayList;
import java.util.List;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ComposterBlock;

public class ComposterGuide extends Guide {
   private static List<String> compostWhitelistCache = new ArrayList<>();
   private static Item[] whitelistItemsCache = new Item[0];

   public ComposterGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (!Configs.Print.FILL_COMPOSTER.getBooleanValue()) {
         return Result.PASS;
      } else if (this.currentState.hasProperty(ComposterBlock.LEVEL) && this.requiredState.hasProperty(ComposterBlock.LEVEL)) {
         int currentLevel = getProperty(this.currentState, ComposterBlock.LEVEL).orElse(0);
         int requiredLevel = getProperty(this.requiredState, ComposterBlock.LEVEL).orElse(0);
         if (currentLevel >= requiredLevel) {
            return Result.PASS;
         } else {
            List<String> whitelist = Configs.Print.FILL_COMPOSTER_WHITELIST.getStrings();
            if (!whitelist.equals(compostWhitelistCache)) {
               compostWhitelistCache = new ArrayList<>(whitelist);
               List<Item> whitelistItems = new ArrayList<>();

               for (Item item : Reference.COMPOSTABLE_ITEMS) {
                  for (String rule : whitelist) {
                     if (PinYinSearchUtils.matchName(rule, new ItemStack(item))) {
                        whitelistItems.add(item);
                        break;
                     }
                  }
               }

               whitelistItemsCache = whitelistItems.toArray(Item[]::new);
            }

            Item[] finalItems = whitelistItemsCache.length > 0 ? whitelistItemsCache : Reference.COMPOSTABLE_ITEMS;
            return finalItems.length > 0 ? Result.success(new ClickAction().setItems(finalItems)) : Result.SKIP;
         }
      } else {
         return Result.SKIP;
      }
   }
}
