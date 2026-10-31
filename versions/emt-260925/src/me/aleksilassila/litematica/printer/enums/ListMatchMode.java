package me.aleksilassila.litematica.printer.enums;

import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.ConfigOptionListEntry;

/**
 * 黑白名单的关键词提取（匹配）方式。
 * <ul>
 *    <li>{@link #FUZZY}：包含匹配——中文显示名 / 拼音 / 注册名只要包含关键词就算命中（原有行为）；</li>
 *    <li>{@link #EXACT}：精确匹配——必须完整输入目标方块的显示名、完整拼音或完整注册名，不做模糊搜索。</li>
 * </ul>
 * 所有走 {@code PinYinSearchUtils.matchName} 的黑/白名单共用这一项。
 */
public enum ListMatchMode implements ConfigOptionListEntry<ListMatchMode> {
   FUZZY("listMatchModeType.fuzzy"),
   EXACT("listMatchModeType.exact");

   private final I18n i18n;

   private ListMatchMode(String translateKey) {
      this.i18n = I18n.of(translateKey);
   }

   @Override
   public I18n getI18n() {
      return this.i18n;
   }
}
