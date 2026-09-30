package me.aleksilassila.litematica.printer.gui;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.UnmodifiableIterator;
import com.google.common.collect.ImmutableList.Builder;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.IConfigResettable;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptions;
import fi.dy.masa.malilib.gui.widgets.WidgetListConfigOptionsBase;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin_extension.ConfigExtension;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jetbrains.annotations.Nullable;

public class ConfigUi extends GuiConfigsBase {
   private static ConfigUi.Tab tab = ConfigUi.Tab.CORE;

   public ConfigUi(@Nullable Screen parent) {
      super(10, 50, "litematica_printer", parent, "Litematica Printer " + I18n.PRINTER_TAGLINE.getName().getString(), new Object[0]);
   }

   public ConfigUi() {
      this(Minecraft.getInstance().gui.screen());
   }

   public static void refresh() {
      if (Reference.MINECRAFT.gui.screen() instanceof ConfigUi gui) {
         gui.initGui();
      }
   }

   public void initGui() {
      super.initGui();
      this.clearOptions();
      int x = 10;
      int y = 26;

      for (ConfigUi.Tab tab : ConfigUi.Tab.values()) {
         x += this.createButton(x, y, -1, tab);
      }
   }

   public void reset() {
      this.reCreateListWidget();
      ((WidgetListConfigOptions)Objects.requireNonNull((WidgetListConfigOptions)this.getListWidget())).resetScrollbarPosition();
      this.initGui();
   }

   private int createButton(int x, int y, int width, ConfigUi.Tab tab) {
      ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getName(), new String[]{tab.getComment()});
      button.setEnabled(ConfigUi.tab != tab);
      this.addButton(button, new ConfigUi.ButtonListener(tab, this));
      return button.getWidth() + 2;
   }

   protected WidgetListConfigOptions createListWidget(int listX, int listY) {
      return (WidgetListConfigOptions)(tab == ConfigUi.Tab.DANGER
         ? new ConfigUi.DangerList(listX, listY, this.getBrowserWidth(), this.getBrowserHeight(), this.getConfigWidth(), 0.0F, this.useKeybindSearch(), this)
         : super.createListWidget(listX, listY));
   }

   public List<ConfigOptionWrapper> getConfigs() {
      Builder<ConfigOptionWrapper> builder = ImmutableList.builder();
      UnmodifiableIterator var2 = tab.getConfigs().iterator();

      while (var2.hasNext()) {
         IConfigBase config = (IConfigBase)var2.next();
         if (config instanceof ConfigExtension) {
            ConfigExtension extension = (ConfigExtension)config;
            BooleanSupplier visible = extension.litematica_printer$getVisible();
            if (visible != null && visible.getAsBoolean()) {
               builder.add(new ConfigOptionWrapper(config));
            }
         }
      }

      return builder.build();
   }

   public static record ButtonListener(ConfigUi.Tab tab, ConfigUi parent) implements IButtonActionListener {
      public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
         ConfigUi.tab = this.tab;
         this.parent.reset();
      }
   }

   public static class DangerList extends WidgetListConfigOptions {
      public DangerList(int x, int y, int width, int height, int configWidth, float zLevel, boolean useKeybindSearch, GuiConfigsBase parent) {
         super(x, y, width, height, configWidth, zLevel, useKeybindSearch, parent);
      }

      protected WidgetConfigOption createListEntryWidget(int x, int y, int listIndex, boolean isOdd, ConfigOptionWrapper wrapper) {
         return new ConfigUi.DangerWidget(
            x, y, this.browserEntryWidth, this.browserEntryHeight, this.maxLabelWidth, this.configWidth, wrapper, listIndex, this.parent, this
         );
      }
   }

   public static class DangerWidget extends WidgetConfigOption {
      public DangerWidget(
         int x,
         int y,
         int width,
         int height,
         int labelWidth,
         int configWidth,
         ConfigOptionWrapper wrapper,
         int listIndex,
         IKeybindConfigGui host,
         WidgetListConfigOptionsBase<?, ?> parent
      ) {
         super(x, y, width, height, labelWidth, configWidth, wrapper, listIndex, host, parent);
      }

      protected void addConfigButtonEntry(int xReset, int yReset, IConfigResettable config, ButtonBase optionButton) {
         Runnable action;
         String textKey;
         if (config == Configs.Danger.SIMIAO) {
            action = ModUtils::crashNow;
            textKey = "simiao.button";
         } else if (config == Configs.Danger.DONOTCLICK_B) {
            action = ModUtils::playBundledVideo;
            textKey = "donotclickA.button";
         } else {
            action = ModUtils::openTrollVideoUrl;
            textKey = "donotclickA.button";
         }

         ButtonGeneric button = new ButtonGeneric(
            optionButton.getX(),
            optionButton.getY(),
            optionButton.getWidth(),
            optionButton.getHeight(),
            I18n.of(textKey).getConfigName().getString(),
            new String[0]
         );
         this.addButton(button, (buttonBase, mouseButton) -> action.run());
      }
   }

   public static enum Tab {
      CORE(I18n.of("category.core")),
      HOTKEYS(I18n.of("category.hotkeys")),
      PRINT(I18n.of("category.print")),
      EXCAVATE(I18n.of("category.mine")),
      FILL(I18n.of("category.fill")),
      BEDROCK(I18n.of("category.bedrock")),
      FLUID(I18n.of("category.fluid")),
      SPECIAL(I18n.of("category.special")),
      GO(I18n.of("category.go")),
      DANGER(I18n.of("category.danger"));

      private final I18n i18n;

      private Tab(I18n i18n) {
         this.i18n = i18n;
      }

      public String getName() {
         return this.i18n.getConfigName().getString();
      }

      public String getComment() {
         return this.i18n.getConfigDesc().getString();
      }

      public ImmutableList<IConfigBase> getConfigs() {
         return switch (this) {
            case CORE -> Configs.Core.OPTIONS;
            case HOTKEYS -> Configs.Hotkeys.OPTIONS;
            case PRINT -> Configs.Print.OPTIONS;
            case EXCAVATE -> Configs.Mine.OPTIONS;
            case FILL -> Configs.Fill.OPTIONS;
            case BEDROCK -> Configs.Bedrock.OPTIONS;
            case FLUID -> Configs.Fluid.OPTIONS;
            case SPECIAL -> Configs.Special.OPTIONS;
            case GO -> Configs.Go.OPTIONS;
            case DANGER -> Configs.Danger.OPTIONS;
         };
      }
   }
}
