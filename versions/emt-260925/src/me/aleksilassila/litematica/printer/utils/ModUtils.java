package me.aleksilassila.litematica.printer.utils;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.CrashReport;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public class ModUtils {
   public static int closeScreen = 0;
   private static final String VIDEO_DIR_NAME = "litematica_printer_EMT";
   private static final String VIDEO_FILE_NAME = "video.mp4";
   private static final String VIDEO_TEMP_PREFIX = "litematica_printer_video_";
   private static final String VIDEO_RESOURCE = "assets/litematica-printer/video.mp4";
   @Nullable
   private static Object tweakToolSwitchEnum;
   @Nullable
   private static Object tweakSwapAlmostBrokenToolsEnum;
   @Nullable
   private static Object disableBlockBreakCooldownConfig;
   @Nullable
   private static Object itemSwapDurabilityThresholdConfig;
   @Nullable
   private static Method trySwitchToEffectiveToolMethod;
   @Nullable
   private static Method trySwapCurrentToolIfNearlyBrokenMethod;
   @Nullable
   private static Method getBooleanValueMethod;
   @Nullable
   private static Method getIntegerValueMethod;

   private static Path findBundledVideo() {
      for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
         Optional<Path> path = container.findPath("assets/litematica-printer/video.mp4");
         if (path.isPresent()) {
            return path.get();
         }
      }

      return null;
   }

   public static void openTrollVideoUrl() {
      Util.getPlatform().openUri("https://www.bilibili.com/video/BV1UT42167xb");
      crashNow();
   }

   public static void playBundledVideo() {
      Path source = findBundledVideo();
      if (source != null) {
         try {
            byte[] bundled = Files.readAllBytes(source);
            Path dir = FabricLoader.getInstance().getConfigDir().resolve("litematica_printer_EMT");
            Files.createDirectories(dir);
            Path target = dir.resolve("video.mp4");
            if (!hasSameContent(target, bundled)) {
               Files.write(target, bundled);
            }

            Util.getPlatform().openUri(target.toUri());
         } catch (Exception var4) {
            var4.printStackTrace();
            return;
         }

         crashNow();
      }
   }

   private static boolean hasSameContent(Path file, byte[] expected) {
      try {
         return Files.isRegularFile(file) && Files.size(file) == (long)expected.length ? Arrays.equals(Files.readAllBytes(file), expected) : false;
      } catch (Exception var3) {
         return false;
      }
   }

   public static void crashNow() {
      Minecraft.getInstance().emergencySaveAndCrash(new CrashReport("You were told NOT to click that! (千万别点！！！)", new RuntimeException("千万别点！！！")));
      throw new RuntimeException("You were told NOT to click that! (千万别点！！！)");
   }

   public static void cleanBundledVideoTemp() {
      try {
         File tmp = new File(System.getProperty("java.io.tmpdir"));
         File[] leftovers = tmp.listFiles((dir, name) -> name.startsWith("litematica_printer_video_") && name.endsWith(".mp4"));
         if (leftovers != null) {
            for (File f : leftovers) {
               try {
                  Files.deleteIfExists(f.toPath());
               } catch (Exception var7) {
               }
            }
         }
      } catch (Exception var8) {
      }
   }

   public static boolean isLoadMod(String modId) {
      return FabricLoader.getInstance().isModLoaded(modId);
   }

   public static boolean isCloudStoreLoaded() {
      return isLoadMod("cloudstore");
   }

   public static boolean isBedrockMinerLoaded() {
      return isLoadMod("bedrockminer");
   }

   public static boolean isBlockMinerLoaded() {
      return isLoadMod("blockminer");
   }

   public static boolean isTweakerooLoaded() {
      return isLoadMod("tweakeroo");
   }

   public static boolean isToolSwitchEnabled() {
      if (getBooleanValueMethod != null && tweakToolSwitchEnum != null) {
         try {
            return (Boolean)getBooleanValueMethod.invoke(tweakToolSwitchEnum);
         } catch (Exception var1) {
            var1.printStackTrace();
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isDisableBlockBreakCooldownEnabled() {
      if (getBooleanValueMethod != null && disableBlockBreakCooldownConfig != null) {
         try {
            return (Boolean)getBooleanValueMethod.invoke(disableBlockBreakCooldownConfig);
         } catch (Exception var1) {
            var1.printStackTrace();
            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean isSwapAlmostBrokenToolsEnabled() {
      if (getBooleanValueMethod != null && tweakSwapAlmostBrokenToolsEnum != null) {
         try {
            return (Boolean)getBooleanValueMethod.invoke(tweakSwapAlmostBrokenToolsEnum);
         } catch (Exception var1) {
            var1.printStackTrace();
            return false;
         }
      } else {
         return false;
      }
   }

   public static void trySwitchToEffectiveTool(BlockPos pos) {
      if (trySwitchToEffectiveToolMethod != null) {
         try {
            trySwitchToEffectiveToolMethod.invoke(null, pos);
         } catch (Exception var2) {
            var2.printStackTrace();
         }
      }
   }

   public static void trySwapCurrentToolIfNearlyBroken() {
      if (trySwapCurrentToolIfNearlyBrokenMethod != null) {
         try {
            trySwapCurrentToolIfNearlyBrokenMethod.invoke(null);
         } catch (Exception var1) {
            var1.printStackTrace();
         }
      }
   }

   public static boolean isToolTooDamagedForBreaking(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isDamageableItem() && isSwapAlmostBrokenToolsEnabled()) {
         int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
         return remainingDurability <= getMinDurability(stack);
      } else {
         return false;
      }
   }

   public static int getSafeBreakBudget(ItemStack stack) {
      if (stack != null && !stack.isEmpty() && stack.isDamageableItem() && isSwapAlmostBrokenToolsEnabled()) {
         int remainingDurability = stack.getMaxDamage() - stack.getDamageValue();
         return Math.max(0, remainingDurability - getMinDurability(stack));
      } else {
         return Integer.MAX_VALUE;
      }
   }

   private static int getMinDurability(ItemStack stack) {
      int threshold = getItemSwapDurabilityThreshold();
      int maxDamage = stack.getMaxDamage();
      if (maxDamage <= 100 && threshold <= 20 && (double)threshold / (double)maxDamage > 0.08) {
         threshold = (int)Math.ceil((double)maxDamage * 0.08);
      }

      return threshold;
   }

   private static int getItemSwapDurabilityThreshold() {
      if (getIntegerValueMethod != null && itemSwapDurabilityThresholdConfig != null) {
         try {
            return (Integer)getIntegerValueMethod.invoke(itemSwapDurabilityThresholdConfig);
         } catch (Exception var1) {
            var1.printStackTrace();
            return 5;
         }
      } else {
         return 5;
      }
   }

   static {
      if (FabricLoader.getInstance().isModLoaded("tweakeroo")) {
         try {
            Class<?> featureToggleClass = Class.forName("fi.dy.masa.tweakeroo.config.FeatureToggle");
            tweakToolSwitchEnum = featureToggleClass.getField("TWEAK_TOOL_SWITCH").get(null);
            tweakSwapAlmostBrokenToolsEnum = featureToggleClass.getField("TWEAK_SWAP_ALMOST_BROKEN_TOOLS").get(null);
            Class<?> disableConfigsClass = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Disable");
            disableBlockBreakCooldownConfig = disableConfigsClass.getField("DISABLE_BLOCK_BREAK_COOLDOWN").get(null);
            Class<?> genericConfigsClass = Class.forName("fi.dy.masa.tweakeroo.config.Configs$Generic");
            itemSwapDurabilityThresholdConfig = genericConfigsClass.getField("ITEM_SWAP_DURABILITY_THRESHOLD").get(null);
            Class<?> iConfigBooleanClass = Class.forName("fi.dy.masa.malilib.config.IConfigBoolean");
            getBooleanValueMethod = iConfigBooleanClass.getDeclaredMethod("getBooleanValue");
            getIntegerValueMethod = itemSwapDurabilityThresholdConfig.getClass().getMethod("getIntegerValue");
            Class<?> inventoryUtilsClass = Class.forName("fi.dy.masa.tweakeroo.util.InventoryUtils");
            trySwitchToEffectiveToolMethod = inventoryUtilsClass.getDeclaredMethod("trySwitchToEffectiveTool", BlockPos.class);
            trySwapCurrentToolIfNearlyBrokenMethod = inventoryUtilsClass.getDeclaredMethod("trySwapCurrentToolIfNearlyBroken");
         } catch (Exception var5) {
            tweakToolSwitchEnum = null;
            tweakSwapAlmostBrokenToolsEnum = null;
            disableBlockBreakCooldownConfig = null;
            itemSwapDurabilityThresholdConfig = null;
            trySwitchToEffectiveToolMethod = null;
            trySwapCurrentToolIfNearlyBrokenMethod = null;
            getBooleanValueMethod = null;
            getIntegerValueMethod = null;
            var5.printStackTrace();
         }
      }
   }
}
