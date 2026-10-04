package net.bullettrain.xenopixelsmod.compat.worldgen;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FastNoiseHeightCompatTest {
    @Test
    void keepsFastNoiseWhenWorldAndNoiseRangesMatch() {
        assertTrue(FastNoiseHeightCompat.canUseFastNoise(-64, 384, -64, 384));
        assertEquals(-64, FastNoiseHeightCompat.fastNoiseSectionBaseY(-64));
    }

    @Test
    void keepsFastNoiseForContainedVanillaRangeInsideExpandedWorld() {
        assertTrue(FastNoiseHeightCompat.canUseFastNoise(-2032, 4064, -64, 384));
        assertEquals(-2032, FastNoiseHeightCompat.fastNoiseSectionBaseY(-2032));
    }

    @Test
    void usesVanillaWhenNoiseRangeFallsOutsideWorldBounds() {
        assertFalse(FastNoiseHeightCompat.canUseFastNoise(-64, 384, -80, 384));
        assertFalse(FastNoiseHeightCompat.canUseFastNoise(-64, 384, -64, 512));
        assertFalse(FastNoiseHeightCompat.canUseFastNoise(-64, 0, -64, 384));
    }
}
