package net.bullettrain.xenopixelsmod.ui;

import java.util.Locale;

/** Nine-point anchors. Offsets are design pixels from the chosen point. */
public enum UiAnchor {
    TOP_LEFT,
    TOP,
    TOP_RIGHT,
    LEFT,
    CENTER,
    RIGHT,
    BOTTOM_LEFT,
    BOTTOM,
    BOTTOM_RIGHT;

    public static UiAnchor byName(String name) {
        if (name == null || name.isBlank()) {
            return TOP_LEFT;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}
