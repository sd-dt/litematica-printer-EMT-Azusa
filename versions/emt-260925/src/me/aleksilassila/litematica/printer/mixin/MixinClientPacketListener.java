package me.aleksilassila.litematica.printer.mixin;

import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils;
import me.aleksilassila.litematica.printer.printer.zxy.utils.ZxyUtils;
import me.aleksilassila.litematica.printer.utils.PacketSoundConfirmationTracker;
import me.aleksilassila.litematica.printer.utils.PacketUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({ClientPacketListener.class})
public abstract class MixinClientPacketListener {
   @Inject(
      method = {"handleOpenScreen"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"
      )},
      cancellable = true
   )
   private void suppressTaskAnvilScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
      if (packet.getType() == MenuType.ANVIL && !ActionManager.INSTANCE.consumeManualAnvilScreenAllowance()) {
         if (ActionManager.INSTANCE.consumeTaskAnvilScreenSuppression()) {
            PacketUtils.sendPacket(new ServerboundContainerClosePacket(packet.getContainerId()));
            ci.cancel();
         }
      }
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"handleBlockUpdate"}
   )
   private void confirmPacketSound(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
      PacketSoundConfirmationTracker.confirmServerBlockUpdate(packet.getPos(), packet.getBlockState());
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"handleChunkBlocksUpdate"}
   )
   private void confirmPacketSectionSound(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
      packet.runUpdates(PacketSoundConfirmationTracker::confirmServerBlockUpdate);
   }

   @Inject(
      at = {@At("TAIL")},
      method = {"handleContainerContent"}
   )
   public void onInventory(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
      if (InventoryUtils.isOpenHandler) {
         InventoryUtils.switchInv();
      }

      if (ZxyUtils.num == 1 || ZxyUtils.num == 3) {
         Minecraft minecraft = Minecraft.getInstance();
         if (minecraft.player != null
            && !minecraft.player.containerMenu.equals(minecraft.player.inventoryMenu)
            && packet.containerId() == minecraft.player.containerMenu.containerId) {
            ZxyUtils.syncInv();
         }
      }
   }
}
