package me.aleksilassila.litematica.printer.utils;

import fi.dy.masa.malilib.config.options.ConfigOptionList;
import java.util.Objects;
import java.util.Optional;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.enums.RadiusShapeType;
import me.aleksilassila.litematica.printer.enums.MineSelectionType;
import me.aleksilassila.litematica.printer.enums.SelectionType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffectUtil;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

public class PlayerUtils {
   private static final Minecraft client = Minecraft.getInstance();

   public static Optional<LocalPlayer> getPlayer() {
      return Optional.ofNullable(client.player);
   }

   public static Abilities getAbilities(LocalPlayer playerEntity) {
      return playerEntity.getAbilities();
   }

   public static double getInteractionRange(double defaultRange) {
      return client.player != null ? client.player.blockInteractionRange() : defaultRange;
   }

   public static boolean isWithinBlockInteractionRange(LocalPlayer player, BlockPos blockPos, double additionalRange) {
      double blockPosX = (double)blockPos.getX();
      double blockPosY = (double)blockPos.getY();
      double blockPosZ = (double)blockPos.getZ();
      double eyePosX = player.getX();
      double eyePosZ = player.getZ();
      double eyePosY = player.getEyeY();
      double distance = getInteractionRange(5.0) + additionalRange;
      double dx = Math.max(Math.max(blockPosX - eyePosX, eyePosX - (blockPosX + 1.0)), 0.0);
      double dy = Math.max(Math.max(blockPosY - eyePosY, eyePosY - (blockPosY + 1.0)), 0.0);
      double dz = Math.max(Math.max(blockPosZ - eyePosZ, eyePosZ - (blockPosZ + 1.0)), 0.0);
      return dx * dx + dy * dy + dz * dz < distance * distance;
   }

   public static boolean isWithinWorkInteractedEuclideanRange(BlockPos blockPos, double range) {
      LocalPlayer player = client.player;
      return player != null && blockPos != null ? player.getEyePosition().distanceToSqr(Vec3.atCenterOf(blockPos)) <= range * range : false;
   }

   public static boolean isWithinWorkInteractedManhattanRange(BlockPos blockPos, double range) {
      LocalPlayer player = client.player;
      if (player != null && blockPos != null) {
         BlockPos center = player.blockPosition();
         int dx = Math.abs(blockPos.getX() - center.getX());
         int dy = Math.abs(blockPos.getY() - center.getY());
         int dz = Math.abs(blockPos.getZ() - center.getZ());
         return (double)(dx + dy + dz) <= range;
      } else {
         return false;
      }
   }

   public static boolean isWithinWorkInteractedCubeRange(BlockPos blockPos, double range) {
      LocalPlayer player = client.player;
      if (player != null && blockPos != null) {
         BlockPos center = player.blockPosition();
         int dx = Math.abs(blockPos.getX() - center.getX());
         int dy = Math.abs(blockPos.getY() - center.getY());
         int dz = Math.abs(blockPos.getZ() - center.getZ());
         return (double)dx <= range && (double)dy <= range && (double)dz <= range;
      } else {
         return false;
      }
   }

   public static float getDestroyProgress(LocalPlayer player, BlockState state, ItemStack itemStack) {
      float hardness = state.getBlock().defaultDestroyTime();
      if (hardness == -1.0F) {
         return 0.0F;
      } else {
         int i = player.hasCorrectToolForDrops(state) ? 30 : 100;
         return getBlockBreakingSpeed(player, state, itemStack) / hardness / (float)i;
      }
   }

   public static float getDestroyProgress(LocalPlayer player, BlockState state, boolean mainHand) {
      return getDestroyProgress(player, state, mainHand ? player.getMainHandItem() : player.getOffhandItem());
   }

   public static float getDestroyProgress(LocalPlayer player, BlockState state) {
      return getDestroyProgress(player, state, true);
   }

