package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.phys.Vec3;

/** Converts a velocity between planet-relative and chart-relative frames. Minecraft motion is blocks per tick. */
final class SpaceHandoffVelocity {
    private static final double TICKS_PER_SECOND = 20.0;

    private SpaceHandoffVelocity() {}

    /**
     * Preserve inertial velocity when changing dimensions. The chart stores each body's orbital velocity in
     * blocks per second; entity motion is in blocks per tick. Current chart and dimension axes are aligned.
     */
    static Vec3 convert(Vec3 sourceLocalPerTick, Vec3 sourceBodyOrbitPerSecond,
                        Vec3 targetBodyOrbitPerSecond, boolean enabled) {
        if (!enabled) return Vec3.ZERO;
        if (!finite(sourceLocalPerTick) || !finite(sourceBodyOrbitPerSecond) || !finite(targetBodyOrbitPerSecond))
            return Vec3.ZERO;
        return sourceLocalPerTick.add(sourceBodyOrbitPerSecond.subtract(targetBodyOrbitPerSecond)
                .scale(1.0 / TICKS_PER_SECOND));
    }

    private static boolean finite(Vec3 value) {
        return value != null && Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
