package net.bullettrain.xenopixelsmod.config;

/** Pure migration/clamping policy for the shared generated-body voxel budget. */
public final class GeneratedBodyBuildPolicy {
    public static final int CONFIG_VERSION = 19;
    public static final int DEFAULT_VOXELS_PER_TICK = 2_000;
    public static final int MAX_VOXELS_PER_TICK = 2_000;

    public record Settings(boolean enabled, int voxelsPerTick) {
        public Settings {
            if (voxelsPerTick < 1 || voxelsPerTick > MAX_VOXELS_PER_TICK)
                throw new IllegalArgumentException("generated-body voxel budget must be in [1, "
                        + MAX_VOXELS_PER_TICK + "]");
        }

        public int activeVoxelsPerTick() { return enabled ? voxelsPerTick : 0; }
    }

    private GeneratedBodyBuildPolicy() {}

    public static Settings resolve(Integer configVersion, int configuredVoxels, boolean enabled) {
        int voxels = configuredVoxels;
        // Versionless configs predate the versioned migration contract. Treat them as legacy too:
        // otherwise an explicit old zero would be clamped to 1 voxel/tick instead of receiving
        // the bounded default introduced by this schema.
        if ((configVersion == null || configVersion < CONFIG_VERSION) && voxels <= 0)
            voxels = DEFAULT_VOXELS_PER_TICK;
        voxels = Math.max(1, Math.min(MAX_VOXELS_PER_TICK, voxels));
        return new Settings(enabled, voxels);
    }
}
