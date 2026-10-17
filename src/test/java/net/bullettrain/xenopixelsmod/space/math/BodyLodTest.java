package net.bullettrain.xenopixelsmod.space.math;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BodyLodTest {
    @Test void visibilityCannotPreventSimulationActivation() {
        assertEquals(BodyLod.FIELD, BodyLod.resolve(30_000, 100, BodyLod.ABSENT, null, false));
        assertEquals(BodyLod.ANCHOR, BodyLod.resolve(5_000, 100, BodyLod.ABSENT, null, false));
        assertEquals(BodyLod.CHUNKS, BodyLod.resolve(100, 100, BodyLod.ABSENT, null, false));
        assertEquals(BodyLod.FIELD, BodyLod.resolve(50_000, 100, BodyLod.FIELD, null, false));
        assertEquals(BodyLod.ABSENT, BodyLod.resolve(50_001, 100, BodyLod.FIELD, null, false));
    }

    @Test void chunksUseTheSameDemotionHysteresisAsOtherStages() {
        assertEquals(BodyLod.CHUNKS, BodyLod.resolve(120, 100, BodyLod.CHUNKS, null, true));
        assertEquals(BodyLod.ANCHOR, BodyLod.resolve(120, 100, BodyLod.ANCHOR, null, true));
        assertEquals(BodyLod.ANCHOR, BodyLod.resolve(132, 100, BodyLod.CHUNKS, null, true));
    }

    @Test void infiniteThresholdsCannotKeepBodiesPermanentlyActive() {
        assertThrows(IllegalArgumentException.class,
                () -> new BodyLod.Thresholds(Double.POSITIVE_INFINITY, 400, 60, 1.05, .25));
        assertThrows(IllegalArgumentException.class,
                () -> new BodyLod.Thresholds(900, 400, 60, 1.05, Double.POSITIVE_INFINITY));
    }
}
