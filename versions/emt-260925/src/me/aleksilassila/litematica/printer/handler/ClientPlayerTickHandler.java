package me.aleksilassila.litematica.printer.handler;

import fi.dy.masa.litematica.world.SchematicWorldHandler;
import fi.dy.masa.malilib.config.options.ConfigBoolean;
import fi.dy.masa.malilib.config.options.ConfigOptionList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import lombok.Generated;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.IterationOrderType;
import me.aleksilassila.litematica.printer.enums.PrintModeType;
import me.aleksilassila.litematica.printer.enums.WorkingModeType;
import me.aleksilassila.litematica.printer.go.GhastFlyer;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import me.aleksilassila.litematica.printer.printer.BlockPosCooldownManager;
import me.aleksilassila.litematica.printer.printer.PrinterBox;
import me.aleksilassila.litematica.printer.printer.SchematicStateCache;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.LitematicaUtils;
import me.aleksilassila.litematica.printer.utils.PlayerUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;
import org.jetbrains.annotations.Nullable;

public abstract class ClientPlayerTickHandler extends ConfigUtils {
   @Nullable
   public final AtomicReference<PrinterBox> boxRef;
   private final String id;
   @Nullable
   private final PrintModeType printMode;
   @Nullable
   private final ConfigBoolean enableConfig;
   @Nullable
   private final ConfigOptionList selectionType;
   private final AtomicReference<Boolean> skipIteration = new AtomicReference<>(false);
   private final Queue<GuiBlockInfo> guiQueue = new ConcurrentLinkedQueue<>();
   private Iterator<BlockPos> cachedIterator = null;
   private int lastSweptY = Integer.MIN_VALUE;
   private int expandRange = -1;
   private static final int MAX_IDLE_BACKOFF_TICKS = 10;
   private static final double REBUILD_MOVE_DISTANCE = 0.4;
   private boolean playerMovedThisTick;
   private int idleBackoffTicks = 1;
   private long nextScanAllowedAt = -1L;
   private long lastSeenCacheRevision = -1L;
   private final Map<String, String> cooldownTypeCache = new HashMap<>();
   protected Minecraft mc;
   protected ClientLevel level;
   protected LocalPlayer player;
   protected ClientPacketListener connection;
   protected MultiPlayerGameMode gameMode;
   protected GameType gameType;
   @Nullable
   protected HitResult hitResult;
   @Nullable
   protected BlockHitResult blockHitResult;
   @Nullable
   private PrinterBox lastBox;
   @Nullable
   private BlockPos lastPos;
   @Nullable
   private BlockPos prevPlayerBlockPos = null;
   private boolean lastExecuteConsumedQuota = true;
   private long lastTickTime = -1L;
   private int renderIndex = 0;
   private int guiCacheTicks;

   protected ClientPlayerTickHandler(
      String id, @Nullable PrintModeType printMode, @Nullable ConfigBoolean enableConfig, @Nullable ConfigOptionList selectionType, boolean useBox
   ) {
      this.id = id;
      this.printMode = printMode;
      this.enableConfig = enableConfig;
      this.selectionType = selectionType;
      this.boxRef = useBox ? new AtomicReference<>() : null;
      this.updateVariables();
   }

   protected void updateVariables() {
      this.mc = Minecraft.getInstance();
      this.level = this.mc.level;
      this.player = this.mc.player;
      this.connection = this.mc.getConnection();
      this.gameMode = this.mc.gameMode;
      this.gameType = this.gameMode == null ? null : this.gameMode.getPlayerMode();
      this.hitResult = this.mc.hitResult;
      this.blockHitResult = this.hitResult != null && this.hitResult.getType() == Type.BLOCK ? (BlockHitResult)this.hitResult : null;
   }

   public void tick() {
      if (this.guiCacheTicks > 0) {
         this.guiCacheTicks--;
      } else {
         this.guiQueue.clear();
         this.renderIndex = 0;
      }

      int tickInterval = this.getTickInterval();
      if (tickInterval > 0) {
         long currentTickTime = ClientPlayerTickManager.getCurrentHandlerTime();
         if (this.lastTickTime != -1L && currentTickTime - this.lastTickTime < (long)tickInterval) {
            return;
         }

         this.lastTickTime = currentTickTime;
      }

      if (!isPrinterEnable()) {
         this.lastPos = null;
      } else if (!this.isConfigAllowed()) {
         this.lastPos = null;
      } else if (GhastFlyer.isEscapingTier2()) {
         this.lastPos = null;
      } else {
         this.updateVariables();
         if (this.mc != null && this.level != null && this.player != null && this.connection != null && this.gameMode != null && this.gameType != null) {
            this.updateBox();
            if (!this.idleBackoffEnabled() || !this.shouldSkipForIdleBackoff()) {
               this.preprocess();
               if (!this.iterateBlocks()) {
                  this.lastPos = null;
               }
            }
         } else {
            this.lastPos = null;
         }
      }
   }

