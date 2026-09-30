package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

public class ChestGuide extends Guide {
   private static final Map<String, Long> DOUBLE_CHEST_PENDING = new HashMap<>();
   private static final long PARTNER_CONFIRM_TIMEOUT_MS = 1000L;

   public ChestGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, ChestBlock.FACING).orElseThrow();
      Direction facingOpposite = facing.getOpposite();
      ChestType chestType = getProperty(this.requiredState, BlockStateProperties.CHEST_TYPE).orElse(ChestType.SINGLE);
      Map<Direction, Vec3> noChestSides = new HashMap<>();

      for (Direction side : Direction.values()) {
         if (!(this.level.getBlockState(this.blockPos.relative(side)).getBlock() instanceof ChestBlock)) {
            noChestSides.put(side, Vec3.ZERO);
         }
      }

      if (chestType == ChestType.SINGLE) {
         return Result.success(new Action().setSides(noChestSides).setLookDirection(facingOpposite).setWaitForHorizontalLook(false).setShift(true));
      } else {
         Direction partnerDir = chestType == ChestType.LEFT ? facing.getClockWise() : facing.getCounterClockWise();
         BlockPos partnerPos = this.blockPos.relative(partnerDir);
         if (this.level.getBlockState(partnerPos).getBlock() instanceof ChestBlock) {
            this.clearPending(this.blockPos);
            this.clearPending(partnerPos);
            return Result.success(new Action().setSides(partnerDir).setLookDirection(facingOpposite).setWaitForHorizontalLook(false).setShift(true));
         } else {
            long now = System.currentTimeMillis();
            Long firstTry = DOUBLE_CHEST_PENDING.get(this.pendingKey(this.blockPos));
            if (firstTry != null && now - firstTry < 1000L) {
               return Result.SKIP;
            } else {
               DOUBLE_CHEST_PENDING.put(this.pendingKey(this.blockPos), now);
               DOUBLE_CHEST_PENDING.put(this.pendingKey(partnerPos), now);
               return Result.success(new Action().setSides(noChestSides).setLookDirection(facingOpposite).setWaitForHorizontalLook(false).setShift(true));
            }
         }
      }
   }

   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      return Result.SKIP;
   }

   private String pendingKey(BlockPos pos) {
      return this.level.dimension().identifier() + "|" + pos.asLong();
   }

   private void clearPending(BlockPos pos) {
      DOUBLE_CHEST_PENDING.remove(this.pendingKey(pos));
   }
}
