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
      AreaSelection selection = DataManager.getSelectionManager().getCurrentSelection();
      if (selection == null) {
         return false;
      } else if (DataManager.getSelectionManager().getSelectionMode() == SelectionMode.NORMAL) {
         for (Box box : selection.getAllSubRegionBoxes()) {
            if (comparePos(box, pos)) {
               return true;
            }
         }

         return false;
      } else {
         Box boxx = selection.getSubRegionBox(DataManager.getSimpleArea().getName());
         return comparePos(boxx, pos);
      }
   }

   static boolean comparePos(Box box, BlockPos pos) {
      if (box != null && box.getPos1() != null && box.getPos2() != null && pos != null) {
         PrinterBox printerBox = new PrinterBox(box.getPos1(), box.getPos2());
         return printerBox.contains(pos);
      } else {
         return false;
      }
   }
}
