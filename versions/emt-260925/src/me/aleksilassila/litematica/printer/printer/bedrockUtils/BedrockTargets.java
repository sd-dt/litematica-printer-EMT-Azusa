package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 破基岩子系统与打印机的对接点（移植自三改版 Printer.bedrockModeTarget / bedrockModeRange）。
 * <p>
 * 三改版把这两件事放在 Printer 里，这里独立出来，避免移植代码反向依赖 EMT 的打印主流程。
 */
public final class BedrockTargets {
   private BedrockTargets() {
   }

   /** 该方块状态是否是"要啃掉的基岩目标"（按「基岩模式白名单」匹配，支持 名字/拼音/#标签 写法） */
   public static boolean isTarget(@Nullable BlockState state) {
      return state != null && Configs.Bedrock.BEDROCK_LIST.getStrings().stream().anyMatch(s -> PinYinSearchUtils.matchName(s, state));
   }

   /** 破基岩的工作半径：与三改版一致，直接复用打印机工作半径 */
   public static int range() {
      return ConfigUtils.getWorkRange();
   }
}
