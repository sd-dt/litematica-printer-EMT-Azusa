package me.aleksilassila.litematica.printer;

import fi.dy.masa.malilib.interfaces.IInitializationHandler;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.gui.ConfigUi;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.bedrockUtils.BreakingFlowController;
import me.aleksilassila.litematica.printer.printer.verifier.PendingChunkRenderer;
import me.aleksilassila.litematica.printer.printer.verifier.VerifierRegistry;
import me.aleksilassila.litematica.printer.printer.zxy.utils.HighlightBlockRenderer;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.PacketRateLimiter;
import me.aleksilassila.litematica.printer.utils.ModUtils;

public class InitHandler implements IInitializationHandler {
   public void registerModHandlers() {
      Configs.init();
      this.initConfigCallback();
      HighlightBlockRenderer.init();
      PendingChunkRenderer.init();
      VerifierRegistry.init();
   }

   private void initConfigCallback() {
      Configs.Hotkeys.CLOSE_ALL_MODE.getKeybind().setCallback((action, keybind) -> {
         if (keybind.isKeybindHeld()) {
            Configs.Core.MINE.setBooleanValue(false);
            Configs.Core.FLUID.setBooleanValue(false);
            Configs.Core.WORK_SWITCH.setBooleanValue(false);
            Configs.Core.WORK_MODE_TYPE.setOptionListValue(PrintModeType.PRINTER);
            MessageUtils.setOverlayMessage(MessageUtils.nullToEmpty("已关闭全部模式"));
         }

         return true;
      });
      Configs.Core.WORK_SWITCH.setValueChangeCallback(b -> {
         if (!b.getBooleanValue()) {
            ActionManager.INSTANCE.clearQueue();
            // 自研破基岩流程：关掉打印机时清空目标机器与队列
            BreakingFlowController.clearAll();
            // 关掉打印机时复位发包限流状态，避免把上一次的降速带到下次
            PacketRateLimiter.reset();
         }
      });
      Configs.Core.WORK_MODE_TYPE.setValueChangeCallback(b -> {
         if (!b.getOptionListValue().equals(PrintModeType.BEDROCK)) {
            BreakingFlowController.clearAll();
         }
      });
      // 开启挖掘模式时自动打开「自动工具切换」（核心分类里的两个开关之一）
      Configs.Core.MINE.setValueChangeCallback(b -> {
         if (b.getBooleanValue() && !Configs.Core.AUTO_TOOL_SWITCH.getBooleanValue()) {
            Configs.Core.AUTO_TOOL_SWITCH.setBooleanValue(true);
            ConfigUi.refresh();
         }
      });
      Configs.Core.WORK_MODE.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Print.FILL_COMPOSTER.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Mine.BREAK_LIMITER.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Mine.BREAK_LIMIT.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Mine.EXCAVATE_LIMITER.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Mine.EXCAVATE_LIMIT.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Fill.FILL_BLOCK_MODE.setValueChangeCallback(b -> ConfigUi.refresh());
      Configs.Core.LAG_CHECK.setValueChangeCallback(b -> ConfigUi.refresh());
      // 重置发包上限：点一下（切成"开"）就清空所有服务器学到的上限记录，然后立刻弹回"关"
      Configs.Core.RESET_PACKET_LIMIT.setValueChangeCallback(b -> {
         if (!b.getBooleanValue()) {
            return;
         }

         int cleared = PacketRateLimiter.clearLearned();
         MessageUtils.setOverlayMessage(I18n.of("message.packetLimitCleared").getName(cleared).getString());
         b.setBooleanValue(false);
         ConfigUi.refresh();
      });
   }
}
