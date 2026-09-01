package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcStatMathTest {
    @Test
    void strengthDrivesMeleeAndUsesItsOwnFormMultiplier() {
        assertEquals(101.0, NpcStatMath.meleeDamage(50, 2.0, 1.0), 1.0e-6);
    }

    @Test
    void strikePowerIncludesOneQuarterStrength() {
        assertEquals(126.0,
                NpcStatMath.strikeDamage(50, 100, 2.0, 1.0, 1.0), 1.0e-6);
    }

    @Test
    void powerDrivesKiDamage() {
        assertEquals(150.0, NpcStatMath.kiDamage(100, 3.0, 0.5), 1.0e-6);
    }

    @Test
    void energyAndResistanceCreateIndependentPools() {
        assertEquals(220.0, NpcStatMath.maxEnergy(100, 2.0), 1.0e-6);
        assertEquals(320.0, NpcStatMath.maxStamina(100, 3.0), 1.0e-6);
    }

    @Test
    void defenseReductionIsBoundedByConfiguredCap() {
        assertEquals(50.0, NpcStatMath.mitigate(100.0, 1000.0, 12.0, 0.5), 1.0e-6);
        assertEquals(100.0, NpcStatMath.mitigate(100.0, 0.0, 12.0, 0.5), 1.0e-6);
    }

    @Test
    void resourceResizePreservesPercentage() {
        assertEquals(100.0, NpcStatMath.preservePercent(50.0, 100.0, 200.0), 1.0e-6);
    }

    @Test
    void recoveryMatchesDmzBasePlusStatOverFivePerSecond() {
        assertEquals(2.2, NpcStatMath.resourceRecoveryPerTick(100, 2.0), 1.0e-6);
    }
}
