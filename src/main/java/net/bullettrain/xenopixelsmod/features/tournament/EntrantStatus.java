package net.bullettrain.xenopixelsmod.features.tournament;

import java.util.Locale;

/** Per-entrant lifecycle in a tournament season. */
public enum EntrantStatus {
    QUEUED,
    ACTIVE,
    ELIMINATED;

    public static EntrantStatus parse(String raw) {
        if (raw == null || raw.isBlank()) return QUEUED;
        String key = raw.trim().toLowerCase(Locale.ROOT);
        for (EntrantStatus status : values()) {
            if (status.name().toLowerCase(Locale.ROOT).equals(key)) {
                return status;
            }
        }
        return QUEUED;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
