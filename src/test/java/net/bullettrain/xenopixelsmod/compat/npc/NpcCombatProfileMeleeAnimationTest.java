package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileMeleeAnimationTest {

    @Test
    void meleeAnimationRoundTripsOnCurrentSchema() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.meleeAnimation = "combat.xeno_my_jab";

        CompoundTag tag = source.toTag();
        assertEquals(20, tag.getInt("Schema"));
        assertEquals("combat.xeno_my_jab", tag.getString("MeleeAnimation"));

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(tag);
        assertEquals("combat.xeno_my_jab", decoded.meleeAnimation);
    }

    @Test
    void visualOptionsCarryTheAttackClip() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.meleeAnimation = "combat.xeno_hakai_hold";

        NpcCombatProfile decoded = new NpcCombatProfile();
        decoded.applyVisualOptions(source.visualOptionsTag());
        assertEquals("combat.xeno_hakai_hold", decoded.meleeAnimation);
    }

    @Test
    void meleeAnimationChoicesStartWithTheDefaultPunches() {
        var choices = NpcCombatProfile.meleeAnimationChoices();
        assertEquals("", choices.get(0));
        assertTrue(choices.size() > 1);
        assertEquals(choices.get(1), NpcCombatProfile.stepMeleeAnimation("", 1));
    }

    @Test
    void stateClipsRoundTripAndPunchMirrorsMeleeAnimation() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.setStateClip("TRANSFORM", "ssj3_pose");
        source.setStateClip("CHARGE_PUNCH", "my_charge");
        source.setStateClip("PUNCH", "my_jab");

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertEquals("ssj3_pose", decoded.getStateClip("TRANSFORM"));
        assertEquals("my_charge", decoded.getStateClip("CHARGE_PUNCH"));
        assertEquals("my_jab", decoded.getStateClip("PUNCH"));
        assertEquals("my_jab", decoded.meleeAnimation);
    }

    @Test
    void meleeSlotsRoundTripTwentyPlaceholders() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.setMeleeSlot(0, "jab_left", true);
        source.setMeleeSlot(3, "uppercut", true);
        source.setMeleeSlot(19, "kick", false);

        NpcCombatProfile decoded = NpcCombatProfile.fromTag(source.toTag());
        assertEquals("jab_left", decoded.meleeSlotClip(0));
        assertTrue(decoded.meleeSlotOn(0));
        assertEquals("uppercut", decoded.meleeSlotClip(3));
        assertTrue(decoded.meleeSlotOn(3));
        assertEquals("kick", decoded.meleeSlotClip(19));
        assertFalse(decoded.meleeSlotOn(19));
        assertEquals("", decoded.meleeSlotClip(1));
        assertFalse(decoded.meleeSlotOn(1));
    }

    @Test
    void visualOverlayWithoutMeleeSlotsKeepsExistingOnClips() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(2, "keep_me", true);
        CompoundTag compact = new CompoundTag();
        compact.putBoolean("FlySkillOn", true);
        profile.applyVisualOptions(compact);
        assertEquals("keep_me", profile.meleeSlotClip(2));
        assertTrue(profile.meleeSlotOn(2));
        assertTrue(profile.flySkillOn);
    }
}
