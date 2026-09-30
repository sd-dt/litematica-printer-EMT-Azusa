package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;
import me.aleksilassila.litematica.printer.printer.PrinterBox;

public enum IterationOrderType implements ConfigOptionListEntry<IterationOrderType> {
   XYZ(I18n.of("iterationOrder.xyz"), IterationOrderType.Axis.X, IterationOrderType.Axis.Y, IterationOrderType.Axis.Z),
   XZY(I18n.of("iterationOrder.xzy"), IterationOrderType.Axis.X, IterationOrderType.Axis.Z, IterationOrderType.Axis.Y),
   YXZ(I18n.of("iterationOrder.yxz"), IterationOrderType.Axis.Y, IterationOrderType.Axis.X, IterationOrderType.Axis.Z),
   YZX(I18n.of("iterationOrder.yzx"), IterationOrderType.Axis.Y, IterationOrderType.Axis.Z, IterationOrderType.Axis.X),
   ZXY(I18n.of("iterationOrder.zxy"), IterationOrderType.Axis.Z, IterationOrderType.Axis.X, IterationOrderType.Axis.Y),
   ZYX(I18n.of("iterationOrder.zyx"), IterationOrderType.Axis.Z, IterationOrderType.Axis.Y, IterationOrderType.Axis.X);

   private final I18n i18n;
   public final IterationOrderType.Axis[] axis;

   private IterationOrderType(I18n i18n, IterationOrderType.Axis... axis) {
      this.i18n = i18n;
      this.axis = axis;
   }

   @Override
   public I18n getI18n() {
      return this.i18n;
   }

   public static IterationOrderType primaryFirst(IterationOrderType base, IterationOrderType.Axis primary) {
      IterationOrderType.Axis[] target = reorder(base.axis, primary);

      for (IterationOrderType t : values()) {
         if (t.axis[0] == target[0] && t.axis[1] == target[1] && t.axis[2] == target[2]) {
            return t;
         }
      }

      return base;
   }

   private static IterationOrderType.Axis[] reorder(IterationOrderType.Axis[] base, IterationOrderType.Axis primary) {
      IterationOrderType.Axis[] result = new IterationOrderType.Axis[]{primary, null, null};
      int idx = 1;

      for (IterationOrderType.Axis a : base) {
         if (a != primary) {
            result[idx++] = a;
         }
      }

      return result;
   }

   public static enum Axis {
      X {
         @Override
         public int getCoord(PrinterBox box, int x, int y, int z) {
            return x;
         }

         @Override
         public int increment(PrinterBox box, int current) {
            return current + (box.xIncrement ? 1 : -1);
         }

         @Override
         public boolean isOverflow(PrinterBox box, int value) {
            return box.xIncrement ? value > box.maxX : value < box.minX;
         }

         @Override
         public int reset(PrinterBox box) {
            return box.xIncrement ? box.minX : box.maxX;
         }
      },
      Y {
         @Override
         public int getCoord(PrinterBox box, int x, int y, int z) {
            return y;
         }

         @Override
         public int increment(PrinterBox box, int current) {
            return current + (box.yIncrement ? 1 : -1);
         }

         @Override
         public boolean isOverflow(PrinterBox box, int value) {
            return box.yIncrement ? value > box.maxY : value < box.minY;
         }

         @Override
         public int reset(PrinterBox box) {
            return box.yIncrement ? box.minY : box.maxY;
         }
      },
      Z {
         @Override
         public int getCoord(PrinterBox box, int x, int y, int z) {
            return z;
         }

         @Override
         public int increment(PrinterBox box, int current) {
            return current + (box.zIncrement ? 1 : -1);
         }

         @Override
         public boolean isOverflow(PrinterBox box, int value) {
            return box.zIncrement ? value > box.maxZ : value < box.minZ;
         }

         @Override
         public int reset(PrinterBox box) {
            return box.zIncrement ? box.minZ : box.maxZ;
         }
      };

      public abstract int getCoord(PrinterBox var1, int var2, int var3, int var4);

      public abstract int increment(PrinterBox var1, int var2);

      public abstract boolean isOverflow(PrinterBox var1, int var2);

      public abstract int reset(PrinterBox var1);
   }
}
