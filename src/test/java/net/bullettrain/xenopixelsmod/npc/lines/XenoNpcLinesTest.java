package net.bullettrain.xenopixelsmod.npc.lines;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoNpcLinesTest {

    private static XenoNpcLines parse(String json) {
        return XenoNpcLines.fromJson(JsonParser.parseString(json));
    }

    @Test
    void parsesEveryCategory() {
        XenoNpcLines lines = parse("""
                {
                  "interact": ["Hey.", "Still here?"],
                  "attack":   ["Come on then!"],
                  "kill":     ["Too easy."],
                  "killed":   ["...urgh"],
                  "random":   ["Hm."],
                  "world":    ["Nice day."]
                }
                """);
        assertEquals(2, lines.lines(XenoNpcLines.Category.INTERACT).size());
        assertTrue(lines.has(XenoNpcLines.Category.ATTACK));
        assertTrue(lines.has(XenoNpcLines.Category.WORLD));
    }

    @Test
    void clickingRepeatedlyCyclesAndWrapsAround() {
        // This is the CustomNPCs behaviour being reproduced: keep clicking and it comes back round
        // rather than sticking on the last line.
        XenoNpcLines lines = parse("""
                { "interact": ["one", "two", "three"] }
                """);
        assertEquals("one", lines.at(XenoNpcLines.Category.INTERACT, 0));
        assertEquals("two", lines.at(XenoNpcLines.Category.INTERACT, 1));
        assertEquals("three", lines.at(XenoNpcLines.Category.INTERACT, 2));
        assertEquals("one", lines.at(XenoNpcLines.Category.INTERACT, 3));
    }

    @Test
    void aNegativeIndexStillLandsInRange() {
        // The server keeps the counter; it must not be possible to index out of bounds even if one
        // wraps past zero.
        XenoNpcLines lines = parse("{ \"interact\": [\"a\", \"b\"] }");
        assertEquals("b", lines.at(XenoNpcLines.Category.INTERACT, -1));
        assertEquals("a", lines.at(XenoNpcLines.Category.INTERACT, -2));
    }

    @Test
    void anAbsentCategoryIsQuietRatherThanAnError() {
        XenoNpcLines lines = parse("{ \"interact\": [\"hi\"] }");
        assertFalse(lines.has(XenoNpcLines.Category.ATTACK));
        assertEquals("", lines.at(XenoNpcLines.Category.ATTACK, 0));
        assertTrue(lines.lines(XenoNpcLines.Category.KILL).isEmpty());
    }

    @Test
    void theEmptySetIsSafeToUseEverywhere() {
        assertFalse(XenoNpcLines.EMPTY.has(XenoNpcLines.Category.INTERACT));
        assertEquals("", XenoNpcLines.EMPTY.at(XenoNpcLines.Category.INTERACT, 7));
    }

    @Test
    void aMisspelledCategoryIsReportedNotSwallowed() {
        // Absorbing it would leave the pack author with an NPC that never says those lines and no
        // clue why.
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> parse("{ \"intreract\": [\"oops\"] }"));
        assertTrue(e.getMessage().contains("intreract"), e.getMessage());
    }

    @Test
    void aCategoryThatIsNotAnArrayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> parse("{ \"interact\": \"hi\" }"));
        assertThrows(IllegalArgumentException.class, () -> parse("[]"));
        assertThrows(IllegalArgumentException.class, () -> XenoNpcLines.fromJson(null));
    }

    @Test
    void blankLinesAreDroppedSoAClickIsNeverSilent() {
        XenoNpcLines lines = parse("{ \"interact\": [\"real\", \"\", \"   \"] }");
        assertEquals(1, lines.lines(XenoNpcLines.Category.INTERACT).size());
        assertEquals("real", lines.at(XenoNpcLines.Category.INTERACT, 0));
    }

    @Test
    void categoryNamesAreCaseInsensitive() {
        XenoNpcLines lines = parse("{ \"INTERACT\": [\"hi\"], \" Attack \": [\"hey\"] }");
        assertTrue(lines.has(XenoNpcLines.Category.INTERACT));
        assertTrue(lines.has(XenoNpcLines.Category.ATTACK));
    }
}
