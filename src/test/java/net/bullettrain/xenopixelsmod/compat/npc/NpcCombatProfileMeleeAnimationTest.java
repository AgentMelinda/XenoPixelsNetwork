package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCombatProfileMeleeAnimationTest {

    @Test
    void meleeAnimationRoundTripsOnSchemaTwelve() {
        NpcCombatProfile source = new NpcCombatProfile();
        source.meleeAnimation = "combat.xeno_my_jab";

        CompoundTag tag = source.toTag();
        assertEquals(12, tag.getInt("Schema"));
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
}