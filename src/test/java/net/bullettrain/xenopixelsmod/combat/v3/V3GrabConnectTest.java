package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class V3GrabConnectTest {
    @Test void throwingDamageHookReleasesBothUncommittedLeases() {
        UUID session = UUID.randomUUID();
        var grabber = new V3Motion.Lease();
        var victim = new V3Motion.Lease();
        var failure = new IllegalStateException("damage listener failed");
        var releases = new AtomicInteger();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> V3Grab.connect(() -> {
            grabber.acquire(V3Motion.Owner.GRAB, session, false);
            victim.acquire(V3Motion.Owner.GRAB, session, true);
            throw failure;
        }, () -> {
            assertEquals(Boolean.FALSE, grabber.release());
            assertEquals(Boolean.TRUE, victim.release());
            releases.incrementAndGet();
        })));
        assertNull(grabber.owner());
        assertNull(victim.owner());
        assertEquals(1, releases.get());
    }

    @Test void refusedContactRollsBackButAcceptedContactKeepsTheHold() {
        var rollbacks = new AtomicInteger();
        assertFalse(V3Grab.connect(() -> false, rollbacks::incrementAndGet));
        assertEquals(1, rollbacks.get());
        assertTrue(V3Grab.connect(() -> true, rollbacks::incrementAndGet));
        assertEquals(1, rollbacks.get());
    }

    @Test void linkageFailureAlsoRollsBack() {
        var rollbacks = new AtomicInteger();
        assertThrows(NoSuchMethodError.class, () -> V3Grab.connect(() -> {
            throw new NoSuchMethodError("foreign combat hook");
        }, rollbacks::incrementAndGet));
        assertEquals(1, rollbacks.get());
    }
}
