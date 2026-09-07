package net.bullettrain.xenopixelsmod.combat;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bt3ComboBeatTest {

    @Test
    void threeLightHitsBecomeAHeavyGroundKnockbackBeat() {
        Bt3ComboBeat beat = Bt3ComboChoreography.beat(
                Bt3AnimationIntent.JAB_LEFT, 3, Bt3ComboChoreography.MASH_STYLE_ROUTE, 0);

        assertFalse(beat.finisher());
        assertFalse(beat.launcher());
        assertFalse(beat.chase());
        assertFalse(beat.guardBreak());
    }

    @Test
    void fourHitStringBecomesFinisherAndGuardBreak() {
        Bt3ComboBeat beat = Bt3ComboChoreography.beat(
                Bt3AnimationIntent.JAB_RIGHT, 4, Bt3ComboChoreography.MASH_STYLE_ROUTE, 0);

        assertTrue(beat.finisher());
        assertTrue(beat.chase());
        assertTrue(beat.guardBreak());
    }

    @Test
    void punchPunchKickLaunchesAndStartsChase() {
        Bt3ComboBeat beat = Bt3ComboChoreography.beat(
                Bt3AnimationIntent.FLYING_KICK, 3, Bt3ComboChoreography.MASH_STYLE_ROUTE, 1);

        assertTrue(beat.launcher());
        assertTrue(beat.chase());
    }

    @Test
    void directionalCyclesEarnAFinisherEveryFourthBeat() {
        Bt3ComboBeat beat = Bt3ComboChoreography.beat(
                Bt3AnimationIntent.CROSS_LEFT, 4, Bt3ComboChoreography.MASH_STYLE_PUNCH, 0);

        assertTrue(beat.finisher());
        assertTrue(beat.guardBreak());
    }
}
