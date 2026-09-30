package me.aleksilassila.litematica.printer.mixin.printer.mc;

import com.mojang.authlib.GameProfile;
import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.Optional;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.printer.BlockPosCooldownManager;
import me.aleksilassila.litematica.printer.printer.RenderOnlyBlockCache;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.printer.zxy.utils.ZxyUtils;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.PacketRateLimiter;
import me.aleksilassila.litematica.printer.utils.CloudStoreUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.EatUtils;
import me.aleksilassila.litematica.printer.utils.HandRestockShulkerCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({LocalPlayer.class})
public class MixinLocalPlayer extends AbstractClientPlayer {
   @Final
   @Shadow
   public ClientPacketListener connection;
   @Final
   @Shadow
   protected Minecraft minecraft;

   public MixinLocalPlayer(ClientLevel world, GameProfile profile) {
      super(world, profile);
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"resetPos"}
   )
   public void init(CallbackInfo ci) {
      ConfigUtils.startAutoEnableSession();
   }

   @Inject(
      at = {@At("HEAD")},
      method = {"tick"}
   )
   public void tick(CallbackInfo ci) {
      ClientPlayerTickManager.updateTickHandlerTime();
      // 限流器采样/自适应必须每 tick 都跑（破坏队列非空时处理器循环会被跳过）
      PacketRateLimiter.tick(ClientPlayerTickManager.getPacketTick());
      ConfigUtils.tickAutoEnable();
      RenderOnlyBlockCache.tickPendingReload();
      BlockPosCooldownManager.INSTANCE.tick();
      EatUtils.tick(this.minecraft);
      InventoryUtils.tick();
      ZxyUtils.tick();
      CloudStoreUtils.tickArrivalCheck(this.minecraft.player);
      BreakUtils.INSTANCE.preprocess();
      if (BreakUtils.INSTANCE.isNeedHandle() && !EatUtils.isBusy()) {
         BreakUtils.INSTANCE.onTick();
      } else {
         ClientPlayerTickManager.tick();
      }
   }

   @Inject(
      method = {"openTextEdit"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void openTextEdit(SignBlockEntity sign, boolean front, CallbackInfo ci) {
      this.openEditSignScreen(sign, front, ci);
   }

   public void openEditSignScreen(SignBlockEntity sign, boolean front, CallbackInfo ci) {
      this.getTargetSignEntity(sign).ifPresent(signBlockEntity -> {
         String line1 = signBlockEntity.getText(front).getMessage(0, false).getString();
         String line2 = signBlockEntity.getText(front).getMessage(1, false).getString();
         String line3 = signBlockEntity.getText(front).getMessage(2, false).getString();
         String line4 = signBlockEntity.getText(front).getMessage(3, false).getString();
         ServerboundSignUpdatePacket packet = new ServerboundSignUpdatePacket(sign.getBlockPos(), front, line1, line2, line3, line4);
         this.connection.send(packet);
         ci.cancel();
      });
   }

   @Unique
   private Optional<SignBlockEntity> getTargetSignEntity(SignBlockEntity sign) {
      WorldSchematic worldSchematic = SchematicWorldHandler.getSchematicWorld();
      if (sign.getLevel() != null && worldSchematic != null) {
         return worldSchematic.getBlockEntity(sign.getBlockPos()) instanceof SignBlockEntity targetSignEntity
            ? Optional.of(targetSignEntity)
            : Optional.empty();
      } else {
         return Optional.empty();
      }
   }

   @Inject(
      method = {"drop"},
      at = {@At("HEAD")}
   )
   private void litematica_printer$markLocalDrop(boolean dropAll, CallbackInfoReturnable<Boolean> cir) {
      HandRestockShulkerCompat.markLocalDrop();
   }
}
