package net.bullettrain.xenopixelsmod.features.playerrole;

import java.util.Locale;

/**
 * Persistent player role (distinct from {@code XenoNpcRole}).
 *
 * <p>Angel is cosmetic + trainer gating only in v1 — no combat modifiers.
 */
public enum PlayerRoleId {
    NONE,
    ANGEL;

    /** Parses a role id; null/blank/unknown → {@link #NONE}. */
    public static PlayerRoleId parse(String raw) {
        if (raw == null || raw.isBlank()) return NONE;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (PlayerRoleId role : values()) {
            if (role.name().toLowerCase(Locale.ROOT).equals(key)) {
                return role;
            }
        }
        return NONE;
    }

    /** Wire / NBT / command form: {@code none}, {@code angel}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
