package me.aleksilassila.litematica.printer.config.builder;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.config.options.ConfigHotkey;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import fi.dy.masa.malilib.hotkeys.IHotkeyCallback;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import me.aleksilassila.litematica.printer.I18n;
import me.aleksilassila.litematica.printer.config.HotkeysCallback;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import org.jetbrains.annotations.Nullable;

public class HotkeyConfigBuilder extends BaseConfigBuilder<ConfigHotkey, HotkeyConfigBuilder> {
   private String defaultStorageString = "";
   private KeybindSettings keybindSettings = KeybindSettings.DEFAULT;
   @Nullable
   private IHotkeyCallback keybindCallback;
   @Nullable
   private ConfigOptionList bindConfig;

   public HotkeyConfigBuilder(I18n i18n) {
      super(i18n);
   }

   public HotkeyConfigBuilder(String translateKey) {
      this(I18n.of(translateKey));
   }

   public HotkeyConfigBuilder defaultStorageString(String storageString) {
      this.defaultStorageString = storageString;
      return this;
   }

   public HotkeyConfigBuilder keybindCallback(@Nullable IHotkeyCallback keybindCallback) {
      this.keybindCallback = keybindCallback;
      return this;
   }

   public HotkeyConfigBuilder bindConfig(@Nullable ConfigOptionList bindConfigOptionList) {
      this.bindConfig = bindConfigOptionList;
      return this;
   }

   public HotkeyConfigBuilder keybindSettings(KeybindSettings settings) {
      this.keybindSettings = settings;
      return this;
   }

   public ConfigHotkey build() {
      ConfigHotkey config = new ConfigHotkey(this.i18n.getNameKey(), this.defaultStorageString, this.keybindSettings, this.descKey);
      if (this.keybindCallback == null) {
         if (this.bindConfig == null) {
            this.keybindCallback = HotkeysCallback::onKeyAction;
         } else {
            this.keybindCallback = (action, key) -> this.onKeyAction(this.bindConfig);
         }
      }

      config.getKeybind().setCallback(this.keybindCallback);
      return this.buildExtension(config);
   }

   private boolean onKeyAction(ConfigOptionList config) {
      IConfigOptionListEntry cycle = config.getOptionListValue().cycle(true);
      config.setOptionListValue(cycle);
      MessageUtils.setOverlayMessage(config.getOptionListValue().getDisplayName());
      return true;
   }
}
