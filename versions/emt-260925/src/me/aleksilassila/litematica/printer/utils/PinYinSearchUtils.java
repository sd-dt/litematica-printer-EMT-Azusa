package me.aleksilassila.litematica.printer.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PinYinSearchUtils {
   private static final HanyuPinyinOutputFormat PINYIN_FORMAT = new HanyuPinyinOutputFormat();
   private static final char TAG_PREFIX = '#';
   private static final String SPLIT_SEPARATOR = ",";
   private static final String CONTAINS_FLAG = "c";

   public static synchronized ArrayList<String> getPinYin(@Nullable String str) {
      if (str != null && !str.isEmpty()) {
         char[] chars = str.toCharArray();
         List<String[]> charPinyinList = new ArrayList<>();

         try {
            for (char c : chars) {
               if (c < 128) {
                  charPinyinList.add(new String[]{String.valueOf(c)});
               } else {
                  String[] pinyinArray = PinyinHelper.toHanyuPinyinStringArray(c, PINYIN_FORMAT);
                  if (pinyinArray != null && pinyinArray.length != 0) {
                     charPinyinList.add(pinyinArray);
                  } else {
                     charPinyinList.add(new String[]{String.valueOf(c)});
                  }
               }
            }
         } catch (BadHanyuPinyinOutputFormatCombination var8) {
            throw new RuntimeException("拼音格式配置错误，无法转换字符串：" + str, var8);
         }

         return generatePinyinCombinations(charPinyinList);
      } else {
         return new ArrayList<>();
      }
   }

   public static boolean hasPinYin(@Nullable String zh, @Nullable String py) {
      if (zh != null && !zh.isEmpty() && py != null && !py.isEmpty()) {
         String lowerPy = py.toLowerCase();
         return getPinYin(zh).stream().anyMatch(s -> s.contains(lowerPy));
      } else {
         return false;
      }
   }

   @NotNull
   private static ArrayList<String> generatePinyinCombinations(List<String[]> charPinyinList) {
      ArrayList<String> fullPinyinList = new ArrayList<>();
      ArrayList<String> shortPinyinList = new ArrayList<>();

      for (int i = 0; i < charPinyinList.size(); i++) {
         String[] currentPinyinArray = charPinyinList.get(i);
         ArrayList<String> tempFullList = new ArrayList<>();
         ArrayList<String> tempShortList = new ArrayList<>();

         for (String pinyin : currentPinyinArray) {
            if (i == 0) {
               tempFullList.add(pinyin);
               tempShortList.add(String.valueOf(pinyin.charAt(0)));
            } else {
               for (String existingFull : fullPinyinList) {
                  tempFullList.add(existingFull + pinyin);
               }

               for (String existingShort : shortPinyinList) {
                  tempShortList.add(existingShort + pinyin.charAt(0));
               }
            }
         }

         fullPinyinList = tempFullList;
         shortPinyinList = tempShortList;
      }

      fullPinyinList.addAll(shortPinyinList);
      return fullPinyinList;
   }

   public static boolean matchString(String targetStr, String matchStr, String[] matchRules) {
      if (targetStr != null && matchStr != null) {
         boolean enableContainsMatch = Arrays.asList(matchRules).contains("c");
         boolean containsMatchResult = enableContainsMatch && targetStr.contains(matchStr);
         boolean exactMatchResult = targetStr.equals(matchStr);
         return containsMatchResult || exactMatchResult;
      } else {
         return false;
      }
   }

   public static boolean matchBlockName(String expectedName, BlockState blockState) {
      return matchName(BlockNbtRule.parse(expectedName).blockMatcher(), blockState);
   }

   public static boolean matchItemName(String expectedName, ItemStack itemStack) {
      return matchName(expectedName, itemStack);
   }

   public static boolean matchName(String expectedName, Object targetObj) {
      if (expectedName != null && targetObj != null) {
         String[] nameAndRules = expectedName.split(",", -1);
         String coreName = nameAndRules[0];
         String[] matchRules = nameAndRules.length > 1 ? Arrays.copyOfRange(nameAndRules, 1, nameAndRules.length) : new String[0];
         String targetRegistryName = getTargetRegistryName(targetObj);
         if (targetRegistryName == null) {
            return false;
         } else if (coreName.startsWith(String.valueOf('#'))) {
            String tagName = coreName.substring(1);
            if (targetObj instanceof BlockState blockState) {
               return matchBlockTag(blockState, tagName, matchRules);
            } else {
               return targetObj instanceof ItemStack itemStack ? matchItemTag(itemStack, tagName, matchRules) : false;
            }
         } else {
            String targetDisplayName = getTargetDisplayName(targetObj);
            if (targetDisplayName == null) {
               return false;
            } else {
               boolean displayNameMatch = matchString(targetDisplayName, coreName, matchRules);
               boolean pinyinMatch = getPinYin(targetDisplayName).stream().anyMatch(pinyin -> matchString(pinyin, coreName, matchRules));
               boolean registryNameMatch = matchString(targetRegistryName, coreName, matchRules);
               return displayNameMatch || pinyinMatch || registryNameMatch;
            }
         }
      } else {
         return false;
      }
   }

   private static String getTargetRegistryName(Object targetObj) {
      if (targetObj instanceof BlockState blockState) {
         return BuiltInRegistries.BLOCK.getKey(blockState.getBlock()).toString();
      } else {
         return targetObj instanceof ItemStack itemStack ? BuiltInRegistries.ITEM.getKey(itemStack.getItem()).toString() : null;
      }
   }

   private static String getTargetDisplayName(Object targetObj) {
      if (targetObj instanceof BlockState blockState) {
         return blockState.getBlock().getName().getString();
      } else {
         return targetObj instanceof ItemStack itemStack ? itemStack.getHoverName().getString() : null;
      }
   }

   private static boolean matchBlockTag(BlockState blockState, String tagName, String[] matchRules) {
      if (tagName.isEmpty()) {
         return false;
      } else {
         Stream<TagKey<Block>> blockTagStream = blockState.tags();
         return blockTagStream.map(tag -> tag.location().toString()).anyMatch(tagFullName -> matchString(tagFullName, tagName, matchRules));
      }
   }

   private static boolean matchItemTag(ItemStack itemStack, String tagName, String[] matchRules) {
      if (tagName.isEmpty()) {
         return false;
      } else {
         Stream<TagKey<Item>> itemTagStream = itemStack.tags();
         return itemTagStream.map(tag -> tag.location().toString()).anyMatch(tagFullName -> matchString(tagFullName, tagName, matchRules));
      }
   }

   static {
      PINYIN_FORMAT.setCaseType(HanyuPinyinCaseType.LOWERCASE);
      PINYIN_FORMAT.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
      PINYIN_FORMAT.setVCharType(HanyuPinyinVCharType.WITH_V);
   }
}
