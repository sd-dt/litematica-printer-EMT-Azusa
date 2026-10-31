package me.aleksilassila.litematica.printer.mixin.printer.mc;

import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.printer.PrintTaskController;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin({Connection.class})
public class MixinConnection {
   @Inject(
      method = {"genericsFtw"},
      at = {@At("HEAD")},
      require = 1
   )
   private static void hookGenericsFtw(Packet<?> packet, PacketListener listener, CallbackInfo ci) {
      if (ConfigUtils.isPrinterEnable()) {
         // 延迟检测用：每收到一个服务器回包就把"未回包计数"清零（MixinGui 的 LAG_CHECK 读它）。
         // 2026-10-24 之前这里还统计所有出站包喂给发包限流器，限流器已按用户要求整体移除。
         ClientPlayerTickManager.setPacketTick(0);
      }
   }

   @Inject(
      method = {"disconnect*"},
      at = {@At("HEAD")}
   )
   public void disconnect(Component ignored, CallbackInfo ci) {
      InventoryUtils.clearAutomatedQuickShulkerScreenProtection();
      PrintTaskController.INSTANCE.reset();
      ConfigUtils.resetAutoEnableSession();
      if (Configs.Core.AUTO_DISABLE_PRINTER.getBooleanValue() && Configs.Core.WORK_SWITCH.getBooleanValue()) {
         Configs.Core.WORK_SWITCH.setBooleanValue(false);
      }
   }
}
