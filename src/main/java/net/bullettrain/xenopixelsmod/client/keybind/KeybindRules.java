package net.bullettrain.xenopixelsmod.client.keybind;

import java.util.List;
import java.util.Locale;

/**
 * Which key mappings the cleanup leaves bound. Minecraft-free so the rule is unit tested.
 *
 * <p>Three owners are always kept: Minecraft itself, DragonMineZ and XenoPixels. Everything else
 * is another mod's binding and is unbound, unless the player has listed that mod's translation-key
 * prefix as one to keep, or the binding is a held modifier (see {@link #heldModifier}).
 */
public final class KeybindRules {

    public static final String DMZ_PREFIX = "key.dragonminez.";
    public static final String XENO_PREFIX = "key.xenopixelsmod.";

    /** GLFW's key codes for left shift through right super are one unbroken run. */
    private static final int FIRST_MODIFIER_KEY = 340;
    private static final int LAST_MODIFIER_KEY = 347;

    private KeybindRules() {}

    /** Iris's "reload shaders", which rebuilds every chunk on screen. Its default key is R. */
    public static final String SHADER_RELOAD = "iris.keybind.reload";
    /** GLFW's R: DragonMineZ's dash, pressed constantly in a fight. */
    public static final int KEY_R = 82;
    /** GLFW's Page Up: where the shader reload goes instead. */
    public static final int KEY_PAGE_UP = 266;

    /**
     * Whether the shader reload binding should be moved to Page Up now.
     *
     * <p>Always when it sits on R: that is the dash key under every combat controller, and a
     * chunk rebuild on every dash is not something anyone chose. When it is unbound, once per
     * profile, so it has a home; after that an unbound one is the player's own doing and is left.
     *
     * @param firstTime this profile has not had the move yet
     */
    public static boolean moveShaderReload(boolean unbound, boolean keyboardKey, int keyCode, boolean firstTime) {
        if (unbound) return firstTime;
        return keyboardKey && keyCode == KEY_R;
    }

    /**
     * @param name         the mapping's translation key, e.g. {@code key.dragonminez.lock_on}
     * @param vanilla      whether the mapping is one of Minecraft's own (decided by identity
     *                     against {@code Options}, never by guessing from the name)
     * @param keepPrefixes extra translation-key prefixes the player wants left alone
     */
    public static boolean keep(String name, boolean vanilla, List<String> keepPrefixes) {
        if (vanilla) return true;
        if (name == null || name.isBlank()) return true;
        String key = name.toLowerCase(Locale.ROOT);
        if (key.startsWith(DMZ_PREFIX) || key.startsWith(XENO_PREFIX)) return true;
        if (keepPrefixes != null) {
            for (String prefix : keepPrefixes) {
                if (prefix == null || prefix.isBlank()) continue;
                if (key.startsWith(prefix.trim().toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    /** Whether a GLFW key code is a shift, control, alt or super key, on either side. */
    public static boolean isModifierKey(int glfwKey) {
        return glfwKey >= FIRST_MODIFIER_KEY && glfwKey <= LAST_MODIFIER_KEY;
    }

    /**
     * Whether a mapping is a held modifier: one whose own default key is shift, control or alt.
     *
     * <p>Such a mapping is not an action waiting for a press. It is a mod's way of asking "is the
     * player holding Shift", it is held alongside other keys rather than instead of them, so it
     * takes nothing away from the v2 layout, and mods read it straight from the keyboard without
     * expecting it to be unbound. Create does exactly that for its "hold Shift for summary"
     * tooltips, and with the mapping unbound it asks the keyboard for key -1. Minecraft builds the
     * creative search index on a worker thread, where that request is fatal: the game crashed on
     * the first letter typed into the creative search.
     *
     * <p>Judged by the default, not the current key, so it is a property of the mapping and does
     * not change when a player moves it.
     *
     * @param defaultIsKeyboardKey whether the mapping's default is a keyboard key at all
     * @param defaultKeyCode       that default's GLFW key code
     */
    public static boolean heldModifier(boolean defaultIsKeyboardKey, int defaultKeyCode) {
        return defaultIsKeyboardKey && isModifierKey(defaultKeyCode);
    }

    /**
     * The whole cleanup rule: whether a mapping may be unbound.
     */
    public static boolean mayUnbind(String name, boolean vanilla, List<String> keepPrefixes,
                                    boolean defaultIsKeyboardKey, int defaultKeyCode) {
        return !keep(name, vanilla, keepPrefixes) && !heldModifier(defaultIsKeyboardKey, defaultKeyCode);
    }

    /** A safe backup file name for a timestamp such as {@code 20261005-142233}. */
    public static String backupFileName(String timestamp) {
        String clean = timestamp == null ? "" : timestamp.replaceAll("[^0-9A-Za-z-]", "");
        return "keybinds-" + (clean.isEmpty() ? "backup" : clean) + ".json";
    }

    /** Whether {@code fileName} is one of ours: a plain name, no path, the expected shape. */
    public static boolean isBackupFileName(String fileName) {
        return fileName != null && fileName.matches("keybinds-[0-9A-Za-z-]{1,40}\\.json");
    }
}
