package me.aleksilassila.litematica.printer.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 打印机发包限流器（「自动限制发包上限」）。
 * <p>
 * 规则（按需求改成"以第一次被踢出前的发包量为准"）：
 * <ol>
 *   <li><b>被踢之前完全不限速</b> —— 不再有"一上来就压速/根据延迟自动降速"的逻辑，
 *       所以也不会再把挖掘速度拖慢；</li>
 *   <li>检测到"从世界里掉出来（被踢/掉线）"且最近还在发包时，把<b>被踢前那一秒实际发出的动作包数量</b>
 *       记为该服务器的上限（上限/20 = 每 tick 允许的动作数，下限 {@value #MIN_LIMIT}/秒）；</li>
 *   <li>上限按服务器地址持久化到 {@code config/litematica-printer-packetlimit.json}，重启后依然生效；
 *       之后再被踢且被踢前速率更低时，取更低的那个（更保守）。</li>
 * </ol>
 * 令牌按"客户端 tick"复位：破坏队列非空时 {@code ClientPlayerTickManager.tick()} 不会执行，
 * 不能依赖它来复位（这是之前"破坏挖到一半停住"的根因）。
 */
public final class PacketRateLimiter {
   /** 学到的上限的下限/上限（动作包每秒） */
   private static final int MIN_LIMIT = 5;
   private static final int MAX_LIMIT = 1000;
   /** 每 20 tick（1 秒）滚动一格统计窗口 */
   private static final int WINDOW_TICKS = 20;
   /** 保留最近 10 秒，用于"被踢前的发包量" */
   private static final int HISTORY_SIZE = 10;
   /** 掉线后多久内还有动作才算"是因为发包被踢/掉线" */
   private static final long KICK_ACTIVITY_MS = 15000L;

   private static final Logger LOGGER = LoggerFactory.getLogger("litematica-printer/packetlimit");
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final AtomicInteger tickActions = new AtomicInteger();
   private static long budgetTick = Long.MIN_VALUE;
   private static int lastTickActions;
   /** 滚动窗口：每秒的动作数 */
   private static final int[] history = new int[HISTORY_SIZE];
   private static int historyIndex;
   private static int secondActions;
   private static int secondPackets;
   private static int windowTicks;
   private static long lastActionTime;
   private static boolean wasInWorld;
   /** 各服务器学到的上限（动作包/秒） */
   private static final Map<String, Integer> learned = new HashMap<>();
   private static boolean loaded;
   /** 当前服务器的上限：<= 0 表示还没被踢过 → 不限速 */
   private static int limit;
   private static String currentServer = "singleplayer";
   private static int lastPacketTick;

   private PacketRateLimiter() {
   }

   /** 是否开启自动限流（config 直接读取，便于热切换） */
   public static boolean enabled() {
      return Configs.Core.AUTO_PACKET_LIMIT.getBooleanValue();
   }

   /** 每个游戏刻调用一次（由 MixinLocalPlayer 驱动：破坏期间也会跑） */
   public static void tick(int packetTick) {
      lastPacketTick = packetTick;
      if (!loaded) {
         load();
      }

      detectWorldChange();

      if (++windowTicks >= WINDOW_TICKS) {
         windowTicks = 0;
         historyIndex = (historyIndex + 1) % HISTORY_SIZE;
         history[historyIndex] = secondActions;
         secondActions = 0;
         secondPackets = 0;
      }
   }

   /** 统计一次实际发出的数据包（所有经打印机发出的包都会计数，但不一定被拦截） */
   public static void onPacketSent() {
      secondPackets++;
   }

   /**
    * 是否允许本 tick 再执行一个"重动作"（放置 / 破坏）。
    * <p>
    * 没开启限流、或还没学到上限（没被踢过）时恒为 true；
    * 学到之后按"每 tick 上限 = limit / 20"放行。
    */
   public static boolean allowAction() {
      if (!enabled()) {
         return true;
      }

      long tick = ClientPlayerTickManager.getCurrentHandlerTime();
      if (tick != budgetTick) {
         budgetTick = tick;
         lastTickActions = tickActions.getAndSet(0);
      }

      if (limit <= 0) {
         // 还没被踢过：不限速（这也是"挖掘变慢"的修复点）
         tickActions.incrementAndGet();
         secondActions++;
         lastActionTime = System.currentTimeMillis();
         return true;
      }

      int perTickBudget = Math.max(1, limit / 20);
      if (tickActions.get() >= perTickBudget) {
         return false;
      }

      tickActions.incrementAndGet();
      secondActions++;
      lastActionTime = System.currentTimeMillis();
      return true;
   }

   /** 当前生效的上限（动作包/秒）；0 = 还没学到（不限速） */
   public static int getLimit() {
      return enabled() ? Math.max(0, limit) : 0;
   }

   /** 当前服务器标识（服务器地址；单机为 singleplayer） */
   public static String getCurrentServer() {
      return currentServer;
   }

   /** 供调试信息显示 */
   public static String describe() {
      return "limit=" + getLimit() + "/s server=" + currentServer + " tickActions=" + lastTickActions
         + " packets=" + secondPackets + " actions=" + secondActions + " packetTick=" + lastPacketTick;
   }

   /** 打印机整体关闭时复位运行期状态；学到的上限（按服务器持久化）保留 */
   public static void reset() {
      tickActions.set(0);
      windowTicks = 0;
      secondActions = 0;
      secondPackets = 0;
      lastTickActions = 0;
      lastPacketTick = 0;
   }

   /** 语言/调试用：清掉缓存，下次进入世界重新按当前服务器取值 */
   public static void reloadForWorld() {
      wasInWorld = false;
   }

   /** 已记录上限的服务器数量 */
   public static int getLearnedCount() {
      return learned.size();
   }

   /**
    * 一键清空所有服务器学到的发包上限（含磁盘缓存）：
    * 回到"没被踢过 → 不限速"的状态，之后可以重新测试、被踢时再写入新的上限。
    *
    * @return 被清掉的服务器条目数
    */
   public static int clearLearned() {
      int cleared = learned.size();
      learned.clear();
      limit = 0;
      tickActions.set(0);
      windowTicks = 0;
      secondActions = 0;
      secondPackets = 0;
      lastTickActions = 0;
      Arrays.fill(history, 0);
      save();
      LOGGER.info("[packet-limit] cleared {} server entr{}", cleared, cleared == 1 ? "y" : "ies");
      return cleared;
   }

   private static void detectWorldChange() {
      Minecraft client = Minecraft.getInstance();
      boolean inWorld = client != null && client.level != null && client.player != null;

      if (inWorld) {
         if (!wasInWorld) {
            wasInWorld = true;
            currentServer = resolveServerName(client);
            Integer known = learned.get(currentServer);
            limit = known == null ? 0 : known;
         }

         return;
      }

      if (!wasInWorld) {
         return;
      }

      wasInWorld = false;

      // 刚从世界掉出来：若最近还在发包，就认为是被踢/掉线 → 学习"被踢前的发包量"
      if (System.currentTimeMillis() - lastActionTime > KICK_ACTIVITY_MS) {
         return;
      }

      learnFromKick();
   }

   /** 被踢/掉线：把"被踢前那一秒的动作包数量"记为该服务器的上限 */
   private static void learnFromKick() {
      int beforeKick = history[historyIndex];
      if (beforeKick <= 0) {
         // 那一秒刚好没记满（比如刚进游戏就被踢）：退回取最近 10 秒的峰值
         for (int value : history) {
            beforeKick = Math.max(beforeKick, value);
         }
      }

      if (beforeKick <= 0) {
         return;
      }

      int value = Math.min(MAX_LIMIT, Math.max(MIN_LIMIT, beforeKick));
      Integer existing = learned.get(currentServer);
      if (existing != null) {
         // 已经被踢过一次：这次更低就用更低的（更保守），否则保持第一次学到的值
         value = Math.min(value, existing);
      }

      limit = value;
      learned.put(currentServer, value);
      save();
      MessageUtils.setOverlayMessage("发包上限：按被踢前的 " + beforeKick + "/秒 设为 " + value + "/秒（" + currentServer + "）");
      LOGGER.info("[packet-limit] learned {} actions/s for {} (beforeKick={}, history={})", value, currentServer, beforeKick, java.util.Arrays.toString(history));
   }

   private static String resolveServerName(Minecraft client) {
      try {
         ServerData info = client.getCurrentServer();
         if (info != null && info.ip != null && !info.ip.isEmpty()) {
            return info.ip;
         }
      } catch (Exception ignored) {
         // 单人/异常情况：落到默认值
      }

      return "singleplayer";
   }

   private static Path configPath() {
      return FabricLoader.getInstance().getGameDir().resolve("config").resolve("litematica-printer-packetlimit.json");
   }

   private static void load() {
      loaded = true;
      try {
         Path path = configPath();
         if (Files.exists(path)) {
            Map<String, Integer> read = GSON.fromJson(
               Files.readString(path, StandardCharsets.UTF_8), new TypeToken<Map<String, Integer>>() {
               }.getType()
            );
            if (read != null) {
               learned.putAll(read);
            }
         }
      } catch (Exception e) {
         LOGGER.warn("[packet-limit] 读取上限文件失败：{}", e.toString());
      }
   }

   private static void save() {
      try {
         Path path = configPath();
         Files.createDirectories(path.getParent());
         Files.writeString(path, GSON.toJson(learned), StandardCharsets.UTF_8);
      } catch (IOException e) {
         LOGGER.warn("[packet-limit] 写入上限文件失败：{}", e.toString());
      }
   }
}
