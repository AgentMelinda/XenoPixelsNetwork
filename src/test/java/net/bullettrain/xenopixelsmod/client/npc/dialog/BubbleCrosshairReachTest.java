package net.bullettrain.xenopixelsmod.client.npc.dialog;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Why right-clicking a bubble did nothing.
 *
 * <p>The options float above the NPC's head. A player looking at the NPC has the crosshair on its
 * body, with every bubble well above — so requiring the crosshair to land <em>inside</em> a bubble
 * meant aiming past the thing you were talking to. Sneak-to-leave kept working, because that lives
 * in the tick handler and is not positional, which is exactly what made it look like only clicking
 * was broken.
 *
 * <p>Found in a running game. The geometry was correct; the interaction it demanded was not.
 */
class BubbleCrosshairReachTest {

    /** A bubble 100 GUI pixels above the crosshair, as one above an NPC's head would be. */
    private static DialogueBubbleRenderer.OptionHit above(int index, float centreY) {
        return new DialogueBubbleRenderer.OptionHit(index, 90f, centreY - 8f, 150f, centreY + 8f);
    }

    @Test
    void anOptionWellAboveTheCrosshairIsStillReachable() {
        DialogueBubbleRenderer.setHitsForTest(List.of(above(0, 20f)));
        // Crosshair at the screen centre; the bubble sits 100px higher.
        assertNotNull(DialogueBubbleRenderer.nearest(120.0, 120.0, 120.0),
                "looking at the NPC must be enough to answer it");
    }

    @Test
    void theNearestOptionWinsWhenSeveralAreInReach() {
        DialogueBubbleRenderer.setHitsForTest(List.of(above(0, 20f), above(1, 60f), above(2, 100f)));
        assertEquals(2, DialogueBubbleRenderer.nearest(120.0, 120.0, 120.0).index(),
                "the bubble closest to where the player is looking");
    }

    @Test
    void somethingFarOffScreenIsNotDraggedIn() {
        // Otherwise a click aimed at nothing would answer a conversation happening behind you.
        DialogueBubbleRenderer.setHitsForTest(List.of(above(0, -900f)));
        assertNull(DialogueBubbleRenderer.nearest(120.0, 120.0, 120.0));
    }

    @Test
    void anUnusableOptionIsNeverChosen() {
        // A bubble whose projection failed has a zero-size box; it must not become the nearest
        // thing to everything.
        DialogueBubbleRenderer.setHitsForTest(
                List.of(new DialogueBubbleRenderer.OptionHit(0, 0f, 0f, 0f, 0f)));
        assertNull(DialogueBubbleRenderer.nearest(120.0, 120.0, 120.0));
    }

    @Test
    void anExactHitStillWinsOverACloserNeighbourCentre() {
        // Containment is checked first, so aiming squarely at a bubble always takes that one even
        // when a neighbour's centre happens to be nearer the crosshair.
        DialogueBubbleRenderer.setHitsForTest(List.of(
                new DialogueBubbleRenderer.OptionHit(0, 100f, 100f, 140f, 140f),
                above(1, 122f)));
        assertEquals(0, DialogueBubbleRenderer.pick(120.0, 120.0).index());
    }

    @Test
    void aMouseClickOnAScreenIsStillExact() {
        // pick() must not gain the tolerance: a click on a screen means this exact spot, and
        // should never drift to a neighbour.
        DialogueBubbleRenderer.setHitsForTest(List.of(above(0, 20f)));
        assertNull(DialogueBubbleRenderer.pick(120.0, 120.0),
                "no containment, no hit - the reach belongs to the crosshair only");
    }

    @Test
    void nothingAtAllPicksNothing() {
        DialogueBubbleRenderer.setHitsForTest(List.of());
        assertNull(DialogueBubbleRenderer.nearest(120.0, 120.0, 120.0));
    }
}
