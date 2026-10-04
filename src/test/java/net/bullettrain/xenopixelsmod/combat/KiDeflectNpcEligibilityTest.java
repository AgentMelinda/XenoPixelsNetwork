package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KiDeflectNpcEligibilityTest {
    @Test
    void blastEligibleWhenBlastFlagOn() {
        assertTrue(KiDeflect.npcEligibleKind(false, false, true, false));
        assertFalse(KiDeflect.npcEligibleKind(false, false, false, true));
    }

    @Test
    void clashableWaveSkippedUnlessWaveFlag() {
        assertFalse(KiDeflect.npcEligibleKind(false, true, true, false));
        assertTrue(KiDeflect.npcEligibleKind(false, true, false, true));
    }

    @Test
    void clashLockedNeverEligible() {
        assertFalse(KiDeflect.npcEligibleKind(true, false, true, true));
        assertFalse(KiDeflect.npcEligibleKind(true, true, true, true));
    }
}
