package net.bullettrain.xenopixelsmod.combat;

/** Temporary cap policy; the native cap and skill data remain authoritative outside Sparking. */
public final class Bt3SparkingReleaseCap {
    private Bt3SparkingReleaseCap() {}

    public static int effective(int nativeCap, int configuredCeiling, boolean sparking) {
        return sparking ? Math.max(nativeCap, configuredCeiling) : nativeCap;
    }
}
