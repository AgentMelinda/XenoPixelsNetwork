package net.bullettrain.xenopixelsmod.fx.aura;

import java.util.Locale;

/** Which aura is drawn: DragonMineZ's own, the generated HD one, or both at once to compare. */
public enum AuraStyle {
    DMZ, HD, BOTH;

    /** 2026-09-29 owner: both auras by default. */
    public static final AuraStyle DEFAULT = BOTH;

    /** Unknown or missing: the default. */
    public static AuraStyle parse(String value) {
        if (value == null) return DEFAULT;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return DEFAULT;
        }
    }

    /** Whether DragonMineZ's aura draws. */
    public boolean dmz() {
        return this != HD;
    }

    /** Whether the HD aura plays. */
    public boolean hd() {
        return this != DMZ;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
