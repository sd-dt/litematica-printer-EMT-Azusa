package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.ArrayList;
import java.util.HashMap;
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
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.core.registries.BuiltInRegistries;

public class FluidHandler extends ClientPlayerTickHandler {
   public static final String NAME = "fluid";
   // fluid-diag：简单排流体诊断日志（定位"只破坏不排水"用，排查完可删）
   private static final org.slf4j.Logger LOGGER =
      org.slf4j.LoggerFactory.getLogger("litematica-printer/fluid-diag");
   /** 按消息类型分别限流：同一条日志最多每 500ms 打一次，互不挤占 */
   private static final java.util.Map<String, Long> diagLast = new java.util.HashMap<>();

   /** 诊断日志：只在（核心 → 调试输出）打开时输出，按消息类型 500ms 限流，互不挤占。包私有：供 FluidDrainBase / FluidInstantBreak 复用 */
   static void diag(String msg) {
      if (!Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
         return;
      }

      String key = msg.length() > 24 ? msg.substring(0, 24) : msg;
      long now = System.currentTimeMillis();
      Long last = diagLast.get(key);
      if (last != null && now - last < 500L) {
         return;
      }

      diagLast.put(key, now);
      LOGGER.info("[fluid-simple] " + msg);
   }
   /** 水平四邻（无限水的连通只可能发生在水平方向） */
   private static final Direction[] HORIZONTAL = new Direction[]{
      Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
   };
   /**
    * 盖住之后最多等这么多 tick 让相邻水源也被盖住，超时就照挖（防止选区外的水源把流程卡死）。
    * 挖掘部分的节奏是"能挖就挖"：这里从 10 压到 2 —— 个别格位即使被渗满，
    * 扫描下一轮会重新盖住，比干等半秒快得多。
    */
   private static final int NEIGHBOUR_WAIT_TICKS = 2;
   /**
    * 每 tick 最多放出几个"已盖住、可挖"的格位给破坏队列（兜底下限）。
    * 破坏队列与挖掘同一条路：能秒破的方块（挖掘进度已满）在同一 tick 内会连续 pop 多个，
    * 所以这里放宽到 8，让队列永远有活干 —— 视觉上就是"多个方块同时碎掉"。
    * 实际放出数由 {@link #removalsPerTick()} 决定，跟随挖掘分类的配置。
    */
   private static final int MAX_REMOVALS_PER_TICK = 8;

   /**
    * 每 tick 放出格位的上限，同步「挖掘 → 每 tick 破坏方块数」配置：
    * 挖掘配 0（不限）按 32 处理；配 1~7 时取 16 兜底；配 8 以上完全跟随挖掘的值。
    * <p>
    * <b>注意这是"连发上限"，不是"每 tick 都放这么多"</b>：只有当放出去的格位本 tick 就能挖掉
    * （挖掘进度已满）时才会接着放下一格，否则每个 tick 只放一格 —— 一次塞十几格会把破坏队列
    * 占住几十上百 tick，期间不再铺新方块、不再重试，就是历史上的「挖一半就停 / 换目标」。
    */
   private static int removalsPerTick() {
      int mine = Configs.Mine.BREAK_BLOCKS_PER_TICK.getIntegerValue();
      if (mine <= 0) {
         return 32;
      }

      return Math.max(16, mine);
   }
   /** 简单排流体：已经盖住、等待挖除的格位 -> 放下填充块时的游戏刻 */
   private final Map<Long, Long> simplePlaced = new HashMap<>();
   /** 简单排流体：已经入队等待挖除的格位（挖失败会按退避重试） */
   private final Map<Long, PendingRemoval> simplePending = new HashMap<>();
   /** 本 tick 已经开了破坏：不再放置（否则换回填充物会把挖掘进度打断，看起来就是"挖得慢"） */
   private boolean holdIterationThisTick;
   /** 两个模式同时开启时只提示一次（否则用户会以为在跑「更好的排流体」，实际被秒破模式接管） */
   private boolean warnedBothModes;
   // 下面四个字段放宽为包私有：FluidDrainBase（基础逻辑·冻结区）与 FluidInstantBreak（秒破移植区）需要读取
   List<String> fillBlocks = new ArrayList<>();
   int fillBlockMatchModeRevision = -1;
   List<Item> fillItems = new ArrayList<>();
   /** 简单排流体专用：过滤掉重力方块与流体之后的填充物（沙子排完水会塌，不能用） */
   private List<Item> simpleFillItems = List.of();
   List<String> fluidBlocks = new ArrayList<>();
   int fluidBlockMatchModeRevision = -1;
   List<Fluid> fluids = List.of();

