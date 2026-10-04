package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-10-02 owner: a second, toggleable HD aura (tools/effekseer/efkgen/effects/aura2.py). The
 * game picks it by the same nearest-colour and brightness rule as variant 1, so every colour of
 * the palette needs its effect at every baked level.
 */
class Aura2EffectsTest {
    static final Path AURA2 = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks/aura2");

    @Test
    void everyPaletteColourHasVariantTwoAtEveryBrightness() {
        for (int rgb : AuraPalette.colours()) {
            String hex = AuraPalette.hex(rgb);
            for (float brightness : new float[] {0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.3f}) {
                String name = "aura2_" + hex + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA2.resolve(name)), name);
                // The copy played on your own character, which ignores scene depth.
                String own = "aura2_" + hex + "_nz" + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA2.resolve(own)), own);
            }
        }
    }
}
