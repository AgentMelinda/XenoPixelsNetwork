package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoAtlasSpritesTest {
    @Test
    void knownShapesResolveWithVerifiedDimensions() {
        assertEquals(256, XenoAtlasSprites.get("mynpcs_main_panel", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(195, XenoAtlasSprites.get("mynpcs_main_panel", XenoAtlasSprites.Theme.BLUE).height());
        assertEquals(176, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(222, XenoAtlasSprites.get("mynpcs_small_panel", XenoAtlasSprites.Theme.BLUE).height());
        assertEquals(28, XenoAtlasSprites.get("mynpcs_tab", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(200, XenoAtlasSprites.get("mynpcs_top_button", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(197, XenoAtlasSprites.get("mynpcs_side_button", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(842, XenoAtlasSprites.get("panel_editor", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(471, XenoAtlasSprites.get("panel_editor", XenoAtlasSprites.Theme.BLUE).height());
        assertEquals(420, XenoAtlasSprites.get("xeno_quest_journal_panel", XenoAtlasSprites.Theme.BLUE).width());
        assertEquals(180, XenoAtlasSprites.get("xeno_quest_complete_banner", XenoAtlasSprites.Theme.GOLD).height());
    }

    @Test
    void themeSuffixMatchesExtractedFileNames() {
        for (String shape : XenoAtlasSprites.shapes()) {
            for (XenoAtlasSprites.Theme t : XenoAtlasSprites.Theme.values()) {
                String path = XenoAtlasSprites.get(shape, t).rl().getPath();
                assertTrue(path.endsWith("_" + t.name().toLowerCase() + ".png"), path);
            }
        }
    }

    @Test
    void redSpeechBubblesResolveToTheGeneratedRedAtlasFiles() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bubblePalette = "red";
        NpcCombatProfile fromVisualOptions = new NpcCombatProfile();
        fromVisualOptions.applyVisualOptions(profile.visualOptionsTag());
        assertEquals("RED", fromVisualOptions.bubblePalette);

        XenoAtlasSprites.Sprite red = XenoAtlasSprites.get(
                "speech_bubble", XenoAtlasSprites.themeForPalette(fromVisualOptions.bubblePalette));
        XenoAtlasSprites.Sprite blue = XenoAtlasSprites.get(
                "speech_bubble", XenoAtlasSprites.Theme.BLUE);

        assertEquals("textures/gui/atlas/speech_bubble_red.png", red.rl().getPath());
        assertNotNull(red.rl());
        assertTrue(!red.rl().equals(blue.rl()), "red must not reuse the blue sprite");
        Path generated = RepoRoot.of("src/main/resources/assets/xenopixelsmod")
                .resolve(red.rl().getPath());
        assertTrue(Files.isRegularFile(generated), generated + " must be shipped");
    }

    @Test
    void lowerCasePaletteNamesResolveWithoutFallingBackToBlue() {
        assertEquals(XenoAtlasSprites.Theme.RED, XenoAtlasSprites.themeForPalette("red"));
        assertEquals(XenoAtlasSprites.Theme.BLUE, XenoAtlasSprites.themeForPalette("not-a-palette"));
    }

    @Test
    void unknownShapeThrows() {
        assertThrows(IllegalArgumentException.class, () -> XenoAtlasSprites.get("nope"));
    }

    @Test
    void everyRegisteredShapeHasAnExtractedPngForEveryTheme() {
        for (String shape : XenoAtlasSprites.shapes()) {
            for (XenoAtlasSprites.Theme t : XenoAtlasSprites.Theme.values()) {
                String path = "assets/xenopixelsmod/" + XenoAtlasSprites.get(shape, t).rl().getPath();
                assertNotNull(XenoAtlasSpritesTest.class.getClassLoader().getResource(path),
                        "missing extracted sprite: " + path);
            }
        }
    }

    @Test
    void fittedSizeKeepsNativeDimensionsWhenTheSpriteFits() {
        // The layouts are built around native dimensions, so a viewport with room to spare must
        // not rescale anything - rescaling is what softens the art the un-blurred screen exists for.
        assertArrayEquals(new int[]{842, 471}, XenoAtlasSprites.fittedSize("panel_editor", 960, 540));
        assertArrayEquals(new int[]{842, 471}, XenoAtlasSprites.fittedSize("panel_editor", 842, 471));
        assertArrayEquals(new int[]{70, 26}, XenoAtlasSprites.fittedSize("tab_docked", 400, 300));
    }

    @Test
    void fittedSizeShrinksUniformlyWhenTheViewportIsTooSmall() {
        // GUI scale 3 on a 1080p screen gives a 640x360 viewport, which cannot hold panel_editor.
        int[] fitted = XenoAtlasSprites.fittedSize("panel_editor", 640, 360);
        assertTrue(fitted[0] <= 640 && fitted[1] <= 360,
                "fitted size " + fitted[0] + "x" + fitted[1] + " must fit the viewport");
        // Uniform scale: the aspect ratio survives, so the frame is never stretched out of shape.
        double nativeRatio = 842.0 / 471.0;
        double fittedRatio = fitted[0] / (double) fitted[1];
        assertTrue(Math.abs(nativeRatio - fittedRatio) < 0.02,
                "aspect ratio drifted: " + nativeRatio + " vs " + fittedRatio);
    }

    @Test
    void fittedSizeNeverReturnsADegenerateBox() {
        int[] fitted = XenoAtlasSprites.fittedSize("panel_editor", 1, 1);
        assertTrue(fitted[0] >= 1 && fitted[1] >= 1);
    }
}
