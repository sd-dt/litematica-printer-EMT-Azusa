package me.aleksilassila.litematica.printer.printer;

import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class QueuedClick {
   final BlockPos target;
   final Direction side;
   Vec3 hitModifier;
   final boolean useShift;
   final boolean requireReplaceableTarget;
   boolean useProtocol;
   final int repeatCount;
   final ActionManager.ActionSource source;
   @Nullable
   final Vec3 queuedPlayerPosition;
   final long queuedTick;
   /** 入队时的世界引用：等待转向期间可能换世界/重连，发送前要用对象身份比较 */
   @Nullable
   final ClientLevel queuedLevel;
   /** 入队时目标格位与点击面相邻格位的方块状态：等待期间可能被玩家/水流/活塞改掉 */
   @Nullable
   final BlockState targetStateAtQueue;
   @Nullable
   final BlockState adjacentStateAtQueue;
   @Nullable
   Item[] expectedItems;
   @Nullable
   Consumer<ActionManager.SendResult> completionListener;
   @Nullable
   Predicate<ItemStack> expectedStackPredicate;
   /** 这一击真正要改的坐标（普通点击=目标格，贴脸点击=点击面相邻格）；用于待确认表 */
   @Nullable
   BlockPos printPos;
   /** 发送前该坐标的方块状态：服务端回包只有状态不同才算确认 */
   @Nullable
   BlockState printBefore;

   QueuedClick(
      @NotNull BlockPos target,
      @NotNull Direction side,
      @NotNull Vec3 hitModifier,
      boolean useShift,
      int repeatCount,
      @NotNull ActionManager.ActionSource source,
      boolean requireReplaceableTarget
   ) {
      // 坐标必须快照：上游扫描/迭代循环可能复用 MutableBlockPos，队列跨 tick 保存引用会被改掉
      this.target = target.immutable();
      this.side = side;
      this.hitModifier = hitModifier == null ? Vec3.ZERO : hitModifier;
      this.useShift = useShift;
      this.requireReplaceableTarget = requireReplaceableTarget;
      this.repeatCount = Math.max(1, repeatCount);
      this.source = source;
      Minecraft client = Minecraft.getInstance();
      this.queuedPlayerPosition = client.player == null ? null : client.player.position();
      this.queuedTick = client.level == null ? Long.MIN_VALUE : client.level.getGameTime();
      this.queuedLevel = client.level;
      this.targetStateAtQueue = client.level == null ? null : client.level.getBlockState(this.target);
      this.adjacentStateAtQueue = client.level == null ? null : client.level.getBlockState(this.target.relative(this.side));
   }

   /**
    * 发送前重读现场：世界对象、目标格位、点击面相邻格位的状态都必须和入队时一致，
    * 否则说明等待转向/排队期间现场已经变了，按旧面发会点错地方。
    */
   boolean contextValid() {
      ClientLevel level = Minecraft.getInstance().level;
      if (level == null || level != this.queuedLevel) {
         return false;
      } else {
         return level.getBlockState(this.target) == this.targetStateAtQueue
            && level.getBlockState(this.target.relative(this.side)) == this.adjacentStateAtQueue;
      }
   }

   void useProtocolHit(Vec3 hitModifier) {
      this.hitModifier = hitModifier;
      this.useProtocol = true;
   }

   void expectItems(@Nullable Item[] expectedItems) {
      this.expectedItems = expectedItems == null ? null : (Item[])expectedItems.clone();
   }

   void onCompletion(@Nullable Consumer<ActionManager.SendResult> completionListener) {
      this.completionListener = completionListener;
   }

   void expectStack(@Nullable Predicate<ItemStack> expectedStackPredicate) {
      this.expectedStackPredicate = expectedStackPredicate;
   }
}
