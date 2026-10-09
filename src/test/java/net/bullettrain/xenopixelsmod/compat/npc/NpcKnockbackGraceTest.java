package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-10-02 owner: "and dummytrain not getting knockedbacked?" */
class NpcKnockbackGraceTest {

    @Test void scriptedMovementRefreshesRecoveryInsteadOfExpiringMidLaunch() {
        long first = NpcKnockbackGrace.until(1000, 10);
        long refresh = NpcKnockbackGrace.until(1008, 10);
        assertFalse(NpcKnockbackGrace.open(first, 1010));
        assertTrue(NpcKnockbackGrace.open(refresh, 1010));
        assertTrue(NpcKnockbackGrace.open(refresh, 1017));
        assertFalse(NpcKnockbackGrace.open(refresh, 1018));
    }

    @Test void recoveryCanBeDisabledAndItsDurationIsBounded() {
        assertFalse(NpcKnockbackGrace.open(NpcKnockbackGrace.until(1000, 0), 1000));
        assertEquals(1000, NpcKnockbackGrace.until(1000, -5));
        assertEquals(1200, NpcKnockbackGrace.until(1000, Integer.MAX_VALUE));
        assertTrue(NpcKnockbackGrace.open(NpcKnockbackGrace.until(1000, 1), 1000));
        assertFalse(NpcKnockbackGrace.open(NpcKnockbackGrace.until(1000, 1), 1001));
    }

    @Test
    void theBrainStaysOffForAFewTicksAndThenTakesTheNpcBack() {
        long until = NpcKnockbackGrace.until(1000L);
        assertTrue(NpcKnockbackGrace.open(until, 1000L));
        assertTrue(NpcKnockbackGrace.open(until, 1000L + NpcKnockbackGrace.TICKS - 1));
        assertFalse(NpcKnockbackGrace.open(until, 1000L + NpcKnockbackGrace.TICKS));
        assertFalse(NpcKnockbackGrace.open(null, 1000L), "never knocked back");
    }

    @Test void faceAwayYawPointsBackAtAttacker() {
        // Attacker at +Z of victim → victim should face toward -Z so its back faces +Z (yaw ~180).
        float yaw = NpcKnockbackGrace.faceAwayYaw(0, 0, 0, 10);
        assertEquals(180f, yaw, 0.5f);
        // Attacker at +X → back faces +X → NPC looks -X → yaw ~90 (west).
        float east = NpcKnockbackGrace.faceAwayYaw(0, 0, 10, 0);
        assertEquals(90f, east, 0.5f);
        assertTrue(Float.isNaN(NpcKnockbackGrace.faceAwayYaw(0, 0, 0, 0)), "zero delta has no facing");
    }

    @Test void recoveryHoldsFacingOnlyWhileWindowOpen() {
        assertTrue(NpcKnockbackGrace.shouldHoldFacing(NpcKnockbackGrace.until(1000, 10), 1000));
        assertTrue(NpcKnockbackGrace.shouldHoldFacing(NpcKnockbackGrace.until(1000, 10), 1009));
        assertFalse(NpcKnockbackGrace.shouldHoldFacing(NpcKnockbackGrace.until(1000, 10), 1010));
        assertFalse(NpcKnockbackGrace.shouldHoldFacing(null, 1000));
    }

    @Test void recoveryYawIsNotForcedAfterOwnerRevert() {
        assertNull(NpcKnockbackGrace.recoveryYaw(null));
    }
}
