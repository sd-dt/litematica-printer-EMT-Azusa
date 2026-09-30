package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Generated;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.FillBlockModeType;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

public class FillHandler extends ClientPlayerTickHandler {
   public static final String NAME = "fill";
   private List<String> fillCacheBlocklist = new ArrayList<>();
   private Item[] fillModeItemList = new Item[0];

   public FillHandler() {
      super("fill", PrintModeType.FILL, Configs.Core.FILL, Configs.Fill.FILL_SELECTION_TYPE, true);
   }

   @Override
   protected int getTickInterval() {
      return Configs.Print.PLACE_INTERVAL.getIntegerValue();
   }

   @Override
   protected int getMaxExecutions() {
      return Configs.Print.PLACE_BLOCKS_PER_TICK.getIntegerValue();
   }

   @Override
   protected void preprocess() {
      FillBlockModeType fillMode = (FillBlockModeType)Configs.Fill.FILL_BLOCK_MODE.getOptionListValue();
      switch (fillMode) {
         case BLOCKLIST:
            List<String> strings = Configs.Fill.FILL_BLOCK_LIST.getStrings();
            if (!strings.equals(this.fillCacheBlocklist)) {
               this.fillCacheBlocklist = new ArrayList<>(strings);
               this.fillModeItemList = new Item[0];
               if (strings.isEmpty()) {
                  return;
               }

               List<Item> items = new ArrayList<>();

               for (String itemName : this.fillCacheBlocklist) {
                  items.addAll(BuiltInRegistries.ITEM.stream().filter(item -> PinYinSearchUtils.matchName(itemName, new ItemStack(item))).toList());
               }

               this.fillModeItemList = items.toArray(new Item[0]);
            }
            break;
         case HANDHELD:
            if (Configs.Fill.FILL_BLOCK_MODE.getOptionListValue() == FillBlockModeType.HANDHELD) {
               ItemStack heldStack = this.player.getMainHandItem();
               if (!heldStack.isEmpty() && heldStack.getCount() > 0) {
                  this.fillModeItemList = new Item[]{this.player.getMainHandItem().getItem()};
               } else {
                  this.fillModeItemList = new Item[0];
               }
            }
      }
   }

   @Override
   protected boolean canIterate() {
      return this.fillModeItemList.length > 0;
   }

   @Override
   public boolean canProcessPos(BlockPos blockPos) {
      if (Configs.Fill.FILL_BLOCK_MODE.getOptionListValue() != FillBlockModeType.HANDHELD) {
         return true;
      } else {
         ItemStack heldStack = this.player.getMainHandItem();
         return !heldStack.isEmpty() && heldStack.getCount() > 0;
      }
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      BlockState currentState = this.level.getBlockState(blockPos);
      if (currentState.isAir()
         || currentState.getBlock() instanceof LiquidBlock
         || Configs.Print.REPLACEABLE_LIST.getStrings().stream().anyMatch(s -> PinYinSearchUtils.matchName(s, currentState))) {
         if (!InventoryUtils.switchToItems(this.player, this.fillModeItemList)) {
            return;
         }

         if (Configs.Print.FALLING_CHECK.getBooleanValue()
            && this.player.getMainHandItem().getItem() instanceof BlockItem item
            && item.getBlock() instanceof FallingBlock block
            && FallingBlock.isFree(this.level.getBlockState(blockPos.below()))) {
            MessageUtils.setOverlayMessage(I18n.BLOCK_NO_SUPPORT.getName(block.getName().getString()));
            return;
         }

         Action action;
         if (ConfigUtils.getFillModeFacing() != null) {
            action = new Action()
               .setActionSource(ActionManager.ActionSource.FILL)
               .setLookDirection(ConfigUtils.getFillModeFacing().getOpposite())
               .queueAction(blockPos, ConfigUtils.getFillModeFacing(), false, this.player);
         } else {
            action = new Action()
               .setActionSource(ActionManager.ActionSource.FILL)
               .queueAction(blockPos, this.getPlayerPlacementDirection(), false, this.player);
         }

         ActionManager.INSTANCE.setLook(action.getPlayerLook());
         ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
         ActionManager.INSTANCE.setWaitForHorizontalLook(false);
         if (ActionManager.INSTANCE.sendQueue(this.player).isWaiting()) {
            skipIteration.set(true);
         } else {
            this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
         }
      }
   }

   @Generated
   public Item[] getFillModeItemList() {
      return this.fillModeItemList;
   }
}
