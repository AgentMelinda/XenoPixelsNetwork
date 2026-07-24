package net.bullettrain.xenopixelsmod.client.hud;

/**
 * Small, allocation-free animation helpers shared by the LDLib-backed HUD
 * views. These are pure client-side visual polish (color/value interpolation
 * computed each frame from {@link System#currentTimeMillis()}); they don't
 * change any underlying game data or DMZ behavior.
 */
public final class AnimUtil {
    private AnimUtil() {
    }

    /** Smooth 0..1 sine pulse with the given period in milliseconds. */
    public static float pulse01(long periodMs) {
        if (periodMs <= 0) return 0f;
        long t = System.currentTimeMillis() % periodMs;
        double phase = (t / (double) periodMs) * Math.PI * 2.0;
        return (float) ((Math.sin(phase) + 1.0) * 0.5);
    }

    /** Same as {@link #pulse01(long)} but offset so multiple elements can shimmer out of phase. */
    public static float pulse01(long periodMs, long phaseOffsetMs) {
        if (periodMs <= 0) return 0f;
        long t = (System.currentTimeMillis() + phaseOffsetMs) % periodMs;
        double phase = (t / (double) periodMs) * Math.PI * 2.0;
        return (float) ((Math.sin(phase) + 1.0) * 0.5);
    }

    public static float lerp(float from, float to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return from + (to - from) * t;
    }

    /** Exponential ease toward a target — call every frame with a fixed rate (0..1, higher = snappier). */
    public static float ease(float current, float target, float rate) {
        return lerp(current, target, rate);
    }

    public static int lerpColor(int from, int to, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int fa = (from >>> 24) & 0xFF, fr = (from >>> 16) & 0xFF, fgc = (from >>> 8) & 0xFF, fb = from & 0xFF;
        int ta = (to >>> 24) & 0xFF, tr = (to >>> 16) & 0xFF, tgc = (to >>> 8) & 0xFF, tb = to & 0xFF;
        int a = Math.round(fa + (ta - fa) * t);
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fgc + (tgc - fgc) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
