package net.bullettrain.xenopixelsmod.client.config;

import java.util.Locale;

/**
 * What XenoPixels does to the DragonMineZ menus behind the V key.
 *
 * <p>Two different reworks of those menus exist in this mod and both are kept, because only playing
 * them side by side settles which is better. This is the switch between them, and it also restores
 * the way back to DragonMineZ's own untouched screens -- the theming shipped as always-on with no
 * escape, which made "is this a Xeno bug or a DMZ one?" impossible to answer in game.
 *
 * @see net.bullettrain.xenopixelsmod.client.hud.DmzMenuThemeState
 */
public enum DmzMenuMode {

    /** DragonMineZ's menus exactly as DragonMineZ draws them. Nothing is themed or replaced. */
    STOCK("stock"),
    /**
     * DMZ's real screens, dressed in Xeno chrome. Its widgets, scrolling, packets and validation
     * all still run, so only the look changes.
     */
    THEME("theme"),
    /**
     * The stats page is replaced by {@code XenoDmzStatsScreen}, our own screen built from the clean
     * UI bundle. The other five menus are still DMZ's, themed.
     */
    SCREEN("screen"),
    /**
     * The second rebuild: {@code XenoNeonStatsScreen}, built from the newer our-style bundle
     * (`dragonminez_our_style_clean_example_dimensions_2.zip`), whose panels are whole slabs and
     * whose reference render puts the live character in a scan ring between them.
     *
     * <p>A separate mode rather than a replacement for {@link #SCREEN}: the two rebuilds use
     * different art and different layouts, and which one plays better is a question only playing
     * both answers.
     *
     * <p>All six V-menus get page-specific art in this mode, each page drawn from its own group in
     * the master bundle -- skills from the skills/information pack, quests from the quest tree,
     * minigames from minigames, party from the server menu, settings from options. Only the
     * character page is a custom screen; the other five remain DragonMineZ's own screens with their
     * panels, headers and widget sheets replaced, so DMZ still owns every control, scroll region,
     * hitbox and packet on them and none of it is repositionable from Xeno's elements editor. Where
     * a page has no neon sheet for a particular slot the {@link #THEME} sheet is used, and where
     * neither exists DragonMineZ's own art is left alone -- {@link #themed()} is true here too.
     */
    NEON("neon");

    /**
     * What the mod ships with, and what a config written before the setting existed is moved to.
     *
     * <p>One constant rather than the value repeated at the field initialiser, the migration rule
     * and the parse fallback: those three drifting apart is how a default silently becomes "whatever
     * the last one of them said".
     */
    public static final DmzMenuMode DEFAULT = THEME;

    private final String id;

    DmzMenuMode(String id) {
        this.id = id;
    }

    /** The name used in config files and in {@code /xenohud menus <id>}. */
    public String id() {
        return id;
    }

    /**
     * Parses a stored or typed id.
     *
     * @param fallback returned for null, blank or unrecognised input, so a hand-edited config
     *                 cannot leave the menus in no mode at all
     */
    public static DmzMenuMode parse(String raw, DmzMenuMode fallback) {
        if (raw == null) return fallback;
        String needle = raw.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return fallback;
        // "bt3" was the original name and "v3" is the asset-bundle/menu-facing name. Both mean
        // the clean modular replacement screen, which is what SCREEN is now.
        if (needle.equals("bt3") || needle.equals("v3")) return SCREEN;
        for (DmzMenuMode mode : values()) {
            if (mode.id.equals(needle)) return mode;
        }
        return fallback;
    }

    /** True when Xeno is drawing over DMZ's menus at all, in either rework. */
    public boolean themed() {
        return this != STOCK;
    }

    /** Every id accepted by the command, for its usage text. */
    public static String usage() {
        StringBuilder out = new StringBuilder();
        for (DmzMenuMode mode : values()) {
            if (out.length() > 0) out.append('|');
            out.append(mode.id);
        }
        return out.toString();
    }
}
