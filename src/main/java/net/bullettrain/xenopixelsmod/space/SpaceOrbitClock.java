package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.MinecraftServer;

/** One server-wide time coordinate for every analytical orbit and physical orbit controller. */
public final class SpaceOrbitClock {
    private SpaceOrbitClock() {}

    /** The overworld game clock is the authoritative tick source, independent of the active dimension. */
    public static long gameTick(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    /** Converts a server game tick to the shared orbital seconds coordinate, including the warp offset. */
    public static double seconds(MinecraftServer server) {
        return seconds(gameTick(server), XenoSpaceConfig.orbitEpochOffsetSeconds());
    }

    /** Converts an already-canonical server tick using the currently configured epoch warp. */
    public static double seconds(long gameTick) {
        return seconds(gameTick, XenoSpaceConfig.orbitEpochOffsetSeconds());
    }

    /** Maximum first-order position bridge allowed between throttled chart solves. */
    public static double maxChartBridgeSeconds() {
        return maxChartBridgeSeconds(XenoSpaceConfig.gravityIntervalTicks());
    }

    static double maxChartBridgeSeconds(int evaluationIntervalTicks) {
        return Math.max(1, evaluationIntervalTicks) / 20.0 + 0.05;
    }

    /** Pure conversion kept separate so orbit-clock semantics can be tested without a running server. */
    static double seconds(long gameTick, double epochOffsetSeconds) {
        return gameTick / 20.0 + epochOffsetSeconds;
    }
}
