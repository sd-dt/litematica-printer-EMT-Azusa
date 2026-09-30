package me.aleksilassila.litematica.printer.mixin.printer.mc;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.mixin_extension.BlockBreakResult;
import me.aleksilassila.litematica.printer.mixin_extension.MultiPlayerGameModeExtension;
import me.aleksilassila.litematica.printer.utils.BreakUtils;
import me.aleksilassila.litematica.printer.utils.ConfigUtils;
import me.aleksilassila.litematica.printer.utils.HandRestockShulkerCompat;
import me.aleksilassila.litematica.printer.utils.PacketSoundConfirmationTracker;
import me.aleksilassila.litematica.printer.utils.PacketUtils;
import me.aleksilassila.litematica.printer.utils.PinYinSearchUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {MultiPlayerGameMode.class},
   priority = 1020
)
public abstract class MixinMultiPlayerGameMode implements MultiPlayerGameModeExtension {
   @Unique
   private static final float MINE_FAST_FINISH_PROGRESS = 0.5F;
   @Unique
   private static final float MINE_FAST_FINISH_COMPLETED_PROGRESS = 0.6F;
   @Shadow
   private BlockPos destroyBlockPos;
   @Shadow
   private ItemStack destroyingItem;
   @Shadow
   private float destroyProgress;
   @Shadow
   private boolean isDestroying;
   @Shadow
   @Final
   private Minecraft minecraft;
   @Unique
   private BlockPos delayedDestroyPos;
   @Unique
   private boolean hasDelayedDestroy;
   @Unique
   private boolean delayedDestroyLocalPrediction;
   @Unique
   private long delayedDestroyStartTick;
   @Unique
   private final Map<BlockPos, Long> litematica_printer$pendingDelayedDestroys = new LinkedHashMap<>();
   @Unique
   private ItemStack litematica_printer$restockSnapshot = ItemStack.EMPTY;
   @Unique
   private InteractionHand litematica_printer$restockSnapshotHand;

   @Override
   public void litematica_printer$resetRuntime() {
      LocalPlayer player = this.minecraft.player;
      if (this.isDestroying) {
         this.litematica_printer$clearDestroyProgress(player, this.destroyBlockPos);
      }

      if (this.hasDelayedDestroy && this.delayedDestroyPos != null) {
         this.litematica_printer$clearDestroyProgress(player, this.delayedDestroyPos);
      }

      this.isDestroying = false;
      this.destroyProgress = 0.0F;
      this.destroyBlockPos = BlockPos.ZERO;
      this.destroyingItem = ItemStack.EMPTY;
      this.delayedDestroyPos = null;
      this.hasDelayedDestroy = false;
      this.delayedDestroyLocalPrediction = false;
      this.delayedDestroyStartTick = 0L;
      this.litematica_printer$pendingDelayedDestroys.clear();
   }

   @Unique
   private void litematica_printer$clearDestroyProgress(LocalPlayer player, BlockPos pos) {
      if (player != null && pos != null && this.minecraft.level != null) {
         int playerId;
         try {
            playerId = player.getId();
         } catch (IllegalStateException var5) {
            return;
         }

         this.minecraft.level.destroyBlockProgress(playerId, pos, -1);
      }
   }

   @Unique
   private void litematica_printer$resetDestroyState(LocalPlayer player, BlockPos pos) {
      this.isDestroying = false;
      this.destroyProgress = 0.0F;
      this.litematica_printer$clearDestroyProgress(player, pos);
   }

   @Unique
   private void litematica_printer$addPendingDelayedDestroy(BlockPos pos) {
      if (pos != null) {
         this.litematica_printer$pendingDelayedDestroys.put(pos.immutable(), this.getClientTickCount());
      }
   }

   @Unique
   private boolean litematica_printer$hasPendingDelayedDestroy(BlockPos pos) {
      return pos != null && this.litematica_printer$pendingDelayedDestroys.containsKey(pos);
   }

