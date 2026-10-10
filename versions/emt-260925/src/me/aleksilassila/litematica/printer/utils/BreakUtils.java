package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.restrictions.UsageRestriction.ListType;
import fi.dy.masa.tweakeroo.config.Configs.Lists;
import fi.dy.masa.tweakeroo.tweaks.PlacementTweaks;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Map.Entry;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.ExcavateListMode;
import me.aleksilassila.litematica.printer.enums.FluidAvoidStrategyType;
import me.aleksilassila.litematica.printer.mixin_extension.BlockBreakResult;
import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
public class BreakUtils {
   public static final Minecraft client = Minecraft.getInstance();
   public static final BreakUtils INSTANCE = new BreakUtils();
   private final Queue<BlockPos> breakQueue = new LinkedList<>();
   /**
    * 「强制挖除」的格位：由处理器自己认定为"必须挖掉"的方块（例如简单排流体盖住水源后留下的填充块）。
    * 这类格位跳过「不挖掘流体 / 不破坏支撑方块」的保护；秒破只在"手上工具不对"时才压制。
    */
   private final Set<Long> forcedBreaks = new HashSet<>();
   /** 强制挖除进行中且手上工具不对：抑制秒破（秒破会跳过工具判定，拿错工具敲掉就不掉落了） */
   private boolean instantMineSuppressed;
   private final Set<BlockPos> queuedBreaks = new HashSet<>();
   private final Map<BlockPos, Integer> recentlyBroken = new HashMap<>();
   private final Map<BlockPos, Integer> pendingBroken = new HashMap<>();
   private BlockPos breakPos;
   private boolean forceDelayedDestroy;
   private int externalDestroyLockTicks;
   private static final Set<Block> fluidAvoidBlocks = Collections.newSetFromMap(new IdentityHashMap<>());
   private static List<String> fluidListSnapshot = List.of();
   private static FluidAvoidStrategyType fluidStrategySnapshot;
   private static boolean fluidMatcherInitialized;
   private static long lastPlayerMineGameTime = -1L;

   private BreakUtils() {
   }

   public static boolean canBreakBlock(BlockPos pos) {
      ClientLevel world = LitematicaUtils.client.level;
      LocalPlayer player = LitematicaUtils.client.player;
      if (world != null && player != null && LitematicaUtils.client.gameMode != null) {
         BlockState currentState = world.getBlockState(pos);
         if (Configs.Mine.BREAK_CHECK_HARDNESS.getBooleanValue() && currentState.getBlock().defaultDestroyTime() < 0.0F) {
            return false;
         } else if (Configs.Mine.BREAK_AVOID_FLUID.getBooleanValue() && isFluidProtected(pos, world)) {
            return false;
         } else {
            return Configs.Mine.BREAK_AVOID_SUPPORT.getBooleanValue() && isSupportProtected(pos, world)
               ? false
               : !currentState.isAir()
                  && !currentState.is(Blocks.AIR)
                  && !currentState.is(Blocks.CAVE_AIR)
                  && !currentState.is(Blocks.VOID_AIR)
                  && !(currentState.getBlock() instanceof LiquidBlock)
                  && !player.blockActionRestricted(LitematicaUtils.client.level, pos, LitematicaUtils.client.gameMode.getPlayerMode());
         }
      } else {
         return false;
      }
   }

