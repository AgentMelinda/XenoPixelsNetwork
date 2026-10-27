package net.bullettrain.xenopixelsmod.space;

import net.minecraft.world.phys.Vec3;

/** Pure bounded camera displacement for the period before the destination player can tick. */
public final class HandoffCameraCoast {
    public static final double MAX_OFFSET_BLOCKS = 4.0;
    private static final double TICKS_PER_SECOND = 20.0;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double MIN_DECAY_BLOCKS_PER_SECOND = 1.0;
    private static final double TERMINAL_DECAY_BLOCKS_PER_SECOND = 2.0;

    private HandoffCameraCoast() {}

    /**
     * Converts destination-frame velocity (Minecraft blocks/tick) and elapsed wall time into a
     * bounded displacement. Invalid velocity and non-positive elapsed time produce no movement.
     */
    public static Vec3 offset(Vec3 velocityPerTick, long elapsedNanos) {
        if (velocityPerTick == null || elapsedNanos <= 0) return Vec3.ZERO;
        double x = velocityPerTick.x;
        double y = velocityPerTick.y;
        double z = velocityPerTick.z;
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) return Vec3.ZERO;

        double scale = Math.max(Math.abs(x), Math.max(Math.abs(y), Math.abs(z)));
        if (scale == 0.0) return Vec3.ZERO;

        // Normalize before measuring length so very large but finite packet values cannot overflow
        // the direction calculation. The final distance is capped before it is used as a position.
        double nx = x / scale;
        double ny = y / scale;
        double nz = z / scale;
        double normalizedLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
        double dx = nx / normalizedLength;
        double dy = ny / normalizedLength;
        double dz = nz / normalizedLength;
        double elapsedTicks = elapsedNanos * TICKS_PER_SECOND / NANOS_PER_SECOND;
        double distance = scale * normalizedLength * elapsedTicks;
        if (!Double.isFinite(distance) || distance >= MAX_OFFSET_BLOCKS) {
            distance = MAX_OFFSET_BLOCKS;
        }
        return new Vec3(dx * distance, dy * distance, dz * distance);
    }

    /** Begin following after a position packet installs the new pose and its chunk exists. */
    public static boolean mayBeginPlayerFollow(boolean positionPacketReceived, boolean playerChunkReady,
                                                Vec3 cameraPlayerOffset) {
        return positionPacketReceived && playerChunkReady && cameraPlayerOffset != null
                && finite(cameraPlayerOffset);
    }

    /** An armed transition that misses its capture deadline must be discarded as a whole. */
    public static boolean shouldExpireArmedTransition(boolean armed, long elapsedNanos, long timeoutNanos) {
        return armed && elapsedNanos >= timeoutNanos;
    }

    /** Do not start the handoff timeout until vanilla has begun replacing the source level. */
    public static boolean shouldReleaseSourceFrameAfterPreBlendTimeout(boolean coastClockStarted, boolean blending,
                                                                        long elapsedNanos, long timeoutNanos) {
        return coastClockStarted && !blending && elapsedNanos >= timeoutNanos;
    }

    /** Hard bound for releasing coast state and its captured source frame, even if camera sampling stops. */
    public static boolean shouldReleaseAtHardEnd(boolean coastClockStarted, long elapsedNanos, long hardEndNanos) {
        return coastClockStarted && elapsedNanos >= hardEndNanos;
    }

    /**
     * Decays camera/player separation without pulling the camera backward during normal forward
     * travel. Perpendicular and idle residuals settle at a small wall-clock rate; at the terminal
     * deadline the bounded remainder is eased out at the terminal rate.
     */
    public static Vec3 decayOffset(Vec3 offset, Vec3 observedPlayerDisplacement,
                                  long elapsedNanos, boolean terminalSettle) {
        if (offset == null || !finite(offset)) return Vec3.ZERO;
        if (observedPlayerDisplacement == null || !finite(observedPlayerDisplacement)) return offset;
        if (elapsedNanos <= 0) return offset;
        double length = offset.length();
        if (length == 0.0) return Vec3.ZERO;
        double elapsedSeconds = elapsedNanos / NANOS_PER_SECOND;
        double playerDistance = observedPlayerDisplacement.length();
        double speed = playerDistance / elapsedSeconds;
        if (!Double.isFinite(speed) || speed <= 1.0) {
            Vec3 direction = offset.scale(1.0 / length);
            double decayRate = terminalSettle ? TERMINAL_DECAY_BLOCKS_PER_SECOND
                    : MIN_DECAY_BLOCKS_PER_SECOND;
            double amount = Math.min(length, decayRate * elapsedSeconds);
            double towardPlayer = observedPlayerDisplacement.dot(direction);
            // If the player is still moving toward the camera offset, never remove more than that
            // observed travel; the view may slow to a stop but cannot reverse against the player.
            if (towardPlayer > 0.0) amount = Math.min(amount, towardPlayer);
            return offset.subtract(direction.scale(amount));
        }

        Vec3 movementDirection = observedPlayerDisplacement.scale(1.0 / playerDistance);
        double parallelAmount = offset.dot(movementDirection);
        Vec3 parallel = movementDirection.scale(parallelAmount);
        Vec3 perpendicular = offset.subtract(parallel);

        if (parallelAmount > 0.0) {
            double maximumCorrection = terminalSettle
                    ? Math.min(playerDistance, TERMINAL_DECAY_BLOCKS_PER_SECOND * elapsedSeconds)
                    : playerDistance * 0.5;
            parallelAmount -= Math.min(parallelAmount, maximumCorrection);
        } else if (parallelAmount < 0.0) {
            double catchUp = terminalSettle
                    ? Math.max(TERMINAL_DECAY_BLOCKS_PER_SECOND * elapsedSeconds, playerDistance * 0.5)
                    : Math.max(MIN_DECAY_BLOCKS_PER_SECOND * elapsedSeconds, playerDistance * 0.5);
            parallelAmount += Math.min(-parallelAmount, catchUp);
        }
        double perpendicularRate = terminalSettle ? TERMINAL_DECAY_BLOCKS_PER_SECOND
                : MIN_DECAY_BLOCKS_PER_SECOND;
        double perpendicularCorrection = Math.max(perpendicularRate * elapsedSeconds, playerDistance * 0.5);
        Vec3 settledPerpendicular = towardZero(perpendicular, perpendicularCorrection);
        return movementDirection.scale(parallelAmount).add(settledPerpendicular);
    }

    private static Vec3 towardZero(Vec3 value, double amount) {
        double length = value.length();
        if (length == 0.0 || amount <= 0.0) return value;
        return value.scale(Math.max(0.0, length - amount) / length);
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
