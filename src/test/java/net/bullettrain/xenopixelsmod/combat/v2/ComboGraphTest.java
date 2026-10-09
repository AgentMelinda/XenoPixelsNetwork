package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.v2.combo.BranchFlavor;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraph;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraphParser;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboGraphs;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboInput;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboMachine;
import net.bullettrain.xenopixelsmod.combat.v2.combo.ComboNode;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shipped combo routes and the rule that walks them. The owner's requirements are the anchors:
 * three landed lights open a choice and the fourth input picks kick, smash, grab or rush; heavy
 * is kicks; and a string is paced by its authored timings, not by how fast the mouse is clicked.
 */
class ComboGraphTest {

    private static final ComboGraph GRAPH = ComboGraphs.bundled();

    /** Presses {@code input} the first tick the beat {@code from} accepts a follow-up. */
    private static ComboNode play(ComboNode from, ComboInput input) {
        int ticks = from == null ? 0 : from.openTick();
        ComboMachine.Decision d = ComboMachine.decide(GRAPH, from, ticks, true, input);
        assertEquals(ComboMachine.Outcome.PLAY, d.outcome(), "expected " + input + " to play");
        return d.node();
    }

    // ---- routes ----

    @Test
    void bundledGraphLoadsAndEveryBranchResolves() {
        assertTrue(GRAPH.size() >= 10);
        for (ComboNode node : GRAPH.nodes()) {
            for (ComboInput input : node.next().keySet()) {
                assertNotNull(GRAPH.next(node, input), node.id() + " " + input);
            }
        }
    }

    @Test
    void threeLightsOpenKickSmashGrabAndRush() {
        ComboNode l3 = play(play(play(null, ComboInput.LIGHT), ComboInput.LIGHT), ComboInput.LIGHT);
        assertEquals("l3", l3.id());
        int mask = l3.branchMask();
        assertTrue(ComboInput.has(mask, ComboInput.LIGHT));
        assertTrue(ComboInput.has(mask, ComboInput.HEAVY));
        assertTrue(ComboInput.has(mask, ComboInput.GRAB));
        assertTrue(ComboInput.has(mask, ComboInput.RUSH));
    }

    @Test
    void fourthLightIsAKickFinisher() {
        ComboNode kick = play(GRAPH.node("l3"), ComboInput.LIGHT);
        assertTrue(kick.intent().isKick(), "the fourth light must be a kick");
        assertTrue(kick.reaction().isKick(), "and must launch like one");
        assertTrue(kick.reaction().opensChase());
        assertTrue(kick.terminal());
    }

    @Test
    void fourthHeavyKnocksAwayAndOpensAChase() {
        ComboNode heavy = play(GRAPH.node("l3"), ComboInput.HEAVY);
        assertTrue(heavy.reaction().opensChase());
    }

    @Test
    void fourthGrabAndRushHandOff() {
        assertEquals(ComboNode.Action.GRAB, play(GRAPH.node("l3"), ComboInput.GRAB).action());
        assertEquals(ComboNode.Action.RUSH, play(GRAPH.node("l3"), ComboInput.RUSH).action());
    }

    @Test
    void heavyAfterTwoLightsLaunches() {
        ComboNode l2 = play(play(null, ComboInput.LIGHT), ComboInput.LIGHT);
        assertEquals(HitReaction.LAUNCH_UP, play(l2, ComboInput.HEAVY).reaction());
    }

    // ---- kicks ----

    @Test
    void heavyIsKicksFromTheFirstPress() {
        ComboNode first = GRAPH.start(ComboInput.HEAVY);
        assertNotNull(first);
        assertTrue(first.intent().isKick(), "a heavy attack in neutral must be a kick");
        assertTrue(play(first, ComboInput.HEAVY).intent().isKick(), "and so must the one after it");
        assertTrue(play(GRAPH.start(ComboInput.LIGHT), ComboInput.HEAVY).intent().isKick(),
                "a heavy after one light must be a kick too");
    }

    @Test
    void aKickWithADirectionHeldIsTheLauncherOrTheSlam() {
        ComboNode kick = GRAPH.start(ComboInput.HEAVY);
        assertEquals(HitReaction.KICK_UP, kick.reactionFor(V2Direction.FORWARD, false));
        assertEquals(HitReaction.KICK_DOWN, kick.reactionFor(V2Direction.BACK, false));
    }

