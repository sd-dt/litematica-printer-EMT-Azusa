package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.core.registries.BuiltInRegistries;

public class FluidHandler extends ClientPlayerTickHandler {
   public static final String NAME = "fluid";
   // fluid-diag：简单排流体诊断日志（定位"只破坏不排水"用，排查完可删）
   private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("litematica-printer/fluid-diag");
   /** 按消息类型分别限流：同一条日志最多每 500ms 打一次，互不挤占 */
   private static final java.util.Map<String, Long> diagLast = new java.util.HashMap<>();

   private static void diag(String msg) {
      String key = msg.length() > 24 ? msg.substring(0, 24) : msg;
      long now = System.currentTimeMillis();
      Long last = diagLast.get(key);
      if (last != null && now - last < 500L) {
         return;
      }

      diagLast.put(key, now);
      LOGGER.info("[fluid-diag] " + msg);
   }
   /** 水平四邻（无限水的连通只可能发生在水平方向） */
   private static final Direction[] HORIZONTAL = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
   };
   /** 盖住之后最多等这么多 tick 让相邻水源也被盖住，超时就照挖（防止选区外的水源把流程卡死） */
   private static final int NEIGHBOUR_WAIT_TICKS = 10;
   /** 简单排流体：已经盖住、等待挖除的格位 -> 放下填充块时的游戏刻 */
   private final Map<Long, Long> simplePlaced = new HashMap<>();
   /** 简单排流体：已经入队等待挖除的格位（挖失败会按退避重试） */
   private final Map<Long, PendingRemoval> simplePending = new HashMap<>();
   /** 本 tick 已经开了破坏：不再放置（否则换回填充物会把挖掘进度打断，看起来就是"挖得慢"） */
   private boolean holdIterationThisTick;
   private List<String> fillBlocks = new ArrayList<>();
   private List<Item> fillItems = new ArrayList<>();
   /** 简单排流体专用：过滤掉重力方块与流体之后的填充物（沙子排完水会塌，不能用） */
   private List<Item> simpleFillItems = List.of();
   private List<String> fluidBlocks = new ArrayList<>();
   private List<Fluid> fluids = List.of();

   public FluidHandler() {
      super("fluid", PrintModeType.FLUID, Configs.Core.FLUID, Configs.Fluid.FLUID_SELECTION_TYPE, true);
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
      List<String> fileBlocks = Configs.Fluid.FLUID_REPLACE_BLOCK_LIST.getStrings();
      if (!fileBlocks.equals(this.fillBlocks)) {
         this.fillBlocks = new ArrayList<>(fileBlocks);
         this.fillItems = new ArrayList<>();

         for (String itemName : this.fillBlocks) {
            List<Item> list = BuiltInRegistries.ITEM.stream().filter(item -> PinYinSearchUtils.matchName(itemName, new ItemStack(item))).toList();
            // 一个条目匹配到一大堆物品（典型是把 id 前缀写成条目，例如 "minecraft"）说明它没有意义：
            // 直接忽略，否则填充物列表被淹掉、切物品乱套，会出现"只破坏不排水"。
            if (list.size() > 32) {
               continue;
            }

            this.fillItems.addAll(list);
         }
      }

      List<String> fluidBlocks = Configs.Fluid.FLUID_LIST.getStrings();
      if (!fluidBlocks.equals(this.fluidBlocks)) {
         this.fluidBlocks = new ArrayList<>(fluidBlocks);
         this.fluids = new ArrayList<>();

         for (String itemName : this.fluidBlocks) {
            List<Fluid> list = BuiltInRegistries.FLUID
               .stream()
               .filter(item -> PinYinSearchUtils.matchName(itemName, item.defaultFluidState().createLegacyBlock()))
               .toList();
            this.fluids.addAll(list);
         }
      }

      // 简单排流体：只保留"非重力、非流体"的填充物
      List<Item> safe = new ArrayList<>();

      for (Item item : this.fillItems) {
         Block block = Block.byItem(item);
         if (block != null && !(block instanceof FallingBlock) && !(block instanceof LiquidBlock)) {
            safe.add(item);
         }
      }

      // 兜底：列表里配的全是重力方块/流体（默认就是沙子）时，筛选结果会是空的 ——
      // 那样简单模式会在放置前直接 return，表现为「只挖不放」。
      // 这种情况退回用户配置的原始列表：沙子照样能把水挤掉，只是不如非重力方块整齐。
      this.simpleFillItems = safe.isEmpty() ? new ArrayList<>(this.fillItems) : safe;
      diag("simpleFillItems=" + this.simpleFillItems.size() + " (回退=" + safe.isEmpty()
         + ") 清单=" + this.fillBlocks + " 简单模式="
         + Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue());
      if (!Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue()) {
         this.simplePlaced.clear();
         this.simplePending.clear();
         this.holdIterationThisTick = false;
      } else {
         // 本 tick 是否已经开了破坏：开了就不再放新的方块（避免又把填充物换回手上、把挖掘进度打断）
         this.holdIterationThisTick = false;
         // 简单排流体：每 tick 单独扫一遍"已盖住 / 已入队"的格位。
         // 不能只靠 executeIteration —— 那一套是跟着玩家走的盒子，玩家一走动，
         // 之前盖住的格位就出了盒子，再也不会被访问到，表现为"挖到一半就停了"。
         this.tickSimpleRemovals();
         // 处理器迭代只把"空气格"交过来（实测日志 16/16 全是 EmptyFluid），水面永远轮不到，
         // 所以简单排流体必须自己扫水面。
         this.tickSimplePlacements();
      }
   }

   /**
    * 简单排流体：与迭代盒无关的清理扫描。
    * <ul>
    *   <li>已盖住且可以挖的（相邻水源也都盖住了，或等待超时）：在交互距离内就交给破坏队列，每 tick 只开一个；</li>
    *   <li>已入队还没消失的：在交互距离内用较短间隔重试，出了距离按退避等玩家走回来。</li>
    * </ul>
    */
   /**
    * 简单排流体：自己扫玩家周围的水源并逐个盖住。
    * <p>
    * 为什么不能靠处理器的迭代：实测日志里 {@code executeIteration} 收到的全是空气格
    * （{@code fluid=EmptyFluid}），水面格根本不在迭代候选里，于是"只破坏不排水"。
    * 这里每 tick 扫一个小方盒（3 层高、半径 8），只挑<b>水源</b>且属于配置流体列表的格子，
    * 走的是同一条 {@link #executeSimpleRemoval} 流程（切物品 → 点击放置 → 记录已铺）。
    */
   private void tickSimplePlacements() {
      if (this.level == null || this.player == null || this.simpleFillItems.isEmpty() || this.holdIterationThisTick) {
         return;
      }

      BlockPos origin = this.player.blockPosition();
      int range = 8;

      for (int dy = -3; dy <= 1; dy++) {
         for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (this.isOnCooldown(pos)) {
                  continue;
               }

               FluidState state = this.level.getBlockState(pos).getFluidState();
               if (state.isEmpty() || !state.isSource() || !this.fluids.contains(state.getType())) {
                  continue;
               }

               AtomicReference<Boolean> skip = new AtomicReference<>(false);
               diag("fluid-diag3 自扫到水源 @" + pos.toShortString() + " 流体=" + state.getType());
               this.executeSimpleRemoval(pos, skip);
               if (Boolean.TRUE.equals(skip.get()) || this.holdIterationThisTick) {
                  return;
               }
            }
         }
      }
   }

   private void tickSimpleRemovals() {
      diag("fluid-diag2 tickSimpleRemovals placed=" + this.simplePlaced.size()
         + " pending=" + this.simplePending.size());
      if (this.level == null || this.player == null) {
         return;
      }

      long now = this.level.getGameTime();

      if (!this.simplePlaced.isEmpty()) {
         Iterator<Map.Entry<Long, Long>> iterator = this.simplePlaced.entrySet().iterator();

         while (iterator.hasNext()) {
            Map.Entry<Long, Long> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            long waited = now - entry.getValue();
            if (waited >= 2L && (this.isLocallySafeToRemove(pos) || waited >= NEIGHBOUR_WAIT_TICKS)) {
               if (!PlayerUtils.canInteracted(pos)) {
                  continue;
               }

               iterator.remove();
               this.queueRemoval(pos, now, null);
               break;
            }
         }
      }

      if (!this.simplePending.isEmpty()) {
         Iterator<Map.Entry<Long, PendingRemoval>> iterator = this.simplePending.entrySet().iterator();

         while (iterator.hasNext()) {
            Map.Entry<Long, PendingRemoval> entry = iterator.next();
            BlockPos pos = BlockPos.of(entry.getKey());
            if (this.isCoverBlockGone(pos)) {
               iterator.remove();
               continue;
            }

            PendingRemoval pending = entry.getValue();
            boolean inRange = PlayerUtils.canInteracted(pos);
            long interval = inRange ? 20L : Math.min(60L + 20L * pending.attempts, 200L);
            if (now - pending.queuedAt >= interval) {
               this.queueRemoval(pos, now, pending);
               break;
            }
         }
      }
   }

   @Override
   protected boolean canIterate() {
      return !this.fillItems.isEmpty() && !this.fluidBlocks.isEmpty();
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      FluidState diagState = this.level.getBlockState(blockPos).getFluidState();
      diag("fluid-diag2 executeIteration @" + blockPos.toShortString() + " fluid=" + diagState.getType()
         + " source=" + diagState.isSource() + " empty=" + diagState.isEmpty()
         + " simple=" + Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue());
      if (this.holdIterationThisTick) {
         // 本 tick 已经交给破坏队列了：不能再放置/换物品，否则会把刚切好的工具换掉、挖掘进度清零
         return;
      }

      boolean simpleMode = Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue();
      FluidState fluidState = this.level.getBlockState(blockPos).getFluidState();
      long key = blockPos.asLong();

      if (!this.fluids.contains(fluidState.getType())) {
         // 简单排流体：被自己盖住的格位已经不是流体了，但还得回来把它挖掉 ——
         // 这里必须放行，否则"盖完再挖"的第二步永远不会发生（填充块会一直留在原地，也不会切工具）。
         if (simpleMode && (this.simplePlaced.containsKey(key) || this.simplePending.containsKey(key))) {
            this.executeSimpleRemoval(blockPos, skipIteration);
         }

         return;
      }

      if (simpleMode) {
         this.executeSimpleRemoval(blockPos, skipIteration);
         return;
      }

      if (!Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() && !fluidState.isSource()) {
         return;
      }

      if (!InventoryUtils.switchToItems(this.player, this.fillItems.toArray(new Item[0]))) {
         return;
      }

      Action action = new Action().setActionSource(ActionManager.ActionSource.FLUID).queueAction(blockPos, Direction.UP, false, this.player);
      ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
      if (ActionManager.INSTANCE.sendQueue(this.player).isWaiting()) {
         skipIteration.set(true);
      } else {
         this.setCooldown(blockPos, Fluids.WATER.getTickDelay(this.level) * 2);
      }
   }

   /**
    * 简单排流体：放一挖一；检测到无限水则"放多挖一"。
    * <p>
    * 无限水的关键：相邻的水源会互相补满，所以只要这一片水源还没全盖住，<b>一格都不能挖</b>，
    * 否则挖掉的瞬间就会被旁边的水源重新渗满，白干。
    * 因此流程是：先把这一片水源逐个盖住（放多，非重力方块），整片盖完再逐格挖掉（挖一）；
    * 水被破坏后不会再生成，最终格位留空。
    */
   private void executeSimpleRemoval(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      if (this.simpleFillItems.isEmpty()) {
         diag("跳过：填充物列表为空");
         return;
      }

      long key = blockPos.asLong();
      long now = this.level.getGameTime();

      // 已经交给破坏队列的格位：确认填充块真的没了；没掉就按退避间隔重试
      // （破坏有可能被交互距离/其它限制拦下，重试能避免"填充块永远留在原地"）
      PendingRemoval pending = this.simplePending.get(key);
      if (pending != null) {
         diag("fluid-diag2 已入队，等待挖掉 @" + blockPos.toShortString());
         if (this.isCoverBlockGone(blockPos)) {
            diag("填充块已消失 @" + blockPos.toShortString());
            this.simplePending.remove(key);
            return;
         }

         long interval = Math.min(40L + 20L * pending.attempts, 200L);
         if (now - pending.queuedAt >= interval) {
            this.queueRemoval(blockPos, now, pending);
            this.setCooldown(blockPos, 20);
            skipIteration.set(true);
         } else {
            this.setCooldown(blockPos, 5);
         }

         return;
      }

      Long placedAt = this.simplePlaced.get(key);
      if (placedAt != null) {
         diag("fluid-diag2 已铺过，等待可挖 @" + blockPos.toShortString());
         // 这一格已经盖住了：优先等相邻的水源也盖住，再动镐子把它挖掉（挖一）；
         // 「无限水」就是这么处理的——同一片水源全部盖住后才会开始挖。
         // 但等待有上限（NEIGHBOUR_WAIT_TICKS）：相邻水源在选区外/盖不住时也不能一直干等，
         // 超时就照挖，剩下的流动水在源头都没了之后会自己流干。
         if (now - placedAt >= 2L && (this.isLocallySafeToRemove(blockPos) || now - placedAt >= NEIGHBOUR_WAIT_TICKS)) {
            this.simplePlaced.remove(key);
            this.queueRemoval(blockPos, now, null);
            // 本 tick 不再继续跑别的格位：避免又把填充物换回手上，把刚开始的挖掘打断
            this.setCooldown(blockPos, 20);
            skipIteration.set(true);
         } else {
            this.setCooldown(blockPos, 2);
         }

         return;
      }

      // 只处理水源本身；流动水等水源被盖住后自然消失
      if (!this.isSource(blockPos)) {
         diag("fluid-diag2 跳过：不是水源（流动的水不铺）@" + blockPos.toShortString()
            + " source=无");
         return;
      }

      if (!InventoryUtils.switchToItems(this.player, this.simpleFillItems.toArray(new Item[0]))) {
         diag("跳过：切物品失败（背包里没有：" + this.simpleFillItems + "）");
         return;
      }

      Action action = new Action().setActionSource(ActionManager.ActionSource.FLUID).queueAction(blockPos, Direction.UP, false, this.player);
      ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
      if (ActionManager.INSTANCE.sendQueue(this.player).isWaiting()) {
         diag("fluid-diag2 放置已发出但等待服务器回包 @" + blockPos.toShortString());
         skipIteration.set(true);
         return;
      }

      // 盖上以后先记下来：本 tick 不动它，等"整片盖完"再回来挖
      diag("已发出放置点击 @" + blockPos.toShortString() + " 物品=" + this.player.getMainHandItem());
      this.simplePlaced.put(key, now);
      this.setCooldown(blockPos, 5);
   }

   /**
    * 把这一格交给破坏队列挖掉：先切到合适的工具（石头→镐子等），再入队并登记"待挖"。
    * 显式切工具是为了不依赖破坏流程内部那条切换路径，保证"先拿镐子，再挖"。
    */
   private void queueRemoval(BlockPos blockPos, long now, PendingRemoval retry) {
      BlockState state = this.level.getBlockState(blockPos);
      if (Configs.Core.AUTO_TOOL_SWITCH.getBooleanValue()) {
         BreakUtils.trySwitchToEffectiveTool(blockPos, state);
      }

      // 用"强制挖除"：排流体盖住的格位必然紧邻流体，走普通判定会被「不挖掘流体」保护拦下
      diag("交给破坏队列 @" + blockPos.toShortString() + " 状态=" + state.getBlock());
      BreakUtils.INSTANCE.addForced(blockPos);
      // 本 tick 不再放置别的方块：保持手上是刚切好的工具，挖掘进度不会被换物品清零
      this.holdIterationThisTick = true;
      if (retry == null) {
         this.simplePending.put(blockPos.asLong(), new PendingRemoval(now));
      } else {
         retry.queuedAt = now;
         retry.attempts++;
      }
   }

   /** 这一格上我们放的填充块是否已经消失（变成空气、变成流体、或被换成别的方块都算完事） */
   private boolean isCoverBlockGone(BlockPos blockPos) {
      BlockState state = this.level.getBlockState(blockPos);
      if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
         return true;
      }

      return !this.simpleFillItems.contains(state.getBlock().asItem());
   }

   /** 简单排流体：已经交给破坏队列、还在等它消失的格位 */
   private static final class PendingRemoval {      private long queuedAt;
      private int attempts;

      private PendingRemoval(long queuedAt) {
         this.queuedAt = queuedAt;
         this.attempts = 1;
      }
   }

   /**
    * 这一格是否可以安全挖掉了：自身与水平四邻都不能还是"水源"。
    * 相邻还有水源的话，挖开的瞬间就会被渗满（无限水就是这么来的），所以要先把它们也盖住。
    */
   private boolean isLocallySafeToRemove(BlockPos pos) {
      if (this.isSource(pos)) {
         return false;
      }

      for (Direction side : HORIZONTAL) {
         if (this.isSource(pos.relative(side))) {
            return false;
         }
      }

      return true;
   }

   private boolean isSource(BlockPos pos) {
      return this.level.getBlockState(pos).getFluidState().isSource();
   }
}
