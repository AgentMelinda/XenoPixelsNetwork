package net.bullettrain.xenopixelsmod.hair;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.client.maker.PreviewDebounce;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PR-D7b — pure document / export envelope + debounce contract (no Minecraft screen).
 */
class HairMakerDocumentTest {
    @Test
    void oneStrandDemoExposesLabFieldsWithoutConnected() throws Exception {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        assertEquals("TOP", doc.face());
        assertEquals(0, doc.strandIndex());
        HairStrandModel strand = doc.selected();
        assertEquals(400, strand.id());
        assertEquals(4, strand.length());
        assertEquals(1.25f, strand.lengthScale(), 1e-5f);
        assertEquals(10f, strand.rotationX(), 1e-5f);
        assertEquals(-5f, strand.rotationY(), 1e-5f);
        assertEquals(15f, strand.rotationZ(), 1e-5f);
        assertEquals(1.1f, strand.scaleX(), 1e-5f);
        assertEquals(2f, strand.curveX(), 1e-5f);
        assertEquals("#ffaa00", strand.color());
        assertTrue(strand.visible());
        assertEquals(1, doc.visibleCount());

        for (Field field : HairStrandModel.class.getDeclaredFields()) {
            assertFalse(field.getName().equalsIgnoreCase("connected"),
                    "must not invent connected boolean");
            assertFalse(field.getName().equalsIgnoreCase("movable"),
                    "must not invent movable boolean");
        }
    }

    @Test
    void exportEnvelopeMarksPathReadyRuntimeUnverifiedAndOmitsConnected() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        JsonObject root = JsonParser.parseString(doc.toExportJson("2026-10-03")).getAsJsonObject();
        assertEquals(HairMakerDocument.EXPORT_SCHEMA, root.get("schema").getAsString());
        assertEquals("path_ready_runtime_unverified", root.get("apply").getAsString());
        assertTrue(root.get("applyNote").getAsString().contains("runtime unverified"));
        assertTrue(root.get("applyNote").getAsString().contains("Full replace"));
        assertTrue(root.get("applyPath").getAsString().contains("UpdateCustomHairC2S"));
        assertEquals("Base", root.getAsJsonObject("codes").get("singleStyle").getAsString());
        assertEquals("", root.getAsJsonObject("codes").get("single").getAsString());
        assertTrue(root.getAsJsonArray("forbiddenFields").toString().contains("connected"));
        JsonObject project = root.getAsJsonObject("project");
        assertFalse(project.toString().contains("\"connected\""),
                "project payload must not invent a connected field");
        assertTrue(doc.isApplyEnabled());
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("Replace-current-style"));
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("runtime unverified"));
        assertEquals("Connected (parented cubes)", HairMakerDocument.CONNECTED_LABEL);
    }

    @Test
    void previewLinesIncludeConnectedLabelAndSummary() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        var lines = doc.previewLines();
        assertTrue(lines.stream().anyMatch(l -> l.contains("Connected (parented cubes)")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("len 4")));
        assertTrue(lines.stream().anyMatch(l -> l.contains("MakerPreviewController")));
    }

    @Test
    void transformEditThenDebounceFiresWithinFiftyMs() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        PreviewDebounce debounce = new PreviewDebounce();
        assertTrue(debounce.delayMs() <= 50L);

        doc.selected().length(7);
        debounce.schedule(1_000L);
        assertFalse(debounce.shouldFire(1_049L));
        assertTrue(debounce.shouldFire(1_050L));
        debounce.clear();
        var lines = doc.previewLines();
        assertTrue(lines.stream().anyMatch(l -> l.contains("len 7")));
    }
}
