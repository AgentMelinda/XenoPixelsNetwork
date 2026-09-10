package net.bullettrain.xenopixelsmod.combat.aura;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the bookkeeping that actually broke the aura.
 *
 * <p>The curve itself has its own tests; what went wrong in play was ownership — a ramp being
 * applied on some frames and not others, and being attributed to the wrong entity. So these are
 * about keys and timing rather than about the shape of the climb.
 */
class RampTableTest {

    private static final long TICK = 50_000_000L; // 50ms
    private static final float RAMP_TICKS = 14f;

    @Test
    void aFirstSightingAdvancesByNothing() {
        // The table has no previous timestamp for a new owner. Advancing by "now" would jump the
        // ramp straight to its end the first time an aura is drawn.
        RampTable<String> table = new RampTable<>();
        assertEquals(0f, table.advance("a", true, System.nanoTime(), RAMP_TICKS), 1e-6f);
    }

    @Test
    void aRampClimbsWhileRising() {
        RampTable<String> table = new RampTable<>();
        long now = 0;
        table.advance("a", true, now, RAMP_TICKS);
        float previous = 0f;
        // One more step than the ramp is long: the first advance deliberately contributes nothing,
        // because a new owner has no previous timestamp to measure against.
        for (int i = 0; i < 15; i++) {
            now += TICK;
            float value = table.advance("a", true, now, RAMP_TICKS);
            assertTrue(value >= previous, "the ramp went backwards while rising");
            previous = value;
        }
        assertEquals(1f, previous, 1e-5f);
    }

    @Test
    void aRampFallsBackWhenItStops() {
        RampTable<String> table = new RampTable<>();
        long now = 0;
        table.advance("a", true, now, RAMP_TICKS);
        for (int i = 0; i < 20; i++) {
            now += TICK;
            table.advance("a", true, now, RAMP_TICKS);
        }
        for (int i = 0; i < 60; i++) {
            now += TICK;
            table.advance("a", false, now, RAMP_TICKS);
        }
        assertEquals(0f, table.peek("a"), 1e-5f);
    }

    @Test
    void twoOwnersDoNotShareARamp() {
        // This is the bug in one test: one entity's charge must never size another's aura.
        RampTable<String> table = new RampTable<>();
        long now = 0;
        table.advance("charging", true, now, RAMP_TICKS);
        table.advance("idle", false, now, RAMP_TICKS);
        for (int i = 0; i < 20; i++) {
            now += TICK;
            table.advance("charging", true, now, RAMP_TICKS);
            table.advance("idle", false, now, RAMP_TICKS);
        }
        assertEquals(1f, table.peek("charging"), 1e-5f);
        assertEquals(0f, table.peek("idle"), 1e-5f);
    }

    @Test
    void aHugeGapDoesNotTeleportTheRamp() {
        // A loading screen or an alt-tab leaves seconds between frames; the aura should climb from
        // where it was rather than snapping to full.
        RampTable<String> table = new RampTable<>();
        table.advance("a", true, 0L, RAMP_TICKS);
        float afterGap = table.advance("a", true, 60_000_000_000L, RAMP_TICKS);
        assertTrue(afterGap < 1f, "a long pause should not complete the ramp: " + afterGap);
    }

    @Test
    void peekingAnUnknownOwnerIsZero() {
        assertEquals(0f, new RampTable<String>().peek("nobody"), 1e-6f);
    }

    @Test
    void aNullOwnerIsIgnoredRatherThanTracked() {
        RampTable<String> table = new RampTable<>();
        assertEquals(0f, table.advance(null, true, System.nanoTime(), RAMP_TICKS), 1e-6f);
        assertEquals(0, table.size());
    }

    @Test
    void clearingForgetsEverything() {
        RampTable<String> table = new RampTable<>();
        table.advance("a", true, 0L, RAMP_TICKS);
        assertEquals(1, table.size());
        table.clear();
        assertEquals(0, table.size());
        assertEquals(0f, table.peek("a"), 1e-6f);
    }
}
