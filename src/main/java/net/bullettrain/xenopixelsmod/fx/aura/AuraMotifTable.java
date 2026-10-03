package net.bullettrain.xenopixelsmod.fx.aura;

import java.util.Map;

/**
 * Per-form HD aura presentation multipliers (Phase 1: brightness only).
 *
 * <p>Key is {@code raceId/formGroup/formName}. Unknown keys return {@link Motif#NEUTRAL}.
 * Edge lick rate and thunder density are Phase 2 (authored in aura3.py), not this table.
 */
public final class AuraMotifTable {

    /** Outer (silhouette / aura_out) and inner (aura_in) brightness multipliers. */
    public record Motif(float outerBrightness, float innerBrightness) {
        public static final Motif NEUTRAL = new Motif(1f, 1f);
    }

    private static final Map<String, Motif> ROWS = Map.of(
            "saiyan/xenopixels_gods_forms/ssb", new Motif(1.0f, 1.0f),
            "saiyan/xenopixels_gods_forms/ssrose", new Motif(1.0f, 1.05f),
            "saiyan/xenopixels_gods_forms/ui", new Motif(1.02f, 1.0f),
            "saiyan/xenopixels_saga_forms/trunks_ikari", new Motif(1.08f, 1.0f));

    private AuraMotifTable() {
    }

    /** Motif key {@code raceId/formGroup/formName}; null parts become empty. Never throws. */
    public static String key(String raceId, String formGroup, String formName) {
        return part(raceId) + "/" + part(formGroup) + "/" + part(formName);
    }

    /** Lookup; unknown or null key → {@link Motif#NEUTRAL}. Never throws. */
    public static Motif motif(String key) {
        if (key == null || key.isEmpty()) return Motif.NEUTRAL;
        Motif found = ROWS.get(key);
        return found == null ? Motif.NEUTRAL : found;
    }

    private static String part(String value) {
        return value == null ? "" : value;
    }
}
