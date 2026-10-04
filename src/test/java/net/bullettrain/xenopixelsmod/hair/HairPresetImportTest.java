package net.bullettrain.xenopixelsmod.hair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Preset id parsing + empty-load contract for Hair Studio cyclers.
 */
class HairPresetImportTest {
    @Test
    void parsePresetIdAcceptsHairColonAndBareNumbers() {
        assertEquals(1, HairPresetImport.parsePresetId("hair:1"));
        assertEquals(12, HairPresetImport.parsePresetId("Hair:12"));
        assertEquals(3, HairPresetImport.parsePresetId("3"));
        assertEquals(-1, HairPresetImport.parsePresetId(""));
        assertEquals(-1, HairPresetImport.parsePresetId("ssj-style"));
        assertEquals(-1, HairPresetImport.parsePresetId(null));
    }

    @Test
    void loadIntoDocumentRejectsNegativeIdsWithoutTouchingDocument() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        int before = doc.visibleCount();
        assertFalse(HairPresetImport.loadIntoDocument(doc, -1, "Base"));
        assertFalse(HairPresetImport.loadIntoDocument(doc, -5, "SSJ"));
        assertEquals(before, doc.visibleCount());
    }
}
