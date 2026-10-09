package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class V3ChaseRulesTest {
    private static final UUID LAUNCHED = UUID.randomUUID();

    @Test void chaseOnlyFollowsAnIntentionalLaunch() {
        assertTrue(V3Chase.allowed(V3Window.CHASE, 40, LAUNCHED, LAUNCHED, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.NONE, 40, LAUNCHED, LAUNCHED, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.DASH_CROSS, 10, LAUNCHED, LAUNCHED, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.COUNTER, 10, LAUNCHED, LAUNCHED, V3State.IDLE));
    }

    @Test void lateWrongTargetOrBusyFighterIsRefused() {
        assertFalse(V3Chase.allowed(V3Window.CHASE, 0, LAUNCHED, LAUNCHED, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.CHASE, 5, LAUNCHED, UUID.randomUUID(), V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.CHASE, 5, LAUNCHED, null, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.CHASE, 5, null, LAUNCHED, V3State.IDLE));
        assertFalse(V3Chase.allowed(V3Window.CHASE, 5, LAUNCHED, LAUNCHED, V3State.TRAVEL));
        assertFalse(V3Chase.allowed(V3Window.CHASE, 5, LAUNCHED, LAUNCHED, V3State.GRABBED));
    }

    @Test void vanishRespectsItsCooldownUnlessACounterIsOpen() {
        assertTrue(V3Defense.vanishAllowed(100, 100, false));
        assertFalse(V3Defense.vanishAllowed(99, 100, false));
        assertTrue(V3Defense.vanishAllowed(99, 100, true));
        assertEquals(-1, V3Defense.side(V3Direction.LEFT));
        assertEquals(1, V3Defense.side(V3Direction.RIGHT));
        assertEquals(0, V3Defense.side(V3Direction.NONE));
        assertEquals(0, V3Defense.side(null));
    }

    @Test void counterOpensOnlyForAHitFromTheLockedTarget() {
        assertTrue(V3Defense.opensCounter(LAUNCHED, LAUNCHED, 2.5f, V3State.IDLE));
        assertFalse(V3Defense.opensCounter(LAUNCHED, UUID.randomUUID(), 2.5f, V3State.IDLE));
        assertFalse(V3Defense.opensCounter(null, LAUNCHED, 2.5f, V3State.IDLE));
        assertFalse(V3Defense.opensCounter(LAUNCHED, LAUNCHED, 0f, V3State.IDLE));
        assertFalse(V3Defense.opensCounter(LAUNCHED, LAUNCHED, Float.NaN, V3State.IDLE));
        assertFalse(V3Defense.opensCounter(LAUNCHED, LAUNCHED, 2.5f, V3State.GRABBED));
    }

    @Test void grabHoldBreakAndThrowRules() {
        assertTrue(V3Grab.techAllowed(0));
        assertTrue(V3Grab.techAllowed(V3Grab.TECH_TICKS));
        assertFalse(V3Grab.techAllowed(V3Grab.TECH_TICKS + 1));
        assertFalse(V3Grab.techAllowed(-1));
        assertTrue(V3Grab.TECH_TICKS < V3Grab.HOLD_TICKS);

        var forward = V3Grab.throwVelocity(V3Direction.FORWARD, 0, 1);
        assertTrue(forward.z > 1.0 && forward.y > 0 && forward.y < 0.6);
        assertTrue(V3Grab.throwVelocity(V3Direction.BACK, 0, 1).z < -1.0);
        assertTrue(V3Grab.throwVelocity(V3Direction.RIGHT, 0, 1).x < -1.0);
        assertTrue(V3Grab.throwVelocity(V3Direction.LEFT, 0, 1).x > 1.0);
        assertTrue(Double.isFinite(V3Grab.throwVelocity(V3Direction.NONE, 0, 0).lengthSqr()));
    }
    @Test void chaseIsAlsoAvailableAtWillInsideTheDashRange() {
        assertTrue(V3Chase.atWill(V3State.IDLE, 20.0 * 20.0, 24, 100, 100));
        assertTrue(V3Chase.atWill(V3State.IDLE, 900.0 * 900.0, 999, 100, 100));
        assertFalse(V3Chase.atWill(V3State.IDLE, 25.0 * 25.0, 24, 100, 100), "beyond the configured range");
        assertFalse(V3Chase.atWill(V3State.IDLE, 2.0 * 2.0, 24, 100, 100), "already on top of the target");
        assertFalse(V3Chase.atWill(V3State.TRAVEL, 20.0 * 20.0, 24, 100, 100));
        assertFalse(V3Chase.atWill(V3State.IDLE, 20.0 * 20.0, 24, 99, 100), "still cooling down");
    }

    @Test void firedKiIsRemovedAfterItsLifetime() {
        long released = 1000;
        assertFalse(net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.kiExpired(released, released));
        assertFalse(net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.kiExpired(released, released + 119));
        assertTrue(net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.kiExpired(released, released + 120));
        assertTrue(net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueRuntime.kiExpired(released, released - 5),
                "a clock that went backwards (restart) must not keep a projectile forever");
    }
}
