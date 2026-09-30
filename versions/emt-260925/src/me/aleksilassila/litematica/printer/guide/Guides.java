package me.aleksilassila.litematica.printer.guide;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import me.aleksilassila.litematica.printer.Reference;
import me.aleksilassila.litematica.printer.enums.BlockMatchResult;
import me.aleksilassila.litematica.printer.guide.guides.AmethystGuide;
import me.aleksilassila.litematica.printer.guide.guides.AnvilGuide;
import me.aleksilassila.litematica.printer.guide.guides.BannerGuide;
import me.aleksilassila.litematica.printer.guide.guides.BedGuide;
import me.aleksilassila.litematica.printer.guide.guides.BellGuide;
import me.aleksilassila.litematica.printer.guide.guides.CampfireGuide;
import me.aleksilassila.litematica.printer.guide.guides.CandleGuide;
import me.aleksilassila.litematica.printer.guide.guides.CauldronGuide;
import me.aleksilassila.litematica.printer.guide.guides.ChestGuide;
import me.aleksilassila.litematica.printer.guide.guides.ClimbingPlantGuide;
import me.aleksilassila.litematica.printer.guide.guides.CocoaGuide;
import me.aleksilassila.litematica.printer.guide.guides.ComparatorGuide;
import me.aleksilassila.litematica.printer.guide.guides.ComposterGuide;
import me.aleksilassila.litematica.printer.guide.guides.CrafterGuide;
import me.aleksilassila.litematica.printer.guide.guides.CropsGuide;
import me.aleksilassila.litematica.printer.guide.guides.DaylightDetectorGuide;
import me.aleksilassila.litematica.printer.guide.guides.DoorGuide;
import me.aleksilassila.litematica.printer.guide.guides.EndPortalFrameGuide;
import me.aleksilassila.litematica.printer.guide.guides.FenceGateGuide;
import me.aleksilassila.litematica.printer.guide.guides.FireGuide;
import me.aleksilassila.litematica.printer.guide.guides.FlowerBedGuide;
import me.aleksilassila.litematica.printer.guide.guides.FlowerGuide;
import me.aleksilassila.litematica.printer.guide.guides.FlowerPotGuide;
import me.aleksilassila.litematica.printer.guide.guides.HopperGuide;
import me.aleksilassila.litematica.printer.guide.guides.LadderGuide;
import me.aleksilassila.litematica.printer.guide.guides.LanternGuide;
import me.aleksilassila.litematica.printer.guide.guides.LeverGuide;
import me.aleksilassila.litematica.printer.guide.guides.LilyPadGuide;
import me.aleksilassila.litematica.printer.guide.guides.NetherPortalGuide;
import me.aleksilassila.litematica.printer.guide.guides.NoteBlockGuide;
import me.aleksilassila.litematica.printer.guide.guides.ObserverGuide;
import me.aleksilassila.litematica.printer.guide.guides.PistonGuide;
import me.aleksilassila.litematica.printer.guide.guides.RailGuide;
import me.aleksilassila.litematica.printer.guide.guides.RedstoneWireGuide;
import me.aleksilassila.litematica.printer.guide.guides.RepeaterGuide;
import me.aleksilassila.litematica.printer.guide.guides.RodGuide;
import me.aleksilassila.litematica.printer.guide.guides.SeaPickleGuide;
import me.aleksilassila.litematica.printer.guide.guides.SignGuide;
import me.aleksilassila.litematica.printer.guide.guides.SkullGuide;
import me.aleksilassila.litematica.printer.guide.guides.SlabGuide;
import me.aleksilassila.litematica.printer.guide.guides.SnowGuide;
import me.aleksilassila.litematica.printer.guide.guides.SoilGuide;
import me.aleksilassila.litematica.printer.guide.guides.StairGuide;
import me.aleksilassila.litematica.printer.guide.guides.StripLogGuide;
import me.aleksilassila.litematica.printer.guide.guides.SubstituteGuide;
import me.aleksilassila.litematica.printer.guide.guides.TorchGuide;
import me.aleksilassila.litematica.printer.guide.guides.TrapDoorGuide;
import me.aleksilassila.litematica.printer.guide.guides.TripWireHookGuide;
import me.aleksilassila.litematica.printer.guide.guides.TurtleEggGuide;
import me.aleksilassila.litematica.printer.guide.guides.VineGuide;
import me.aleksilassila.litematica.printer.guide.guides.WaterGuide;
import me.aleksilassila.litematica.printer.printer.SchematicBlockContext;
import me.aleksilassila.litematica.printer.printer.action.Action;
import net.minecraft.world.level.block.AbstractBannerBlock;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.BeetrootBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.BigDripleafStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CauldronBlock;
import net.minecraft.world.level.block.CaveVinesBlock;
import net.minecraft.world.level.block.CaveVinesPlantBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.ComparatorBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DaylightDetectorBlock;
import net.minecraft.world.level.block.DirtPathBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LavaCauldronBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.LilyPadBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RepeaterBlock;
import net.minecraft.world.level.block.RodBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.SoulFireBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TrappedChestBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.TwistingVinesBlock;
import net.minecraft.world.level.block.TwistingVinesPlantBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallSkullBlock;
import net.minecraft.world.level.block.WeepingVinesBlock;
import net.minecraft.world.level.block.WeepingVinesPlantBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.jetbrains.annotations.Nullable;

