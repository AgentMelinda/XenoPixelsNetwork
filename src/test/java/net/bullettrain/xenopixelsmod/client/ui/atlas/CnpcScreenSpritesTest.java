package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 owner: the Nearby NPCs and scripter screens are 1:1 CustomNPCs screens in the blue
 * atlas. Their panel and buttons are generated at CustomNPCs' exact sizes (docs/atlas-ui-doco.md
 * section 5) rather than resampled from other sizes.
 */
class CnpcScreenSpritesTest {
    @Test
    void theCustomNpcsSizesAreRegisteredWithPngsInEveryPalette() {
        check("mynpcs_nearby_panel", 256, 216);
        check("mynpcs_button_80x20", 80, 20);
        check("mynpcs_button_100x20", 100, 20);
    }

    private static void check(String shape, int w, int h) {
        XenoAtlasSprites.Sprite sprite = XenoAtlasSprites.get(shape);
        assertNotNull(sprite, shape);
        assertEquals(w, sprite.width(), shape);
        assertEquals(h, sprite.height(), shape);
        String project = System.getProperty("xenopixels.projectDir");
        for (String palette : new String[] {"blue", "gold", "green", "red"}) {
            String file = shape + "_" + palette + ".png";
            boolean found = Files.isRegularFile(Path.of(project, "src/generated/resources/assets/xenopixelsmod/textures/gui/atlas", file))
                    || Files.isRegularFile(Path.of(project, "src/main/resources/assets/xenopixelsmod/textures/gui/atlas", file));
            assertTrue(found, "missing " + file);
        }
    }
}
