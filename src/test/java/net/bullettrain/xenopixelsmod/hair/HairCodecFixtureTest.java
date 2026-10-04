package net.bullettrain.xenopixelsmod.hair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D7a — thin Java smoke over hair golden fixtures (codec lives in the hair lab).
 * Full encode/decode asserted by {@code tools/dmz-hair-builder-site} {@code npm test}.
 * No CustomizationManager apply; export-only.
 */
class HairCodecFixtureTest {
    private static String readResource(String path) throws Exception {
        try (InputStream in = HairCodecFixtureTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing classpath resource " + path);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void goldenVectorsJsonParsesWithEmptyAndOneStrand() throws Exception {
        JsonObject root = JsonParser.parseString(readResource("/hair/golden-vectors.json")).getAsJsonObject();
        assertEquals("xenopixels.hair.codec.vectors.v1", root.get("schema").getAsString());
        JsonObject vectors = root.getAsJsonObject("vectors");
        assertTrue(vectors.has("empty_single"));
        assertTrue(vectors.has("one_strand_single"));

        String emptyCode = vectors.getAsJsonObject("empty_single").get("dmzCode").getAsString();
        String oneCode = vectors.getAsJsonObject("one_strand_single").get("dmzCode").getAsString();
        assertTrue(emptyCode.startsWith("DMZ1:"));
        assertTrue(oneCode.startsWith("DMZ1:"));
        assertEquals(emptyCode.trim(), readResource("/hair/empty-single.dmz.txt").trim());
        assertEquals(oneCode.trim(), readResource("/hair/one-strand-single.dmz.txt").trim());

        JsonObject strand = vectors.getAsJsonObject("one_strand_single").getAsJsonObject("strand");
        assertEquals(4, strand.get("length").getAsInt());
        assertEquals(400, strand.get("id").getAsInt());
        assertFalse(strand.has("connected"), "must not invent connected boolean");
        assertFalse(strand.has("movable"), "must not invent movable boolean");
    }

    @Test
    void exportFormatSampleMarksApplyBlocked() throws Exception {
        JsonObject root = JsonParser.parseString(readResource("/hair/export-format-sample.json")).getAsJsonObject();
        assertEquals("xenopixels.hair.export.v1", root.get("schema").getAsString());
        assertEquals("blocked", root.get("apply").getAsString());
        assertTrue(root.getAsJsonObject("codes").get("single").getAsString().startsWith("DMZ1:"));
        assertTrue(root.getAsJsonObject("codes").get("full").getAsString().startsWith("DMZF1:"));
        assertTrue(root.getAsJsonArray("forbiddenFields").toString().contains("connected"));
    }
}
