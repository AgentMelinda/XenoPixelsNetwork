package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import org.junit.jupiter.api.Test;

import static net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the rush choreography.
 *
 * <p>The server picks a beat and the attacking client predicts the same one by calling this
 * function independently. That only holds while it stays pure and deterministic, so purity is
 * asserted here rather than discovered as a desync in play. The route order is pinned too: the
 * previous implementation alternated punch/kick off the combo counter, and losing the authored
 * order again would be invisible in a diff of the caller.
 */
class Bt3ComboChoreographyTest {

    /** Held-R route: our DMZ-faithful punch pair with strict left/right alternation. */
    private static final Bt3AnimationIntent[] MASH = {
            JAB_LEFT, JAB_RIGHT,
    };

    @Test
    void heldMashAlternatesTheCustomDmzPunchPair() {
        for (int i = 0; i < MASH.length; i++) {
            assertEquals(MASH[i], Bt3ComboChoreography.resolve(0, i + 1, false, false),
                    "mash beat " + (i + 1));
            assertEquals(MASH[i], Bt3ComboChoreography.resolve(0, i + 1, true, true),
                    "finisher flag and airborne must not change the pose");
        }
        assertEquals(2, MASH.length);
        assertEquals(JAB_LEFT, Bt3ComboChoreography.resolve(0, 3, false, false));
    }

    @Test
    void resolveIsPure() {
        // Server authority and client prediction both call this; identical arguments must give
        // identical answers or the attacker sees a different strike from every observer.
        for (int step = 1; step <= 20; step++) {
            Bt3AnimationIntent first = Bt3ComboChoreography.resolve(1, step, false, false);
            assertEquals(first, Bt3ComboChoreography.resolve(1, step, false, false));
        }
    }

    @Test
    void noSameSideStrikeTwiceInARow() {
        for (int route = 0; route < Bt3ComboChoreography.routeCount(); route++) {
            Bt3AnimationIntent previous = null;
            for (int step = 1; step <= 30; step++) {
                Bt3AnimationIntent current = Bt3ComboChoreography.resolve(route, step, false, false);
                if (previous != null && side(previous) != 0) {
                    assertNotEquals(side(previous), side(current),
                            "route " + route + " step " + step + " repeats a side: "
                                    + previous + " then " + current);
                }
                previous = current;
            }
        }
    }

    @Test
    void airborneRouteNeverPlaysAGroundedStanceBeat() {
        // A step-in dash or a rush-in chase read as standing on thin air when played mid-flight.
        for (int step = 1; step <= 20; step++) {
            Bt3AnimationIntent intent = Bt3ComboChoreography.resolve(0, step, false, true);
            assertNotEquals(STEP_IN_DASH, intent);
            assertNotEquals(RUSH_IN_CHASE, intent);
        }
    }

    @Test
    void stringsLongerThanTheRouteKeepResolving() {
        for (int step = 1; step <= 200; step++) {
            assertTrue(Bt3ComboChoreography.resolve(0, step, false, false) != null);
        }
        // Out-of-range route indices wrap rather than throwing: the client mirrors the server's
        // rotation and must not crash if the two ever disagree about how far it has advanced.
        assertTrue(Bt3ComboChoreography.resolve(-7, 3, false, false) != null);
        assertTrue(Bt3ComboChoreography.resolve(999, 3, false, false) != null);
    }

    @Test
    void travelPosesAreNotCountedAsKicks() {
        // isKick() drives the lifting knockback, so a pose-only beat must not silently gain it.
        assertFalse(STEP_IN_DASH.isKick());
        assertFalse(RUSH_IN_CHASE.isKick());
        assertTrue(LOW_KICK_LEFT.isKick());
        assertFalse(JAB_LEFT.isKick());
    }

    @Test
    void onlyTurningBeatsOwnCameraYaw() {
        assertFalse(JAB_LEFT.isBodyYawPose());
        assertFalse(BODY_PUNCH_RIGHT.isBodyYawPose());
        assertFalse(HIGH_ROUNDHOUSE.isBodyYawPose());
        assertTrue(FLYING_KICK.isBodyYawPose());
        assertTrue(SPINNING_BACK_KICK.isBodyYawPose());
        assertTrue(SPIN_HOOK_LEFT.isBodyYawPose());
    }

    @Test
    void overlayLeavesAuthoredBeatsAloneWhenIdle() {
        for (int i = 0; i < MASH.length; i++) {
            int step = i + 1;
            assertEquals(MASH[i], Bt3ComboChoreography.overlay(MASH[i], 0),
                    "overlay must not rewrite mash beat " + step);
        }
    }

