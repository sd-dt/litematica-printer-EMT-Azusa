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
import net.minecraft.world.level.block.Blocks;
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

   private static boolean isSuspiciousBlock(BlockState state) {
      return state.is(Blocks.SUSPICIOUS_SAND) || state.is(Blocks.SUSPICIOUS_GRAVEL);
   }

   /**
    * 考古模式保护判定（{@link Configs.Mine#ARCHAEOLOGY_MODE} 开启时生效）：
    * 可疑沙子/可疑砂砾本身，以及它们正下方的整列方块一律不挖，其余无关方块照常破坏。
    * 判定方式：本格是可疑方块 → 保护；否则沿本格向上扫到建筑上限，
    * 该列任何一处出现可疑方块 → 本格视为「其下方的方块」而保护。
    * 仅在考古模式开启时才会逐格向上扫，默认关闭零开销。
    */
   private boolean isArchaeologyProtected(BlockPos pos, BlockState state) {
      if (!Configs.Mine.ARCHAEOLOGY_MODE.getBooleanValue()) {
         return false;
      } else if (isSuspiciousBlock(state)) {
         return true;
      } else {
         int top = this.level.getMaxY();
         BlockPos.MutableBlockPos cursor = pos.mutable();
         while (cursor.getY() < top) {
            cursor.move(Direction.UP);
            if (isSuspiciousBlock(this.level.getBlockState(cursor))) {
               return true;
            }
         }

         return false;
      }
   }

   @Override
   public boolean canProcessPos(BlockPos pos) {
      if (Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() && BreakUtils.isPlayerMining()) {
         return false;
      } else if (this.isOnCooldown(pos) || BlockPosCooldownManager.INSTANCE.isOnCooldown(this.level, "fluid", pos)) {
         return false;
      } else {
         BlockState state = this.level.getBlockState(pos);
         return BreakUtils.canBreakBlock(pos)
            && mineRestriction(pos, state)
            && !this.isArchaeologyProtected(pos, state);
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
         // ★ 与排流体的破坏队列互斥：队列正在挖别的格位时这边先等一拍。
         //   两边同时挥镐（目标不同）会让客户端不断 STOP→START，挖掘进度反复清零，
         //   表现就是"每个方块只挖一半就换下一个"，来回横跳直到卡死。
         if (BreakUtils.INSTANCE.hasActiveDestroyTarget() && !BreakUtils.INSTANCE.isDestroyingPos(blockPos)) {
            skipIteration.set(true);
            return;
         }

         BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlock(blockPos);
         if (result == BlockBreakResult.IN_PROGRESS) {
            // 本格还在挖：滚动锁住排流体的破坏队列，防止它中途插队换目标
            BreakUtils.INSTANCE.suppressQueuedBreaks(2);
         }

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
            // 正在挖的这一格不再请求切换工具：换手会立刻清零挖掘进度，
            // 表现就是"换完工具卡一下才挖得动"、甚至看着像"工具有时候不切换"。
            // 这一格该用什么工具，在"会话"开始时（executeToolSession）已经判定过了。
            // ★ 与排流体的破坏队列互斥：队列正在挖别的格位时先按住不动（保持会话，不换目标），
            //   否则两边交替挥镐会把彼此的挖掘进度反复清零，"只挖一半就换"。
            if (BreakUtils.INSTANCE.hasActiveDestroyTarget() && !BreakUtils.INSTANCE.isDestroyingPos(pos)) {
               return;
            }

            BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlockForMine(pos, Direction.DOWN, false);
            if (result == BlockBreakResult.IN_PROGRESS) {
               // 本格还在挖：滚动锁住排流体的破坏队列
               BreakUtils.INSTANCE.suppressQueuedBreaks(2);
               this.toolSession.consumeAction();
            } else if (result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
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
      if (pos == null || !PlayerUtils.canInteracted(pos) || !BreakUtils.canBreakBlock(pos)) {
         return false;
      } else {
         BlockState state = this.level.getBlockState(pos);
         return mineRestriction(pos, state) && !this.isArchaeologyProtected(pos, state);
      }
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
      // ★ 与排流体的破坏队列互斥：队列正在挖别的格位时这边只等待，不挥镐不换目标
      if (BreakUtils.INSTANCE.hasActiveDestroyTarget() && !BreakUtils.INSTANCE.isDestroyingPos(target.pos())) {
         return BlockBreakResult.ABORTED;
      }

      BlockBreakResult result = BreakUtils.INSTANCE.continueDestroyBlockForMine(target.pos(), Direction.DOWN, allowToolSwitch);
      if (result == BlockBreakResult.IN_PROGRESS) {
         // 本格还在挖：滚动锁住排流体的破坏队列
         BreakUtils.INSTANCE.suppressQueuedBreaks(2);
         this.activeMinePos = target.pos();
      }

      return result;
   }
}