   private void updateBox() {
      if (this.boxRef != null) {
         BlockPos eyePos = new BlockPos(
            new Vec3i((int)Math.round(this.player.getX()), (int)Math.round(this.player.getEyeY()), (int)Math.round(this.player.getZ()))
         );
         PrinterBox box = this.boxRef.get();
         int currentRange = Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue() ? (int)PlayerUtils.getInteractionRange(5.0) : getWorkRange();
         boolean fastDirectionalPrint = Configs.Print.PRINT_FAST_DIRECTIONAL_PLACEMENT.getBooleanValue() && this.getPrintMode() == PrintModeType.PRINTER;
         boolean adaptive = Configs.Core.MOVE_ADAPTIVE_ITERATION.getBooleanValue() && this.getPrintMode() == PrintModeType.MINE || fastDirectionalPrint;
         if (fastDirectionalPrint && this.player.getDeltaMovement().length() * 20.0 < 14.0) {
            adaptive = false;
         }

         int dominantAxis = -1;
         int dominantSign = 1;
         if (adaptive && this.prevPlayerBlockPos != null) {
            int dx = eyePos.getX() - this.prevPlayerBlockPos.getX();
            int dy = eyePos.getY() - this.prevPlayerBlockPos.getY();
            int dz = eyePos.getZ() - this.prevPlayerBlockPos.getZ();
            int adx = Math.abs(dx);
            int ady = Math.abs(dy);
            int adz = Math.abs(dz);
            if (adx >= ady && adx >= adz) {
               dominantAxis = 0;
               dominantSign = dx >= 0 ? 1 : -1;
            } else if (ady >= adz) {
               dominantAxis = 1;
               dominantSign = dy >= 0 ? 1 : -1;
            } else {
               dominantAxis = 2;
               dominantSign = dz >= 0 ? 1 : -1;
            }

            if (adx == 0 && ady == 0 && adz == 0) {
               dominantAxis = -1;
            }
         }

         if (fastDirectionalPrint && adaptive && dominantAxis < 0) {
            double dxMotion = this.player.getDeltaMovement().x;
            double dyMotion = this.player.getDeltaMovement().y;
            double dzMotion = this.player.getDeltaMovement().z;
            double axMotion = Math.abs(dxMotion);
            double ayMotion = Math.abs(dyMotion);
            double azMotion = Math.abs(dzMotion);
            if (axMotion >= ayMotion && axMotion >= azMotion) {
               dominantAxis = 0;
               dominantSign = dxMotion >= 0.0 ? 1 : -1;
            } else if (ayMotion >= azMotion) {
               dominantAxis = 1;
               dominantSign = dyMotion >= 0.0 ? 1 : -1;
            } else {
               dominantAxis = 2;
               dominantSign = dzMotion >= 0.0 ? 1 : -1;
            }
         }

         this.playerMovedThisTick = this.prevPlayerBlockPos != null && !this.prevPlayerBlockPos.equals(eyePos);
         this.prevPlayerBlockPos = eyePos;
         boolean needRebuild = box == null
            || !box.equals(this.lastBox)
            || this.lastPos == null
            || this.expandRange != currentRange
            || !this.lastPos.closerThan(eyePos, 0.4);
         if (adaptive && dominantAxis >= 0 && this.lastPos != null) {
            int moved = dominantAxis == 0
               ? eyePos.getX() - this.lastPos.getX()
               : (dominantAxis == 1 ? eyePos.getY() - this.lastPos.getY() : eyePos.getZ() - this.lastPos.getZ());
            if ((double)Math.abs(moved) > (double)currentRange * 0.5) {
               needRebuild = true;
            }
         }

         if (needRebuild) {
            this.lastPos = eyePos;
            this.expandRange = currentRange;
            if (adaptive && dominantAxis >= 0 && Configs.Core.MOTION_AHEAD.getIntegerValue() > 0) {
               box = this.buildMotionBox(eyePos, this.expandRange, dominantAxis, dominantSign, Configs.Core.MOTION_AHEAD.getIntegerValue());
            } else {
               box = new PrinterBox(eyePos).expand(this.expandRange, this.expandRange, this.expandRange);
            }

            this.lastBox = box;
            this.boxRef.set(box);
            box.iterationMode = (IterationOrderType)Configs.Core.ITERATION_ORDER.getOptionListValue();
            box.xIncrement = !Configs.Core.X_REVERSE.getBooleanValue();
            box.yIncrement = !Configs.Core.Y_REVERSE.getBooleanValue();
            box.zIncrement = !Configs.Core.Z_REVERSE.getBooleanValue();
            if (adaptive && dominantAxis >= 0) {
               IterationOrderType.Axis primary = dominantAxis == 0
                  ? IterationOrderType.Axis.X
                  : (dominantAxis == 1 ? IterationOrderType.Axis.Y : IterationOrderType.Axis.Z);
               box.iterationMode = IterationOrderType.primaryFirst(box.iterationMode, primary);
               boolean fromFront = dominantSign <= 0;
               if (dominantAxis == 0) {
                  box.xIncrement = fromFront;
               } else if (dominantAxis == 1) {
                  box.yIncrement = fromFront;
               } else {
                  box.zIncrement = fromFront;
               }
            }

            this.cachedIterator = null;
            this.idleBackoffTicks = 1;
            this.nextScanAllowedAt = -1L;
         }
      }
   }