   public static float getBlockBreakingSpeed(LocalPlayer player, BlockState blockState, ItemStack itemStack) {
      float f = itemStack.getDestroySpeed(blockState);
      if (f > 1.0F) {
         for (Holder<Enchantment> enchantment : itemStack.getEnchantments().keySet()) {
            Optional<ResourceKey<Enchantment>> enchantmentKey = enchantment.unwrapKey();
            if (enchantmentKey.isPresent() && enchantmentKey.get() == Enchantments.EFFICIENCY) {
               int level = EnchantmentHelper.getItemEnchantmentLevel(enchantment, itemStack);
               if (level > 0 && !itemStack.isEmpty()) {
                  f += (float)(level * level + 1);
               }
            }
         }
      }

      if (MobEffectUtil.hasDigSpeed(player)) {
         f *= 1.0F + (float)(MobEffectUtil.getDigSpeedAmplification(player) + 1) * 0.2F;
      }

      if (player.hasEffect(MobEffects.MINING_FATIGUE)) {
         f *= switch (Objects.requireNonNull(player.getEffect(MobEffects.MINING_FATIGUE)).getAmplifier()) {
            case 0 -> 0.3F;
            case 1 -> 0.09F;
            case 2 -> 0.0027F;
            default -> 8.1E-4F;
         };
      }

      f *= (float)player.getAttributeValue(Attributes.BLOCK_BREAK_SPEED);
      if (player.isEyeInFluid(FluidTags.WATER)) {
         AttributeInstance submergedMiningSpeed = player.getAttribute(Attributes.SUBMERGED_MINING_SPEED);
         if (submergedMiningSpeed != null) {
            f *= (float)submergedMiningSpeed.getValue();
         }
      }

      if (!player.onGround()) {
         f /= 5.0F;
      }

      return f;
   }

   public static boolean canInteracted(BlockPos blockPos) {
      double workRange = (double)ConfigUtils.getWorkRange();
      if (Configs.Core.CHECK_PLAYER_INTERACTION_RANGE.getBooleanValue()
         && ConfigUtils.client.player != null
         && !isWithinBlockInteractionRange(ConfigUtils.client.player, blockPos, 1.0)) {
         return false;
      } else if (Configs.Core.ITERATOR_SHAPE.getOptionListValue() instanceof RadiusShapeType radiusShapeType) {
         return switch (radiusShapeType) {
            case SPHERE -> isWithinWorkInteractedEuclideanRange(blockPos, workRange);
            case OCTAHEDRON -> isWithinWorkInteractedManhattanRange(blockPos, workRange);
            case CUBE -> isWithinWorkInteractedCubeRange(blockPos, workRange);
         };
      } else {
         return true;
      }
   }

   public static boolean isPositionInSelectionRange(Player player, @NotNull BlockPos pos, ConfigOptionList selectionTypeConfig) {
      if (player == null || selectionTypeConfig == null) {
         return false;
      }

      Object value = selectionTypeConfig.getOptionListValue();
      // 「平面无边界」只可能出现在挖掘专用的 MineSelectionType 里：两个 Y 高度之间不做选区限制
      if (value == MineSelectionType.PLANE_UNBOUNDED) {
         int y = pos.getY();
         return y >= Configs.Mine.MINE_PLANE_MIN_Y.getIntegerValue() && y <= Configs.Mine.MINE_PLANE_MAX_Y.getIntegerValue();
      }

      // 其余四种语义在共享枚举与挖掘专用枚举里完全一致
      if (value instanceof SelectionType selectionType) {
         return switch (selectionType) {
            case LITEMATICA_RENDER_LAYER -> LitematicaUtils.isPositionWithinRange(pos);
            case LITEMATICA_SELECTION_BELOW_PLAYER -> (double)pos.getY() <= Math.floor(player.getY());
            case LITEMATICA_SELECTION_ABOVE_PLAYER -> (double)pos.getY() >= Math.ceil(player.getY());
            default -> true;
         };
      }

      if (value instanceof MineSelectionType mineSelectionType) {
         return switch (mineSelectionType) {
            case LITEMATICA_RENDER_LAYER -> LitematicaUtils.isPositionWithinRange(pos);
            case LITEMATICA_SELECTION_BELOW_PLAYER -> (double)pos.getY() <= Math.floor(player.getY());
            case LITEMATICA_SELECTION_ABOVE_PLAYER -> (double)pos.getY() >= Math.ceil(player.getY());
            default -> true;
         };
      }

      return false;
   }
}
