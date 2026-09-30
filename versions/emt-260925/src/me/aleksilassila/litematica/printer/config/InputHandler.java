package me.aleksilassila.litematica.printer.config;

import com.google.common.collect.UnmodifiableIterator;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybindManager;
import fi.dy.masa.malilib.hotkeys.IKeybindProvider;
import fi.dy.masa.malilib.hotkeys.IKeyboardInputHandler;

public class InputHandler implements IKeybindProvider, IKeyboardInputHandler {
   private static final InputHandler INSTANCE = new InputHandler();

   public void addKeysToMap(IKeybindManager manager) {
      UnmodifiableIterator var2 = Configs.HOTKEYS.iterator();

      while (var2.hasNext()) {
         IHotkey hotkey = (IHotkey)var2.next();
         manager.addKeybindToMap(hotkey.getKeybind());
      }
   }

   public void addHotkeys(IKeybindManager manager) {
      manager.addHotkeysForCategory("litematica_printer", "hotkeys", Configs.HOTKEYS);
   }

   public static InputHandler getInstance() {
      return INSTANCE;
   }
}
