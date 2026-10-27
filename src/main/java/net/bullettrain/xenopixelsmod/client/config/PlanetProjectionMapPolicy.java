package net.bullettrain.xenopixelsmod.client.config;

/** One-time migration from the legacy shared atlas default to identity-correct far planet maps. */
public final class PlanetProjectionMapPolicy {
    public static final int PER_BODY_DEFAULT_VERSION = 11;

    private PlanetProjectionMapPolicy() {}

    public static boolean shouldUpgradeToPerBodyDefault(int savedVersion, int currentVersion) {
        return currentVersion >= PER_BODY_DEFAULT_VERSION && savedVersion < PER_BODY_DEFAULT_VERSION;
    }
}
