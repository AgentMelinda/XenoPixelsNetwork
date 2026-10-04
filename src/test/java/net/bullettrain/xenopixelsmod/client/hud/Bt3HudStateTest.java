package net.bullettrain.xenopixelsmod.client.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How fast the BT3 HUD catches up with the game, and what it does on the way.
 *
 * <p>These are the rules the plan asks for in words — state-driven, frame-rate independent, no
 * client-side completion of a server sequence, a prompt that goes the moment its window does — in a
 * form that can fail. None of them is checkable by looking at a screenshot.
 */
class Bt3HudStateTest {

    /** Put the state at full everything, past its first-frame snap. */
    private static Bt3HudState settled() {
        Bt3HudState state = new Bt3HudState();
        state.advance(0f, 1f, 1f, 1f, 0, false, false);
        return state;
    }

    private static void run(Bt3HudState state, float seconds, float step,
                            float hp, float ki, float stm, int lit, boolean prompt,
                            boolean reduced) {
        for (float t = 0f; t < seconds; t += step) {
            state.advance(step, hp, ki, stm, lit, prompt, reduced);
        }
    }

    @Test
    void theFirstFrameShowsTheTruthRatherThanSweepingUpToIt() {
        Bt3HudState state = new Bt3HudState();
        state.advance(0.016f, 0.3f, 0.2f, 0.1f, 0, false, false);
        assertEquals(0.3f, state.hp(), 1e-6f);
        assertEquals(0.2f, state.ki(), 1e-6f);
        assertEquals(0.1f, state.stamina(), 1e-6f);
        assertEquals(0.3f, state.hpGhost(), 1e-6f);
    }

    /**
     * The same wall-clock time gets you to the same place at any frame rate.
     *
     * <p>The per-frame {@code lerp(value, target, k)} the other views use does not do this: at
     * 300fps it covers the distance five times faster than at 60fps, so the same hit reads as a
     * different animation on a different machine.
     */
    @Test
    void easingDoesNotDependOnFrameRate() {
        Bt3HudState slow = settled();
        Bt3HudState fast = settled();
        run(slow, 0.5f, 1f / 30f, 0.2f, 0.2f, 0.2f, 0, false, false);
        run(fast, 0.5f, 1f / 240f, 0.2f, 0.2f, 0.2f, 0, false, false);
        assertEquals(slow.hp(), fast.hp(), 0.01f);
        assertEquals(slow.ki(), fast.ki(), 0.01f);
    }

    @Test
    void barsReachTheirTargetAndStopThere() {
        Bt3HudState state = settled();
        run(state, 2f, 1f / 60f, 0.4f, 0.6f, 0.8f, 0, false, false);
        assertEquals(0.4f, state.hp(), 0.005f);
        assertEquals(0.6f, state.ki(), 0.005f);
        assertEquals(0.8f, state.stamina(), 0.005f);
    }

    @Test
    void theLostHealthTrailHoldsThenFallsBackToTheBar() {
        Bt3HudState state = settled();
        run(state, 0.2f, 1f / 60f, 0.4f, 1f, 1f, 0, false, false);
        assertTrue(state.hpGhost() > state.hp() + 0.1f,
                "the trail should still be showing the health that was just lost");

        run(state, 3f, 1f / 60f, 0.4f, 1f, 1f, 0, false, false);
        assertEquals(state.hp(), state.hpGhost(), 0.01f,
                "the trail never came back down; it would stay at full health for the whole fight");
    }

    @Test
    void healingLeavesNoTrailBehind() {
        Bt3HudState state = settled();
        run(state, 2f, 1f / 60f, 0.2f, 1f, 1f, 0, false, false);
        run(state, 2f, 1f / 60f, 0.9f, 1f, 1f, 0, false, false);
        assertEquals(state.hp(), state.hpGhost(), 0.01f);
    }

    @Test
    void aConvertedMaxPowerSegmentFlashesOnceAndThenSettles() {
        Bt3HudState state = settled();
        state.advance(1f / 60f, 1f, 1f, 1f, 1, false, false);
        assertTrue(state.segmentFlash01() > 0f, "the segment that just converted did not flash");

        run(state, 0.4f, 1f / 60f, 1f, 1f, 1f, 1, false, false);
        assertEquals(0f, state.segmentFlash01(), 1e-6f,
                "the flash looped instead of playing once; converted segments must sit still");
    }