   public FluidHandler() {
      super("fluid", PrintModeType.FLUID, Configs.Core.FLUID, Configs.Fluid.FLUID_SELECTION_TYPE, true);
   }

   // ── 供 FluidDrainBase / FluidInstantBreak（同包、非子类）访问：
   //    父类 ClientPlayerTickHandler（不同包）的 level/player 是 protected，仅子类可见 ──
   ClientLevel world() {
      return this.level;
   }

   LocalPlayer player() {
      return this.player;
   }

   @Override
   protected int getTickInterval() {
      // 排流体自扫描每 tick 都跑：管线节奏（铺→等邻居→挖→重试）由自己的冷却与等待控制，
      // 跟「打印间隔」解耦 —— 打印间隔调大不该拖慢排流体。
      return 1;
   }

   @Override
   protected int getMaxExecutions() {
      return Configs.Print.PLACE_BLOCKS_PER_TICK.getIntegerValue();
   }

   @Override
   protected int rangeHorizontalMargin() {
      // 排流体：投影选区东西南北四面各往外扩 1 格（Y 层不变），外圈也要排/堵
      return 1;
   }

   @Override
   protected void preprocess() {
      List<String> fileBlocks = Configs.Fluid.FLUID_REPLACE_BLOCK_LIST.getStrings();
      int rev = PinYinSearchUtils.getMatchModeRevision();
      if (rev != this.fillBlockMatchModeRevision || !fileBlocks.equals(this.fillBlocks)) {
         this.fillBlockMatchModeRevision = rev;
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
      if (rev != this.fluidBlockMatchModeRevision || !fluidBlocks.equals(this.fluidBlocks)) {
         this.fluidBlockMatchModeRevision = rev;
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
         + Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue()
         + " 秒破模式=" + Configs.Fluid.FLUID_INSTANT_BREAK_MODE.getBooleanValue());

      boolean simpleMode = Configs.Fluid.FLUID_SIMPLE_MODE.getBooleanValue();
      boolean instantMode = Configs.Fluid.FLUID_INSTANT_BREAK_MODE.getBooleanValue();

      // 严格串行「先排完水再挖掘」：本 tick 还有流体工作，挖掘处理器就停手
      this.updateFluidWorkPending();

      if (instantMode && simpleMode && !this.warnedBothModes) {
         // 两个开关同时开时秒破模式全权接管，「更好的排流体」一行代码都不会跑。
         // 不说一声的话，看到的其实是秒破模式的行为，却会去抱怨「更好的排流体」的 bug。
         this.warnedBothModes = true;
         MessageUtils.setOverlayMessage("排流体：秒破方块排流体与更好的排流体同时开启，正在按【秒破方块排流体】执行");
      }

      if (instantMode) {
         // 秒破方块排流体（LPCTools liquidCleaner 移植）：全权接管，清理简单排流体的状态
         this.simplePlaced.clear();
         this.simplePending.clear();
         this.holdIterationThisTick = false;
         FluidInstantBreak.tick(this);
      } else if (simpleMode) {
         // 本 tick 是否已经开了破坏：开了就不再放新的方块（避免又把填充物换回手上、把挖掘进度打断）
         this.holdIterationThisTick = false;
         // 简单排流体：每 tick 单独扫一遍"已盖住 / 已入队"的格位。
         // 不能只靠 executeIteration —— 那一套是跟着玩家走的盒子，玩家一走动，
         // 之前盖住的格位就出了盒子，再也不会被访问到，表现为"挖到一半就停了"。
         this.tickSimpleRemovals();
         // 处理器迭代只把"空气格"交过来（实测日志 16/16 全是 EmptyFluid），水面永远轮不到，
         // 所以简单排流体必须自己扫水面。
         this.tickSimplePlacements();
      } else {
         // 两个功能按钮都关：排流体走基础逻辑（BiliXWhite replaceMode 语义，冻结区）
         FluidDrainBase.tickScan(this);
      }
   }

   /**
    * 排流体自扫描的水平半径：{@code workRange} + {@code fluidScanMargin}。
    * <p>
    * 2026-10-24 之前三条路径都硬编码 8，与选区基准（workRange 默认 6、原版交互距离 5）
    * 脱钩，相对交互距离正好外扩 3 格 —— 表现为「选区偏移多出去三格 / 打出多余方块」。
    * 现在严格跟随选区，只按配置外扩（默认 1 格）。</p>
    */
   static int scanRange(FluidHandler handler) {
      int margin = Configs.Fluid.FLUID_SCAN_MARGIN.getIntegerValue();
      return Math.max(1, ConfigUtils.getWorkRange() + margin);
   }

   /**
    * 选「往水里放方块」要点的面：点实心邻居（池底/池壁）而不是水块本身。
    * <p>以前三处放置都写死 {@code Direction.UP}，只在头顶方向点水，于是表现为
    * 「必须站/飞到水源上方才排得动」；改成点邻居后，站在水面旁边就能放。</p>
    *
    * @return 可放置的面；四周全是流体（深水柱、还没有任何实心邻居）时返回 null
    */
   static Direction findFillSide(FluidHandler handler, BlockPos pos) {
      return new Action().setRequiresSupport().getValidSide(handler.world(), pos);
   }

   /** 统一构造排流体放置动作：能点实心邻居就点邻居，实在没有才退回旧行为（点水块本身）。 */
   static Action createFillAction(FluidHandler handler, BlockPos pos) {
      Action action = new Action().setActionSource(ActionManager.ActionSource.FLUID);
      Direction side = findFillSide(handler, pos);
      if (side == null) {
         // 周围没有任何可点的实心方块：只能沿用旧逻辑（点水块，通常要站上方）
         return action.queueAction(pos, Direction.UP, false, handler.player());
      }

      action.setRequiresSupport();
      return action.queueAction(pos, side, false, handler.player());
   }

   /** 本 tick 是否还有能实际推进的流体工作（供挖掘严格串行让位） */
   private boolean fluidWorkPending;

   public boolean hasPendingFluidWork() {
      // 排流体被关掉时不能继续扣着挖掘（否则可能因为残留标记永久卡死挖掘）
      return Configs.Core.FLUID.getBooleanValue() && this.fluidWorkPending;
   }

   /**
    * 严格串行「先排完水再挖掘」：只有排流体真的还有活干时，才让挖掘停手。
    * <p>只统计「可达 + 在工作范围内」的流体，够不着的水不会把挖掘永久卡死；
    * 已盖上待挖的格位也算没排完。</p>
    */
   private void updateFluidWorkPending() {
      if (!Configs.Core.MINE.getBooleanValue()) {
         this.fluidWorkPending = false;
         return;
      }

      if (!this.simplePending.isEmpty() || !this.simplePlaced.isEmpty()) {
         this.fluidWorkPending = true;
         return;
      }

      int range = scanRange(this);
      BlockPos center = this.player.blockPosition();
      // ★ 垂直窗口的上方与水平半径一致（原来只扫到 +1）：水也可能在玩家上方（水墙/水柱、
      //   站在坑里仰头就是水面），只扫 +1 时这些格根本不进入候选，表现为「必须飞到水源上方才排得动」。
      //   下方仍保留 -3（水只往下流，够用）；越界候选依旧由 isWithinWorkRange / canInteracted 过滤。
      for (int dy = range; dy >= -3; dy--) {
         for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
               BlockPos pos = center.offset(dx, dy, dz);
               if (FluidDrainBase.matchesFluid(this, pos)
                  && this.isWithinWorkRange(pos)
                  && PlayerUtils.canInteracted(pos)) {
                  this.fluidWorkPending = true;
                  return;
               }
            }
         }
      }

