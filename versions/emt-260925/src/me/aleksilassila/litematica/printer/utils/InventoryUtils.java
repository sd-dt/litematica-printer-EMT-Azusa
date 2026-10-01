package me.aleksilassila.litematica.printer.utils;

import com.google.common.collect.Lists;
import com.google.common.primitives.Shorts;
import com.google.common.primitives.SignedBytes;
import fi.dy.masa.litematica.util.EntityUtils;
import fi.dy.masa.malilib.gui.Message.MessageType;
import fi.dy.masa.malilib.util.InfoUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.EasyPlaceUtilsAccessor;
import me.aleksilassila.litematica.printer.mixin.printer.litematica.InventoryUtilsAccessor;
import net.minecraft.network.HashedStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.NonNullList;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.core.component.DataComponents;
import org.jetbrains.annotations.Nullable;

public class InventoryUtils {
   private static final Minecraft client = Minecraft.getInstance();
   private static final int OFFHAND_SLOT_INDEX = 40;
   private static final long MESSAGE_COOLDOWN_MS = 5000L;
   private static final Map<String, Long> LAST_MESSAGE_SEND_TIME = new ConcurrentHashMap<>();
   /** 工具切换调试输出的限流时间戳（毫秒） */
   private static long lastToolDebugTime;
   /** 换手会清零挖掘进度：只有新工具的破坏速度至少比当前高这么多倍才值得换 */
   private static final float MIN_SWITCH_IMPROVEMENT = 1.2F;
   private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("litematica-printer/toolswitch");

   public static int getSelectedSlot(Inventory inventory) {
      return inventory.getSelectedSlot();
   }

   public static void setSelectedSlot(Inventory inventory, int slot) {
      inventory.setSelectedSlot(slot);
   }

   public static NonNullList<ItemStack> getMainStacks(Inventory inventory) {
      return inventory.getNonEquipmentItems();
   }

   public static boolean playerHasAccessToItem(LocalPlayer playerEntity, Item item) {
      return playerHasAccessToItems(playerEntity, item);
   }

   public static boolean playerHasAccessToItems(LocalPlayer playerEntity, Item... items) {
      if (items != null && items.length != 0) {
         if (PlayerUtils.getAbilities(playerEntity).mayBuild) {
            return true;
         } else if (!playerEntity.containerMenu.equals(playerEntity.inventoryMenu)) {
            return false;
         } else {
            Inventory inventory = playerEntity.getInventory();

            for (Item item : items) {
               for (int i = 0; i < inventory.getContainerSize(); i++) {
                  if (inventory.getItem(i).getItem() == item) {
                     return true;
                  }
               }

               me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(item);
            }

            return false;
         }
      } else {
         return true;
      }
   }

