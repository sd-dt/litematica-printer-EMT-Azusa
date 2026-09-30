package me.aleksilassila.litematica.printer.handler.handlers;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction.ListType;
import fi.dy.masa.tweakeroo.config.Configs.Lists;
import fi.dy.masa.tweakeroo.tweaks.PlacementTweaks;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.ExcavateListMode;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.mixin_extension.BlockBreakResult;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.BlockPosCooldownManager;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class MineHandler extends ClientPlayerTickHandler {
   public static final String NAME = "mine";
   private final MineBreakExecutor analyzer = new MineBreakExecutor();
   private final MineToolSession toolSession = new MineToolSession();
   private final List<MineBreakExecutor.Target> candidates = new ArrayList<>();
   @Nullable
   private BlockPos activeMinePos;

   public MineHandler() {
      super("mine", PrintModeType.MINE, Configs.Core.MINE, Configs.Mine.MINE_SELECTION_TYPE, true);
   }

   private boolean isParallelMode() {
      return Configs.Mine.BREAK_PARALLEL.getBooleanValue();
   }

   public static boolean mineRestriction(BlockState blockState) {
      return mineRestriction(null, blockState);
   }

   public static boolean mineRestriction(@Nullable BlockPos pos, BlockState blockState) {
      if (!BreakUtils.breakRestriction(Minecraft.getInstance().level, pos, blockState)) {
         return false;
      } else if (Configs.Mine.EXCAVATE_LIMITER.getOptionListValue().equals(ExcavateListMode.TWEAKEROO)) {
         if (!ModUtils.isTweakerooLoaded()) {
            return true;
         } else {
            ListType listType = PlacementTweaks.BLOCK_TYPE_BREAK_RESTRICTION.getListType();
            if (listType == ListType.BLACKLIST) {
               return Lists.BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST
                  .getStrings()
                  .stream()
                  .noneMatch(string -> BreakUtils.matchesRule(string, Minecraft.getInstance().level, pos, blockState));
            } else {
               return listType == ListType.WHITELIST
                  ? Lists.BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST
                     .getStrings()
                     .stream()
                     .anyMatch(string -> BreakUtils.matchesRule(string, Minecraft.getInstance().level, pos, blockState))
                  : true;
            }
         }
      } else {
         IConfigOptionListEntry optionListValue = Configs.Mine.EXCAVATE_LIMIT.getOptionListValue();
         if (optionListValue == ListType.BLACKLIST) {
            return Configs.Mine.EXCAVATE_BLACKLIST
               .getStrings()
               .stream()
               .noneMatch(string -> BreakUtils.matchesRule(string, Minecraft.getInstance().level, pos, blockState));
         } else {
            return optionListValue == ListType.WHITELIST
               ? Configs.Mine.EXCAVATE_WHITELIST
                  .getStrings()
                  .stream()
                  .anyMatch(string -> BreakUtils.matchesRule(string, Minecraft.getInstance().level, pos, blockState))
               : true;
         }
      }
   }

   @Override
   protected int getTickInterval() {
      return Configs.Mine.BREAK_INTERVAL.getIntegerValue();
   }

   @Override
   protected int getMaxExecutions() {
      return this.isParallelMode() ? 0 : Configs.Mine.BREAK_BLOCKS_PER_TICK.getIntegerValue();
   }

   @Override
   protected boolean canIterate() {
      if (Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() && BreakUtils.isPlayerMining()) {
         return false;
      } else {
         return !this.isParallelMode() ? true : this.activeMinePos == null && !BreakUtils.INSTANCE.hasActiveDestroyTarget();
      }
   }

   @Override
   protected void preprocess() {
      if (this.isParallelMode()) {
         this.candidates.clear();
         this.analyzer.beginTick();
         this.toolSession.beginTick();
         this.continueActiveMineTarget();
      }
   }

   @Override
   public boolean canProcessPos(BlockPos pos) {
      if (Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() && BreakUtils.isPlayerMining()) {
         return false;
      } else {
         return !this.isOnCooldown(pos) && !BlockPosCooldownManager.INSTANCE.isOnCooldown(this.level, "fluid", pos)
            ? BreakUtils.canBreakBlock(pos) && mineRestriction(pos, this.level.getBlockState(pos))
            : false;
      }
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      if (this.isParallelMode()) {
         if (!BreakUtils.INSTANCE.isRecentlyBroken(blockPos) && !BreakUtils.INSTANCE.isPendingDelayedDestroy(blockPos)) {
            MineBreakExecutor.Target target = this.analyzer.analyze(blockPos);
            if (target != null) {
               this.candidates.add(target);
            }
         }
      } else {
         BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlock(blockPos);
         if (result == BlockBreakResult.IN_PROGRESS || result == BlockBreakResult.COMPLETED_WAIT) {
            skipIteration.set(true);
         }

         this.setCooldown(blockPos, ConfigUtils.getBreakCooldown());
      }
   }

   @Override
   protected void stopIteration(boolean interrupt) {
      if (this.isParallelMode() && !interrupt) {
         if (!ActionManager.INSTANCE.needWaitModifyLook && this.activeMinePos == null && !this.candidates.isEmpty()) {
            this.candidates.sort(this.toolSession.comparator(this.player));
            MineBreakExecutor.Target nearest = this.candidates.get(0);
            MineBreakExecutor.Target selected = this.toolSession.selectTarget(this.candidates, this.analyzer, this.player);
            this.executeToolSession(selected, MineToolSession.distanceScore(this.player, nearest));
         }
      }
   }

   private void continueActiveMineTarget() {
      BlockPos pos = this.activeMinePos;
      if (pos != null) {
         if (!this.canContinueActiveMineTarget(pos)) {
            this.activeMinePos = null;
         } else {
            BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlockForMine(pos, Direction.DOWN, true);
            if (result == BlockBreakResult.IN_PROGRESS || result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
               this.toolSession.consumeAction();
            }

            this.toolSession.onTargetResolved(result, pos);
            if (result != BlockBreakResult.IN_PROGRESS) {
               this.activeMinePos = null;
               this.setCooldown(pos, ConfigUtils.getBreakCooldown());
            }
         }
      }
   }

   private boolean canContinueActiveMineTarget(BlockPos pos) {
      return pos != null && PlayerUtils.canInteracted(pos) && BreakUtils.canBreakBlock(pos) && mineRestriction(pos, this.level.getBlockState(pos));
   }

   private void executeToolSession(MineBreakExecutor.Target firstTarget, double nearestDistance) {
      this.toolSession.startSession(firstTarget);
      if (this.toolSession.ensureHandToolProtected(this.player, firstTarget)) {
         BlockBreakResult result = this.executeSessionTarget(firstTarget, !this.analyzer.isCurrentToolEffective(firstTarget));
         if (!this.toolSession.shouldStop(result, this.activeMinePos != null)) {
            for (MineBreakExecutor.Target target : this.candidates) {
               if (target != firstTarget) {
                  if (!this.toolSession.hasInstantBudget()) {
                     break;
                  }

                  if (this.toolSession.matchesSessionTool(this.analyzer, target)) {
                     if (!this.toolSession.isInsideFrontier(this.player, target, nearestDistance)
                        || !this.toolSession.ensureHandToolProtected(this.player, target)) {
                        break;
                     }

                     result = this.executeSessionTarget(target, false);
                     if (this.toolSession.shouldStop(result, this.activeMinePos != null)) {
                        break;
                     }
                  }
               }
            }
         }
      }
   }

   private BlockBreakResult executeSessionTarget(MineBreakExecutor.Target target, boolean allowToolSwitch) {
      boolean switchForRecovery = this.player != null && target.shouldSwitchToRecoveryTool(this.player.getMainHandItem());
      BlockBreakResult result = this.executeMineTarget(target, allowToolSwitch || switchForRecovery);
      if (result != BlockBreakResult.FAILED) {
         this.setCooldown(target.pos(), ConfigUtils.getBreakCooldown());
      }

      if (result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
         this.toolSession.consumeInstantBudget();
      }

      if (result == BlockBreakResult.IN_PROGRESS || result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
         this.toolSession.consumeAction();
      }

      this.toolSession.onTargetResolved(result, target.pos());
      return result;
   }

   private BlockBreakResult executeMineTarget(MineBreakExecutor.Target target, boolean allowToolSwitch) {
      BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlockForMine(target.pos(), Direction.DOWN, allowToolSwitch);
      if (result == BlockBreakResult.IN_PROGRESS) {
         this.activeMinePos = target.pos();
      }

      return result;
   }
}
