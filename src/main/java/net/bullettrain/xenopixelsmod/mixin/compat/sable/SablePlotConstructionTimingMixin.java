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
    /** Constructors cannot access instance state at HEAD before their superclass constructor returns. */
    @Unique private static final ThreadLocal<Long> xenopixels$plotConstructionStarted = new ThreadLocal<>();

    @Inject(method = "<init>(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;IIILdev/ryanhcode/sable/sublevel/ServerSubLevel;)V",
            at = @At("HEAD"), require = 1, remap = false)
    private static void xenopixels$startPlotConstruction(CallbackInfo callback) {
        if (SableAllocationTiming.isActive()) xenopixels$plotConstructionStarted.set(System.nanoTime());
        else xenopixels$plotConstructionStarted.remove();
    }

    @Inject(method = "<init>(Ldev/ryanhcode/sable/api/sublevel/SubLevelContainer;IIILdev/ryanhcode/sable/sublevel/ServerSubLevel;)V",
            at = @At("RETURN"), require = 1, remap = false)
    private static void xenopixels$finishPlotConstruction(CallbackInfo callback) {
        Long started = xenopixels$plotConstructionStarted.get();
        xenopixels$plotConstructionStarted.remove();
        if (started != null && started != 0L) SableAllocationTiming.recordPlotConstruction(System.nanoTime() - started);
    }
}