      this.fluidWorkPending = false;
   }

   /**
    * 简单排流体：自己扫玩家周围的水源并逐个盖住。
    * <p>
    * 为什么不能靠处理器的迭代：实测日志里 {@code executeIteration} 收到的全是空气格
    * （{@code fluid=EmptyFluid}），水面格根本不在迭代候选里，于是"只破坏不排水"。
    * 这里每 tick 扫一个方盒（水平半径 {@link #scanRange}、垂直 -3 ~ 水平半径），只挑<b>水源</b>
    * 且属于配置流体列表、且在交互距离内的格子，
    * 走的是同一条 {@link #executeSimpleRemoval} 流程（切物品 → 点击放置 → 记录已铺）。
    */
   private void tickSimplePlacements() {
      // ★ 2026-10-24：这里原来还有 `|| this.holdIterationThisTick`，被删掉了。
      //   那一项把「铺方块」和「挖方块」在整 tick 粒度上互斥：只要本 tick 放出过待挖格位
      //   （simplePending 非空且距离内时每 tick 都会放），这里就一票否决直接 return，
      //   于是整个挖掘期间（圆石约 30 tick）一个新方块都不铺，水从别处渗回来 ——
      //   正是「更好的排流体有时无法正常使用 / 大片水域越排越漏」。
      //   「会不会打断挖掘」在 executeSimpleRemoval 里已经按「手上是不是填充物」精确判过，
      //   executeIteration 里的 holdIterationThisTick 判断仍然保留（迭代器来的格位可能是任意方块）。
      if (this.level == null || this.player == null || this.simpleFillItems.isEmpty()) {
         return;
      }

      BlockPos origin = this.player.blockPosition();
      int range = scanRange(this);

      // 层高高的先排、层高低的后排（与秒破方块排流体同一遍历方向）
      // 上方边界跟随水平半径：玩家上方（水墙/水柱）的格也要能排（原来只扫 +1，就是「要飞到水源上方」）
      for (int dy = range; dy >= -3; dy--) {
         for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
               BlockPos pos = origin.offset(dx, dy, dz);
               if (this.isOnCooldown(pos)) {
                  continue;
               }

               FluidState state = this.level.getBlockState(pos).getFluidState();
               // ★ 「包含流动液体」开关也要作用于更好的排流体（以前这里写死 isSource，开关在本模式下一律无效）
               if (state.isEmpty() || !this.fluids.contains(state.getType())
                  || (!Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() && !state.isSource())) {
                  continue;
               }

               // 够不着的先不铺：铺了也是白排一个动作、白占放置配额，反而拖慢够得着的那批。
               if (!PlayerUtils.canInteracted(pos)) {
                  continue;
               }

               // ★ 只在投影工作范围内排流体：自扫描不受迭代器约束，必须显式套用
               //   与处理器迭代同一套选区判定，否则会跑到选区外把不相干的水也排掉。
               if (!this.isWithinWorkRange(pos)) {
                  continue;
               }

               AtomicReference<Boolean> skip = new AtomicReference<>(false);
               this.executeSimpleRemoval(pos, skip);
               if (Boolean.TRUE.equals(skip.get()) || this.holdIterationThisTick) {
                  return;
               }
            }
         }
      }
   }

   /**
    * 简单排流体：与迭代盒无关的清理扫描。
    * <ul>
    *   <li>已盖住且可以挖的（相邻水源也都盖住了，或等待超时）：在交互距离内就交给破坏队列，每 tick 最多放出
    *       {@link #removalsPerTick()} 个 —— 跟随「挖掘 → 每 tick 破坏方块数」配置；</li>
    *   <li>已入队还没消失的：在距离内下一 tick 立刻重试，出了距离按 40~200 tick 退避等玩家走回来。</li>
    * </ul>
    */
   private void tickSimpleRemovals() {
      if (this.level == null || this.player == null) {
         return;
      }

      long now = this.level.getGameTime();
      int cap = removalsPerTick();
      int released = 0;
      // 本 tick 还能不能接着放下一格：只有当刚放出去那一格"这一 tick 就能挖掉"（挖掘进度已满）
      // 时才允许连发。否则一次塞进破坏队列十几格，破坏队列要连着挖十几格、几十上百 tick
      // 都不会回到处理器循环 —— 期间既不再铺新方块、也不再重试，水一渗回来就卡住，
      // 看起来正是"挖到一半就停 / 换目标"。宁可一格一格来，也不要饿死管线。
      boolean burst = true;

      // 层高高的先挖：把已盖住/待挖的格位按 Y 从高到低排序后再放出，
      // 与"铺填充块自上而下"同一方向，视觉上是整片从上往下清。
      if (!this.simplePlaced.isEmpty()) {
         List<Map.Entry<Long, Long>> entries = new ArrayList<>(this.simplePlaced.entrySet());
         entries.sort((a, b) -> Integer.compare(BlockPos.of(b.getKey()).getY(), BlockPos.of(a.getKey()).getY()));

         for (Map.Entry<Long, Long> entry : entries) {
            if (released >= cap || !burst) {
               break;
            }

            Long key = entry.getKey();
            Long placedAt = this.simplePlaced.get(key);
            if (placedAt == null) {
               continue;
            }

            BlockPos pos = BlockPos.of(key);
            long waited = now - placedAt;
            if (waited >= 1L && (this.isLocallySafeToRemove(pos) || waited >= NEIGHBOUR_WAIT_TICKS)) {
               if (!PlayerUtils.canInteracted(pos)) {
                  continue;
               }

               // 破坏队列正在挖别的格：现在放出会切工具、清掉那一格的挖掘进度 —— 留在表里等下一 tick
               if (BreakUtils.INSTANCE.hasActiveDestroyTarget() && !BreakUtils.INSTANCE.isDestroyingPos(pos)) {
                  continue;
               }

               burst = this.isInstantBreakable(pos);
               this.simplePlaced.remove(key);
               this.queueRemoval(pos, now, null);
               released++;
            }
         }
      }

      if (!this.simplePending.isEmpty() && released < cap && burst) {
         List<Map.Entry<Long, PendingRemoval>> entries = new ArrayList<>(this.simplePending.entrySet());
         entries.sort((a, b) -> Integer.compare(BlockPos.of(b.getKey()).getY(), BlockPos.of(a.getKey()).getY()));

         for (Map.Entry<Long, PendingRemoval> entry : entries) {
            if (released >= cap || !burst) {
               break;
            }

            Long key = entry.getKey();
            if (!this.simplePending.containsKey(key)) {
               continue;
            }

            BlockPos pos = BlockPos.of(key);
            if (this.isCoverBlockGone(pos)) {
               this.simplePending.remove(key);
               continue;
            }

            PendingRemoval pending = entry.getValue();
            boolean inRange = PlayerUtils.canInteracted(pos);
            // 破坏队列正在挖别的格：重试会切工具、清进度 —— 留在表里等下一 tick
            if (inRange && BreakUtils.INSTANCE.hasActiveDestroyTarget() && !BreakUtils.INSTANCE.isDestroyingPos(pos)) {
               continue;
            }

            // 在交互距离内就立刻重试（挖掘部分的节奏：能挖就挖，不干等）
            long interval = inRange ? 1L : Math.min(40L + 20L * pending.attempts, 200L);
            if (now - pending.queuedAt >= interval) {
               burst = this.isInstantBreakable(pos);
               this.queueRemoval(pos, now, pending);
               released++;
            }
         }
      }

      if (released > 0 && Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
         diag("放出 " + released + " 格待挖；placed=" + this.simplePlaced.size() + " pending=" + this.simplePending.size());
      }
   }

   /**
    * 这一格是不是"本 tick 就能挖掉"（挖掘进度已经 >= 1，破坏队列会当场 COMPLETED）。
    * 只有这种格位才值得一口气多放几个：它们在同一 tick 里连续 pop，不会占住后续 tick。
    */
   private boolean isInstantBreakable(BlockPos pos) {
      if (this.level == null || this.player == null) {
         return false;
      }

      BlockState state = this.level.getBlockState(pos);
      if (state.isAir()) {
         return true;
      }

      return PlayerUtils.getDestroyProgress(this.player, state) >= 1.0F;
   }

   @Override
   protected boolean canIterate() {
      return !this.fillItems.isEmpty() && !this.fluidBlocks.isEmpty();
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      if (this.holdIterationThisTick) {
         // 本 tick 已经交给破坏队列了：不能再放置/换物品，否则会把刚切好的工具换掉、挖掘进度清零
         return;
      }

      boolean instantMode = Configs.Fluid.FLUID_INSTANT_BREAK_MODE.getBooleanValue();
      if (instantMode) {
         // 秒破方块排流体：全部工作由 preprocess 里的自扫描完成（LPCTools 语义），
         // 迭代进来的格位不再处理，避免两条通路互相抢动作配额。
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

      // 基础逻辑（冻结区）：BiliXWhite replaceMode 语义的"切填充物 → 右键放置"。
      // ★ 必须先过 matchesFluid —— 它内含「包含流动液体」判定；原先直接 tryPlace 只判了
      //   fluids.contains(type)，而流动水的 type 仍是 water，于是开关关闭时流动水照样被铺掉。
      if (!FluidDrainBase.matchesFluid(this, blockPos)) {
         return;
      }

      FluidDrainBase.tryPlace(this, blockPos, skipIteration);
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

         long interval = Math.min(4L + 4L * pending.attempts, 40L);
         if (now - pending.queuedAt >= interval) {
            this.queueRemoval(blockPos, now, pending);
            this.setCooldown(blockPos, 2);
            skipIteration.set(true);
         } else {
            this.setCooldown(blockPos, 1);
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
         if (now - placedAt >= 1L && (this.isLocallySafeToRemove(blockPos) || now - placedAt >= NEIGHBOUR_WAIT_TICKS)) {
            this.simplePlaced.remove(key);
            this.queueRemoval(blockPos, now, null);
            // 本 tick 不再继续跑别的格位：避免又把填充物换回手上，把刚开始的挖掘打断
            this.setCooldown(blockPos, 1);
            skipIteration.set(true);
         } else {
            this.setCooldown(blockPos, 1);
         }

         return;
      }

      // 「包含流动液体」关闭时只处理水源（流动水等源头被盖住后自然消失）；开启时流动水也铺
      if (!Configs.Fluid.FILL_FLOWING_FLUID.getBooleanValue() && !this.isSource(blockPos)) {
         diag("fluid-diag2 跳过：不是水源（包含流动液体=关）@" + blockPos.toShortString()
            + " source=无");
         return;
      }

      // ★ 破坏进行中绝不切物品：服务端在手持物品变化的瞬间会把当前挖掘进度清零。
      //   但如果主手上**已经是填充物**，铺块不会发任何切手包（switchToItems 幂等），
      //   可以与挖掘并行 —— 以前一刀切的"破坏中不许铺"把流水线串行化了。
      if (BreakUtils.INSTANCE.hasActiveDestroyTarget()
         && !this.simpleFillItems.contains(this.player.getMainHandItem().getItem())) {
         this.setCooldown(blockPos, 2);
         return;
      }

      if (!InventoryUtils.switchToItems(this.player, this.simpleFillItems.toArray(new Item[0]))) {
         diag("跳过：切物品失败（背包里没有：" + this.simpleFillItems + "）");
         return;
      }

      // 够不着的格位不铺：放置包发出去也不会生效，只会白占动作配额
      if (!PlayerUtils.canInteracted(blockPos)) {
         diag("跳过：超出交互距离 @" + blockPos.toShortString());
         this.setCooldown(blockPos, 10);
         return;
      }

      Action action = createFillAction(this, blockPos);
      ActionManager.INSTANCE.setNeedWaitModifyLookFromAction(action.getNeedWaitModifyLook());
      if (ActionManager.INSTANCE.sendQueue(this.player).isWaiting()) {
         diag("fluid-diag2 放置已发出但等待服务器回包 @" + blockPos.toShortString());
         skipIteration.set(true);
         return;
      }

      // 盖上以后先记下来：本 tick 不动它，等"整片盖完"再回来挖
      diag("已发出放置点击 @" + blockPos.toShortString() + " 物品=" + this.player.getMainHandItem());
      this.simplePlaced.put(key, now);
      this.setCooldown(blockPos, 1);
   }

   /**
    * 把这一格交给破坏队列挖掉：先切到合适的工具（填充块是石头就切镐子），再入队并登记"待挖"。
    * <p>
    * 工具切换走 {@link BreakUtils#autoSwitchToolIfEnabled} —— 与主破坏流程同一处判定，
    * 也就是「核心 → 自动工具切换」这一个开关同时管住"主页面挖掘"和"简单排流体"两条路，
    * 不再各判各的、互相把对方换好的工具换掉。
    */
   private void queueRemoval(BlockPos blockPos, long now, PendingRemoval retry) {
      BlockState state = this.level.getBlockState(blockPos);
      BreakUtils.autoSwitchToolIfEnabled(blockPos, state);

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

   /** 手上现在拿的是不是我们用来盖水的填充物（是的话说明当前没在"拿工具挖东西"） */
   private boolean isHoldingFillItem() {
      if (this.player == null || this.simpleFillItems.isEmpty()) {
         return false;
      }

      Item held = this.player.getMainHandItem().getItem();
      if (this.simpleFillItems.contains(held)) {
         return true;
      }

      // 兜底：拿副手在放的情况
      return this.simpleFillItems.contains(this.player.getOffhandItem().getItem());
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
