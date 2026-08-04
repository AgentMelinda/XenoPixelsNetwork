package net.bullettrain.xenopixelsmod.perf;

import net.minecraft.server.MinecraftServer;

/**
 * Removed: Create contraption freeze / coupled VS sleep.
 * Stub kept for any leftover references.
 */
public final class CreatePerfHooks {
    private CreatePerfHooks() {}

    public static boolean isCreateLoaded() {
        return false;
    }

    public static int lastContraptionCount() {
        return 0;
    }

    public static int lastFrozenCount() {
        return 0;
    }

    public static void onServerTick(MinecraftServer server) {
    }

    public static void onShipColdParked(long shipId) {
    }

    public static void onShipWoken(long shipId) {
    }

    public static void clear() {
    }
}
