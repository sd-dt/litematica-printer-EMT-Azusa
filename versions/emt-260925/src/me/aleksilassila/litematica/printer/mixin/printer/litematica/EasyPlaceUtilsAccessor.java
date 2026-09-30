package me.aleksilassila.litematica.printer.mixin.printer.litematica;

import fi.dy.masa.litematica.util.EasyPlaceUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(
   value = {EasyPlaceUtils.class},
   remap = false
)
public interface EasyPlaceUtilsAccessor {
   @Invoker("setEasyPlaceLastPickBlockTime")
   static void callSetEasyPlaceLastPickBlockTime() {
      throw new UnsupportedOperationException();
   }
}
