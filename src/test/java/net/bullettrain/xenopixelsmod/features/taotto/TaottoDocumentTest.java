package net.bullettrain.xenopixelsmod.features.taotto;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaottoDocumentTest {

    @Test
    void oversizedPaintCannotSpillOntoOtherBodyParts() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.part(TaottoBodyPart.RIGHT_ARM);
        for (int y = 0; y < doc.size(); y++) {
            for (int x = 0; x < doc.size(); x++) doc.setPixel(x, y, 0xFFFFFFFF);
        }
        doc.scale(4f);
        int[] overlay = doc.bakeOverlay(64);
        var island = doc.part().front();
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 64; x++) {
                if (x < island.u() || x >= island.u() + island.w()
                        || y < island.v() || y >= island.v() + island.h()) {
                    assertEquals(0, overlay[y * 64 + x]);
                }
            }
        }
    }

    @Test
    void malformedPlacementAndOversizedNbtAreNormalized() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Size", Integer.MAX_VALUE);
        tag.putFloat("Scale", Float.NaN);
        tag.putFloat("U", Float.POSITIVE_INFINITY);
        tag.putFloat("V", Float.NaN);
        TaottoDocument document = TaottoDocument.loadNbt(tag);
        assertEquals(TaottoDocument.MAX_SIZE, document.size());
        assertEquals(1f, document.scale());
        assertEquals(0f, document.offsetU());
        assertEquals(0f, document.offsetV());
    }

    @Test
    void draftCopyAndClearDoNotModifyTheSavedDocument() {
        TaottoDocument saved = TaottoDocument.blank();
        saved.setPixel(0, 0, 0xFFFFFFFF);
        TaottoDocument draft = saved.copy();
        draft.clear();
        assertFalse(draft.hasPaint());
        assertTrue(saved.hasPaint());
        CompoundTag tag = new CompoundTag();
        draft.saveNbt(tag);
        assertFalse(TaottoDocument.loadNbt(tag).hasPaint());
    }

    @Test
    void blankCanvasHasNoPaintedPixels() {
        TaottoDocument doc = TaottoDocument.blank();
        assertEquals(32, doc.size());
        assertEquals(0, doc.paintedCount());
        assertFalse(doc.hasPaint());
        assertEquals(TaottoBodyPart.TORSO, doc.part());
        assertEquals(1f, doc.scale(), 1e-5f);
    }

    @Test
    void setPixelAndClearRoundTrip() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.setPixel(4, 5, 0xFFFF0000);
        assertEquals(0xFFFF0000, doc.pixel(4, 5));
        assertEquals(1, doc.paintedCount());
        doc.clearPixel(4, 5);
        assertEquals(0, doc.pixel(4, 5));
        assertEquals(0, doc.paintedCount());
    }

    @Test
    void nbtRoundTripKeepsPaintPlacementAndScale() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.setPixel(2, 3, 0xFF00FF88);
        doc.part(TaottoBodyPart.HEAD);
        doc.scale(0.125f);
        doc.offsetU(3.5f);
        doc.offsetV(1.25f);
        CompoundTag tag = new CompoundTag();
        doc.saveNbt(tag);
        TaottoDocument loaded = TaottoDocument.loadNbt(tag);
        assertEquals(0xFF00FF88, loaded.pixel(2, 3));
        assertEquals(TaottoBodyPart.HEAD, loaded.part());
        assertEquals(3.5f, loaded.offsetU(), 1e-4f);
        assertEquals(1.25f, loaded.offsetV(), 1e-4f);
        assertEquals(0.125f, loaded.scale(), 1e-4f);
        assertTrue(loaded.hasPaint());
    }

    @Test
    void bakePlacesPixelOnSelectedIsland() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.part(TaottoBodyPart.HEAD);
        doc.offsetU(0f);
        doc.offsetV(0f);
        doc.scale(1f);
        doc.setPixel(0, 0, 0xFF112233);
        int[] overlay = doc.bakeOverlay(64);
        TaottoBodyPart.UvIsland island = TaottoBodyPart.HEAD.front();
        int dest = (island.v() * 64 + island.u()) ;
        assertEquals(0xFF112233, overlay[dest]);
        assertEquals(0, overlay[0]);
    }

    @Test
    void scaleTwoWritesABlockOnTheIsland() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.part(TaottoBodyPart.TORSO);
        doc.scale(2f);
        doc.setPixel(0, 0, 0xFFFFFFFF);
        int[] overlay = doc.bakeOverlay(64);
        TaottoBodyPart.UvIsland island = TaottoBodyPart.TORSO.front();
        assertEquals(0xFFFFFFFF, overlay[island.v() * 64 + island.u()]);
        assertEquals(0xFFFFFFFF, overlay[island.v() * 64 + island.u() + 1]);
        assertEquals(0xFFFFFFFF, overlay[(island.v() + 1) * 64 + island.u()]);
    }

    @Test
    void dragClampsInsideTheIsland() {
        TaottoDocument doc = TaottoDocument.blank();
        doc.part(TaottoBodyPart.HEAD);
        doc.dragBy(1000f, 1000f);
        TaottoBodyPart.UvIsland island = TaottoBodyPart.HEAD.front();
        assertTrue(doc.offsetU() <= island.w() - 1);
        assertTrue(doc.offsetV() <= island.h() - 1);
        doc.dragBy(-1000f, -1000f);
        assertEquals(0f, doc.offsetU(), 1e-4f);
        assertEquals(0f, doc.offsetV(), 1e-4f);
    }
}
