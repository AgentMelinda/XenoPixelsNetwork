package net.bullettrain.xenopixelsmod.client.config;

import java.util.Locale;

/**
 * Which HUD renderer draws the Xeno HUD.
 *
 * <p>Replaces a pair of booleans ({@code legacyHudRenderer}, {@code unifiedHudRenderer}) whose
 * combinations had to be decoded at every call site and which had no room for a fourth renderer.
 * Those booleans still exist as derived values so existing callers keep working, but this is the
 * value that is stored and chosen; see {@link XenoHudConfig#setRenderer}.
 */
public enum XenoHudRenderer {

    /** Procedural renderer, drawn without the chrome atlas. */
    LEGACY("legacy"),
    /** Textured atlas renderer. */
    MODERN("modern"),
    /** Stat cluster and combat cooldown strip drawn as one panel. */
    MODERN_UNIFIED("modernunified"),
    /** Budokai Tenkaichi 3 inspired renderer. */
    BT3("bt3");

    private final String id;

    XenoHudRenderer(String id) {
        this.id = id;
    }

    /** The name used in config files and in {@code /xenohud renderer <id>}. */
    public String id() {
        return id;
    }

    /**
     * Parses a stored or typed id.
     *
     * <p>{@code ldlib} is kept as an alias for {@link #MODERN}: an early flat-rectangle LDLib spike
     * was replaced by the textured renderer, and anyone with that word in a config or a macro
     * should land on its successor rather than on an error.
     *
     * @param fallback returned for null, blank or unrecognised input, so a hand-edited config
     *                 cannot leave the HUD with no renderer at all
     */
    public static XenoHudRenderer parse(String raw, XenoHudRenderer fallback) {
        if (raw == null) return fallback;
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return fallback;
        if (needle.equals("ldlib")) return MODERN;
        for (XenoHudRenderer renderer : values()) {
            if (renderer.id.equals(needle)) return renderer;
        }
        return fallback;
    }

    /**
     * The renderer a pre-enum config was using.
     *
     * <p>The exact mapping of the two old booleans, so an existing player's HUD looks the same
     * after the upgrade as it did before it. Nothing migrates to {@link #BT3}: that is opt-in.
     */
    public static XenoHudRenderer fromLegacyFlags(boolean legacy, boolean unified) {
        if (legacy) return LEGACY;
        return unified ? MODERN_UNIFIED : MODERN;
    }

    /** Every id accepted by the command, for its usage text and suggestions. */
    public static String usage() {
        StringBuilder out = new StringBuilder();
        for (XenoHudRenderer renderer : values()) {
            if (out.length() > 0) out.append('|');
            out.append(renderer.id);
        }
        return out.toString();
    }
}
