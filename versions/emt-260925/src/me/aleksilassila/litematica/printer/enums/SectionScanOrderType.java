package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

public enum SectionScanOrderType implements ConfigOptionListEntry<SectionScanOrderType> {
   XYZ(I18n.of("sectionScanOrder.xyz"), SectionScanOrderType.Axis.X, SectionScanOrderType.Axis.Y, SectionScanOrderType.Axis.Z),
   XZY(I18n.of("sectionScanOrder.xzy"), SectionScanOrderType.Axis.X, SectionScanOrderType.Axis.Z, SectionScanOrderType.Axis.Y),
   YXZ(I18n.of("sectionScanOrder.yxz"), SectionScanOrderType.Axis.Y, SectionScanOrderType.Axis.X, SectionScanOrderType.Axis.Z),
   YZX(I18n.of("sectionScanOrder.yzx"), SectionScanOrderType.Axis.Y, SectionScanOrderType.Axis.Z, SectionScanOrderType.Axis.X),
   ZXY(I18n.of("sectionScanOrder.zxy"), SectionScanOrderType.Axis.Z, SectionScanOrderType.Axis.X, SectionScanOrderType.Axis.Y),
   ZYX(I18n.of("sectionScanOrder.zyx"), SectionScanOrderType.Axis.Z, SectionScanOrderType.Axis.Y, SectionScanOrderType.Axis.X);

   private final I18n i18n;
   public final SectionScanOrderType.Axis[] axis;

   private SectionScanOrderType(I18n i18n, SectionScanOrderType.Axis... axis) {
      this.i18n = i18n;
      this.axis = axis;
   }

   @Override
   public I18n getI18n() {
      return this.i18n;
   }

   public static enum Axis {
      X(1, 0, 0),
      Y(0, 1, 0),
      Z(0, 0, 1);

      public final int dx;
      public final int dy;
      public final int dz;

      private Axis(int dx, int dy, int dz) {
         this.dx = dx;
         this.dy = dy;
         this.dz = dz;
      }
   }
}
