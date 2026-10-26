package net.bullettrain.xenopixelsmod.space;

/** Pure timing rules for the local screenshot overlay used across a dimension handoff. */
public final class HandoffOverlayTiming {
    public static final long MAX_HOLD_NANOS = 1_000_000_000L;
    public static final long FADE_MILLIS = 600L;

    private HandoffOverlayTiming() {}

    public static boolean shouldStartFade(boolean destinationReady, long nowNanos, long capturedAtNanos) {
        return destinationReady || nowNanos - capturedAtNanos >= MAX_HOLD_NANOS;
    }

    public static float alpha(long fadeElapsedMillis) {
        return alpha(fadeElapsedMillis, FADE_MILLIS);
    }

    public static float alpha(long fadeElapsedMillis, long fadeMillis) {
        if (fadeMillis <= 0) return 0.0f;
        if (fadeElapsedMillis <= 0) return 1.0f;
        if (fadeElapsedMillis >= fadeMillis) return 0.0f;
        return 1.0f - (float) fadeElapsedMillis / fadeMillis;
    }

    public static boolean shouldDrawCapturedFrame(boolean captured, boolean fading,
                                                   long fadeElapsedMillis, long fadeMillis) {
        return captured && (!fading || alpha(fadeElapsedMillis, fadeMillis) > 0.0f);
    }
}