   @Unique
   private void litematica_printer$removePendingDelayedDestroy(BlockPos pos) {
      if (pos != null) {
         this.litematica_printer$pendingDelayedDestroys.remove(pos);
      }
   }

   @Unique
   private void litematica_printer$cleanupPendingDelayedDestroys(LocalPlayer player, ClientLevel level) {
      if (!this.litematica_printer$pendingDelayedDestroys.isEmpty()) {
         long currentTick = this.getClientTickCount();
         Iterator<Entry<BlockPos, Long>> iterator = this.litematica_printer$pendingDelayedDestroys.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<BlockPos, Long> entry = iterator.next();
            BlockPos pos = entry.getKey();
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !(state.getBlock() instanceof LiquidBlock)) {
               int timeoutTicks = this.litematica_printer$getPendingDelayedDestroyTimeoutTicks(player, level, pos, state);
               if (currentTick - entry.getValue() >= (long)timeoutTicks) {
                  iterator.remove();
               }
            } else {
               iterator.remove();
            }
         }
      }
   }

   @Unique
   private int litematica_printer$getPendingDelayedDestroyTimeoutTicks(LocalPlayer player, ClientLevel level, BlockPos pos, BlockState state) {
      float progressPerTick = state.getDestroyProgress(player, level, pos);
      if (progressPerTick <= 0.0F) {
         return 200;
      } else {
         int estimatedTicks = (int)Math.ceil((double)(1.0F / progressPerTick));
         return Math.max(8, Math.min(estimatedTicks + 10, 200));
      }
   }

   @Override
   public boolean litematica_printer$isPendingDelayedDestroy(BlockPos blockPos) {
      return this.litematica_printer$hasPendingDelayedDestroy(blockPos);
   }

   @Shadow
   public abstract boolean destroyBlock(BlockPos var1);

   @Shadow
   protected abstract boolean sameDestroyTarget(BlockPos var1);

   @Shadow
   protected abstract void ensureHasSentCarriedItem();

   @Shadow
   public abstract InteractionResult useItemOn(LocalPlayer var1, InteractionHand var2, BlockHitResult var3);

   @Inject(
      at = {@At("HEAD")},
      method = {"tick"}
   )
   public void tick(CallbackInfo ci) {
      if (this.hasDelayedDestroy) {
         LocalPlayer player = this.minecraft.player;
         ClientLevel level = this.minecraft.level;
         if (player == null || level == null) {
            return;
         }

         this.litematica_printer$cleanupPendingDelayedDestroys(player, level);
         BlockState blockState = level.getBlockState(this.delayedDestroyPos);
         if (blockState.isAir()) {
            this.litematica_printer$removePendingDelayedDestroy(this.delayedDestroyPos);
            this.hasDelayedDestroy = false;
            this.delayedDestroyLocalPrediction = false;
            this.litematica_printer$clearDestroyProgress(player, this.delayedDestroyPos);
            return;
         }

         long currentTick = this.getClientTickCount();
         int elapsedTicks = (int)(currentTick - this.delayedDestroyStartTick);
         float delayedDestroyProgress = blockState.getDestroyProgress(player, level, this.delayedDestroyPos) * (float)elapsedTicks;
         if (delayedDestroyProgress >= 1.0F) {
            this.litematica_printer$playBreakEffect(this.delayedDestroyPos, blockState);
            if (this.delayedDestroyLocalPrediction) {
               this.litematica_printer$destroyBlockSilently(this.delayedDestroyPos);
            }

            this.litematica_printer$removePendingDelayedDestroy(this.delayedDestroyPos);
            this.hasDelayedDestroy = false;
            this.delayedDestroyLocalPrediction = false;
         }
      } else {
         LocalPlayer playerx = this.minecraft.player;
         ClientLevel levelx = this.minecraft.level;
         if (playerx != null && levelx != null) {
            this.litematica_printer$cleanupPendingDelayedDestroys(playerx, levelx);
         }
      }
   }

   @Override
   public BlockPos litematica_printer$destroyBlockPos() {
      return this.destroyBlockPos;
   }

   @Override
   public boolean litematica_printer$isDestroying() {
      return this.isDestroying;
   }

   @Override
   public void litematica_printer$startPrediction(MultiPlayerGameModeExtension.PredictiveAction predictiveAction) {
      PacketUtils.sendPacket(predictiveAction);
   }

   @Override
   public InteractionResult litematica_printer$useItemOn(boolean localPrediction, InteractionHand hand, BlockHitResult blockHit) {
      if (localPrediction) {
         return this.useItemOn(this.minecraft.player, hand, blockHit);
      } else {
         this.ensureHasSentCarriedItem();
         if (!this.minecraft.level.getWorldBorder().isWithinBounds(blockHit.getBlockPos())) {
            return InteractionResult.FAIL;
         } else {
            PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> new ServerboundUseItemOnPacket(hand, blockHit, sequence)));
            return InteractionResult.PASS;
         }
      }
   }

   @Unique
   private int litematica_printer$getDestroyStage() {
      float breakingProgress = this.destroyProgress >= ConfigUtils.getBreakProgressThreshold() ? 1.0F : this.destroyProgress;
      return breakingProgress > 0.0F ? (int)(breakingProgress * 10.0F) : -1;
   }

   @Unique
   private ServerboundPlayerActionPacket getActionPacket(Action action, BlockPos blockPos, Direction direction, int sequence) {
      return new ServerboundPlayerActionPacket(action, blockPos, direction, sequence);
   }

   @Unique
   private boolean litematica_printer$instantMine(BlockPos blockPos, Direction direction) {
      if (Configs.Mine.BREAK_USE_PACKET.getBooleanValue() && Configs.Mine.BREAK_INSTANT_MINE.getBooleanValue()) {
         List<String> veilList = Configs.Mine.BREAK_INSTANT_MINE_LIST.getStrings();
         if (!veilList.isEmpty() && this.minecraft.level != null) {
            BlockState veilState = this.minecraft.level.getBlockState(blockPos);
            if (veilList.stream().noneMatch(s -> PinYinSearchUtils.matchName(s, veilState))) {
               return false;
            }
         }

         if (this.isDestroying && !this.sameDestroyTarget(blockPos)) {
            PacketUtils.sendPacket(this.getActionPacket(Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, direction, 0));
         }

         this.isDestroying = false;
         this.destroyProgress = 0.0F;
         PacketUtils.sendPacket(
            (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(Action.START_DESTROY_BLOCK, blockPos, direction, sequence))
         );
         if (Configs.Mine.BREAK_SOUND.getBooleanValue() && this.minecraft.level != null) {
            PacketSoundConfirmationTracker.trackBreak(blockPos, this.minecraft.level.getBlockState(blockPos));
         }

         PacketUtils.sendPacket(
            (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence))
         );
         return true;
      } else {
         return false;
      }
   }

   @Override
   public BlockBreakResult litematica_printer$continueDestroyBlockForMine(BlockPos blockPos, Direction direction, boolean allowToolSwitch) {
      LocalPlayer player = this.minecraft.player;
      ClientLevel level = this.minecraft.level;
      MultiPlayerGameMode gameMode = this.minecraft.gameMode;
      if (player == null || level == null || gameMode == null) {
         return BlockBreakResult.FAILED;
      } else if (Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() && BreakUtils.isPlayerMining()) {
         return BlockBreakResult.ABORTED;
      } else if (this.litematica_printer$instantMine(blockPos, direction)) {
         return BlockBreakResult.COMPLETED;
      } else {
         BlockState blockState = level.getBlockState(blockPos);
         if (!blockState.isAir() && !(blockState.getBlock() instanceof LiquidBlock)) {
            if (!level.getWorldBorder().isWithinBounds(blockPos)) {
               return BlockBreakResult.FAILED;
            } else {
               if (!allowToolSwitch || !BreakUtils.trySwitchToEffectiveTool(blockPos, blockState)) {
                  this.ensureHasSentCarriedItem();
               }

               if (!BreakUtils.protectCurrentToolBeforeBreak(blockState)) {
                  return BlockBreakResult.FAILED;
               } else if (!BreakUtils.isRecoveryToolReadyForBreak(blockState)) {
                  return BlockBreakResult.FAILED;
               } else {
                  this.ensureHasSentCarriedItem();
                  float destroyProgress = blockState.getDestroyProgress(player, level, blockPos);
                  boolean fastPath = player.getAbilities().instabuild || destroyProgress >= 0.5F;
                  if (!fastPath) {
                     if (this.hasDelayedDestroy) {
                        return blockPos.equals(this.delayedDestroyPos) ? BlockBreakResult.IN_PROGRESS : BlockBreakResult.ABORTED;
                     } else if (this.litematica_printer$hasPendingDelayedDestroy(blockPos)) {
                        return BlockBreakResult.IN_PROGRESS;
                     } else {
                        BlockBreakResult result = this.litematica_printer$continueDestroyBlock(false, blockPos, direction, false, allowToolSwitch);
                        if (result == BlockBreakResult.FAILED) {
                           return BlockBreakResult.FAILED;
                        } else if (this.hasDelayedDestroy && blockPos.equals(this.delayedDestroyPos)) {
                           return BlockBreakResult.IN_PROGRESS;
                        } else {
                           return this.litematica_printer$hasPendingDelayedDestroy(blockPos) ? BlockBreakResult.IN_PROGRESS : result;
                        }
                     }
                  } else {
                     if (this.isDestroying) {
                        this.litematica_printer$resetDestroyState(player, this.destroyBlockPos);
                     }

                     PacketUtils.sendPacket(
                        (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(
                              Action.START_DESTROY_BLOCK, blockPos, direction, sequence
                           ))
                     );
                     if (!player.getAbilities().instabuild) {
                        PacketUtils.sendPacket(
                           (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(
                                 Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence
                              ))
                        );
                     }

                     this.litematica_printer$playBreakEffect(blockPos, blockState);
                     return BlockBreakResult.COMPLETED_WAIT;
                  }
               }
            }
         } else {
            this.litematica_printer$removePendingDelayedDestroy(blockPos);
            if (this.hasDelayedDestroy && blockPos.equals(this.delayedDestroyPos)) {
               this.hasDelayedDestroy = false;
               this.delayedDestroyLocalPrediction = false;
            }

            if (this.isDestroying && this.sameDestroyTarget(blockPos)) {
               this.litematica_printer$resetDestroyState(player, blockPos);
            }

            return BlockBreakResult.COMPLETED;
         }
      }
   }

   @Override
   public BlockBreakResult litematica_printer$continueDestroyBlock(
      boolean localPrediction, BlockPos blockPos, Direction direction, boolean forceDelayedDestroy, boolean allowToolSwitch
   ) {
      LocalPlayer player = this.minecraft.player;
      ClientLevel level = this.minecraft.level;
      MultiPlayerGameMode gameMode = this.minecraft.gameMode;
      if (player == null || level == null || gameMode == null) {
         return BlockBreakResult.FAILED;
      } else if (Configs.Mine.BREAK_NON_BLOCKING.getBooleanValue() && BreakUtils.isPlayerMining()) {
         return BlockBreakResult.ABORTED;
      } else if (this.litematica_printer$instantMine(blockPos, direction)) {
         return BlockBreakResult.COMPLETED;
      } else {
         boolean localEffects = localPrediction || forceDelayedDestroy;
         boolean localBlockRemoval = localPrediction && !forceDelayedDestroy;
         BlockState blockState = level.getBlockState(blockPos);
         if (!blockState.isAir() && !(blockState.getBlock() instanceof LiquidBlock)) {
            if (this.hasDelayedDestroy) {
               BlockState blockState2 = this.minecraft.level.getBlockState(this.delayedDestroyPos);
               long currentTick = this.getClientTickCount();
               int elapsedTicks = (int)(currentTick - this.delayedDestroyStartTick);
               float delayedDestroyProgress = blockState2.getDestroyProgress(player, level, this.delayedDestroyPos) * (float)elapsedTicks;
               if (delayedDestroyProgress >= 1.0F) {
                  this.litematica_printer$playBreakEffect(this.delayedDestroyPos, blockState2);
                  if (this.delayedDestroyLocalPrediction) {
                     this.litematica_printer$destroyBlockSilently(this.delayedDestroyPos);
                  }

                  this.litematica_printer$removePendingDelayedDestroy(this.delayedDestroyPos);
                  this.hasDelayedDestroy = false;
                  this.delayedDestroyLocalPrediction = false;
               }
            }

            if (!level.getWorldBorder().isWithinBounds(blockPos)) {
               return BlockBreakResult.FAILED;
            } else if (player.getAbilities().instabuild) {
               PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> {
                  if (localBlockRemoval) {
                     this.litematica_printer$destroyBlockSilently(blockPos);
                  }

                  return this.getActionPacket(Action.START_DESTROY_BLOCK, blockPos, direction, sequence);
               }));
               this.litematica_printer$playBreakEffect(blockPos, blockState);
               return BlockBreakResult.COMPLETED;
            } else {
               if (allowToolSwitch) {
                  BreakUtils.trySwitchToEffectiveTool(blockPos, blockState);
               }

               this.ensureHasSentCarriedItem();
               if (!BreakUtils.protectCurrentToolBeforeBreak(blockState)) {
                  return BlockBreakResult.FAILED;
               } else if (!BreakUtils.isRecoveryToolReadyForBreak(blockState)) {
                  return BlockBreakResult.FAILED;
               } else {
                  this.ensureHasSentCarriedItem();
                  boolean useDelayedDestroy = forceDelayedDestroy || Configs.Mine.BREAK_USE_DELAYED_DESTROY.getBooleanValue();
                  if (blockState.isAir()) {
                     if (this.hasDelayedDestroy && blockPos.equals(this.delayedDestroyPos)) {
                        this.hasDelayedDestroy = false;
                        this.delayedDestroyLocalPrediction = false;
                        this.litematica_printer$clearDestroyProgress(player, blockPos);
                     }

                     this.litematica_printer$removePendingDelayedDestroy(blockPos);
                     if (this.isDestroying && this.sameDestroyTarget(blockPos)) {
                        this.litematica_printer$resetDestroyState(player, blockPos);
                     }

                     return BlockBreakResult.COMPLETED;
                  } else if (!this.litematica_printer$hasPendingDelayedDestroy(blockPos) || this.hasDelayedDestroy && blockPos.equals(this.delayedDestroyPos)) {
                     if (this.hasDelayedDestroy && blockPos.equals(this.delayedDestroyPos)) {
                        return this.isDestroying ? BlockBreakResult.IN_PROGRESS : BlockBreakResult.COMPLETED;
                     } else {
                        if (this.isDestroying && !blockPos.equals(this.destroyBlockPos)) {
                           PacketUtils.sendPacket(this.getActionPacket(Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, direction, 0));
                           this.litematica_printer$resetDestroyState(player, this.destroyBlockPos);
                        }

                        if (blockPos.equals(this.destroyBlockPos)) {
                           this.destroyProgress = this.destroyProgress + blockState.getDestroyProgress(player, level, blockPos);
                           if (localEffects) {
                              level.destroyBlockProgress(player.getId(), blockPos, this.litematica_printer$getDestroyStage());
                           }

                           if (this.destroyProgress >= ConfigUtils.getBreakProgressThreshold()) {
                              PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> {
                                 if (localBlockRemoval) {
                                    this.litematica_printer$destroyBlockSilently(blockPos);
                                 }

                                 this.litematica_printer$resetDestroyState(player, blockPos);
                                 return this.getActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence);
                              }));
                              if (localEffects) {
                                 level.destroyBlockProgress(player.getId(), blockPos, -1);
                              }

                              this.litematica_printer$playBreakEffect(blockPos, blockState);
                              return BlockBreakResult.COMPLETED;
                           } else {
                              return BlockBreakResult.IN_PROGRESS;
                           }
                        } else if (this.isDestroying && blockPos.equals(this.destroyBlockPos)) {
                           return BlockBreakResult.FAILED;
                        } else {
                           if (this.isDestroying) {
                              PacketUtils.sendPacket(this.getActionPacket(Action.ABORT_DESTROY_BLOCK, this.destroyBlockPos, direction, 0));
                              this.litematica_printer$resetDestroyState(player, this.destroyBlockPos);
                           }

                           float destroyProgress = blockState.getDestroyProgress(player, level, blockPos);
                           boolean mineFastFinish = forceDelayedDestroy && destroyProgress > 0.5F;
                           if (!(destroyProgress >= ConfigUtils.getBreakProgressThreshold()) && !mineFastFinish) {
                              PacketUtils.sendPacket(
                                 (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(
                                       Action.START_DESTROY_BLOCK, blockPos, direction, sequence
                                    ))
                              );
                              if (destroyProgress >= 1.0F) {
                                 if (localEffects) {
                                    level.destroyBlockProgress(player.getId(), blockPos, -1);
                                 }

                                 this.litematica_printer$resetDestroyState(player, blockPos);
                                 this.litematica_printer$playBreakEffect(blockPos, blockState);
                                 return BlockBreakResult.COMPLETED;
                              } else if (!useDelayedDestroy) {
                                 this.isDestroying = true;
                                 this.destroyBlockPos = blockPos;
                                 this.destroyProgress = destroyProgress;
                                 this.destroyingItem = player.getMainHandItem();
                                 if (localEffects) {
                                    level.destroyBlockProgress(player.getId(), blockPos, this.litematica_printer$getDestroyStage());
                                 }

                                 return BlockBreakResult.IN_PROGRESS;
                              } else if (destroyProgress >= ConfigUtils.getBreakProgressThreshold()) {
                                 PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> {
                                    if (localBlockRemoval) {
                                       this.litematica_printer$destroyBlockSilently(blockPos);
                                    }

                                    this.hasDelayedDestroy = false;
                                    return this.getActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence);
                                 }));
                                 if (localEffects) {
                                    level.destroyBlockProgress(player.getId(), blockPos, -1);
                                 }

                                 this.litematica_printer$playBreakEffect(blockPos, blockState);
                                 return BlockBreakResult.COMPLETED;
                              } else {
                                 PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> {
                                    this.hasDelayedDestroy = true;
                                    this.delayedDestroyPos = blockPos;
                                    this.delayedDestroyLocalPrediction = localBlockRemoval;
                                    this.delayedDestroyStartTick = this.getClientTickCount();
                                    this.litematica_printer$addPendingDelayedDestroy(blockPos);
                                    this.litematica_printer$resetDestroyState(player, blockPos);
                                    return this.getActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence);
                                 }));
                                 level.destroyBlockProgress(player.getId(), blockPos, this.litematica_printer$getDestroyStage());
                                 return BlockBreakResult.COMPLETED_WAIT;
                              }
                           } else {
                              boolean waitForServerState = mineFastFinish
                                 && destroyProgress < ConfigUtils.getBreakProgressThreshold()
                                 && destroyProgress <= 0.6F;
                              PacketUtils.sendPacket(
                                 (MultiPlayerGameModeExtension.PredictiveAction)(sequence -> this.getActionPacket(
                                       Action.START_DESTROY_BLOCK, blockPos, direction, sequence
                                    ))
                              );
                              PacketUtils.sendPacket((MultiPlayerGameModeExtension.PredictiveAction)(sequence -> {
                                 if (localBlockRemoval) {
                                    this.litematica_printer$destroyBlockSilently(blockPos);
                                 }

                                 this.litematica_printer$resetDestroyState(player, blockPos);
                                 return this.getActionPacket(Action.STOP_DESTROY_BLOCK, blockPos, direction, sequence);
                              }));
                              if (localEffects) {
                                 level.destroyBlockProgress(player.getId(), blockPos, -1);
                              }

                              if (!waitForServerState) {
                                 this.litematica_printer$playBreakEffect(blockPos, blockState);
                              }

                              return waitForServerState ? BlockBreakResult.COMPLETED_WAIT : BlockBreakResult.COMPLETED;
                           }
                        }
                     }
                  } else {
                     return BlockBreakResult.COMPLETED_WAIT;
                  }
               }
            }
         } else {
            this.litematica_printer$removePendingDelayedDestroy(blockPos);
            return BlockBreakResult.COMPLETED;
         }
      }
   }

   @Unique
   private void litematica_printer$destroyBlockSilently(BlockPos pos) {
      if (pos != null && this.minecraft.level != null) {
         ClientLevel level = this.minecraft.level;
         LocalPlayer player = this.minecraft.player;
         if (!level.getBlockState(pos).isAir()) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 11);
         }

         if (player != null) {
            level.destroyBlockProgress(player.getId(), pos, -1);
         }
      }
   }

   @Unique
   private void litematica_printer$playBreakEffect(BlockPos pos, BlockState state) {
      ClientLevel level = this.minecraft.level;
      if (level != null && pos != null) {
         if (state == null || state.isAir() || state.getBlock() instanceof LiquidBlock) {
            state = level.getBlockState(pos);
            if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
               return;
            }
         }

         level.addDestroyBlockEffect(pos, state);
         if (Configs.Mine.BREAK_USE_PACKET.getBooleanValue()) {
            PacketSoundConfirmationTracker.trackBreak(pos, state);
         } else if (Configs.Mine.BREAK_SOUND.getBooleanValue()) {
            level.playLocalSound(pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
         }
      }
   }

   @Unique
   private long getClientTickCount() {
      ClientLevel level = this.minecraft.level;
      return level == null ? 0L : level.getGameTime();
   }

   @Inject(
      method = {"useItem"},
      at = {@At("HEAD")},
      require = 0
   )
   private void litematica_printer$captureUseItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      this.litematica_printer$captureHandSnapshot(player, hand);
   }

   @Inject(
      method = {"useItem"},
      at = {@At("TAIL")},
      require = 0
   )
   private void litematica_printer$detectUseItemConsumption(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
      this.litematica_printer$detectHandConsumption(player, hand);
   }

   @Inject(
      method = {"useItemOn"},
      at = {@At("HEAD")},
      require = 0
   )
   private void litematica_printer$captureUseItemOn(
      LocalPlayer player, InteractionHand hand, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir
   ) {
      this.litematica_printer$captureHandSnapshot(player, hand);
   }

   @Inject(
      method = {"useItemOn"},
      at = {@At("TAIL")},
      require = 0
   )
   private void litematica_printer$detectUseItemOnConsumption(
      LocalPlayer player, InteractionHand hand, BlockHitResult blockHitResult, CallbackInfoReturnable<InteractionResult> cir
   ) {
      this.litematica_printer$detectHandConsumption(player, hand);
   }

   @Unique
   private void litematica_printer$captureHandSnapshot(Player player, InteractionHand hand) {
      this.litematica_printer$restockSnapshot = player.getItemInHand(hand).copy();
      this.litematica_printer$restockSnapshotHand = hand;
   }

   @Unique
   private void litematica_printer$detectHandConsumption(Player player, InteractionHand hand) {
      if (this.litematica_printer$restockSnapshotHand == hand) {
         HandRestockShulkerCompat.onHandStackConsumed(player, hand, this.litematica_printer$restockSnapshot, player.getItemInHand(hand));
      }
   }

   @Inject(
      method = {"handleContainerInput"},
      at = {@At("HEAD")},
      require = 0
   )
   private void litematica_printer$markThrowDrop(int containerId, int slotId, int mouseButton, ContainerInput clickType, Player player, CallbackInfo ci) {
      if (clickType == ContainerInput.THROW) {
         HandRestockShulkerCompat.markLocalDrop();
      }
   }
}
