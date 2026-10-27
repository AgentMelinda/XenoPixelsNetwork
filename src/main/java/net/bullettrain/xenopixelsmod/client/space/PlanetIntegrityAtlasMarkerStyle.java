package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules;

/** Map marker tint from the server-synchronized integrity stage. */
public final class PlanetIntegrityAtlasMarkerStyle {
    public static final int CRITICAL = 0xFFFF7654;
    public static final int DAMAGED = 0xFFFFC46B;

    private PlanetIntegrityAtlasMarkerStyle() {}

    public static int color(PlanetIntegrityRules.Stage stage) {
        return stage == PlanetIntegrityRules.Stage.CRITICAL ? CRITICAL : DAMAGED;
    }
}
