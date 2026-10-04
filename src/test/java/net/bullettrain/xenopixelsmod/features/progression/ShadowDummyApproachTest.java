package net.bullettrain.xenopixelsmod.features.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "a xeno dummy version where he only attacks you no ki blasts" (/xenotrain dummytrain). */
class ShadowDummyApproachTest {

    @Test
    void itClosesInAtItsSpeedAndStopsAtArmsLength() {
        assertEquals(ShadowDummyTraining.MELEE_SPEED, ShadowDummyTraining.approachStep(20.0), 1e-9);
        assertEquals(0.1, ShadowDummyTraining.approachStep(ShadowDummyTraining.MELEE_STAND + 0.1), 1e-9);
        assertEquals(0.0, ShadowDummyTraining.approachStep(ShadowDummyTraining.MELEE_STAND));
        assertEquals(0.0, ShadowDummyTraining.approachStep(0.5), "never backs into or through the trainer");
        assertEquals(0.0, ShadowDummyTraining.approachStep(Double.NaN));
    }

    @Test
    void whereItStopsIsWithinItsReach() {
        assertTrue(ShadowDummyTraining.MELEE_STAND < ShadowDummyTraining.MELEE_REACH);
    }
}
