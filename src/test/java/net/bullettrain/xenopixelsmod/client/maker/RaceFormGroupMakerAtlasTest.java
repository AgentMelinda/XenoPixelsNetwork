package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.dmz.form.RaceFormGroupGuard;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Smoke: green atlas sprites for {@link RaceFormGroupMakerScreen} + guard refuse contract.
 */
class RaceFormGroupMakerAtlasTest {
    private static final String[] SHAPES = {
            "xeno_editor_panel",
            "panel_wide",
            "pill_button",
            "mynpcs_toast",
            "mynpcs_button_row",
            "mynpcs_button_arrow"
    };

    @Test
    void greenMakerShapesResolveAtNativeSize() {
        assertEquals(600, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(320, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(220, XenoAtlasSprites.get("panel_wide", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(90, XenoAtlasSprites.get("panel_wide", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(90, XenoAtlasSprites.get("pill_button", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(24, XenoAtlasSprites.get("pill_button", XenoAtlasSprites.Theme.GREEN).height());
    }

    @Test
    void greenMakerPngsAreOnClasspath() {
        for (String shape : SHAPES) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(RaceFormGroupMakerAtlasTest.class.getClassLoader().getResource(path),
                    "missing green sprite: " + path);
        }
    }

    @Test
    void guardRefusesUnknownRaceForMakerOpen() {
        assertFalse(RaceFormGroupGuard.validateRace("not_a_real_race_id").ok());
        assertTrue(RaceFormGroupGuard.validateGroup("saiyan", "xenopixels_gods_forms").ok());
    }

    @Test
    void previewDebounceDefaultIsWithinSpec() {
        assertTrue(PreviewDebounce.DEFAULT_MS <= 50L);
    }
}
