package me.aleksilassila.litematica.printer.handler;

public final class GuiDebugHandlerInfo {
   public final ClientPlayerTickHandler handler;
   public final GuiBlockInfo guiInfo;

   public GuiDebugHandlerInfo(ClientPlayerTickHandler handler, GuiBlockInfo guiInfo) {
      this.handler = handler;
      this.guiInfo = guiInfo;
   }
}
