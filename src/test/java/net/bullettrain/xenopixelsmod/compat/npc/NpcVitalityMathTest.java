package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcVitalityMathTest {
    @Test
    void addsVitalityToConfiguredBaseHealth() {
        assertEquals(60, NpcVitalityMath.maxHealth(20, 40, 1.0));
    }

    @Test
    void appliesTheActiveFormsVitalityMultiplierOnlyToVitality() {
        assertEquals(100, NpcVitalityMath.maxHealth(20, 40, 2.0));
    }

    @Test
    void invalidInputsFallBackSafely() {
        assertEquals(21, NpcVitalityMath.maxHealth(0, 20, 1.0));
        assertEquals(40, NpcVitalityMath.maxHealth(20, 20, Double.NaN));
        assertEquals(20, NpcVitalityMath.maxHealth(20, -100, 5.0));
    }

    @Test
    void calculationSaturatesInsteadOfOverflowing() {
        assertEquals(Integer.MAX_VALUE,
                NpcVitalityMath.maxHealth(Integer.MAX_VALUE, Integer.MAX_VALUE, 1000.0));
    }

    @Test
    void fullHealthStaysFullWhenMaxIncreases() {
        assertEquals(100.0f,
                NpcVitalityMath.preserveHealthPercent(20.0f, 20.0f, 100.0f), 1.0e-6f);
    }

    @Test
    void damagedNpcKeepsItsHealthPercentage() {
        assertEquals(50.0f,
                NpcVitalityMath.preserveHealthPercent(10.0f, 20.0f, 100.0f), 1.0e-6f);
        assertEquals(25.0f,
                NpcVitalityMath.preserveHealthPercent(50.0f, 100.0f, 50.0f), 1.0e-6f);
    }

    @Test
    void zeroHealthDoesNotReviveAnNpc() {
        assertEquals(0.0f,
                NpcVitalityMath.preserveHealthPercent(0.0f, 20.0f, 100.0f), 1.0e-6f);
    }
}
