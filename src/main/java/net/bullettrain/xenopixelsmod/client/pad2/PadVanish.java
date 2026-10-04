package net.bullettrain.xenopixelsmod.client.pad2;

/**
 * Hysteresis for the BT3 guard-plus-stick vanish gesture.
 *
 * <p>Two thresholds rather than one, and the reason is the whole class: a single threshold makes a
 * stick resting near it fire repeatedly as it jitters. The stick has to travel past
 * {@code engage} to trigger, and fall back inside {@code release} before it can trigger again.
 *
 * <p>Ported unchanged from {@code client.pad.PadVanishGesture}, which is package-private. Pure, so
 * it is testable on its own.
 */
public final class PadVanish {

    private final float engageThreshold;
    private final float releaseThreshold;
    private boolean armed = true;

    public PadVanish(float engageThreshold, float releaseThreshold) {
        this.engageThreshold = engageThreshold;
        this.releaseThreshold = releaseThreshold;
    }

    /**
     * One reading of the stick.
     *
     * @return -1 for a left vanish, +1 for right, 0 for nothing this sample
     */
    public int sample(float horizontal, boolean guardHeld) {
        if (Math.abs(horizontal) <= releaseThreshold) {
            armed = true;
            return 0;
        }
        if (!armed || !guardHeld || Math.abs(horizontal) < engageThreshold) {
            return 0;
        }
        armed = false;
        return horizontal < 0f ? -1 : 1;
    }

    public void reset() {
        armed = true;
    }
}
