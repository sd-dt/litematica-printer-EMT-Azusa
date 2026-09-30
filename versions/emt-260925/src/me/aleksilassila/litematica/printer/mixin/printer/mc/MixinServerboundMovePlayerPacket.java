package me.aleksilassila.litematica.printer.mixin.printer.mc;

import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.PlayerLook;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(
   value = {ServerboundMovePlayerPacket.class},
   priority = 1010
)
public class MixinServerboundMovePlayerPacket {
   @ModifyVariable(
      method = {"<init>(DDDFFZZZZ)V"},
      at = @At("HEAD"),
      ordinal = 0,
      argsOnly = true
   )
   private static float modifyLookYaw(float yaw) {
      PlayerLook playerLook = ActionManager.INSTANCE.look;
      return playerLook != null ? playerLook.yaw() : yaw;
   }

   @ModifyVariable(
      method = {"<init>(DDDFFZZZZ)V"},
      at = @At("HEAD"),
      ordinal = 1,
      argsOnly = true
   )
   private static float modifyLookPitch(float pitch) {
      PlayerLook playerLook = ActionManager.INSTANCE.look;
      return playerLook != null ? playerLook.pitch() : pitch;
   }
}
