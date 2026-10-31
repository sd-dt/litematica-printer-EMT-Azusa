package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.concurrent.atomic.AtomicReference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * ══════════════════════════════════════════════════════════════════════
 *  排流体基础逻辑 —— 冻结区（FROZEN），后续改动不得修改本文件。
 * ══════════════════════════════════════════════════════════════════════
 *  <p>语义移植自 BiliXWhite/litematica-printer（zhaixianyu 线）的
 *  {@code Printer#replaceMode()} + {@code FLUID_BLOCK_LIST}：
 *  以玩家为中心扫一个方盒（水平半径 = workRange + fluidScanMargin、垂直 {@link #DY_MIN} ~ 水平半径），
 *  凡是世界状态命中「流体列表」（来源方块），就用「流体填充方块列表」里的物品对准该格右键放置，
 *  把流体顶掉；流动液体是否处理由「包含流动液体」开关决定（对应 BiliXWhite 的
 *  {@code fluidState.isEmpty() || isSource()} 限定）。</p>
 *
 *  <p>与旧基础逻辑的区别：旧版只依赖处理器迭代（实测只吐空气格，水面格根本
 *  进不来，表现为"排流体开着却什么都不干"）；BiliXWhite 的逻辑是<b>世界状态驱动</b>
 *  的主动扫描，所以这里改为自扫描。放置动作仍走本模组统一的 Action 流水线。</p>
 *
 *  <p>当「更好的排流体」「秒破方块排流体」两个开关都关闭时，排流体只跑本类，
 *  行为稳定、无状态、不残留任何队列。</p>
 *
 *  <p><b>2026-10-24 的两处修正</b>（不触碰上面的 BiliXWhite replaceMode 语义）：
 *  ① 水平半径从硬编码 8 改为 {@code workRange + fluidScanMargin}（与另两条路径一致）——
 *     原来相对原版交互距离 5 正好外扩 3 格，就是「选区偏移多出去三格 / 打出多余方块」；
 *  ② 补上 {@link FluidHandler#isWithinWorkRange} 选区过滤 —— 自扫描原先完全不受
 *     litematica 选区约束，两开关都关时会一路排到选区外面去。</p>
 */
public final class FluidDrainBase {
   private FluidDrainBase() {
   }

   /** 垂直下界（水只往下流，够用）；上界跟随水平半径 —— 水也可能在玩家上方（水墙/水柱） */
   private static final int DY_MIN = -3;

   /**
    * 基础逻辑的自扫描：每 tick 最多发出一个放置动作（与 BiliXWhite replaceMode 的节奏一致）。
    */
   static void tickScan(FluidHandler handler) {
      if (handler.world() == null || handler.player() == null) {
         return;
      }

      if (handler.fillItems.isEmpty() || handler.fluids.isEmpty()) {
         return;
      }

      BlockPos origin = handler.player().blockPosition();
      int range = FluidHandler.scanRange(handler);

      // 上界用水平半径：原来只扫到 +1，玩家上方（水墙/水柱）的格不进入候选，
      // 表现为「必须飞到水源上方才排得动」
      for (int dy = DY_MIN; dy <= range; dy++) {
         for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (handler.isOnCooldown(pos)) {
                  continue;
               }

               // ★ 2026-10-24 补：与 iterateBlocks / tickSimplePlacements / FluidInstantBreak
               //   同一套选区判定。缺了它，基础逻辑会以玩家为中心扫满整个方盒，
               //   完全不管 litematica 选区 —— 选区外远处的水也会被铺掉（"打出多余方块"）。
               if (!handler.isWithinWorkRange(pos)) {
                  continue;
               }

               if (!matchesFluid(handler, pos)) {
                  continue;
               }

               // 够不着的格位跳过：放置包发出去也不会生效，只会白占动作配额
               if (!PlayerUtils.canInteracted(pos)) {
                  continue;
               }

               if (tryPlace(handler, pos, null)) {
                  return;
               }
            }
         }
      }
   }

   /** 该格是否是需要排的流体（含"是否处理流动液体"判定） */
   static boolean matchesFluid(FluidHandler handler, BlockPos pos) {
      FluidState fluidState = handler.world().getBlockState(pos).getFluidState();
      if (fluidState.isEmpty() || !handler.fluids.contains(fluidState.getType())) {
         return false;
      }

      return Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() || fluidState.isSource();
   }

   /**
    * 对单个格位执行"切填充物 → 右键放置"（BiliXWhite 分支 B 的放置半段）。
    *
    * @param skipIteration 迭代模式下等待服务器回包时置 true；自扫描模式传 null
    * @return 是否已处理（发出放置 / 等待回包 / 无法切到填充物时为 false）
    */
   static boolean tryPlace(FluidHandler handler, BlockPos pos, AtomicReference<Boolean> skipIteration) {
      if (!InventoryUtils.switchToItems(handler.player(), handler.fillItems.toArray(new Item[0]))) {
         return false;
      }

      Action action = FluidHandler.createFillAction(handler, pos);
      ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
      if (ActionManager.INSTANCE.sendQueue(handler.player()).isWaiting()) {
         if (skipIteration != null) {
            skipIteration.set(true);
         }
      } else {
         handler.setCooldown(pos, Fluids.WATER.getTickDelay(handler.world()) * 2);
      }

      return true;
   }
}
