package net.bullettrain.xenopixelsmod.client.tournament;

import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Smoke: green atlas sprites used by {@link TournamentQueueScreen} exist at native sizes.
 */
class TournamentQueueAtlasTest {
    private static final String[] SHAPES = {
            "xeno_editor_panel",
            "header_strip",
            "panel_wide",
            "pill_button",
            "mynpcs_toast"
    };

    @Test
    void greenTournamentShapesResolveAtNativeSize() {
        assertEquals(600, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(320, XenoAtlasSprites.get("xeno_editor_panel", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(180, XenoAtlasSprites.get("header_strip", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(22, XenoAtlasSprites.get("header_strip", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(220, XenoAtlasSprites.get("panel_wide", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(90, XenoAtlasSprites.get("panel_wide", XenoAtlasSprites.Theme.GREEN).height());
        assertEquals(90, XenoAtlasSprites.get("pill_button", XenoAtlasSprites.Theme.GREEN).width());
        assertEquals(24, XenoAtlasSprites.get("pill_button", XenoAtlasSprites.Theme.GREEN).height());
    }

    @Test
    void greenTournamentPngsAreOnClasspath() {
        for (String shape : SHAPES) {
            String path = "assets/xenopixelsmod/"
                    + XenoAtlasSprites.get(shape, XenoAtlasSprites.Theme.GREEN).rl().getPath();
            assertNotNull(TournamentQueueAtlasTest.class.getClassLoader().getResource(path),
                    "missing green sprite: " + path);
        }
    }
}
