package me.aleksilassila.litematica.printer;

import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.ComposterBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Reference {
   public static final Minecraft MINECRAFT = Minecraft.getInstance();
   public static final String MOD_ID = "litematica_printer";
   public static final String MOD_NAME = "Litematica Printer";
   public static final Logger LOGGER = LoggerFactory.getLogger("litematica_printer");
   public static final Item[] COMPOSTABLE_ITEMS = Arrays.stream((ItemLike[])ComposterBlock.COMPOSTABLES.keySet().toArray(ItemLike[]::new))
      .map(ItemLike::asItem)
      .toArray(Item[]::new);
   public static final Item[] HOE_ITEMS = new Item[]{
      Items.DIAMOND_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.NETHERITE_HOE, Items.STONE_HOE, Items.WOODEN_HOE
   };
   public static final Item[] SHOVEL_ITEMS = new Item[]{
      Items.DIAMOND_SHOVEL, Items.IRON_SHOVEL, Items.GOLDEN_SHOVEL, Items.NETHERITE_SHOVEL, Items.STONE_SHOVEL, Items.WOODEN_SHOVEL
   };
   public static final Item[] AXE_ITEMS = new Item[]{
      Items.DIAMOND_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.NETHERITE_AXE, Items.STONE_AXE, Items.WOODEN_AXE
   };
}
