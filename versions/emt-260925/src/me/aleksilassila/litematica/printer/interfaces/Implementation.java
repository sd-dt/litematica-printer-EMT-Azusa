package me.aleksilassila.litematica.printer.interfaces;

import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.BeaconBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BrewingStandBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CartographyTableBlock;
import net.minecraft.world.level.block.CommandBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.DragonEggBlock;
import net.minecraft.world.level.block.DropperBlock;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LoomBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SmithingTableBlock;
import net.minecraft.world.level.block.SmokerBlock;
import net.minecraft.world.level.block.StonecutterBlock;
import net.minecraft.world.level.block.TrapDoorBlock;

public class Implementation {
   public static Class<?>[] interactiveBlocks = new Class[]{
      AbstractFurnaceBlock.class,
      CraftingTableBlock.class,
      LeverBlock.class,
      DoorBlock.class,
      TrapDoorBlock.class,
      BedBlock.class,
      RedStoneWireBlock.class,
      ScaffoldingBlock.class,
      HopperBlock.class,
      EnchantingTableBlock.class,
      NoteBlock.class,
      JukeboxBlock.class,
      CakeBlock.class,
      FenceGateBlock.class,
      BrewingStandBlock.class,
      DragonEggBlock.class,
      CommandBlock.class,
      BeaconBlock.class,
      AnvilBlock.class,
      ComparatorBlock.class,
      RepeaterBlock.class,
      DropperBlock.class,
      DispenserBlock.class,
      ShulkerBoxBlock.class,
      LecternBlock.class,
      FlowerPotBlock.class,
      BarrelBlock.class,
      BellBlock.class,
      SmithingTableBlock.class,
      LoomBlock.class,
      CartographyTableBlock.class,
      GrindstoneBlock.class,
      StonecutterBlock.class,
      SmokerBlock.class,
      BlastFurnaceBlock.class,
      CrafterBlock.class
   };

   public static boolean isInteractive(Block block) {
      for (Class<?> clazz : interactiveBlocks) {
         if (clazz.isInstance(block)) {
            return true;
         }
      }

      return false;
   }
}
