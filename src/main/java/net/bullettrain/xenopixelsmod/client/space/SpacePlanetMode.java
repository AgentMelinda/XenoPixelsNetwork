package net.bullettrain.xenopixelsmod.client.space;

/** Client presentation policy for the approved stars-only sky and physical Sable planets. */
public enum SpacePlanetMode {
    /** Draw HD surfaces only from replicated Sable bodies. */
    BODIES("bodies"),
    /** No planets at all. */
    OFF("off");

    public final String id;

    SpacePlanetMode(String id) {
        this.id = id;
    }

    /** Resolves old projection settings as body-only mode, so client config upgrades cannot restore fake planets. */
    public static SpacePlanetMode byName(String name) {
        if (name != null) {
            for (SpacePlanetMode mode : values()) {
                if (mode.id.equalsIgnoreCase(name.trim())) return mode;
            }
        }
        if (name != null && (name.equalsIgnoreCase("proxies") || name.equalsIgnoreCase("planets"))) return BODIES;
        return BODIES;
    }

    /** Retained for the legacy chart-snapshot renderer; chart-only globes are retired and never drawn. */
    public boolean drawsProxies() {
        return false;
    }

    /** True only when the corresponding Sable body is replicated on this client. */
    public boolean drawsBodies() {
        return this == BODIES;
    }
}
