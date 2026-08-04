package net.bullettrain.xenopixelsmod.perf;

/**
 * Removed: global VS2 ship Hot/Cold sleep is no longer part of XenoPixels.
 * <p>
 * Kept as a no-op stub so any leftover call sites / soft-compat compile without
 * parking ships. Module performance is thruster/guidance/tube/chunk-loader only.
 */
public final class Vs2ShipSleepManager {
    private Vs2ShipSleepManager() {}

    /** No-op — ship sleep removed. */
    public static void forceHot(long shipId, int ticks) {
        // intentionally empty
    }

    public static int lastDynamicCount() {
        return 0;
    }

    public static int lastStaticCount() {
        return 0;
    }

    public static int lastParkedByUsCount() {
        return 0;
    }
}
