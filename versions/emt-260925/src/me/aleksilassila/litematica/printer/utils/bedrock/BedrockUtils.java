package me.aleksilassila.litematica.printer.utils.bedrock;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;

public class BedrockUtils {
   private static Miner bedrockMiner;

   public static void addToBreakList(BlockPos pos, ClientLevel world) {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.addToBreakList(pos, world);
         } catch (Exception var3) {
            var3.printStackTrace();
         }
      }
   }

   public static void clearTask() {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.clearTask();
         } catch (Exception var1) {
            var1.printStackTrace();
         }
      }
   }

   public static boolean isWorking() {
      if (bedrockMiner == null) {
         return false;
      } else {
         try {
            return bedrockMiner.isWorking();
         } catch (Exception var1) {
            var1.printStackTrace();
            return false;
         }
      }
   }

   public static void setWorking(boolean running) {
      setWorking(running, false);
   }

   public static void setWorking(boolean running, boolean showMessage) {
      if (BlockUtils.client.player != null && BlockUtils.client.player.isCreative() && running) {
         MessageUtils.setOverlayMessage(I18n.BEDROCK_CREATIVE_MODE.getName());
      } else if (bedrockMiner != null) {
         try {
            bedrockMiner.setWorking(running, showMessage);
            if (!running) {
               clearTask();
            }
         } catch (Exception var3) {
            var3.printStackTrace();
         }
      }
   }

   public static boolean isBedrockMinerFeatureEnable() {
      if (bedrockMiner == null) {
         return false;
      } else {
         try {
            return bedrockMiner.isBedrockMinerFeatureEnable();
         } catch (Exception var1) {
            var1.printStackTrace();
            return false;
         }
      }
   }

   public static void setBedrockMinerFeatureEnable(boolean bedrockMinerFeatureEnable) {
      if (bedrockMiner != null) {
         try {
            bedrockMiner.setBedrockMinerFeatureEnable(bedrockMinerFeatureEnable);
         } catch (Exception var2) {
            var2.printStackTrace();
         }
      }
   }

   static {
      try {
         if (ModUtils.isBlockMinerLoaded()) {
            bedrockMiner = new BlockMiner();
         } else if (ModUtils.isBedrockMinerLoaded()) {
            bedrockMiner = new BedrockMiner();
         } else {
            bedrockMiner = null;
         }
      } catch (Exception var1) {
         var1.printStackTrace();
         bedrockMiner = null;
      }
   }
}
