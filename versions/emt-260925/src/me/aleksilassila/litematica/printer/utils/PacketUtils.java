package me.aleksilassila.litematica.printer.utils;

import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot;

public class PacketUtils {
   private static final Minecraft client = Minecraft.getInstance();

   public static void sendPacket(Packet<?> packet) {
      ClientPacketListener connection = client.getConnection();
      if (connection != null) {
         connection.send(packet);
         // 计数已移到 MixinConnection.genericsFtw（所有出站包的必经之路）：
         // 客户端自身的移动/输入包服务端也要数，只数打印机发的包会让学到的上限偏松、
         // 学完还是被踢。这里不再重复计。
      }
   }

   public static void sendPacket(MultiPlayerGameModeExtension.PredictiveAction packetCreator) {
      if (client.level instanceof PacketUtils.SequenceExtension sequenceExtension) {
         int currentSequence = sequenceExtension.litematica_printer3$getSequence();
         Packet<ServerGamePacketListener> packet = packetCreator.predict(currentSequence);
         sendPacket(packet);
      }
   }

   public static void sendLookPacket(LocalPlayer playerEntity, float lookYaw, float lookPitch) {
      playerEntity.connection.send(new Rot(lookYaw, lookPitch, playerEntity.onGround(), playerEntity.horizontalCollision));
      // 同上：包计数在 MixinConnection.genericsFtw 统一做，视线包同样会经过那里。
   }

   public static void sendLookPacket(LocalPlayer playerEntity, PlayerLook playerLook) {
      sendLookPacket(playerEntity, playerLook.yaw(), playerLook.pitch());
   }

   public interface SequenceExtension {
      default int litematica_printer3$getSequence() {
         return 0;
      }
   }
}
