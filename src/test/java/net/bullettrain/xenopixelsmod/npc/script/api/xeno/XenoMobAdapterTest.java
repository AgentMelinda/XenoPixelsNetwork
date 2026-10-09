package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XenoMobAdapterTest {
    @Test void navigationBoundsRejectNonFiniteAndOutOfRangeBeforeMutation() {
        assertDoesNotThrow(() -> XenoMobAdapter.validateNavigation(256, 0, 0, 3, 256 * 256));
        assertThrows(IllegalArgumentException.class,
                () -> XenoMobAdapter.validateNavigation(0, Double.NaN, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> XenoMobAdapter.validateNavigation(0, 0, 0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class,
                () -> XenoMobAdapter.validateNavigation(0, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> XenoMobAdapter.validateNavigation(0, 0, 0, 3.01, 0));
        assertThrows(IllegalArgumentException.class,
                () -> XenoMobAdapter.validateNavigation(257, 0, 0, 1, 257 * 257));
    }
}
