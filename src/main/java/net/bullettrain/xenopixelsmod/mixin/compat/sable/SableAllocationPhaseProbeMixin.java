package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.physics.SableAllocationTiming;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Times the two synchronous Sable physics-observer stages only inside a scoped PlotBodyBuilder allocation. */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem", remap = false)
public abstract class SableAllocationPhaseProbeMixin {
    @Redirect(method = "onSubLevelAdded(Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;buildMassTracker()V"),
            require = 1, remap = false)
    private void xenopixels$timeMassTracker(ServerSubLevel body) {
        if (!XenoSpaceConfig.sableAllocationPhaseProbe() || !SableAllocationTiming.isActive()) {
            body.buildMassTracker();
            return;
        }
        long started = System.nanoTime();
        try { body.buildMassTracker(); }
        finally { SableAllocationTiming.recordMassTracker(System.nanoTime() - started); }
    }

    @Redirect(method = "onSubLevelAdded(Ldev/ryanhcode/sable/sublevel/SubLevel;)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;add(Ldev/ryanhcode/sable/sublevel/ServerSubLevel;Ldev/ryanhcode/sable/companion/math/Pose3dc;)V"),
            require = 1, remap = false)
    private void xenopixels$timePipelineAdd(PhysicsPipeline pipeline, ServerSubLevel body, Pose3dc pose) {
        if (!XenoSpaceConfig.sableAllocationPhaseProbe() || !SableAllocationTiming.isActive()) {
            pipeline.add(body, pose);
            return;
        }
        long started = System.nanoTime();
        try { pipeline.add(body, pose); }
        finally { SableAllocationTiming.recordPipelineAdd(System.nanoTime() - started); }
    }
}
