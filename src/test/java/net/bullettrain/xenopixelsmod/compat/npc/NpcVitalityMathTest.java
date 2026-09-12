package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcVitalityMathTest {
    @Test
    void authoritativeHealthIsVanillaBasePlusScaledVitality() {
        // Player HP is 20 + getHealthBonus(); bonus is vit * VIT_scaling * form VIT mult.
        assertEquals(60, NpcVitalityMath.authoritativeMaxHealth(40, 1.0, 1.0));
        assertEquals(250_020, NpcVitalityMath.authoritativeMaxHealth(250_000, 1.0, 1.0));
    }

    @Test
    void authoritativeHealthAppliesFormMultiplierAndRaceVitScaling() {
        assertEquals(100, NpcVitalityMath.authoritativeMaxHealth(40, 2.0, 1.0));
        // Warrior VIT_scaling is 1.2 in dragonminez-2.1.3 race stats.
        assertEquals(20 + 12000, NpcVitalityMath.authoritativeMaxHealth(10_000, 1.0, 1.2));
        assertEquals(20 + 40 * 2 * 12 / 10, NpcVitalityMath.authoritativeMaxHealth(40, 2.0, 1.2));
        assertEquals(20 + 10_000, NpcVitalityMath.authoritativeMaxHealth(10_000, 1.0, 1.0),
                "HP is never 1:1 with VIT — vanilla 20 is always added");
    }

    @Test
    void hybridHealthStillAddsTheConfiguredNativeBaseline() {
        assertEquals(60, NpcVitalityMath.hybridMaxHealth(20, 40, 1.0, 1.0));
        assertEquals(100, NpcVitalityMath.hybridMaxHealth(20, 40, 2.0, 1.0));
        assertEquals(20 + 48, NpcVitalityMath.hybridMaxHealth(20, 40, 1.0, 1.2));
    }

    @Test
    void invalidInputsFallBackSafely() {
        assertEquals(20, NpcVitalityMath.authoritativeMaxHealth(0, 1.0, 1.0));
        assertEquals(40, NpcVitalityMath.authoritativeMaxHealth(20, Double.NaN, 1.0));
        assertEquals(20, NpcVitalityMath.authoritativeMaxHealth(-100, 5.0, 1.0));
        assertEquals(21, NpcVitalityMath.hybridMaxHealth(0, 20, 1.0, 1.0));
    }

    @Test
    void calculationSaturatesInsteadOfOverflowing() {
        assertEquals(NpcVitalityMath.MAX_HEALTH,
                NpcVitalityMath.authoritativeMaxHealth(Integer.MAX_VALUE, 1000.0, 3.0));
        assertEquals(NpcVitalityMath.MAX_HEALTH,
                NpcVitalityMath.hybridMaxHealth(Integer.MAX_VALUE, Integer.MAX_VALUE, 1000.0, 3.0));
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
