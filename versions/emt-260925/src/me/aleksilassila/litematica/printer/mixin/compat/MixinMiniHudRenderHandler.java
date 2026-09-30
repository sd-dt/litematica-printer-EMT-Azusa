package me.aleksilassila.litematica.printer.mixin.compat;

import java.util.List;
import me.aleksilassila.litematica.printer.utils.PrinterHudStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 往 MiniHUD 的 HUD 里加一行「工作状态」（打印机开关 + 当前模式）。
 * <p>
 * 注入点选的是 MiniHUD 私有的 {@code RenderHandler#updateLines()} 末尾：
 * MiniHUD 每次刷新 HUD（约 50ms 一次）都会先清空并重建 {@code lines}，在最后追加就不会被清掉，
 * 于是这一行永远是 HUD 的**最后一行**（即最下面），并且完全由 MiniHUD 自己渲染
 * —— 字号、颜色、背景、阴影、对齐、位置全部跟随 MiniHUD 的设置。
 * <p>
 * 用 {@code targets = "..."} 字符串形式而不是直接引用类，配合 {@link MiniHudMixinPlugin}，
 * 没装 MiniHUD 时这个类不会被加载、也不会报错。
 */
@Mixin(targets = "fi.dy.masa.minihud.event.RenderHandler", remap = false)
public class MixinMiniHudRenderHandler {
   @Shadow
   @Final
   private List<String> lines;

   @Inject(method = "updateLines", at = @At("TAIL"), remap = false)
   private void litematica_printer$appendWorkStatus(CallbackInfo ci) {
      String line = PrinterHudStatus.line();
      if (line != null && !line.isEmpty()) {
         this.lines.add(line);
      }
   }
}
