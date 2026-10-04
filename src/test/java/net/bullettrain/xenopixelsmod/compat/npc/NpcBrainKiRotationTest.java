package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBrainKiRotationTest {
    @Test
    void genericIdsClassifyWithoutTheRegistry() {
        assertEquals(NpcBrainKiRotation.Band.BLAST, NpcBrainKiRotation.bandOf("kiblast"));
        assertEquals(NpcBrainKiRotation.Band.WAVE, NpcBrainKiRotation.bandOf("kiwave"));
        assertEquals(NpcBrainKiRotation.Band.DISK, NpcBrainKiRotation.bandOf("kienzan"));
    }

    @Test
    void pickRotatesReadyTechniquesAndFallsBackToGenerics() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.techniques.add("kiblast");
        profile.techniques.add("kiwave");
        profile.techniques.add("kienzan");
        assertEquals("kiblast", NpcBrainKiRotation.pick(profile, 0, id -> true));
        assertEquals("kiwave", NpcBrainKiRotation.pick(profile, 1, id -> true));
        assertEquals("kienzan", NpcBrainKiRotation.pick(profile, 2, id -> true));
        assertEquals("kiblast", NpcBrainKiRotation.pick(profile, 3, id -> true));

        profile.brainKiBlast = false;
        profile.brainKiWave = false;
        profile.brainKiDisk = false;
        profile.brainKiNamed = false;
        profile.techniques.clear();
        assertNull(NpcBrainKiRotation.pick(profile, 0, id -> true));

        profile.brainKiWave = true;
        assertEquals("kiwave", NpcBrainKiRotation.pick(profile, 0, id -> true));
    }

    @Test
    void skippedUnreadiedIdsDoNotStarveTheNextType() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.techniques.addAll(List.of("kiblast", "kiwave"));
        assertEquals("kiwave", NpcBrainKiRotation.pick(profile, 0, id -> !"kiblast".equals(id)));
    }

    @Test
    void specialsAreSpiceNotTheDefaultCloseRangeAction() {
        assertFalse(NpcBrainKiRotation.useSpecialThisTick(0, 0));
        assertTrue(NpcBrainKiRotation.useSpecialThisTick(2, 0));
        assertFalse(NpcBrainKiRotation.useSpecialThisTick(2, 1));
        assertFalse(NpcBrainKiRotation.useSpecialThisTick(2, 3));
    }

    @Test
    void flyWhenAirborneAndLandWhenTargetOnGround() {
        assertFalse(NpcBrainKiRotation.shouldFly(false, false, true));
        assertTrue(NpcBrainKiRotation.shouldFly(true, false, false));
        assertFalse(NpcBrainKiRotation.shouldFly(true, true, false));
        assertTrue(NpcBrainKiRotation.shouldFly(true, true, true));
    }

    @Test
    void disengageClearsComboMaps() {
        UUID id = UUID.randomUUID();
        NpcCombatBrain.COMBOS.put(id, new NpcCombatBrain.Combo(id, 3, 10, 1));
        NpcCombatBrain.clearTransient(id);
        assertFalse(NpcCombatBrain.COMBOS.containsKey(id));
    }

    @Test
    void chargeOffAndZeroChanceCannotStartACharge() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertTrue(NpcCombatBrain.mayStartCharge(profile));
        profile.brainCharge = false;
        assertFalse(NpcCombatBrain.mayStartCharge(profile));
        profile.brainCharge = true;
        profile.setBrainChance("charge", 0);
        assertFalse(NpcCombatBrain.mayStartCharge(profile));
    }

    @Test
    void flyingFistOffAndZeroChanceStayOffTheSpecialList() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFlyingFist = true;
        profile.setBrainChance("flyingFist", 0);
        assertFalse(NpcCombatBrain.mayStartFlyingFist(profile));
        assertTrue(NpcCombatBrain.readySpecials(profile, 2.0).isEmpty());
        profile.setBrainChance("flyingFist", 100);
        assertTrue(NpcCombatBrain.mayStartFlyingFist(profile));
        profile.brainFlyingFist = false;
        assertFalse(NpcCombatBrain.mayStartFlyingFist(profile));
        assertTrue(NpcCombatBrain.readySpecials(profile, 2.0).isEmpty());
    }

    @Test
    void meleeBandSpecialsStayOffTheListUntilTheirFlagsAreOn() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertTrue(NpcCombatBrain.readySpecials(profile, 2.0).isEmpty());
        profile.brainFlyingFist = true;
        profile.brainKiai = true;
        List<NpcCombatBrain.Special> ready = NpcCombatBrain.readySpecials(profile, 2.0);
        assertEquals(2, ready.size());
        assertTrue(ready.contains(NpcCombatBrain.Special.FLYING_FIST));
        assertTrue(ready.contains(NpcCombatBrain.Special.KIAI));
    }

    @Test
    void boneCrusherImpulseIsDownwardAndDoesNotEncodeATeleport() {
        var impulse = NpcCombatMoves.boneCrusherImpulse();
        assertEquals(0.0, impulse.x, 1.0e-9);
        assertEquals(0.0, impulse.z, 1.0e-9);
        assertTrue(impulse.y < 0.0);
    }

    @Test
    void lastAttackerPreferenceRequiresAliveInRangeFreshMemory() {
        assertTrue(NpcTargetKeeper.preferLastAttacker(true, true, true));
        assertFalse(NpcTargetKeeper.preferLastAttacker(false, true, true));
        assertFalse(NpcTargetKeeper.preferLastAttacker(true, false, true));
        assertFalse(NpcTargetKeeper.preferLastAttacker(true, true, false));
    }

    @Test
    void persistentRetaliationLockRequiresNativeNpcRetaliationAndEnabledKiSense() {
        assertFalse(NpcTargetKeeper.shouldUseNativeKiSenseLock(false, true, true, true));
        assertFalse(NpcTargetKeeper.shouldUseNativeKiSenseLock(true, false, true, true));
        assertFalse(NpcTargetKeeper.shouldUseNativeKiSenseLock(true, true, false, true));
        assertFalse(NpcTargetKeeper.shouldUseNativeKiSenseLock(true, true, true, false));
        assertTrue(NpcTargetKeeper.shouldUseNativeKiSenseLock(true, true, true, true));
    }

    @Test
    void kiSensePoseTakesShortestTurnAcrossYawBoundary() {
        assertEquals(180.3f, NpcKiAim.smoothAngle(180.0f, -178.0f), 0.01f);
        assertEquals(-180.3f, NpcKiAim.smoothAngle(-180.0f, 178.0f), 0.01f);
        assertEquals(-15.0f, NpcKiAim.smoothAngle(0.0f, -100.0f), 0.01f);
    }

    @Test
    void zanzokenAfterimagesAreDecoysMultiFormCopiesAreNot() {
        assertTrue(NpcTargetKeeper.isZanzokenDecoy(true,
                net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.SLOT_STATIONARY));
        assertFalse(NpcTargetKeeper.isZanzokenDecoy(true, 0));
        assertFalse(NpcTargetKeeper.isZanzokenDecoy(true,
                net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.SLOT_TRAINING));
        assertFalse(NpcTargetKeeper.isZanzokenDecoy(false,
                net.bullettrain.xenopixelsmod.combat.clone.XenoCloneEntity.SLOT_STATIONARY));
    }

    @Test
    void climbAndStandoffSuppressFightAndKi() {
        assertTrue(NpcBrainKiRotation.needsClimb(3.0));
        assertFalse(NpcBrainKiRotation.needsClimb(1.0));
        assertTrue(NpcBrainKiRotation.stackedForAim(1.0, 10.0));
        assertFalse(NpcBrainKiRotation.stackedForAim(6.0, 10.0));
        assertFalse(NpcBrainKiRotation.canFight(1.0, 10.0));
        assertTrue(NpcBrainKiRotation.canFight(5.0, 1.0));
        assertTrue(NpcBrainKiRotation.suppressTeleports(true, true, false, false));
        assertTrue(NpcBrainKiRotation.suppressTeleports(false, false, true, false));
        assertFalse(NpcBrainKiRotation.mayFireKi(true, false));
        assertFalse(NpcBrainKiRotation.mayFireKi(false, true));
        assertTrue(NpcBrainKiRotation.mayFireKi(false, false));
        double[] away = NpcBrainKiRotation.standoffVelocity(0, 0, 0, 0);
        assertEquals(NpcBrainKiRotation.FLY_SPEED, Math.hypot(away[0], away[1]), 1.0e-6);
        double[] climb = NpcBrainKiRotation.climbVelocity(4.0, 8.0, 0.0);
        double climbSpeed = Math.sqrt(climb[0] * climb[0] + climb[1] * climb[1] + climb[2] * climb[2]);
        assertEquals(NpcBrainKiRotation.FLY_SPEED, climbSpeed, 1.0e-6);
        assertEquals(0.35, NpcBrainKiRotation.FLY_SPEED, 1.0e-9);
        assertTrue(NpcBrainKiRotation.shouldFlyV3(true, 4.0));
        assertFalse(NpcBrainKiRotation.shouldFlyV3(true, -5.0),
                "a target below the NPC does not trigger takeoff");
        assertFalse(NpcBrainKiRotation.shouldFlyV3(true, 3.9));
        assertTrue(NpcBrainKiRotation.shouldFlyV3(true, 3.9, true),
                "an active flight chase must not flap off at the takeoff threshold");
        assertTrue(NpcBrainKiRotation.shouldFlyV3(true, 1.9, true),
                "the NPC must stay airborne after reaching the target's height");
        assertTrue(NpcBrainKiRotation.shouldFlyV3(true, -3.0, true),
                "an airborne target below the NPC must not cause another takeoff cycle");
        assertFalse(NpcBrainKiRotation.shouldFlyV3(false, 10.0));
        assertFalse(NpcBrainKiRotation.shouldFlyV3(true, 8.0, false, true),
                "a grounded retaliator emulates the NPC pressing DMZ's flight toggle off");
        assertFalse(NpcBrainKiRotation.shouldFlyV3(true, 8.0, true, true));
        assertTrue(NpcBrainKiRotation.shouldLandV3(true, 0.5));
        assertTrue(NpcBrainKiRotation.shouldLandV3(true, -0.5));
        assertFalse(NpcBrainKiRotation.shouldLandV3(false, 0.5));
        assertFalse(NpcBrainKiRotation.shouldLandV3(true, 2.0));
    }

    @Test
    void airChaseBrakesNearTheTargetAndCruisesAtDmzSpeed() {
        double[] hover = NpcBrainKiRotation.airChaseVelocity(0.0, -1.0, 0.0, 0.35);
        assertEquals(0.0, hover[0], 1.0e-9);
        assertEquals(0.0, hover[1], 1.0e-9);
        assertEquals(0.0, hover[2], 1.0e-9);

        double[] close = NpcBrainKiRotation.airChaseVelocity(1.4, -1.0, 0.0, 0.35, 1.0);
        double closeSpeed = Math.sqrt(close[0] * close[0] + close[1] * close[1]
                + close[2] * close[2]);
        assertTrue(closeSpeed > 0.0);
        assertTrue(closeSpeed < 0.35,
                "the direct chase must brake before crossing the target and reversing sideways");

        double[] insideStandoff = NpcBrainKiRotation.airChaseVelocity(0.8, -1.0, 0.0, 0.35, 1.0);
        assertEquals(0.0, Math.hypot(insideStandoff[0], insideStandoff[2]), 1.0e-9,
                "the chase must hold a small gap instead of flying through the target");

        assertEquals(1.0, NpcBrainKiRotation.airHoverDistance(0.6, 0.6), 1.0e-9);
        double largeGap = NpcBrainKiRotation.airHoverDistance(2.0, 2.0);
        assertTrue(largeGap > 2.0);
        double[] largeStop = NpcBrainKiRotation.airChaseVelocity(2.0, -1.0, 0.0,
                0.35, largeGap);
        assertEquals(0.0, largeStop[0], 1.0e-9,
                "large hitboxes must stop before overlapping");

        double[] closeAndAbove = NpcBrainKiRotation.airChaseVelocity(0.5, 2.0, -0.4, 0.35, 1.0);
        assertEquals(0.0, closeAndAbove[0], 1.0e-9,
                "vertical correction must not steer the NPC sideways");
        assertEquals(0.0, closeAndAbove[2], 1.0e-9,
                "vertical correction must not steer the NPC sideways");
        assertTrue(closeAndAbove[1] > 0.0,
                "vertical correction should move toward the one-block DMZ aim height");

        double[] chase = NpcBrainKiRotation.airChaseVelocity(10.0, 0.0, 0.0, 0.35);
        double speed = Math.sqrt(chase[0] * chase[0] + chase[1] * chase[1] + chase[2] * chase[2]);
        assertTrue(chase[0] > 0.0);
        assertTrue(speed <= 0.35 + 1.0e-9);
        assertEquals(0.35, NpcBrainKiRotation.flySpeedForDistance(10.0, 0.35), 1.0e-9);
        assertEquals(0.70, NpcBrainKiRotation.flySpeedForDistance(20.0, 0.35), 1.0e-9);
        assertEquals(-90.0f, NpcBrainKiRotation.targetYaw(1.0, 0.0), 0.01f);
        assertEquals(0.0f, NpcBrainKiRotation.targetYaw(0.0, 1.0), 0.01f);
    }

    @Test
    void clashAnswerPicksWaveNotBlastAndBusyWhileClashing() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.techniques.addAll(List.of("kiblast", "kiwave", "kienzan"));
        assertEquals("kiwave", NpcBrainKiRotation.clashAnswerId(profile));
        profile.techniques.clear();
        profile.techniques.add("kiblast");
        assertEquals("kiwave", NpcBrainKiRotation.clashAnswerId(profile));
        assertTrue(NpcBrainKiRotation.shouldAnswerBeam(false, true, true));
        assertFalse(NpcBrainKiRotation.shouldAnswerBeam(true, true, true));
        assertFalse(NpcBrainKiRotation.shouldAnswerBeam(false, false, true));
        assertFalse(NpcBrainKiRotation.shouldAnswerBeam(false, true, false));
    }

    @Test
    void genericKiCooldownRemainingAfterConsume() {
        UUID id = UUID.randomUUID();
        assertTrue(NpcKiCooldowns.ready(id, "kiblast"));
        NpcKiCooldowns.consume(id, "kiblast", NpcBrainKiRotation.GENERIC_KI_COOLDOWN);
        assertEquals(NpcBrainKiRotation.GENERIC_KI_COOLDOWN, NpcKiCooldowns.remaining(id, "kiblast"));
        assertFalse(NpcKiCooldowns.ready(id, "kiblast"));
        NpcKiCooldowns.clear(id);
        assertTrue(NpcKiCooldowns.ready(id, "kiblast"));
    }

    @Test
    void slewTurnsTheShortWayAndCapsTheStep() {
        assertEquals(20.0f, NpcBrainKiRotation.slew(0.0f, 90.0f, 20.0f), 1.0e-4f);
        // 170 -> -170 is 20 degrees clockwise, not 340 the long way round.
        assertEquals(-175.0f, NpcBrainKiRotation.slew(170.0f, -170.0f, 15.0f), 1.0e-4f);
        assertEquals(45.0f, NpcBrainKiRotation.slew(40.0f, 45.0f, 30.0f), 1.0e-4f);
    }

    @Test
    void yawsAreWrapped() {
        float yaw = NpcBrainKiRotation.targetYaw(-1.0, -0.01);
        assertTrue(yaw >= -180.0f && yaw < 180.0f, "targetYaw must be wrapped: " + yaw);
        float travel = NpcBrainKiRotation.travelYaw(0.01, -1.0);
        assertTrue(travel >= -180.0f && travel < 180.0f, "travelYaw must be wrapped: " + travel);
    }

    @Test
    void theArrivalDeadbandHoldsStillAndMovingNeverCrawls() {
        // Just past the gap: inside the deadband, so hold still rather than creep.
        double[] edge = NpcBrainKiRotation.airChaseVelocity(1.2, -1.0, 0.0, 0.35, 1.0);
        assertEquals(0.0, Math.hypot(edge[0], edge[2]), 1.0e-9);
        // Past the deadband: at least the minimum cruise, so the NPC visibly moves.
        double[] past = NpcBrainKiRotation.airChaseVelocity(1.3, -1.0, 0.0, 0.35, 1.0);
        assertTrue(Math.hypot(past[0], past[2]) >= NpcBrainKiRotation.AIR_MIN_CHASE - 1.0e-9);
    }
}
