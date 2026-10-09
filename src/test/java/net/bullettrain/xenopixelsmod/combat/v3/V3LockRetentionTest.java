package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Owner rule 2026-10-08: a failed or refused action never costs the lock. */
class V3LockRetentionTest {
    @Test void onlyAGoneTargetDropsTheLock() {
        assertTrue(V3Targeting.dropsLock(false, true, true, true), "unloaded / other dimension");
        assertTrue(V3Targeting.dropsLock(true, false, true, true), "dead or removed");
        assertTrue(V3Targeting.dropsLock(true, true, false, true), "changed dimension");
    }

    @Test void temporarilyIneligibleTargetsKeepTheLockAndOnlyRefuseTheAction() {
        assertFalse(V3Targeting.dropsLock(true, true, true, false),
                "out of range, behind a block, invisible for a frame, protection: refuse, keep lock");
        assertFalse(V3Targeting.dropsLock(true, true, true, true));
    }

    @Test void cancelLiveStateFromFailedActionDoesNotClearApprovedLock() {
        var fighter = new V3Fighter(java.util.UUID.randomUUID());
        java.util.UUID lock = java.util.UUID.randomUUID();
        fighter.approvedTarget = lock;
        fighter.state = V3State.TRAVEL;
        fighter.dashTarget = lock;
        fighter.window = V3Window.DASH_CROSS;
        fighter.windowTicksLeft = fighter.windowTicksTotal = 40;
        fighter.cancelLiveState();
        assertEquals(lock, fighter.approvedTarget);
        assertEquals(V3State.IDLE, fighter.state);
        assertNull(fighter.dashTarget);
        assertEquals(V3Window.NONE, fighter.window);
    }
}