   private PrinterBox buildMotionBox(BlockPos eye, int range, int dominantAxis, int sign, int ahead) {
      int xe = eye.getX();
      int ye = eye.getY();
      int ze = eye.getZ();
      int minX = xe - range;
      int maxX = xe + range;
      int minY = ye - range;
      int maxY = ye + range;
      int minZ = ze - range;
      int maxZ = ze + range;
      if (dominantAxis == 0) {
         if (sign > 0) {
            maxX += ahead;
         } else {
            minX -= ahead;
         }
      } else if (dominantAxis == 1) {
         if (sign > 0) {
            maxY += ahead;
         } else {
            minY -= ahead;
         }
      } else if (sign > 0) {
         maxZ += ahead;
      } else {
         minZ -= ahead;
      }

      return new PrinterBox(minX, minY, minZ, maxX, maxY, maxZ);
   }

   private boolean iterateBlocks() {
      if (this.boxRef != null && this.canExecute()) {
         PrinterBox box = this.boxRef.get();
         if (box != null && this.canIterate()) {
            if (this.cachedIterator == null) {
               this.cachedIterator = box.iterator();
               this.lastSweptY = Integer.MIN_VALUE;
            }

            int maxExecs = this.getMaxExecutions();
            int timeLimit = this.getIterationTimeLimit();
            boolean debugMode = Configs.Core.DEBUG_OUTPUT.getBooleanValue();
            boolean needRangeCheck = this.needsRangeCheck();
            boolean isSchematic = this.isSchematicHandler();
            long startTime = timeLimit > 0 ? System.nanoTime() : 0L;
            long timeLimitNanos = (long)timeLimit * 1000000L;
            int checkInterval = 10;
            int iterCount = 0;
            this.skipIteration.set(false);
            this.guiQueue.clear();
            this.renderIndex = 0;
            int execCount = this.processFastRetry(maxExecs, this.skipIteration);
            if (this.skipIteration.get()) {
               this.stopIteration(true);
               return true;
            } else {
               int workCandidates = execCount;
               if (maxExecs <= 0 || execCount < maxExecs) {
                  int targeted = this.processTargetedScan(maxExecs <= 0 ? -1 : maxExecs - execCount, this.skipIteration);
                  if (targeted > 0) {
                     execCount += targeted;
                     workCandidates += targeted;
                  }

                  if (this.skipIteration.get()) {
                     this.stopIteration(true);
                     return true;
                  }
               }

               if (maxExecs > 0 && execCount >= maxExecs) {
                  this.stopIteration(true);
                  return true;
               } else {
                  while (this.cachedIterator.hasNext()) {
                     if (!this.skipIteration.get() && !ActionManager.INSTANCE.needWaitModifyLook) {
                        BlockPos pos = this.cachedIterator.next();
                        if (pos == null) {
                           continue;
                        }

                        if (timeLimit > 0
                           && pos.getY() != this.lastSweptY
                           && this.lastSweptY != Integer.MIN_VALUE
                           && System.nanoTime() - startTime >= timeLimitNanos) {
                           this.stopIteration(true);
                           return true;
                        }

                        this.lastSweptY = pos.getY();
                        if (timeLimit > 0) {
                           if (++iterCount % checkInterval == 0 && System.nanoTime() - startTime >= timeLimitNanos) {
                              this.stopIteration(true);
                              return true;
                           }
                        }

                        if (!PlayerUtils.canInteracted(pos)
                           || needRangeCheck
                              && (
                                 !(this.selectionType != null
                                       && this.selectionType.getOptionListValue()
                                          == me.aleksilassila.litematica.printer.enums.MineSelectionType.PLANE_UNBOUNDED)
                                    && (isSchematic ? !LitematicaUtils.isSchematicBlock(pos) : !LitematicaUtils.isWithinSelection1ModeRange(pos))
                                    || this.selectionType != null && !PlayerUtils.isPositionInSelectionRange(this.player, pos, this.selectionType)
                              )
                           || this.shouldSkipFromScan(pos)) {
                           continue;
                        }

                        if (debugMode) {
                           GuiBlockInfo gui = isSchematic
                              ? new GuiBlockInfo(this.level, SchematicWorldHandler.getSchematicWorld(), pos)
                              : new GuiBlockInfo(this.level, null, pos);
                           gui.interacted = true;
                           gui.posInSelectionRange = true;
                           gui.execute = !this.isOnCooldown(pos) && this.canProcessPos(pos);
                           this.addGuiInfo(gui);
                        }

                        if (this.isVerifiedNoWork(pos)) {
                           continue;
                        }

                        workCandidates++;
                        if (this.isOnCooldown(pos) || !this.canProcessPos(pos)) {
                           continue;
                        }

                        this.lastExecuteConsumedQuota = true;
                        this.executeIteration(pos, this.skipIteration);
                        if (!this.skipIteration.get()) {
                           if (maxExecs <= 0 || !this.lastExecuteConsumedQuota || ++execCount < maxExecs) {
                              continue;
                           }
                        }

                        this.stopIteration(true);
                        return true;
                     }

                     this.stopIteration(true);
                     return true;
                  }

                  this.cachedIterator = null;
                  this.stopIteration(false);
                  if (this.idleBackoffEnabled()) {
                     if (this.playerMovedThisTick) {
                        this.idleBackoffTicks = 1;
                        this.nextScanAllowedAt = -1L;
                     } else {
                        this.idleBackoffTicks = workCandidates == 0 ? Math.min(this.idleBackoffTicks * 2, 10) : 1;
                        this.nextScanAllowedAt = ClientPlayerTickManager.getCurrentHandlerTime() + (long)this.idleBackoffTicks;
                     }
                  }

                  return false;
               }
            }
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   protected void stopIteration(boolean interrupt) {
   }

   protected boolean isSchematicHandler() {
      return false;
   }

   protected boolean isVerifiedNoWork(BlockPos pos) {
      return false;
   }

   protected boolean shouldSkipFromScan(BlockPos pos) {
      return false;
   }

   protected boolean idleBackoffEnabled() {
      return false;
   }

   protected int processFastRetry(int maxExecs, AtomicReference<Boolean> skipIteration) {
      return 0;
   }

   protected boolean hasUrgentRetries() {
      return false;
   }

   protected int processTargetedScan(int remainingExecs, AtomicReference<Boolean> skipIteration) {
      return 0;
   }

   private boolean shouldSkipForIdleBackoff() {
      if (!this.hasUrgentRetries() && !this.playerMovedThisTick) {
         long revision = SchematicStateCache.INSTANCE.getRevision();
         if (revision != this.lastSeenCacheRevision) {
            this.lastSeenCacheRevision = revision;
            this.idleBackoffTicks = 1;
            this.nextScanAllowedAt = -1L;
            return false;
         } else {
            return this.nextScanAllowedAt > ClientPlayerTickManager.getCurrentHandlerTime();
         }
      } else {
         return false;
      }
   }

   private void addGuiInfo(GuiBlockInfo info) {
      if (info != null) {
         this.guiQueue.add(info);
         this.guiCacheTicks = 20;
      }
   }

   @Nullable
   public GuiBlockInfo nextGuiInfo() {
      if (this.guiQueue.isEmpty()) {
         return null;
      } else {
         GuiBlockInfo[] arr = this.guiQueue.toArray(new GuiBlockInfo[0]);
         if (this.renderIndex >= arr.length) {
            this.renderIndex = 0;
            return arr[arr.length - 1];
         } else {
            return arr[this.renderIndex++];
         }
      }
   }

   @Nullable
   public GuiBlockInfo getLastGuiInfo() {
      if (this.guiQueue.isEmpty()) {
         return null;
      } else {
         GuiBlockInfo[] arr = this.guiQueue.toArray(new GuiBlockInfo[0]);
         return arr[arr.length - 1];
      }
   }

   public void setGuiInfo(@Nullable GuiBlockInfo info) {
      this.addGuiInfo(info);
   }

   public int getGuiQueueSize() {
      return this.guiQueue.size();
   }

   private boolean isConfigAllowed() {
      if (!ConfigUtils.isPrinterEnable()) {
         return false;
      } else if (this.printMode != null && this.enableConfig != null) {
         WorkingModeType mode = (WorkingModeType)Configs.Core.WORK_MODE.getOptionListValue();

         return switch (mode) {
            case SINGLE -> Configs.Core.WORK_MODE_TYPE.getOptionListValue().equals(this.printMode);
            case MULTI -> this.enableConfig.getBooleanValue();
         };
      } else {
         return this.enableConfig == null || this.enableConfig.getBooleanValue();
      }
   }

   protected int getTickInterval() {
      return -1;
   }

   protected int getMaxExecutions() {
      return -1;
   }

   protected int getIterationTimeLimit() {
      return Configs.Core.ITERATION_TIME_LIMIT.getIntegerValue();
   }

   protected void preprocess() {
   }

   protected boolean canExecute() {
      return true;
   }

   protected boolean canIterate() {
      return true;
   }

   public boolean canProcessPos(BlockPos pos) {
      return true;
   }

   protected void setExecuteConsumedQuota(boolean consumed) {
      this.lastExecuteConsumedQuota = consumed;
   }

   protected void executeIteration(BlockPos pos, AtomicReference<Boolean> skipIteration) {
   }

   public boolean isOnCooldown(@Nullable BlockPos pos) {
      return this.level != null && pos != null ? BlockPosCooldownManager.INSTANCE.isOnCooldown(this.level, this.id, pos) : true;
   }

   public boolean isOnCooldown(String name, @Nullable BlockPos pos) {
      return this.level != null && pos != null ? BlockPosCooldownManager.INSTANCE.isOnCooldown(this.level, this.cachedCooldownType(name), pos) : true;
   }

   public void setCooldown(@Nullable BlockPos pos, int ticks) {
      if (this.level != null && pos != null && ticks >= 1) {
         BlockPosCooldownManager.INSTANCE.setCooldown(this.level, this.id, pos, ticks);
      }
   }

   public void setCooldown(String name, @Nullable BlockPos pos, int ticks) {
      if (this.level != null && pos != null && ticks >= 1) {
         BlockPosCooldownManager.INSTANCE.setCooldown(this.level, this.cachedCooldownType(name), pos, ticks);
      }
   }

   private String cachedCooldownType(String name) {
      return this.cooldownTypeCache.computeIfAbsent(name, n -> this.id + "_" + n);
   }

   protected Direction[] getPlayerOrderedByNearest() {
      return Direction.orderedByNearest(this.player);
   }

   protected Direction getPlayerPlacementDirection() {
      return Direction.orderedByNearest(this.player)[0].getOpposite();
   }

   protected boolean needsRangeCheck() {
      return true;
   }

   @Nullable
   @Generated
   public AtomicReference<PrinterBox> getBoxRef() {
      return this.boxRef;
   }

   @Generated
   public String getId() {
      return this.id;
   }

   @Nullable
   @Generated
   public PrintModeType getPrintMode() {
      return this.printMode;
   }

   @Nullable
   @Generated
   public ConfigBoolean getEnableConfig() {
      return this.enableConfig;
   }

   @Nullable
   @Generated
   public ConfigOptionList getSelectionType() {
      return this.selectionType;
   }

   @Generated
   public int getRenderIndex() {
      return this.renderIndex;
   }
}
