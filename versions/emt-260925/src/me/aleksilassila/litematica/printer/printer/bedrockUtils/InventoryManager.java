package me.aleksilassila.litematica.printer.printer.bedrockUtils;

import me.aleksilassila.litematica.printer.utils.InventoryUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * 破基岩子系统的物品管理（移植自三改版 bedrockUtils.InventoryManager）。
 * <p>
 * 对接差异：三改版用自己那套 {@code zxy.inventory.InventoryUtils}（getSelectedSlot / setSelectedSlot /
 * getMainStacks）与 {@code ZxyUtils.getEnchantmentLevel}，这里改用 EMT 的
 * {@link InventoryUtils}（setSelectedSlot / setPickedItemToHand / setItemToOffhand）与
 * {@link PlayerUtils#getBlockBreakingSpeed}（与三改版逐行等价的挖掘速度公式）。
 * <p>
 * 三改版的 {@code refresh()}（Tweakeroo 刷新背包的取巧手法）与破基岩流程无关，未纳入移植。
 */
public final class InventoryManager {
   private InventoryManager() {
   }

   /**
    * 切到指定物品：
    * 镐子类 → 主手，且必须是能"瞬间挖掉活塞"的那把（破基岩机依赖瞬间破坏，否则活塞来不及复位）；
    * 其余（活塞 / 红石火把 / 粘液块）→ 副手，主手始终留给镐子。
    */
   public static boolean switchToItem(ItemLike item) {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player == null) {
         return false;
      } else {
         Item target = item.asItem();
         boolean isPickaxe = target == Items.DIAMOND_PICKAXE || target == Items.NETHERITE_PICKAXE;
         if (!isPickaxe) {
            TargetBlock.switchPickaxe = false;
            return InventoryUtils.setItemToOffhand(new ItemStack(item), mc);
         } else {
            int slot = getEfficientTool();
            if (slot == -1) {
               return false;
            } else {
               Inventory inventory = player.getInventory();
               if (slot < 9) {
                  InventoryUtils.setSelectedSlot(inventory, slot);
                  return true;
               } else {
                  return InventoryUtils.setPickedItemToHand(slot, inventory.getItem(slot), mc);
               }
            }
         }
      }
   }

   /** 主背包（36 格）里第一把能瞬间挖掉活塞的工具所在槽位 */
   private static int getEfficientTool() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return -1;
      } else {
         for (int i = 0; i < 36; i++) {
            if (isInstantMineTool(player, player.getInventory().getItem(i))) {
               return i;
            }
         }

         return -1;
      }
   }

   /** 是否存在能瞬间挖掉活塞的工具（= 破基岩的必要条件之一） */
   public static boolean canInstantlyMinePiston() {
      LocalPlayer player = Minecraft.getInstance().player;
      if (player == null) {
         return false;
      } else {
         Inventory inventory = player.getInventory();

         for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (isInstantMineTool(player, inventory.getItem(i))) {
               return true;
            }
         }

         return false;
      }
   }

   /** 与三改版同判据：对活塞的挖掘速度 > 45（即瞬间破坏，阈值取自原实现） */
   private static boolean isInstantMineTool(LocalPlayer player, ItemStack stack) {
      return PlayerUtils.getBlockBreakingSpeed(player, Blocks.PISTON.defaultBlockState(), stack) > 45.0F;
   }

   public static int getInventoryItemCount(ItemLike item) {
      LocalPlayer player = Minecraft.getInstance().player;
      return player == null ? 0 : player.getInventory().countItem(item.asItem());
   }

   /** 材料自检：返回需要提示的翻译键；一切就绪返回 null */
   public static String warningMessage() {
      Minecraft minecraftClient = Minecraft.getInstance();
      if (minecraftClient.gameMode.getPlayerMode().isCreative()) {
         return "bedrockminer.fail.missing.survival";
      } else if (getInventoryItemCount(Blocks.PISTON) < 2) {
         return "bedrockminer.fail.missing.piston";
      } else if (getInventoryItemCount(Blocks.REDSTONE_TORCH) < 1) {
         return "bedrockminer.fail.missing.redstonetorch";
      } else if (getInventoryItemCount(Blocks.SLIME_BLOCK) < 1) {
         return "bedrockminer.fail.missing.slime";
      } else {
         return !canInstantlyMinePiston() ? "bedrockminer.fail.missing.instantmine" : null;
      }
   }
}
