package net.bullettrain.xenopixelsmod.config;

/** Pure migration/clamping policy for shared generated-body voxel and elapsed-time budgets. */
public final class GeneratedBodyBuildPolicy {
    public static final int CONFIG_VERSION = 30;
    private static final int VOXEL_BUDGET_VERSION = 19;
    private static final int MILLIS_BUDGET_VERSION = 20;
    public static final int DEFAULT_VOXELS_PER_TICK = 2_000;
    public static final int MAX_VOXELS_PER_TICK = 2_000;
    /** Global best-effort cap shared among every generated-body owner before indivisible work. */
    public static final double DEFAULT_MILLIS_PER_TICK = 8.0;
    public static final double MAX_MILLIS_PER_TICK = 20.0;

    public record Settings(boolean enabled, int voxelsPerTick, double millisPerTick) {
        public Settings {
            if (voxelsPerTick < 1 || voxelsPerTick > MAX_VOXELS_PER_TICK)
                throw new IllegalArgumentException("generated-body voxel budget must be in [1, "
                        + MAX_VOXELS_PER_TICK + "]");
            if (!Double.isFinite(millisPerTick) || millisPerTick < 0.0 || millisPerTick > MAX_MILLIS_PER_TICK)
                throw new IllegalArgumentException("generated-body time budget must be in [0, "
                        + MAX_MILLIS_PER_TICK + "]");
        }

        public int activeVoxelsPerTick() { return enabled ? voxelsPerTick : 0; }
    }

    private GeneratedBodyBuildPolicy() {}

    public static Settings resolve(Integer configVersion, int configuredVoxels, boolean enabled) {
        boolean legacy = configVersion == null || configVersion < MILLIS_BUDGET_VERSION;
        return resolve(configVersion, configuredVoxels, enabled,
                legacy ? 0.0 : DEFAULT_MILLIS_PER_TICK, !legacy);
    }

    public static Settings resolve(Integer configVersion, int configuredVoxels, boolean enabled,
                                    double configuredMillis) {
        return resolve(configVersion, configuredVoxels, enabled, configuredMillis, true);
    }

    public static Settings resolve(Integer configVersion, int configuredVoxels, boolean enabled,
                                   double configuredMillis, boolean millisSettingPresent) {
        int voxels = configuredVoxels;
        // Versionless configs predate the versioned migration contract. Treat them as legacy too:
        // otherwise an explicit old zero would be clamped to 1 voxel/tick instead of receiving
        // the bounded default introduced by this schema.
        if ((configVersion == null || configVersion < VOXEL_BUDGET_VERSION) && voxels <= 0)
            voxels = DEFAULT_VOXELS_PER_TICK;
        voxels = Math.max(1, Math.min(MAX_VOXELS_PER_TICK, voxels));
        boolean legacy = configVersion == null || configVersion < MILLIS_BUDGET_VERSION;
        double millis;
        if (legacy && !millisSettingPresent) {
            millis = DEFAULT_MILLIS_PER_TICK;
        } else if (Double.isFinite(configuredMillis)) {
            millis = Math.max(0.0, Math.min(MAX_MILLIS_PER_TICK, configuredMillis));
        } else {
            millis = legacy ? DEFAULT_MILLIS_PER_TICK : 0.0;
        }
        return new Settings(enabled, voxels, millis);
    }
}
