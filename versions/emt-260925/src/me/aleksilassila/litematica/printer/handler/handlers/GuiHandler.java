package me.aleksilassila.litematica.printer.handler.handlers;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.config.options.ConfigBase;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Generated;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.BlockPrintState;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickHandler;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LiquidBlock;

public class GuiHandler extends ClientPlayerTickHandler {
   public static final String NAME = "gui";
   private final GuiHandler.Progress totalProgress = new GuiHandler.Progress(Configs.Core.PRINT);
   private final GuiHandler.Progress printProgress = new GuiHandler.Progress(Configs.Core.PRINT);
   private final GuiHandler.Progress fluidProgress = new GuiHandler.Progress(Configs.Core.FLUID);
   private final GuiHandler.Progress fillProgress = new GuiHandler.Progress(Configs.Core.FILL);
   private final GuiHandler.Progress mineProgress = new GuiHandler.Progress(Configs.Core.MINE);

   public GuiHandler() {
      super("gui", null, Configs.Core.RENDER_HUD, null, true);
   }

   @Override
   protected boolean needsRangeCheck() {
      return false;
   }

   @Override
   public boolean canProcessPos(BlockPos pos) {
      return super.canProcessPos(pos);
   }

   @Override
   protected void executeIteration(BlockPos blockPos, AtomicReference<Boolean> skipIteration) {
      if (ConfigUtils.isPrintMode()) {
         WorldSchematic schematic = SchematicWorldHandler.getSchematicWorld();
         if (schematic != null) {
            if (LitematicaUtils.getSchematicBlockState(blockPos) == null) {
               return;
            }

            SchematicBlockContext context = new SchematicBlockContext(client, this.level, schematic, blockPos);
            if (!context.requiredState.isAir()) {
               if (BlockPrintState.get(context) == BlockPrintState.CORRECT) {
                  this.printProgress.finished++;
                  this.totalProgress.finished++;
               }

               this.printProgress.total++;
               this.totalProgress.total++;
            }
         }
      }

      if (isFluidMode()) {
         if (!(this.level.getBlockState(blockPos).getBlock() instanceof LiquidBlock)) {
            this.fluidProgress.finished++;
            this.totalProgress.finished++;
         }

         this.fluidProgress.total++;
         this.totalProgress.total++;
      }

      if (isFillMode()) {
         if (Arrays.asList(ClientPlayerTickManager.FILL.getFillModeItemList()).contains(this.level.getBlockState(blockPos).getBlock().asItem())) {
            this.fillProgress.finished++;
            this.totalProgress.finished++;
         }

         this.fillProgress.total++;
         this.totalProgress.total++;
      }

      if (isMineMode()) {
         if (this.level.getBlockState(blockPos).isAir()) {
            this.mineProgress.finished++;
            this.totalProgress.finished++;
         }

         this.mineProgress.total++;
         this.totalProgress.total++;
      }

      this.printProgress.calculateProgress();
      this.fluidProgress.calculateProgress();
      this.fillProgress.calculateProgress();
      this.mineProgress.calculateProgress();
      this.totalProgress.calculateProgress();
   }

   @Override
   protected void stopIteration(boolean interrupt) {
      if (!interrupt) {
         this.totalProgress.reset();
         this.printProgress.reset();
         this.fluidProgress.reset();
         this.fillProgress.reset();
         this.mineProgress.reset();
      }
   }

   @Generated
   public GuiHandler.Progress getTotalProgress() {
      return this.totalProgress;
   }

   @Generated
   public GuiHandler.Progress getPrintProgress() {
      return this.printProgress;
   }

   @Generated
   public GuiHandler.Progress getFluidProgress() {
      return this.fluidProgress;
   }

   @Generated
   public GuiHandler.Progress getFillProgress() {
      return this.fillProgress;
   }

   @Generated
   public GuiHandler.Progress getMineProgress() {
      return this.mineProgress;
   }

   public static class Progress {
      private final ConfigBase<?> config;
      private long total;
      private long finished;
      private double progress;
      private double lastProgress;

      public Progress(ConfigBase<?> config) {
         this.config = config;
         this.total = 0L;
         this.finished = 0L;
         this.progress = 0.0;
      }

      public double getProgress() {
         return this.progress <= 0.0 ? this.lastProgress : this.progress;
      }

      public void calculateProgress() {
         this.progress = this.total < 1L ? this.lastProgress : (double)((float)this.finished / (float)this.total);
         this.lastProgress = this.progress;
      }

      public void reset() {
         this.total = 0L;
         this.finished = 0L;
         this.progress = 0.0;
      }

      @Generated
      public ConfigBase<?> getConfig() {
         return this.config;
      }

      @Generated
      public long getTotal() {
         return this.total;
      }

      @Generated
      public long getFinished() {
         return this.finished;
      }

      @Generated
      public double getLastProgress() {
         return this.lastProgress;
      }
   }
}
