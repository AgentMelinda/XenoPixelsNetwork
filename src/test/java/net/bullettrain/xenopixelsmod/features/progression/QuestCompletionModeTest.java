package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * How a quest finishes.
 *
 * <p>INSTANT is the default on purpose: it is exactly what every quest did before this field
 * existed, so adding the field changes no existing quest's meaning.
 */
class QuestCompletionModeTest {

    @Test
    void anUnknownOrAbsentModeIsInstant() {
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse(null));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse(""));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("  "));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("handshake"));
    }

    @Test
    void bothModesParseCaseAndPaddingInsensitively() {
        assertEquals(QuestCompletionMode.NPC, QuestCompletionMode.parse("npc"));
        assertEquals(QuestCompletionMode.NPC, QuestCompletionMode.parse("  NPC "));
        assertEquals(QuestCompletionMode.INSTANT, QuestCompletionMode.parse("Instant"));
    }
}
