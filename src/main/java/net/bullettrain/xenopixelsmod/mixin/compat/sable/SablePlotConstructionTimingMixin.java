package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import net.bullettrain.xenopixelsmod.space.physics.SableAllocationTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Measures the Sable plot constructor only inside PlotBodyBuilder's opt-in allocation trace. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public abstract class SablePlotConstructionTimingMixin {
    @Unique private long xenopixels$plotConstructionStarted;

    @Inject(method = "<init>(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;IIILdev/ryanhcode/sable/sublevel/ServerSubLevel;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private void xenopixels$startPlotConstruction(CallbackInfo callback) {
        xenopixels$plotConstructionStarted = SableAllocationTiming.isActive() ? System.nanoTime() : 0L;
    }

    @Inject(method = "<init>(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;IIILdev/ryanhcode/sable/sublevel/ServerSubLevel;)V",
            at = @At("RETURN"), require = 1, remap = false)
    private void xenopixels$finishPlotConstruction(CallbackInfo callback) {
        long started = xenopixels$plotConstructionStarted;
        if (started != 0L) SableAllocationTiming.recordPlotConstruction(System.nanoTime() - started);
    }
}
