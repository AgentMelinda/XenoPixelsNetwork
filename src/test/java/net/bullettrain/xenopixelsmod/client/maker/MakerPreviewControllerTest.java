package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Glow / dirty state for {@link MakerPreviewController} (no Minecraft render in unit scope).
 */
class MakerPreviewControllerTest {
    @Test
    void glowDefaultsToNone() {
        MakerPreviewController preview = new MakerPreviewController();
        assertEquals(MakerPreviewController.GlowTarget.NONE, preview.glowTarget());
        assertEquals("", preview.glowId());
        assertFalse(preview.isBound());
    }

    @Test
    void setGlowStoresKindAndId() {
        MakerPreviewController preview = new MakerPreviewController();
        preview.setGlow(MakerPreviewController.GlowTarget.HAIR_SEGMENT, "strand-3");
        assertEquals(MakerPreviewController.GlowTarget.HAIR_SEGMENT, preview.glowTarget());
        assertEquals("strand-3", preview.glowId());

        preview.setGlow(null, null);
        assertEquals(MakerPreviewController.GlowTarget.NONE, preview.glowTarget());
        assertEquals("", preview.glowId());
    }

    @Test
    void setHairSegmentHighlightStoresGreenTarget() {
        MakerPreviewController preview = new MakerPreviewController();
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        preview.setHairSegmentHighlight(doc.face(), doc.strandIndex(), doc.selected());
        assertEquals(MakerPreviewController.GlowTarget.HAIR_SEGMENT, preview.glowTarget());
        assertEquals("TOP", preview.highlightFace());
        assertEquals(0, preview.highlightIndex());
        assertTrue(preview.glowId().startsWith("TOP:0"));
    }

    @Test
    void fromHairTintsSelectedStrandGreenForPreviewOnly() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.selected().color("#ffaa00");
        MakerPreviewAppearance appearance = MakerPreviewAppearance.fromHair(doc);
        assertFalse(appearance.isEmpty());
        assertEquals("#ffaa00", doc.selected().color());
        com.dragonminez.common.hair.CustomHair previewHair =
                net.bullettrain.xenopixelsmod.hair.HairApplyService.toCustomHair(
                        net.bullettrain.xenopixelsmod.hair.HairApplyService.plan(doc));
        MakerPreviewAppearance.tintSelectedStrand(previewHair, doc.face(), doc.strandIndex());
        assertEquals(MakerPreviewAppearance.SEGMENT_HIGHLIGHT_COLOR,
                previewHair.getStrand(com.dragonminez.common.hair.CustomHair.HairFace.TOP, 0).getColor());
    }

    @Test
    void zoomClampsAndScalesPickRadius() {
        MakerPreviewController preview = new MakerPreviewController();
        assertEquals(1.0f, preview.zoom(), 0.001f);
        preview.addZoom(20f);
        assertTrue(preview.zoom() <= 2.4f);
        preview.addZoom(-40f);
        assertTrue(preview.zoom() >= 0.55f);
        float tight = HairStrandPick.hitRadiusPxForScale(110f);
        float wide = HairStrandPick.hitRadiusPxForScale(35f);
        assertTrue(wide > tight, "zoomed out should have larger pick radius");
    }

    @Test
    void addZoomAtDoesNotCrashBeforeFirstRender() {
        MakerPreviewController preview = new MakerPreviewController();
        preview.addZoomAt(1f, 120f, 80f);
        assertTrue(preview.zoom() > 1.0f);
        preview.resetPan();
        assertEquals(0f, preview.panX(), 0.001f);
        assertEquals(0f, preview.panY(), 0.001f);
    }

    @Test
    void markDirtyArmsDebounce() {
        MakerPreviewController preview = new MakerPreviewController();
        preview.markDirty();
        assertTrue(preview.isDirty());
    }

    @Test
    void extraColourChannelsMarkAppearanceUsed() {
        assertTrue(new MakerPreviewAppearance().isEmpty());
        assertFalse(new MakerPreviewAppearance().bodyColor2("#111111").isEmpty());
        assertFalse(new MakerPreviewAppearance().bodyColor3("#222222").isEmpty());
        assertFalse(new MakerPreviewAppearance().eye2Color("#333333").isEmpty());
        assertFalse(new MakerPreviewAppearance().auraColor("#7FFFFF").isEmpty());
    }
}
