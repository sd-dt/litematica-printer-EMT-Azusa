package me.aleksilassila.litematica.printer.handler.handlers;

import java.util.concurrent.atomic.AtomicReference;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.printer.bedrockUtils.BedrockTargets;
import me.aleksilassila.litematica.printer.printer.bedrockUtils.BreakingFlowController;
import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 破基岩模式（已整体替换为三改版的自研活塞破基岩子系统）。
 * <p>
 * 旧实现只是转发给外部前置模组 Fabric-Bedrock-Miner / Block-Miner（没装就不能用）；
 * 现在改为 {@link BreakingFlowController} 自成一体的状态机：
 * 基岩正下方放朝上的活塞 + 红石火把通电顶碎基岩，再瞬间挖掉活塞/火把复位，逐层推进。
 * <p>
 * 每个游戏刻：{@link #preprocess()} 驱动一次状态机（清残骸 + 推进所有目标机器），
 * 随后由基类的盒遍历逐格调用 {@link #executeIteration}，把机器残骸记进清理清单、
 * 把"下方不是同类目标"的基岩登记为新目标（等价于三改版 Printer.bedrockMode 的循环体）。
 * 选区限制由基类提供（非投影处理器要求 {@code LitematicaUtils.isWithinSelection1ModeRange}）。
 */
public class BedrockHandler extends ClientPlayerTickHandler {
   public BedrockHandler() {
      super("bedrock", PrintModeType.BEDROCK, Configs.Hotkeys.BEDROCK, null, true);
   }

   @Override
   protected int getTickInterval() {
      return Configs.Mine.BREAK_INTERVAL.getIntegerValue();
   }

   @Override
   protected int getMaxExecutions() {
      return Configs.Mine.BREAK_BLOCKS_PER_TICK.getIntegerValue();
   }

   @Override
   protected boolean canExecute() {
      if (this.player.isCreative()) {
         MessageUtils.setOverlayMessage(I18n.BEDROCK_CREATIVE_MODE.getName());
         return false;
      } else {
         // 自研破基岩不再要求安装 Fabric-Bedrock-Miner / Block-Miner，材料不足由状态机自己在动作栏提示
         return true;
      }
   }

   @Override
   protected void preprocess() {
      BreakingFlowController.tick();
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      BlockState currentState = this.level.getBlockState(blockPos);
      // 1) 机器残骸：活塞，或是还没进过 temppos 的粘液块 → 记进清理清单
      if ((currentState.is(Blocks.PISTON)
            || currentState.is(Blocks.SLIME_BLOCK)
               && BreakingFlowController.cachedTargetBlockList.stream()
                  .allMatch(targetBlock -> targetBlock.temppos.stream().noneMatch(tempPos -> tempPos.equals(blockPos))))
         && !BedrockTargets.isTarget(this.level.getBlockState(blockPos.below()))) {
         BreakingFlowController.addPosList(blockPos);
      } else if (currentState.is(Blocks.PISTON_HEAD)) {
         // 2) 活塞头：换成镐子空手右击一下，让伸出的活塞头复位（三改版原逻辑）
         InventoryUtils.switchToItems(this.player, new Item[]{Items.DIAMOND_PICKAXE});
         this.gameMode
            .useItemOn(
               this.player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(blockPos), Direction.UP, blockPos, false)
            );
      }

      // 3) 基岩目标：只登记"下方不是同类目标"、且同柱上方没有正在工作的机器的那一层，自上而下逐层啃
      if (BedrockTargets.isTarget(currentState)
         && !BedrockTargets.isTarget(this.level.getBlockState(blockPos.above()))
         && !BreakingFlowController.hasActiveMachineAbove(blockPos)) {
         BreakingFlowController.addBlockPosToList(blockPos);
      }

      this.setCooldown(blockPos, 5);
   }
}
