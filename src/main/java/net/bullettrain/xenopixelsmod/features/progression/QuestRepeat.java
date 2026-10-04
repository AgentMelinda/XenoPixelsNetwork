package net.bullettrain.xenopixelsmod.features.progression;

import java.util.Locale;

/** Repeat rules stored with a native quest definition. */
public enum QuestRepeat {
    NONE, REPEATABLE, MCDAILY, MCWEEKLY, RLDAILY, RLWEEKLY;

    public static QuestRepeat parse(String value) {
        if (value == null || value.isBlank()) return REPEATABLE;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown quest repeat rule '" + value + "'");
        }
    }

    /** MyNPCs 1.5.0 EnumQuestRepeat ordinal order, verified against the installed jar. */
    public static QuestRepeat fromMyNpcs(int ordinal) {
        if (ordinal < 0 || ordinal >= values().length) {
            throw new IllegalArgumentException("unknown MyNPCs repeat rule " + ordinal);
        }
        return values()[ordinal];
    }

    public boolean canRestart(long lastGameTime, long lastEpochMillis,
                              long nowGameTime, long nowEpochMillis) {
        return switch (this) {
            case NONE -> false;
            case REPEATABLE -> true;
            case MCDAILY -> nowGameTime / 24000L > lastGameTime / 24000L;
            case MCWEEKLY -> nowGameTime / 168000L > lastGameTime / 168000L;
            case RLDAILY -> lastEpochMillis > 0 &&
                    Math.floorDiv(nowEpochMillis, 86_400_000L) > Math.floorDiv(lastEpochMillis, 86_400_000L);
            case RLWEEKLY -> lastEpochMillis > 0 &&
                    Math.floorDiv(nowEpochMillis, 604_800_000L) > Math.floorDiv(lastEpochMillis, 604_800_000L);
        };
    }
}
