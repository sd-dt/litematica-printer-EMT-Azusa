package me.aleksilassila.litematica.printer.mixin_extension;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.config.options.ConfigBase;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;

public interface ConfigExtension {
   @Unique
   BooleanSupplier litematica_printer$TRUE = () -> true;
   @Unique
   BooleanSupplier litematica_printer$FALSE = () -> false;

   @Nullable
   String litematica_printer$getTranslateNameKey();

   void litematica_printer$setTranslateNameKey(@Nullable String var1);

   @Nullable
   String litematica_printer$getTranslateCommentKey();

   void litematica_printer$setTranslateCommentKey(@Nullable String var1);

   @Nullable
   BooleanSupplier litematica_printer$getVisible();

   void litematica_printer$setVisible(@Nullable BooleanSupplier var1);

   default void litematica_printer$setVisible(boolean visible) {
      this.litematica_printer$setVisible(visible ? litematica_printer$TRUE : litematica_printer$FALSE);
   }

   static BooleanSupplier litematica_printer$getVisible(ConfigExtension configExtension) {
      return configExtension.litematica_printer$getVisible();
   }

   static <T extends IConfigBase> void litematica_printer$setVisible(ConfigBase<T> config, BooleanSupplier visible) {
      if (config instanceof ConfigExtension configExtension) {
         configExtension.litematica_printer$setVisible(visible);
      }
   }

   static <T extends IConfigBase> void litematica_printer$setVisible(ConfigBase<T> config, boolean visible) {
      if (config instanceof ConfigExtension configExtension) {
         configExtension.litematica_printer$setVisible(visible);
      }
   }

   CopyOnWriteArrayList<Consumer<IConfigBase>> litematica_printer$getValueChangeCallbacks();

   default void litematica_printer$addValueChangeListener(Consumer<IConfigBase> callback) {
      if (callback != null) {
         this.litematica_printer$getValueChangeCallbacks().add(callback);
      }
   }

   default void litematica_printer$removeValueChangeListener(Consumer<IConfigBase> callback) {
      if (callback != null) {
         this.litematica_printer$getValueChangeCallbacks().remove(callback);
      }
   }

   default void litematica_printer$clearValueChangeCallbacks() {
      this.litematica_printer$getValueChangeCallbacks().clear();
   }

   default void litematica_printer$triggerValueChangeCallbacks(IConfigBase config) {
      for (Consumer<IConfigBase> callback : this.litematica_printer$getValueChangeCallbacks()) {
         try {
            if (callback != null) {
               callback.accept(config);
            }
         } catch (Exception var5) {
         }
      }
   }
}
