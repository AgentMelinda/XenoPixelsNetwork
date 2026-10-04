package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcKiDeflectPolicyTest {
    @Test
    void closeVictimSuppressesDeflectWhenMinRangeIsSet() {
        assertFalse(NpcKiDeflectPolicy.allow(3.0, 7.0));
        assertFalse(NpcKiDeflectPolicy.allow(6.99, 7.0));
        assertTrue(NpcKiDeflectPolicy.allow(7.0, 7.0));
        assertTrue(NpcKiDeflectPolicy.allow(12.0, 7.0));
    }

    @Test
    void zeroOrInvalidMinRangeKeepsOldAnyDistanceDeflect() {
        assertTrue(NpcKiDeflectPolicy.allow(0.5, 0.0));
        assertTrue(NpcKiDeflectPolicy.allow(1.0, -1.0));
        assertTrue(NpcKiDeflectPolicy.allow(1.0, Double.NaN));
        assertTrue(NpcKiDeflectPolicy.allow(Double.POSITIVE_INFINITY, 7.0));
    }
}
