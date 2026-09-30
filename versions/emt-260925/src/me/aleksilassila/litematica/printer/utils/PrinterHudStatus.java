package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.Configs;

/**
 * MiniHUD 里那一行「工作状态」文本的构造器。
 * <p>
 * 文本：{@code 打印机: 开 | 模式: 打印}（打印机关闭时只显示 {@code 打印机: 关}）。
 * 真正把它插进 MiniHUD HUD 的动作在 {@code mixin/compat/MixinMiniHudRenderHandler} 里，
 * 注入 MiniHUD 的 {@code RenderHandler#updateLines} 末尾 → 该行永远排在 HUD 最后一行，
 * 并且走 MiniHUD 自己的渲染（字号/颜色/背景/阴影/对齐都由 MiniHUD 的设置决定）。
 */
public final class PrinterHudStatus {
   private static final I18n LABEL_PRINTER = I18n.of("hud.workStatus.printer");
   private static final I18n LABEL_MODE = I18n.of("hud.workStatus.mode");

   private PrinterHudStatus() {
   }

   /** 需要显示的那一行；关闭开关时返回 null（MiniHUD 那边就不加这一行） */
   public static String line() {
      if (!Configs.Core.HUD_WORK_STATUS.getBooleanValue()) {
         return null;
      }

      boolean working = Configs.Core.WORK_SWITCH.getBooleanValue();
      String state = (working ? I18n.MESSAGE_VALUE_ON : I18n.MESSAGE_VALUE_OFF).getName().getString();
      String text = LABEL_PRINTER.getName().getString() + ": " + state;

      // 关闭时也显示模式：模式是"下次开启会用哪个"，照样有参考价值
      IConfigOptionListEntry mode = Configs.Core.WORK_MODE_TYPE.getOptionListValue();
      if (mode != null) {
         text = text + " | " + LABEL_MODE.getName().getString() + ": " + mode.getDisplayName();
      }

      return text;
   }
}
