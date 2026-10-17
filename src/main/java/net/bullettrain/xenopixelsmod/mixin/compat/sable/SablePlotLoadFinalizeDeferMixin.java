package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sable 2.0.3 ServerLevelPlot.load(CompoundTag) ends with a body finalize: updateMergedMassData(1.0f) then
 * PhysicsPipeline.onStatsChanged (native setCenterOfMass + setMassPropertiesFrom + setLocalBounds), at two call sites
 * (javap offsets 1444/1454 and 1649/1659). A batched planet build calls load() once per batch, so the finalize runs
 * over the whole grown body every batch (Qwen Q-047); S11 per-batch timing shows recurring 50-67 ms batches late in a
 * Sun build. While {@link PlotBodyBuilder#deferringFinalize()} is set (only around a non-final batch of a batched build,
 * switch XenoSpaceConfig.planetBuildDeferFinalize), both calls are skipped; the final batch's load() runs them once.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot", remap = false)
public abstract class SablePlotLoadFinalizeDeferMixin {
    @Redirect(method = "load(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;updateMergedMassData(F)V"),
            require = 2)
    private void xenopixels$deferMass(ServerSubLevel body, float partialTick) {
        if (!PlotBodyBuilder.deferringFinalize()) body.updateMergedMassData(partialTick);
    }

    @Redirect(method = "load(Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;onStatsChanged(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;)V"),
            require = 2)
    private void xenopixels$deferStats(PhysicsPipeline pipeline, ServerSubLevel body) {
        if (!PlotBodyBuilder.deferringFinalize()) pipeline.onStatsChanged(body);
    }
}
