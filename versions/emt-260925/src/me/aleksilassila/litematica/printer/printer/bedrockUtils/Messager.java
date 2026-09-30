package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import me.aleksilassila.litematica.printer.utils.MessageUtils;

/**
 * 破基岩子系统的提示通道（移植自三改版 bedrockUtils.Messager）。
 * 三改版直接用 Minecraft 的 actionBar/chat，这里改为走 EMT 自己的 MessageUtils，行为一致。
 */
public final class Messager {
   private Messager() {
   }

   /** 动作栏提示（参数是翻译键，例如 bedrockminer.fail.missing.piston） */
   public static void actionBar(String message) {
      MessageUtils.setOverlayMessage(MessageUtils.translatable(message));
   }

   /** 聊天栏提示 */
   public static void chat(String message) {
      MessageUtils.addMessage(MessageUtils.translatable(message));
   }
}
