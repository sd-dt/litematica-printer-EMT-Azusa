package me.aleksilassila.litematica.printer.guide.guides;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.Guide;
import me.aleksilassila.litematica.printer.guide.Result;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.phys.Vec3;

public class StairGuide extends Guide {
   public StairGuide(SchematicBlockContext context) {
      super(context);
   }

   @Override
   protected Result onBuildActionMissingBlock(BlockMatchResult state) {
      Direction facing = (Direction)getProperty(this.requiredState, StairBlock.FACING).orElse(null);
      Half half = (Half)getProperty(this.requiredState, StairBlock.HALF).orElse(null);
      if (facing != null && half != null) {
         return Result.success(new Action().setSides(sidesFor(half)).setLookDirection(facing));
      } else {
         return Result.PASS;
      }
   }

   /**
    * 原版 {@code StairBlock.getStateForPlacement} 用 {@code hitY > 0.5} 严格比较来决定上下半区，
    * 命中点正好等于 0.5 时会被判成下半 —— 也就是侧面点击不能落在上下分界线上。
    * <p>
    * 这里的偏移在使用时还会再乘 0.5（见 {@code ActionManager.sendQueue}），所以：
    * 下半楼梯用 -0.5 → 侧面命中 1/4 高度；上半楼梯用 +0.5 → 侧面命中 3/4 高度；
    * 垂直支撑面（点下方/上方方块的顶面/底面）则必须是 0 偏移，不能带高度修饰。
    * <p>
    * 四个水平方向都作为候选：支撑合法性仍由 {@code Action.getValidSide} 检查，
    * 顺序由 {@code Action.setSides(Map)} 统一重排，避免"有可用支撑却跳过"。
    */
   private static Map<Direction, Vec3> sidesFor(Half half) {
      boolean top = half == Half.TOP;
      Vec3 horizontal = new Vec3(0.0, top ? 0.5 : -0.5, 0.0);
      Map<Direction, Vec3> sides = new HashMap<>();
      sides.put(top ? Direction.UP : Direction.DOWN, Vec3.ZERO);
      for (Direction side : Direction.Plane.HORIZONTAL) {
         sides.put(side, horizontal);
      }

      return sides;
   }

   /**
    * 楼梯的 SHAPE（拐角内外形）由邻居决定，邻居还在打印时形状自然对不上。
    * 只有 SHAPE 不同就跳过拆除，否则会陷入"拆了又放"的循环；方块类型、朝向、上下半区
    * 真的不同时仍交给原有修复流程。
    */
   @Override
   protected Result onBuildActionWrongState(BlockMatchResult state) {
      if (BlockStateUtils.statesEqualIgnoreProperties(this.requiredState, this.currentState, StairBlock.SHAPE)) {
         return Result.SKIP;
      } else {
         return Result.PASS;
      }
   }
}
