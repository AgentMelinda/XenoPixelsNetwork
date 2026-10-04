package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29 owner: an HD aura "better than DMZ", in every aura colour DragonMineZ's forms use,
 * switchable back to DMZ's. Two generated effects per colour (tools/effekseer/efkgen/effects/aura.py).
 */
class AuraPaletteTest {
    static final Path AURA = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks/aura");

    @Test
    void everyPaletteColourHasItsInnerOuterAndRimEffect() {
        List<Integer> palette = AuraPalette.colours();
        assertTrue(palette.size() >= 43, "all DMZ form colours plus the wheel");
        for (int rgb : palette) {
            String hex = AuraPalette.hex(rgb);
            assertTrue(Files.isRegularFile(AURA.resolve("aura_in_" + hex + ".efkefc")), hex);
            assertTrue(Files.isRegularFile(AURA.resolve("aura_out_" + hex + ".efkefc")), hex);
            assertTrue(Files.isRegularFile(AURA.resolve("aura_rim_" + hex + ".efkefc")),
                    hex + ": the aura-off rim flame (2026-09-29 owner)");
        }
    }

    @Test
    void dmzFormColoursMatchExactly() {
        for (int rgb : new int[] {0xFFD700, 0x7FFFFF, 0xDB182C, 0xFF69B4, 0x05030A, 0xFFFFFF}) {
            assertEquals(rgb, AuraPalette.nearest(rgb), AuraPalette.hex(rgb) + " is a form colour");
        }
    }

    @Test
    void aCustomColourSnapsToACloseOne() {
        int custom = 0x3050F0;
        int picked = AuraPalette.nearest(custom);
        // The wheel is 24 hues (15 degrees) at two saturations, so a custom colour lands within
        // about half a step: 0x3050F0 picks 0x0040FF at 80, the same vivid blue.
        assertTrue(AuraPalette.distance(custom, picked) < 100, "picked " + AuraPalette.hex(picked));
    }

    @Test
    void theOuterFlameUsesTheExtraColourWhenTheFormHasOne() {
        float[] rose = {1f, 0.41f, 0.71f};
        float[] red = {0.72f, 0.11f, 0.11f};
        assertArrayEquals(new String[] {"ff69b4", "b71c1c"}, AuraPalette.pair(rose, red));
        assertArrayEquals(new String[] {"ff69b4", "ff69b4"}, AuraPalette.pair(rose, null), "one colour: both layers");
    }

    @Test
    void theStyleSwitchParses() {
        assertEquals(AuraStyle.BOTH, AuraStyle.parse(null), "2026-09-29 owner: both by default");
        assertEquals(AuraStyle.HD, AuraStyle.parse("hd"));
        assertEquals(AuraStyle.BOTH, AuraStyle.parse("Both"));
        assertEquals(AuraStyle.BOTH, AuraStyle.parse("nonsense"));
        assertEquals(AuraStyle.DMZ, AuraStyle.parse("dmz"));
        assertTrue(AuraStyle.HD.hd() && !AuraStyle.HD.dmz());
        assertTrue(AuraStyle.BOTH.hd() && AuraStyle.BOTH.dmz());
    }

    /** 2026-09-29 owner: "aura alpha/brightness". Baked levels 50/75/100/130%, the nearest is used. */
    @Test
    void brightnessPicksTheNearestBakedLevel() {
        assertEquals("", AuraPalette.brightnessSuffix(1.0f));
        assertEquals("_b50", AuraPalette.brightnessSuffix(0.4f));
        assertEquals("_b75", AuraPalette.brightnessSuffix(0.7f));
        assertEquals("_b130", AuraPalette.brightnessSuffix(2.0f));
        for (String level : new String[] {"_b50", "_b75", "", "_b130"}) {
            for (String kind : new String[] {"in", "out", "rim"}) {
                assertTrue(Files.isRegularFile(AURA.resolve("aura_" + kind + "_ffd700" + level + ".efkefc")), kind + level);
            }
        }
    }
}
