package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

/**
 * 「挖掘」分类专用的选区类型。
 * <p>
 * 与共享的 {@link SelectionType} 相比只多一个「平面无边界」(PLANE_UNBOUNDED)：
 * 打印 / 填充 / 流体的下拉框仍然枚举共享枚举，所以那里看不到、也选不中它。
 * 切换顺序即枚举声明顺序，故 PLANE_UNBOUNDED 放在最后一位。
 */
public enum MineSelectionType implements ConfigOptionListEntry<MineSelectionType> {
   LITEMATICA_SELECTION("selectionType.litematica.selection"),
   LITEMATICA_RENDER_LAYER("selectionType.litematica.renderLayer"),
   LITEMATICA_SELECTION_BELOW_PLAYER("selectionType.litematica.selection.belowPlayer"),
   LITEMATICA_SELECTION_ABOVE_PLAYER("selectionType.litematica.selection.abovePlayer"),
   PLANE_UNBOUNDED("selectionType.planeUnbounded");

   private final I18n i18n;

   private MineSelectionType(String translateKey) {
      this.i18n = I18n.of(translateKey);
   }

   @Override
   public I18n getI18n() {
      return this.i18n;
   }
}
