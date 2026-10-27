package net.bullettrain.xenopixelsmod.space.event;

/** Pure analytic front math for the §23.1 supernova wave; no per-tick geometry is built. */
public final class StellarBlastWaveMath {
    public static final double MAX_RADIUS_BLOCKS = 30_000_000.0;
    public static final int MAX_EXPOSURE_TICKS = 1_200;

    public record Wave(double originX, double originY, double originZ, long startedAtTick,
                       double speedBlocksPerTick, int exposureTicks) {
        public Wave {
            if (!coordinate(originX) || !coordinate(originY) || !coordinate(originZ)
                    || startedAtTick < 0L || !Double.isFinite(speedBlocksPerTick)
                    || speedBlocksPerTick <= 0.0 || exposureTicks < 1
                    || exposureTicks > MAX_EXPOSURE_TICKS)
                throw new IllegalArgumentException("invalid supernova wave");
        }
    }

    private StellarBlastWaveMath() {}

    /** Radius at a game tick, saturated to Minecraft's supported world-coordinate range. */
    public static double radiusAt(Wave wave, long nowTick) {
        if (nowTick <= wave.startedAtTick()) return 0.0;
        long age = nowTick - wave.startedAtTick();
        if (age < 0L) return MAX_RADIUS_BLOCKS; // signed overflow after an extreme future tick
        return radiusAtAge(age, wave.speedBlocksPerTick());
    }

    /** Client interpolation helper; accepts fractional ticks and clamps malformed values to a bounded radius. */
    public static double radiusAtAge(double ageTicks, double speedBlocksPerTick) {
        if (!Double.isFinite(ageTicks) || !Double.isFinite(speedBlocksPerTick)
                || speedBlocksPerTick <= 0.0 || ageTicks <= 0.0) return 0.0;
        double radius = ageTicks * speedBlocksPerTick;
        return !Double.isFinite(radius) ? MAX_RADIUS_BLOCKS : Math.min(MAX_RADIUS_BLOCKS, radius);
    }

    /** True while a persisted supernova front is inside its configured lifetime. */
    public static boolean activeAt(long startedAtTick, long nowTick, long durationTicks) {
        return startedAtTick >= 0L && nowTick >= startedAtTick && durationTicks > 0L
                && nowTick - startedAtTick < durationTicks;
    }

    /** Starts exposure once per wave; subsequent samples keep the original deadline instead of extending it. */
    public static long exposureDeadline(long existingUntil, long nowTick, int exposureTicks) {
        if (exposureTicks <= 0) return nowTick;
        if (existingUntil > nowTick) return existingUntil;
        return nowTick > Long.MAX_VALUE - exposureTicks ? Long.MAX_VALUE : nowTick + exposureTicks;
    }

    /** True only while the expanding shell occupies the queried point; points behind its trailing edge are clear. */
    public static boolean exposes(Wave wave, double x, double y, double z, long nowTick) {
        if (!coordinate(x) || !coordinate(y) || !coordinate(z) || nowTick < wave.startedAtTick()) return false;
        double distance = Math.hypot(Math.hypot(x - wave.originX(), y - wave.originY()), z - wave.originZ());
        if (!Double.isFinite(distance)) return false;
        double front = radiusAt(wave, nowTick);
        double thickness = Math.min(MAX_RADIUS_BLOCKS, wave.speedBlocksPerTick() * wave.exposureTicks());
        double trailingEdge = Math.max(0.0, front - thickness);
        return distance >= trailingEdge && distance <= front;
    }

    private static boolean coordinate(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_RADIUS_BLOCKS;
    }
}
