package me.aleksilassila.litematica.printer.mixin.printer.mc;

import com.google.common.collect.UnmodifiableIterator;
import java.awt.Color;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.WorkingModeType;
import me.aleksilassila.litematica.printer.go.GhastRideState;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.handler.GuiBlockInfo;
import me.aleksilassila.litematica.printer.handler.GuiDebugHandlerInfo;
import me.aleksilassila.litematica.printer.handler.handlers.GuiHandler;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.MessageUtils;
import me.aleksilassila.litematica.printer.utils.RenderUtils;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({Hud.class})
public abstract class MixinGui {
   @Unique
   private static final int DEBUG_PADDING = 4;
   @Unique
   private static final int DEBUG_LINE_HEIGHT = 12;
   @Unique
   private static final int MIN_COLUMN_WIDTH = 120;
   @Unique
   private static final int SIDE_MARGIN = 10;
   @Unique
   private static final int COLUMN_SPACING = 12;
   @Unique
   private static final int COMMON_INFO_OFFSET_Y = 10;
   @Unique
   private static final long GHAST_HINT_REFRESH_MS = 1900L;
   @Unique
   private static String lastGhastHint;
   @Unique
   private static long ghastHintRefreshAt;

   @Unique
   private static String booleanToColoredString(boolean value) {
      return value ? "§atrue" : "§cfalse";
   }

   @Unique
   private static String formatAlignedNumber(int current, int total) {
      int totalDigits = total == 0 ? 1 : String.valueOf(total).length();
      DecimalFormat formatter = new DecimalFormat(String.format("%0" + totalDigits + "d", 0));
      return formatter.format((long)current);
   }

   @Unique
   private List<String> buildHandlerDebugLines(ClientPlayerTickHandler handler, GuiBlockInfo guiInfo) {
      List<String> lines = new ArrayList<>();
      lines.add("处理类型: " + handler.getId());
      lines.add("当前位置: " + guiInfo.pos.toShortString());
      if (guiInfo.requiredState != null) {
         lines.add("投影方块: " + guiInfo.requiredState.getBlock().getName().getString());
      }

      lines.add("当前方块: " + guiInfo.currentState.getBlock().getName().getString());
      lines.add("交互范围: " + booleanToColoredString(guiInfo.interacted));
      lines.add("选区类型: " + booleanToColoredString(guiInfo.posInSelectionRange));
      lines.add("已经执行: " + booleanToColoredString(guiInfo.execute));
      int renderIndex = handler.getRenderIndex();
      int queueSize = handler.getGuiQueueSize();
      lines.add("同刻迭代(GUI): " + formatAlignedNumber(renderIndex, queueSize) + "/" + queueSize);
      return lines;
   }

   @Unique
   private void drawDebugLine(String text, int x, int y) {
      RenderUtils.drawString(text, x, y, new Color(0, 255, 255, 255), true);
   }

