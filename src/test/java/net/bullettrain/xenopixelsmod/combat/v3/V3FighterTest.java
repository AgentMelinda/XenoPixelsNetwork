package net.bullettrain.xenopixelsmod.combat.v3;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class V3FighterTest {
    @Test void cancelLiveStateKeepsApprovedLockAndOnlyClearsGestures() {
        var fighter = new V3Fighter(UUID.randomUUID());
        UUID lock = UUID.randomUUID();
        fighter.approvedTarget = lock;
        fighter.target = new V3TargetSnapshot(lock, 7, new net.minecraft.world.phys.Vec3(1, 2, 3),
                net.minecraft.world.phys.Vec3.ZERO, 9);
        fighter.targetRevision = 42;
        fighter.acquireReadyTick = 55;
        fighter.state = V3State.CHARGING_PUNCH;
        fighter.chargeTicks = 12;
        fighter.windowTicksLeft = fighter.windowTicksTotal = 20;
        fighter.cancelLiveState();
        assertEquals(lock, fighter.approvedTarget, "failed/cancelled actions must keep the approved lock");
        assertNotNull(fighter.target, "lock snapshot stays so the client marker does not blink off");
        assertEquals(V3State.IDLE, fighter.state);
        assertEquals(0, fighter.chargeTicks);
        assertEquals(0, fighter.windowTicksLeft);
        assertEquals(0, fighter.windowTicksTotal);
        assertEquals(42, fighter.targetRevision);
        assertEquals(55, fighter.acquireReadyTick);
    }

    @Test void clearApprovedLockDropsOnlyTheLockIdentity() {
        var fighter = new V3Fighter(UUID.randomUUID());
        fighter.approvedTarget = UUID.randomUUID();
        fighter.target = new V3TargetSnapshot(fighter.approvedTarget, 1, net.minecraft.world.phys.Vec3.ZERO,
                net.minecraft.world.phys.Vec3.ZERO, 1);
        fighter.state = V3State.CHARGING_KICK;
        fighter.chargeTicks = 4;
        fighter.clearApprovedLock();
        assertNull(fighter.approvedTarget);
        assertNull(fighter.target);
        assertEquals(V3State.CHARGING_KICK, fighter.state, "explicit unlock does not cancel an unrelated gesture by itself");
        assertEquals(4, fighter.chargeTicks);
    }
    @Test void sessionRotationRejectsQueuedPriorModeInputRegardlessOfHigherSequence() {
        var fighter = new V3Fighter(UUID.randomUUID());
        UUID oldSession = fighter.session();
        assertTrue(fighter.admit(oldSession, 4));
        assertFalse(fighter.admit(oldSession, 4));
        assertFalse(fighter.admit(oldSession, 3));
        fighter.dashReadyTick = 1234;
        fighter.state = V3State.TRAVEL;
        fighter.rotateSession();
        assertNotEquals(oldSession, fighter.session());
        assertEquals(V3State.IDLE, fighter.state);
        assertEquals(1234, fighter.dashReadyTick, "cleanup preserves cooldowns");
        assertFalse(fighter.admit(oldSession, Integer.MAX_VALUE));
        assertEquals(-1, fighter.acknowledgedSequence());
        assertTrue(fighter.admit(fighter.session(), 0));
        assertFalse(fighter.admit(null, 5));
        assertFalse(fighter.admit(fighter.session(), -1));
    }
}
