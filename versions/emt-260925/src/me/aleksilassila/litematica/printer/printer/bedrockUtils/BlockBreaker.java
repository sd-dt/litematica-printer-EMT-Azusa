package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import me.aleksilassila.litematica.printer.utils.BreakUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 破基岩子系统的方块破坏（移植自三改版 bedrockUtils.BlockBreaker，逻辑原样保留）。
 * <p>
 * 与三改版一致：调用原版 MultiPlayerGameMode 的破坏入口，先切到手斧（钻石镐）再动手，
 * 因为破基岩流程要求"瞬间挖掉活塞"，工具不对就会卡住状态机。
 */
public final class BlockBreaker {
   private BlockBreaker() {
   }

   /** 从上往下破坏（面朝下），用于拆活塞/红石火把 */
   public static void breakBlock(ClientLevel world, BlockPos pos) {
      InventoryManager.switchToItem(net.minecraft.world.item.Items.DIAMOND_PICKAXE);
      Minecraft.getInstance().gameMode.startDestroyBlock(pos, Direction.DOWN);
   }

   /** 从下往上破坏（面朝上） */
   public static void upBreakBlock(ClientLevel world, BlockPos pos) {
      Minecraft.getInstance().gameMode.continueDestroyBlock(pos, Direction.UP);
   }

   /**
    * "扒"一下方块（移植自三改版 PlayerAction.waJue）：
    * continueDestroyBlock + stopDestroyBlock，等价于玩家对着方块连点一下，用来触发原版的瞬间破坏。
    */
   public static boolean waJue(BlockPos pos) {
      Minecraft client = Minecraft.getInstance();
      ClientLevel level = client.level;
      BlockState currentState = level.getBlockState(pos);
      Block block = currentState.getBlock();
      if (BreakUtils.canBreakBlock(pos)) {
         client.gameMode.continueDestroyBlock(pos, Direction.DOWN);
         client.gameMode.stopDestroyBlock();
         return level.getBlockState(pos).is(block);
      } else {
         return false;
      }
   }
}
