package me.aleksilassila.litematica.printer.handler.handlers;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.Long2LongMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Generated;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.go.GhastRideState;
import me.aleksilassila.litematica.printer.go.GhastShiftBlacklist;
import me.aleksilassila.litematica.printer.guide.Guides;
import me.aleksilassila.litematica.printer.guide.guides.ShulkerPlacementGuard;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.interfaces.Implementation;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.PrintTaskController;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.printer.ScanWhitelistCache;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.SchematicStateCache;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.CloudStoreUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.PacketSoundConfirmationTracker;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class PrintHandler extends ClientPlayerTickHandler {
   public static final String NAME = "print";
   private boolean pistonNeedFix;
   private boolean printerMemorySync;
   private Action action;
   private boolean icePlacementTask;
   @Nullable
   private Item activePlacementItem;
   private long nextPlacementItemTick;
   private long lastActivePlacementTick;
   private SchematicBlockContext ctx;
   private final Long2LongOpenHashMap retryTable = new Long2LongOpenHashMap();
   private final Long2LongOpenHashMap pendingConfirm = new Long2LongOpenHashMap();
   private static final int CONFIRM_WINDOW_TICKS = 5;
   private PrintHandler.ExecuteOutcome lastOutcome = PrintHandler.ExecuteOutcome.DEFERRED;
   private static final int PENDING_SCAN_TTL_TICKS = 10;
   @Nullable
   private Item memoScanItem;
   private int memoScanBoxId;
   private long memoScanRevision = Long.MIN_VALUE;
   private long memoScanTick = Long.MIN_VALUE;
   private boolean memoScanResult;

   public PrintHandler() {
      super("print", PrintModeType.PRINTER, Configs.Core.PRINT, Configs.Print.PRINT_SELECTION_TYPE, true);
   }

   public SchematicBlockContext getContext() {
      return this.ctx;
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
   protected boolean isSchematicHandler() {
      return true;
   }

   @Override
   protected boolean isVerifiedNoWork(BlockPos pos) {
      return this.level != null && SchematicStateCache.INSTANCE.isVerifiedNoWork(pos, this.level);
   }

   @Override
   protected boolean shouldSkipFromScan(BlockPos pos) {
      if (ScanWhitelistCache.PRINT.active() && this.level != null) {
         BlockState state = SchematicStateCache.INSTANCE.getSchematicState(pos);
         return state != null && !ScanWhitelistCache.PRINT.isWhitelisted(state);
      } else {
         return false;
      }
   }

   @Override
   protected boolean idleBackoffEnabled() {
      return true;
   }

   @Override
   protected boolean hasUrgentRetries() {
      return Configs.Print.PRINT_USE_PACKET.getBooleanValue() && !this.retryTable.isEmpty();
   }

   @Override
   protected int processTargetedScan(int remainingExecs, AtomicReference<Boolean> skipIteration) {
      int sphericalExecuted = this.processSphericalQueueScan(remainingExecs, skipIteration);
      if (skipIteration.get()) {
         return sphericalExecuted;
      } else {
         int budget = remainingExecs <= 0 ? -1 : remainingExecs - sphericalExecuted;
         return remainingExecs > 0 && budget <= 0 ? sphericalExecuted : sphericalExecuted + this.processSameItemScan(budget, skipIteration);
      }
   }

   private int processSphericalQueueScan(int remainingExecs, AtomicReference<Boolean> skipIteration) {
      if (!Configs.Print.SPHERICAL_PLACE.getBooleanValue() || this.level == null || this.player == null) {
         return 0;
      } else {
         PrinterBox box = this.boxRef == null ? null : this.boxRef.get();
         if (box == null) {
            return 0;
         } else {
            List<BlockPos> queue = SchematicStateCache.INSTANCE
               .getPendingPositionsNearToFar(this.player.getX(), this.player.getY(), this.player.getZ());
            if (queue.isEmpty()) {
               return 0;
            } else {
               int executed = 0;
               int attemptLimit = remainingExecs > 0 ? Math.max(remainingExecs * 2, 16) : 64;
               int attempts = 0;
               int timeLimit = this.getIterationTimeLimit();
               long budgetNanos = timeLimit > 0 ? (long)timeLimit * 1000000L : 0L;
               long startNanos = System.nanoTime();

               for (BlockPos pos : queue) {
                  if (skipIteration.get()
                     || remainingExecs > 0 && executed >= remainingExecs
                     || budgetNanos > 0L && attempts % 8 == 0 && System.nanoTime() - startNanos >= budgetNanos) {
                     break;
                  }

                  if (box.contains(pos) && PlayerUtils.canInteracted(pos) && !this.isOnCooldown(pos) && !this.isVerifiedNoWork(pos)) {
                     if (++attempts > attemptLimit) {
                        break;
                     }

                     if (this.canProcessPos(pos)) {
                        this.executeIteration(pos, skipIteration);
                        if (this.lastOutcome == PrintHandler.ExecuteOutcome.PLACED) {
                           executed++;
                        }
                     }
                  }
               }

               return executed;
            }
         }
      }
   }

   private int processSameItemScan(int remainingExecs, AtomicReference<Boolean> skipIteration) {
      if (Configs.Print.PLACE_SAME_ITEM_FIRST.getBooleanValue() && this.activePlacementItem != null && this.level != null) {
         PrinterBox box = this.boxRef == null ? null : this.boxRef.get();
         if (box == null) {
            return 0;
         } else {
            Item item = this.activePlacementItem;
            int executed = 0;
            int attemptLimit = remainingExecs > 0 ? Math.max(remainingExecs * 2, 16) : 64;
            int attempts = 0;
            int timeLimit = this.getIterationTimeLimit();
            long budgetNanos = timeLimit > 0 ? (long)timeLimit * 1000000L : 0L;
            long startNanos = System.nanoTime();

            for (BlockPos pos : SchematicStateCache.INSTANCE.getPendingPositions(item)) {
               if (skipIteration.get()
                  || remainingExecs > 0 && executed >= remainingExecs
                  || budgetNanos > 0L && attempts % 8 == 0 && System.nanoTime() - startNanos >= budgetNanos) {
                  break;
               }

               if (box.contains(pos) && PlayerUtils.canInteracted(pos) && !this.isOnCooldown(pos) && !this.isVerifiedNoWork(pos)) {
                  if (++attempts > attemptLimit) {
                     break;
                  }

                  if (this.canProcessPos(pos)) {
                     this.executeIteration(pos, skipIteration);
                     if (this.lastOutcome == PrintHandler.ExecuteOutcome.PLACED) {
                        executed++;
                     }
                  }
               }
            }

            return executed;
         }
      } else {
         return 0;
      }
   }


   @Override
   protected int processFastRetry(int maxExecs, AtomicReference<Boolean> skipIteration) {
      if (this.level != null && (!this.retryTable.isEmpty() || !this.pendingConfirm.isEmpty())) {
         if (!Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
            this.retryTable.clear();
            this.pendingConfirm.clear();
            return 0;
         } else {
            long now = ClientPlayerTickManager.getCurrentHandlerTime();
            int executed = 0;
            ObjectIterator<Entry> confirmIt = this.pendingConfirm.long2LongEntrySet().iterator();

            while (confirmIt.hasNext()) {
               Entry entry = (Entry)confirmIt.next();
               long key = entry.getLongKey();
               BlockPos pos = BlockPos.of(key);
               if (this.isVerifiedNoWork(pos)) {
                  confirmIt.remove();
               } else if (now >= entry.getLongValue()) {
                  long sentAt = entry.getLongValue() - 5L;
                  confirmIt.remove();
                  this.retryTable.put(key, Math.max(now, sentAt + (long)Math.max(5, getPlaceCooldown())));
               }
            }

            if (this.retryTable.isEmpty()) {
               return executed;
            } else {
               LongArrayList dueKeys = new LongArrayList();
               ObjectIterator var22 = this.retryTable.long2LongEntrySet().iterator();

               while (var22.hasNext()) {
                  Entry entry = (Entry)var22.next();
                  if (now >= entry.getLongValue()) {
                     dueKeys.add(entry.getLongKey());
                  }
               }

               int attemptLimit = maxExecs > 0 ? Math.max(maxExecs * 2, 16) : 64;
               int attempts = 0;
               int timeLimit = this.getIterationTimeLimit();
               long budgetNanos = timeLimit > 0 ? (long)timeLimit * 1000000L : 0L;
               long startNanos = System.nanoTime();

               for (long key : dueKeys.toLongArray()) {
                  if (skipIteration.get()
                     || maxExecs > 0 && executed >= maxExecs
                     || budgetNanos > 0L && attempts % 8 == 0 && System.nanoTime() - startNanos >= budgetNanos) {
                     break;
                  }

                  this.retryTable.remove(key);
                  BlockPos pos = BlockPos.of(key);
                  if (!this.isVerifiedNoWork(pos) && PlayerUtils.canInteracted(pos)) {
                     if (++attempts > attemptLimit) {
                        break;
                     }

                     if (this.canProcessPos(pos)) {
                        this.executeIteration(pos, skipIteration);
                        if (this.lastOutcome == PrintHandler.ExecuteOutcome.PLACED) {
                           executed++;
                        }

                        if (skipIteration.get()) {
                           break;
                        }
                     }
                  }
               }

               return executed;
            }
         }
      } else {
         return 0;
      }
   }

   @Override
   public boolean canProcessPos(BlockPos blockPos) {
      if (!Configs.Print.PLACE_SAME_ITEM_FIRST.getBooleanValue()) {
         this.activePlacementItem = null;
         this.nextPlacementItemTick = 0L;
         this.lastActivePlacementTick = 0L;
      }

      WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
      if (schematic == null) {
         return false;
      } else if (LitematicaUtils.getSchematicBlockState(blockPos) == null) {
         return false;
      } else {
         this.ctx = new SchematicBlockContext(client, this.level, schematic, blockPos);
         if (PrintHandler.SkipListCache.isSkipped(this.ctx.requiredState)) {
            return false;
         } else if (!ScanWhitelistCache.PRINT.isWhitelisted(this.ctx.requiredState)) {
            return false;
         } else if (Configs.Print.PRINT_SKIP_SHULKER.getBooleanValue() && this.ctx.requiredState.getBlock() instanceof ShulkerBoxBlock) {
            return false;
         } else if (Configs.Print.PRINT_SHULKER_AFTER_ORDINARY.getBooleanValue()
            && this.ctx.requiredState.getBlock() instanceof ShulkerBoxBlock
            && PrintTaskController.INSTANCE.hasPendingOrdinaryInRange(true)) {
            return false;
         } else {
            Action waterTask = PrintTaskController.INSTANCE.handle(this.ctx);
            if (waterTask != null) {
               this.action = waterTask;
               this.icePlacementTask = true;
               return true;
            } else {
               this.icePlacementTask = false;
               if (PrintTaskController.INSTANCE.isIcePlaced(blockPos) || PrintTaskController.INSTANCE.isWaitingWater(blockPos)) {
                  return false;
               } else if (PrintTaskController.INSTANCE.isBreaking(blockPos)) {
                  this.action = new Action();
                  return true;
               } else {
                  Action action = Guides.INSTANCE.buildAction(this.ctx).orElse(null);
                  if (action == null) {
                     if (Configs.Print.SAFELY_OBSERVER.getBooleanValue() && this.ctx.requiredState.getBlock() instanceof ObserverBlock) {
                        this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
                     }

                     return false;
                  } else {
                     if (!(action instanceof ClickAction)) {
                        boolean allAir = true;

                        for (Item reqItem : action.getRequiredItems(this.ctx.requiredState.getBlock())) {
                           if (reqItem != null && reqItem != Items.AIR) {
                              allAir = false;
                              break;
                           }
                        }

                        if (allAir) {
                           this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
                           return false;
                        }
                     }

                     Item placementItem = this.getPlacementItem(action);
                     if (placementItem != null && !this.canPlaceItemNow(placementItem)) {
                        return false;
                     } else {
                        this.action = action;
                        return true;
                     }
                  }
               }
            }
         }
      }
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      PrintHandler.ExecuteOutcome outcome = this.doExecute(blockPos, skipIteration);
      this.lastOutcome = outcome;
      this.setExecuteConsumedQuota(outcome == PrintHandler.ExecuteOutcome.PLACED);
      if (Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
         long key = blockPos.asLong();
         long now = ClientPlayerTickManager.getCurrentHandlerTime();
         switch (outcome) {
            case PLACED:
               this.retryTable.remove(key);
               this.pendingConfirm.put(key, now + 5L);
               break;
            case FAILED:
               this.pendingConfirm.remove(key);
               this.retryTable.put(key, now + (long)Math.max(1, getPlaceCooldown()));
               break;
            case DEFERRED:
               this.retryTable.remove(key);
               this.pendingConfirm.remove(key);
         }
      }
   }

   private PrintHandler.ExecuteOutcome doExecute(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
      if (PrintTaskController.INSTANCE.isBreaking(blockPos)) {
         BreakUtils.INSTANCE.add(blockPos);
         this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
         return PrintHandler.ExecuteOutcome.DEFERRED;
      } else {
         if (Configs.Print.PRINT_ONLY_EMPTY_SHULKER.getBooleanValue() && this.ctx.requiredState.getBlock() instanceof ShulkerBoxBlock) {
            ShulkerPlacementGuard.GuardResult guardResult = ShulkerPlacementGuard.INSTANCE.evaluate(this.ctx);
            switch (guardResult) {
               case WAIT_OTHER_BLOCKS:
               case WAIT_CLOSE:
               case WAIT_CONFIRM:
                  this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
                  return PrintHandler.ExecuteOutcome.DEFERRED;
               case NO_EMPTY:
                  this.requestCloudStoreRefill(new Item[]{this.ctx.requiredState.getBlock().asItem()});
                  this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
                  return PrintHandler.ExecuteOutcome.DEFERRED;
               case READY:
            }
         }

         if (Configs.Print.FALLING_CHECK.getBooleanValue() && this.ctx.requiredState.getBlock() instanceof FallingBlock) {
            BlockPos downPos = blockPos.below();
            if (FallingBlock.isFree(this.level.getBlockState(downPos))) {
               MessageUtils.setOverlayMessage(I18n.BLOCK_NO_SUPPORT.getName(this.ctx.getRequiredBlockName().getString()));
               return PrintHandler.ExecuteOutcome.FAILED;
            }

            if (LitematicaUtils.getSchematicBlockState(downPos) == null
               || !BlockStateUtils.statesEqualIgnoreProperties(this.level.getBlockState(downPos), LitematicaUtils.getSchematicBlockState(downPos))) {
               MessageUtils.setOverlayMessage(I18n.BLOCK_MISMATCH.getName(this.ctx.getRequiredBlockName().getString()));
               return PrintHandler.ExecuteOutcome.FAILED;
            }
         }

         Item[] reqItems = this.action.getRequiredItems(this.ctx.requiredState.getBlock());
         Item placementItem = this.getPlacementItem(this.action);
         boolean shulkerReady = Configs.Print.PRINT_ONLY_EMPTY_SHULKER.getBooleanValue()
            && this.ctx.requiredState.getBlock() instanceof ShulkerBoxBlock
            && ShulkerPlacementGuard.INSTANCE.isReady(blockPos);
         // 主手或副手已经拿着需要的物品就不必换手了（副手拿建材的情况由 ActionManager 直接用副手放置）
         if (!shulkerReady
            && !isFreeHandClick(this.action, reqItems)
            && !InventoryUtils.isRequiredItemInHands(this.player, reqItems)
            && !InventoryUtils.switchToItems(this.player, reqItems)) {
            this.requestCloudStoreRefill(reqItems);
            return PrintHandler.ExecuteOutcome.FAILED;
         } else {
            Direction side = this.action.getValidSide(this.level, blockPos);
            if (side == null) {
               return PrintHandler.ExecuteOutcome.FAILED;
            } else if (Configs.Print.PLACE_IN_AIR.getBooleanValue()
               && !this.action.requiresSupport()
               && !(this.action instanceof ClickAction)
               && !BlockUtils.isReplaceable(this.level.getBlockState(blockPos))) {
               return PrintHandler.ExecuteOutcome.FAILED;
            } else {
               boolean ridingGhast = GhastRideState.riddenGhast(this.player) != null;
               if (GhastShiftBlacklist.contains(this.player, blockPos)) {
                  return PrintHandler.ExecuteOutcome.DEFERRED;
               } else {
                  boolean useShift;
                  if (this.action.getShift() == null) {
                     useShift = Implementation.isInteractive(this.level.getBlockState(blockPos.relative(side)).getBlock())
                           && !(this.action instanceof ClickAction)
                        || Configs.Print.PRINT_FORCED_SNEAK.getBooleanValue() && !ridingGhast;
                  } else {
                     useShift = this.action.getShift();
                  }

                  if (useShift && ridingGhast) {
                     GhastShiftBlacklist.add(this.player, blockPos);
                     return PrintHandler.ExecuteOutcome.DEFERRED;
                  } else {
                     this.action.setActionSource(ActionManager.ActionSource.PRINT);
                     this.action.queueAction(blockPos, side, useShift, this.player, reqItems);
                     if (this.icePlacementTask) {
                        ActionManager.INSTANCE.setQueueCompletionListener(sendResultx -> {
                           if (sendResultx.isSent()) {
                              PrintTaskController.INSTANCE.onIcePlaceSent(blockPos);
                           }
                        });
                     }

                     Vec3 hitModifier = LitematicaUtils.usePrecisionPlacement(blockPos, this.ctx.requiredState);
                     if (hitModifier != null) {
                        ActionManager.INSTANCE.hitModifier = hitModifier;
                        ActionManager.INSTANCE.useProtocol = true;
                     }

                     ActionManager.INSTANCE
                        .setQueuedDirectionalPlacement(Configs.Print.PRINT_FAST_DIRECTIONAL_PLACEMENT.getBooleanValue() && this.action.isDirectional());
                     ActionManager.INSTANCE.setLook(this.action.getPlayerLook());
                     ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(this.action.getNeedWaitModifyLook());
                     ActionManager.INSTANCE.setWaitForHorizontalLook(this.action.isWaitForHorizontalLook());
                     ActionManager.SendResult sendResult = ActionManager.INSTANCE.sendQueue(this.player);
                     if (sendResult.isSent() && Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
                        PacketSoundConfirmationTracker.trackPlacement(blockPos, this.ctx.requiredState);
                     }

                     if (sendResult.isSent() && placementItem != null && Configs.Print.PLACE_SAME_ITEM_FIRST.getBooleanValue()) {
                        this.activePlacementItem = placementItem;
                        this.lastActivePlacementTick = this.level.getGameTime();
                     }

                     if (sendResult.isWaiting() || sendResult == ActionManager.SendResult.RESERVE_LIMIT) {
                        skipIteration.set(true);
                     }

                     if (this.action.getCooldownTicksOverride() >= 0) {
                        this.setCooldown(blockPos, this.action.getCooldownTicksOverride());
                     } else {
                        this.setCooldown(blockPos, ConfigUtils.getPlaceCooldown());
                     }

                     return sendResult.isSent() ? PrintHandler.ExecuteOutcome.PLACED : PrintHandler.ExecuteOutcome.FAILED;
                  }
               }
            }
         }
      }
   }

   @Nullable
   private Item getPlacementItem(Action action) {
      Item targetItem = this.ctx.requiredState.getBlock().asItem();
      if (targetItem == Items.AIR) {
         return null;
      } else {
         Item[] requiredItems = action.getRequiredItems(this.ctx.requiredState.getBlock());
         if (isFreeHandClick(action, requiredItems)) {
            return null;
         } else {
            for (Item requiredItem : requiredItems) {
               if (requiredItem == targetItem) {
                  return targetItem;
               }
            }

            return null;
         }
      }
   }

   private boolean canPlaceItemNow(Item item) {
      long tick = this.level.getGameTime();
      if (tick < this.nextPlacementItemTick) {
         return false;
      } else if (this.activePlacementItem != null && this.activePlacementItem != item) {
         if (this.hasPendingPlacement(this.activePlacementItem)
            && tick - this.lastActivePlacementTick <= Math.max((long)Configs.Print.ITEM_SWITCH_INTERVAL.getIntegerValue() * 5L, 1L)) {
            return false;
         } else {
            this.activePlacementItem = null;
            int interval = Configs.Print.ITEM_SWITCH_INTERVAL.getIntegerValue();
            this.nextPlacementItemTick = tick + (long)interval;
            return interval == 0;
         }
      } else {
         return true;
      }
   }

   private boolean hasPendingPlacement(Item item) {
      WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
      PrinterBox box = this.boxRef == null ? null : this.boxRef.get();
      if (schematic != null && box != null) {
         int boxId = System.identityHashCode(box);
         long now = this.level.getGameTime();
         long revision = SchematicStateCache.INSTANCE.getRevision();
         if (this.memoScanItem == item && this.memoScanBoxId == boxId && this.memoScanRevision == revision && now - this.memoScanTick < 10L) {
            return this.memoScanResult;
         } else {
            boolean result = false;

            for (BlockPos pos : SchematicStateCache.INSTANCE.getPendingPositions(item)) {
               if (box.contains(pos) && PlayerUtils.canInteracted(pos)) {
                  BlockState required = LitematicaUtils.getSchematicBlockState(pos);
                  if (required != null
                     && required.getBlock().asItem() == item
                     && !BlockStateUtils.statesEqualIgnoreProperties(this.level.getBlockState(pos), required)) {
                     result = true;
                     break;
                  }
               }
            }

            if (!result) {
               for (BlockPos posx : box) {
                  if (PlayerUtils.canInteracted(posx) && LitematicaUtils.isSchematicBlock(posx)) {
                     BlockState required = LitematicaUtils.getSchematicBlockState(posx);
                     if (required != null
                        && required.getBlock().asItem() == item
                        && !BlockStateUtils.statesEqualIgnoreProperties(this.level.getBlockState(posx), required)) {
                        result = true;
                        break;
                     }
                  }
               }
            }

            this.memoScanItem = item;
            this.memoScanBoxId = boxId;
            this.memoScanRevision = revision;
            this.memoScanTick = now;
            this.memoScanResult = result;
            return result;
         }
      } else {
         return false;
      }
   }

   private static boolean isFreeHandClick(Action action, Item[] reqItems) {
      if (!(action instanceof ClickAction)) {
         return false;
      } else if (reqItems == null) {
         return true;
      } else {
         for (Item item : reqItems) {
            if (item != null && item != Items.AIR) {
               return false;
            }
         }

         return true;
      }
   }

   private Set<Item> collectMissingMaterials() {
      PrinterBox box = this.boxRef == null ? null : this.boxRef.get();
      if (box == null) {
         return new HashSet<>();
      } else if (InventoryUtils.hasRecentlyOpenedShulker(this.player)) {
         return new HashSet<>();
      } else {
         int timeLimit = this.getIterationTimeLimit();
         long budgetNanos = timeLimit > 0 ? (long)timeLimit * 1000000L : 0L;
         long startNanos = System.nanoTime();
         Set<Item> required = new HashSet<>();
         int scanned = 0;

         for (BlockPos pos : box) {
            scanned++;
            if (scanned > 20000 || budgetNanos > 0L && scanned % 256 == 0 && System.nanoTime() - startNanos >= budgetNanos) {
               break;
            }

            if (PlayerUtils.canInteracted(pos)
               && LitematicaUtils.isSchematicBlock(pos)
               && (this.getSelectionType() == null || PlayerUtils.isPositionInSelectionRange(this.player, pos, this.getSelectionType()))) {
               Item[] reqItems = this.getRequiredItemsFor(pos);
               if (reqItems != null) {
                  for (Item reqItem : reqItems) {
                     if (reqItem != null && reqItem != Items.AIR) {
                        required.add(reqItem);
                     }
                  }
               }
            }
         }

         Set<Item> missing = new HashSet<>();

         for (Item reqItemx : required) {
            if (InventoryUtils.countAvailableIncludingShulkers(this.player, reqItemx) == 0) {
               missing.add(reqItemx);
            }
         }

         return missing;
      }
   }

   @Nullable
   private Item[] getRequiredItemsFor(BlockPos pos) {
      WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
      if (schematic == null) {
         return null;
      } else {
         SchematicBlockContext context = new SchematicBlockContext(client, this.level, schematic, pos);
         if (PrintHandler.SkipListCache.isSkipped(context.requiredState)) {
            return null;
         } else if (Configs.Print.PRINT_SKIP_SHULKER.getBooleanValue() && context.requiredState.getBlock() instanceof ShulkerBoxBlock) {
            return null;
         } else {
            BlockState required = context.requiredState;
            if (!required.isAir() && !(required.getBlock() instanceof LiquidBlock)) {
               BlockMatchResult match = BlockMatchResult.compare(context);
               return match != BlockMatchResult.MISSING && match != BlockMatchResult.WRONG_BLOCK ? null : new Item[]{required.getBlock().asItem()};
            } else {
               return null;
            }
         }
      }
   }

   private void requestCloudStoreRefill(Item[] reqItems) {
      if (Configs.Special.PRINT_CLOUD_STORE_REFILL.getBooleanValue() && !CloudStoreUtils.isRefillInCooldown()) {
         Set<Item> missing = this.collectMissingMaterials();
         if (reqItems != null) {
            for (Item reqItem : reqItems) {
               if (reqItem != null
                  && reqItem != Items.AIR
                  && InventoryUtils.countAvailableIncludingShulkers(this.player, reqItem) == 0
                  && !InventoryUtils.hasRecentlyOpenedShulker(this.player)) {
                  missing.add(reqItem);
               }
            }
         }

         if (!missing.isEmpty()) {
            CloudStoreUtils.tryRequestRefillMany(this.player, missing, Configs.Special.PRINT_CLOUD_STORE_REFILL_AMOUNT.getIntegerValue());
         }
      }
   }

   @Generated
   public boolean isPistonNeedFix() {
      return this.pistonNeedFix;
   }

   @Generated
   public void setPistonNeedFix(boolean pistonNeedFix) {
      this.pistonNeedFix = pistonNeedFix;
   }

   @Generated
   public boolean isPrinterMemorySync() {
      return this.printerMemorySync;
   }

   @Generated
   public void setPrinterMemorySync(boolean printerMemorySync) {
      this.printerMemorySync = printerMemorySync;
   }

   private static enum ExecuteOutcome {
      PLACED,
      FAILED,
      DEFERRED;
   }

   private static final class SkipListCache {
      private static List<String> source = List.of();
      private static boolean enabled;
      private static List<String> patterns = List.of();
      private static final Map<BlockState, Boolean> matchCache = new HashMap<>();

      static boolean isSkipped(BlockState requiredState) {
         boolean en = Configs.Print.PRINT_SKIP.getBooleanValue();
         List<String> cur = Configs.Print.PRINT_SKIP_LIST.getStrings();
         if (en != enabled || cur.size() != source.size() || !cur.equals(source)) {
            enabled = en;
            source = List.copyOf(cur);
            patterns = List.copyOf(cur);
            matchCache.clear();
         }

         return !en ? false : matchCache.computeIfAbsent(requiredState, st -> {
            for (String s : patterns) {
               if (PinYinSearchUtils.matchName(s, st)) {
                  return true;
               }
            }

            return false;
         });
      }
   }
}
