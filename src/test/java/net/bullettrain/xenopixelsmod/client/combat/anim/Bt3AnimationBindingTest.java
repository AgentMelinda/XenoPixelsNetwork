package net.bullettrain.xenopixelsmod.client.combat.anim;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the four DragonMineZ clips the held-direction mash cycles are built out of.
 *
 * <p>{@code Bt3ComboChoreography.styled} only names intents; which animation each one actually
 * plays lives in {@link Bt3AnimationBinding}, so retargeting one of these four there would quietly
 * change what holding a strafe key does with nothing in the choreography or its tests to show it.
 * Cross V1/V2 and generation 4 are Xeno-owned aliases of DMZ's punch movement; generation 1
 * should no longer expose a stock cross name to callers.
 */
class Bt3AnimationBindingTest {

    @Test
    void heldPunchCycleIsBoundToOurCrossAliasesInGenerationOne() {
        assertEquals("combat.xeno_cross_right",
                Bt3AnimationBinding.of(Bt3AnimationIntent.CROSS_RIGHT, 1).dmzAnim());
        assertEquals("combat.xeno_cross_left",
                Bt3AnimationBinding.of(Bt3AnimationIntent.CROSS_LEFT, 1).dmzAnim());
        assertEquals("combat.xeno_cross_right_v2",
                Bt3AnimationBinding.of(Bt3AnimationIntent.CROSS_RIGHT, 2).dmzAnim());
    }

    @Test
    void heldUppercutCycleIsBoundToTheStockOneHandedUppercutsInGenerationOne() {
        assertEquals("combat.one_handed_uppercut_right",
                Bt3AnimationBinding.of(Bt3AnimationIntent.UPPERCUT_RIGHT, 1).dmzAnim());
        assertEquals("combat.one_handed_uppercut_left",
                Bt3AnimationBinding.of(Bt3AnimationIntent.UPPERCUT_LEFT, 1).dmzAnim());
    }

    /**
     * Generation 3 authored its own cross and uppercut, which generations 1 and 2 never had - they
     * borrowed DragonMineZ's 0.5s clips, and those are nine of the seventeen mash beats running at
     * 1.67x. The held A/D cycles still alternate right then left; only the clip changed.
     */
    @Test
    void heldCyclesUseOurOwnClipsInGenerationThree() {
        assertEquals("combat.xeno_cross_right_v3",
                Bt3AnimationBinding.of(Bt3AnimationIntent.CROSS_RIGHT, 3).dmzAnim());
        assertEquals("combat.xeno_cross_left_v3",
                Bt3AnimationBinding.of(Bt3AnimationIntent.CROSS_LEFT, 3).dmzAnim());
        assertEquals("combat.xeno_uppercut_right_v3",
                Bt3AnimationBinding.of(Bt3AnimationIntent.UPPERCUT_RIGHT, 3).dmzAnim());
        assertEquals("combat.xeno_uppercut_left_v3",
                Bt3AnimationBinding.of(Bt3AnimationIntent.UPPERCUT_LEFT, 3).dmzAnim());
    }

    /**
     * The flicker: a held mash fires a beat on a fixed cadence while every clip it names is longer
     * than that cadence, so at the authored speed each swing was cut off partway and restarted
     * from its wind-up — worst on the 360 spins, which got about a quarter turn. Scaling the clip
     * to the beat is what fixes it, and it only holds if it holds for every beat of the route.
     */
    @Test
    void everyMashBeatFinishesInsideItsBeatAtTheDefaultCadence() {
        int beatTicks = 6;
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            Bt3AnimationBinding.Binding binding = Bt3AnimationBinding.of(intent);
            assertNotNull(binding, intent.name() + " has no binding");
            assertTrue(binding.clipSeconds() > 0.0f, intent.name() + " has no clip length");

            float clipTicks = binding.clipSeconds() * 20.0f;
            float speed = Bt3AnimationBinding.mashSpeed(intent, beatTicks);
            assertTrue(clipTicks / speed <= beatTicks + 1.0e-4f,
                    intent.name() + " runs " + (clipTicks / speed) + " ticks in a " + beatTicks
                            + "-tick beat");
        }
    }

    @Test
    void aClipIsOnlyEverSpedUpNeverSlowedBelowItsAuthoredSpeed() {
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            Bt3AnimationBinding.Binding binding = Bt3AnimationBinding.of(intent);
            for (int beatTicks : new int[]{2, 6, 20}) {
                assertTrue(Bt3AnimationBinding.mashSpeed(intent, beatTicks) >= binding.speed(),
                        intent.name() + " slowed below its authored speed at beat " + beatTicks);
            }
        }
    }

    /** A clip shorter than the beat plays at its authored speed and holds its last pose. */
    @Test
    void aShortClipIsNotStretchedToFillTheBeat() {
        Bt3AnimationBinding.Binding dash = Bt3AnimationBinding.of(Bt3AnimationIntent.STEP_IN_DASH);
        assertTrue(dash.clipSeconds() * 20.0f < 6.0f, "this test needs a clip shorter than the beat");
        assertEquals(dash.speed(), Bt3AnimationBinding.mashSpeed(Bt3AnimationIntent.STEP_IN_DASH, 6),
                1.0e-6f);
    }
}