public class Guides {
   public static final Guides INSTANCE = new Guides();
   private final List<Guides.GuideRegistration> registrations = new ArrayList<>();
   private final Map<Class<?>, List<Guides.GuideRegistration>> dispatchCache = new HashMap<>();

   private Guides() {
      this.register(WaterGuide.class);
      this.register(SkipGuide.class, LiquidBlock.class, BubbleColumnBlock.class);
      this.register(LilyPadGuide.class, LilyPadBlock.class);
      this.register(TorchGuide.class, BaseTorchBlock.class);
      this.register(AmethystGuide.class, AmethystClusterBlock.class);
      this.register(FlowerGuide.class, FlowerBlock.class);
      this.register(SlabGuide.class, SlabBlock.class);
      this.register(StairGuide.class, StairBlock.class);
      this.register(TrapDoorGuide.class, TrapDoorBlock.class);
      this.register(DoorGuide.class, DoorBlock.class);
      this.register(FenceGateGuide.class, FenceGateBlock.class);
      this.register(BedGuide.class, BedBlock.class);
      this.register(BellGuide.class, BellBlock.class);
      this.register(ObserverGuide.class, ObserverBlock.class);
      this.register(PistonGuide.class, PistonBaseBlock.class);
      this.register(ChestGuide.class, ChestBlock.class, TrappedChestBlock.class);
      this.register(SignGuide.class, StandingSignBlock.class, WallSignBlock.class, WallHangingSignBlock.class, CeilingHangingSignBlock.class);
      this.register(BannerGuide.class, AbstractBannerBlock.class);
      this.register(SkullGuide.class, SkullBlock.class, WallSkullBlock.class);
      this.register(NetherPortalGuide.class, NetherPortalBlock.class);
      this.register(LadderGuide.class, LadderBlock.class);
      this.register(LanternGuide.class, LanternBlock.class);
      this.register(RodGuide.class, RodBlock.class);
      this.register(HopperGuide.class, HopperBlock.class);
      this.register(AnvilGuide.class, AnvilBlock.class);
      this.register(StripLogGuide.class, RotatedPillarBlock.class);
      this.register(CocoaGuide.class, CocoaBlock.class);
      this.register(TripWireHookGuide.class, TripWireHookBlock.class);
      this.register(RailGuide.class, BaseRailBlock.class);
      this.register(CrafterGuide.class, CrafterBlock.class);
      this.register(CandleGuide.class, CandleBlock.class);
      this.register(SeaPickleGuide.class, SeaPickleBlock.class);
      this.register(TurtleEggGuide.class, TurtleEggBlock.class);
      this.register(RepeaterGuide.class, RepeaterBlock.class);
      this.register(ComparatorGuide.class, ComparatorBlock.class);
      this.register(RedstoneWireGuide.class, RedStoneWireBlock.class);
      this.register(LeverGuide.class, LeverBlock.class);
      this.register(CampfireGuide.class, CampfireBlock.class);
      this.register(CropsGuide.class, AttachedStemBlock.class, StemBlock.class, CropBlock.class, BeetrootBlock.class);
      this.register(NoteBlockGuide.class, NoteBlock.class);
      this.register(SnowGuide.class, SnowLayerBlock.class);
      this.register(EndPortalFrameGuide.class, EndPortalFrameBlock.class);
      this.register(DaylightDetectorGuide.class, DaylightDetectorBlock.class);
      this.register(FlowerBedGuide.class, FlowerBedBlock.class);
      this.register(VineGuide.class, VineBlock.class, GlowLichenBlock.class);
      this.register(FireGuide.class, FireBlock.class, SoulFireBlock.class);
      this.register(CauldronGuide.class, CauldronBlock.class, LavaCauldronBlock.class, LayeredCauldronBlock.class);
      this.register(ComposterGuide.class, ComposterBlock.class);
      this.register(SoilGuide.class, FarmlandBlock.class, DirtPathBlock.class);
      this.register(FlowerPotGuide.class, FlowerPotBlock.class);
      this.register(
         ClimbingPlantGuide.class,
         BigDripleafStemBlock.class,
         CaveVinesBlock.class,
         CaveVinesPlantBlock.class,
         WeepingVinesBlock.class,
         WeepingVinesPlantBlock.class,
         TwistingVinesBlock.class,
         TwistingVinesPlantBlock.class
      );
      this.register(SubstituteGuide.class);
      this.register(DefaultGuide.class);
   }