    @Test
    void overlayWTapIsFlyingKickLauncher() {
        assertEquals(FLYING_KICK, Bt3ComboChoreography.overlay(JAB_LEFT, 1));
        assertEquals(FLYING_KICK, Bt3ComboChoreography.overlay(HOOK_RIGHT, 1));
    }

    @Test
    void defaultMashContainsNoImplicitSpecialMoves() {
        for (Bt3AnimationIntent intent : MASH) {
            assertFalse(intent.isKick(), intent + " must not be a kick");
            assertNotEquals(UPPERCUT_LEFT, intent);
            assertNotEquals(UPPERCUT_RIGHT, intent);
            assertNotEquals(HEAVY_FINISH, intent);
            assertFalse(intent.name().startsWith("SPIN_"), intent + " must not spin");
        }
    }

    @Test
    void launcherDeltaIsSteepDiagonalOffTheFloor() {
        double horiz = 0.55;
        double up = 1.85;
        double[] d = Bt3ComboChoreography.launcherDelta(1.0, 0.0, horiz, up);
        assertEquals(horiz, d[0], 1.0e-6);
        assertEquals(up, d[1], 1.0e-6);
        assertEquals(0.0, d[2], 1.0e-6);
        assertTrue(d[1] > 0.55, "must leave the floor harder than a mash shove");
        assertTrue(d[1] > Math.hypot(d[0], d[2]), "sharp diagonal: up exceeds away");
    }

    @Test
    void heldStrafeLeftPinsTheStringToAlternatingCrosses() {
        for (int step = 1; step <= 12; step++) {
            Bt3AnimationIntent beat = Bt3ComboChoreography.styled(
                    Bt3ComboChoreography.MASH_STYLE_PUNCH, step);
            assertEquals(step % 2 == 1 ? CROSS_RIGHT : CROSS_LEFT, beat, "punch cycle step " + step);
        }
    }

    @Test
    void heldStrafeRightPinsTheStringToAlternatingUppercuts() {
        for (int step = 1; step <= 12; step++) {
            Bt3AnimationIntent beat = Bt3ComboChoreography.styled(
                    Bt3ComboChoreography.MASH_STYLE_UPPERCUT, step);
            assertEquals(step % 2 == 1 ? UPPERCUT_RIGHT : UPPERCUT_LEFT, beat,
                    "uppercut cycle step " + step);
        }
    }

    @Test
    void aHeldCycleNeverThrowsTheSameSideTwiceRunning() {
        for (int style : new int[]{Bt3ComboChoreography.MASH_STYLE_PUNCH,
                Bt3ComboChoreography.MASH_STYLE_UPPERCUT}) {
            for (int step = 1; step <= 20; step++) {
                assertNotEquals(side(Bt3ComboChoreography.styled(style, step)),
                        side(Bt3ComboChoreography.styled(style, step + 1)),
                        "style " + style + " repeated a side at step " + step);
            }
        }
    }

    /**
     * A null here is what hands the beat back to {@link Bt3ComboChoreography#resolve}, so no held
     * key, a value from a future build, or a forged byte all fall through to the authored route
     * rather than pinning the string to something.
     */
    @Test
    void noHeldKeyAndUnknownStylesFallBackToTheRoute() {
        for (int style : new int[]{Bt3ComboChoreography.MASH_STYLE_ROUTE, -1, 3, 127}) {
            assertNull(Bt3ComboChoreography.styled(style, 1), "style " + style);
            assertNull(Bt3ComboChoreography.styled(style, 7), "style " + style);
        }
    }

    /** Step is clamped the same way {@code resolve} clamps it, so a zero step is still beat one. */
    @Test
    void aNonPositiveStepIsTreatedAsTheFirstBeat() {
        assertEquals(CROSS_RIGHT, Bt3ComboChoreography.styled(Bt3ComboChoreography.MASH_STYLE_PUNCH, 0));
        assertEquals(UPPERCUT_RIGHT,
                Bt3ComboChoreography.styled(Bt3ComboChoreography.MASH_STYLE_UPPERCUT, -4));
    }

    private static int side(Bt3AnimationIntent intent) {
        String name = intent.name();
        if (name.endsWith("_LEFT")) return -1;
        if (name.endsWith("_RIGHT")) return 1;
        return 0;
    }
}
