package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class V3NativeMeleeClockTest {
    @Test void savedWorldTimeDoesNotDependOnServerUptimeAfterRestart() {
        long savedWorldTime = 900_000L;
        assertFalse(V3NativeMelee.cooldownElapsed(savedWorldTime + 7, savedWorldTime, 8));
        assertTrue(V3NativeMelee.cooldownElapsed(savedWorldTime + 8, savedWorldTime, 8));
        assertTrue(V3NativeMelee.cooldownElapsed(savedWorldTime + 20, savedWorldTime, 8));
    }

    @Test void firstNativeAttackAndFutureSavedTimeFollowDmzAdmission() {
        assertTrue(V3NativeMelee.cooldownElapsed(12, 0, 8));
        assertTrue(V3NativeMelee.cooldownElapsed(12, -1, 8));
        assertFalse(V3NativeMelee.cooldownElapsed(12, 900_000, 8));
    }
}
