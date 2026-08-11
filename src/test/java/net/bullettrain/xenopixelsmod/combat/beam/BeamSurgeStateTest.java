package net.bullettrain.xenopixelsmod.combat.beam;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The surge curve. Pure math, so it is testable without a running game — the same reason
 * {@code SableAttitudeMathTest} exists.
 */
class BeamSurgeStateTest {

    /** Long enough for the asymptote to settle at any mastery level. */
    private static final int LONG_HOLD = 2_000;

    private static BeamSurgeState held(int ticks, int mastery) {
        BeamSurgeState state = new BeamSurgeState();
        for (int i = 0; i < ticks; i++) state.tick(true, mastery);
        return state;
    }

    @Test
    void startsAtZero() {
        assertEquals(0.0, new BeamSurgeState().surge(), 1.0e-9);
    }

    @Test
    void sustainedHoldApproachesButNeverExceedsTheCeiling() {
        for (int mastery = 0; mastery <= 3; mastery++) {
            BeamSurgeState state = held(LONG_HOLD, mastery);
            double ceiling = BeamSurgeState.ceiling(mastery);
            assertTrue(state.surge() <= ceiling + 1.0e-9,
                    "mastery " + mastery + " overshot its ceiling: " + state.surge());
            assertTrue(state.surge() > ceiling - 1.0e-3,
                    "mastery " + mastery + " never reached its ceiling: " + state.surge());
        }
    }

    @Test
    void higherMasteryGivesABiggerCeiling() {
        for (int mastery = 1; mastery <= 3; mastery++) {
            assertTrue(BeamSurgeState.ceiling(mastery) > BeamSurgeState.ceiling(mastery - 1),
                    "mastery " + mastery + " is not above " + (mastery - 1));
        }
    }

    /** The ceiling must never exceed 1, or the manager would scale past its configured maxima. */
    @Test
    void ceilingIsClampedToOne() {
        assertTrue(BeamSurgeState.ceiling(99) <= 1.0);
    }

    @Test
    void higherMasteryRampsFaster() {
        // Same short hold, so this compares approach rate rather than final ceiling.
        assertTrue(held(20, 3).surge() > held(20, 0).surge());
    }

    @Test
    void releasingDecaysBackToZero() {
        BeamSurgeState state = held(200, 3);
        assertTrue(state.surge() > 0.1, "precondition: should have surged");
        for (int i = 0; i < 500; i++) state.tick(false, 3);
        assertEquals(0.0, state.surge(), 1.0e-9);
    }

    /** A player who cannot pay is not fed, and an unfed beam must not grow at all. */
    @Test
    void neverFedNeverGrows() {
        BeamSurgeState state = new BeamSurgeState();
        for (int i = 0; i < 200; i++) state.tick(false, 3);
        assertEquals(0.0, state.surge(), 1.0e-9);
        assertEquals(0, state.sustainedTicks());
    }

    @Test
    void sustainedTicksCountUnbrokenFeedingAndResetOnRelease() {
        BeamSurgeState state = held(10, 0);
        assertEquals(10, state.sustainedTicks());
        state.tick(false, 0);
        assertEquals(0, state.sustainedTicks());
    }

    /** Tiers exist to fire feedback cues, so they must be reached in order and only once each. */
    @Test
    void tiersRiseMonotonicallyWhileHeld() {
        BeamSurgeState state = new BeamSurgeState();
        int previous = state.tier();
        assertEquals(0, previous);
        for (int i = 0; i < LONG_HOLD; i++) {
            state.tick(true, 3);
            int tier = state.tier();
            assertTrue(tier >= previous, "tier went backwards while held");
            assertTrue(tier - previous <= 1, "tier skipped a step, so a cue would be missed");
            previous = tier;
        }
        assertEquals(3, previous, "a fully mastered hold should reach the top tier");
    }
}
