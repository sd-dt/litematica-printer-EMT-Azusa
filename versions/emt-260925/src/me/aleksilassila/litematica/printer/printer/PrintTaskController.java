package me.aleksilassila.litematica.printer.printer;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.handler.ClientPlayerTickManager;
import me.aleksilassila.litematica.printer.printer.action.Action;
import me.aleksilassila.litematica.printer.utils.BlockStateUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class PrintTaskController {
   public static final PrintTaskController INSTANCE = new PrintTaskController();
   private static final int WAIT_WATER_TIMEOUT_TICKS = 60;
   private final Map<Long, PrintTaskController.Stage> stages = new HashMap<>();
   private final Map<Long, Long> stageStartTicks = new HashMap<>();
   private Iterator<BlockPos> scanIterator;
   private int lastSweptY = Integer.MIN_VALUE;
   private long scanTick = -1L;
   private boolean scanPendingIncludingShulkers;
   private boolean scanPendingExcludingShulkers;
   private boolean scanComplete = true;

   private PrintTaskController() {
   }

   @Nullable
   public Action handle(SchematicBlockContext ctx) {
      if (!Configs.Print.PRINT_ICE_FOR_WATER.getBooleanValue()) {
         return null;
      } else {
         BlockState required = ctx.requiredState;
         if (!BlockStateUtils.isWaterBlock(required)) {
            return null;
         } else {
            BlockPos pos = ctx.blockPos;
            long key = pos.asLong();
            BlockState current = ctx.currentState;
            PrintTaskController.Stage stage = this.stages.getOrDefault(key, PrintTaskController.Stage.NONE);
            if (!current.getFluidState().isEmpty()) {
               this.stages.remove(key);
               this.stageStartTicks.remove(key);
               return null;
            } else if (current.is(Blocks.ICE)) {
               this.stages.put(key, PrintTaskController.Stage.BREAKING);
               return null;
            } else if (!current.isAir() && current.getFluidState().isEmpty() && !BlockStateUtils.isReplaceable(current)) {
               this.stages.remove(key);
               this.stageStartTicks.remove(key);
               return null;
            } else if (stage == PrintTaskController.Stage.BREAKING) {
               this.stages.put(key, PrintTaskController.Stage.WAITING_WATER);
               this.stageStartTicks.put(key, getClientTick());
               return null;
            } else if (stage == PrintTaskController.Stage.WAITING_WATER) {
               long start = this.stageStartTicks.getOrDefault(key, getClientTick());
               if (getClientTick() - start >= 60L) {
                  this.stages.put(key, PrintTaskController.Stage.NEED_ICE);
                  return new Action().setItem(Items.ICE);
               } else {
                  return null;
               }
            } else if (stage == PrintTaskController.Stage.ICE_PLACED) {
               return null;
            } else if (Configs.Print.PRINT_ICE_FOR_WATER_OPTIMIZED.getBooleanValue()
               && this.hasPendingOrdinaryInRange(false)) {
               return null;
            } else {
               this.stages.put(key, PrintTaskController.Stage.NEED_ICE);
               return new Action().setItem(Items.ICE);
            }
         }
      }
   }

   public boolean hasPendingOrdinaryInRange(boolean excludeShulkers) {
      this.advanceScan();
      return excludeShulkers ? this.scanPendingExcludingShulkers : this.scanPendingIncludingShulkers;
   }

   private void advanceScan() {
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.level != null && SchematicWorldHandler.getSchematicWorld() != null) {
         long tick = minecraft.level.getGameTime();
         if (tick != this.scanTick) {
            this.scanTick = tick;
            if (this.scanComplete) {
               this.scanIterator = null;
               this.scanPendingIncludingShulkers = false;
               this.scanPendingExcludingShulkers = false;
               this.scanComplete = false;
            }

            if (this.scanIterator == null) {
               AtomicReference<PrinterBox> boxRef = ClientPlayerTickManager.PRINT.getBoxRef();
               PrinterBox box = boxRef == null ? null : boxRef.get();
               if (box == null) {
                  this.scanComplete = true;
                  return;
               }

               this.scanIterator = box.iterator();
               this.lastSweptY = Integer.MIN_VALUE;
            }

            ClientLevel level = minecraft.level;
            int timeLimit = Configs.Core.ITERATION_TIME_LIMIT.getIntegerValue();
            long startTime = timeLimit > 0 ? System.nanoTime() : 0L;
            long timeLimitNanos = (long)timeLimit * 1000000L;
            int checkInterval = 10;
            int iterCount = 0;

            while (this.scanIterator.hasNext()) {
               BlockPos pos = this.scanIterator.next();
               if (pos != null) {
                  if (timeLimit > 0 && pos.getY() != this.lastSweptY && this.lastSweptY != Integer.MIN_VALUE && System.nanoTime() - startTime >= timeLimitNanos
                     )
                   {
                     return;
                  }

                  this.lastSweptY = pos.getY();
                  if (timeLimit > 0) {
                     if (++iterCount % checkInterval == 0 && System.nanoTime() - startTime >= timeLimitNanos) {
                        return;
                     }
                  }

                  if (PlayerUtils.canInteracted(pos) && LitematicaUtils.isPositionWithinRange(pos)) {
                     BlockState required = LitematicaUtils.getSchematicBlockState(pos);
                     if (required != null
                        && !required.isAir()
                        && !(required.getBlock() instanceof LiquidBlock)
                        && !BlockStateUtils.isWaterBlock(required)
                        && !BlockStateUtils.statesEqualIgnoreProperties(level.getBlockState(pos), required)) {
                        this.scanPendingIncludingShulkers = true;
                        if (!(required.getBlock() instanceof ShulkerBoxBlock)) {
                           this.scanPendingExcludingShulkers = true;
                        }

                        if (this.scanPendingExcludingShulkers && this.scanPendingIncludingShulkers) {
                           this.scanIterator = null;
                           this.scanComplete = true;
                           return;
                        }
                     }
                  }
               }
            }

            this.scanIterator = null;
            this.scanComplete = true;
         }
      } else {
         this.scanIterator = null;
         this.scanComplete = true;
         this.scanPendingIncludingShulkers = false;
         this.scanPendingExcludingShulkers = false;
      }
   }

   public boolean isBreaking(BlockPos pos) {
      return !Configs.Print.PRINT_ICE_FOR_WATER.getBooleanValue()
         ? false
         : this.stages.getOrDefault(pos.asLong(), PrintTaskController.Stage.NONE) == PrintTaskController.Stage.BREAKING;
   }

   public boolean isWaitingWater(BlockPos pos) {
      return !Configs.Print.PRINT_ICE_FOR_WATER.getBooleanValue()
         ? false
         : this.stages.getOrDefault(pos.asLong(), PrintTaskController.Stage.NONE) == PrintTaskController.Stage.WAITING_WATER;
   }

   public boolean isIcePlaced(BlockPos pos) {
      return !Configs.Print.PRINT_ICE_FOR_WATER.getBooleanValue()
         ? false
         : this.stages.getOrDefault(pos.asLong(), PrintTaskController.Stage.NONE) == PrintTaskController.Stage.ICE_PLACED;
   }

   public void onIcePlaceSent(BlockPos pos) {
      long key = pos.asLong();
      if (this.stages.getOrDefault(key, PrintTaskController.Stage.NONE) == PrintTaskController.Stage.NEED_ICE) {
         this.stages.put(key, PrintTaskController.Stage.ICE_PLACED);
      }
   }

   public void reset() {
      this.stages.clear();
      this.stageStartTicks.clear();
      this.scanIterator = null;
      this.lastSweptY = Integer.MIN_VALUE;
      this.scanTick = -1L;
      this.scanPendingIncludingShulkers = false;
      this.scanPendingExcludingShulkers = false;
      this.scanComplete = true;
   }

   private static long getClientTick() {
      Minecraft minecraft = Minecraft.getInstance();
      return minecraft.level == null ? 0L : minecraft.level.getGameTime();
   }

   private static enum Stage {
      NONE,
      NEED_ICE,
      ICE_PLACED,
      BREAKING,
      WAITING_WATER;
   }
}