   public static boolean playerHasItemInInventory(LocalPlayer playerEntity, Item item) {
      if (playerEntity != null && item != null) {
         if (PlayerUtils.getAbilities(playerEntity).instabuild) {
            return true;
         } else {
            Inventory inventory = playerEntity.getInventory();

            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
               if (inventory.getItem(slot).is(item)) {
                  return true;
               }
            }

            return false;
         }
      } else {
         return false;
      }
   }

   public static boolean playerHasAccessToMatchingStack(LocalPlayer playerEntity, ItemStack creativeFallback, Predicate<ItemStack> predicate) {
      if (playerEntity != null && predicate != null) {
         if (!PlayerUtils.getAbilities(playerEntity).instabuild) {
            if (!playerEntity.containerMenu.equals(playerEntity.inventoryMenu)) {
               return false;
            } else {
               Inventory inventory = playerEntity.getInventory();

               for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                  ItemStack stack = inventory.getItem(slot);
                  if (!stack.isEmpty() && predicate.test(stack)) {
                     return true;
                  }
               }

               return false;
            }
         } else {
            return creativeFallback != null && predicate.test(creativeFallback);
         }
      } else {
         return false;
      }
   }

   public static ItemStack createWaterPotionStack() {
      return PotionContents.createItemStack(Items.POTION, Potions.WATER);
   }

   public static boolean isWaterPotion(ItemStack stack) {
      if (stack != null && stack.is(Items.POTION)) {
         PotionContents contents = (PotionContents)stack.get(DataComponents.POTION_CONTENTS);
         return contents != null && contents.is(Potions.WATER);
      } else {
         return false;
      }
   }

   public static boolean setPickedItemToHand(ItemStack stack, Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else {
         int slotNum = mc.player.getInventory().findSlotMatchingItem(stack);
         return setPickedItemToHand(slotNum, stack, mc);
      }
   }

   public static void setHotbarSlot(int slot, Inventory inventory) {
      boolean usePacket = Configs.Print.PRINT_USE_PACKET.getBooleanValue();
      if (usePacket) {
         client.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
      }

      setSelectedSlot(inventory, slot);
   }

   public static InventoryUtils.PickResult checkPickSlotAvailable(int sourceSlot, Minecraft mc) {
      if (mc.player == null) {
         return InventoryUtils.PickResult.FAIL;
      } else {
         Player player = mc.player;
         Inventory inventory = player.getInventory();
         if (Inventory.isHotbarSlot(sourceSlot)) {
            return InventoryUtils.PickResult.SUCCESS;
         } else if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
            return InventoryUtils.PickResult.FAIL_NO_PICK_SLOTS_CONFIGURED;
         } else {
            int hotbarSlot = sourceSlot;
            if (sourceSlot == -1 || !Inventory.isHotbarSlot(sourceSlot)) {
               hotbarSlot = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory);
            }

            if (hotbarSlot == -1) {
               hotbarSlot = InventoryUtilsAccessor.getPickBlockTargetSlot(player);
            }

            return hotbarSlot != -1 ? InventoryUtils.PickResult.SUCCESS : InventoryUtils.PickResult.FAIL_NO_SUITABLE_SLOT_FOUND;
         }
      }
   }

   public static boolean setPickedItemToHand(int sourceSlot, ItemStack stack, Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else {
         Player player = mc.player;
         Inventory inventory = player.getInventory();
         if (Inventory.isHotbarSlot(sourceSlot)) {
            setHotbarSlot(sourceSlot, inventory);
            return true;
         } else if (InventoryUtilsAccessor.getPICK_BLOCKABLE_SLOTS().isEmpty()) {
            showMessageWithCooldown(MessageType.WARNING, "litematica.message.warn.pickblock.no_valid_slots_configured");
            return false;
         } else {
            int hotbarSlot = sourceSlot;
            if (sourceSlot == -1 || !Inventory.isHotbarSlot(sourceSlot)) {
               hotbarSlot = InventoryUtilsAccessor.getEmptyPickBlockableHotbarSlot(inventory);
            }

            if (hotbarSlot == -1) {
               hotbarSlot = InventoryUtilsAccessor.getPickBlockTargetSlot(player);
            }

            if (hotbarSlot != -1) {
               setHotbarSlot(hotbarSlot, inventory);
               if (EntityUtils.isCreativeMode(player)) {
                  getMainStacks(inventory).set(hotbarSlot, stack.copy());
                  client.gameMode.handleCreativeModeItemAdd(client.player.getMainHandItem(), 36 + hotbarSlot);
                  return true;
               } else {
                  EasyPlaceUtilsAccessor.callSetEasyPlaceLastPickBlockTime();
                  return swapItemToMainHand(stack.copy(), mc);
               }
            } else {
               showMessageWithCooldown(MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found");
               return false;
            }
         }
      }
   }

   public static boolean swapItemToMainHand(ItemStack stackReference, Minecraft mc) {
      Player player = mc.player;
      if (player == null) {
         return false;
      } else {
         boolean b = fi.dy.masa.malilib.util.InventoryUtils.areStacksEqualIgnoreNbt(stackReference, player.getMainHandItem());
         if (b) {
            return false;
         } else {
            int slot = fi.dy.masa.malilib.util.InventoryUtils.findSlotWithItem(player.inventoryMenu, stackReference, true);
            if (slot != -1) {
               ClientPacketListener connection = client.getConnection();
               if (connection == null) {
                  return false;
               } else {
                  int currentHotbarSlot = getSelectedSlot(player.getInventory());
                  if (Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
                     NonNullList<Slot> slots = player.inventoryMenu.slots;
                     int totalSlots = slots.size();
                     List<ItemStack> copies = Lists.newArrayListWithCapacity(totalSlots);

                     for (Slot slotItem : slots) {
                        copies.add(slotItem.getItem().copy());
                     }

                     Int2ObjectMap<HashedStack> snapshot = new Int2ObjectOpenHashMap();

                     for (int j = 0; j < totalSlots; j++) {
                        ItemStack original = copies.get(j);
                        ItemStack current = ((Slot)slots.get(j)).getItem();
                        if (!ItemStack.isSameItem(original, current)) {
                           snapshot.put(j, HashedStack.create(current, connection.decoratedHashOpsGenenerator()));
                        }
                     }

                     HashedStack hashedStack = HashedStack.create(player.inventoryMenu.getCarried(), connection.decoratedHashOpsGenenerator());
                     connection.send(
                        new ServerboundContainerClickPacket(
                           player.inventoryMenu.containerId,
                           player.inventoryMenu.getStateId(),
                           Shorts.checkedCast((long)slot),
                           SignedBytes.checkedCast((long)currentHotbarSlot),
                           ContainerInput.SWAP,
                           snapshot,
                           hashedStack
                        )
                     );
                     player.inventoryMenu.clicked(slot, currentHotbarSlot, ContainerInput.SWAP, player);
                  } else if (client.gameMode != null) {
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, slot, currentHotbarSlot, ContainerInput.SWAP, player);
                  }

                  return true;
               }
            } else {
               // 主背包里没有：也可能是"副手"拿着的。
               // malilib 的 findSlotWithItem 对玩家菜单会套 isRegularInventorySlot(x, false) = (x > 8 && x < 45)，
               // 而副手在玩家菜单里是 45 号槽 —— 正好被排除，于是副手的东西永远换不到主手。
               return swapOffhandToMainHand(stackReference, mc);
            }
         }
      }
   }

   /**
    * 副手 → 主手：一次原版 SWAP 点击即可（{@code button = 40} 就代表"副手"，被点的槽是当前选中的快捷栏槽，
    * 与游戏里按 F 交换等价）。修的是「副手拿的建材无法放置」：那条路径原本被 malilib 的槽位过滤挡掉了。
    */
   public static boolean swapOffhandToMainHand(ItemStack stackReference, Minecraft mc) {
      Player player = mc.player;
      if (player == null || client.gameMode == null) {
         return false;
      }

      // 只有"没开容器界面"时，槽位号才对应玩家自己的背包
      if (player.containerMenu != player.inventoryMenu) {
         return false;
      }

      if (!fi.dy.masa.malilib.util.InventoryUtils.areStacksEqualIgnoreNbt(getOffhandStack(player), stackReference)) {
         return false;
      }

      int hotbarSlot = getSelectedSlot(player.getInventory());
      // 玩家菜单的布局：0 合成结果、1~4 合成格、5~8 盔甲、9~35 背包、36~44 快捷栏、45 副手
      sendSwapClick(player, 36 + hotbarSlot, OFFHAND_SLOT_INDEX);
      return true;
   }

   /**
    * 发一次 SWAP（交换）点击。{@code button} 为 0~8 表示与快捷栏那一格交换、为 40 表示与副手交换；
    * 两种分支（发包放置 / 原版点击）与 {@link #swapItemToMainHand} 里原有写法保持一致。
    */
   private static void sendSwapClick(Player player, int menuSlot, int button) {
      ClientPacketListener connection = client.getConnection();
      if (connection == null) {
         return;
      }

      if (Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
         NonNullList<Slot> slots = player.inventoryMenu.slots;
         int totalSlots = slots.size();
         List<ItemStack> copies = Lists.newArrayListWithCapacity(totalSlots);

         for (Slot slotItem : slots) {
            copies.add(slotItem.getItem().copy());
         }

         Int2ObjectMap<HashedStack> snapshot = new Int2ObjectOpenHashMap();

         for (int j = 0; j < totalSlots; j++) {
            ItemStack original = copies.get(j);
            ItemStack current = ((Slot)slots.get(j)).getItem();
            if (!ItemStack.isSameItem(original, current)) {
               snapshot.put(j, HashedStack.create(current, connection.decoratedHashOpsGenenerator()));
            }
         }

         HashedStack hashedStack = HashedStack.create(player.inventoryMenu.getCarried(), connection.decoratedHashOpsGenenerator());
         connection.send(
            new ServerboundContainerClickPacket(
               player.inventoryMenu.containerId,
               player.inventoryMenu.getStateId(),
               Shorts.checkedCast((long)menuSlot),
               SignedBytes.checkedCast((long)button),
               ContainerInput.SWAP,
               snapshot,
               hashedStack
            )
         );
         player.inventoryMenu.clicked(menuSlot, button, ContainerInput.SWAP, player);
      } else if (client.gameMode != null) {
         client.gameMode.handleContainerInput(player.inventoryMenu.containerId, menuSlot, button, ContainerInput.SWAP, player);
      }
   }

   /**
    * 这次放置该用哪只手：
    * <ol>
    *   <li>主手已经有需要的物品 → 主手；</li>
    *   <li>主手没有、<b>副手有</b> → 副手（原版从副手放方块就是这么走的：既不用来回换物品，
    *       主手的工具/武器也不会被挤到副手，避免"换建材 → 工具跑到副手 → 挖掘又没工具"的来回折腾）；</li>
    *   <li>两只手都没有 → 主手（调用方随后会去背包里换）。</li>
    * </ol>
    */
   public static InteractionHand placementHand(Player player, @Nullable Item[] items, @Nullable Predicate<ItemStack> predicate) {
      if (player == null) {
         return InteractionHand.MAIN_HAND;
      } else if (matchesRequired(player.getMainHandItem(), items, predicate)) {
         return InteractionHand.MAIN_HAND;
      } else {
         return matchesRequired(player.getOffhandItem(), items, predicate) ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
      }
   }

   /** 主手或副手是否已经拿着需要的物品 —— 拿着就不用再换手了 */
   public static boolean isRequiredItemInHands(Player player, Item[] items) {
      return player != null
         && (matchesRequired(player.getMainHandItem(), items, null) || matchesRequired(player.getOffhandItem(), items, null));
   }

   /** 手上的这一堆是不是"需要的物品"（物品表或自定义判定任一命中） */
   private static boolean matchesRequired(ItemStack stack, @Nullable Item[] items, @Nullable Predicate<ItemStack> predicate) {
      if (stack == null || stack.isEmpty()) {
         return false;
      }

      if (predicate != null && predicate.test(stack)) {
         return true;
      }

      if (items != null) {
         for (Item item : items) {
            if (item != null && stack.is(item)) {
               return true;
            }
         }
      }

      return false;
   }

   public static ItemStack getOffhandStack(Player player) {
      return player.getInventory().getItem(40);
   }

   public static boolean setItemToOffhand(ItemStack stack, Minecraft mc) {
      if (mc.player == null) {
         return false;
      } else {
         Player player = mc.player;
         boolean isAlreadyInOffhand = fi.dy.masa.malilib.util.InventoryUtils.areStacksEqual(stack, getOffhandStack(player));
         if (isAlreadyInOffhand) {
            return true;
         } else if (EntityUtils.isCreativeMode(player)) {
            player.getInventory().setItem(40, stack.copy());
            client.gameMode.handleCreativeModeItemAdd(getOffhandStack(player), 40);
            return true;
         } else {
            int sourceSlot = fi.dy.masa.malilib.util.InventoryUtils.findSlotWithItem(player.inventoryMenu, stack, true);
            if (sourceSlot == -1) {
               InfoUtils.showGuiOrInGameMessage(MessageType.WARNING, "litematica.message.warn.pickblock.no_suitable_slot_found", new Object[0]);
               return false;
            } else {
               ClientPacketListener connection = client.getConnection();
               if (connection == null) {
                  return false;
               } else {
                  if (Configs.Print.PRINT_USE_PACKET.getBooleanValue()) {
                     NonNullList<Slot> slots = player.inventoryMenu.slots;
                     int totalSlots = slots.size();
                     List<ItemStack> copies = Lists.newArrayListWithCapacity(totalSlots);

                     for (Slot slotItem : slots) {
                        copies.add(slotItem.getItem().copy());
                     }

                     Int2ObjectMap<HashedStack> snapshot = new Int2ObjectOpenHashMap();

                     for (int j = 0; j < totalSlots; j++) {
                        ItemStack original = copies.get(j);
                        ItemStack current = ((Slot)slots.get(j)).getItem();
                        if (!ItemStack.isSameItem(original, current)) {
                           snapshot.put(j, HashedStack.create(current, connection.decoratedHashOpsGenenerator()));
                        }
                     }

                     HashedStack hashedStack = HashedStack.create(player.inventoryMenu.getCarried(), connection.decoratedHashOpsGenenerator());
                     connection.send(
                        new ServerboundContainerClickPacket(
                           player.inventoryMenu.containerId,
                           player.inventoryMenu.getStateId(),
                           Shorts.checkedCast((long)sourceSlot),
                           SignedBytes.checkedCast(40L),
                           ContainerInput.SWAP,
                           snapshot,
                           hashedStack
                        )
                     );
                     player.inventoryMenu.clicked(sourceSlot, 40, ContainerInput.SWAP, player);
                  } else if (client.gameMode != null) {
                     client.gameMode.handleContainerInput(player.inventoryMenu.containerId, sourceSlot, 40, ContainerInput.SWAP, player);
                  }

                  return true;
               }
            }
         }
      }
   }

   private static void showMessageWithCooldown(MessageType type, String messageKey) {
      long currentTime = System.currentTimeMillis();
      long lastSendTime = LAST_MESSAGE_SEND_TIME.getOrDefault(messageKey, 0L);
      if (currentTime - lastSendTime >= 5000L) {
         InfoUtils.showGuiOrInGameMessage(type, messageKey, new Object[0]);
         LAST_MESSAGE_SEND_TIME.put(messageKey, currentTime);
      }
   }

   public static boolean switchToItems(LocalPlayer player, Item[] items) {
      if (items == null || items.length == 0) {
         items = new Item[]{Items.AIR};
      }

      Inventory inventory = player.getInventory();
      boolean isCreativeMode = PlayerUtils.getAbilities(player).instabuild;
      if (isCreativeMode) {
         ItemStack stack = new ItemStack(items[0]);
         return setPickedItemToHand(stack, client);
      } else {
         for (Item item : items) {
            int slot = -1;
            boolean onlyEmptyShulker = isShulkerItem(item) && Configs.Print.PRINT_ONLY_EMPTY_SHULKER.getBooleanValue();

            for (int i = 0; i < inventory.getContainerSize(); i++) {
               ItemStack itemStack = inventory.getItem(i);
               if ((!onlyEmptyShulker || !BlockUtils.isShulkerRecentlyOpened(i))
                  && itemStack.getItem().equals(item)
                  && (!onlyEmptyShulker || isEmptyShulker(itemStack))) {
                  slot = i;
                  break;
               }
            }

            if (slot != -1) {
               ItemStack itemStack = inventory.getItem(slot);
               return setPickedItemToHand(slot, itemStack, client);
            }

            me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(item);
         }

         return false;
      }
   }

   public static boolean isShulkerItem(Item item) {
      return Block.byItem(item) instanceof ShulkerBoxBlock;
   }

   public static boolean isEmptyShulker(ItemStack stack) {
      ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
      return contents == null ? true : !contents.nonEmptyItemCopyStream().toList().iterator().hasNext();
   }

   /**
    * 自动工具切换：把"挖这个方块最合适的工具"换到手上。
    * <p>
    * 判定分两轮（与原来一致，但写清楚并把"当前手持就是最优"直接短路）：
    <ol>
    *   <li>方块需要精准采集才有掉落（玻璃类）时：优先有精准采集的工具；</li>
    *   <li>否则比破坏速度（{@link #getDestroyProgress}，含工具挖掘速度与附魔/药水加成）。</li>
    </ol>
    * 只有"严格更好"才换手；工具在快捷栏就直接选中，在背包里就换到当前手持槽。
    */
   private static long lastToolDemandMs;

   /**
    * 兼容快捷潜影盒自动取货：背包里找不到可用工具时，按方块类型向潜影盒登记工具需求。
    * <p>
    * 工具具体是哪一把事先不知道，所以按物品标签（镐/斧/锹/锄）登记，最多 6 个候选，
    * 每秒最多登记一次，避免刷屏式开盒。潜影盒取货开着的时候才会生效。
    */
   public static void requestToolFromShulker(BlockState blockState) {
      if (blockState == null || blockState.isAir() || !Configs.Core.QUICK_SHULKER.getBooleanValue()) {
         return;
      }

      long now = System.currentTimeMillis();
      if (now - lastToolDemandMs < 1000L) {
         return;
      }

      lastToolDemandMs = now;
      net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag = null;
      if (blockState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)) {
         tag = net.minecraft.tags.ItemTags.PICKAXES;
      } else if (blockState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)) {
         tag = net.minecraft.tags.ItemTags.AXES;
      } else if (blockState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)) {
         tag = net.minecraft.tags.ItemTags.SHOVELS;
      } else if (blockState.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)) {
         tag = net.minecraft.tags.ItemTags.HOES;
      }

      if (tag == null) {
         return;
      }

      int demands = 0;
      for (net.minecraft.world.item.Item item : net.minecraft.core.registries.BuiltInRegistries.ITEM) {
         if (demands >= 6) {
            break;
         }

         if (item.getDefaultInstance().is(tag)) {
            me.aleksilassila.litematica.printer.printer.zxy.inventory.InventoryUtils.addQuickShulkerDemand(item);
            demands++;
         }
      }
   }

   public static boolean switchToBestTool(LocalPlayer player, BlockState blockState) {
      if (player == null || blockState == null || blockState.isAir()) {
         return false;
      }

      if (PlayerUtils.getAbilities(player).instabuild) {
         return false;
      }

      Inventory inventory = player.getInventory();
      ItemStack currentStack = player.getMainHandItem();
      boolean preferSilkTouch = ToolSelectionUtils.prefersSilkTouchForDrops(blockState);
      NonNullList<ItemStack> stacks = getMainStacks(inventory);
      int selectedSlot = getSelectedSlot(inventory);

      boolean currentAllowed = BreakUtils.isToolAllowedByDurabilityProtection(currentStack);
      float currentProgress = currentAllowed ? getDestroyProgress(player, blockState, currentStack) : 0.0F;
      boolean currentSilk = preferSilkTouch && currentAllowed && ToolSelectionUtils.hasSilkTouch(currentStack);

      // 第一轮：需要精准采集时先只看"有精准采集"的工具
      int bestSlot = -1;
      float bestProgress = currentProgress;
      if (preferSilkTouch && !currentSilk) {
         for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && BreakUtils.isToolAllowedByDurabilityProtection(stack) && ToolSelectionUtils.hasSilkTouch(stack)) {
               float progress = getDestroyProgress(player, blockState, stack);
               if (progress > bestProgress) {
                  bestProgress = progress;
                  bestSlot = slot;
               }
            }
         }
      }

      // 第二轮：普通情况（或第一轮没找到精准采集工具）比破坏速度
      if (bestSlot == -1) {
         for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (!stack.isEmpty() && BreakUtils.isToolAllowedByDurabilityProtection(stack)) {
               float progress = getDestroyProgress(player, blockState, stack);
               if (progress > bestProgress) {
                  bestProgress = progress;
                  bestSlot = slot;
               }
            }
         }
      }

      // 换工具会立刻清零当前挖掘进度，所以：
      // 1) 最优工具在背包里、而快捷栏里有"几乎一样好"的，就用快捷栏那个（省一次背包交换）；
      // 2) 只有明显更好（默认 20% 以上）才值得换 —— 需要精准采集而手上没有时例外（为了掉落必须换）。
      if (bestSlot >= 9) {
         boolean bestSilk = preferSilkTouch && ToolSelectionUtils.hasSilkTouch(stacks.get(bestSlot));
         float threshold = bestProgress * 0.95F;

         for (int slot = 0; slot < 9 && slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack.isEmpty() || !BreakUtils.isToolAllowedByDurabilityProtection(stack)) {
               continue;
            }

            if (preferSilkTouch && bestSilk && !ToolSelectionUtils.hasSilkTouch(stack)) {
               continue;
            }

            if (getDestroyProgress(player, blockState, stack) >= threshold) {
               bestSlot = slot;
               break;
            }
         }
      }

      if (!preferSilkTouch || currentSilk) {
         float required = currentProgress * MIN_SWITCH_IMPROVEMENT;
         if (bestProgress <= required) {
            if (Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
               debugToolSwitch(
                  "自动工具切换：提升不足（手持 "
                     + currentStack.getItem()
                     + " "
                     + String.format("%.3f", currentProgress)
                     + " → 最优 "
                     + String.format("%.3f", bestProgress)
                     + ", "
                     + blockState.getBlock()
                     + "）",
                  3000L
               );
            }

            return false;
         }
      }

      if (bestSlot == -1 || bestSlot == selectedSlot) {
         // 手上的已经是最优（或没有更好的）：什么都不用做
         if (Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
            debugToolSwitch(
               "自动工具切换：无需切换（手持 "
                  + currentStack.getItem()
                  + " "
                  + String.format("%.3f", currentProgress)
                  + " / 最优 "
                  + String.format("%.3f", bestProgress)
                  + ", "
                  + blockState.getBlock()
                  + "）",
               3000L
            );
         }

         return false;
      }

      boolean switched = swapToolToSelectedSlot(bestSlot, player);
      if (Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
         String where = bestSlot < 9 ? "快捷栏" + bestSlot : "背包槽" + bestSlot;
         debugToolSwitch(
            (switched ? "自动工具切换 → " : "自动工具切换失败 → ")
               + where
               + " "
               + stacks.get(bestSlot).getItem()
               + " ("
               + String.format("%.3f", bestProgress)
               + " vs 手持 "
               + currentStack.getItem()
               + " "
               + String.format("%.3f", currentProgress)
               + ", 手持槽 "
               + selectedSlot
               + ", "
               + blockState.getBlock()
               + ")",
            500L
         );
      }

      return switched;
   }

   /** 工具切换的调试输出（只在「核心 → 调试输出」打开时输出，按间隔限流，避免刷屏） */
   private static void debugToolSwitch(String text, long minIntervalMs) {
      long now = System.currentTimeMillis();
      if (now - lastToolDebugTime < minIntervalMs) {
         return;
      }

      lastToolDebugTime = now;
      MessageUtils.setOverlayMessage(text);
      // 同时写进游戏日志（logs/latest.log），方便开发侧直接核对"到底有没有走到切换"
      LOGGER.info("[tool-switch] {}", text);
   }

   /**
    * 把工具换到手上。机制照抄 Tweakeroo 的 {@code InventoryUtils.swapToolToHand}：
    * <ul>
    *   <li>只有"没开容器"（{@code containerMenu == playerScreenHandler}）时才动手，否则槽位号对应的不是背包，会串物品；</li>
    *   <li>快捷栏：直接 {@code setSelectedSlot} + 发 {@code UpdateSelectedSlotC2SPacket}；</li>
    *   <li>背包槽：先用 {@link #getUsableHotbarSlotForTool} 挑一个"可用的快捷栏槽"（空的优先，其次不拿工具/武器的），
    *       切到那个槽，再和它做原版 SWAP 点击；</li>
    *   <li>背包那条路额外先做一次本地交换，保证本 tick 就能用上新工具（否则要等服务端回包，破坏进度会先用旧工具算一次）。</li>
    * </ul>
    */
   public static boolean swapToolToSelectedSlot(int sourceSlot, LocalPlayer player) {
      if (player == null || sourceSlot < 0 || client.gameMode == null) {
         return false;
      }

      Inventory inventory = player.getInventory();
      // 开着容器界面时不要动（Tweakeroo 同样有这个判定）
      if (player.containerMenu != player.inventoryMenu) {
         return false;
      }

      if (sourceSlot < 9) {
         // 已经在快捷栏：直接选中并通知服务端
         setHotbarSlotForTool(sourceSlot, inventory);
         return true;
      }

      if (sourceSlot >= inventory.getContainerSize()) {
         return false;
      }

      ItemStack toolStack = inventory.getItem(sourceSlot);
      if (toolStack.isEmpty()) {
         return false;
      }

      int hotbarSlot = getUsableHotbarSlotForTool(getSelectedSlot(inventory), inventory);
      if (hotbarSlot < 0) {
         return false;
      }

      if (hotbarSlot != getSelectedSlot(inventory)) {
         setHotbarSlotForTool(hotbarSlot, inventory);
      }

      // 1) 本地立即交换：本 tick 就能用上新工具
      ItemStack handStack = inventory.getItem(hotbarSlot).copy();
      inventory.setItem(sourceSlot, handStack);
      inventory.setItem(hotbarSlot, toolStack.copy());
      // 2) 原版 SWAP 点击，让服务端做同样的交换；玩家菜单里 36~44 是快捷栏、9~35 是背包
      client.gameMode
         .handleContainerInput(player.containerMenu.containerId, sourceSlot, hotbarSlot, ContainerInput.SWAP, player);
      return true;
   }

   /** 快捷栏选中：先改本地选中槽，再发原版 UpdateSelectedSlot 包（照 Tweakeroo：不依赖「发包放置」开关） */
   private static void setHotbarSlotForTool(int slot, Inventory inventory) {
      setSelectedSlot(inventory, slot);
      if (client.getConnection() != null) {
         client.getConnection().send(new ServerboundSetCarriedItemPacket(inventory.getSelectedSlot()));
      }
   }

   /**
    * 挑一个"可以放工具的快捷栏槽"（Tweakeroo 的 {@code getUsableHotbarSlotForTool}）：
    * 当前选中槽是空的就用它；否则优先空槽，其次不拿工具/武器的槽，最后退回第一个槽。
    */
   private static int getUsableHotbarSlotForTool(int currentHotbarSlot, Inventory inventory) {
      int first = -1;
      int nonTool = -1;

      if (currentHotbarSlot >= 0 && currentHotbarSlot < 9) {
         ItemStack stack = inventory.getItem(currentHotbarSlot);
         if (stack.isEmpty()) {
            return currentHotbarSlot;
         }

         if (!isRegularTool(stack)) {
            nonTool = currentHotbarSlot;
         }
      }

      for (int slot = 0; slot < 9; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (stack.isEmpty()) {
            return slot;
         }

         if (nonTool == -1 && !isRegularTool(stack)) {
            nonTool = slot;
         }

         if (first == -1) {
            first = slot;
         }
      }

      return nonTool >= 0 ? nonTool : first;
   }

   /** 大致等价于 Tweakeroo 的 {@code EquipmentUtils.isRegularTool}：有耐久的就是工具/武器/护甲 */
   private static boolean isRegularTool(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isDamageableItem();
   }

   public static boolean hasUsableSilkTouchTool(LocalPlayer player) {
      if (player != null && !PlayerUtils.getAbilities(player).instabuild) {
         for (ItemStack stack : getMainStacks(player.getInventory())) {
            if (!stack.isEmpty() && BreakUtils.isToolAllowedByDurabilityProtection(stack) && ToolSelectionUtils.hasSilkTouch(stack)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static float getDestroyProgress(LocalPlayer player, BlockState state, ItemStack stack) {
      float hardness = state.getBlock().defaultDestroyTime();
      if (hardness < 0.0F) {
         return 0.0F;
      } else if (hardness == 0.0F) {
         return 1.0F;
      } else {
         int divisor = state.requiresCorrectToolForDrops() && !stack.isCorrectToolForDrops(state) ? 100 : 30;
         return PlayerUtils.getBlockBreakingSpeed(player, state, stack) / hardness / (float)divisor;
      }
   }

   public static int getConsumableSurplus(LocalPlayer player, ItemStack stack, @Nullable Predicate<ItemStack> requiredStackPredicate, int reserveCount) {
      if (player == null || stack == null) {
         return 0;
      } else if (reserveCount >= 0 && !PlayerUtils.getAbilities(player).instabuild && !stack.isEmpty() && !stack.isDamageableItem()) {
         Predicate<ItemStack> predicate = requiredStackPredicate != null ? requiredStackPredicate : candidate -> candidate.is(stack.getItem());
         return Math.max(0, countMatchingMainInventory(player, predicate) - reserveCount);
      } else {
         return Integer.MAX_VALUE;
      }
   }

   public static ItemStack findReserveBlockedStack(
      LocalPlayer player, Item[] items, @Nullable Predicate<ItemStack> requiredStackPredicate, int reserveCount
   ) {
      if (player != null && reserveCount >= 0 && !PlayerUtils.getAbilities(player).instabuild) {
         Inventory inventory = player.getInventory();
         int size = Math.min(36, inventory.getContainerSize());
         if (requiredStackPredicate != null) {
            for (int slot = 0; slot < size; slot++) {
               ItemStack stack = inventory.getItem(slot);
               if (!stack.isEmpty() && requiredStackPredicate.test(stack) && getConsumableSurplus(player, stack, requiredStackPredicate, reserveCount) <= 0
                  )
                {
                  return stack.copy();
               }
            }

            return ItemStack.EMPTY;
         } else {
            Item[] targetItems = items != null && items.length != 0 ? items : new Item[]{Items.AIR};

            for (Item item : targetItems) {
               for (int slotx = 0; slotx < size; slotx++) {
                  ItemStack stack = inventory.getItem(slotx);
                  if (!stack.isEmpty() && stack.is(item) && getConsumableSurplus(player, stack, null, reserveCount) <= 0) {
                     return stack.copy();
                  }
               }
            }

            return ItemStack.EMPTY;
         }
      } else {
         return ItemStack.EMPTY;
      }
   }

   public static int countMatchingMainInventory(LocalPlayer player, Predicate<ItemStack> predicate) {
      Inventory inventory = player.getInventory();
      int size = Math.min(36, inventory.getContainerSize());
      int count = 0;

      for (int slot = 0; slot < size; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!stack.isEmpty() && predicate.test(stack)) {
            count += stack.getCount();
         }
      }

      return count;
   }

   /** 统计"可用材料"数量：主背包 + 快捷栏；{@code includeOffhand} 为真时把副手也算进去 */
   public static int countMatchingAvailable(LocalPlayer player, Predicate<ItemStack> predicate, boolean includeOffhand) {
      int count = countMatchingMainInventory(player, predicate);
      if (includeOffhand && player != null) {
         ItemStack offhand = player.getInventory().getItem(OFFHAND_SLOT_INDEX);
         if (!offhand.isEmpty() && predicate.test(offhand)) {
            count += offhand.getCount();
         }
      }

      return count;
   }

   public static int countAvailableIncludingShulkers(LocalPlayer player, Item item) {
      int count = countMatchingMainInventory(player, stackx -> stackx.is(item));
      // 副手也算"身上有"：建材放在副手时不能被判成缺少材料（否则会一直提示缺料、并触发云存储补货）
      ItemStack offhand = player.getInventory().getItem(OFFHAND_SLOT_INDEX);
      if (!offhand.isEmpty() && offhand.is(item)) {
         count += offhand.getCount();
      }
      Inventory inventory = player.getInventory();
      int size = Math.min(36, inventory.getContainerSize());

      for (int slot = 0; slot < size; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!stack.isEmpty() && isShulkerItem(stack.getItem())) {
            ItemContainerContents contents = (ItemContainerContents)stack.get(DataComponents.CONTAINER);
            if (contents != null) {
               for (ItemStack inner : contents.nonEmptyItemCopyStream().toList()) {
                  if (inner.is(item)) {
                     count += inner.getCount();
                  }
               }
            }
         }
      }

      return count;
   }

   public static boolean hasRecentlyOpenedShulker(LocalPlayer player) {
      Inventory inventory = player.getInventory();
      int size = Math.min(36, inventory.getContainerSize());

      for (int slot = 0; slot < size; slot++) {
         ItemStack stack = inventory.getItem(slot);
         if (!stack.isEmpty() && isShulkerItem(stack.getItem()) && BlockUtils.isShulkerRecentlyOpened(slot)) {
            return true;
         }
      }

      return false;
   }

   public InventoryUtils.PickResult checkCanSwitchToItems(LocalPlayer player, Item[] items) {
      if (player == null) {
         return InventoryUtils.PickResult.FAIL;
      } else {
         Item[] targetItems = items;
         if (items == null || items.length == 0) {
            targetItems = new Item[]{Items.AIR};
         }

         Inventory inv = player.getInventory();
         boolean isCreativeMode = PlayerUtils.getAbilities(player).instabuild;
         if (isCreativeMode) {
            return checkPickSlotAvailable(-1, client);
         } else {
            for (Item item : targetItems) {
               for (int i = 0; i < inv.getContainerSize(); i++) {
                  ItemStack itemStack = inv.getItem(i);
                  if (itemStack.getItem().equals(item)) {
                     return checkPickSlotAvailable(i, client);
                  }
               }
            }

            return InventoryUtils.PickResult.FAIL;
         }
      }
   }

   public static enum PickResult {
      SUCCESS,
      FAIL,
      FAIL_NO_PICK_SLOTS_CONFIGURED,
      FAIL_NO_SUITABLE_SLOT_FOUND;

      public boolean isNoPickSlotsConfigured() {
         return this == FAIL_NO_PICK_SLOTS_CONFIGURED;
      }

      public boolean isNoSuitableSlotFound() {
         return this == FAIL_NO_SUITABLE_SLOT_FOUND;
      }

      public boolean isNoAvailableSlot() {
         return this.isNoPickSlotsConfigured() || this.isNoSuitableSlotFound();
      }

      public boolean isAvailable() {
         return this == SUCCESS;
      }
   }
}
