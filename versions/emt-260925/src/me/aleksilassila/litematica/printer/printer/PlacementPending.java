package me.aleksilassila.litematica.printer.printer;

import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * 按坐标的「已发送、未确认」放置表。
 * <p>
 * 发包放置不做客户端预测，点击发出后客户端世界要等服务器回包才更新。这段窗口里如果再对同一
 * 坐标点一次（普通扫描或快速重试），服务端会把方块放到点击面相邻那一格 —— 合成双半砖时就会
 * 向外多出一个半砖。所以发送完成时按坐标登记一次，只有<b>服务端方块更新</b>能把对应坐标放行；
 * 本地预测（{@code ClientLevel.setBlock}）不算确认。失败发送不登记。
 * <p>
 * 只按坐标查找，不做全局睡眠、不改每 tick 配额；坐标 A 待确认时 B、C 仍可正常打印。
 * 过期条目最多每 250ms 清扫一次，避免每个方块都遍历整张表。
 */
public final class PlacementPending {
   private static final Map<Long, Entry> PENDING = new HashMap<>();
   private static final long SWEEP_INTERVAL_NANOS = 250_000_000L;
   private static Object world;
   private static long nextSweep;
   /** 便于测试替换时钟；生产环境就是 {@link System#nanoTime()} */
   private static LongSupplier clock = System::nanoTime;

   private PlacementPending() {
   }

   /** 等待窗口：按客户端记录的延迟估算，下限 250ms、上限 10s；取不到延迟时 750ms */
   public static long timeoutNanos(int latencyMs) {
      long millis = latencyMs < 0 ? 750L : Math.max(250L, Math.min(10_000L, 2L * latencyMs + 200L));
      return millis * 1_000_000L;
   }

   /** 换世界/重连时整表作废：旧世界的坐标、状态、截止时间都没有意义了 */
   private static void bind(Object currentWorld) {
      if (world != currentWorld) {
         PENDING.clear();
         world = currentWorld;
         nextSweep = 0L;
      }
   }

   public static synchronized void begin(Object currentWorld, long pos, Object before, int latencyMs) {
      if (currentWorld == null) {
         return;
      }
      bind(currentWorld);
      long now = clock.getAsLong();
      if (nextSweep == 0L || now - nextSweep >= 0L) {
         PENDING.values().removeIf(entry -> now - entry.deadline >= 0L);
         nextSweep = now + SWEEP_INTERVAL_NANOS;
      }
      PENDING.put(pos, new Entry(before, now + timeoutNanos(latencyMs)));
   }

   /** 该坐标还在等确认就返回 true（调用方应跳过它）；过期条目顺手清掉 */
   public static synchronized boolean blocked(Object currentWorld, long pos) {
      bind(currentWorld);
      Entry entry = PENDING.get(pos);
      if (entry == null) {
         return false;
      }
      if (clock.getAsLong() - entry.deadline >= 0L) {
         PENDING.remove(pos);
         return false;
      }
      return true;
   }

   /**
    * 服务端方块更新回来的确认：只有状态真的和发送前不同才算数。
    * 相同状态的重复通知（例如别的插件重发）不能提前放行。
    */
   public static synchronized void confirm(Object currentWorld, long pos, Object serverState) {
      if (currentWorld != world) {
         return;
      }
      Entry entry = PENDING.get(pos);
      if (entry != null && serverState != entry.before) {
         PENDING.remove(pos);
      }
   }

   public static synchronized void reset() {
      PENDING.clear();
      world = null;
      nextSweep = 0L;
   }

   private record Entry(Object before, long deadline) {
   }
}
