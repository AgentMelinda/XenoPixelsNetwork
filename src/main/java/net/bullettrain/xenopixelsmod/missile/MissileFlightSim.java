package net.bullettrain.xenopixelsmod.missile;

import net.minecraft.world.phys.Vec3;

/**
 * When the next chunk is not ready, treat the round as kinematic so {@code move()}
 * does not register phantom collisions on the loaded/unloaded border.
 */
public final class MissileFlightSim {
    private MissileFlightSim() {
    }

    public static boolean kinematic(boolean ejectPhase, boolean nextChunkReady) {
        return ejectPhase || !nextChunkReady;
    }

    public static boolean impactDetonation(boolean ejectPhase, boolean collided, boolean impactChunkReady) {
        return !ejectPhase && collided && impactChunkReady;
    }

    public static boolean reachedTarget(Vec3 from, Vec3 to, double targetX, double targetY, double targetZ,
                                        double radius) {
        double r2 = radius * radius;
        Vec3 delta = to.subtract(from);
        double ab2 = delta.lengthSqr();
        if (ab2 < 1.0e-12) {
            return from.distanceToSqr(targetX, targetY, targetZ) <= r2;
        }
        double tx = targetX - from.x;
        double ty = targetY - from.y;
        double tz = targetZ - from.z;
        double t = (tx * delta.x + ty * delta.y + tz * delta.z) / ab2;
        if (t < 0.0) t = 0.0;
        else if (t > 1.0) t = 1.0;
        double dx = from.x + delta.x * t - targetX;
        double dy = from.y + delta.y * t - targetY;
        double dz = from.z + delta.z * t - targetZ;
        return dx * dx + dy * dy + dz * dz <= r2;
    }
}
