package me.aleksilassila.litematica.printer.printer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import lombok.Generated;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.printer.action.ClickAction;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.PacketUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.SlabBlock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ActionManager {
   public static final ActionManager INSTANCE = new ActionManager();
   private static final float LOOK_SETTLED_EPSILON_DEGREES = 1.0F;
   private static final double STALE_WAIT_MOVE_DISTANCE_SQR = 0.5625;
   private static final long PRINT_SIGN_EDIT_ARM_TIMEOUT_NANOS = 30000000000L;
   private static final long PRINT_SIGN_EDIT_RESPONSE_TIMEOUT_NANOS = 5000000000L;
   private static final long PRINT_SIGN_EDIT_PRUNE_INTERVAL_NANOS = 1000000000L;
   private static final long TASK_ANVIL_SCREEN_RESPONSE_TIMEOUT_NANOS = 5000000000L;
   private static final int MAX_PENDING_TASK_ANVIL_SCREENS = 64;
   private QueuedClick queuedClick;
   private final Map<Long, Long> pendingPrintSignEdits = new HashMap<>();
   private long nextPrintSignEditPruneNanos;
   private int pendingTaskAnvilScreens;
   private long taskAnvilScreenSuppressionDeadlineNanos;
   private long manualAnvilScreenAllowanceDeadlineNanos;
   public Vec3 hitModifier;
   public boolean useProtocol = false;
   @Nullable
   public PlayerLook look;
   public boolean needWaitModifyLook = false;
   private boolean waitForHorizontalLook = true;
   private boolean actionRequiresWaitModifyLook = false;
   private long lastQueuedLookTick = Long.MIN_VALUE;
   private float lastQueuedLookYaw;
   private float lastQueuedLookPitch;
   private boolean printerInteractionActive;
   private boolean easyPlaceProtocolActive;
   private boolean queuedDirectionalPlacement;
   private static final double FAST_DIRECTIONAL_SPEED_BLOCKS_PER_SECOND = 14.0;
   private static final double FAST_DIRECTIONAL_STALE_DISTANCE = 2.0;
   private ActionManager.ActionSource activeSource = ActionManager.ActionSource.GENERIC;
   private static final long RESERVE_PENDING_EXPIRE_TICKS = 20L;
   private final Map<Item, Integer> reservePendingConsumed = new HashMap<>();
   private final Map<Item, Integer> reserveLastSeenCount = new HashMap<>();
   private final Map<Item, Long> reservePendingLastMoveTick = new HashMap<>();
   /** 本次交互前手里物品的快照：消耗归属要用它，不能用交互后读到的（最后一份用完会变成空气） */
   @Nullable
   private Item reservedItem;
   /** 未确认的"空中放置"点击：pos -> 点击时客户端看到的状态 + 点击时刻 */
   private final Map<Long, ActionManager.UnconfirmedInAirClick> unconfirmedInAirClicks = new HashMap<>();

   private ActionManager() {
   }

   public boolean queueClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift) {
      return this.queueClick(target, side, hitModifier, useShift, 1);
   }

   public boolean queueClick(@NotNull BlockPos target, @NotNull Direction side, @NotNull Vec3 hitModifier, boolean useShift, int clickRepeatCount) {
      return this.queueClick(target, side, hitModifier, useShift, clickRepeatCount, null);
   }

   public boolean queueClick(
      @NotNull BlockPos target,
      @NotNull Direction side,
      @NotNull Vec3 hitModifier,
      boolean useShift,
      int clickRepeatCount,
      @Nullable Item[] expectedItems
   ) {
      return this.queueClick(target, side, hitModifier, useShift, clickRepeatCount, expectedItems, ActionManager.ActionSource.GENERIC, false);
   }

   public boolean queueClick(
      @NotNull BlockPos target,
      @NotNull Direction side,
      @NotNull Vec3 hitModifier,
      boolean useShift,
      int clickRepeatCount,
      @Nullable Item[] expectedItems,
      @NotNull ActionManager.ActionSource source
   ) {
      return this.queueClick(target, side, hitModifier, useShift, clickRepeatCount, expectedItems, source, false);
   }

   public boolean queueClick(
      @NotNull BlockPos target,
      @NotNull Direction side,
      @NotNull Vec3 hitModifier,
      boolean useShift,
      int clickRepeatCount,
      @Nullable Item[] expectedItems,
      @NotNull ActionManager.ActionSource source,
      boolean requireReplaceableTarget
   ) {
      if (this.queuedClick != null) {
         return false;
      } else {
         this.queuedClick = new QueuedClick(target, side, hitModifier, useShift, clickRepeatCount, source, requireReplaceableTarget);
         this.queuedClick.expectItems(expectedItems);
         return true;
      }
   }

   public void useProtocolHitModifier(@NotNull Vec3 hitModifier) {
      if (this.queuedClick != null) {
         this.queuedClick.useProtocolHit(hitModifier);
      }
   }

   public boolean setQueueCompletionListener(@Nullable Consumer<ActionManager.SendResult> completionListener) {
      if (this.queuedClick == null) {
         return false;
      } else {
         this.queuedClick.onCompletion(completionListener);
         return true;
      }
   }

   public boolean setExpectedStackPredicate(@Nullable Predicate<ItemStack> expectedStackPredicate) {
      if (this.queuedClick == null) {
         return false;
      } else {
         this.queuedClick.expectStack(expectedStackPredicate);
         return true;
      }
   }

   public void setQueuedDirectionalPlacement(boolean directional) {
      this.queuedDirectionalPlacement = directional;
   }

   public ActionManager.SendResult sendQueue(@Nullable LocalPlayer player) {
      QueuedClick click = this.queuedClick;
      if (click == null) {
         return ActionManager.SendResult.NO_QUEUED_ACTION;
      } else {
         if (this.useProtocol && this.hitModifier != null) {
            click.useProtocolHit(this.hitModifier);
         }

         if (player == null) {
            return this.finish(click, ActionManager.SendResult.NO_PLAYER);
         } else if (this.shouldDropStaleQueuedClick(player, click)) {
            return this.finish(click, ActionManager.SendResult.STALE_POSITION);
         } else if (!click.contextValid()) {
            // 等待转向期间世界/目标格位/相邻格位变了：按旧面发会点错，整次作废（调用方会重新排队）
            return this.finish(click, ActionManager.SendResult.STALE_POSITION);
         } else {
            // 不能用 needWaitModifyLook 做门限：等待转向期间朝向可能已被别的移动包覆盖，
            // 恢复发送时必须无条件先补发一次朝向包，否则服务端看到的是旧朝向
            if (this.look != null && this.shouldSendQueuedLook(this.look)) {
               PacketUtils.sendLookPacket(player, this.look);
               this.recordQueuedLook(this.look);
            }

            if (this.shouldWaitForServerLook(player, click)) {
               this.needWaitModifyLook = true;
               return ActionManager.SendResult.WAITING_FOR_LOOK;
            } else {
               if (this.needWaitModifyLook) {
                  this.needWaitModifyLook = false;
               }

               // 这次放置用哪只手：主手没有需要的物品、而副手有 → 直接用副手放（副手拿的建材放不出来的修复）
               InteractionHand useHand = InventoryUtils.placementHand(player, click.expectedItems, click.expectedStackPredicate);
               if (!isHoldingExpectedItem(player, click, useHand)) {
                  return this.finish(click, ActionManager.SendResult.HELD_ITEM_CHANGED);
               } else {
                  int reserveAllowance = this.getReserveAllowance(player, click, useHand);
                  if (reserveAllowance <= 0) {
                     return this.finish(click, ActionManager.SendResult.RESERVE_LIMIT);
                  } else if (click.requireReplaceableTarget
                     && Reference.MINECRAFT.level != null
                     && !BlockUtils.isReplaceable(Reference.MINECRAFT.level.getBlockState(click.target))) {
                     return this.finish(click, ActionManager.SendResult.INTERACTION_REJECTED);
                  } else {
                     Direction direction;
                     if (this.look == null) {
                        direction = click.side;
                     } else {
                        direction = BlockUtils.getHorizontalDirection(this.look.yaw());
                     }

                     Vec3 hitVec;
                     if (!click.useProtocol) {
                        Vec3 targetCenter = Vec3.atCenterOf(click.target);
                        Vec3 sideOffset = Vec3.atLowerCornerOf(BlockUtils.getVector(click.side)).scale(0.5);
                        // 注意单位：Direction.toYRot() 返回「度」，Vec3.yRot() 要「弧度」，必须先换算
                        Vec3 rotatedHitModifier = click.hitModifier
                           .yRot(((direction.toYRot() + 90.0F) % 360.0F) * 0.017453292f)
                           .scale(0.5);
                        hitVec = targetCenter.add(sideOffset).add(rotatedHitModifier);
                        // ★ 命中点计算与原版 EMT 完全一致（中心+面×0.5+旋转修饰），不要做任何投影/修正：
                        //   2026-10-10 曾加过"骑乘时投影回非完整方块轮廓"，但 shape.clip 会返回射线进入面
                        //   （下半砖底面放拉杆 → 命中点被搬到顶面 → 服务端拒绝），兜底还会把面上的点压进方块
                        //   —— 用户实测 EMT 无此问题、新逻辑反而打不出（dt261011c 回退）。
                     } else {
                        hitVec = click.hitModifier;
                     }

                     boolean wasSneak = player.isShiftKeyDown();
                     if (click.useShift && !wasSneak) {
                        this.setShift(player, true);
                     } else if (!click.useShift && wasSneak) {
                        this.setShift(player, false);
                     }

                     if (Reference.MINECRAFT.gameMode instanceof MultiPlayerGameModeExtension gameModeExtension) {
                        boolean var21 = false;
                        this.printerInteractionActive = true;
                        this.easyPlaceProtocolActive = click.useProtocol;
                        this.activeSource = click.source;

                        try {
                           BlockHitResult blockHitResult = new BlockHitResult(hitVec, click.side, click.target, false);
                           boolean localPrediction = !Configs.Print.PRINT_USE_PACKET.getBooleanValue();

                           for (int i = 0; i < click.repeatCount; i++) {
                              int allowance = this.getReserveAllowance(player, click, useHand);
                              if (allowance <= 0) {
                                 break;
                              }

                              boolean interactionAccepted = useItemOnWithLook(
                                    gameModeExtension, localPrediction, useHand, blockHitResult, player, this.look
                                 )
                                 != InteractionResult.FAIL;
                              var21 |= interactionAccepted;
                              if (interactionAccepted) {
                                 this.armTaskAnvilScreenSuppression(click);
                              }

                              if (interactionAccepted && allowance != Integer.MAX_VALUE) {
                                 // 用交互「前」记下的物品：最后一份用完后再读手里会变成空气/换槽，消耗就记错了
                                 Item consumedItem = this.reservedItem != null
                                    ? this.reservedItem
                                    : player.getItemInHand(useHand).getItem();
                                 this.reservePendingConsumed.merge(consumedItem, 1, Integer::sum);
                                 this.reservePendingLastMoveTick
                                    .put(consumedItem, Reference.MINECRAFT.level == null ? 0L : Reference.MINECRAFT.level.getGameTime());
                              }
                           }
                        } catch (RuntimeException e) {
                           // 交互抛异常：队列必须清掉，否则下一 tick 会把同一次点击再发一遍；
                           // 玩家状态（潜行/标志位）仍由 finally 统一恢复
                           this.clearQueue();
                           throw e;
                        } finally {
                           this.printerInteractionActive = false;
                           this.easyPlaceProtocolActive = false;
                           this.activeSource = ActionManager.ActionSource.GENERIC;
                           this.restoreShift(player, click, wasSneak);
                        }

                        if (var21 && click.requireReplaceableTarget) {
                           this.recordUnconfirmedInAirClick(click.target);
                        }

                        return this.finish(click, var21 ? ActionManager.SendResult.SENT : ActionManager.SendResult.INTERACTION_REJECTED);
                     } else {
                        this.restoreShift(player, click, wasSneak);
                        return this.finish(click, ActionManager.SendResult.NO_GAME_MODE);
                     }
                  }
               }
            }
         }
      }
   }

   private void armTaskAnvilScreenSuppression(QueuedClick click) {
      if (!this.hasManualAnvilScreenAllowance()
         && (click.source == ActionManager.ActionSource.PRINT || click.source == ActionManager.ActionSource.FILL)
         && Reference.MINECRAFT.level != null
         && Reference.MINECRAFT.level.getBlockState(click.target).getBlock() instanceof AnvilBlock) {
         this.pendingTaskAnvilScreens = Math.min(64, this.pendingTaskAnvilScreens + 1);
         this.taskAnvilScreenSuppressionDeadlineNanos = System.nanoTime() + 5000000000L;
      }
   }

   public boolean consumeTaskAnvilScreenSuppression() {
      if (this.pendingTaskAnvilScreens <= 0) {
         return false;
      } else if (this.taskAnvilScreenSuppressionDeadlineNanos < System.nanoTime()) {
         this.clearTaskAnvilScreenSuppressions();
         return false;
      } else {
         this.pendingTaskAnvilScreens--;
         if (this.pendingTaskAnvilScreens == 0) {
            this.taskAnvilScreenSuppressionDeadlineNanos = 0L;
         }

         return true;
      }
   }

   public void prioritizeManualAnvilScreen() {
      this.clearTaskAnvilScreenSuppressions();
      this.manualAnvilScreenAllowanceDeadlineNanos = System.nanoTime() + 5000000000L;
   }

   public boolean consumeManualAnvilScreenAllowance() {
      if (!this.hasManualAnvilScreenAllowance()) {
         return false;
      } else {
         this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
         return true;
      }
   }

   public void clearTaskAnvilScreenSuppressions() {
      this.pendingTaskAnvilScreens = 0;
      this.taskAnvilScreenSuppressionDeadlineNanos = 0L;
   }

   private boolean hasManualAnvilScreenAllowance() {
      if (this.manualAnvilScreenAllowanceDeadlineNanos == 0L) {
         return false;
      } else if (this.manualAnvilScreenAllowanceDeadlineNanos < System.nanoTime()) {
         this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
         return false;
      } else {
         return true;
      }
   }

   private int getReserveAllowance(LocalPlayer player, QueuedClick click, InteractionHand hand) {
      // 每一轮（含重复点击的每一轮）都重检手持物：中途用完/换槽就应当停下，不能拿别的物品接着点
      if (!isHoldingExpectedItem(player, click, hand)) {
         this.reservedItem = null;
         return 0;
      }

      ItemStack heldSnapshot = player.getItemInHand(hand);
      this.reservedItem = heldSnapshot.isEmpty() ? null : heldSnapshot.getItem();
      if (click.source == ActionManager.ActionSource.PRINT && Configs.Print.PRINT_RESERVE_ITEMS.getBooleanValue()) {
         ItemStack held = heldSnapshot;
         int reserveCount = Configs.Print.PRINT_RESERVE_ITEM_COUNT.getIntegerValue();
         if (reserveCount >= 0 && !PlayerUtils.getAbilities(player).instabuild && !held.isEmpty() && !held.isDamageableItem()) {
            Predicate<ItemStack> predicate = click.expectedStackPredicate != null
               ? click.expectedStackPredicate
               : candidate -> candidate.is(held.getItem());
            Item item = held.getItem();
            // 用副手放置时，副手那一份也要算进"可用材料"里
            int count = InventoryUtils.countMatchingAvailable(player, predicate, hand == InteractionHand.OFF_HAND);
            int lastSeen = this.reserveLastSeenCount.getOrDefault(item, count);
            int pending = this.reservePendingConsumed.getOrDefault(item, 0);
            long changeTick = this.reservePendingLastMoveTick.getOrDefault(item, Long.MIN_VALUE);
            if (count > lastSeen) {
               pending = 0;
            } else if (count < lastSeen) {
               pending = Math.max(0, pending - (lastSeen - count));
            }

            if (pending > 0 && changeTick != Long.MIN_VALUE) {
               long tick = Reference.MINECRAFT.level == null ? 0L : Reference.MINECRAFT.level.getGameTime();
               if (tick - changeTick > 20L) {
                  pending = 0;
               }
            }

            this.reservePendingConsumed.put(item, pending);
            if (pending == 0) {
               this.reservePendingLastMoveTick.remove(item);
            }

            this.reserveLastSeenCount.put(item, count);
            return Math.max(0, count - reserveCount - pending);
         } else {
            return Integer.MAX_VALUE;
         }
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private void restoreShift(LocalPlayer player, QueuedClick click, boolean wasSneak) {
      if (click.useShift && !wasSneak) {
         this.setShift(player, false);
      } else if (!click.useShift && wasSneak) {
         this.setShift(player, true);
      }
   }

   private ActionManager.SendResult finish(QueuedClick click, ActionManager.SendResult result) {
      this.recordSent(click, result);
      Consumer<ActionManager.SendResult> completionListener = click.completionListener;
      this.clearQueue();
      if (completionListener != null) {
         completionListener.accept(result);
      }

      return result;
   }

   public boolean isPrinterInteractionActive() {
      return this.printerInteractionActive;
   }

   public boolean isPrintInteractionActive() {
      return this.printerInteractionActive && this.activeSource == ActionManager.ActionSource.PRINT;
   }

   public boolean isEasyPlaceProtocolActive() {
      return this.printerInteractionActive && this.activeSource == ActionManager.ActionSource.PRINT && this.easyPlaceProtocolActive;
   }

   /**
    * 该格位现在能不能安全地走"空中放置"（直接点击目标格位本身）。
    *
    * 发包放置（placeUsePacket）不做客户端预测：点击发出后客户端世界要等服务器回包才更新，
    * 这段窗口里如果对同一格位再点一次，服务端看到的是"目标格位已被占用"，会把方块放到
    * 点击面相邻的那一格（默认朝上）——于是投影区域之外会莫名其妙多出一个方块。
    * 因此只要该格位存在"未确认的空中点击"（客户端看到的状态没变、且没有超时），
    * 就返回 false，让调用方改走安全的邻格点击路径（那条路径的落点恒为目标格位，不会外溢）。
    */
   public boolean canUseInAirPlacement(BlockPos pos) {
      if (this.unconfirmedInAirClicks.isEmpty()) {
         return true;
      } else {
         ClientLevel level = Reference.MINECRAFT.level;
         long tick = level == null ? Long.MIN_VALUE : level.getGameTime();
         if (tick != Long.MIN_VALUE) {
            this.pruneUnconfirmedInAirClicks(tick);
         }

         long key = pos.asLong();
         ActionManager.UnconfirmedInAirClick pending = this.unconfirmedInAirClicks.get(key);
         if (pending == null || level == null) {
            return true;
         } else {
            BlockState current = level.getBlockState(pos);
            if (current != pending.stateAtClick || tick - pending.tick > 40L) {
               this.unconfirmedInAirClicks.remove(key);
               return true;
            } else {
               return false;
            }
         }
      }
   }

   /** 记录一次已发出的"空中放置"点击：等到该格位的世界状态变化（或超时）之前不再重复空中点击 */
   private void recordUnconfirmedInAirClick(BlockPos pos) {
      ClientLevel level = Reference.MINECRAFT.level;
      if (level != null) {
         this.unconfirmedInAirClicks
            .put(pos.asLong(), new ActionManager.UnconfirmedInAirClick(level.getBlockState(pos), level.getGameTime()));
      }
   }

   /** 清理超时的未确认记录，避免长期积累（只在记录较多时扫描） */
   private void pruneUnconfirmedInAirClicks(long tick) {
      if (this.unconfirmedInAirClicks.size() > 64) {
         this.unconfirmedInAirClicks.entrySet().removeIf(entry -> tick - entry.getValue().tick > 40L);
      }
   }

   public void armPrintSignEdit(BlockPos blockPos) {
      long now = System.nanoTime();
      this.pruneExpiredPrintSignEdits(now);
      this.pendingPrintSignEdits.put(blockPos.asLong(), now + 30000000000L);
   }

   public void confirmPrintSignEditSent(BlockPos blockPos) {
      this.pendingPrintSignEdits.replace(blockPos.asLong(), System.nanoTime() + 5000000000L);
   }

   public void cancelPrintSignEdit(BlockPos blockPos) {
      this.pendingPrintSignEdits.remove(blockPos.asLong());
   }

   public boolean consumePrintSignEdit(BlockPos blockPos) {
      long now = System.nanoTime();
      Long deadline = this.pendingPrintSignEdits.remove(blockPos.asLong());
      return deadline != null && deadline >= now;
   }

   private void pruneExpiredPrintSignEdits(long now) {
      if (now >= this.nextPrintSignEditPruneNanos) {
         this.nextPrintSignEditPruneNanos = now + 1000000000L;
         this.pendingPrintSignEdits.entrySet().removeIf(entry -> entry.getValue() < now);
      }
   }

   public void setShift(LocalPlayer player, boolean shift) {
      Input input = new Input(
         player.input.keyPresses.forward(),
         player.input.keyPresses.backward(),
         player.input.keyPresses.left(),
         player.input.keyPresses.right(),
         player.input.keyPresses.jump(),
         shift,
         player.input.keyPresses.sprint()
      );
      ServerboundPlayerInputPacket packet = new ServerboundPlayerInputPacket(input);
      player.setShiftKeyDown(shift);
      PacketUtils.sendPacket(packet);
   }

   public void setWaitForHorizontalLook(boolean waitForHorizontalLook) {
      this.waitForHorizontalLook = waitForHorizontalLook;
   }

   public void setNeedWaitModifyLookFromAction(boolean actionRequiresWaitModifyLook) {
      this.actionRequiresWaitModifyLook = actionRequiresWaitModifyLook;
   }

   private boolean shouldWaitForServerLook(LocalPlayer player, QueuedClick click) {
      if (Configs.Print.PRINT_FAST_DIRECTIONAL_PLACEMENT.getBooleanValue() && this.queuedDirectionalPlacement) {
         return false;
      } else if ((this.waitForHorizontalLook || this.actionRequiresWaitModifyLook) && !click.useProtocol && !this.needWaitModifyLook && this.look != null) {
         Direction lookDirection = BlockUtils.orderedByNearest(this.look.yaw(), this.look.pitch())[0];
         return lookDirection.getAxis().isHorizontal() && !isPlayerLookSettled(player, this.look);
      } else {
         return false;
      }
   }

   private boolean shouldDropStaleQueuedClick(LocalPlayer player, QueuedClick click) {
      if (this.needWaitModifyLook && click.queuedPlayerPosition != null) {
         double speed = player.getDeltaMovement().length() * 20.0;
         double staleDistance = Configs.Print.PRINT_FAST_DIRECTIONAL_PLACEMENT.getBooleanValue() && this.queuedDirectionalPlacement && speed > 14.0
            ? 2.0
            : Math.sqrt(0.5625);
         long currentTick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
         return currentTick != Long.MIN_VALUE && currentTick > click.queuedTick
            ? player.position().distanceToSqr(click.queuedPlayerPosition) > staleDistance * staleDistance
            : false;
      } else {
         return false;
      }
   }

   private static boolean isHoldingExpectedItem(LocalPlayer player, QueuedClick click, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      if (click.expectedStackPredicate != null && !click.expectedStackPredicate.test(held)) {
         return false;
      } else if (click.expectedItems != null && click.expectedItems.length != 0) {
         Item heldItem = held.getItem();

         for (Item expectedItem : click.expectedItems) {
            if (heldItem.equals(expectedItem)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   private static boolean isPlayerLookSettled(LocalPlayer player, PlayerLook look) {
      return Math.abs(Mth.wrapDegrees(player.getYRot() - look.yaw())) <= 1.0F && Math.abs(player.getXRot() - look.pitch()) <= 1.0F;
   }

   /**
    * 同一 tick 内只发一次「相同朝向」的朝向包；跨 tick 仍然无条件重发。
    * <p>
    * 跨 tick 不能去重（fix1 的教训）：等待转向期间朝向可能已被别的移动包覆盖，恢复发送时必须补发，
    * 否则服务端看到的朝向与点击面不符。
    * 同一 tick 可以去重：一个 tick 内玩家自己的移动包至多一个，之后发的朝向包不会被覆盖；
    * 填充模式一 tick 能连放几十个同朝向的方块，逐格重发只是把带宽与服务端负载放大几十倍
    * （服务端处理不过来就表现为「方块一顿一顿地冒出来」）。
    */
   private boolean shouldSendQueuedLook(PlayerLook look) {
      long tick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
      return tick == Long.MIN_VALUE
         || tick != this.lastQueuedLookTick
         || Math.abs(look.yaw() - this.lastQueuedLookYaw) > 1.0E-4F
         || Math.abs(look.pitch() - this.lastQueuedLookPitch) > 1.0E-4F;
   }

   private void recordQueuedLook(PlayerLook look) {
      this.lastQueuedLookTick = Reference.MINECRAFT.level == null ? Long.MIN_VALUE : Reference.MINECRAFT.level.getGameTime();
      this.lastQueuedLookYaw = look.yaw();
      this.lastQueuedLookPitch = look.pitch();
   }

   public void clearQueue() {
      this.queuedClick = null;
      this.hitModifier = null;
      this.useProtocol = false;
      this.needWaitModifyLook = false;
      this.waitForHorizontalLook = true;
      this.actionRequiresWaitModifyLook = false;
      this.look = null;
      this.printerInteractionActive = false;
      this.easyPlaceProtocolActive = false;
      this.activeSource = ActionManager.ActionSource.GENERIC;
      this.queuedDirectionalPlacement = false;
   }

   public void resetRuntime() {
      this.clearQueue();
      this.pendingPrintSignEdits.clear();
      this.unconfirmedInAirClicks.clear();
      this.nextPrintSignEditPruneNanos = 0L;
      this.clearTaskAnvilScreenSuppressions();
      this.manualAnvilScreenAllowanceDeadlineNanos = 0L;
      // 换世界/重连：保留量与消耗归属、朝向缓存全部作废，否则会按旧会话的数据继续算
      this.reservePendingConsumed.clear();
      this.reserveLastSeenCount.clear();
      this.reservePendingLastMoveTick.clear();
      this.reservedItem = null;
      this.lastQueuedLookTick = Long.MIN_VALUE;
      this.lastQueuedLookYaw = 0.0F;
      this.lastQueuedLookPitch = 0.0F;
      // 换世界/重连：上一局登记过的「已发送未确认」坐标全部作废
      PlacementPending.reset();
   }

   /**
    * 普通（非包模式）放置是本机预测，原版 {@code useItemOn} 会用玩家当前的 yaw/pitch 决定
    * 楼梯/半砖等方块的朝向与上下半区，而打印机是"先转向再点击"，预测就会和服务的观感不一致。
    * 这里在调用期间临时改成打印机的朝向，调用结束（含异常）立刻恢复原值；
    * 包模式发送的是纯服务端交互，不需要也不应该改本地视角。
    */
   private static InteractionResult useItemOnWithLook(
      MultiPlayerGameModeExtension gameMode,
      boolean localPrediction,
      InteractionHand hand,
      BlockHitResult hitResult,
      LocalPlayer player,
      @Nullable PlayerLook look
   ) {
      if (!localPrediction || look == null) {
         return gameMode.litematica_printer$useItemOn(localPrediction, hand, hitResult);
      }

      float yaw = player.getYRot();
      float pitch = player.getXRot();

      try {
         player.setYRot(look.yaw());
         player.setXRot(look.pitch());
         return gameMode.litematica_printer$useItemOn(localPrediction, hand, hitResult);
      } finally {
         player.setYRot(yaw);
         player.setXRot(pitch);
      }
   }

   /**
    * 记下这次点击真正要改的坐标和发送前状态，供 {@link PlacementPending} 在发送成功后登记。
    * 普通放置点的是目标格本身；工具/贴脸点击（{@link ClickAction}）点的是点击面相邻格，
    * 只有半砖（要合成双半砖，实际改的还是目标格）例外。
    */
   public void trackPrint(BlockPos pos, Action action) {
      QueuedClick click = this.queuedClick;
      ClientLevel level = Reference.MINECRAFT.level;
      if (click == null || level == null) {
         return;
      }

      if (action instanceof ClickAction && !(level.getBlockState(pos).getBlock() instanceof SlabBlock)) {
         return;
      }

      click.printPos = pos.immutable();
      click.printBefore = level.getBlockState(pos);
   }

   /** 只有真正发出的点击才登记；被拒绝/取消的不该挡住后续打印 */
   private void recordSent(QueuedClick click, ActionManager.SendResult result) {
      if (result != ActionManager.SendResult.SENT || click.printPos == null || click.printBefore == null) {
         return;
      }

      ClientLevel level = Reference.MINECRAFT.level;
      if (level == null) {
         return;
      }

      PlacementPending.begin(level, click.printPos.asLong(), click.printBefore, currentLatencyMs());
   }

   /** 客户端记录的服务器延迟（毫秒）；取不到返回 -1，由待确认表用保守的默认窗口 */
   private static int currentLatencyMs() {
      ClientPacketListener connection = Reference.MINECRAFT.getConnection();
      LocalPlayer player = Reference.MINECRAFT.player;
      if (connection == null || player == null) {
         return -1;
      }

      PlayerInfo info = connection.getPlayerInfo(player.getUUID());
      return info == null ? -1 : info.getLatency();
   }

   @Generated
   public void setLook(@Nullable PlayerLook look) {
      this.look = look;
   }

   /** 一次已经发出、但还没等到世界更新的"空中放置"点击 */
   private static final class UnconfirmedInAirClick {
      final BlockState stateAtClick;
      final long tick;

      UnconfirmedInAirClick(BlockState stateAtClick, long tick) {
         this.stateAtClick = stateAtClick;
         this.tick = tick;
      }
   }

   public static enum ActionSource {
      GENERIC,
      PRINT,
      FILL,
      FLUID;
   }

   public static enum SendResult {
      SENT,
      WAITING_FOR_LOOK,
      NO_QUEUED_ACTION,
      NO_PLAYER,
      STALE_POSITION,
      HELD_ITEM_CHANGED,
      RESERVE_LIMIT,
      NO_GAME_MODE,
      INTERACTION_REJECTED;

      public boolean isSent() {
         return this == SENT;
      }

      public boolean isWaiting() {
         return this == WAITING_FOR_LOOK;
      }
   }
}