    @Test
    void aHeldKickLaunchesAndATappedOneKeepsTheTargetClose() {
        ComboNode kick = GRAPH.start(ComboInput.HEAVY);
        assertEquals(HitReaction.KICK_ARC, kick.reactionFor(V2Direction.NONE, true));
        assertFalse(kick.reactionFor(V2Direction.NONE, false).opensChase(),
                "a tapped opening kick must leave the target in reach for the next beat");
    }

    @Test
    void aHeldDirectionDecidesTheKickHoweverLongItWasCharged() {
        ComboNode kick = GRAPH.start(ComboInput.HEAVY);
        assertEquals(HitReaction.KICK_UP, kick.reactionFor(V2Direction.FORWARD, true));
    }

    /**
     * DragonMineZ already charges, and can empty, the attacker's stamina on a melee hit. A beat
     * that also cost stamina is what stopped every kick coming out in the first v2 build.
     */
    @Test
    void noShippedBeatCostsStamina() {
        for (ComboNode node : GRAPH.nodes()) {
            assertEquals(0f, node.staminaCost(), node.id() + " must not cost stamina");
        }
    }

    // ---- styling ----

    @Test
    void heldDirectionChangesThePoseOnEveryLightBeat() {
        for (String id : new String[]{"l1", "l2", "l3"}) {
            ComboNode node = GRAPH.node(id);
            Bt3AnimationIntent neutral = node.intentFor(V2Direction.NONE);
            assertEquals(node.intent(), neutral);
            for (V2Direction dir : new V2Direction[]{
                    V2Direction.FORWARD, V2Direction.BACK, V2Direction.LEFT, V2Direction.RIGHT}) {
                assertNotEquals(neutral, node.intentFor(dir),
                        id + " must have its own pose with " + dir + " held");
            }
        }
    }

    @Test
    void anUnauthoredDirectionChangesNothing() {
        ComboNode node = GRAPH.node("l1");
        assertEquals(node.intent(), node.intentFor(V2Direction.UP));
        assertEquals(node.intent(), node.intentFor(null));
        assertEquals(node.reaction(), node.reactionFor(V2Direction.UP, false));
        assertEquals(node.reaction(), node.reactionFor(null, false));
    }

    @Test
    void aPoseOnlyVariantKeepsTheNodesReaction() {
        ComboNode l1 = GRAPH.node("l1");
        assertEquals(l1.reaction(), l1.reactionFor(V2Direction.LEFT, false));
        assertEquals(l1.chargedReaction(), l1.reactionFor(V2Direction.LEFT, true));
    }

    // ---- pacing ----

    @Test
    void anEarlyInputIsHeldNotDropped() {
        ComboNode l1 = GRAPH.node("l1");
        assertTrue(l1.openTick() > 0);
        assertEquals(ComboMachine.Outcome.BUFFER,
                ComboMachine.decide(GRAPH, l1, 0, false, ComboInput.LIGHT).outcome());
    }

    @Test
    void anEarlyInputThatCanOnlyOpenANewStringIsHeldToo() {
        ComboNode kick = GRAPH.node("kick_finisher");
        assertEquals(ComboMachine.Outcome.BUFFER,
                ComboMachine.decide(GRAPH, kick, 0, true, ComboInput.HEAVY).outcome());
    }

    @Test
    void anEarlyInputThatLeadsNowhereIsRejected() {
        ComboNode kick = GRAPH.node("kick_finisher");
        assertEquals(ComboMachine.Outcome.REJECT,
                ComboMachine.decide(GRAPH, kick, 0, true, ComboInput.RUSH).outcome());
    }

    /** The click rate must not set the pace: nothing may start while a beat is still closed. */
    @Test
    void nothingStartsBeforeABeatHasRecovered() {
        for (ComboNode node : GRAPH.nodes()) {
            for (ComboInput input : ComboInput.values()) {
                for (int tick = 0; tick < node.openTick(); tick++) {
                    assertNotEquals(ComboMachine.Outcome.PLAY,
                            ComboMachine.decide(GRAPH, node, tick, true, input).outcome(),
                            node.id() + " accepted " + input + " at tick " + tick
                                    + " of " + node.openTick());
                }
            }
        }
    }

