package net.bullettrain.xenopixelsmod.features.progression;

import java.util.Locale;

/**
 * How a quest finishes.
 *
 * <p>Mirrors My NPCs' {@code EnumQuestCompletion}, whose quest editor offers exactly two choices -
 * {@code quest.npc} "Complete by npc" and {@code quest.instant} "Instant Complete".
 *
 * <p>{@link #INSTANT} is the default everywhere. It is precisely what every quest in this repo did
 * before this type existed, so a quest that says nothing about completion keeps behaving as it
 * always has.
 */
public enum QuestCompletionMode {

    /** Pays out the moment progress reaches target. */
    INSTANT,

    /**
     * Reaches target and waits to be handed in to a named NPC.
     *
     * <p>A quest in this mode stays active and is marked ready; it is not complete until the player
     * talks to its completer.
     */
    NPC;

    /** Parses a mode. Anything unrecognised is {@link #INSTANT} rather than a failure. */
    public static QuestCompletionMode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return INSTANT;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            // A pack that misspells the mode gets the old behaviour, not a broken quest.
            return INSTANT;
        }
    }
}
