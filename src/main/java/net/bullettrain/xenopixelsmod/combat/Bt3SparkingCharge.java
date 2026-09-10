package net.bullettrain.xenopixelsmod.combat;

/** Pure arithmetic for the BT3-style full-ki Max Power charge. */
public final class Bt3SparkingCharge {
    public static final int SEGMENTS = 8;

    private Bt3SparkingCharge() {
    }

    public static int requiredTicks(int configuredTicks) {
        return Math.max(1, configuredTicks);
    }

    public static int advance(int currentTicks, int configuredTicks) {
        return Math.min(requiredTicks(configuredTicks), Math.max(0, currentTicks) + 1);
    }

    public static boolean complete(int currentTicks, int configuredTicks) {
        return currentTicks >= requiredTicks(configuredTicks);
    }

    public static int litSegments(int currentTicks, int configuredTicks) {
        if (currentTicks <= 0) return 0;
        int required = requiredTicks(configuredTicks);
        return Math.min(SEGMENTS, (int) ((long) currentTicks * SEGMENTS / required));
    }
}
