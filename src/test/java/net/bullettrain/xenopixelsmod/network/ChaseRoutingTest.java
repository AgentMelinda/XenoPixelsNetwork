package net.bullettrain.xenopixelsmod.network;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChaseRoutingTest {

    @Test
    void chaseProbabilityEndpointsDoNotRollAndExplicitRandomnessIsRetained() {
        java.util.function.DoubleSupplier noRoll = () -> { throw new AssertionError("unexpected roll"); };
        assertTrue(Bt3CombatPacket.rollChaseSuccess(1f, noRoll));
        assertFalse(Bt3CombatPacket.rollChaseSuccess(0f, noRoll));
        assertFalse(Bt3CombatPacket.rollChaseSuccess(Float.NaN, noRoll));
        assertTrue(Bt3CombatPacket.rollChaseSuccess(0.45f, () -> 0.44));
        assertFalse(Bt3CombatPacket.rollChaseSuccess(0.45f, () -> 0.46));
    }

    @Test
    void groundUnderTheTargetIsNotAnObstacle() {
        // The clip stops just short of the destination because the target stands on it.
        assertFalse(ChaseRouting.obstructs(19.5, 20.0));
        // A wall well before the target is.
        assertTrue(ChaseRouting.obstructs(6.0, 20.0));
    }

    @Test
    void aVerticalApproachNeverDetours() {
        assertFalse(ChaseRouting.detourWorthwhile(0.0));
        assertFalse(ChaseRouting.detourWorthwhile(1.0));
        assertTrue(ChaseRouting.detourWorthwhile(8.0));
    }

    @Test
    void clearanceIsMeasuredFromTheObstructionNotThePlayer() {
        // Player at y=80, obstruction at y=64: the detour targets just above the obstruction.
        assertEquals(67.8, ChaseRouting.clearanceY(64.0, 1.8, 2.0), 1.0e-6);
    }

    @Test
    void aDetourThatWouldNotRiseAboveThePlayerIsRejected() {
        // Already above the obstruction: climbing again is the loop that stranded the chase.
        assertFalse(ChaseRouting.detourReachable(67.8, 80.0, 16.0));
        assertTrue(ChaseRouting.detourReachable(67.8, 64.0, 16.0));
        // Too tall to climb.
        assertFalse(ChaseRouting.detourReachable(200.0, 64.0, 16.0));
    }
}
