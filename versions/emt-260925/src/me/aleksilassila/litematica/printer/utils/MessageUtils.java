package me.aleksilassila.litematica.printer.utils;

import java.util.StringJoiner;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

public class MessageUtils {
   public static final Minecraft client = Minecraft.getInstance();
   public static final MutableComponent EMPTY = literal("");

   public static void setOverlayMessage(Component message, boolean bl) {
      client.gui.hud.setOverlayMessage(message, bl);
   }

   public static void addMessage(Component message) {
      client.gui.hud.getChat().addClientSystemMessage(message);
   }

   public static void setOverlayMessage(Component message) {
      setOverlayMessage(message, false);
   }

   public static void setOverlayMessage(String message) {
      setOverlayMessage(literal(message));
   }

   public static void addMessage(String message) {
      addMessage(literal(message));
   }

   public static MutableComponent translatable(String key) {
      return Component.translatable(key);
   }

   public static MutableComponent translatable(String key, Object... objects) {
      return Component.translatable(key, objects);
   }

   public static MutableComponent literal(String text) {
      return Component.literal(text);
   }

   public static MutableComponent nullToEmpty(@Nullable String string) {
      return string != null ? literal(string) : EMPTY;
   }

   public static String mergeComments(String delimiter, Component... customComments) {
      StringJoiner joiner = new StringJoiner(delimiter);

      for (Component comment : customComments) {
         if (comment != null) {
            joiner.add(comment.getString());
         }
      }

      return joiner.toString();
   }
}
