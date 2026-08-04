package net.bullettrain.xenopixelsmod.perf;

import net.minecraft.server.MinecraftServer;

/**
 * Removed: MSPT-driven VS2 ship sleep. Stub for compile safety.
 */
public final class MsptWatchdog {
    private MsptWatchdog() {}

    public static void onServerTick(MinecraftServer server) {
    }

    public static boolean isStressed() {
        return false;
    }

    public static double averageMspt() {
        return 0.0;
    }

    public static double effectiveHotRange() {
        return 0.0;
    }

    public static double effectiveWarmRange() {
        return 0.0;
    }

    public static void reset() {
    }
}
