package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke: Unified Maker Studio atlas regions resolve at native sizes (PR-D6c).
 *
 * <p>New PanelSpecs cover only missing sizes; frames / style list / full-body preview /
 * pills reuse existing green shapes.
 */
class MakerAtlasPanelsTest {
    private static final Object[][] NEW_SHAPES = {
            {"xeno_maker_race_card", 72, 56},
            {"xeno_maker_category_col", 96, 240},
            {"xeno_maker_part_grid", 240, 220},
            {"xeno_maker_form_list", 140, 280},
            {"xeno_maker_form_settings", 220, 240},
            {"xeno_maker_preview_sm", 150, 112},
            {"xeno_maker_hair_preview", 280, 240},
    };

    private static final String[] REUSED = {
            "xeno_editor_panel",
            "panel_tall",
            "mynpcs_small_panel",
            "pill_button",
            "xeno_swatch_frame",
            "icon_slot_lg",
    };

    @Test
    void newMakerShapesResolveAtNativeSize() {
        for (Object[] row : NEW_SHAPES) {
            String shape = (String) row[0];
            XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN);
            assertEquals(row[1], sprite.width(), shape);
            assertEquals(row[2], sprite.height(), shape);
        }
    }

    @Test
    void reusedMakerChromeResolvesGreen() {
        assertEquals(600, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(320, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(141, XenoAtlasSprites.get("panel_tall", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(213, XenoAtlasSprites.get("panel_tall", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(176, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(222, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.GREEN).height());
    }

    @Test
    void makerPngsAreOnClasspathForEveryTheme() {
        for (Object[] row : NEW_SHAPES) {
            String shape = (String) row[0];
            for (XenoAtlasSprites.Theme theme : XenoAtlasSprites.Theme.values()) {
                String path = "assets/xenopixelsmod/"
                        + XenoAtlasSprites.get(shape, theme).rl().getPath();
                assertNotNull(MakerAtlasPanelsTest.class.getClassLoader().getResource(path),
                        "missing sprite: " + path);
            }
        }
        for (String shape : REUSED) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(MakerAtlasPanelsTest.class.getClassLoader().getResource(path),
                    "missing reused green sprite: " + path);
        }
    }
}
