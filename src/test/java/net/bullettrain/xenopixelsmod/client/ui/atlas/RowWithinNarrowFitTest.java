package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code rowWithin} must never answer wider than it was asked.
 *
 * <p>It could, and the editor showed it. The narrowest generated row was 64 wide, and
 * {@code rowWithin} falls back to the first entry when nothing fits — so an eighteen-pixel request
 * came back sixty-four. A slot row lays out as {@code [X] [ pick ]} and reserved eighteen pixels for
 * the clear button, so the clear button was three and a half times its slot and drew straight over
 * the pick button: the {@code X} appeared inside the pick frame, and the pick label ran past its own
 * border because the row had lost 46 pixels to an overlap nobody had budgeted for.
 *
 * <p>The fix was to generate the missing narrow widths rather than to scale the 64 one down — the
 * atlas is blitted 1:1 and its two-pixel frame is baked in at generation, so a scaled face reads as
 * a distorted frame. These tests pin both halves: that the narrow sizes exist, and that the
 * resolver's contract holds across the whole range.
 */
class RowWithinNarrowFitTest {

    /** The smallest row a caller can reasonably ask for and still be given something narrower. */
    private static final int NARROWEST = 16;

    @Test
    void theNarrowSizesExistAtTheirDeclaredDimensions() {
        // Generated siblings of mynpcs_button_row, so the height has to match it exactly or a row
        // of mixed widths would sit at mixed heights.
        for (int width : new int[]{16, 20, 28, 40, 52}) {
            XenoAtlasSprites.Sprite sprite =
                    XenoAtlasSprites.get("mynpcs_button_row_w" + width, XenoAtlasSprites.Theme.BLUE);
            assertEquals(width, sprite.width(), "row_w" + width + " width");
            assertEquals(22, sprite.height(), "row_w" + width + " height");
        }
    }

    @Test
    void aNarrowRequestIsNeverAnsweredWithAWiderSprite() {
        // The whole bug, as one property. Every request from the narrowest generated size upward
        // must come back no wider than it asked for.
        for (int request = NARROWEST; request <= 400; request++) {
            int answered = XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(request)).width();
            assertTrue(answered <= request,
                    "rowWithin(" + request + ") answered " + answered + ", which is wider");
        }
    }

    @Test
    void theClearButtonOfASlotRowFitsItsSlot() {
        // The exact request the editor makes for a slot row's clear button.
        int answered = XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(18)).width();
        assertTrue(answered <= 18, "an 18px clear button asked for 18 and got " + answered);
    }

    @Test
    void aRequestIsAnsweredWithTheWidestThatStillFits() {
        // Not merely "something that fits" - the widest one, or a 200px column would be filled by
        // a 16px button and the label would ellipsise for no reason.
        assertEquals(20, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(27)).width());
        assertEquals(28, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(28)).width());
        assertEquals(64, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(95)).width());
        assertEquals(360, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(4000)).width());
    }

    @Test
    void aRequestBelowEverythingGeneratedStillAnswersTheNarrowest() {
        // It cannot answer nothing, so it answers the smallest it has. Below the narrowest
        // generated size the caller is asking for something that does not exist, and the old
        // failure mode - silently returning the widest - is what must not come back.
        assertEquals(NARROWEST, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(4)).width());
        assertEquals(NARROWEST, XenoAtlasSprites.get(XenoAtlasSprites.rowWithin(0)).width());
    }
}