   @SafeVarargs
   public final void register(Class<? extends Guide> guideClass, Class<? extends Block>... supportedBlocks) {
      this.registrations.add(new Guides.GuideRegistration(guideClass, supportedBlocks));
   }

   public final Optional<Action> buildAction(SchematicBlockContext context) {
      BlockMatchResult blockMatchResult = BlockMatchResult.compare(context);
      Class<?> blockClass = context.requiredState.getBlock().getClass();
      List<Guides.GuideRegistration> matches = this.dispatchCache.get(blockClass);
      if (matches == null) {
         matches = new ArrayList<>();
         Block block = context.requiredState.getBlock();

         for (Guides.GuideRegistration registration : this.registrations) {
            if (registration.matches(block)) {
               matches.add(registration);
            }
         }

         this.dispatchCache.put(blockClass, matches);
      }

      for (Guides.GuideRegistration registrationx : matches) {
         Guide guide = registrationx.create(context);
         if (guide != null && guide.canExecute()) {
            Result result = guide.buildAction(blockMatchResult);
            if (result.hasAction()) {
               return result.toOptional();
            }

            if (result.skipOtherGuide()) {
               break;
            }
         }
      }

      return Optional.empty();
   }

   private static class GuideRegistration {
      private final Class<? extends Guide> guideClass;
      private final MethodHandle constructor;
      public final Class<? extends Block>[] blockClass;

      public GuideRegistration(Class<? extends Guide> guideClass, Class<? extends Block>[] blockClass) {
         try {
            Constructor<? extends Guide> ctor = guideClass.getConstructor(SchematicBlockContext.class);
            this.constructor = MethodHandles.lookup().unreflectConstructor(ctor).asType(MethodType.methodType(Guide.class, SchematicBlockContext.class));
         } catch (IllegalAccessException | NoSuchMethodException var4) {
            throw new IllegalArgumentException("Guide must expose a SchematicBlockContext constructor: " + guideClass.getName(), var4);
         }

         this.guideClass = guideClass;
         this.blockClass = blockClass;
      }

      @Nullable
      public Guide create(SchematicBlockContext context) {
         try {
            return (Guide)this.constructor.invokeExact((SchematicBlockContext)context);
         } catch (Throwable var3) {
            Reference.LOGGER.error("Failed to create printer guide {}", this.guideName(), var3);
            return null;
         }
      }

      public boolean matches(Block block) {
         if (this.blockClass.length == 0) {
            return true;
         } else {
            for (Class<? extends Block> clazz : this.blockClass) {
               if (clazz.isInstance(block)) {
                  return true;
               }
            }

            return false;
         }
      }

      public String guideName() {
         return this.guideClass.getName();
      }
   }
}
