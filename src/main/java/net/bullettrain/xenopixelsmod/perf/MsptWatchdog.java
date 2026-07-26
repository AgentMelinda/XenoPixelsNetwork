package net.bullettrain.xenopixelsmod.perf;

import net.bullettrain.xenopixelsmod.config.XenoPerfConfig;
import net.minecraft.server.MinecraftServer;

/**
 * Rolling MSPT average for stress-mode ship sleep.
 */
public final class MsptWatchdog {
    private static final int MAX_SAMPLES = 1200; // 60s @ 20 tps
    private static final long[] samples = new long[MAX_SAMPLES];
    private static int head;
    private static int count;
    private static long lastNanos = -1L;
    private static boolean stressed;
    private static long lastStressLogMs;

    private MsptWatchdog() {}

    public static void onServerTick(MinecraftServer server) {
        if (!XenoPerfConfig.perfEnabled || !XenoPerfConfig.msptWatchdogEnabled) {
            stressed = false;
            lastNanos = -1L;
            return;
        }
        long now = System.nanoTime();
        if (lastNanos > 0L) {
            long dt = now - lastNanos;
            samples[head] = dt;
            head = (head + 1) % MAX_SAMPLES;
            if (count < MAX_SAMPLES) count++;
        }
        lastNanos = now;

        double avgMs = averageMspt();
        boolean was = stressed;
        stressed = avgMs >= XenoPerfConfig.msptThreshold;
        if (stressed && !was) {
            long t = System.currentTimeMillis();
            if (t - lastStressLogMs > 15_000L) {
                lastStressLogMs = t;
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                        "Perf stress: avg MSPT {} >= {} — shrinking VS2 hot range x{}",
                        String.format("%.1f", avgMs),
                        XenoPerfConfig.msptThreshold,
                        XenoPerfConfig.msptStressHotScale);
            }
        }
    }

    public static boolean isStressed() {
        return stressed && XenoPerfConfig.msptWatchdogEnabled && XenoPerfConfig.perfEnabled;
    }

    public static double averageMspt() {
        int window = Math.min(count, XenoPerfConfig.msptWindowSeconds * 20);
        if (window <= 0) return 0.0;
        long sum = 0L;
        for (int i = 0; i < window; i++) {
            int idx = (head - 1 - i + MAX_SAMPLES) % MAX_SAMPLES;
            sum += samples[idx];
        }
        return (sum / (double) window) / 1_000_000.0;
    }

    public static double effectiveHotRange() {
        double hot = XenoPerfConfig.vs2HotRange;
        if (isStressed()) {
            hot *= XenoPerfConfig.msptStressHotScale;
        }
        return Math.max(24.0, hot);
    }

    public static double effectiveWarmRange() {
        double warm = XenoPerfConfig.vs2WarmRange;
        double hot = effectiveHotRange();
        if (warm < hot) warm = hot;
        if (isStressed()) {
            warm = Math.max(hot, warm * XenoPerfConfig.msptStressHotScale);
        }
        return warm;
    }

    public static void reset() {
        head = 0;
        count = 0;
        lastNanos = -1L;
        stressed = false;
    }
}
