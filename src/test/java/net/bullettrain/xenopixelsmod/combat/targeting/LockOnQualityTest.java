package net.bullettrain.xenopixelsmod.combat.targeting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LockOnQualityTest {

    @Test
    void zeroProgressIsWeak() {
        assertEquals(LockOnQuality.WEAK, LockOnQuality.fromProgress(0.0));
    }

    @Test
    void justBelowSoftThresholdIsStillWeak() {
        assertEquals(LockOnQuality.WEAK, LockOnQuality.fromProgress(0.34));
    }

    @Test
    void atSoftThresholdBecomesSoft() {
        assertEquals(LockOnQuality.SOFT, LockOnQuality.fromProgress(0.35));
        assertEquals(LockOnQuality.SOFT, LockOnQuality.fromProgress(0.99));
    }

    @Test
    void fullProgressIsHard() {
        assertEquals(LockOnQuality.HARD, LockOnQuality.fromProgress(1.0));
        // Progress is expected to be clamped upstream, but the quality mapping itself must not
        // regress if it ever receives something slightly over 1.0.
        assertEquals(LockOnQuality.HARD, LockOnQuality.fromProgress(1.2));
    }
}