   private static void ensureFluidAvoidMatcher() {
      List<String> configured = List.copyOf(Configs.Mine.BREAK_FLUID_LIST.getStrings());
      FluidAvoidStrategyType strategy = (FluidAvoidStrategyType)Configs.Mine.BREAK_FLUID_STRATEGY.getOptionListValue();
      if (fluidMatcherInitialized && fluidListSnapshot.equals(configured)) {
         fluidStrategySnapshot = strategy;
      } else {
         fluidAvoidBlocks.clear();

         for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            boolean matched = configured.stream().anyMatch(entry -> PinYinSearchUtils.matchBlockName(entry, state));
            Item item = block.asItem();
            if (!matched && item != Items.AIR) {
               matched = configured.stream().anyMatch(entry -> PinYinSearchUtils.matchItemName(entry, new ItemStack(item)));
            }

            if (matched) {
               fluidAvoidBlocks.add(block);
            }
         }

         fluidListSnapshot = configured;
         fluidStrategySnapshot = strategy;
         fluidMatcherInitialized = true;
      }
   }

   private static boolean isConfiguredFluid(BlockState state) {
      if (state == null) {
         return false;
      } else {
         return fluidAvoidBlocks.contains(state.getBlock())
            ? true
            : !state.getFluidState().isEmpty() && fluidAvoidBlocks.contains(state.getFluidState().createLegacyBlock().getBlock());
      }
   }

   private static boolean isGravityBlock(BlockState state) {
      if (state != null && !state.isAir()) {
         Block block = state.getBlock();
         return block instanceof FallingBlock || block instanceof AnvilBlock || block instanceof DragonEggBlock;
      } else {
         return false;
      }
   }

   private static int avoidScanRadius() {
      int radius = Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue() ? (int)PlayerUtils.getInteractionRange(5.0) : ConfigUtils.getWorkRange();
      return radius + 2;
   }

   private static boolean isFluidProtected(BlockPos pos, ClientLevel level) {
      LocalPlayer player = LitematicaUtils.client.player;
      if (player == null) {
         return false;
      } else {
         ensureFluidAvoidMatcher();
         return !isConfiguredFluid(level.getBlockState(pos.relative(Direction.UP)))
               && !isConfiguredFluid(level.getBlockState(pos.relative(Direction.EAST)))
               && !isConfiguredFluid(level.getBlockState(pos.relative(Direction.WEST)))
               && !isConfiguredFluid(level.getBlockState(pos.relative(Direction.NORTH)))
               && !isConfiguredFluid(level.getBlockState(pos.relative(Direction.SOUTH)))
            ? fluidStrategySnapshot == FluidAvoidStrategyType.SIX_FACES && isConfiguredFluid(level.getBlockState(pos.relative(Direction.DOWN)))
            : true;
      }
   }

   private static boolean isSupportProtected(BlockPos pos, ClientLevel level) {
      return isGravityBlock(level.getBlockState(pos.relative(Direction.UP)));
   }

   public static boolean breakRestriction(BlockState blockState) {
      return breakRestriction(LitematicaUtils.client.level, null, blockState);
   }

   public static boolean breakRestriction(@Nullable ClientLevel level, @Nullable BlockPos pos, BlockState blockState) {
      if (Configs.Mine.BREAK_LIMITER.getOptionListValue().equals(ExcavateListMode.TWEAKEROO)) {
         if (!ModUtils.isTweakerooLoaded()) {
            return true;
         } else {
            ListType listType = PlacementTweaks.BLOCK_TYPE_BREAK_RESTRICTION.getListType();
            if (listType == ListType.BLACKLIST) {
               return Lists.BLOCK_TYPE_BREAK_RESTRICTION_BLACKLIST.getStrings().stream().noneMatch(string -> matchesRule(string, level, pos, blockState));
            } else {
               return listType == ListType.WHITELIST
                  ? Lists.BLOCK_TYPE_BREAK_RESTRICTION_WHITELIST.getStrings().stream().anyMatch(string -> matchesRule(string, level, pos, blockState))
                  : true;
            }
         }
      } else {
         IConfigOptionListEntry optionListValue = Configs.Mine.BREAK_LIMIT.getOptionListValue();
         if (optionListValue == ListType.BLACKLIST) {
            return Configs.Mine.BREAK_BLACKLIST.getStrings().stream().noneMatch(string -> matchesRule(string, level, pos, blockState));
         } else {
            return optionListValue == ListType.WHITELIST
               ? Configs.Mine.BREAK_WHITELIST.getStrings().stream().anyMatch(string -> matchesRule(string, level, pos, blockState))
               : true;
         }
      }
   }

   public static boolean matchesRule(String rule, @Nullable ClientLevel level, @Nullable BlockPos pos, BlockState state) {
      BlockNbtRule parsed = BlockNbtRule.parse(rule);
      return PinYinSearchUtils.matchBlockName(parsed.blockMatcher(), state) && parsed.matchesState(state);
   }

   /**
    * 「自动工具切换」的统一入口 —— 主破坏流程与简单排流体共用这一处判定。
    * <p>
    * 只有（核心 → 自动工具切换）开着才动手：开关关掉时谁都不许换手；开着时走同一套
    * {@link InventoryUtils#switchToBestTool} / {@link #protectCurrentToolBeforeBreak(BlockState)}。
    * 以前简单排流体是在自己那边单独判这个开关，两条路各切各的，容易出现
    * "排流体把工具换好了、主流程又把填充物换回手上" 的互相打断。
    */
   public static boolean autoSwitchToolIfEnabled(@Nullable BlockPos pos, @Nullable BlockState blockState) {
      return !Configs.Core.AUTO_TOOL_SWITCH.getBooleanValue() ? false : trySwitchToEffectiveTool(pos, blockState);
   }

   public static boolean trySwitchToEffectiveTool(BlockPos pos, BlockState blockState) {
      if (pos != null && blockState != null && !blockState.isAir() && !(blockState.getBlock() instanceof LiquidBlock)) {
         LocalPlayer player = client.player;
         // 原生「自动工具切换」（核心 → 自动工具切换）：不依赖 Tweakeroo，也不依赖 Litematica 的 pick block 槽位配置
         if (Configs.Core.AUTO_TOOL_SWITCH.getBooleanValue()) {
            if (player == null) {
               return false;
            } else {
               boolean switched = InventoryUtils.switchToBestTool(player, blockState);
                  if (!switched) {
                     // 背包里没有可用工具 → 让快捷潜影盒取货去盒子里拿（潜影盒自动取货开着时才有动作）
                     InventoryUtils.requestToolFromShulker(blockState);
                  }

                  return switched || protectCurrentToolBeforeBreak(blockState);
            }
         }

         boolean tweakerooToolSwitch = ModUtils.isTweakerooLoaded() && ModUtils.isToolSwitchEnabled();
         if (player != null
            && tweakerooToolSwitch
            && ToolSelectionUtils.prefersSilkTouchForDrops(blockState)
            && (!isToolAllowedByDurabilityProtection(player.getMainHandItem()) || !ToolSelectionUtils.hasSilkTouch(player.getMainHandItem()))
            && InventoryUtils.hasUsableSilkTouchTool(player)) {
            return InventoryUtils.switchToBestTool(player, blockState);
         } else if (tweakerooToolSwitch) {
            ModUtils.trySwitchToEffectiveTool(pos);
            return protectCurrentToolBeforeBreak(blockState);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isToolAllowedByDurabilityProtection(ItemStack stack) {
      return !ModUtils.isToolTooDamagedForBreaking(stack);
   }

   public static boolean isRecoveryToolReadyForBreak(BlockState blockState) {
      LocalPlayer player = client.player;
      if (player != null && !player.getAbilities().instabuild && ToolSelectionUtils.prefersSilkTouchForDrops(blockState)) {
         boolean toolSwitchEnabled = Configs.Core.AUTO_TOOL_SWITCH.getBooleanValue()
            || ModUtils.isTweakerooLoaded() && ModUtils.isToolSwitchEnabled();
         if (!toolSwitchEnabled) {
            return true;
         } else {
            return isToolAllowedByDurabilityProtection(player.getMainHandItem()) && ToolSelectionUtils.hasSilkTouch(player.getMainHandItem())
               ? true
               : !InventoryUtils.hasUsableSilkTouchTool(player);
         }
      } else {
         return true;
      }
   }

   public static int getCurrentToolSafeBreakBudget() {
      LocalPlayer player = client.player;
      return player != null && !player.getAbilities().instabuild ? ModUtils.getSafeBreakBudget(player.getMainHandItem()) : Integer.MAX_VALUE;
   }

   public static boolean protectCurrentToolBeforeBreak() {
      return protectCurrentToolBeforeBreak(null);
   }

   public static boolean protectCurrentToolBeforeBreak(@Nullable BlockState blockState) {
      LocalPlayer player = client.player;
      if (player == null || player.getAbilities().instabuild) {
         return true;
      } else if (isToolAllowedByDurabilityProtection(player.getMainHandItem())) {
         return true;
      } else {
         ModUtils.trySwapCurrentToolIfNearlyBroken();
         if (isToolAllowedByDurabilityProtection(player.getMainHandItem())) {
            return true;
         } else {
            return blockState != null && InventoryUtils.switchToBestTool(player, blockState)
               ? isToolAllowedByDurabilityProtection(player.getMainHandItem())
               : false;
         }
      }
   }

   /**
    * 强制挖除：由调用方保证"这一格确实该挖"（例如简单排流体自己盖上去的填充块）。
    * <p>
    * 与 {@link #add(BlockPos)} 的区别：破坏判定会跳过「不挖掘流体」「不破坏支撑方块」这两项保护，
    * 并且手上工具不对时不会被秒破接管 —— 秒破分支会跳过"先换工具再挖"，拿错工具敲掉就不掉落了。
    * 「不挖掘流体」默认配置下无所谓，但很多人在挖掘分类里是开着的，而排流体盖住水源的格子
    * <b>必然</b>紧邻流体，不豁免的话这些填充块永远挖不掉。
    */
   public void addForced(BlockPos pos) {
      if (pos != null) {
         BlockPos queuedPos = pos.immutable();
         this.forcedBreaks.add(queuedPos.asLong());
         this.add(queuedPos);
      }
   }

   /** 当前这一格是否跳过秒破（供 mixin 判断） */
   public boolean isInstantMineSuppressed() {
      return this.instantMineSuppressed;
   }

   /**
    * 强制挖除的格位要不要压制秒破。
    * <p>
    * 压制只为一件事：秒破分支拿的是"手上现成的东西"，手上一把不对的工具（甚至空手）会把方块敲掉却<b>不掉落</b>。
    * 所以手上工具正确（{@code isCorrectToolForDrops}，例如排流体在入队前已经显式切好的镐子）时就不压制 ——
    * 秒破照走，排流体一格一格慢慢敲的瓶颈就没了；工具不对时才压制，让正常破坏流程有机会去把工具换过来。
    */
   private static boolean shouldSuppressInstantMineFor(BlockState state) {
      LocalPlayer player = client.player;
      if (player == null || state == null) {
         return false;
      } else if (player.getAbilities().instabuild) {
         return false;
      } else {
         ItemStack stack = player.getMainHandItem();
         return stack.isEmpty() || !stack.isCorrectToolForDrops(state);
      }
   }

   /** 排队中的格位是否允许挖：强制挖除的跳过流体/支撑保护 */
   private boolean canBreakQueued(BlockPos pos) {
      return pos != null && this.forcedBreaks.contains(pos.asLong()) ? canBreakForced(pos) : canBreakBlock(pos);
   }

   /**
    * 强制挖除用的判定：只保证"不是空气 / 不是流体 / 没被保护区禁止"，
    * 不看「不挖掘流体」「不破坏支撑方块」这两项 —— 因为排流体盖住水源的填充块必然紧邻流体。
    */
   private static boolean canBreakForced(BlockPos pos) {
      ClientLevel world = LitematicaUtils.client.level;
      LocalPlayer player = LitematicaUtils.client.player;
      if (world == null || player == null || LitematicaUtils.client.gameMode == null) {
         return false;
      }

      BlockState state = world.getBlockState(pos);
      return !state.isAir()
         && !state.is(Blocks.AIR)
         && !state.is(Blocks.CAVE_AIR)
         && !state.is(Blocks.VOID_AIR)
         && !(state.getBlock() instanceof LiquidBlock)
         && !player.blockActionRestricted(world, pos, LitematicaUtils.client.gameMode.getPlayerMode());
   }

   public void add(BlockPos pos) {
      if (pos != null) {
         BlockPos queuedPos = pos.immutable();
         if (!queuedPos.equals(this.breakPos)
            && !this.recentlyBroken.containsKey(queuedPos)
            && !this.pendingBroken.containsKey(queuedPos)
            && this.queuedBreaks.add(queuedPos)) {
            this.breakQueue.add(queuedPos);
         }
      }
   }

   public void add(SchematicBlockContext ctx) {
      if (ctx != null) {
         this.add(ctx.blockPos);
      }
   }

   private void tickRecentlyBroken() {
      this.tickBreakMarkerMap(this.recentlyBroken);
      this.tickBreakMarkerMap(this.pendingBroken);
   }

   private void tickBreakMarkerMap(Map<BlockPos, Integer> markerMap) {
      if (!markerMap.isEmpty()) {
         Iterator<Entry<BlockPos, Integer>> iterator = markerMap.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<BlockPos, Integer> entry = iterator.next();
            int remainingTicks = entry.getValue() - 1;
            if (remainingTicks <= 0) {
               iterator.remove();
            } else {
               entry.setValue(remainingTicks);
            }
         }
      }
   }

   public void preprocess() {
      this.tickRecentlyBroken();
      if (this.externalDestroyLockTicks > 0) {
         this.externalDestroyLockTicks--;
      }

      if (!ConfigUtils.isPrinterEnable()) {
         if (!this.breakQueue.isEmpty()) {
            this.breakQueue.clear();
            this.queuedBreaks.clear();
         }

         if (this.breakPos != null) {
            this.breakPos = null;
         }

         if (!this.recentlyBroken.isEmpty()) {
            this.recentlyBroken.clear();
         }

         if (!this.pendingBroken.isEmpty()) {
            this.pendingBroken.clear();
         }

         this.externalDestroyLockTicks = 0;
         this.forceDelayedDestroy = false;
      }
   }

   public void resetRuntime() {
      this.breakQueue.clear();
      this.queuedBreaks.clear();
      this.recentlyBroken.clear();
      this.pendingBroken.clear();
      this.breakPos = null;
      this.forceDelayedDestroy = false;
      this.externalDestroyLockTicks = 0;
   }

   public boolean isNeedHandle() {
      return !this.breakQueue.isEmpty() || this.breakPos != null;
   }

   public static boolean isPlayerMining() {
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null && !player.getAbilities().instabuild && !player.isSpectator()) {
         boolean mining = client.options.keyAttack.isDown() && client.hitResult != null && client.hitResult.getType() == Type.BLOCK;
         if (mining) {
            lastPlayerMineGameTime = level.getGameTime();
         }

         long diff = level.getGameTime() - lastPlayerMineGameTime;
         return diff >= 0L && diff <= 1L;
      } else {
         return false;
      }
   }

   public void onTick() {
      // 破坏动作同样受限流器管：令牌用尽就本 tick 不动手（下 tick 自然重试）
      if (!PacketRateLimiter.allowAction()) {
         return;
      }
      LocalPlayer player = client.player;
      ClientLevel level = client.level;
      if (player != null && level != null) {
         if (!Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() || !isPlayerMining()) {
            if (this.externalDestroyLockTicks <= 0) {
               if (this.breakPos != null || !this.breakQueue.isEmpty()) {
                  if (this.breakPos == null) {
                     while (!this.breakQueue.isEmpty()) {
                        BlockPos pos = this.breakQueue.poll();
                        this.queuedBreaks.remove(pos);
                        if (pos != null
                           && PlayerUtils.canInteracted(pos)
                           && this.canBreakQueued(pos)
                           && breakRestriction(level, pos, level.getBlockState(pos))) {
                           // 强制挖除的格位不走秒破：秒破分支直接返回 COMPLETED，用不上工具，
                           // 会把"先换镐子再挖"这条链路整段跳过。
                           this.instantMineSuppressed = this.forcedBreaks.contains(pos.asLong());
                           BlockBreakResult result = this.continueDestroyBlock(pos, Direction.DOWN);
                           this.instantMineSuppressed = false;
                           if (result != BlockBreakResult.FAILED) {
                              this.forcedBreaks.remove(pos.asLong());
                           }

                           if (result == BlockBreakResult.IN_PROGRESS) {
                              this.breakPos = pos;
                              break;
                           }

                           if (result == BlockBreakResult.COMPLETED || result == BlockBreakResult.COMPLETED_WAIT) {
                              this.markRecentlyBroken(pos);
                              if (result == BlockBreakResult.COMPLETED_WAIT) {
                                 this.markPendingBroken(pos, ConfigUtils.getBreakCooldown());
                              }
                           }
                        }
                     }
                  } else {
                     if (!this.canBreakQueued(this.breakPos)) {
                        this.forcedBreaks.remove(this.breakPos.asLong());
                        this.breakPos = null;
                        this.forceDelayedDestroy = false;
                        this.onTick();
                        return;
                     }

                     this.instantMineSuppressed = this.forcedBreaks.contains(this.breakPos.asLong())
                        && shouldSuppressInstantMineFor(level.getBlockState(this.breakPos));
                     BlockBreakResult resultx = this.continueDestroyBlock(this.breakPos, Direction.DOWN);
                     this.instantMineSuppressed = false;
                     if (resultx != BlockBreakResult.IN_PROGRESS) {
                        if (resultx == BlockBreakResult.COMPLETED || resultx == BlockBreakResult.COMPLETED_WAIT) {
                           this.markRecentlyBroken(this.breakPos);
                           if (resultx == BlockBreakResult.COMPLETED_WAIT) {
                              this.markPendingBroken(this.breakPos, ConfigUtils.getBreakCooldown());
                           }
                        }

                        this.forcedBreaks.remove(this.breakPos.asLong());
                        this.breakPos = null;
                        this.forceDelayedDestroy = false;
                        this.onTick();
                     }
                  }
               }
            }
         }
      }
   }

   public boolean hasActiveDestroyTarget() {
      return this.breakPos != null;
   }

   /** 破坏队列当前正在挖的格位是不是 pos（供挖掘/排流体两边互斥仲裁用） */
   public boolean isDestroyingPos(@Nullable BlockPos pos) {
      return pos != null && pos.equals(this.breakPos);
   }

   public void suppressQueuedBreaks(int ticks) {
      this.externalDestroyLockTicks = Math.max(this.externalDestroyLockTicks, ticks);
   }

   public void markRecentlyBroken(BlockPos pos) {
      if (pos != null) {
         this.recentlyBroken.put(pos.immutable(), 2);
      }
   }

   public void markPendingBroken(BlockPos pos, int timeoutTicks) {
      if (pos != null) {
         this.pendingBroken.put(pos.immutable(), Math.max(timeoutTicks, 1));
      }
   }

   public void confirmServerBlockUpdate(BlockPos pos) {
      if (pos != null) {
         this.recentlyBroken.remove(pos);
         this.pendingBroken.remove(pos);
      }
   }

   public void clearPendingBroken(BlockPos pos) {
      if (pos != null) {
         this.pendingBroken.remove(pos);
      }
   }

   public boolean isRecentlyBroken(BlockPos pos) {
      return pos != null && (this.recentlyBroken.containsKey(pos) || this.pendingBroken.containsKey(pos));
   }

   public BlockBreakResult continueDestroyBlock(BlockPos blockPos, Direction direction, boolean localPrediction, boolean trackBreakPos) {
      MultiPlayerGameModeExtension gameMode = (MultiPlayerGameModeExtension)client.gameMode;
      if (gameMode != null && blockPos != null && direction != null) {
         BlockBreakResult result = gameMode.litematica_printer$continueDestroyBlock(localPrediction, blockPos, direction, this.forceDelayedDestroy);
         if (trackBreakPos && result == BlockBreakResult.IN_PROGRESS) {
            this.breakPos = blockPos;
         }

         if (result != BlockBreakResult.IN_PROGRESS) {
            this.forceDelayedDestroy = false;
         }

         return result;
      } else {
         return BlockBreakResult.FAILED;
      }
   }

   public BlockBreakResult continueDestroyBlock(BlockPos blockPos, Direction direction, boolean localPrediction) {
      return this.continueDestroyBlock(blockPos, direction, localPrediction, true);
   }

   public BlockBreakResult continueDestroyBlock(BlockPos blockPos, Direction direction) {
      return this.continueDestroyBlock(blockPos, direction, true);
   }

   public BlockBreakResult continueDestroyBlock(BlockPos blockPos) {
      return this.continueDestroyBlock(blockPos, Direction.DOWN);
   }

   public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos, Direction direction) {
      return this.continueDestroyBlockForMine(blockPos, direction, true);
   }

   public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos, Direction direction, boolean allowToolSwitch) {
      MultiPlayerGameModeExtension gameMode = (MultiPlayerGameModeExtension)client.gameMode;
      return gameMode == null ? BlockBreakResult.FAILED : gameMode.litematica_printer$continueDestroyBlockForMine(blockPos, direction, allowToolSwitch);
   }

   public BlockBreakResult continueDestroyBlockForMine(BlockPos blockPos) {
      return this.continueDestroyBlockForMine(blockPos, Direction.DOWN);
   }

   public boolean isPendingDelayedDestroy(BlockPos blockPos) {
      MultiPlayerGameModeExtension gameMode = (MultiPlayerGameModeExtension)client.gameMode;
      return gameMode != null && gameMode.litematica_printer$isPendingDelayedDestroy(blockPos);
   }

   public BlockBreakResult continueDestroyBlockWithoutTracking(BlockPos blockPos, Direction direction) {
      return this.continueDestroyBlock(blockPos, direction, true, false);
   }

   public BlockBreakResult continueDestroyBlockWithoutTracking(BlockPos blockPos) {
      return this.continueDestroyBlockWithoutTracking(blockPos, Direction.DOWN);
   }

   public BlockBreakResult continueDestroyBlockWithoutToolSwitch(BlockPos blockPos, Direction direction, boolean trackBreakPos) {
      MultiPlayerGameModeExtension gameMode = (MultiPlayerGameModeExtension)client.gameMode;
      if (gameMode == null) {
         return BlockBreakResult.FAILED;
      } else {
         BlockBreakResult result = gameMode.litematica_printer$continueDestroyBlock(true, blockPos, direction, this.forceDelayedDestroy, false);
         if (trackBreakPos && result == BlockBreakResult.IN_PROGRESS) {
            this.breakPos = blockPos;
         }

         if (result != BlockBreakResult.IN_PROGRESS) {
            this.forceDelayedDestroy = false;
         }

         return result;
      }
   }

   public BlockBreakResult continueDestroyBlockWithoutToolSwitch(BlockPos blockPos, Direction direction) {
      return this.continueDestroyBlockWithoutToolSwitch(blockPos, direction, true);
   }
}
