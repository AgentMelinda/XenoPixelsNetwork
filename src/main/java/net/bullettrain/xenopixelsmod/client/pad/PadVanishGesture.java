package net.bullettrain.xenopixelsmod.client.pad;

/** Hysteresis for the BT3 guard-plus-stick vanish gesture. */
final class PadVanishGesture {
    private final float engageThreshold;
    private final float releaseThreshold;
    private boolean armed = true;

    PadVanishGesture(float engageThreshold, float releaseThreshold) {
        this.engageThreshold = engageThreshold;
        this.releaseThreshold = releaseThreshold;
    }

    int sample(float horizontal, boolean guardHeld) {
        if (Math.abs(horizontal) <= releaseThreshold) {
            armed = true;
            return 0;
        }
        if (!armed || !guardHeld || Math.abs(horizontal) < engageThreshold) return 0;
        armed = false;
        return horizontal < 0f ? -1 : 1;
    }

    void reset() {
        armed = true;
    }
}
