package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.PlacementHandler;
import java.util.ArrayList;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 破基岩流程控制器（移植自三改版 bedrockUtils.BreakingFlowController，状态机逐行保留）。
 * <p>
 * 与三改版的三处对接差异：
 * <ul>
 *   <li>{@code Printer.bedrockModeTarget(state)} → {@link BedrockTargets#isTarget}</li>
 *   <li>{@code Printer.bedrockModeRange()} → {@link BedrockTargets#range()}（复用打印机工作半径）</li>
 *   <li>{@code ZxyUtils.bedrockCanInteracted(pos, r)} → {@link PlayerUtils#isWithinWorkInteractedEuclideanRange}
 *       （两者判据相同：眼睛到格位中心的距离平方 &lt; r²）；{@code ZxyUtils.getRage()} → {@link BedrockTargets#range()}</li>
 * </ul>
 * {@code LitematicaMixinMod.EASY_MODE} 对应 EMT 的「精准放置」{@link Configs.Print#EASY_PLACE_PROTOCOL}。
 */
public final class BreakingFlowController {
   public static ArrayList<TargetBlock> cachedTargetBlockList = new ArrayList<>();
   public static ArrayList<BlockPos> poslist = new ArrayList<>();

   private BreakingFlowController() {
   }

   /**
    * 登记一个基岩目标。三改版原逻辑：
    * 同时在处理的机器不超过 5 台；材料不够就只提示不动手；重复目标去重；
    * 目标上方两格是空气才真正开机器，否则把挡路的活塞/粘液块记进清理清单。
    */
   public static void addBlockPosToList(BlockPos pos) {
      if (cachedTargetBlockList.size() <= 5) {
         ClientLevel world = Minecraft.getInstance().level;
         Minecraft minecraftClient = Minecraft.getInstance();
         String haveEnoughItems = InventoryManager.warningMessage();
         if (haveEnoughItems != null) {
            Messager.actionBar(haveEnoughItems);
         } else {
            for (TargetBlock block : cachedTargetBlockList) {
               if (pos.equals(block.getBlockPos()) || pos.equals(block.getnyk()) || pos.equals(block.geths())) {
                  return;
               }
            }

            if (minecraftClient.level.getBlockState(pos.above()).isAir()
               && minecraftClient.level.getBlockState(pos.above().above()).isAir()) {
               cachedTargetBlockList.add(new TargetBlock(pos, world));
            } else {
               if (!BedrockTargets.isTarget(minecraftClient.level.getBlockState(pos.above()))) {
                  addPosList(pos.above());
               }

               if (!BedrockTargets.isTarget(minecraftClient.level.getBlockState(pos.above().above()))) {
                  addPosList(pos.above().above());
               }
            }
         }
      }
   }

   /** 记入"待清理格位"（活塞、火把、粘液块等机器残骸），去重 */
   public static void addPosList(BlockPos pos) {
      if (poslist.stream().noneMatch(pos1 -> pos1.equals(pos))) {
         poslist.add(pos);
      }
   }

   /** 清理机器残骸：能交互就拆，离得太远（2 倍半径）就放弃 */
   static void deleteBlock() {
      for (int i = 0; i < poslist.size(); i++) {
         BlockPos blockPos = poslist.get(i);
         if (Minecraft.getInstance().level.getBlockState(blockPos).isAir()
            && PlayerUtils.isWithinWorkInteractedEuclideanRange(blockPos, BedrockTargets.range())) {
            InventoryManager.switchToItem(Items.DIAMOND_PICKAXE);
            Minecraft.getInstance()
               .gameMode
               .useItemOn(
                  Minecraft.getInstance().player,
                  InteractionHand.MAIN_HAND,
                  new BlockHitResult(Vec3.atCenterOf(blockPos), Direction.UP, poslist.get(i), false)
               );
            if (Minecraft.getInstance().level.getBlockState(blockPos).isAir()) {
               poslist.remove(i);
               i--;
               continue;
            }
         }

         if (!PlayerUtils.isWithinWorkInteractedEuclideanRange(blockPos, BedrockTargets.range() * 2)) {
            poslist.remove(i);
            i--;
         } else if (PlayerUtils.isWithinWorkInteractedEuclideanRange(blockPos, BedrockTargets.range())
            && !Minecraft.getInstance().level.getBlockState(blockPos).isAir()) {
            InventoryManager.switchToItem(Items.DIAMOND_PICKAXE);
            BlockBreaker.waJue(blockPos);
         }
      }
   }

   /** 每游戏刻驱动：先清残骸，再推进所有目标机器 */
   public static void tick() {
      deleteBlock();
      if (InventoryManager.warningMessage() == null) {
         Minecraft minecraftClient = Minecraft.getInstance();

         for (int i = 0; i < cachedTargetBlockList.size(); i++) {
            TargetBlock selectedBlock = cachedTargetBlockList.get(i);
            if (!PlayerUtils.isWithinWorkInteractedEuclideanRange(selectedBlock.getBlockPos(), BedrockTargets.range() - 1.5)) {
               cachedTargetBlockList.remove(i);
            } else {
               if (selectedBlock.getWorld() != Minecraft.getInstance().level) {
                  cachedTargetBlockList = new ArrayList<>();
                  break;
               }

               ItemStack mainHandStack = Minecraft.getInstance().player.getMainHandItem();
               cachedTargetBlockList.stream()
                  .filter(targetBlock -> targetBlock.getStatus() == TargetBlock.Status.EXTENDED)
                  .forEach(TargetBlock::tick);
               TargetBlock.Status status = cachedTargetBlockList.get(i).tick();
               if (status != TargetBlock.Status.RETRACTING && (status == TargetBlock.Status.FAILED || status == TargetBlock.Status.RETRACTED)) {
                  for (BlockPos temppo : cachedTargetBlockList.get(i).temppos) {
                     if (!minecraftClient.level.getBlockState(temppo).isAir()) {
                        addPosList(temppo);
                     }
                  }

                  cachedTargetBlockList.remove(i);
               }
            }
         }

         if (cachedTargetBlockList.stream().anyMatch(targetBlock -> targetBlock.getStatus() == TargetBlock.Status.EXTENDED)) {
            InventoryManager.switchToItem(Items.DIAMOND_PICKAXE);
            TargetBlock.switchPickaxe = true;
         }
      }
   }

   /** 工作模式：开了精准放置且投影协议是 V2（地毯附加协议）时走 CARPET_EXTRA，否则走原版 */
   public static BreakingFlowController.WorkingMode getWorkingMode() {
      return Configs.Print.EASY_PLACE_PROTOCOL.getBooleanValue() && PlacementHandler.getEffectiveProtocolVersion() == EasyPlaceProtocol.V2
         ? BreakingFlowController.WorkingMode.CARPET_EXTRA
         : BreakingFlowController.WorkingMode.VANILLA;
   }

   private static boolean shouldAddNewTargetBlock(BlockPos pos) {
      for (int i = 0; i < cachedTargetBlockList.size(); i++) {
         if (cachedTargetBlockList.get(i).getBlockPos().distManhattan(pos) == 0) {
            return false;
         }
      }

      return true;
   }

   /**
    * 该格位上方（同一条柱子）是否已经有一台正在工作的破基岩机。
    * <p>
    * 对应三改版驱动循环里的"往下扫到更低 Y 就重置迭代器"提前退出：破基岩必须自上而下逐层啃，
    * 上一层还没清掉之前，下层不该再开一台机器（否则机器会互相顶住、也浪费材料）。
    */
   public static boolean hasActiveMachineAbove(BlockPos pos) {
      for (int i = 0; i < cachedTargetBlockList.size(); i++) {
         BlockPos machinePos = cachedTargetBlockList.get(i).getBlockPos();
         if (machinePos.getX() == pos.getX()
            && machinePos.getZ() == pos.getZ()
            && machinePos.getY() > pos.getY()) {
            return true;
         }
      }

      return false;
   }

   public static void switchOnOff() {
   }

   /** 关闭破基岩模式时清空全部目标机器与清理队列（等价于旧实现 setWorking(false) + clearTask()） */
   public static void clearAll() {
      cachedTargetBlockList = new ArrayList<>();
      poslist.clear();
      TargetBlock.switchPickaxe = false;
   }

   /** 工作模式（三改版原样保留；MANUALLY 目前未被使用） */
   public enum WorkingMode {
      CARPET_EXTRA,
      VANILLA,
      MANUALLY;
   }
}
