package net.bullettrain.xenopixelsmod.mixin.compat.ballistix;

import ballistix.api.missile.virtual.VirtualMissile;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.compat.ballistix.BallistixVs2Compat;
import net.bullettrain.xenopixelsmod.compat.ballistix.MissileChunkLoadManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Virtual missiles launched from VS ships:
 * <ul>
 *   <li>Project shipyard spawn → world and clear the hull roof</li>
 *   <li>Ignore collisions with the launching ship for a grace window
 *       (otherwise warhead detonates on the launcher)</li>
 *   <li>Chunkload corridor + wake</li>
 * </ul>
 */
@Mixin(value = VirtualMissile.class, remap = false)
public abstract class VirtualMissileMixin {
    /** Ticks to ignore hits on the launch ship after fire. */
    private static final int LAUNCH_GRACE_TICKS = 80;

    @Shadow
    public Vec3 position;

    @Shadow
    public Vec3 deltaMovement;

    @Unique
    private boolean xenopixels$clearedHull;

    @Unique
    private long xenopixels$launchShipId = -1L;

    @Unique
    private int xenopixels$graceTicks;

    @Inject(method = "tick", at = @At("HEAD"), remap = false, require = 0)
    private void xenopixels$virtualMissileTick(ServerLevel level, CallbackInfo ci) {
        if (position == null || level == null) return;

        // First frame: capture launch ship, clear hull, start grace
        if (!xenopixels$clearedHull) {
            xenopixels$clearedHull = true;
            // Prefer shipyard pos (pre-transform) to resolve launch ship
            long id = BallistixVs2Compat.findShipIdAt(level, position);
            Vec3 cleared = BallistixVs2Compat.toWorldLaunchSpawn(level, position);
            if (id < 0) {
                id = BallistixVs2Compat.findShipIdAt(level, cleared);
            }
            xenopixels$launchShipId = id;
            if (cleared != null && cleared.distanceToSqr(position) > 0.01) {
                position = cleared;
            }
            // Boost initial climb so loft clears superstructure
            if (xenopixels$launchShipId >= 0) {
                if (deltaMovement == null || deltaMovement.lengthSqr() < 0.01) {
                    deltaMovement = new Vec3(0, 1.0, 0);
                } else if (deltaMovement.y < 0.5) {
                    deltaMovement = new Vec3(deltaMovement.x, Math.max(1.0, deltaMovement.y + 0.8), deltaMovement.z);
                }
                xenopixels$graceTicks = LAUNCH_GRACE_TICKS;
                XenoPixelsMod.LOGGER.info(
                        "Ballistix missile grace ship={} ticks={} pos=({}, {}, {})",
                        xenopixels$launchShipId, xenopixels$graceTicks,
                        String.format("%.1f", position.x),
                        String.format("%.1f", position.y),
                        String.format("%.1f", position.z));
            }
        } else if (xenopixels$graceTicks > 0) {
            xenopixels$graceTicks--;
            // Keep nudging up if still stuck inside launch ship voxels
            if (xenopixels$launchShipId >= 0
                    && BallistixVs2Compat.isBlockOnShip(level, BlockPos.containing(position), xenopixels$launchShipId)) {
                position = position.add(0, 1.5, 0);
            }
        }

        BallistixVs2Compat.onVirtualMissileTick(level, position);

        BlockPos target = xenopixels$readTarget();
        if (target != null) {
            target = BallistixVs2Compat.toWorldBlockPos(level, target);
        }
        MissileChunkLoadManager.trackMissile(level, position, target);
    }

    /**
     * During grace, ignore collision samples that land on the launching ship
     * (would detonate the warhead on the pad).
     */
    @Inject(method = "projectMovementForCollision", at = @At("RETURN"), cancellable = true,
            remap = false, require = 0)
    private void xenopixels$ignoreLaunchShipHits(ServerLevel level, CallbackInfoReturnable<BlockPos> cir) {
        if (xenopixels$graceTicks <= 0 || xenopixels$launchShipId < 0) return;
        BlockPos hit = cir.getReturnValue();
        if (hit == null || level == null) return;
        if (BallistixVs2Compat.isBlockOnShip(level, hit, xenopixels$launchShipId)) {
            cir.setReturnValue(null);
        }
    }

    @Unique
    private BlockPos xenopixels$readTarget() {
        try {
            Object self = this;
            var f = self.getClass().getDeclaredField("targetData");
            f.setAccessible(true);
            Object td = f.get(self);
            if (td == null) return null;
            for (String name : new String[]{"target", "targetPos", "blockPos"}) {
                try {
                    var tf = td.getClass().getDeclaredField(name);
                    tf.setAccessible(true);
                    Object v = tf.get(td);
                    if (v instanceof BlockPos bp) return bp;
                } catch (NoSuchFieldException ignored) {
                }
            }
            for (var tf : td.getClass().getDeclaredFields()) {
                if (BlockPos.class.isAssignableFrom(tf.getType())) {
                    tf.setAccessible(true);
                    Object v = tf.get(td);
                    if (v instanceof BlockPos bp) return bp;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
