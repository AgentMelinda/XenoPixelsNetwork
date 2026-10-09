package net.bullettrain.xenopixelsmod.combat.v3;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import java.util.UUID;

class V3ChargeCadenceTest {
    @Test void damageCallbackSessionRotationCannotFinishAnOldCharge() {
        V3Fighter fighter = new V3Fighter(UUID.randomUUID());
        UUID session = fighter.session();
        assertTrue(fighter.admit(session, 9));
        assertTrue(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        fighter.rotateSession();
        assertFalse(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        assertFalse(V3Charge.ownsReleasedStrike(fighter, new V3Fighter(fighter.playerId), session, 9));
        assertFalse(V3Charge.ownsReleasedStrike(fighter, null, session, 9));
    }

    @Test void damageCallbackStartingANewerGestureOrMotionCannotFinishAnOldCharge() {
        V3Fighter fighter = new V3Fighter(UUID.randomUUID());
        UUID session = fighter.session();
        assertTrue(fighter.admit(session, 9));
        fighter.state = V3State.CHARGING_KICK;
        assertFalse(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        fighter.state = V3State.IDLE;
        fighter.chargeStartTick = 120;
        assertFalse(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        fighter.chargeStartTick = -1;
        fighter.motion.acquire(V3Motion.Owner.CINEMATIC, UUID.randomUUID(), false);
        assertFalse(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        fighter.motion.release();
        assertTrue(fighter.admit(session, 10));
        assertFalse(V3Charge.ownsReleasedStrike(fighter, fighter, session, 9));
        assertTrue(V3Charge.ownsReleasedStrike(fighter, fighter, session, 10));
    }

    @Test void fullChargeCueCrossesOnceEvenWhenTheClockSkipsATick() {
        assertFalse(V3Charge.becameFullyCharged(0, 19));
        assertTrue(V3Charge.becameFullyCharged(19, 20));
        assertTrue(V3Charge.becameFullyCharged(18, 21));
        assertFalse(V3Charge.becameFullyCharged(20, 20));
        assertFalse(V3Charge.becameFullyCharged(20, 21));
        assertFalse(V3Charge.becameFullyCharged(20, 0));
        assertTrue(V3Charge.becameFullyCharged(0, 20));
    }

    private static V3Charge.Cadence full(int saved, double distance) {
        return V3Charge.cadence(saved, 1f, distance, true, true);
    }

    @Test void releasesOneToThreeDoNotTeleportAndTheFourthDoes() {
        int saved = 0;
        for (int release = 1; release <= 3; release++) {
            var step = full(saved, 12);
            assertFalse(step.teleport(), "release " + release);
            assertEquals(release, step.count());
            saved = step.count();
        }
        var fourth = full(saved, 12);
        assertTrue(fourth.teleport());
        assertEquals(0, fourth.count());
    }

    @Test void punchAndKickCountersAreSeparateSavedKeysSharedWithV2() {
        assertEquals("xenopixelsmod.v2_charged_punch_count", V3Charge.countKey(false));
        assertEquals("xenopixelsmod.v2_charged_kick_count", V3Charge.countKey(true));
    }

    @Test void sevenAndTwentyBlocksCountButJustOutsideDoesNot() {
        assertEquals(1, full(0, 7).count());
        assertEquals(1, full(0, 20).count());
        assertEquals(0, full(0, 6.99).count());
        assertEquals(0, full(0, 20.01).count());
        assertEquals(0, full(0, Double.NaN).count());
        assertFalse(full(3, 6.99).teleport());
        assertEquals(3, full(3, 20.01).count());
    }

    @Test void partialBlockedSightAndRefusedLandingDoNotConsumeTheCount() {
        assertEquals(3, V3Charge.cadence(3, 0.95f, 12, true, true).count());
        assertFalse(V3Charge.cadence(3, 0.95f, 12, true, true).teleport());
        assertEquals(2, V3Charge.cadence(2, 1f, 12, false, true).count());
        // The fourth release with nowhere to land keeps its turn for the next attempt.
        var refused = V3Charge.cadence(3, 1f, 12, true, false);
        assertFalse(refused.teleport());
        assertEquals(3, refused.count());
        assertTrue(V3Charge.cadence(refused.count(), 1f, 12, true, true).teleport());
    }

    @Test void savedCadenceFromAnotherModeCarriesOnAndBadValuesAreNormalised() {
        // Three eligible v2 releases already saved: the first v3 one is the teleport.
        assertTrue(full(3, 10).teleport());
        assertEquals(1, full(4, 10).count());
        assertEquals(0, full(-1, 10).count());
        assertTrue(full(-1, 10).teleport());
    }

    @Test void serverProgressFillsOverTwentyTicksAndReleaseDamageGrowsWithIt() {
        assertEquals(0f, V3Charge.progress(100, 100));
        assertEquals(0.5f, V3Charge.progress(100, 110));
        assertEquals(1f, V3Charge.progress(100, 120));
        assertEquals(1f, V3Charge.progress(100, 300));
        assertEquals(0f, V3Charge.progress(100, 90));
        assertEquals(1.0f, V3Charge.damageScale(false, 0f), 1e-6);
        assertEquals(1.9f, V3Charge.damageScale(false, 1f), 1e-6);
        assertEquals(1.8f, V3Charge.damageScale(true, 1f), 1e-6);
        assertTrue(V3Charge.damageScale(true, 0.5f) > 1.0f && V3Charge.damageScale(true, 0.5f) < 1.8f);
    }

    @Test void serverChargeIdentityRejectsZeroTickReleaseAndRecoveryBypass() {
        assertFalse(V3Charge.startAllowed(99, 100, -1));
        assertTrue(V3Charge.startAllowed(100, 100, -1));
        assertFalse(V3Charge.startAllowed(101, 100, 90));
        assertFalse(V3Charge.releaseAllowed(100, 100, 4, 5));
        assertFalse(V3Charge.releaseAllowed(100, 101, -1, 5));
        assertFalse(V3Charge.releaseAllowed(100, 101, 5, 5));
        assertTrue(V3Charge.releaseAllowed(100, 101, 4, 5));
    }
}
