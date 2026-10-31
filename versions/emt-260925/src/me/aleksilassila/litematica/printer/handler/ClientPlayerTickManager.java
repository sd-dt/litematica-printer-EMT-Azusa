package me.aleksilassila.litematica.printer.handler;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import lombok.Generated;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.go.AutoWalkScanner;
import me.aleksilassila.litematica.printer.go.GoManager;
import me.aleksilassila.litematica.printer.handler.handlers.BedrockHandler;
import me.aleksilassila.litematica.printer.handler.handlers.FillHandler;
import me.aleksilassila.litematica.printer.handler.handlers.FluidHandler;
import me.aleksilassila.litematica.printer.handler.handlers.GuiHandler;
import me.aleksilassila.litematica.printer.handler.handlers.MineHandler;
import me.aleksilassila.litematica.printer.handler.handlers.PrintHandler;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.BlockPosCooldownManager;
import me.aleksilassila.litematica.printer.printer.PrintTaskController;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.EatUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.jetbrains.annotations.Nullable;

public class ClientPlayerTickManager {
   public static final Minecraft mc = Minecraft.getInstance();
   public static final GuiHandler GUI = new GuiHandler();
   public static final PrintHandler PRINT = new PrintHandler();
   public static final FillHandler FILL = new FillHandler();
   public static final MineHandler MINE = new MineHandler();
   public static final FluidHandler FLUID = new FluidHandler();
   public static final BedrockHandler BEDROCK = new BedrockHandler();
   private static int packetTick;
   private static long currentHandlerTime;
   /** 上一次 tick 看到的客户端世界对象（只用于「对象身份」比较，不参与业务） */
   @Nullable
   private static ClientLevel lastLevel;
   public static final ImmutableList<ClientPlayerTickHandler> VALUES = ImmutableList.of(GUI, PRINT, FILL, FLUID, MINE, BEDROCK);

   /**
    * 换世界/重连检测：用 ClientLevel 的对象身份比较。
    * 不能用 dimension() 之类的逻辑键 —— 同一维度退出存档再进（或重连）维度键相同，检测不到，
    * 旧会话的冷却/重试/保留量会继续生效。
    */
   public static void checkDimensionChange(@Nullable ClientLevel level) {
      if (level != lastLevel) {
         lastLevel = level;
         if (level != null) {
            resetRuntimeState();
         }
      }
   }

   public static void resetRuntimeState() {
      PrintTaskController.INSTANCE.reset();
      BreakUtils.INSTANCE.resetRuntime();
      ActionManager.INSTANCE.resetRuntime();
      PRINT.resetFastRetry();
      BlockPosCooldownManager.INSTANCE.clearAllCooldowns();
   }

   public static void tick() {
      GoManager.INSTANCE.tick();
      AutoWalkScanner.INSTANCE.tick();
      if (!InventoryUtils.isOpenHandler && !InventoryUtils.switchItem() && !BreakUtils.INSTANCE.isNeedHandle() && !EatUtils.isBusy()) {
         if (!ActionManager.INSTANCE.sendQueue(mc.player).isWaiting()) {
            if (Configs.Core.LAG_CHECK.getBooleanValue()) {
               if (packetTick > Configs.Core.LAG_CHECK_MAX.getIntegerValue()) {
                  return;
               }

               packetTick++;
            }

            UnmodifiableIterator var0 = VALUES.iterator();

            while (var0.hasNext()) {
               ClientPlayerTickHandler handler = (ClientPlayerTickHandler)var0.next();
               if (!(handler instanceof GuiHandler)) {
                  if (InventoryUtils.isOpenHandler || InventoryUtils.switchItem() || BreakUtils.INSTANCE.isNeedHandle()) {
                     return;
                  }

                  if (ActionManager.INSTANCE.needWaitModifyLook) {
                     return;
                  }
               }

               handler.tick();
            }
         }
      }
   }

   public static void updateTickHandlerTime() {
      currentHandlerTime++;
   }

   @Generated
   public static int getPacketTick() {
      return packetTick;
   }

   @Generated
   public static void setPacketTick(int packetTick) {
      ClientPlayerTickManager.packetTick = packetTick;
   }

   @Generated
   public static long getCurrentHandlerTime() {
      return currentHandlerTime;
   }
}
