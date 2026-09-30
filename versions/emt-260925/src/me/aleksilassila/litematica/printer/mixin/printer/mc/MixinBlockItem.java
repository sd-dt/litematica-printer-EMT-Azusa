package me.aleksilassila.litematica.printer.mixin.printer.mc;

import fi.dy.masa.litematica.util.PlacementHandler;
import fi.dy.masa.litematica.util.PlacementHandler.UseContext;
import me.aleksilassila.litematica.printer.config.Configs;
import me.aleksilassila.litematica.printer.printer.ActionManager;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(
   value = {BlockItem.class},
   priority = 1020
)
public abstract class MixinBlockItem extends Item {
   private MixinBlockItem(Properties builder) {
      super(builder);
   }

   @Shadow
   protected abstract boolean canPlace(BlockPlaceContext var1, BlockState var2);

   @Shadow
   public abstract Block getBlock();

   @Inject(
      method = {"getPlacementState"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void modifyPlacementState(BlockPlaceContext ctx, CallbackInfoReturnable<BlockState> cir) {
      boolean usePrinterProtocol = Configs.Print.EASY_PLACE_PROTOCOL.getBooleanValue() && ActionManager.INSTANCE.isEasyPlaceProtocolActive();
      if (ActionManager.INSTANCE.isPrinterInteractionActive()) {
         BlockState state = this.getBlock().getStateForPlacement(ctx);
         if (state != null && this.canPlace(ctx, state)) {
            if (usePrinterProtocol) {
               UseContext context = UseContext.from(ctx, ctx.getHand());
               state = PlacementHandler.applyPlacementProtocolToPlacementState(state, context);
            }

            cir.setReturnValue(state);
         } else {
            cir.setReturnValue(null);
         }
      }
   }
}
