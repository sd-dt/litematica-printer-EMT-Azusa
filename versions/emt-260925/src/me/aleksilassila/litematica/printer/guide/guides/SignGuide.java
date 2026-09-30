package me.aleksilassila.litematica.printer.guide.guides;

import java.util.List;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.core.Direction.Axis;

public class SignGuide extends Guide {
   public SignGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, HorizontalDirectionalBlock.FACING).orElse(null);
      if (this.requiredBlock instanceof StandingSignBlock) {
         int rotation = getProperty(this.requiredState, StandingSignBlock.ROTATION).orElseThrow();
         // 低头看：让原版选中「站立」变体（平视时旁边有墙就会被放成墙牌）
         return Result.success(new Action().setSides(Direction.DOWN).setLookRotation(rotation, 90.0F).setRequiresSupport());
      } else if (this.requiredBlock instanceof WallSignBlock && facing != null) {
         return Result.success(new Action().setSides(facing.getOpposite()).setLookDirection(facing.getOpposite()).setRequiresSupport());
      } else if (this.requiredBlock instanceof WallHangingSignBlock && facing != null) {
         List<Direction> sides = facing.getAxis() == Direction.Axis.X
            ? List.of(Direction.NORTH, Direction.SOUTH)
            : List.of(Direction.EAST, Direction.WEST);
         return Result.success(new Action().setSides(sides.toArray(new Direction[0])).setLookDirection(facing.getOpposite()).setRequiresSupport());
      } else if (this.requiredBlock instanceof CeilingHangingSignBlock) {
         int rotation = getProperty(this.requiredState, CeilingHangingSignBlock.ROTATION).orElse(0);
         boolean attached = getProperty(this.requiredState, BlockStateProperties.ATTACHED).orElse(false);
         // 抬头看：让原版选中「天花板悬挂」变体——这正是"挂在告示牌下面的浮空牌"。
         // 平视时原版会先拿 WallHangingSignBlock 的候选（旁边有实心方块或同轴悬挂牌就能存活），
         // 于是放出来的是墙挂变体，与投影不符 → 被"破坏错误方块"拆掉 → 永远打不出来。
         return Result.success(
            new Action().setShift(attached).setSides(Direction.UP).setLookRotation(rotation, -90.0F).setRequiresSupport()
         );
      } else {
         return Result.SKIP;
      }
   }

   @Override
   protected Result onBuildActionWrongBlock(BlockMatchResult state) {
      if (Configs.Print.BREAK_WRONG_BLOCK.getBooleanValue() && BreakUtils.canBreakBlock(this.blockPos)) {
         boolean isLegitimateSign = this.currentBlock instanceof StandingSignBlock
            || this.currentBlock instanceof WallSignBlock
            || this.currentBlock instanceof WallHangingSignBlock
            || this.currentBlock instanceof CeilingHangingSignBlock;
         // 世界里是告示牌、但不是投影要求的那一种（站立牌 / 墙上牌 / 悬挂牌互换、木种不同等）时，
         // 只要允许"破坏错误状态方块"就先拆掉：下一轮扫描才会按投影重新放置正确的牌子。
         // 否则这里直接 SKIP（且不交给后面的 guide），该格位会永远停在错误的那一种牌子上，
         // 表现就是"告示牌打不出来"。
         boolean wrongSignKind = isLegitimateSign
            && this.currentBlock != this.requiredBlock
            && Configs.Print.BREAK_WRONG_STATE_BLOCK.getBooleanValue()
            && BreakUtils.breakRestriction(this.level, this.blockPos, this.currentState);
         if (!isLegitimateSign || wrongSignKind) {
            BreakUtils.INSTANCE.add(this.context);
         }
      }

      return Result.SKIP;
   }
}
