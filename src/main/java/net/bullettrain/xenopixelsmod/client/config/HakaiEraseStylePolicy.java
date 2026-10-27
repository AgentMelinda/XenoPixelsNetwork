package net.bullettrain.xenopixelsmod.client.config;

/** Pure migration rule for the client-side Hakai presentation default. */
public final class HakaiEraseStylePolicy {
    public static final int IMPACT_DEFAULT_VERSION = 10;

    private HakaiEraseStylePolicy() {}

    /** Migrate configs written before the current default selected the legacy top-down sweep. */
    public static boolean shouldUpgradeDefaultToImpact(int configVersion, int impactDefaultVersion, String style) {
        return configVersion < impactDefaultVersion
                && (style == null || "sweep".equalsIgnoreCase(style.trim()));
    }
}
