package net.bullettrain.xenopixelsmod.features.tournament;

import java.util.Locale;

/**
 * Tournament bracket mode. v1 ships {@link #QUEUE_KOTH}; {@link #EVENT_ELIM} is reserved.
 */
public enum TournamentMode {
    QUEUE_KOTH,
    EVENT_ELIM;

    public static TournamentMode parse(String raw) {
        if (raw == null || raw.isBlank()) return QUEUE_KOTH;
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (TournamentMode mode : values()) {
            if (mode.name().toLowerCase(Locale.ROOT).equals(key)) {
                return mode;
            }
        }
        return QUEUE_KOTH;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
