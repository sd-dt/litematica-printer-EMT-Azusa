package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.selection.Box;
import fi.dy.masa.litematica.selection.SelectionMode;
import fi.dy.masa.litematica.util.EasyPlaceProtocol;
import fi.dy.masa.litematica.util.EasyPlaceUtils;
import fi.dy.masa.litematica.util.PlacementHandler;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.printer.SchematicStateCache;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public class LitematicaUtils {
   public static final Minecraft client = Minecraft.getInstance();
   public static final LitematicaUtils INSTANCE = new LitematicaUtils();

   private LitematicaUtils() {
   }

   public static boolean isPositionWithinRange(BlockPos pos) {
      return DataManager.getRenderLayerRange().isPositionWithinRange(pos);
   }

   public static Vec3 usePrecisionPlacement(BlockPos pos, BlockState stateSchematic) {
      if (Configs.Print.EASY_PLACE_PROTOCOL.getBooleanValue()) {
         EasyPlaceProtocol protocol = PlacementHandler.getEffectiveProtocolVersion();
         Vec3 hitPos = Vec3.atLowerCornerOf(pos);
         if (protocol == EasyPlaceProtocol.V3) {
            return EasyPlaceUtils.applyPlacementProtocolV3(pos, stateSchematic, hitPos);
         }

         if (protocol == EasyPlaceProtocol.V2) {
            return EasyPlaceUtils.applyCarpetProtocolHitVec(pos, stateSchematic, hitPos);
         }
      }

      return null;
   }

   public static boolean isSchematicBlock(BlockPos pos) {
      return getSchematicBlockState(pos) != null;
   }

   public static BlockState getSchematicBlockState(BlockPos pos) {
      return pos == null ? null : SchematicStateCache.INSTANCE.getSchematicState(pos);
   }

   public static boolean isWithinSelection1ModeRange(BlockPos pos) {
      return isWithinSelection1ModeRange(pos, 0);
   }

   /**
    * @param horizontalMargin 水平方向外扩的格数（东西南北各扩这么多，Y 不变）
    */
   public static boolean isWithinSelection1ModeRange(BlockPos pos, int horizontalMargin) {
      AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();
      if (selection == null) {
         return false;
      } else if (DataManager.getSelectionManager().getSelectionMode() == SelectionMode.NORMAL) {
         for (Box box : selection.getAllSubRegionBoxes()) {
            if (comparePos(box, pos, horizontalMargin)) {
               return true;
            }
         }

         return false;
      } else {
         Box boxx = selection.getSubRegionBox(DataManager.getSimpleArea().getName());
         return comparePos(boxx, pos, horizontalMargin);
      }
   }

   static boolean comparePos(Box box, BlockPos pos, int horizontalMargin) {
      if (box != null && box.getPos1() != null && box.getPos2() != null && pos != null) {
         // Litematica 的 pos1/pos2 保存的是「先点/后点」的两个角，pos1 不保证是最小角。
         // 假如先给两个角各做 offset 再交给 PrinterBox 归一化，那么某个轴（取决于拖拽方向）
         // 会由「外扩 margin」变成「内缩 margin」，表现为该轴两侧的边界列与选区外那一圈
         // 全被判为不在工作范围（南北边界排流体不工作）。必须先归一化再统一外扩。
         return new PrinterBox(box.getPos1(), box.getPos2())
            .expand(horizontalMargin, 0, horizontalMargin)
            .contains(pos);
      } else {
         return false;
      }
   }
}
