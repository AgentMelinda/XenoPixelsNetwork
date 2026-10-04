package net.bullettrain.xenopixelsmod.client.npc;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The editor frame had to shrink so the visualizer could sit beside it.
 *
 * <p>My NPCs draws its visualizer outside the GUI; ours used to take a column out of the frame
 * instead. Moving it out is not just a placement change - the canvas is small. {@code ScaledScreen}
 * gives roughly 640x360 at 1080p GUI scale 2 and never less than 320x240
 * ({@code docs/atlas-ui-doco.md}), and the old 600-wide frame filled it, so a panel placed beside it
 * was clamped straight back on top. The narrow frame exists to make room.
 *
 * <p>{@code init()} needs a live screen, so what is pinned here is the arithmetic it depends on:
 * the sprites are the sizes claimed, and the frame-plus-gap-plus-panel block fits the canvas at both
 * ends of its range.
 */
class XenoNpcEditorFrameLayoutTest {

    /** Must match {@code XenoNpcEditorScreen.PREVIEW_GAP} and {@code NpcPreviewPanel.GAP}. */
    private static final int GAP = 8;

    /** Margin {@code init()} keeps around the block. */
    private static final int MARGIN = 8;

    private static final String FRAME = "xeno_editor_panel_w420";
    private static final String PREVIEW = "mynpcs_small_panel";

    @Test
    void theNarrowFrameIsRegisteredAtTheSizeItWasGeneratedAt() {
        XenoAtlasSprites.Sprite frame = XenoAtlasSprites.get(FRAME);
        assertEquals(420, frame.width());
        assertEquals(320, frame.height());
    }

    @Test
    void theWideFrameIsStillRegisteredBecauseSomethingElseUsesIt() {
        // client/screen/XenoPartyScreen still draws xeno_editor_panel. Narrowing the editor must
        // not take that away.
        XenoAtlasSprites.Sprite wide = XenoAtlasSprites.get("xeno_editor_panel");
        assertEquals(600, wide.width());
        assertEquals(320, wide.height());
    }

    @Test
    void frameAndVisualizerFitSideBySideOnTheUsualCanvas() {
        // 1080p, GUI scale 2 - the case that matters in practice.
        int canvasW = 640;
        int frameW = XenoAtlasSprites.get(FRAME).width();
        int previewW = XenoAtlasSprites.get(PREVIEW).width();

        int block = frameW + GAP + previewW;
        assertTrue(block <= canvasW - MARGIN,
                "frame " + frameW + " + gap " + GAP + " + panel " + previewW + " = " + block
                        + " must fit in " + (canvasW - MARGIN));
    }

    @Test
    void theOldFrameIsWhyThisWasNeeded() {
        // The reason the frame was narrowed, stated as a check rather than a comment: the 600-wide
        // one plus the panel does not fit, which is why a panel beside it used to be clamped back
        // over the frame.
        int canvasW = 640;
        int oldBlock = XenoAtlasSprites.get("xeno_editor_panel").width()
                + GAP + XenoAtlasSprites.get(PREVIEW).width();
        assertTrue(oldBlock > canvasW - MARGIN,
                "the 600 frame left no room beside it; that is the whole reason for the 420 one");
    }

    @Test
    void bothStillFitWhenTheCanvasIsAtItsSmallest() {
        // ScaledScreen never goes below 320x240. Both sprites are scaled down together there, so
        // what has to hold is that the *ratio* still leaves room, not the native widths.
        int canvasW = 320;
        int canvasH = 240;

        int[] preview = XenoAtlasSprites.fittedSize(PREVIEW,
                Math.max(40, canvasW / 3), Math.max(40, canvasH - 40));
        int[] frame = XenoAtlasSprites.fittedSize(FRAME,
                canvasW - MARGIN - preview[0] - GAP, canvasH - MARGIN);

        int block = frame[0] + GAP + preview[0];
        assertTrue(block <= canvasW - MARGIN,
                "at the smallest canvas the block is " + block + ", canvas " + canvasW);
        assertTrue(frame[0] > 0 && frame[1] > 0, "the frame must not collapse");
        assertTrue(preview[0] > 0 && preview[1] > 0, "the visualizer must not collapse");
    }

    @Test
    void theFrameKeepsItsNativeSizeOnTheUsualCanvas() {
        // fittedSize resamples when a sprite does not fit, and this art has its border baked in at
        // generation - so on the canvas that matters it must come back untouched.
        int canvasW = 640;
        int canvasH = 360;
        int[] preview = XenoAtlasSprites.fittedSize(PREVIEW,
                Math.max(40, canvasW / 3), Math.max(40, canvasH - 40));
        int[] frame = XenoAtlasSprites.fittedSize(FRAME,
                canvasW - MARGIN - preview[0] - GAP, canvasH - MARGIN);

        assertEquals(420, frame[0], "the frame should be blitted 1:1, not scaled");
        assertEquals(320, frame[1]);
        assertEquals(176, preview[0], "and so should the visualizer panel");
        assertEquals(222, preview[1]);
    }

    @Test
    void shorterFramesExistSoASparsePageHasNoEmptyBand() {
        // A tab with four rows was drawing the same 320-tall frame as one with twenty, leaving a
        // visible gap above Save/Close. The frame cannot be drawn shorter than it was generated -
        // AtlasPanel resamples, and this art has its border baked in - so shorter ones exist.
        for (int h : new int[]{200, 240, 280}) {
            String name = XenoAtlasSprites.editorFrame(h);
            XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(name);
            assertEquals(420, sprite.width(), name + " must stay 420 wide");
            assertTrue(sprite.height() >= h, name + " should hold " + h);
        }
    }

    @Test
    void theShortestFrameThatHoldsTheContentIsChosen() {
        assertEquals(200, XenoAtlasSprites.get(XenoAtlasSprites.editorFrame(10)).height(),
                "a nearly empty page should take the shortest frame");
        assertEquals(200, XenoAtlasSprites.get(XenoAtlasSprites.editorFrame(200)).height());
        assertEquals(240, XenoAtlasSprites.get(XenoAtlasSprites.editorFrame(201)).height());
        assertEquals(320, XenoAtlasSprites.get(XenoAtlasSprites.editorFrame(281)).height());
    }

    @Test
    void aFullPageStillGetsTheFullFrame() {
        // And anything taller than the tallest falls back rather than failing, so a page can never
        // end up with no frame at all.
        assertEquals("xeno_editor_panel_w420", XenoAtlasSprites.editorFrame(320));
        assertEquals("xeno_editor_panel_w420", XenoAtlasSprites.editorFrame(9999));
        assertEquals(320, XenoAtlasSprites.maxEditorFrameHeight());
    }

    @Test
    void everyChosenFrameIsARegisteredSprite() {
        // editorFrame builds a name by concatenation, so a height present in one place and not the
        // other would only show up as a missing texture in game.
        for (int h = 0; h <= 400; h += 10) {
            String name = XenoAtlasSprites.editorFrame(h);
            assertTrue(XenoAtlasSprites.shapes().contains(name),
                    name + " is not a registered atlas shape");
        }
    }
}
