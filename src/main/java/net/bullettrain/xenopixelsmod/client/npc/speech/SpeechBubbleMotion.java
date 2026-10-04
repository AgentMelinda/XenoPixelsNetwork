package net.bullettrain.xenopixelsmod.client.npc.speech;

/** Pure layout and easing rules for short-lived ambient speech bubbles. */
public final class SpeechBubbleMotion {
    private static final double RISE_TICKS = 8.0;
    private static final double SETTLE_TICKS = 12.0;
    private static final double FADE_TICKS = 10.0;
    private static final float RISE_BLOCKS = 0.16f;

    private SpeechBubbleMotion() {}

    /** Height is an offset above the head; zero places the tail exactly at head height. */
    public static float anchorHeight(float entityHeight, float configuredHeight,
                                     float previousDefault) {
        if (!Float.isFinite(entityHeight) || entityHeight <= 0.0f) entityHeight = 1.0f;
        if (!Float.isFinite(configuredHeight)) configuredHeight = previousDefault;
        return entityHeight + Math.max(0.0f, configuredHeight);
    }

    /** Eased rise followed by an eased return to the anchor as the bubble expires. */
    public static float verticalOffset(long shownAt, long expiresAt, double now) {
        double age = Math.max(0.0, now - shownAt);
        double remaining = Math.max(0.0, expiresAt - now);
        return (float) (RISE_BLOCKS * smoothstep(age / RISE_TICKS)
                * smoothstep(remaining / SETTLE_TICKS));
    }

    /** Smooth opacity falloff over the last ten ticks. */
    public static float alpha(long expiresAt, double now) {
        double remaining = Math.max(0.0, expiresAt - now);
        return (float) smoothstep(remaining / FADE_TICKS);
    }

    /** A stable left/right choice shared by clients for a given entity. */
    public static int side(int entityHash) {
        return (entityHash & 1) == 0 ? -1 : 1;
    }

    private static double smoothstep(double value) {
        double t = Math.max(0.0, Math.min(1.0, value));
        return t * t * (3.0 - 2.0 * t);
    }
}
