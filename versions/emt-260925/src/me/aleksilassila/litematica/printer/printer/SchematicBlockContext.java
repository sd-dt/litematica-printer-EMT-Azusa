package me.aleksilassila.litematica.printer.printer;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.Optional;
import lombok.Generated;
import me.aleksilassila.litematica.printer.utils.BlockUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

public class SchematicBlockContext {
   public final Minecraft client;
   public final ClientLevel level;
   public final WorldSchematic schematic;
   public final BlockPos blockPos;
   public final BlockState currentState;
   public final BlockState requiredState;

   public SchematicBlockContext(Minecraft client, ClientLevel level, WorldSchematic schematic, BlockPos blockPos) {
      this.client = client;
      this.level = level;
      this.schematic = schematic;
      this.blockPos = blockPos;
      this.currentState = level.getBlockState(blockPos);
      this.requiredState = LitematicaUtils.getSchematicBlockState(blockPos);
   }

   public static <T extends Comparable<T>> Optional<T> getProperty(BlockState blockState, Property<T> property) {
      return BlockUtils.getProperty(blockState, property);
   }

   public SchematicBlockContext offset(Direction direction) {
      return new SchematicBlockContext(this.client, this.level, this.schematic, this.blockPos.relative(direction));
   }

   public <T extends Comparable<T>> Optional<T> getRequiredStateProperty(Property<T> property) {
      return getProperty(this.requiredState, property);
   }

   public <T extends Comparable<T>> Optional<T> getCurrentStateProperty(Property<T> property) {
      return getProperty(this.currentState, property);
   }

   public Block getRequiredBlock() {
      return this.requiredState.getBlock();
   }

   public Block getCurrentBlock() {
      return this.currentState.getBlock();
   }

   public MutableComponent getRequiredBlockName() {
      return this.requiredState.getBlock().getName();
   }

   public MutableComponent getCurrentBlockName() {
      return this.currentState.getBlock().getName();
   }

   @Generated
   @Override
   public String toString() {
      return "SchematicBlockContext(client="
         + this.client
         + ", level="
         + this.level
         + ", schematic="
         + this.schematic
         + ", blockPos="
         + this.blockPos
         + ", currentState="
         + this.currentState
         + ", requiredState="
         + this.requiredState
         + ")";
   }
}
