package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.options.ConfigOptionList;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.FillModeFacingType;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.enums.SelectionType;
import me.aleksilassila.litematica.printer.enums.WorkingModeType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

public class ConfigUtils {
   @NotNull
   public static final Minecraft client = Minecraft.getInstance();
   private static final int AUTO_ENABLE_MAX_ATTEMPTS = 60;
   private static final int AUTO_ENABLE_INTERVAL_TICKS = 20;
   private static int autoEnableAttemptsLeft = 0;
   private static int autoEnableTickCounter = 0;
   private static boolean autoEnableSessionActive = false;

   public static void startAutoEnableSession() {
      if (Configs.Core.AUTO_ENABLE_PRINTER.getBooleanValue() && !autoEnableSessionActive) {
         autoEnableSessionActive = true;
         autoEnableAttemptsLeft = 60;
         autoEnableTickCounter = 0;
         tryAutoEnableNow();
      }
   }

   public static void tickAutoEnable() {
      if (autoEnableSessionActive) {
         if (isPrinterEnable()) {
            resetAutoEnableSession();
         } else if (autoEnableAttemptsLeft <= 0) {
            resetAutoEnableSession();
         } else {
            autoEnableTickCounter++;
            if (autoEnableTickCounter >= 20) {
               autoEnableTickCounter = 0;
               tryAutoEnableNow();
            }
         }
      }
   }

   private static void tryAutoEnableNow() {
      if (autoEnableAttemptsLeft > 0) {
         Configs.Core.WORK_SWITCH.setBooleanValue(true);
         autoEnableAttemptsLeft--;
      }
   }

   public static void resetAutoEnableSession() {
      autoEnableSessionActive = false;
      autoEnableAttemptsLeft = 0;
      autoEnableTickCounter = 0;
   }

   public static boolean isPrinterEnable() {
      return Configs.Core.WORK_SWITCH.getBooleanValue();
   }

   public static boolean isPrintModeActive() {
      if (!isPrinterEnable()) {
         return false;
      } else {
         WorkingModeType mode = (WorkingModeType)Configs.Core.WORK_MODE.getOptionListValue();

         return switch (mode) {
            case SINGLE -> Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.PRINTER;
            case MULTI -> Configs.Core.PRINT.getBooleanValue();
         };
      }
   }

   public static boolean isMultiMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI);
   }

   public static boolean isSingleMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.SINGLE);
   }

   public static boolean isPrintMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI) && Configs.Core.PRINT.getBooleanValue()
         || Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.PRINTER;
   }

   public static boolean isMineMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI) && Configs.Core.MINE.getBooleanValue()
         || Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.MINE;
   }

   public static boolean isFillMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI) && Configs.Core.FILL.getBooleanValue()
         || Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.FILL;
   }

   public static boolean isFluidMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI) && Configs.Core.FLUID.getBooleanValue()
         || Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.FLUID;
   }

   public static boolean isBedrockMode() {
      return Configs.Core.WORK_MODE.getOptionListValue().equals(WorkingModeType.MULTI) && Configs.Hotkeys.BEDROCK.getBooleanValue()
         || Configs.Core.WORK_MODE_TYPE.getOptionListValue() == PrintModeType.BEDROCK;
   }

   public static PrintModeType getPrintModeType() {
      return (PrintModeType)Configs.Core.WORK_MODE_TYPE.getOptionListValue();
   }

   public static int getPlaceCooldown() {
      return Configs.Print.PLACE_COOLDOWN.getIntegerValue();
   }

   public static int getBreakCooldown() {
      return Configs.Mine.BREAK_COOLDOWN.getIntegerValue();
   }

   public static float getBreakProgressThreshold() {
      int value = Configs.Mine.BREAK_PROGRESS_THRESHOLD.getIntegerValue();
      if (value < 70) {
         value = 70;
      } else if (value > 100) {
         value = 100;
      }

      return (float)value / 100.0F;
   }

   public static int getWorkRange() {
      return Configs.Core.WORK_RANGE.getIntegerValue();
   }

   public static Direction getFillModeFacing() {
      if (Configs.Fill.FILL_BLOCK_FACING.getOptionListValue() instanceof FillModeFacingType fillModeFacingType) {
         return switch (fillModeFacingType) {
            case DOWN -> Direction.DOWN;
            case UP -> Direction.UP;
            case WEST -> Direction.WEST;
            case EAST -> Direction.EAST;
            case NORTH -> Direction.NORTH;
            case SOUTH -> Direction.SOUTH;
            default -> null;
         };
      } else {
         return null;
      }
   }

   public static boolean isPositionInSelectionRange(Player player, @NotNull BlockPos pos, ConfigOptionList selectionTypeConfig) {
      if (player != null && selectionTypeConfig != null) {
         if (selectionTypeConfig.getOptionListValue() instanceof SelectionType selectionType) {
            return switch (selectionType) {
               case LITEMATICA_RENDER_LAYER -> LitematicaUtils.isPositionWithinRange(pos);
               case LITEMATICA_SELECTION_BELOW_PLAYER -> (double)pos.getY() <= Math.floor(player.getY());
               case LITEMATICA_SELECTION_ABOVE_PLAYER -> (double)pos.getY() >= Math.ceil(player.getY());
               default -> true;
            };
         } else {
            return false;
         }
      } else {
         return false;
      }
   }
}
