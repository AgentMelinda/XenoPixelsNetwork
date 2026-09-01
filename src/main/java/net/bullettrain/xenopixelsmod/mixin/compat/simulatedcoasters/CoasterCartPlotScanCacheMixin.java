package net.bullettrain.xenopixelsmod.mixin.compat.simulatedcoasters;

import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.bullettrain.xenopixelsmod.compat.sable.CoasterBearingScanCache;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Target: Simulated Coasters {@code CoasterCartPlotScan.scanBearingCells}
 *         (javap of simulatedcoasters-0.1.4).
 * Reason: Spark mYjF2cjFbf — full plot cell walk every client tick (rail
 *         sound) and every frame (cart render). {@code representativeBearingPlotPos}
 *         just calls this.
 * Version: simulatedcoasters 0.1.4. Result is a {@code BearingScanResult} record.
 * Side: common (client-heavy). Gated on simulatedcoasters.
 */
@Mixin(targets = "dev.silvergold.simulatedcoasters.track.cart.CoasterCartPlotScan", remap = false)
public abstract class CoasterCartPlotScanCacheMixin {

    @Inject(
            method = "scanBearingCells(Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;Lnet/minecraft/world/level/block/Block;)Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartPlotScan$BearingScanResult;",
            at = @At("HEAD"),
            cancellable = true,
            require = 0
    )
    private static void xenopixels$hitCache(LevelPlot plot, Block block, CallbackInfoReturnable<Object> cir) {
        Object cached = CoasterBearingScanCache.get(plot, block);
        if (cached != null) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
            method = "scanBearingCells(Ldev/ryanhcode/sable/sublevel/plot/LevelPlot;Lnet/minecraft/world/level/block/Block;)Ldev/silvergold/simulatedcoasters/track/cart/CoasterCartPlotScan$BearingScanResult;",
            at = @At("RETURN"),
            require = 0
    )
    private static void xenopixels$storeCache(LevelPlot plot, Block block, CallbackInfoReturnable<Object> cir) {
        if (cir.getReturnValue() != null) {
            CoasterBearingScanCache.put(plot, block, cir.getReturnValue());
        }
    }
}
