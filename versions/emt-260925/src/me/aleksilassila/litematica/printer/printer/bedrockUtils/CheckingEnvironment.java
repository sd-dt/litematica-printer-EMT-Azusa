package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import java.util.ArrayList;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.client.multiplayer.ClientLevel;

/**
 * 破基岩子系统的环境检查（移植自三改版 bedrockUtils.CheckingEnvironment）。
 * <p>
 * 唯一改动：三改版用 {@code PlacementGuide.Action.isReplaceable(state)}，EMT 里对应的是
 * {@link BlockUtils#isReplaceable}。其余判定（红石火把落点、粘液块落点、活塞两格空间）逐行保留。
 */
public final class CheckingEnvironment {
   private CheckingEnvironment() {
   }

   /**
    * 找基岩旁边可以插红石火把的"平整"位置：要求该位置能贴住下方的支撑面，
    * 且下方是空气或已经是要放的火把（避免把别的方块当成支撑）。
    */
   public static BlockPos findNearbyFlatBlockToPlaceRedstoneTorch(ClientLevel world, BlockPos blockPos) {
      if ((
            !Block.canSupportCenter(world, blockPos.east(), Direction.UP)
               || !BlockUtils.isReplaceable(world.getBlockState(blockPos.east().above()))
         )
         && (
            !world.getBlockState(blockPos.east().above()).is(Blocks.REDSTONE_TORCH)
               || world.getBlockState(blockPos.east()).isAir()
         )) {
         if ((
               !Block.canSupportCenter(world, blockPos.west(), Direction.UP)
                  || !BlockUtils.isReplaceable(world.getBlockState(blockPos.west().above()))
            )
            && (
               !world.getBlockState(blockPos.west().above()).is(Blocks.REDSTONE_TORCH)
                  || world.getBlockState(blockPos.west()).isAir()
            )) {
            if ((
                  !Block.canSupportCenter(world, blockPos.north(), Direction.UP)
                     || !BlockUtils.isReplaceable(world.getBlockState(blockPos.north().above()))
               )
               && (
                  !world.getBlockState(blockPos.north().above()).is(Blocks.REDSTONE_TORCH)
                     || world.getBlockState(blockPos.north()).isAir()
               )) {
               return (
                        !Block.canSupportCenter(world, blockPos.south(), Direction.UP)
                           || !BlockUtils.isReplaceable(world.getBlockState(blockPos.south().above()))
                     )
                     && (
                        !world.getBlockState(blockPos.south().above()).is(Blocks.REDSTONE_TORCH)
                           || world.getBlockState(blockPos.south()).isAir()
                     )
                  ? null
                  : blockPos.south();
            } else {
               return blockPos.north();
            }
         } else {
            return blockPos.west();
         }
      } else {
         return blockPos.east();
      }
   }

   /** 找可以放粘液块的位置（基岩四周没有插火把的地方时的兜底方案） */
   public static BlockPos findPossibleSlimeBlockPos(ClientLevel world, BlockPos blockPos) {
      if (BlockUtils.isReplaceable(world.getBlockState(blockPos.east().above()))
         && BlockUtils.isReplaceable(world.getBlockState(blockPos.east().above()))) {
         return blockPos.east();
      } else if (BlockUtils.isReplaceable(world.getBlockState(blockPos.west().above()))
         && BlockUtils.isReplaceable(world.getBlockState(blockPos.west().above()))) {
         return blockPos.west();
      } else if (BlockUtils.isReplaceable(world.getBlockState(blockPos.south().above()))
         && BlockUtils.isReplaceable(world.getBlockState(blockPos.south().above()))) {
         return blockPos.south();
      } else {
         return BlockUtils.isReplaceable(world.getBlockState(blockPos.north().above()))
               && BlockUtils.isReplaceable(world.getBlockState(blockPos.north().above()))
            ? blockPos.north()
            : null;
      }
   }

   /** 基岩下方两格是否够放活塞（不够就先把挡路的方块挖掉） */
   public static boolean has2BlocksOfPlaceToPlacePiston(ClientLevel world, BlockPos blockPos) {
      if (world.getBlockState(blockPos.above()).getDestroySpeed(world, blockPos.above()) == 0.0F) {
         BlockBreaker.breakBlock(world, blockPos.above());
      }

      return BlockUtils.isReplaceable(world.getBlockState(blockPos.above()))
         && BlockUtils.isReplaceable(world.getBlockState(blockPos.above().above()));
   }

   /** 在活塞周围（以及上下两层）找已经存在的红石火把，用于判断电路是否已通电 */
   public static ArrayList<BlockPos> findNearbyRedstoneTorch(ClientLevel world, BlockPos pistonBlockPos) {
      ArrayList<BlockPos> list = new ArrayList<>();
      if (world.getBlockState(pistonBlockPos.east()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.east());
      }

      if (world.getBlockState(pistonBlockPos.west()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.west());
      }

      if (world.getBlockState(pistonBlockPos.south()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.south());
      }

      if (world.getBlockState(pistonBlockPos.north()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.north());
      }

      pistonBlockPos = pistonBlockPos.above();
      if (world.getBlockState(pistonBlockPos.east()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.east());
      }

      if (world.getBlockState(pistonBlockPos.west()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.west());
      }

      if (world.getBlockState(pistonBlockPos.south()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.south());
      }

      if (world.getBlockState(pistonBlockPos.north()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.north());
      }

      pistonBlockPos = pistonBlockPos.below(2);
      if (world.getBlockState(pistonBlockPos.east()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.east());
      }

      if (world.getBlockState(pistonBlockPos.west()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.west());
      }

      if (world.getBlockState(pistonBlockPos.south()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.south());
      }

      if (world.getBlockState(pistonBlockPos.north()).is(Blocks.REDSTONE_TORCH)) {
         list.add(pistonBlockPos.north());
      }

      return list;
   }
}
