package net.bullettrain.xenopixelsmod.npc.dialog;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parser coverage for datapack dialogues.
 *
 * <p>The guiding rule: a pack author's mistake should either fail loudly at load, or degrade to
 * something harmless - never to a conversation that silently misbehaves at runtime.
 */
class XenoDialogueTest {

    private static XenoDialogue parse(String json) {
        return XenoDialogue.fromJson(JsonParser.parseString(json));
    }

    @Test
    void parsesAFullDialogue() {
        XenoDialogue d = parse("""
                {
                  "start": "root",
                  "nodes": {
                    "root": {
                      "text": "So you want to train, {player}?",
                      "options": [
                        { "text": "Tell me more", "type": "text",  "target": "more" },
                        { "text": "I will do it", "type": "quest", "quest": "xenopixelsmod:kill_mobs" },
                        { "text": "Not now",      "type": "quit" }
                      ]
                    },
                    "more": { "text": "It is hard work.", "options": [] }
                  }
                }
                """);

        assertEquals("root", d.start());
        assertEquals(2, d.nodes().size());
        assertEquals("So you want to train, {player}?", d.startNode().text());

        var options = d.startNode().options();
        assertEquals(3, options.size());
        assertEquals(XenoDialogue.OptionType.TEXT, options.get(0).type());
        assertEquals("more", options.get(0).target());
        assertEquals(XenoDialogue.OptionType.QUEST, options.get(1).type());
        assertEquals("xenopixelsmod:kill_mobs", options.get(1).quest());
        assertEquals(XenoDialogue.OptionType.QUIT, options.get(2).type());
    }

    @Test
    void aStartNodeThatDoesNotExistIsARefusalNotASurprise() {
        // Catching this at load is the whole point: at runtime it would be an NPC that opens a
        // conversation with nothing in it, which reads as a different bug entirely.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> parse("""
                { "start": "missing", "nodes": { "root": { "text": "hi", "options": [] } } }
                """));
        assertTrue(e.getMessage().contains("missing"), e.getMessage());
    }

    @Test
    void anEmptyDialogueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"nodes\": {} }"));
        assertThrows(IllegalArgumentException.class, () -> parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> XenoDialogue.fromJson(null));
    }

    @Test
    void anUnknownOptionTypeClosesTheConversationRatherThanBreakingIt() {
        // One bad option should not cost the pack its whole dialogue.
        XenoDialogue d = parse("""
                {
                  "start": "root",
                  "nodes": { "root": { "text": "hm", "options": [
                    { "text": "???", "type": "teleport_to_the_moon" }
                  ] } }
                }
                """);
        assertEquals(XenoDialogue.OptionType.QUIT, d.startNode().options().get(0).type());
    }

    @Test
    void anAbsentTypeMeansPlainText() {
        assertEquals(XenoDialogue.OptionType.TEXT, XenoDialogue.parseType(null));
        assertEquals(XenoDialogue.OptionType.TEXT, XenoDialogue.parseType(""));
        // Case and padding come from hand-written JSON, so they must not decide the outcome.
        assertEquals(XenoDialogue.OptionType.QUEST, XenoDialogue.parseType("  Quest "));
    }

    @Test
    void roleOptionsParseEvenThoughTheyAreNotImplementedYet() {
        // A pack written against the reference must still load; the screen shows the option
        // disabled with a reason rather than the whole dialogue failing.
        XenoDialogue d = parse("""
                {
                  "start": "root",
                  "nodes": { "root": { "text": "shop?", "options": [
                    { "text": "Trade", "type": "role" }
                  ] } }
                }
                """);
        assertEquals(XenoDialogue.OptionType.ROLE, d.startNode().options().get(0).type());
    }

    @Test
    void missingOptionFieldsGetHarmlessDefaults() {
        XenoDialogue d = parse("""
                { "start": "root", "nodes": { "root": { "options": [ {} ] } } }
                """);
        var option = d.startNode().options().get(0);
        assertEquals("", d.startNode().text());
        assertEquals("...", option.text());
        assertEquals("", option.target());
        assertEquals("", option.quest());
        assertEquals("", option.command());
    }

    @Test
    void nodesWithNoOptionsAreAllowedAsConversationEnds() {
        XenoDialogue d = parse("""
                { "start": "bye", "nodes": { "bye": { "text": "Farewell." } } }
                """);
        assertTrue(d.startNode().options().isEmpty());
    }
}
