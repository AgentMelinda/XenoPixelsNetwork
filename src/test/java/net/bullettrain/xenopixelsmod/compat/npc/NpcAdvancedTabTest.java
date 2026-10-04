package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The MyNPCs Advanced set: Sounds, Night, Linked NPCs, Editing Mode and the Mark.
 *
 * <p>Every one of these is a new profile field, and the failure they all share is the one this
 * editor has already had once - a control that looks live, takes a value, and silently loses it on
 * reload because the write and the read disagree. So the bulk of this is round-tripping.
 */
class NpcAdvancedTabTest {

    private static NpcCombatProfile roundTrip(NpcCombatProfile profile) {
        return NpcCombatProfile.fromTag(profile.toTag());
    }

    @Test
    void everySoundSlotSurvivesASaveAndReload() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.soundLiving = "minecraft:entity.villager.ambient";
        profile.soundAngry = "minecraft:entity.villager.no";
        profile.soundHurt = "minecraft:entity.villager.hurt";
        profile.soundDeath = "minecraft:entity.villager.death";
        profile.soundStep = "minecraft:block.gravel.step";

        NpcCombatProfile back = roundTrip(profile);
        assertEquals(profile.soundLiving, back.soundLiving);
        assertEquals(profile.soundAngry, back.soundAngry);
        assertEquals(profile.soundHurt, back.soundHurt);
        assertEquals(profile.soundDeath, back.soundDeath);
        assertEquals(profile.soundStep, back.soundStep);
    }

    @Test
    void soundForNamesTheRightSlotAndToleratesTheAliases() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.soundLiving = "a";
        profile.soundAngry = "b";
        profile.soundHurt = "c";
        profile.soundDeath = "d";
        profile.soundStep = "e";

        assertEquals("a", profile.soundFor("living"));
        assertEquals("b", profile.soundFor("angry"));
        assertEquals("c", profile.soundFor("hurt"));
        assertEquals("d", profile.soundFor("death"));
        assertEquals("e", profile.soundFor("step"));

        // MyNPCs calls the first one "Living"; the vanilla hook is getAmbientSound, and the code
        // reads better at each call site using its own word. Both have to land on the same field.
        assertEquals("a", profile.soundFor("idle"));
        assertEquals("a", profile.soundFor("ambient"));
        assertEquals("b", profile.soundFor("anger"));
        assertEquals("d", profile.soundFor("die"));

        assertEquals("", profile.soundFor("not-a-slot"));
        assertEquals("", profile.soundFor(null));
    }

    @Test
    void hasPitchDefaultsOnAndSurvivesBeingTurnedOff() {
        assertTrue(new NpcCombatProfile().soundHasPitch, "MyNPCs ships Has Pitch on");

        NpcCombatProfile profile = new NpcCombatProfile();
        profile.soundHasPitch = false;
        assertFalse(roundTrip(profile).soundHasPitch);
    }

    @Test
    void anOlderProfileWithoutTheKeyStillGetsPitch() {
        // A save written before these fields existed has no SoundHasPitch key at all. Reading that
        // as false would silently change how every existing NPC sounds.
        CompoundTag old = new NpcCombatProfile().toTag();
        old.remove("SoundHasPitch");
        assertTrue(NpcCombatProfile.fromTag(old).soundHasPitch);
    }

    @Test
    void theNightTextureAndEditingLockRoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.nightTexture = "xenopixelsmod:textures/entity/night.png";
        profile.editingLocked = true;

        NpcCombatProfile back = roundTrip(profile);
        assertEquals(profile.nightTexture, back.nightTexture);
        assertTrue(back.editingLocked);

        // And the default is unlocked, or every newly made NPC would refuse its first edit.
        assertFalse(new NpcCombatProfile().editingLocked);
    }

    @Test
    void linkedNpcsRoundTripInOrderAndDropBlanks() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.linkedNpcs.add("11111111-1111-1111-1111-111111111111");
        profile.linkedNpcs.add("");
        profile.linkedNpcs.add("   ");
        profile.linkedNpcs.add("22222222-2222-2222-2222-222222222222");

        NpcCombatProfile back = roundTrip(profile);
        assertEquals(2, back.linkedNpcs.size(), "blank entries should not be stored");
        assertEquals("11111111-1111-1111-1111-111111111111", back.linkedNpcs.get(0));
        assertEquals("22222222-2222-2222-2222-222222222222", back.linkedNpcs.get(1));
    }

    @Test
    void theMarkRoundTripsAndAnUnknownIconBecomesNone() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.markIcon = "skull";
        profile.markColor = 0xFF8800;

        NpcCombatProfile back = roundTrip(profile);
        assertEquals("skull", back.markIcon);
        assertEquals(0xFF8800, back.markColor);

        // An icon with no sprite behind it would ask the renderer for a texture that is not there.
        assertEquals("", NpcCombatProfile.canonicalMarkIcon("banana"));
        assertEquals("", NpcCombatProfile.canonicalMarkIcon(null));
        assertEquals("star", NpcCombatProfile.canonicalMarkIcon("  STAR  "));
    }

    @Test
    void theMarkListIsTheSixMyNpcsIconsPlusNone() {
        assertEquals(7, NpcCombatProfile.MARK_ICONS.size());
        assertEquals("", NpcCombatProfile.MARK_ICONS.get(0), "the first entry is 'no mark'");
        for (String icon : new String[]{"cross", "exclamation", "pointer", "question", "skull",
                "star"}) {
            assertTrue(NpcCombatProfile.MARK_ICONS.contains(icon), icon + " is missing");
        }
    }

    @Test
    void everyMarkInTheListSurvivesBeingCanonicalised() {
        // The editor cycles this list straight into the field, so anything in it must be accepted
        // back unchanged or the cycler would snap to "none" on the next open.
        for (String icon : NpcCombatProfile.MARK_ICONS) {
            assertEquals(icon, NpcCombatProfile.canonicalMarkIcon(icon));
        }
    }

    @Test
    void theAdvancedSetDoesNotDisturbAnythingElseOnTheProfile() {
        // These were added to toTag/fromTag late, and the risk with that is clobbering a neighbour.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.combatBrain = true;
        profile.brainVersion = NpcCombatBrainVersion.V6;
        profile.meleeAnimation = "punch";
        profile.soundDeath = "minecraft:entity.villager.death";
        profile.markIcon = "star";

        NpcCombatProfile back = roundTrip(profile);
        assertTrue(back.combatBrain);
        assertEquals(NpcCombatBrainVersion.V6, back.brainVersion);
        assertEquals("punch", back.meleeAnimation);
        assertEquals("minecraft:entity.villager.death", back.soundDeath);
        assertEquals("star", back.markIcon);
    }
}
