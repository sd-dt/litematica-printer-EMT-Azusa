package me.aleksilassila.litematica.printer.handler;

import fi.dy.masa.litematica.world.WorldSchematic;
import java.util.Objects;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class GuiBlockInfo {
   public final ClientLevel level;
   public final Identifier world;
   @Nullable
   public final WorldSchematic schematic;
   public final BlockPos pos;
   public final BlockState currentState;
   @Nullable
   public final BlockState requiredState;
   @Nullable
   public SchematicBlockContext context;
   public boolean interacted = false;
   public boolean execute = false;
   public boolean posInSelectionRange = false;

   public GuiBlockInfo(ClientLevel level, @Nullable WorldSchematic schematic, BlockPos pos) {
      this.level = level;
      this.world = level.dimension().identifier();
      this.schematic = schematic;
      this.pos = pos;
      this.currentState = level.getBlockState(pos);
      if (schematic == null) {
         this.requiredState = null;
      } else {
         this.requiredState = LitematicaUtils.getSchematicBlockState(pos.above());
      }
   }

   @Override
   public boolean equals(Object o) {
      if (o != null && this.getClass() == o.getClass()) {
         GuiBlockInfo that = (GuiBlockInfo)o;
         return Objects.equals(this.level, that.level)
            && Objects.equals(this.world, that.world)
            && Objects.equals(this.pos, that.pos)
            && Objects.equals(this.currentState, that.currentState);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.level, this.world, this.pos, this.currentState);
   }

   @Override
   public String toString() {
      return "GuiBlockInfo{level=" + this.level + ", world=" + this.world + ", pos=" + this.pos + ", state=" + this.currentState + "}";
   }
}
