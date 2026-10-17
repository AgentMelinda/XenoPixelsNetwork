package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Per-body alternative to ignore_chunks (Qwen's C3 proposal). Sable 2.0.3 PhysicsChunkTicketManager.update loops
 * over every sub-level and, per tick, sweeps every 16^3 section of its predicted world AABB twice (addTicket for world
 * colliders, inhabitChunk to force-load world chunks under force-loaded bodies, moveToUnloaded otherwise). For the
 * Sun that is ~8,000 section probes per loop per tick. The loop's only skip is `if (sub.isRemoved()) continue`
 * (javap: update offsets 496-502, the only isRemoved call in the method), so reporting managed planets as removed
 * there skips exactly that body's sweep while ships keep world-block collision. A planet sits in empty space, has no
 * world blocks to hit, and stays loaded through its own force-load ticket. Switch:
 * XenoSpaceConfig.planetSkipChunkTickets (off until proven at runtime).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager", remap = false)
public abstract class SablePlanetChunkTicketSkipMixin {
    @Redirect(method = "update(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V",
            at = @At(value = "INVOKE", target = "Ldev/ryanhcode/sable/sublevel/ServerSubLevel;isRemoved()Z"),
            require = 1)
    private boolean xenopixels$skipPlanetSweep(ServerSubLevel body) {
        return body.isRemoved() || PhysicalEarth.skipsChunkTickets(body);
    }
}
