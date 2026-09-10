package net.bullettrain.xenopixelsmod.client.combat;

import net.bullettrain.xenopixelsmod.combat.Bt3SparkingCharge;

/** Owner-client state for the full-ki charge that precedes Sparking. */
public final class SparkingChargeClientState {
    private static boolean charging;
    private static int litSegments;

    private SparkingChargeClientState() {
    }

    public static void set(boolean active, int segments) {
        charging = active;
        litSegments = active ? Math.max(0, Math.min(Bt3SparkingCharge.SEGMENTS, segments)) : 0;
    }

    public static boolean isCharging() {
        return charging;
    }

    public static int litSegments() {
        return litSegments;
    }

    public static void clear() {
        charging = false;
        litSegments = 0;
    }
}
