package net.bullettrain.xenopixelsmod.config;

/** Hard upper bound for in-flight chart-planet assemblies. */
public final class PlanetBuildConcurrencyPolicy {
    public static final int DEFAULT_MAX_CONCURRENT = 1;
    public static final int MAX_CONCURRENT = 8;

    private PlanetBuildConcurrencyPolicy() {}

    public static int clamp(int configured) {
        return Math.max(1, Math.min(MAX_CONCURRENT, configured));
    }

    public static boolean hasCapacity(int activeBuilds, int configuredMaximum) {
        return activeBuilds < clamp(configuredMaximum);
    }
}
