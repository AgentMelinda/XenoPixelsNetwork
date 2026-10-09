package net.bullettrain.xenopixelsmod.combat.v3.ki;

import java.util.Locale;

/**
 * Presentation backend for owned Combat V3 Strike Ki.
 *
 * <ul>
 *   <li>{@link #DMZ} — real DragonMineZ ki projectile entities (hitboxes + DMZ colors).</li>
 *   <li>{@link #AAA} — AAA Particles / Effekseer HD path.</li>
 *   <li>{@link #NEWEFFECTS} — owned {@code V3KiShots} HD path; aliases {@link #AAA} until a
 *       distinct third renderer exists.</li>
 * </ul>
 */
public enum KiAttackVisualMode {
    DMZ,
    AAA,
    NEWEFFECTS;

    public static KiAttackVisualMode parse(String raw) {
        if (raw == null || raw.isBlank()) return AAA;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        return switch (key) {
            case "dmz", "dragonminez", "native" -> DMZ;
            case "aaa", "effekseer", "hd" -> AAA;
            case "neweffects", "new", "v3", "shots" -> NEWEFFECTS;
            default -> throw new IllegalArgumentException(
                    "Unknown ki attack visual mode '" + raw + "' (dmz|aaa|neweffects)");
        };
    }

    /** Legacy {@code effekseerKiAttacks}: true was HD/AAA, false was native DMZ rendering. */
    public static KiAttackVisualMode fromLegacy(boolean effekseerKiAttacks) {
        return effekseerKiAttacks ? AAA : DMZ;
    }

    /** Whether this mode uses the owned Effekseer/AAA visual stack rather than DMZ projectiles. */
    public boolean usesOwnedHdVisuals() {
        return this == AAA || this == NEWEFFECTS;
    }

    public String commandName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
