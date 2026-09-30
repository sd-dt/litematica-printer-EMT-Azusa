package me.aleksilassila.litematica.printer.printer;

import java.util.Iterator;
import java.util.Objects;
import me.aleksilassila.litematica.printer.enums.IterationOrderType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import org.jetbrains.annotations.NotNull;

public class PrinterBox implements Iterable<BlockPos> {
   public static final Minecraft client = Minecraft.getInstance();
   public final int minX;
   public final int minY;
   public final int minZ;
   public final int maxX;
   public final int maxY;
   public final int maxZ;
   public boolean yIncrement = true;
   public boolean xIncrement = true;
   public boolean zIncrement = true;
   public IterationOrderType iterationMode = IterationOrderType.XZY;

   public PrinterBox(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
      this.minX = Math.min(minX, maxX);
      this.minZ = Math.min(minZ, maxZ);
      this.maxX = Math.max(minX, maxX);
      this.maxZ = Math.max(minZ, maxZ);
      int rawMinY = Math.min(minY, maxY);
      int rawMaxY = Math.max(minY, maxY);
      if (client.level != null) {
         this.minY = Math.max(client.level.getMinY(), rawMinY);
         this.maxY = Math.min(client.level.getMaxY(), rawMaxY);
      } else {
         this.minY = rawMinY;
         this.maxY = rawMaxY;
      }
   }

   public PrinterBox(BlockPos pos) {
      this(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
   }

   public PrinterBox(Vec3i pos1, Vec3i pos2) {
      this(pos1.getX(), pos1.getY(), pos1.getZ(), pos2.getX(), pos2.getY(), pos2.getZ());
   }

   public boolean contains(int x, int y, int z) {
      return x >= this.minX && x <= this.maxX && y >= this.minY && y <= this.maxY && z >= this.minZ && z <= this.maxZ;
   }

   public boolean contains(Vec3i vec3i) {
      return vec3i.getX() >= this.minX
         && vec3i.getX() <= this.maxX
         && vec3i.getY() >= this.minY
         && vec3i.getY() <= this.maxY
         && vec3i.getZ() >= this.minZ
         && vec3i.getZ() <= this.maxZ;
   }

   public PrinterBox expand(int expandX, int expandY, int expandZ) {
      int minX = this.minX - expandX;
      int minZ = this.minZ - expandZ;
      int maxX = this.maxX + expandX;
      int maxZ = this.maxZ + expandZ;
      int minY = this.minY - expandY;
      int maxY = this.maxY + expandY;
      if (client.level != null) {
         minY = Math.max(client.level.getMinY(), minY);
         maxY = Math.min(client.level.getMaxY(), maxY);
      }

      return new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
   }

   public PrinterBox expand(int value) {
      return this.expand(value, value, value);
   }

   @NotNull
   @Override
   public Iterator<BlockPos> iterator() {
      return new PrinterBox.BoxIterator();
   }

   @Override
   public boolean equals(Object o) {
      if (o != null && this.getClass() == o.getClass()) {
         PrinterBox box = (PrinterBox)o;
         return this.minX == box.minX
            && this.minY == box.minY
            && this.minZ == box.minZ
            && this.maxX == box.maxX
            && this.maxY == box.maxY
            && this.maxZ == box.maxZ;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.minX, this.minY, this.minZ, this.maxX, this.maxY, this.maxZ);
   }

   private class BoxIterator implements Iterator<BlockPos> {
      public BlockPos currPos;

      private BoxIterator() {
         Objects.requireNonNull(PrinterBox.this);
         super();
      }

      @Override
      public boolean hasNext() {
         if (this.currPos == null) {
            return true;
         } else {
            int x = this.currPos.getX();
            int y = this.currPos.getY();
            int z = this.currPos.getZ();
            int targetX = PrinterBox.this.xIncrement ? PrinterBox.this.maxX : PrinterBox.this.minX;
            int targetY = PrinterBox.this.yIncrement ? PrinterBox.this.maxY : PrinterBox.this.minY;
            int targetZ = PrinterBox.this.zIncrement ? PrinterBox.this.maxZ : PrinterBox.this.minZ;
            return x != targetX || y != targetY || z != targetZ;
         }
      }

      public BlockPos next() {
         if (this.currPos == null) {
            this.currPos = new BlockPos(
               IterationOrderType.Axis.X.reset(PrinterBox.this),
               IterationOrderType.Axis.Y.reset(PrinterBox.this),
               IterationOrderType.Axis.Z.reset(PrinterBox.this)
            );
            return this.currPos;
         } else {
            int x = this.currPos.getX();
            int y = this.currPos.getY();
            int z = this.currPos.getZ();

            label33:
            for (IterationOrderType.Axis axis : PrinterBox.this.iterationMode.axis) {
               int newValue = axis.increment(PrinterBox.this, axis.getCoord(PrinterBox.this, x, y, z));
               if (!axis.isOverflow(PrinterBox.this, newValue)) {
                  switch (axis) {
                     case X:
                        x = newValue;
                        break label33;
                     case Y:
                        y = newValue;
                        break label33;
                     case Z:
                        z = newValue;
                     default:
                        break label33;
                  }
               }

               switch (axis) {
                  case X:
                     x = axis.reset(PrinterBox.this);
                     break;
                  case Y:
                     y = axis.reset(PrinterBox.this);
                     break;
                  case Z:
                     z = axis.reset(PrinterBox.this);
               }
            }

            this.currPos = new BlockPos(x, y, z);
            return this.currPos;
         }
      }
   }
}
