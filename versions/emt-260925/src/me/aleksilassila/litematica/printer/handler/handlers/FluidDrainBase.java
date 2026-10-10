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
 *  以玩家为中心扫一个方盒（水平半径 8、垂直 -3 ~ +1），凡是世界状态命中
 *  「流体列表」（来源方块），就用「流体填充方块列表」里的物品对准该格右键放置，
 *  把流体顶掉；流动液体是否处理由「包含流动液体」开关决定（对应 BiliXWhite 的
 *  {@code fluidState.isEmpty() || isSource()} 限定）。</p>
 *
 *  <p>与旧基础逻辑的区别：旧版只依赖处理器迭代（实测只吐空气格，水面格根本
 *  进不来，表现为"排流体开着却什么都不干"）；BiliXWhite 的逻辑是<b>世界状态驱动</b>
 *  的主动扫描，所以这里改为自扫描。放置动作仍走本模组统一的 Action 流水线。</p>
 *
 *  <p>当「更好的排流体」「秒破方块排流体」两个开关都关闭时，排流体只跑本类，
 *  行为稳定、无状态、不残留任何队列。</p>
 */
public final class FluidDrainBase {
   private FluidDrainBase() {
   }

   private static final int RANGE = 8;
   private static final int DY_MIN = -3;
   private static final int DY_MAX = 1;

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

      for (int dy = DY_MIN; dy <= DY_MAX; dy++) {
         for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (handler.isOnCooldown(pos)) {
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

      Action action = new Action()
         .setActionSource(ActionManager.ActionSource.FLUID)
         .queueAction(pos, Direction.UP, false, handler.player());
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