    /**
     * An early release resets the charge on the server, and the HUD goes with it.
     *
     * <p>The alternative — finishing the sweep locally because it had started — is exactly the
     * client-side completion the plan forbids.
     */
    @Test
    void losingChargeSegmentsCancelsTheFlashRatherThanFinishingIt() {
        Bt3HudState state = settled();
        state.advance(1f / 60f, 1f, 1f, 1f, 4, false, false);
        state.advance(1f / 60f, 1f, 1f, 1f, 0, false, false);
        assertEquals(0f, state.segmentFlash01(), 1e-6f);
    }

    @Test
    void thePromptFadesInAndIsGoneSoonAfterItsWindowCloses() {
        Bt3HudState state = settled();
        run(state, 0.3f, 1f / 60f, 1f, 1f, 1f, 0, true, false);
        assertTrue(state.prompt01() > 0.9f, "the prompt never came up");

        // A tick is 50ms. The window closing has to take the prompt with it, not leave it hanging.
        run(state, 0.1f, 1f / 60f, 1f, 1f, 1f, 0, false, false);
        assertTrue(state.prompt01() < 0.4f, "the prompt lingered after its window closed");
        run(state, 0.3f, 1f / 60f, 1f, 1f, 1f, 0, false, false);
        assertEquals(0f, state.prompt01(), 1e-6f, "the prompt never reached zero");
    }

    @Test
    void reducedMotionKeepsTheValuesAndDropsTheDecoration() {
        Bt3HudState state = settled();
        state.advance(1f / 60f, 0.3f, 1f, 1f, 3, true, true);
        assertEquals(state.hp(), state.hpGhost(), 1e-6f, "reduced motion still drew a trail");
        assertEquals(0f, state.segmentFlash01(), 1e-6f, "reduced motion still flashed a segment");

        run(state, 0.5f, 1f / 60f, 0.3f, 0.5f, 0.7f, 3, true, true);
        assertEquals(0.3f, state.hp(), 0.01f, "reduced motion stopped showing the real value");
        assertEquals(0.5f, state.ki(), 0.01f);
        assertEquals(0.7f, state.stamina(), 0.01f);
        assertTrue(state.prompt01() > 0.9f, "the prompt has to appear in reduced motion too");
    }

    /**
     * Reduced motion is a shorter fade, not an instant cut — but it is shorter.
     *
     * <p>A cut is its own kind of distraction, and the plan asks for a direct 100-150ms fade.
     */
    @Test
    void reducedMotionSettlesSoonerThanTheFullTreatment() {
        Bt3HudState reduced = settled();
        Bt3HudState full = settled();
        run(reduced, 0.15f, 1f / 60f, 0f, 1f, 1f, 0, false, true);
        run(full, 0.15f, 1f / 60f, 0f, 1f, 1f, 0, false, false);
        assertTrue(reduced.hp() < full.hp(),
                "reduced motion should already be closer to the new value than the full easing");
    }

    /**
     * A long frame does not skip the animation.
     *
     * <p>Without the step clamp, the first frame after a world load or an alt-tab integrates whole
     * seconds at once and every bar arrives already finished.
     */
    @Test
    void oneEnormousFrameDoesNotFinishTheWholeAnimation() {
        Bt3HudState state = settled();
        state.advance(30f, 0f, 0f, 0f, 0, false, false);
        assertTrue(state.hp() > 0.05f, "a single long frame swallowed the whole easing");
    }

    @Test
    void resetGoesBackToShowingTheNextPlayersRealValues() {
        Bt3HudState state = settled();
        run(state, 1f, 1f / 60f, 0.2f, 0.2f, 0.2f, 0, false, false);
        state.reset();
        state.advance(1f / 60f, 1f, 1f, 1f, 0, false, false);
        assertEquals(1f, state.hp(), 1e-6f,
                "a respawn swept up from the dead player's health instead of showing the new one's");
    }
}
