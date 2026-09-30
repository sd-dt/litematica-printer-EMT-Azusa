package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import java.util.ArrayList;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 破基岩子系统的"单个基岩目标"状态机（移植自三改版 bedrockUtils.TargetBlock，逻辑逐行保留）。
 * <p>
 * 原理：在基岩正下方放一个朝上的活塞 + 红石火把通电，让活塞头把基岩"顶碎"（原版特性），
 * 再瞬间挖掉活塞/火把复位，循环推进。判定入口只有一处改动：
 * 三改版的 {@code Printer.bedrockModeTarget(state)} → {@link BedrockTargets#isTarget}。
 */
public class TargetBlock {
   public static boolean switchPickaxe = false;
   private BlockPos blockPos;
   private BlockPos redstoneTorchBlockPos;
   private BlockPos pistonBlockPos;
   private ClientLevel world;
   private TargetBlock.Status status;
   private BlockPos slimeBlockPos;
   private int tickTimes;
   private boolean hasTried;
   private int stuckTicksCounter;
   public boolean pistonIsBreak = false;
   public ArrayList<BlockPos> temppos = new ArrayList<>();

   public TargetBlock(BlockPos pos, ClientLevel world) {
      this.hasTried = false;
      this.stuckTicksCounter = 0;
      this.status = TargetBlock.Status.UNINITIALIZED;
      this.blockPos = pos;
      this.world = world;
      this.pistonBlockPos = pos.above();
      this.redstoneTorchBlockPos = CheckingEnvironment.findNearbyFlatBlockToPlaceRedstoneTorch(this.world, this.blockPos);
      if (this.redstoneTorchBlockPos == null) {
         this.slimeBlockPos = CheckingEnvironment.findPossibleSlimeBlockPos(world, pos);
         if (this.slimeBlockPos != null) {
            BlockPlacer.simpleBlockPlacement(this, this.slimeBlockPos, Blocks.SLIME_BLOCK);
            this.redstoneTorchBlockPos = this.slimeBlockPos.above();
         } else {
            this.status = TargetBlock.Status.FAILED;
         }
      }
   }

   public TargetBlock.Status tick() {
      this.tickTimes++;
      if (!this.pistonIsBreak) {
         this.updateStatus();
      }

      switch (this.status) {
         case FAILED:
            BreakingFlowController.addPosList(this.pistonBlockPos);
            BreakingFlowController.addPosList(this.pistonBlockPos.above());
            return TargetBlock.Status.FAILED;
         case UNINITIALIZED:
            InventoryManager.switchToItem(Blocks.PISTON);
            BlockPlacer.pistonPlacement(this.pistonBlockPos, Direction.UP);
            InventoryManager.switchToItem(Blocks.REDSTONE_TORCH);
            BlockPlacer.simpleBlockPlacement(this, this.redstoneTorchBlockPos, Blocks.REDSTONE_TORCH);
         case UNEXTENDED_WITH_POWER_SOURCE:
         case NEEDS_WAITING:
         default:
            break;
         case UNEXTENDED_WITHOUT_POWER_SOURCE:
            InventoryManager.switchToItem(Blocks.REDSTONE_TORCH);
            BlockPlacer.simpleBlockPlacement(this, this.redstoneTorchBlockPos, Blocks.REDSTONE_TORCH);
            break;
         case EXTENDED:
            Item item = Minecraft.getInstance().player.getMainHandItem().getItem();
            if ((item.equals(Items.NETHERITE_PICKAXE) || item.equals(Items.DIAMOND_PICKAXE)) && switchPickaxe) {
               for (BlockPos pos : CheckingEnvironment.findNearbyRedstoneTorch(this.world, this.pistonBlockPos)) {
                  BlockBreaker.breakBlock(this.world, pos);
               }

               BlockBreaker.breakBlock(this.world, this.pistonBlockPos);

               for (int i = 1; i < 6; i++) {
                  BreakingFlowController.addPosList(this.pistonBlockPos.above(i));
               }

               BlockPlacer.pistonPlacement(this.pistonBlockPos, Direction.DOWN);
               this.hasTried = true;
            }
            break;
         case RETRACTING:
            return TargetBlock.Status.RETRACTING;
         case RETRACTED:
            BreakingFlowController.addPosList(this.pistonBlockPos);
            BreakingFlowController.addPosList(this.pistonBlockPos.above());
            if (this.slimeBlockPos != null) {
               BreakingFlowController.addPosList(this.slimeBlockPos);
            }

            return TargetBlock.Status.RETRACTED;
         case STUCK:
            BreakingFlowController.addPosList(this.pistonBlockPos);
            BreakingFlowController.addPosList(this.pistonBlockPos.above());
      }

      return null;
   }

   public BlockPos getBlockPos() {
      return this.blockPos;
   }

   /** 红石火把位置（三改版原名 geths） */
   public BlockPos geths() {
      return this.redstoneTorchBlockPos;
   }

   /** 粘液块位置（三改版原名 getnyk） */
   public BlockPos getnyk() {
      return this.slimeBlockPos;
   }

   public ClientLevel getWorld() {
      return this.world;
   }

   public TargetBlock.Status getStatus() {
      return this.status;
   }

   private void updateStatus() {
      if (this.tickTimes > 40) {
         this.status = TargetBlock.Status.FAILED;
      } else {
         this.redstoneTorchBlockPos = CheckingEnvironment.findNearbyFlatBlockToPlaceRedstoneTorch(this.world, this.blockPos);
         if (this.redstoneTorchBlockPos == null) {
            this.slimeBlockPos = CheckingEnvironment.findPossibleSlimeBlockPos(this.world, this.blockPos);
            if (this.slimeBlockPos != null) {
               BlockPlacer.simpleBlockPlacement(this, this.slimeBlockPos, Blocks.SLIME_BLOCK);
               this.redstoneTorchBlockPos = this.slimeBlockPos.above();
            } else {
               this.status = TargetBlock.Status.FAILED;
               Messager.actionBar("bedrockminer.fail.place.redstonetorch");
            }
         } else if (!BedrockTargets.isTarget(this.world.getBlockState(this.blockPos))
            && this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)) {
            this.status = TargetBlock.Status.RETRACTED;
         } else if (this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)
            && (Boolean)this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.EXTENDED)) {
            this.status = TargetBlock.Status.EXTENDED;
         } else if (this.world.getBlockState(this.pistonBlockPos).is(Blocks.MOVING_PISTON)) {
            this.status = TargetBlock.Status.RETRACTING;
         } else if (this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)
            && !(Boolean)this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.EXTENDED)
            && CheckingEnvironment.findNearbyRedstoneTorch(this.world, this.pistonBlockPos).size() != 0
            && BedrockTargets.isTarget(this.world.getBlockState(this.blockPos))) {
            this.status = TargetBlock.Status.UNEXTENDED_WITH_POWER_SOURCE;
         } else if (this.hasTried
            && this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)
            && this.stuckTicksCounter < 15) {
            this.status = TargetBlock.Status.NEEDS_WAITING;
            this.stuckTicksCounter++;
         } else if (this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)
            && this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.FACING) == Direction.DOWN
            && !(Boolean)this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.EXTENDED)
            && CheckingEnvironment.findNearbyRedstoneTorch(this.world, this.pistonBlockPos).size() != 0
            && BedrockTargets.isTarget(this.world.getBlockState(this.blockPos))) {
            this.status = TargetBlock.Status.STUCK;
            this.hasTried = false;
            this.stuckTicksCounter = 0;
         } else if (this.world.getBlockState(this.pistonBlockPos).is(Blocks.PISTON)
            && !(Boolean)this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.EXTENDED)
            && this.world.getBlockState(this.pistonBlockPos).getValue(PistonBaseBlock.FACING) == Direction.UP
            && CheckingEnvironment.findNearbyRedstoneTorch(this.world, this.pistonBlockPos).size() == 0
            && BedrockTargets.isTarget(this.world.getBlockState(this.blockPos))) {
            this.status = TargetBlock.Status.UNEXTENDED_WITHOUT_POWER_SOURCE;
         } else if (CheckingEnvironment.has2BlocksOfPlaceToPlacePiston(this.world, this.blockPos)) {
            this.status = TargetBlock.Status.UNINITIALIZED;
         } else if (!CheckingEnvironment.has2BlocksOfPlaceToPlacePiston(this.world, this.blockPos)) {
            this.status = TargetBlock.Status.FAILED;
            Messager.actionBar("bedrockminer.fail.place.piston");
         } else {
            this.status = TargetBlock.Status.FAILED;
         }
      }
   }

   enum Status {
      FAILED,
      UNINITIALIZED,
      UNEXTENDED_WITH_POWER_SOURCE,
      UNEXTENDED_WITHOUT_POWER_SOURCE,
      EXTENDED,
      NEEDS_WAITING,
      RETRACTING,
      RETRACTED,
      STUCK;
   }
}