    /**
     * Two hits on one target closer together than DragonMineZ's own 35 ms hit guard are dropped
     * by DragonMineZ, so no route may put a hit on the tick after the last one.
     */
    @Test
    void consecutiveHitsAreNeverOnAdjacentTicks() {
        for (ComboNode node : GRAPH.nodes()) {
            if (node.action() != ComboNode.Action.STRIKE) continue;
            for (ComboInput input : node.next().keySet()) {
                ComboNode next = GRAPH.next(node, input);
                if (next.action() != ComboNode.Action.STRIKE) continue;
                int gap = node.cancelTicks() + next.startupTicks();
                assertTrue(gap >= 2, node.id() + " -> " + next.id() + " hits " + gap + " tick(s) apart");
            }
        }
    }

    @Test
    void aWhiffedBeatCannotBeContinuedButOpensANewString() {
        ComboNode l2 = GRAPH.node("l2");
        ComboMachine.Decision d = ComboMachine.decide(GRAPH, l2, l2.openTick(), false, ComboInput.LIGHT);
        assertEquals(ComboMachine.Outcome.PLAY, d.outcome());
        assertEquals("l1", d.node().id());
    }

    @Test
    void aClosedWindowStartsOver() {
        ComboNode l2 = GRAPH.node("l2");
        int late = l2.closeTick() + 1;
        assertTrue(ComboMachine.expired(l2, late));
        assertFalse(ComboMachine.expired(l2, l2.closeTick()));
        assertEquals("l1", ComboMachine.decide(GRAPH, l2, late, true, ComboInput.LIGHT).node().id());
        assertEquals(0, ComboMachine.choiceTicksLeft(l2, late));
    }

    @Test
    void aFinisherRecoversAndThenStartsAFreshString() {
        ComboNode kick = GRAPH.node("kick_finisher");
        ComboMachine.Decision d = ComboMachine.decide(GRAPH, kick, kick.openTick(), true, ComboInput.LIGHT);
        assertEquals(ComboMachine.Outcome.PLAY, d.outcome());
        assertEquals("l1", d.node().id());
    }

    @Test
    void theChoiceIsCountedFromTheHitThroughRecoveryToTheEndOfTheWindow() {
        ComboNode l3 = GRAPH.node("l3");
        int total = ComboMachine.choiceTicksTotal(l3);
        assertEquals(l3.cancelTicks() + l3.windowTicks(), total);
        assertEquals(0, ComboMachine.choiceTicksLeft(l3, l3.startupTicks() - 1));
        assertEquals(total, ComboMachine.choiceTicksLeft(l3, l3.startupTicks()));
        assertEquals(total - 5, ComboMachine.choiceTicksLeft(l3, l3.startupTicks() + 5));
        assertEquals(0, ComboMachine.choiceTicksLeft(l3, l3.closeTick()));
    }

    @Test
    void bufferExpires() {
        assertTrue(ComboMachine.bufferLive(10, 14, 6));
        assertFalse(ComboMachine.bufferLive(10, 17, 6));
        assertFalse(ComboMachine.bufferLive(-1, 0, 6));
    }

    // ---- what the prompt calls a branch ----

    @Test
    void branchesAreNamedForWhatTheyDo() {
        assertEquals(BranchFlavor.PUNCH, BranchFlavor.of(GRAPH.node("l2")));
        assertEquals(BranchFlavor.KICK, BranchFlavor.of(GRAPH.node("kick_finisher")));
        assertEquals(BranchFlavor.KICK, BranchFlavor.of(GRAPH.node("l1_kick")),
                "a kick pose is a kick even when it only staggers");
        assertEquals(BranchFlavor.LAUNCH, BranchFlavor.of(GRAPH.node("launcher")));
        assertEquals(BranchFlavor.SMASH, BranchFlavor.of(GRAPH.node("knockback")));
        assertEquals(BranchFlavor.PUNCH, BranchFlavor.of(null));
    }

    @Test
    void afterThreeLightsThePromptSaysKickNotLight() {
        ComboNode l3 = GRAPH.node("l3");
        assertEquals(BranchFlavor.KICK, BranchFlavor.of(GRAPH.next(l3, ComboInput.LIGHT)));
        assertEquals(BranchFlavor.SMASH, BranchFlavor.of(GRAPH.next(l3, ComboInput.HEAVY)));
    }

