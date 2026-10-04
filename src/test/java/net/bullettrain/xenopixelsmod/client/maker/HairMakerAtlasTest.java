package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke: exact-layout Hair Editor atlas sprites + Apply-enabled (D7c) contract.
 */
class HairMakerAtlasTest {
    private static final String[] GREEN_SHAPES = {
            "xeno_maker_form_list",
            "xeno_maker_form_settings",
            "xeno_maker_hair_preview",
            "mynpcs_button_row_w96",
            "mynpcs_button_row",
            "mynpcs_button_arrow"
    };

    @Test
    void greenHairMakerShapesResolveAtNativeSize() {
        assertEquals(140, XenoAtlasSprites.get("xeno_maker_form_list", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(280, XenoAtlasSprites.get("xeno_maker_form_list", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(220, XenoAtlasSprites.get("xeno_maker_form_settings", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_form_settings", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(280, XenoAtlasSprites.get("xeno_maker_hair_preview", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(240, XenoAtlasSprites.get("xeno_maker_hair_preview", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(96, XenoAtlasSprites.get("mynpcs_button_row_w96", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(22, XenoAtlasSprites.get("mynpcs_button_row_w96", XenoAtlasSprites.Theme.GREEN).height());
    }

    @Test
    void greenHairMakerPngsAreOnClasspath() {
        for (String shape : GREEN_SHAPES) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(HairMakerAtlasTest.class.getClassLoader().getResource(path),
                    "missing green sprite: " + path);
        }
    }

    @Test
    void goldBannerResolvesAtNativeSize() {
        assertEquals(150, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).width());
        assertEquals(60, XenoAtlasSprites.get("banner_top", XenoAtlasSprites.Theme.GOLD).height());
    }

    @Test
    void styleDisplayNamesMatchApplySlots() {
        assertEquals(HairMakerDocument.STYLE_NAMES.size(), HairMakerScreen.STYLE_DISPLAY_NAMES.size());
        assertEquals("Default Hair Style", HairMakerScreen.displayStyle("Base"));
        assertEquals("Super Saiyan", HairMakerScreen.displayStyle("SSJ"));
        assertEquals("Super Saiyan 2", HairMakerScreen.displayStyle("SSJ2"));
        assertEquals("Super Saiyan 3", HairMakerScreen.displayStyle("SSJ3"));
        assertEquals("Base", HairMakerScreen.styleIdAt(0));
        assertEquals("SSJ3", HairMakerScreen.styleIdAt(3));
    }

    @Test
    void applyEnabledAfterD7cEvidenceWithReplaceWarning() {
        HairMakerDocument doc = new HairMakerDocument();
        assertTrue(doc.isApplyEnabled());
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("Replace-current-style"));
        assertTrue(HairMakerDocument.APPLY_ENABLED_TOOLTIP.contains("runtime unverified"));
        assertEquals("path_ready_runtime_unverified", HairMakerDocument.APPLY_STATUS);
    }

    @Test
    void glowTargetIncludesHairSegment() {
        assertNotNull(MakerPreviewController.GlowTarget.HAIR_SEGMENT);
    }

    @Test
    void previewDebounceDefaultIsWithinSpec() {
        assertTrue(PreviewDebounce.DEFAULT_MS <= 50L);
    }

    @Test
    void hairPresetCatalogFallbackWhenLiveCountZero() {
        var labels = MakerPresetCatalog.labels(RaceMakerParts.Category.HAIR, "saiyan", "male");
        assertTrue(labels.size() >= MakerPresetCatalog.FALLBACK_HAIR.size()
                || !labels.isEmpty());
        assertTrue(labels.contains("Default")
                || labels.get(0).startsWith("Hair ")
                || labels.get(0).equalsIgnoreCase("Default"));
    }
}
