package net.bullettrain.xenopixelsmod.network;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChaseFlightOwnershipTest {
    @Test void restoresOnlyUninterruptedOwnedGrant() {
        assertTrue(ChaseFlightOwnership.mayRestoreGrant(false, true, 0));
        assertFalse(ChaseFlightOwnership.mayRestoreGrant(true, true, 0));
        assertFalse(ChaseFlightOwnership.mayRestoreGrant(true, true, 1));
        assertFalse(ChaseFlightOwnership.mayRestoreGrant(false, false, 0));
        assertFalse(ChaseFlightOwnership.mayRestoreGrant(false, true, 1));
    }
    @Test void endsOnDepletionDeathWorldSeatOrFeatureLoss() {
        assertFalse(ChaseFlightOwnership.mustStop(true, true, true, true, false, true));
        assertFalse(ChaseFlightOwnership.mustStop(false, false, true, true, false, true));
        assertTrue(ChaseFlightOwnership.mustStop(true, false, true, true, false, true));
        assertTrue(ChaseFlightOwnership.mustStop(true, true, false, true, false, true));
        assertTrue(ChaseFlightOwnership.mustStop(true, true, true, false, false, true));
        assertTrue(ChaseFlightOwnership.mustStop(true, true, true, true, true, true));
        assertTrue(ChaseFlightOwnership.mustStop(true, true, true, true, false, false));
    }
}
