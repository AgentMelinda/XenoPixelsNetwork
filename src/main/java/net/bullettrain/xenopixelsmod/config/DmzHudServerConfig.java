package net.bullettrain.xenopixelsmod.config;

/**
 * Backward-compatible facade over {@link XenoServerConfig#dmzHudEnabled}.
 */
public final class DmzHudServerConfig {
    private DmzHudServerConfig() {}

    public static boolean isDmzHudEnabled() {
        return XenoServerConfig.dmzHudEnabled;
    }

    public static void setDmzHudEnabled(boolean enabled) {
        XenoServerConfig.setDmzHudEnabled(enabled);
    }

    public static boolean toggle() {
        boolean next = !XenoServerConfig.dmzHudEnabled;
        XenoServerConfig.setDmzHudEnabled(next);
        return next;
    }

    public static void load() {
        XenoServerConfig.load();
    }

    public static void save() {
        XenoServerConfig.save();
    }
}
