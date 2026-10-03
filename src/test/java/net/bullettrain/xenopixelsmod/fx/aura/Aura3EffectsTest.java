package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-10-03: aura3 matrix gate (tools/effekseer/efkgen/effects/aura3.py). Same nearest-colour
 * and brightness rule as aura2; every palette colour needs depth and _nz at every baked level.
 */
class Aura3EffectsTest {
    static final Path AURA3 = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks/aura3");

    @Test
    void everyPaletteColourHasVariantThreeAtEveryBrightness() {
        assertTrue(Files.isDirectory(AURA3), "missing aura3 folder");
        for (int rgb : AuraPalette.colours()) {
            String hex = AuraPalette.hex(rgb);
            for (float brightness : new float[] {0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.3f}) {
                String name = "aura3_" + hex + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA3.resolve(name)), name);
                // The copy played on your own character, which ignores scene depth.
                String own = "aura3_" + hex + "_nz" + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA3.resolve(own)), own);
            }
        }
    }
}
