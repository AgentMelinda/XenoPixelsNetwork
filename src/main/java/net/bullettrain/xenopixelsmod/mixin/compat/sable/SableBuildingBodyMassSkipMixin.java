package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Companion to SablePlotLoadFinalizeDeferMixin. Sable 2.0.3 SubLevelPhysicsSystem.tickPipelinePhysics calls
 * ServerSubLevel.updateMergedMassData once per body per substep (javap offset 144, the only call site), which recomputes
 * a half-built planet's merged mass after every batch and moved the deferred cost into the physics step (S11 t20h
 * profile: updateMergedMassData 380 of the slow-tick weight). While a body is under a batched planet build with the
 * finalize deferred, its merged mass stays at the first batch's values; the final batch's ServerLevelPlot.load
 * finalize computes and uploads the real values once. The body floats with no forces in the zero-gravity Voidsea.
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false)
public abstract class SableBuildingBodyMassSkipMixin {
    @Redirect(method = "tickPipelinePhysics(Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;updateMergedMassData(F)V"),
            require = 1)
    private void xenopixels$skipBuildingMass(ServerSubLevel body, float partialTick) {
        if (!PhysicalEarth.defersBuildMass(body)) body.updateMergedMassData(partialTick);
    }
}
