package net.bullettrain.xenopixelsmod.combat;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the per-string airborne latch in {@link Bt3CombatLimiter#advance}.
 *
 * <p>A jump mid-combo used to swap {@link Bt3ComboChoreography} onto the air route (or off it)
 * because each hit re-read {@code player.onGround()}. The latch is the whole fix, so it is
 * asserted here rather than discovered as a mid-string animation swap in play.
 */
class Bt3ComboLatchTest {

    @Test
    void firstStringStartsAtRouteZero() {
        Bt3CombatLimiter.ComboState first = Bt3CombatLimiter.advance(null, 100, false);
        assertEquals(0, first.routeIndex);
        assertEquals(1, first.step);
        assertFalse(first.airborne);
    }

    @Test
    void groundedStartStaysGroundedWhenALaterHitIsAirborne() {
        Bt3CombatLimiter.ComboState first = Bt3CombatLimiter.advance(null, 100, false);
        Bt3CombatLimiter.ComboState second = Bt3CombatLimiter.advance(first, 104, true);
        assertSame(first, second);
        assertFalse(second.airborne);
        assertEquals(0, second.routeIndex);
        assertEquals(2, second.step);
    }

    @Test
    void airStartStaysAirWhenALaterHitIsGrounded() {
        Bt3CombatLimiter.ComboState first = Bt3CombatLimiter.advance(null, 100, true);
        Bt3CombatLimiter.ComboState second = Bt3CombatLimiter.advance(first, 104, false);
        assertTrue(second.airborne);
        assertEquals(0, second.routeIndex);
        assertEquals(2, second.step);
    }

    @Test
    void decayTakesANewAirborneSnapshotAndRotatesTheRoute() {
        Bt3CombatLimiter.ComboState first = Bt3CombatLimiter.advance(null, 100, false);
        int later = 100 + Bt3CombatLimiter.COMBO_DECAY_TICKS + 1;
        Bt3CombatLimiter.ComboState next = Bt3CombatLimiter.advance(first, later, true);
        assertNotSame(first, next);
        assertTrue(next.airborne);
        assertEquals(Math.floorMod(1, Bt3ComboChoreography.routeCount()), next.routeIndex);
        assertEquals(1, next.step);
    }

    @Test
    void decayOnTheLastRouteWrapsToZero() {
        Bt3CombatLimiter.ComboState state = Bt3CombatLimiter.advance(null, 0, false);
        int routeCount = Bt3ComboChoreography.routeCount();
        int now = 0;
        // Expire and restart until the rotated index wraps back to 0.
        for (int i = 0; i < routeCount; i++) {
            now += Bt3CombatLimiter.COMBO_DECAY_TICKS + 1;
            state = Bt3CombatLimiter.advance(state, now, false);
        }
        assertEquals(0, state.routeIndex);
        assertEquals(1, state.step);
    }

    @Test
    void changingVictimStartsBeatOne() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Bt3CombatLimiter.ComboState state =
                Bt3CombatLimiter.advance(null, 100, false, first);
        state = Bt3CombatLimiter.advance(state, 101, false, first);

        Bt3CombatLimiter.ComboState restarted =
                Bt3CombatLimiter.advance(null, 102, false, second);
        assertEquals(1, restarted.step);
        org.junit.jupiter.api.Assertions.assertEquals(second, restarted.targetId);
    }
}
