package net.bullettrain.xenopixelsmod.combat.targeting;

import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3dc;

/**
 * Resolves a target's velocity for lead prediction, in blocks per second, world space.
 *
 * <p>A target riding a {@link XenoPilotSeatEntity} bound to a loaded Sable ship is moving with
 * that ship, and the ship's own velocity — not the rider's per-tick position delta, which is
 * noisy and lags a physics body — is the correct value. Everything else falls back to the
 * entity's own delta movement.
 */
public final class TargetMotionEstimator {

    private TargetMotionEstimator() {
    }

    /** Velocity in blocks/second, world space. Never null; zero when nothing better is known. */
    public static Vec3 velocityOf(ServerLevel level, Entity target) {
        if (target == null) return Vec3.ZERO;

        Entity vehicle = target.getVehicle();
        if (vehicle instanceof XenoPilotSeatEntity) {
            ServerSubLevel ship = shipUnder(level, vehicle);
            if (ship != null) {
                Vector3dc v = VsShipHelper.velocity(level, ship);
                return new Vec3(v.x(), v.y(), v.z());
            }
        }

        // getDeltaMovement is blocks/tick; the rest of this system works in blocks/second.
        return target.getDeltaMovement().scale(20.0);
    }

    /**
     * The ship a rider's vehicle is standing on, if any.
     *
     * <p>{@code VsShipHelper.getLoadedShipAt} resolves by world block position, which for a seat
     * bound to a moving hull is exactly where the seat entity itself is right now — the seat
     * always tracks the ship it belongs to (Sable carries and rotates it, per
     * {@link XenoPilotSeatEntity}'s own class javadoc), so this needs no extra bookkeeping beyond
     * the seat's live position.
     */
    private static ServerSubLevel shipUnder(ServerLevel level, Entity seatEntity) {
        return VsShipHelper.getLoadedShipAt(level, seatEntity.blockPosition());
    }
}
