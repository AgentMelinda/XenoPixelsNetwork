package net.bullettrain.xenopixelsmod.ui;

import java.util.Locale;

/** Widget types the v1 document format understands. */
public enum UiNodeType {
    ROOT,
    PANEL,
    TEXT,
    IMAGE,
    ICON,
    PORTRAIT,
    PROGRESS_BAR,
    BUTTON,
    HBOX,
    VBOX,
    SCROLL,
    NAV_TAB,
    STAT_ROW,
    SKILL_SLOT;

    public static UiNodeType byName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /** Playback clicks fire {@code action} on buttons and nav tabs. */
    public boolean firesClickAction() {
        return this == BUTTON || this == NAV_TAB;
    }
}
