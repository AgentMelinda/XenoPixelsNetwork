package net.bullettrain.xenopixelsmod.mixin.compat.sable;

import dev.ryanhcode.sable.api.physics.PhysicsPipeline;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysics;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sable 2.0.3 PhysicsChunkTicketManager.update returns early when the dimension's datapack physics record says
 * ignore_chunks (javap: DimensionPhysicsData.of(level).ignoreChunks() at offsets 27-37; it is the only caller of
 * ignoreChunks() in the jar). With that flag Sable stops feeding world chunk sections around bodies to the native
 * pipeline and stops holding/unloading world chunks for them. In the S11 batch test this removed the remaining
 * >150 ms ticks of a batched Sun build. Side effects: bodies in such a dimension no longer collide with ordinary
 * world blocks, and Sable no longer auto-unloads bodies there. Switch: XenoSpaceConfig.ignoreChunkPhysicsDimensions
 * (empty = Sable's own value everywhere).
 */
@Mixin(targets = "dev.ryanhcode.sable.sublevel.system.ticket.PhysicsChunkTicketManager", remap = false)
public abstract class SableIgnoreChunksDimensionMixin {
    @Redirect(method = "update(Lnet/minecraft/server/level/ServerLevel;Ldev/ryanhcode/sable/api/sublevel/ServerSubLevelContainer;Ldev/ryanhcode/sable/sublevel/system/SubLevelPhysicsSystem;Ldev/ryanhcode/sable/api/physics/PhysicsPipeline;D)V",
            at = @At(value = "INVOKE",
                    target = "Ldev/ryanhcode/sable/physics/config/dimension_physics/DimensionPhysics;ignoreChunks()Z"),
            require = 1)
    private boolean xenopixels$ignoreChunksByConfig(DimensionPhysics physics, ServerLevel level, ServerSubLevelContainer container,
                                                   SubLevelPhysicsSystem system, PhysicsPipeline pipeline, double timeStep) {
        return physics.ignoreChunks() || XenoSpaceConfig.ignoresPhysicsChunks(level.dimension().location().toString());
    }
}
