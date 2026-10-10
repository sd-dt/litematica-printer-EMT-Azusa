package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.VegetationBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 秒破方块排流体 —— 移植自 LPCTools（Le-Petit-C/LPCTools）的
 * {@code liquidCleaner}（液体清理）工具，语义保持一致：
 *
 * <ul>
 *   <li><b>秒破段</b>（{@code shouldAttackBlock} + {@code startDestroyBlock}）：
 *       只碰"一击必碎"的格子（{@code getDestroyProgress >= 1}）——
 *       ① 可替换且含液体的方块（海草/海带这类）直接挖；
 *       ② 硬度 0 的"清理方块"，且六向邻居都没有液体时挖（防止挖开瞬间被渗满）。</li>
 *   <li><b>填充段</b>（{@code useItemOn}）：把手上的清理方块对准液体格右键放置，
 *       把液体顶掉；「包含流动液体」关闭时只处理水源（对应 {@code liquidSourceOnly}）。</li>
 * </ul>
 *
 * <p>清理方块的选取规则照抄 LPCTools {@code LiquidCleanerData#isDefaultCleaningBlock}：
 * 硬度 0、非空气、自身不含流体、不能被任何液体冲掉、不可含水、非植被。
 * 优先从用户的「流体填充方块列表」里筛；筛不出任何方块时退回全注册表扫描
 * （与 LPCTools 的默认列表同源）。</p>
 *
 * <p>与本模组的差异（适配说明）：LPCTools 以玩家交互距离为工作区域、按 ShapeList
 * 限定范围；这里复用排流体的自扫描方盒（水平半径 8、垂直 -3 ~ +1），
 * 破坏走本模组统一的强制破坏队列（{@link BreakUtils#addForced}）。</p>
 */
public final class FluidInstantBreak {
   private FluidInstantBreak() {
   }

   private static final int RANGE = 8;
   private static final int DY_MIN = -3;
   private static final int DY_MAX = 1;
   /**
    * 每 tick 最多秒破几个格子 —— 抄挖掘部分的节奏：破坏队列对"能秒破"的方块
    * （挖掘进度已满）会在同一 tick 内连续 pop 多个，放宽预算让视觉上一次碎一片。
    */
   private static final int MAX_ATTACKS_PER_TICK = 16;
   /** 每 tick 最多填充几个液体格 */
   private static final int MAX_FILLS_PER_TICK = 8;

   /** 清理物品（切换到手上用于填充液体） */
   private static List<Item> cleaningItems = List.of();
   /** 清理方块集合（秒破段判定"这一格是不是我们放的清理方块"） */
   private static Set<Block> cleaningBlocks = Set.of();
   /** 缓存签名：用户的填充方块列表变了才重算清理方块 */
   private static List<String> lastFillBlocks = List.of();

   static void tick(FluidHandler handler) {
      if (handler.world() == null || handler.player() == null) {
         return;
      }

      refreshCleaningItems(handler);
      if (cleaningItems.isEmpty()) {
         FluidHandler.diag("秒破排流体：没有可用的清理方块，跳过");
         return;
      }

      // ★ 主破坏流程正在挖：秒破段的 attack 会切工具（清掉那一格的挖掘进度），
      //   填充段也要切清理方块 —— 两段都必须让路，等这一格挖完（通常 1 tick）。
      //   以前 tickAttacks 在守卫之前跑，破坏中还会往破坏队列塞格+切手，拖慢自己也可能打断主流程。
      if (BreakUtils.INSTANCE.hasActiveDestroyTarget()) {
         return;
      }

      int attacks = tickAttacks(handler);
      tickFills(handler);
      if (attacks > 0 && Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
         FluidHandler.diag("秒破排流体：本 tick 秒破 " + attacks + " 格");
      }
   }

   /** ── 秒破段（LPCTools: shouldAttackBlock + manager.startDestroyBlock）── */
   private static int tickAttacks(FluidHandler handler) {
      int attacks = 0;
      BlockPos origin = handler.player().blockPosition();

      // 层高高的先排（自上而下）：上层清干净了，下面的水失去遮拦也更容易顺流排掉
      for (int dy = DY_MAX; dy >= DY_MIN && attacks < MAX_ATTACKS_PER_TICK; dy--) {
         for (int dx = -RANGE; dx <= RANGE && attacks < MAX_ATTACKS_PER_TICK; dx++) {
            for (int dz = -RANGE; dz <= RANGE && attacks < MAX_ATTACKS_PER_TICK; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (handler.isOnCooldown("instantBreak", pos)) {
                  continue;
               }

               if (!shouldAttackBlock(handler, pos)) {
                  continue;
               }

               attack(handler, pos);
               attacks++;
            }
         }
      }

      return attacks;
   }

   private static boolean shouldAttackBlock(FluidHandler handler, BlockPos pos) {
      BlockState state = handler.world().getBlockState(pos);
      if (state.isAir()) {
         return false;
      }

      if (!PlayerUtils.canInteracted(pos)) {
         return false;
      }

      // ★ 只在投影工作范围内干活（与更好的排流体同一套选区判定）
      if (!handler.isWithinWorkRange(pos)) {
         return false;
      }

      // LPCTools canBreakInstantly：只秒破（挖掘进度已满 1.0 的格子）
      if (PlayerUtils.getDestroyProgress(handler.player(), state) < 1.0F) {
         return false;
      }

      // 海草/海带这类"可替换 + 含液体"的方块：直接挖掉
      if (state.canBeReplaced() && isContainingLiquid(state)) {
         return true;
      }

      // 硬度 0 的清理方块：邻居还有液体时先别拆（挖开会被渗满）
      if (!cleaningBlocks.contains(state.getBlock())) {
         return false;
      }

      for (Direction direction : Direction.values()) {
         if (isContainingLiquid(handler.world().getBlockState(pos.relative(direction)))) {
            return false;
         }
      }

      return true;
   }

   private static void attack(FluidHandler handler, BlockPos pos) {
      BlockState state = handler.world().getBlockState(pos);
      // 工具切换与主破坏流程同一入口（更好的排流体也是这么接的）
      BreakUtils.autoSwitchToolIfEnabled(pos, state);
      // 排流体盖住的格位必然紧邻流体，走普通判定会被「不挖掘流体」保护拦下，用强制挖除
      BreakUtils.INSTANCE.addForced(pos);
      handler.setCooldown("instantBreak", pos, 10);
      FluidHandler.diag("秒破 @" + pos.toShortString() + " 方块=" + state.getBlock());
   }

   /** ── 填充段（LPCTools: isAllowedReplaceableLiquid + manager.useItemOn）── */
   private static void tickFills(FluidHandler handler) {
      int fills = 0;
      BlockPos origin = handler.player().blockPosition();

      // 层高高的先排（自上而下，与秒破段一致）
      fillLoop:
      for (int dy = DY_MAX; dy >= DY_MIN && fills < MAX_FILLS_PER_TICK; dy--) {
         for (int dx = -RANGE; dx <= RANGE && fills < MAX_FILLS_PER_TICK; dx++) {
            for (int dz = -RANGE; dz <= RANGE && fills < MAX_FILLS_PER_TICK; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (handler.isOnCooldown("instantFill", pos)) {
                  continue;
               }

               BlockState state = handler.world().getBlockState(pos);
               if (!isAllowedReplaceableLiquid(state)) {
                  continue;
               }

               if (!PlayerUtils.canInteracted(pos)) {
                  continue;
               }

               // ★ 只在投影工作范围内填充（与更好的排流体同一套选区判定）
               if (!handler.isWithinWorkRange(pos)) {
                  continue;
               }

               if (!InventoryUtils.switchToItems(handler.player(), cleaningItems.toArray(new Item[0]))) {
                  FluidHandler.diag("秒破排流体：背包里没有清理方块 " + cleaningItems);
                  return;
               }

               Action action = new Action()
                  .setActionSource(ActionManager.ActionSource.FLUID)
                  .queueAction(pos, Direction.UP, false, handler.player());
               ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
               if (ActionManager.INSTANCE.sendQueue(handler.player()).isWaiting()) {
                  // 等服务器回包：本 tick 不再发新的放置（与简单排流体同款保护）
                  break;
               }

               handler.setCooldown("instantFill", pos, 10);
               fills++;
            }
         }
      }
   }

   /** LPCTools isAllowedReplaceableLiquid：可替换的液体格；「包含流动液体」关闭时只要水源 */
   private static boolean isAllowedReplaceableLiquid(BlockState state) {
      if (!(state.canBeReplaced() || state.getBlock() instanceof net.minecraft.world.level.block.SculkVeinBlock)
         || state.getFluidState().getAmount() == 0) {
         return false;
      }

      return Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() || state.getFluidState().isSource();
   }

   private static boolean isContainingLiquid(BlockState state) {
      return state.getFluidState().getAmount() != 0;
   }

   /** 用户填充方块列表变化时重算清理方块（LPCTools LiquidCleanerData 规则） */
   private static void refreshCleaningItems(FluidHandler handler) {
      List<String> current = handler.fillBlocks;
      if (!cleaningItems.isEmpty() && current.equals(lastFillBlocks)) {
         return;
      }

      lastFillBlocks = new ArrayList<>(current);
      List<Item> items = new ArrayList<>();
      Set<Block> blocks = new HashSet<>();

      for (Item item : handler.fillItems) {
         Block block = Block.byItem(item);
         if (block != null && isDefaultCleaningBlock(block.defaultBlockState())) {
            items.add(item);
            blocks.add(block);
         }
      }

      if (items.isEmpty()) {
         // 用户列表里全是石头之类的常规方块（硬度 > 0）：退回 LPCTools 的全注册表默认清理方块
         for (Block block : BuiltInRegistries.BLOCK) {
            if (isDefaultCleaningBlock(block.defaultBlockState()) && !block.asItem().getDefaultInstance().isEmpty()) {
               items.add(block.asItem());
               blocks.add(block);
            }
         }

         FluidHandler.diag("秒破排流体：用户填充列表无硬度0方块，使用注册表默认清理方块 "
            + (items.isEmpty() ? 0 : items.size()) + " 个");
      }

      cleaningItems = List.copyOf(items);
      cleaningBlocks = Set.copyOf(blocks);
   }

   /** LPCTools LiquidCleanerData#isDefaultCleaningBlock */
   private static boolean isDefaultCleaningBlock(BlockState state) {
      if (state.getBlock().defaultDestroyTime() != 0) {
         return false;
      }

      if (state.isAir()) {
         return false;
      }

      if (state.getFluidState().getAmount() != 0) {
         return false;
      }

      // 不能被任何液体冲掉（会被冲掉的东西填进去等于白填）
      if (canAnyBucketPlaceAt(state)) {
         return false;
      }

      if (state.getBlock() instanceof SimpleWaterloggedBlock) {
         return false;
      }

      return !(state.getBlock() instanceof VegetationBlock);
   }

   /** LPCTools canAnyBucketPlaceAt：检测能不能被液体冲掉 */
   private static boolean canAnyBucketPlaceAt(BlockState state) {
      if (state.getBlock() instanceof SimpleWaterloggedBlock) {
         return false;
      }

      for (Fluid fluid : BuiltInRegistries.FLUID) {
         if (fluid != Fluids.EMPTY && state.canBeReplaced(fluid)) {
            return true;
         }
      }

      return false;
   }
}
