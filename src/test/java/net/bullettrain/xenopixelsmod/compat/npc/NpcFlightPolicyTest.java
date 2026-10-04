package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcFlightPolicyTest {
    @Test
    void sagaFlightRequiresTheBrainAndAnEnabledDmzFlySkill() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFly = true;
        profile.flySkillOn = true;

        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.skills.set(NpcSkillSet.FLY, false, 3);
        assertFalse(NpcFlightPolicy.canCombatFly(profile));

        profile.skills.set(NpcSkillSet.FLY, true, 3);
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.brainFly = false;
        assertFalse(NpcFlightPolicy.canCombatFly(profile));
    }

    @Test
    void aLegacyFlyToggleStillWorksWhenNoSkillMapEntryExists() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFly = true;
        profile.flySkillOn = true;
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.flySkillOn = false;
        assertFalse(NpcFlightPolicy.canCombatFly(profile));
    }

    @Test
    void pureDmzBrainVersionsUseTheDmzFlySkillWithoutAHiddenBrainToggle() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFly = false;
        profile.brainVersion = NpcCombatBrainVersion.V8;
        profile.flySkillOn = true;
        profile.skills.set(NpcSkillSet.FLY, true, 3);
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.brainVersion = NpcCombatBrainVersion.V9;
        assertFalse(NpcFlightPolicy.canCombatFly(profile),
                "V9 exposes its separate per-action Fly control");
        profile.brainFly = true;
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.flySkillOn = false;
        assertFalse(NpcFlightPolicy.canCombatFly(profile),
                "the DMZ Fly skill remains the hard capability gate");
    }

    @Test
    void dmzFlyControlSetsTheSkillCapabilityWithoutTouchingTheBrainAction() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFly = false;
        profile.brainVersion = NpcCombatBrainVersion.V9;
        profile.flySkillLevel = 4;

        profile.setDmzFlyEnabled(true);

        assertTrue(profile.flySkillOn);
        assertTrue(profile.skills.isActive(NpcSkillSet.FLY));
        assertEquals(4, profile.skills.level(NpcSkillSet.FLY));
        assertFalse(profile.brainFly,
                "the skills-menu control must not flip the operator's Brain Fly action");
        assertFalse(NpcFlightPolicy.canCombatFly(profile),
                "toggle-aware brains still require the action to be enabled separately");

        profile.brainFly = true;
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.setDmzFlyEnabled(false);
        assertFalse(profile.flySkillOn);
        assertFalse(profile.skills.isActive(NpcSkillSet.FLY));
        assertTrue(profile.brainFly, "disabling the skill must not rewrite the action either");
        assertFalse(NpcFlightPolicy.canCombatFly(profile));
    }

    @Test
    void canUseFlightIsADefaultOnMasterGate() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainFly = true;
        profile.flySkillOn = true;
        assertTrue(profile.canUseFlight, "profiles saved before the key existed keep flying");
        assertTrue(NpcFlightPolicy.canCombatFly(profile));

        profile.canUseFlight = false;
        assertFalse(NpcFlightPolicy.canCombatFly(profile));
    }

    @Test
    void optionalChaseMoveYieldsWhileTheSagaAirChaseOwnsMovement() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.brainChase = true;

        assertTrue(NpcFlightPolicy.canUseGroundChase(profile, false));
        assertFalse(NpcFlightPolicy.canUseGroundChase(profile, true),
                "the teleporting chase move must not interrupt DMZ flight velocity");

        profile.brainChase = false;
        assertFalse(NpcFlightPolicy.canUseGroundChase(profile, false));
    }

    @Test
    void walkingPlayerWithBrieflyFalseGroundFlagEndsAirChase() {
        assertTrue(NpcFlightPolicy.walkingOnSupport(false, false, true, 0.0));
        assertTrue(NpcFlightPolicy.walkingOnSupport(true, true, false, 0.0));
        assertTrue(NpcFlightPolicy.walkingOnSupport(false, true, true, 0.0),
                "physical floor support counts even when the Fly skill stays enabled");
        assertFalse(NpcFlightPolicy.walkingOnSupport(false, true, false, 0.0),
                "a side wall is not physical floor support");
        assertFalse(NpcFlightPolicy.walkingOnSupport(false, false, false, 0.0),
                "turning Fly off midair should allow the NPC to keep chasing until landing");
        assertFalse(NpcFlightPolicy.walkingOnSupport(false, false, true, 0.2),
                "jumping beside a block must not count as landing");
    }
}
