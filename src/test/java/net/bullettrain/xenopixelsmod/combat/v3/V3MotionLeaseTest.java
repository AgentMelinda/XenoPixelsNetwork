package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.V3Motion.Owner;
import org.junit.jupiter.api.Test;

class V3MotionLeaseTest {
    private static final UUID SESSION = UUID.randomUUID();

    @Test void competingOwnersCannotBothHoldTheLease() {
        var lease = new V3Motion.Lease();
        assertTrue(lease.acquire(Owner.APPROACH, SESSION, false));
        assertFalse(lease.acquire(Owner.GRAB, SESSION, false));
        assertFalse(lease.acquire(Owner.CINEMATIC, UUID.randomUUID(), true));
        assertEquals(Owner.APPROACH, lease.owner());
        assertTrue(lease.heldBy(Owner.APPROACH, SESSION));
        assertFalse(lease.heldBy(Owner.GRAB, SESSION));
    }

    @Test void sameOwnerAndSessionReacquiresWithoutResnapshotting() {
        var lease = new V3Motion.Lease();
        assertTrue(lease.acquire(Owner.APPROACH, SESSION, false));
        // Second acquisition happens while our own no-gravity is already applied.
        assertTrue(lease.acquire(Owner.APPROACH, SESSION, true));
        assertEquals(Boolean.FALSE, lease.release());
    }

    @Test void releaseRestoresTheEntryStateOnceAndIsIdempotent() {
        var lease = new V3Motion.Lease();
        assertNull(lease.release());
        lease.acquire(Owner.CROSS, SESSION, true);
        assertEquals(Boolean.TRUE, lease.release());
        assertNull(lease.release());
        assertNull(lease.owner());
        assertTrue(lease.acquire(Owner.THROW, SESSION, false));
    }

    @Test void staleSessionDoesNotOwnTheLease() {
        var lease = new V3Motion.Lease();
        lease.acquire(Owner.APPROACH, SESSION, false);
        assertFalse(lease.heldBy(Owner.APPROACH, UUID.randomUUID()));
        assertFalse(lease.acquire(null, SESSION, false));
        assertFalse(lease.acquire(Owner.STRIKE, null, false));
    }

    @Test void reentrantCinematicUsesANewCastTokenEvenInTheSameFighterSession() {
        var lease = new V3Motion.Lease();
        UUID oldCast = UUID.randomUUID(), nextCast = UUID.randomUUID();
        assertTrue(lease.acquire(Owner.CINEMATIC, oldCast, false));
        assertEquals(Boolean.FALSE, lease.release());
        assertTrue(lease.acquire(Owner.CINEMATIC, nextCast, true));
        assertFalse(lease.heldBy(Owner.CINEMATIC, oldCast));
        assertTrue(lease.heldBy(Owner.CINEMATIC, nextCast));
        assertEquals(Boolean.TRUE, lease.release());
    }
}
