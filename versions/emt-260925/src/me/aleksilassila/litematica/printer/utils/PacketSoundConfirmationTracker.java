package me.aleksilassila.litematica.printer.utils;

import java.util.HashMap;
import java.util.Map;
import me.aleksilassila.litematica.printer.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;

public final class PacketSoundConfirmationTracker {
   private static final long TIMEOUT_TICKS = 40L;
   private static final Map<BlockPos, PacketSoundConfirmationTracker.PendingSound> PENDING_SOUNDS = new HashMap<>();

   private PacketSoundConfirmationTracker() {
   }

   public static void trackPlacement(BlockPos pos, BlockState expectedState) {
      if (pos != null && expectedState != null && Configs.Print.PRINT_SOUND.getBooleanValue()) {
         track(pos, expectedState, PacketSoundConfirmationTracker.SoundType.PLACEMENT);
      }
   }

   public static void trackBreak(BlockPos pos, BlockState brokenState) {
      if (pos != null && brokenState != null && Configs.Mine.BREAK_SOUND.getBooleanValue()) {
         track(pos, brokenState, PacketSoundConfirmationTracker.SoundType.BREAK);
      }
   }

   private static void track(BlockPos pos, BlockState state, PacketSoundConfirmationTracker.SoundType type) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         prune(level.getGameTime());
         PENDING_SOUNDS.put(pos.immutable(), new PacketSoundConfirmationTracker.PendingSound(state, type, level.getGameTime() + 40L));
      }
   }

   public static void confirmServerBlockUpdate(BlockPos pos, BlockState updatedState) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null && pos != null && updatedState != null) {
         prune(level.getGameTime());
         PacketSoundConfirmationTracker.PendingSound pending = PENDING_SOUNDS.get(pos);
         if (pending != null) {
            boolean confirmed = pending.type == PacketSoundConfirmationTracker.SoundType.PLACEMENT
               ? updatedState.getBlock() == pending.state.getBlock()
               : updatedState.isAir();
            if (confirmed) {
               PENDING_SOUNDS.remove(pos);
               if (pending.type == PacketSoundConfirmationTracker.SoundType.PLACEMENT) {
                  level.playLocalSound(pos, pending.state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
               } else {
                  level.playLocalSound(pos, pending.state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 1.0F, 0.8F, false);
               }
            }
         }
      }
   }

   private static void prune(long currentTick) {
      PENDING_SOUNDS.entrySet().removeIf(entry -> entry.getValue().expiresAtTick < currentTick);
   }

   private static record PendingSound(BlockState state, PacketSoundConfirmationTracker.SoundType type, long expiresAtTick) {
   }

   private static enum SoundType {
      PLACEMENT,
      BREAK;
   }
}
