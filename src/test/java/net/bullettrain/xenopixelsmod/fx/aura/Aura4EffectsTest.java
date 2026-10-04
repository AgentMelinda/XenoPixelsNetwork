package net.bullettrain.xenopixelsmod.fx.aura;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2026-10-04: aura4 cheaper plume (tools/effekseer/efkgen/effects/aura4.py). Same nearest-colour
 * and brightness rule as aura3; v4 plays these instead of dense {@code aura_out_*}.
 */
class Aura4EffectsTest {
    static final Path AURA4 = Path.of(System.getProperty("xenopixels.projectDir"),
            "src/main/resources/assets/xenopixelsmod/effeks/aura4");

    @Test
    void everyPaletteColourHasVariantFourAtEveryBrightness() {
        assertTrue(Files.isDirectory(AURA4), "missing aura4 folder — run python tools/effekseer/gen_effects.py aura4");
        for (int rgb : AuraPalette.colours()) {
            String hex = AuraPalette.hex(rgb);
            for (float brightness : new float[] {0.1f, 0.25f, 0.5f, 0.75f, 1.0f, 1.3f}) {
                String name = "aura4_" + hex + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA4.resolve(name)), name);
                String own = "aura4_" + hex + "_nz" + AuraPalette.brightnessSuffix(brightness) + ".efkefc";
                assertTrue(Files.isRegularFile(AURA4.resolve(own)), own);
            }
        }
    }
}
