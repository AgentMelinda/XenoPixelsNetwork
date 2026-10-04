package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the two sprite families that were generated rather than shipped in the original bundle.
 *
 * <p>Both exist because the atlas is blitted 1:1 and never stretched, so a size that was needed but
 * missing had to be added to the generator instead of faked at runtime. These tests pin the
 * contract that made that worthwhile: a requested width always resolves to a real registered shape
 * that is at least as wide as asked for.
 */
class GeneratedSpriteFamiliesTest {

    @Test
    void chipResolvesToARegisteredShapeThatIsWideEnough() {
        for (int requested = 1; requested <= 120; requested++) {
            String name = XenoAtlasSprites.chip(requested);
            XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(name);
            assertNotNull(sprite, name);
            if (requested <= 112) {
                assertTrue(sprite.width() >= requested,
                        "chip(" + requested + ") -> " + name + " is only " + sprite.width() + " wide");
            }
        }
    }

    @Test
    void chipPicksTheNarrowestFit() {
        // Exact generated widths must map to themselves rather than the next size up, or every
        // toolbar would drift wider than it was laid out for.
        assertEquals("ui_chip_w20", XenoAtlasSprites.chip(20));
        assertEquals("ui_chip_w32", XenoAtlasSprites.chip(32));
        assertEquals("ui_chip_w32", XenoAtlasSprites.chip(21));
        assertEquals("ui_chip_w112", XenoAtlasSprites.chip(112));
    }

    @Test
    void chipWidthsAreAllEighteenHigh() {
        // The studio toolbar bands are pitched on this height; a stray value would misalign a row.
        for (int w : new int[]{20, 32, 40, 48, 56, 64, 112}) {
            assertEquals(18, XenoAtlasSprites.get("ui_chip_w" + w).height(), "ui_chip_w" + w);
            assertEquals(w, XenoAtlasSprites.get("ui_chip_w" + w).width(), "ui_chip_w" + w);
        }
    }

    @Test
    void dockedTabFamilyIsRegisteredAtEveryGeneratedWidth() {
        for (int w : new int[]{20, 28, 36, 44, 52, 60, 68}) {
            XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get("tab_docked_w" + w);
            assertEquals(w, sprite.width());
            assertEquals(26, sprite.height(), "tab cells share one height so the strip is flush");
        }
    }

    @Test
    void mynpcsRowButtonsHaveGeneratedEditorWidths() {
        for (int width : new int[]{96, 128, 176, 256, 360}) {
            XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get("mynpcs_button_row_w" + width);
            assertEquals(width, sprite.width());
            assertEquals(22, sprite.height());
        }
        assertEquals("mynpcs_button_row_w176", XenoAtlasSprites.rowWithin(179));
        assertEquals("mynpcs_button_row_w360", XenoAtlasSprites.rowWithin(364));
    }

    @Test
    void theEditorFrameIsRegisteredAtItsGeneratedSize() {
        XenoAtlasSprites.Sprite frame = XenoAtlasSprites.get("xeno_editor_panel");
        assertEquals(600, frame.width());
        assertEquals(320, frame.height());
        // It has to fit the DragonMineZ scaled canvas, which is 640x360 at 1080p GUI scale 2.
        assertTrue(frame.width() <= 640 && frame.height() <= 360,
                "editor frame must fit the typical DMZ UI canvas");
    }
}