   @Inject(
      method = {"extractHotbarAndDecorations"},
      at = {@At("TAIL")}
   )
   private void hookRenderItemHotbar(GuiGraphicsExtractor guiGraphics, DeltaTracker deltaTracker, CallbackInfo ci) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && mc.level != null && !mc.player.isSpectator() && ConfigUtils.isPrinterEnable()) {
         float scaledWidth = (float)mc.getWindow().getGuiScaledWidth();
         float scaledHeight = (float)mc.getWindow().getGuiScaledHeight();
         RenderUtils.initGuiGraphics(guiGraphics);
         if (Configs.Core.DEBUG_OUTPUT.getBooleanValue()) {
            this.drawDebugInfo(scaledWidth, scaledHeight);
         }

         if (Configs.Core.RENDER_HUD.getBooleanValue()) {
            this.drawHudInfo(scaledWidth, scaledHeight);
         }
      }
   }

   @Unique
   private void drawDebugInfo(float scaledWidth, float scaledHeight) {
      Minecraft mc = Minecraft.getInstance();
      List<GuiDebugHandlerInfo> validHandlers = new ArrayList<>();
      int globalMaxTextWidth = 120;
      UnmodifiableIterator commonInfoBottomY = ClientPlayerTickManager.VALUES.iterator();

      while (commonInfoBottomY.hasNext()) {
         ClientPlayerTickHandler handler = (ClientPlayerTickHandler)commonInfoBottomY.next();
         GuiBlockInfo guiInfo = handler.nextGuiInfo();
         if (guiInfo != null) {
            validHandlers.add(new GuiDebugHandlerInfo(handler, guiInfo));

            for (String line : this.buildHandlerDebugLines(handler, guiInfo)) {
               String cleanLine = line.replaceAll("§[0-9a-fA-Fklmnor]", "");
               globalMaxTextWidth = Math.max(globalMaxTextWidth, mc.font.width(cleanLine));
            }
         }
      }

      if (!validHandlers.isEmpty()) {
         int commonInfoBottomYx = this.drawCommonDebugInfo(10, 10);
         int columnWidth = globalMaxTextWidth + 8;
         int maxColumnsPerSide = this.calculateMaxColumnsPerSide(scaledWidth, columnWidth);
         int availableHeight = (int)(scaledHeight - (float)commonInfoBottomYx - 10.0F - 10.0F);
         int drawnHandlers = this.drawHandlerPanels(
            validHandlers, 0, 10, commonInfoBottomYx + 10, columnWidth, maxColumnsPerSide, availableHeight, scaledHeight
         );
         if (drawnHandlers < validHandlers.size()) {
            int rightStartX = (int)(scaledWidth - 10.0F - (float)columnWidth);
            this.drawHandlerPanels(
               validHandlers, drawnHandlers, rightStartX, commonInfoBottomYx + 10, columnWidth, maxColumnsPerSide, availableHeight, scaledHeight
            );
         }
      }
   }

   @Unique
   private int calculateMaxColumnsPerSide(float scaledWidth, int columnWidth) {
      float centerAreaWidth = scaledWidth * 0.5F;
      float sideAvailableWidth = (scaledWidth - centerAreaWidth) / 2.0F - 20.0F;
      int maxColumns = Math.max(1, (int)(sideAvailableWidth / (float)(columnWidth + 12)));
      return Math.min(maxColumns, 3);
   }

   @Unique
   private int drawHandlerPanels(
      List<GuiDebugHandlerInfo> handlers, int startIndex, int startX, int startY, int columnWidth, int maxColumns, int availableHeight, float scaledHeight
   ) {
      int drawnCount = 0;
      int currentColumn = 0;
      int currentX = startX;
      int currentY = startY;

      for (int i = startIndex; i < handlers.size(); i++) {
         GuiDebugHandlerInfo handlerInfo = handlers.get(i);
         List<String> debugLines = this.buildHandlerDebugLines(handlerInfo.handler, handlerInfo.guiInfo);
         int panelHeight = debugLines.size() * 12 + 8;
         if (currentColumn >= maxColumns) {
            currentColumn = 0;
            currentX = startX;
            currentY += panelHeight + 8;
            if ((float)(currentY + panelHeight) > scaledHeight - 10.0F) {
               break;
            }
         }

         RenderUtils.fill(currentX, currentY, currentX + columnWidth, currentY + panelHeight, new Color(0, 0, 0, 50));
         int lineY = currentY + 4;

         for (String line : debugLines) {
            this.drawDebugLine(line, currentX + 4, lineY);
            lineY += 12;
         }

         drawnCount++;
         currentColumn++;
         currentX += columnWidth + 12;
         if ((float)(currentY + panelHeight) > scaledHeight - 10.0F) {
            break;
         }
      }

      return drawnCount;
   }

   @Unique
   private int drawCommonDebugInfo(int startX, int startY) {
      List<String> commonLines = new ArrayList<>();
      commonLines.add("全局Tick: " + ClientPlayerTickManager.getCurrentHandlerTime());
      commonLines.add("活跃Handler数: " + ClientPlayerTickManager.VALUES.size());
      Minecraft mc = Minecraft.getInstance();
      int maxWidth = 0;

      for (String line : commonLines) {
         String cleanLine = line.replaceAll("§[0-9a-fA-Fklmnor]", "");
         maxWidth = Math.max(maxWidth, mc.font.width(cleanLine));
      }

      int bgWidth = maxWidth + 8;
      int bgHeight = commonLines.size() * 12 + 8;
      RenderUtils.fill(startX, startY, startX + bgWidth, startY + bgHeight, new Color(0, 0, 0, 50));
      int lineY = startY + 4;

      for (String line : commonLines) {
         this.drawDebugLine(line, startX + 4, lineY);
         lineY += 12;
      }

      return startY + bgHeight;
   }

   @Unique
   private void drawHudInfo(float scaledWidth, float scaledHeight) {
      int centerX = (int)(scaledWidth / 2.0F);
      int centerY = (int)(scaledHeight / 2.0F);
      GuiHandler guiHandler = ClientPlayerTickManager.GUI;
      if (Configs.Core.LAG_CHECK.getBooleanValue() && ClientPlayerTickManager.getPacketTick() > Configs.Core.LAG_CHECK_MAX.getIntegerValue()) {
         RenderUtils.drawString("延迟过大，已暂停运行", centerX, centerY - 22, Color.ORANGE, true, true);
      }

      WorkingModeType workMode = (WorkingModeType)Configs.Core.WORK_MODE.getOptionListValue();
      if (workMode.equals(WorkingModeType.SINGLE)) {
         double progress = guiHandler.getTotalProgress().getProgress();
         RenderUtils.drawString((int)(progress * 100.0) + "%", centerX, centerY + 22, Color.WHITE, true, true);
         this.drawProgressBar(centerX, centerY + 36, 40, 6, progress, new Color(0, 0, 0, 150), new Color(0, 255, 0, 255));
      }

      if (ConfigUtils.isSingleMode()) {
         String modeName = Configs.Core.WORK_MODE_TYPE.getOptionListValue().getDisplayName();
         RenderUtils.drawString(modeName, centerX, centerY + 52, Color.WHITE, true, true);
      } else {
         HashSet<String> modeNames = new HashSet<>();
         UnmodifiableIterator nowMs = ClientPlayerTickManager.VALUES.iterator();

         while (nowMs.hasNext()) {
            ClientPlayerTickHandler handler = (ClientPlayerTickHandler)nowMs.next();
            if (!handler.getId().equals("gui") && handler.getEnableConfig() != null && handler.getEnableConfig().getBooleanValue()) {
               modeNames.add(handler.getEnableConfig().getPrettyName());
            }
         }

         RenderUtils.drawString(String.join(", ", modeNames), centerX, centerY + 52, Color.WHITE, true, true);
      }

      if (Configs.Go.GHAST_PATHFIND.getBooleanValue() && Configs.Go.PRINT_SCAN_AUTOWALK.getBooleanValue() && ConfigUtils.isPrintModeActive()) {
         String ghastHint = this.ghastHint();
         long nowMs = System.currentTimeMillis();
         if (ghastHint != null && (!ghastHint.equals(lastGhastHint) || nowMs >= ghastHintRefreshAt)) {
            lastGhastHint = ghastHint;
            ghastHintRefreshAt = nowMs + 1900L;
            MessageUtils.setOverlayMessage(Component.literal(ghastHint));
         }
      } else {
         lastGhastHint = null;
      }
   }

   @Unique
   private String ghastHint() {
      GhastRideState.Status status = GhastRideState.check(Minecraft.getInstance().player);

      return switch (status) {
         case NOT_RIDING -> "乐魂寻路：请骑乘快乐恶魂";
         case NOT_CONTROLLER -> "乐魂寻路：你不是该乐魂的操控者";
         case NO_HARNESS -> "乐魂寻路：请先给乐魂装备挽具";
         case STILL_TIMEOUT -> "乐魂寻路：乐魂被站立占用，无法操控";
         case OK -> null;
      };
   }

   @Unique
   private void drawProgressBar(int x, int y, int barWidth, int barHeight, double progress, Color bgColor, Color fgColor) {
      double clampedProgress = Math.max(0.0, Math.min(1.0, progress));
      int barXStart = x - barWidth / 2;
      int barXEnd = x + barWidth / 2;
      int barYEnd = y + barHeight;
      int filledWidth = (int)(clampedProgress * (double)barWidth);
      RenderUtils.fill(barXStart, y, barXEnd, barYEnd, bgColor);
      if (filledWidth > 0) {
         RenderUtils.fill(barXStart, y, barXStart + filledWidth, barYEnd, fgColor);
      }
   }
}