    @Test
    void flavoursRideInTheMaskWithoutDisturbingTheInputBits() {
        int plain = ComboInput.LIGHT.bit() | ComboInput.HEAVY.bit() | ComboInput.GRAB.bit() | ComboInput.RUSH.bit();
        for (BranchFlavor light : BranchFlavor.values()) {
            for (BranchFlavor heavy : BranchFlavor.values()) {
                int mask = BranchFlavor.pack(BranchFlavor.pack(plain, ComboInput.LIGHT, light),
                        ComboInput.HEAVY, heavy);
                assertTrue(mask >= 0 && mask <= 0xFF, "the mask travels as one byte");
                assertEquals(light, BranchFlavor.unpack(mask, ComboInput.LIGHT));
                assertEquals(heavy, BranchFlavor.unpack(mask, ComboInput.HEAVY));
                for (ComboInput input : ComboInput.values()) {
                    assertTrue(ComboInput.has(mask, input), input + " lost from " + mask);
                }
            }
        }
        assertFalse(ComboInput.has(BranchFlavor.pack(0, ComboInput.LIGHT, BranchFlavor.SMASH), ComboInput.LIGHT),
                "a flavour must not read as an open input");
        assertEquals(plain, BranchFlavor.pack(plain, ComboInput.GRAB, BranchFlavor.KICK));
        assertEquals(BranchFlavor.PUNCH, BranchFlavor.unpack(0xFF, ComboInput.RUSH));
    }

    // ---- parser ----

    @Test
    void aVariantMayChangeThePoseAloneOrThePoseAndTheReaction() {
        String json = "{\"starts\":{\"heavy\":\"k\"},\"nodes\":{\"k\":{\"intent\":\"MID_KICK_LEFT\","
                + "\"reaction\":\"HIT_HEAVY\",\"charged\":\"KICK_ARC\",\"variants\":{"
                + "\"left\":\"KNEE_LEFT\","
                + "\"forward\":{\"intent\":\"FLYING_KICK\",\"reaction\":\"KICK_UP\"}}}}}";
        ComboNode k = ComboGraphParser.parse(new StringReader(json)).node("k");
        assertEquals(Bt3AnimationIntent.KNEE_LEFT, k.intentFor(V2Direction.LEFT));
        assertEquals(HitReaction.HIT_HEAVY, k.reactionFor(V2Direction.LEFT, false));
        assertEquals(Bt3AnimationIntent.FLYING_KICK, k.intentFor(V2Direction.FORWARD));
        assertEquals(HitReaction.KICK_UP, k.reactionFor(V2Direction.FORWARD, false));
        assertEquals(HitReaction.KICK_ARC, k.reactionFor(V2Direction.NONE, true));
        assertEquals(4, k.cancelTicks(), "a beat with no authored recovery still has one");
    }

    @Test
    void aVariantWithAnUnknownReactionIsAnError() {
        String json = "{\"starts\":{\"heavy\":\"k\"},\"nodes\":{\"k\":{\"intent\":\"MID_KICK_LEFT\","
                + "\"variants\":{\"forward\":{\"intent\":\"FLYING_KICK\",\"reaction\":\"ORBIT\"}}}}}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ComboGraphParser.parse(new StringReader(json)));
        assertTrue(e.getMessage().contains("ORBIT"));
    }

    @Test
    void aGraphPointingAtAMissingNodeIsRefused() {
        String json = "{\"starts\":{\"light\":\"a\"},\"nodes\":{\"a\":{\"intent\":\"JAB_LEFT\","
                + "\"next\":{\"heavy\":\"nope\"}}}}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ComboGraphParser.parse(new StringReader(json)));
        assertTrue(e.getMessage().contains("nope"));
    }

    @Test
    void anUnknownIntentIsAnErrorNamingTheNode() {
        String json = "{\"starts\":{\"light\":\"a\"},\"nodes\":{\"a\":{\"intent\":\"MOONSAULT\"}}}";
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> ComboGraphParser.parse(new StringReader(json)));
        assertTrue(e.getMessage().contains("MOONSAULT"));
    }

    @Test
    void aGraphWithNoStartIsRefused() {
        String json = "{\"nodes\":{\"a\":{\"intent\":\"JAB_LEFT\"}}}";
        assertThrows(IllegalArgumentException.class,
                () -> ComboGraphParser.parse(new StringReader(json)));
    }

    @Test
    void anInputThatStartsNothingIsRejectedInNeutral() {
        assertNull(GRAPH.start(ComboInput.RUSH));
        assertEquals(ComboMachine.Outcome.REJECT,
                ComboMachine.decide(GRAPH, null, 0, false, ComboInput.RUSH).outcome());
    }
}
