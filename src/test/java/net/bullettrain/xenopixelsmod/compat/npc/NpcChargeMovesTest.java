package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcChargeMovesTest {

    @Test
    void percentCapsAtTheConfiguredMax() {
        assertEquals(0, NpcChargeMoves.percentOf(0, 28));
        assertEquals(50, NpcChargeMoves.percentOf(14, 28));
        assertEquals(100, NpcChargeMoves.percentOf(28, 28));
        assertEquals(100, NpcChargeMoves.percentOf(40, 28));
    }

    @Test
    void damageChargeFloorsAtAQuarter() {
        assertEquals(0.25f, NpcChargeMoves.damageCharge(0), 1.0e-6f);
        assertEquals(0.25f, NpcChargeMoves.damageCharge(10), 1.0e-6f);
        assertEquals(1.0f, NpcChargeMoves.damageCharge(100), 1.0e-6f);
        assertEquals(1.0f, NpcChargeMoves.damageCharge(200), 1.0e-6f);
    }

    @Test
    void fullChargeIsNinetyFivePercent() {
        assertFalse(NpcChargeMoves.fullyCharged(NpcChargeMoves.damageCharge(94)));
        assertTrue(NpcChargeMoves.fullyCharged(NpcChargeMoves.damageCharge(95)));
        assertTrue(NpcChargeMoves.fullyCharged(1.0f));
    }

    @Test
    void durationZeroHoldsUntilTheMaxAndPositiveDurationClamps() {
        assertEquals(28, NpcChargeMoves.holdTicks(0, 28));
        assertEquals(28, NpcChargeMoves.holdTicks(-3, 28));
        assertEquals(10, NpcChargeMoves.holdTicks(10, 28));
        assertEquals(28, NpcChargeMoves.holdTicks(99, 28));
        assertEquals(1, NpcChargeMoves.holdTicks(0, 0));
    }

    @Test
    void kickBiasClampsToOneStep() {
        assertEquals(-1, NpcChargeMoves.clampBias(-4));
        assertEquals(0, NpcChargeMoves.clampBias(0));
        assertEquals(1, NpcChargeMoves.clampBias(9));
    }

    @Test
    void cancelWithoutAnEntityDoesNotThrow() {
        assertFalse(NpcChargeMoves.cancel(null));
        NpcChargeMoves.forget(null);
    }

    @Test
    void forgetRemovesALiveChargeEntry() throws Exception {
        java.lang.reflect.Field field = NpcChargeMoves.class.getDeclaredField("CHARGES");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<java.util.UUID, Object> charges =
                (java.util.Map<java.util.UUID, Object>) field.get(null);
        Class<?> chargeClass = Class.forName(
                "net.bullettrain.xenopixelsmod.compat.npc.NpcChargeMoves$Charge");
        java.lang.reflect.Constructor<?> ctor = chargeClass.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        java.util.UUID id = java.util.UUID.randomUUID();
        Object charge = ctor.newInstance(NpcChargeMoves.Style.PUNCH, 0, 20, 0);
        charges.put(id, charge);
        try {
            assertTrue(charges.containsKey(id));
            NpcChargeMoves.forget(id);
            assertFalse(charges.containsKey(id));
        } finally {
            charges.remove(id);
        }
    }
}
