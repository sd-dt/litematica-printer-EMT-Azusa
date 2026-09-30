package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.utils.PacketUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ItemLike;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.player.LocalPlayer;

/**
 * 破基岩子系统的放置动作（移植自三改版 bedrockUtils.BlockPlacer）。
 * <p>
 * 三改版通过 {@code PlayerAction.interactBlock} → {@code client.gameMode.useItemOn} 完成交互；
 * EMT 里同样的接缝是 {@link MultiPlayerGameModeExtension#litematica_printer$useItemOn}（本地预测分支），
 * 语义一致。放置统一走<b>副手</b>（InteractionHand.OFF_HAND），主手留给镐子，这与
 * {@link InventoryManager#switchToItem} 的"非镐子物品换到副手"设计配套。
 */
public final class BlockPlacer {
   private static float yaw;
   private static float pitch;

   private BlockPlacer() {
   }

   /** 在指定格位凭空放一个方块，并把该格位记进 TargetBlock.temppos（收尾时要清理） */
   public static void simpleBlockPlacement(TargetBlock tar, BlockPos pos, ItemLike item) {
      Minecraft minecraft = Minecraft.getInstance();
      InventoryManager.switchToItem(item);
      tar.temppos.add(pos);
      BlockHitResult hitResult = new BlockHitResult(
         new Vec3(pos.getX(), pos.getY(), pos.getZ()), Direction.UP, pos, false
      );
      placeBlockWithoutInteractingBlock(minecraft, hitResult);
   }

   private static void resetLook() {
      sendLookPacket(yaw, pitch);
   }

   private static void sendLookPacket(float lookYaw, float lookPitch) {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         PacketUtils.sendLookPacket(player, lookYaw, lookPitch);
      }
   }

   /**
    * 放活塞。VANILLA 模式先把视角包发出去（原版放置要靠低头才能立起活塞）；
    * CARPET_EXTRA 模式把命中点整体平移，走地毯附加协议。
    */
   public static void pistonPlacement(BlockPos pos, Direction direction) {
      Minecraft minecraftClient = Minecraft.getInstance();
      double x = pos.getX();
      switch (BreakingFlowController.getWorkingMode()) {
         case CARPET_EXTRA:
            x = x + 2.0 + direction.get3DDataValue() * 2;
            break;
         case VANILLA:
            Player player = minecraftClient.player;
            float lookPitch = switch (direction) {
               case UP -> 90.0F;
               case DOWN -> -90.0F;
               default -> 90.0F;
            };
            yaw = player.getYRot();
            BlockPlacer.pitch = player.getXRot();
            sendLookPacket(player.getViewYRot(1.0F), lookPitch);
      }

      Vec3 vec3d = new Vec3(x, pos.getY(), pos.getZ());
      InventoryManager.switchToItem(Items.PISTON);
      BlockHitResult hitResult = new BlockHitResult(vec3d, Direction.UP, pos, false);
      placeBlockWithoutInteractingBlock(minecraftClient, hitResult);
      resetLook();
   }

   /** 用副手物品做一次放置交互；随后让客户端自己也 useOn 一次，保持本地预测与世界一致 */
   private static void placeBlockWithoutInteractingBlock(Minecraft minecraftClient, BlockHitResult hitResult) {
      LocalPlayer player = minecraftClient.player;
      if (player == null) {
         return;
      }

      ItemStack itemStack = player.getItemInHand(InteractionHand.OFF_HAND);
      if (minecraftClient.gameMode instanceof MultiPlayerGameModeExtension gameModeExtension) {
         gameModeExtension.litematica_printer$useItemOn(true, InteractionHand.OFF_HAND, hitResult);
      }

      if (!itemStack.isEmpty() && !player.getCooldowns().isOnCooldown(itemStack)) {
         UseOnContext itemUsageContext = new UseOnContext(player, InteractionHand.OFF_HAND, hitResult);
         itemStack.useOn(itemUsageContext);
      }
   }
}
