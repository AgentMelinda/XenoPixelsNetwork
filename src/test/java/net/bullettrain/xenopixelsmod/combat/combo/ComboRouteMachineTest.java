package net.bullettrain.xenopixelsmod.combat.combo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComboRouteMachineTest {

    @Test
    void rushComboWalksApproachThenHitsThenKnockbackThenOneReapproachThenSecondString() {
        ComboRoute route = ComboRouteCatalog.bySkillId("rushcombo");
        ComboRoutePhase phase = ComboRoutePhase.IDLE;

        ComboRouteDecision start = ComboRouteMachine.decide(route, phase, ComboRouteInput.start(4, 1, true));
        assertEquals(ComboRoutePhase.APPROACH, start.next());
        phase = start.next();

        ComboRouteDecision reached = ComboRouteMachine.decide(route, phase, ComboRouteInput.inRange(4, 1, true, 0, 0));
        assertEquals(ComboRoutePhase.STRING1, reached.next());
        phase = reached.next();

        for (int hit = 0; hit < 3; hit++) {
            ComboRouteDecision swing = ComboRouteMachine.decide(route, phase,
                    ComboRouteInput.inRange(4, 1, true, hit, 0));
            assertTrue(swing.spendHit());
            assertEquals(ComboRoutePhase.STRING1, swing.next());
            assertEquals(hit, swing.hitIndex());
        }
        ComboRouteDecision last = ComboRouteMachine.decide(route, phase,
                ComboRouteInput.inRange(4, 1, true, 3, 0));
        assertTrue(last.spendHit());
        assertEquals(ComboRoutePhase.KNOCKBACK, last.next());
        phase = last.next();

        ComboRouteDecision knock = ComboRouteMachine.decide(route, phase,
                ComboRouteInput.inRange(4, 1, true, 4, 0));
        assertTrue(knock.applyKnockback());
        assertEquals(ComboRoutePhase.REAPPROACH, knock.next());
        phase = knock.next();

        ComboRouteDecision re = ComboRouteMachine.decide(route, phase,
                ComboRouteInput.inRange(4, 1, true, 4, 0));
        assertEquals(ComboRoutePhase.STRING2, re.next());
        phase = re.next();

        ComboRouteDecision secondLast = ComboRouteMachine.decide(route, phase,
                ComboRouteInput.inRange(4, 1, true, 3, 1));
        assertEquals(ComboRoutePhase.RECOVERY, secondLast.next());
        phase = secondLast.next();

        ComboRouteDecision done = ComboRouteMachine.decide(route, phase,
                ComboRouteInput.inRange(4, 1, true, 4, 1));
        assertTrue(done.finish());
        assertEquals(ComboRoutePhase.IDLE, done.next());
    }

    @Test
    void secondReapproachIsRejectedWhenMaxIsOne() {
        ComboRoute route = ComboRouteCatalog.bySkillId("rushcombo");
        ComboRouteDecision knock = ComboRouteMachine.decide(route, ComboRoutePhase.KNOCKBACK,
                ComboRouteInput.inRange(4, 1, true, 4, 1));
        assertEquals(ComboRoutePhase.RECOVERY, knock.next());
        assertFalse(knock.next() == ComboRoutePhase.REAPPROACH);
    }

    @Test
    void targetLossDuringApproachGoesIdleWithoutSecondCost() {
        ComboRoute route = ComboRouteCatalog.bySkillId("rushcombo");
        ComboRouteDecision lost = ComboRouteMachine.decide(route, ComboRoutePhase.APPROACH,
                ComboRouteInput.lost(4, 1, true));
        assertEquals(ComboRoutePhase.IDLE, lost.next());
        assertTrue(lost.finish());
        assertFalse(lost.spendHit());
        assertFalse(lost.applyKnockback());
    }

    @Test
    void hitCountThreeTruncatesAuthoredFourBeatString() {
        ComboRoute route = ComboRouteCatalog.bySkillId("rushcombo");
        ComboRouteDecision lastOfThree = ComboRouteMachine.decide(route, ComboRoutePhase.STRING1,
                ComboRouteInput.inRange(3, 1, true, 2, 0));
        assertTrue(lastOfThree.spendHit());
        assertEquals(ComboRoutePhase.KNOCKBACK, lastOfThree.next());
        assertEquals(2, lastOfThree.hitIndex());
    }

    @Test
    void liftComboKnockbackStartsChaseNotReapproach() {
        ComboRoute route = ComboRouteCatalog.bySkillId("liftcombo");
        ComboRouteDecision knock = ComboRouteMachine.decide(route, ComboRoutePhase.KNOCKBACK,
                ComboRouteInput.inRange(3, 1, false, 3, 0));
        assertTrue(knock.applyKnockback());
        assertTrue(knock.startChase());
        assertEquals(ComboRoutePhase.RECOVERY, knock.next());
    }

    @Test
    void arrivalReadyTreatsOverlapAndExactRangeAsArrived() {
        assertTrue(ComboRouteMachine.arrivalReady(0.0, 2.5));
        assertTrue(ComboRouteMachine.arrivalReady(2.5, 2.5));
        assertFalse(ComboRouteMachine.arrivalReady(2.51, 2.5));
    }
}
