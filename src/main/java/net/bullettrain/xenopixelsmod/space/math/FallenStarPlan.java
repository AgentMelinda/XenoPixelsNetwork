package net.bullettrain.xenopixelsmod.space.math;

import java.util.SplittableRandom;

/** Deterministic, bounded cosmetic meteor streaks for the space sky. */
public final class FallenStarPlan {
    public static final int WINDOW_TICKS = 600;
    public static final double SKY_RADIUS = 58.0;
    private static final int MAX_DURATION = 36;

    public record Direction(double x, double y, double z) {
        public double length() { return Math.sqrt(x * x + y * y + z * z); }
        public boolean isFinite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }

    /** start/end ticks are offsets within windowIndex, and direction endpoints are sky-local. */
    public record Streak(long windowIndex, int startTick, int endTick, Direction start, Direction end) {}

    private FallenStarPlan() {}

    /** At most one streak is scheduled in each 30-second window; density is a 0..100 chance per window. */
    public static Streak at(long worldTick, long worldSeed, int densityPercent) {
        if (worldTick < 0 || densityPercent <= 0) return null;
        int density = Math.min(100, densityPercent);
        long window = Math.floorDiv(worldTick, WINDOW_TICKS);
        SplittableRandom random = new SplittableRandom(mix(worldSeed ^ mix(window + 0xFA11_E57A2L)));
        if (random.nextInt(100) >= density) return null;
        int duration = 22 + random.nextInt(MAX_DURATION - 21);
        int start = random.nextInt(WINDOW_TICKS - MAX_DURATION);
        if (worldTick < window * WINDOW_TICKS + start || worldTick >= window * WINDOW_TICKS + start + duration)
            return null;

        double y = random.nextDouble(-0.8, 0.8);
        double phi = random.nextDouble(Math.PI * 2.0);
        double horizontal = Math.sqrt(1.0 - y * y);
        double sx = horizontal * Math.cos(phi), sy = y, sz = horizontal * Math.sin(phi);
        // Select a stable tangent direction without a pole singularity, then move a short arc across the dome.
        // |y| is at most .8, so the horizontal component is at least .6 and this tangent is non-zero.
        double tx = -sz, ty = 0, tz = sx;
        double tangentLength = Math.sqrt(tx * tx + ty * ty + tz * tz);
        tx /= tangentLength; ty /= tangentLength; tz /= tangentLength;
        if (random.nextBoolean()) { tx = -tx; ty = -ty; tz = -tz; }
        double angle = random.nextDouble(0.18, 0.42);
        double ex = sx * Math.cos(angle) + tx * Math.sin(angle);
        double ey = sy * Math.cos(angle) + ty * Math.sin(angle);
        double ez = sz * Math.cos(angle) + tz * Math.sin(angle);
        return new Streak(window, start, start + duration,
                new Direction(sx * SKY_RADIUS, sy * SKY_RADIUS, sz * SKY_RADIUS),
                new Direction(ex * SKY_RADIUS, ey * SKY_RADIUS, ez * SKY_RADIUS));
    }

    /** Smoothly fades the streak in and out; tick is relative to its 600-tick window. */
    public static double alpha(Streak streak, double tickInWindow) {
        if (streak == null || !Double.isFinite(tickInWindow)
                || tickInWindow < streak.startTick() || tickInWindow >= streak.endTick()) return 0.0;
        double age = tickInWindow - streak.startTick();
        double remaining = streak.endTick() - tickInWindow;
        return Math.max(0.0, Math.min(1.0, Math.min(age / 3.0, remaining / 5.0)));
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
