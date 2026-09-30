package me.aleksilassila.litematica.printer;

import fi.dy.masa.malilib.event.InitializationHandler;
import me.aleksilassila.litematica.printer.go.GoCommand;
import me.aleksilassila.litematica.printer.utils.ModUtils;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

public class LitematicaPrinterMod implements ModInitializer, ClientModInitializer {
   public void onInitialize() {
   }

   public void onInitializeClient() {
      ModUtils.cleanBundledVideoTemp();
      GoCommand.register();
      InitializationHandler.getInstance().registerInitializationHandler(new InitHandler());
   }
}
