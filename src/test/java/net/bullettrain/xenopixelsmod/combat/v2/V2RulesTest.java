package net.bullettrain.xenopixelsmod.combat.v2;

import net.bullettrain.xenopixelsmod.combat.controller.CombatControllerMode;
import net.bullettrain.xenopixelsmod.combat.controller.LegacyActionPolicy;
import net.bullettrain.xenopixelsmod.combat.v2.grab.GrabRules;
import net.bullettrain.xenopixelsmod.combat.v2.motion.MotionRules;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Gate, mode, reactions, reach, lock-on, grab timing, motion maths, the counter window and config. */
class V2RulesTest {

    // ---- selector ----

    @Test
    void v2IsOptInAndLegacyStaysDefault() {
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.DEFAULT);
        assertEquals(CombatControllerMode.V2, CombatControllerMode.fromId("v2"));
        assertEquals(CombatControllerMode.V2, CombatControllerMode.parseStrict(" V2 "));
        assertEquals(CombatControllerMode.LEGACY, CombatControllerMode.fromId("unsupported_controller"));
        assertNull(CombatControllerMode.parseStrict("unsupported_controller"));
        assertEquals("v2", CombatControllerMode.V2.id());
    }

    @Test
    void switchingToOrFromV2SweepsLiveState() {
        assertTrue(CombatControllerMode.requiresCleanup(CombatControllerMode.LEGACY, CombatControllerMode.V2));
        assertTrue(CombatControllerMode.requiresCleanup(CombatControllerMode.V2, CombatControllerMode.LEGACY));
        assertFalse(CombatControllerMode.requiresCleanup(CombatControllerMode.V2, CombatControllerMode.V2));
    }

    @Test
    void gateAcceptsOnlyV2WithCombatOnPermissionAndAnAbleBody() {
        assertEquals(V2CombatGate.Decision.ACCEPT,
                V2CombatGate.decide(CombatControllerMode.V2, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                V2CombatGate.decide(CombatControllerMode.LEGACY, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                V2CombatGate.decide(CombatControllerMode.BT3_MANUAL, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                V2CombatGate.decide(CombatControllerMode.V3, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                V2CombatGate.decide(null, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_COMBAT_DISABLED,
                V2CombatGate.decide(CombatControllerMode.V2, false, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NO_PERMISSION,
                V2CombatGate.decide(CombatControllerMode.V2, true, false, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_ABLE,
                V2CombatGate.decide(CombatControllerMode.V2, true, true, false));
    }

    // ---- the grab, shared with the legacy and manual controllers ----

    @Test
    void theOtherControllersAreGivenTheGrabAndNothingElse() {
        assertEquals(V2CombatGate.Decision.ACCEPT,
                V2CombatGate.decideGrabOnly(V2Input.GRAB, true, true, true, true));
        for (V2Input input : V2Input.values()) {
            if (input == V2Input.GRAB) continue;
            assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                    V2CombatGate.decideGrabOnly(input, true, true, true, true), input.name());
        }
    }

    @Test
    void aServerCanKeepTheGrabToV2() {
        assertEquals(V2CombatGate.Decision.REJECT_NOT_V2,
                V2CombatGate.decideGrabOnly(V2Input.GRAB, true, false, true, true));
        V2Config.Values defaults = new V2Config.Values();
        assertTrue(defaults.grabEnabled);
        assertTrue(defaults.grabOutsideV2, "the grab is shared unless a server turns that off");
    }

    @Test
    void theSharedGrabStillNeedsCombatOnItsOwnPermissionAndAnAbleBody() {
        assertEquals(V2CombatGate.Decision.REJECT_COMBAT_DISABLED,
                V2CombatGate.decideGrabOnly(V2Input.GRAB, false, true, true, true));
        assertEquals(V2CombatGate.Decision.REJECT_NO_PERMISSION,
                V2CombatGate.decideGrabOnly(V2Input.GRAB, true, true, false, true));
        assertEquals(V2CombatGate.Decision.REJECT_NOT_ABLE,
                V2CombatGate.decideGrabOnly(V2Input.GRAB, true, true, true, false));
    }

    /**
     * A grab can be in progress under any controller, and the legacy packet handler asks this to
     * know that no legacy action may cut across it.
     */
    @Test
    void aGrabAtEitherEndIsCommitted() {
        assertTrue(V2State.GRAB_STARTUP.committed());
        assertTrue(V2State.GRAB_HOLD.committed());
        assertTrue(V2State.GRABBED.committed());
        assertFalse(V2State.NEUTRAL.committed());
    }

    /**
     * Under the legacy and manual controllers the chase after a throw is their dragon homing,
     * opened for exactly the throws that open the v2 chase.
     */
    @Test
    void everyThrowButTheSlamCanBeChased() {
        assertTrue(HitReaction.THROW_FORWARD.opensChase());
        assertTrue(HitReaction.THROW_BACK.opensChase());
        assertTrue(HitReaction.THROW_UP.opensChase());
        assertFalse(HitReaction.THROW_DOWN.opensChase(), "thrown down, the target is at the grabber's feet");
    }

    /**
     * Under the legacy and manual controllers the grab is two keys, and two fingers never land
     * on the same tick: either may come first by a little.
     */
    @Test
    void guardAndPunchAreAGrabInEitherOrder() {
        assertTrue(GrabRules.chord(true, false, true, true, 1), "a punch into a raised guard");
        assertTrue(GrabRules.chord(true, true, true, true, 1), "both on the same tick");
        assertTrue(GrabRules.chord(true, true, true, false, 2), "the guard one tick behind the punch");
        assertTrue(GrabRules.chord(true, true, true, false, GrabRules.CHORD_TICKS));
    }

    /**
     * Holding the punch key keeps a legacy string going, and raising the guard then has always
     * been a block. It still is.
     */
    @Test
    void aGuardRaisedIntoAHeldPunchIsOnlyAGuard() {
        assertFalse(GrabRules.chord(true, true, true, false, GrabRules.CHORD_TICKS + 1));
        assertFalse(GrabRules.chord(true, true, true, false, 40));
        assertFalse(GrabRules.chord(true, false, true, false, 2), "both held and nothing new is not a press");
        assertFalse(GrabRules.chord(false, false, true, true, 1), "a punch with no guard is a punch");
        assertFalse(GrabRules.chord(true, true, false, false, 0), "a guard with no punch is a guard");
    }

    /**
     * A grab is seen, not only felt: the grabber reaches in, and as the victim leaves their hands
     * plays a strike that sends a target the same way. Every direction has one, and none of them
     * turns the body by itself, which would leave the head pointing somewhere else.
     */
    @Test
    void aGrabAndEveryThrowHaveAPose() {
        assertEquals(net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent.STEP_IN_DASH, V2Grab.REACH_POSE);
        for (V2Direction direction : V2Direction.values()) {
            net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent pose = V2Grab.throwPose(direction);
            assertFalse(pose.isBodyYawPose(), direction.name());
            assertFalse(pose.isTravelPose(), direction.name());
        }
        assertEquals(V2Grab.throwPose(V2Direction.NONE), V2Grab.throwPose(null));
        assertEquals(V2Grab.throwPose(V2Direction.NONE), V2Grab.throwPose(V2Direction.LEFT),
                "the side keys throw forward, so they look like a throw forward");
        assertFalse(V2Grab.throwPose(V2Direction.UP) == V2Grab.throwPose(V2Direction.NONE));
        assertFalse(V2Grab.throwPose(V2Direction.BACK) == V2Grab.throwPose(V2Direction.NONE));
        assertFalse(V2Grab.throwPose(V2Direction.DOWN) == V2Grab.throwPose(V2Direction.NONE));
    }

    /**
     * While a grab has a fighter their client reads no other combat key, so a grab whose end was
     * never reported must not be able to hold those keys for good.
     */
    @Test
    void aGrabTheServerNeverEndedIsGivenUpOn() {
        assertFalse(GrabRules.outstays(true, GrabRules.MAX_STATE_TICKS));
        assertTrue(GrabRules.outstays(true, GrabRules.MAX_STATE_TICKS + 1));
        assertFalse(GrabRules.outstays(false, 100_000), "only a grab is ever given up on");
    }

    /** The give-up time is tied to the longest grab a server can configure, with room to spare. */
    @Test
    void noConfiguredGrabCanOutlastTheGiveUpTime() {
        V2Config.Values longest = new V2Config.Values();
        longest.grabStartupTicks = Integer.MAX_VALUE;
        longest.grabHoldTicks = Integer.MAX_VALUE;
        longest.clamped();
        int longestState = Math.max(longest.grabStartupTicks, longest.grabHoldTicks);
        assertTrue(GrabRules.MAX_STATE_TICKS >= 2 * longestState,
                "a legitimate hold of " + longestState + " ticks would be cut short");
    }

    @Test
    void v2RefusesTheLegacyMovesItReplacesAndKeepsTheRest() {
        Bt3CombatPacket.Action[] replaced = {
                Bt3CombatPacket.Action.COMBO_HIT, Bt3CombatPacket.Action.CINEMATIC_RUSH,
                Bt3CombatPacket.Action.CHARGE_FIST, Bt3CombatPacket.Action.CHARGE_KICK,
                Bt3CombatPacket.Action.DRAGON_DASH, Bt3CombatPacket.Action.CHASE_DASH,
                Bt3CombatPacket.Action.CHASE_STOP, Bt3CombatPacket.Action.Z_BURST,
                Bt3CombatPacket.Action.RUSH_CHAIN, Bt3CombatPacket.Action.SONIC_SWAY,
                Bt3CombatPacket.Action.SUPER_COUNTER};
        for (Bt3CombatPacket.Action action : replaced) {
            assertFalse(LegacyActionPolicy.allowed(action, CombatControllerMode.V2), action.name());
        }
        Bt3CombatPacket.Action[] shared = {
                Bt3CombatPacket.Action.GUARD, Bt3CombatPacket.Action.VANISH,
                Bt3CombatPacket.Action.BACKSTEP, Bt3CombatPacket.Action.KI_BLAST_CANCEL,
                Bt3CombatPacket.Action.ULTIMATE, Bt3CombatPacket.Action.SPARKING,
                Bt3CombatPacket.Action.HAKAI_START, Bt3CombatPacket.Action.HAKAI_CANCEL,
                Bt3CombatPacket.Action.ZANZOKEN, Bt3CombatPacket.Action.MULTIFORM,
                Bt3CombatPacket.Action.RUSH_COMBO, Bt3CombatPacket.Action.LIFT_COMBO};
        for (Bt3CombatPacket.Action action : shared) {
            assertTrue(LegacyActionPolicy.allowed(action, CombatControllerMode.V2), action.name());
        }
    }

    @Test
    void unknownWireOrdinalsAreNotTrusted() {
        assertNull(V2Input.byOrdinal(-1));
        assertNull(V2Input.byOrdinal(V2Input.values().length));
        assertEquals(V2Direction.NONE, V2Direction.byOrdinal(99));
        assertEquals(V2State.NEUTRAL, V2State.byOrdinal(-3));
    }

    @Test
    void retiredStepCannotBeDecodedAndLaterOrdinalsStayStable() {
        assertNull(V2Input.byOrdinal(5));
        assertEquals(V2State.NEUTRAL, V2State.byOrdinal(2));
        assertEquals(V2Input.CHASE, V2Input.byOrdinal(6));
        assertEquals(V2Input.VANISH, V2Input.byOrdinal(11));
        assertEquals(V2State.TRAVEL, V2State.byOrdinal(3));
    }

    @Test
    void oldStepSettingsAreIgnoredAndRemovedOnSerialization() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        V2Config.Values cfg = gson.fromJson(
                "{\"version\":2,\"stepEnabled\":true,\"stepDistance\":8,\"vanishCooldownTicks\":31}",
                V2Config.Values.class);
        assertEquals(31, cfg.vanishCooldownTicks);
        assertFalse(gson.toJson(cfg).contains("step"));
    }

    @Test
    void aFighterInsideADragonMineZStrikeIsCommitted() {
        assertTrue(V2State.STRIKE.committed());
        assertTrue(V2State.RUSH.committed());
        assertTrue(V2State.GRABBED.committed());
        assertFalse(V2State.ATTACK.committed());
        assertFalse(V2State.TRAVEL.committed());
    }

    // ---- reactions ----

    @Test
    void reactionPushesAwayFromTheAttacker() {
        double[] v = HitReaction.KNOCKBACK_LONG.impulse(3.0, 0.0, 1.0);
        assertTrue(v[0] > 1.0);
        assertEquals(0.0, v[2], 1.0e-9);
        assertTrue(v[1] > 0.0);
    }

    @Test
    void reactionScaleOnlyTouchesTheHorizontalPart() {
        double[] one = HitReaction.LAUNCH_UP.impulse(0.0, 2.0, 1.0);
        double[] two = HitReaction.LAUNCH_UP.impulse(0.0, 2.0, 2.0);
        assertEquals(one[1], two[1], 1.0e-9);
        assertEquals(one[2] * 2.0, two[2], 1.0e-9);
    }

    @Test
    void aStackedVictimStillGetsAVector() {
        double[] v = HitReaction.KNOCKBACK_SHORT.impulse(0.0, 0.0, 1.0);
        assertTrue(Math.hypot(v[0], v[2]) > 0.5);
    }

    @Test
    void aLightHitDoesNotSlideTheVictimOutOfReach() {
        assertFalse(HitReaction.HIT_LIGHT.moves());
        assertFalse(HitReaction.HIT_LIGHT.opensChase());
    }

    @Test
    void kicksAreTheLegacyKickTrajectories() {
        assertEquals(0, HitReaction.KICK_ARC.kick().verticalBias());
        assertEquals(1, HitReaction.KICK_UP.kick().verticalBias());
        assertEquals(-1, HitReaction.KICK_DOWN.kick().verticalBias());
        for (HitReaction reaction : HitReaction.values()) {
            assertEquals(reaction.name().startsWith("KICK_"), reaction.isKick(), reaction.name());
            if (reaction.isKick()) assertTrue(reaction.replacesVelocity(), reaction.name());
        }
    }

    @Test
    void aKickThatSendsTheTargetAwayOrUpCanBeChased() {
        assertTrue(HitReaction.KICK_ARC.opensChase());
        assertTrue(HitReaction.KICK_UP.opensChase());
        assertFalse(HitReaction.KICK_DOWN.opensChase(), "a slam leaves the target at the attacker's feet");
    }

    @Test
    void aFullChargeHitsOneTierHarderAndLeavesKicksAlone() {
        assertEquals(HitReaction.HIT_HEAVY, HitReaction.HIT_LIGHT.charged());
        assertEquals(HitReaction.KNOCKBACK_SHORT, HitReaction.HIT_HEAVY.charged());
        assertEquals(HitReaction.KNOCKBACK_LONG, HitReaction.KNOCKBACK_SHORT.charged());
        assertEquals(HitReaction.KNOCKBACK_LONG, HitReaction.KNOCKBACK_LONG.charged());
        assertEquals(HitReaction.KICK_ARC, HitReaction.KICK_ARC.charged());
        assertEquals(HitReaction.LAUNCH_UP, HitReaction.LAUNCH_UP.charged());
    }

    // ---- reach ----

    @Test
    void reachIsMeasuredToTheNearestPointOfTheHitbox() {
        // Inside the box.
        assertEquals(0.0, ReachRules.distanceToBox(0.5, 1.0, 0.5, 0, 0, 0, 1, 2, 1), 1.0e-9);
        // Two blocks in front of a face.
        assertEquals(2.0, ReachRules.distanceToBox(3.0, 1.0, 0.5, 0, 0, 0, 1, 2, 1), 1.0e-9);
        // Eyes level with the chest of a tall target: its height adds nothing to the distance.
        assertEquals(1.0, ReachRules.distanceToBox(-1.0, 1.6, 0.5, 0, 0, 0, 1, 6, 1), 1.0e-9);
        // Past a corner: the diagonal to it.
        assertEquals(5.0, ReachRules.distanceToBox(4.0, 0.0, 5.0, 0, 0, 0, 1, 2, 1), 1.0e-9);
    }

    @Test
    void facingIsJudgedOnTheFlat() {
        assertEquals(1.0, ReachRules.facingDot(0, 1, 0, 5), 1.0e-9);
        assertEquals(-1.0, ReachRules.facingDot(0, 1, 0, -5), 1.0e-9);
        assertEquals(0.0, ReachRules.facingDot(0, 1, 3, 0), 1.0e-9);
        // A target straight overhead, or a fighter looking straight down, is not "behind".
        assertEquals(1.0, ReachRules.facingDot(0, 1, 0, 0), 1.0e-9);
        assertEquals(1.0, ReachRules.facingDot(0, 0, 4, 4), 1.0e-9);
    }

    @Test
    void aStrikeNeedsBothReachAndFacing() {
        assertTrue(ReachRules.reachable(3.0, 3.75, 0.8, 0.0));
        assertFalse(ReachRules.reachable(4.0, 3.75, 0.8, 0.0));
        assertFalse(ReachRules.reachable(3.0, 3.75, -0.2, 0.0));
        assertTrue(ReachRules.reachable(3.0, 3.75, -0.9, -1.0), "-1 asks for no facing at all");
        assertFalse(ReachRules.reachable(0.5, -1.0, 1.0, 0.0), "a negative range reaches nothing");
    }

    // ---- lock-on ----

    /** v2 is a lock-on system, and DragonMineZ's lock-on is the Ki Sense skill. */
    @Test
    void thereIsNoLockWithoutKiSense() {
        assertFalse(LockRules.canLock(0));
        assertFalse(LockRules.canLock(-1));
        assertTrue(LockRules.canLock(1));
    }

    @Test
    void lockRangeGrowsWithKiSenseAsDragonMineZsDoes() {
        assertEquals(133.0, LockRules.range(128.0, 1, false), 1.0e-9);
        assertEquals(178.0, LockRules.range(128.0, 10, false), 1.0e-9);
        assertEquals(158.0, LockRules.range(128.0, 1, true), 1.0e-9, "an upgraded android reaches 25 further");
        assertEquals(128.0, LockRules.range(128.0, -3, false), 1.0e-9, "a negative level adds nothing");
    }

    @Test
    void aTargetJustPastTheEdgeIsStillTheLock() {
        double range = LockRules.range(128.0, 1, false);
        assertTrue(LockRules.inRange(10.0, range));
        assertTrue(LockRules.inRange(range, range));
        assertTrue(LockRules.inRange(range + LockRules.SLACK, range),
                "the two sides see positions a few ticks apart");
        assertFalse(LockRules.inRange(range + LockRules.SLACK + 0.5, range));
    }

    /** DragonMineZ's own figures, read from the tracked decompile so these cannot drift from it. */
    @Test
    void theLockRulesAreDragonMineZsOwn() throws Exception {
        String lockOn = java.nio.file.Files.readString(net.bullettrain.xenopixelsmod.RepoRoot.of(
                "tools/generated/dmz_decompiled_full", "com/dragonminez/client/events/LockOnEvent.java"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(lockOn.contains("15.0 + 5.0 * (double)level"), "range per Ki Sense level is no longer 5");
        assertTrue(lockOn.contains("range += 25.0;"), "the android bonus is no longer 25");
        assertEquals(5.0, LockRules.RANGE_PER_LEVEL, 1.0e-9);
        assertEquals(25.0, LockRules.ANDROID_BONUS, 1.0e-9);
    }

    @Test
    void throwFollowsTheHeldDirection() {
        assertEquals(HitReaction.THROW_FORWARD, HitReaction.throwFor(V2Direction.NONE));
        assertEquals(HitReaction.THROW_FORWARD, HitReaction.throwFor(V2Direction.LEFT));
        assertEquals(HitReaction.THROW_BACK, HitReaction.throwFor(V2Direction.BACK));
        assertEquals(HitReaction.THROW_UP, HitReaction.throwFor(V2Direction.UP));
        assertEquals(HitReaction.THROW_DOWN, HitReaction.throwFor(V2Direction.DOWN));
        assertEquals(HitReaction.THROW_FORWARD, HitReaction.throwFor(null));
        // Back throws behind the grabber: the sign of the away component flips.
        assertTrue(HitReaction.THROW_BACK.impulse(1.0, 0.0, 1.0)[0] < 0.0);
    }

    // ---- grab ----

    @Test
    void grabWalksStartupConnectHoldThrow() {
        assertEquals(GrabRules.Phase.STARTUP, GrabRules.phase(0, 6, 14));
        assertEquals(GrabRules.Phase.STARTUP, GrabRules.phase(5, 6, 14));
        assertEquals(GrabRules.Phase.CONNECT, GrabRules.phase(6, 6, 14));
        assertEquals(GrabRules.Phase.HOLD, GrabRules.phase(7, 6, 14));
        assertEquals(GrabRules.Phase.HOLD, GrabRules.phase(19, 6, 14));
        assertEquals(GrabRules.Phase.THROW, GrabRules.phase(20, 6, 14));
    }

    @Test
    void grabNeedsReachAndFacing() {
        assertTrue(GrabRules.inReach(2.0, 2.6, 0.9, 0.5));
        assertFalse(GrabRules.inReach(3.0, 2.6, 0.9, 0.5));
        assertFalse(GrabRules.inReach(2.0, 2.6, 0.1, 0.5));
    }

    @Test
    void aHitOnlyBreaksAGrabDuringStartup() {
        assertTrue(GrabRules.interruptedByHit(GrabRules.Phase.STARTUP, 1.0f));
        assertFalse(GrabRules.interruptedByHit(GrabRules.Phase.STARTUP, 0.0f));
        assertFalse(GrabRules.interruptedByHit(GrabRules.Phase.HOLD, 50.0f));
    }

    @Test
    void onlyAPlayerInsideTheWindowCanTech() {
        assertTrue(GrabRules.techs(3, 6, true));
        assertFalse(GrabRules.techs(7, 6, true));
        assertFalse(GrabRules.techs(3, 6, false));
        assertFalse(GrabRules.techs(0, 0, true));
    }

    // ---- motion ----

    @Test
    void travelNeverOvershootsTheGoal() {
        double[] far = MotionRules.stepToward(10.0, 0.0, 0.0, 1.5);
        assertEquals(1.5, far[0], 1.0e-9);
        double[] near = MotionRules.stepToward(0.4, 0.0, 0.0, 1.5);
        assertEquals(0.4, near[0], 1.0e-9);
        double[] there = MotionRules.stepToward(0.0, 0.0, 0.0, 1.5);
        assertEquals(0.0, there[0] + there[1] + there[2], 1.0e-9);
    }

    @Test
    void travelTimeoutIsBounded() {
        assertEquals(40, MotionRules.timeoutTicks(2.0, 1.0, 40));
        assertEquals(120, MotionRules.timeoutTicks(100.0, 1.0, 40));
        assertEquals(MotionRules.ABSOLUTE_MAX_TICKS, MotionRules.timeoutTicks(1.0e6, 1.0, 40));
        assertEquals(40, MotionRules.timeoutTicks(50.0, 0.0, 40));
    }

    @Test
    void directionalBlinkFollowsTheFightersFacing() {
        // Yaw 0 looks along +Z in Minecraft.
        double[] forward = MotionRules.stepOffset(0f, 1, 0, 2.0);
        assertEquals(0.0, forward[0], 1.0e-9);
        assertEquals(2.0, forward[1], 1.0e-9);
        double[] back = MotionRules.stepOffset(0f, -1, 0, 2.0);
        assertEquals(-2.0, back[1], 1.0e-9);
        // Facing +Z, the right hand is -X.
        double[] right = MotionRules.stepOffset(0f, 0, 1, 2.0);
        assertEquals(-2.0, right[0], 1.0e-9);
        double[] diagonal = MotionRules.stepOffset(0f, 1, 1, 2.0);
        assertEquals(2.0, Math.hypot(diagonal[0], diagonal[1]), 1.0e-9);
        double[] none = MotionRules.stepOffset(0f, 0, 0, 2.0);
        assertEquals(0.0, none[0] + none[1], 1.0e-9);
    }

    // ---- counter ----

    @Test
    void counterWindowOpensOnRealDamageOutsideTheLockout() {
        assertTrue(CounterRules.opens(2.0f, true, 100, 90));
        assertFalse(CounterRules.opens(2.0f, false, 100, 90));
        assertFalse(CounterRules.opens(0.0f, true, 100, 90));
        assertFalse(CounterRules.opens(2.0f, true, 100, 120));
        assertTrue(CounterRules.live(105, 110));
        assertFalse(CounterRules.live(111, 110));
        assertFalse(CounterRules.live(5, 0));
    }

    // ---- config ----

    @Test
    void configClampsHostileValues() {
        try {
            V2Config.Values hostile = new V2Config.Values();
            hostile.strikeRange = 9999;
            hostile.chaseSpeed = Double.NaN;
            hostile.grabStartupTicks = -5;
            hostile.inputBufferTicks = 500;
            hostile.vanishKiCost = -3f;
            hostile.strikeFacingDot = 7.0;
            V2Config.use(hostile);
            V2Config.Values cfg = V2Config.get();
            assertEquals(12.0, cfg.strikeRange, 1.0e-9);
            assertEquals(0.1, cfg.chaseSpeed, 1.0e-9);
            assertEquals(1, cfg.grabStartupTicks);
            assertEquals(20, cfg.inputBufferTicks);
            assertEquals(0f, cfg.vanishKiCost);
            assertEquals(1.0, cfg.strikeFacingDot, 1.0e-9);
        } finally {
            V2Config.use(null);
        }
        assertEquals(new V2Config.Values().strikeRange, V2Config.get().strikeRange, 1.0e-9);
    }

    /**
     * DragonMineZ charges the attacker stamina per melee hit and empties the pool when it runs
     * short. v2 strikes leave that off unless a server turns it on.
     */
    @Test
    void strikesDoNotDrainStaminaUnlessAskedTo() {
        assertFalse(new V2Config.Values().strikesDrainStamina);
    }

    @Test
    void vanishOutrangesTheLegacyOneAndCoversTheBlowAlreadyInFlight() {
        V2Config.Values cfg = new V2Config.Values();
        assertTrue(cfg.vanishRange >= 12.0);
        assertTrue(cfg.vanishIFrameTicks >= 8);
        assertTrue(cfg.counterRange > 0.0 && cfg.counterRange < cfg.chaseMaxRange);
    }

    /**
     * A file from the first v2 build carries damage scales that meant "share of a bonus on top of
     * a full hit". Read as "multiple of melee damage" they are wrong, so they go back to defaults.
     */
    @Test
    void aConfigFromBeforeTheDamageRebaseHasItsScalesReset() {
        V2Config.Values old = new V2Config.Values();
        old.version = 0;
        old.rushDamageScale = 0.2f;
        old.grabDamageScale = 9f;
        old.strikeRange = 4.5;
        old.chaseSpeed = 2.4;
        old.rebase();
        V2Config.Values fresh = new V2Config.Values();
        assertEquals(V2Config.VERSION, old.version);
        assertEquals(fresh.rushDamageScale, old.rushDamageScale);
        assertEquals(fresh.grabDamageScale, old.grabDamageScale);
        assertEquals(fresh.strikeRange, old.strikeRange, 1.0e-9);
        assertEquals(2.4, old.chaseSpeed, 1.0e-9, "settings whose meaning did not change are kept");
    }
}
