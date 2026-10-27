package net.bullettrain.xenopixelsmod.client.space;

import net.bullettrain.xenopixelsmod.space.hakai.PlanetIntegrityRules;

/** Stage-specific color multipliers for the cached HD surface and its shared damage overlay. */
final class PlanetIntegritySurfaceTint {
    record Tint(float red, float green, float blue) {}

    private static final Tint INTACT = new Tint(1.0f, 1.0f, 1.0f);
    private static final Tint SCARRED = new Tint(1.0f, 0.84f, 0.72f);
    private static final Tint CRITICAL = new Tint(1.0f, 0.48f, 0.38f);

    private PlanetIntegritySurfaceTint() {}

    static Tint forStage(PlanetIntegrityRules.Stage stage) {
        return switch (stage) {
            case SCARRED -> SCARRED;
            case CRITICAL -> CRITICAL;
            case INTACT, DESTROYED -> INTACT;
        };
    }

    static boolean hasCrackOverlay(PlanetIntegrityRules.Stage stage) {
        return stage == PlanetIntegrityRules.Stage.SCARRED || stage == PlanetIntegrityRules.Stage.CRITICAL;
    }
}
