package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ranged and projectile fields My NPCs has and we did not.
 *
 * <p>Each one below is a field on {@code DataRanged} in {@code mynpcs-neoforge-1.5.0.jar}:
 * {@code shotCount}, {@code pDur}, {@code pEffAmp}, {@code pTrail}, {@code pSpin}, {@code pStick}
 * and {@code pGlows}. The parity list calls for all of them; we had none, and two of them were
 * quietly sharing the melee fields instead.
 */
class RangedProjectileParityTest {

    // ------------------------------------------------------------ shot count

    @Test
    void shotCountAndBurstCountAreSeparateIdeas() {
        // Theirs are two nested counts: a shot is a spread released together, a burst repeats that
        // spread over time. Folding them into one could only ever produce a stream.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcRangedShotCount = 5;
        profile.npcRangedBurstCount = 3;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(5, read.npcRangedShotCount);
        assertEquals(3, read.npcRangedBurstCount);
    }

    @Test
    void aProfileSavedBeforeShotCountExistedFiresOneRatherThanNone() {
        // The migration that matters. An absent key reads zero, and a zero shot count is an NPC
        // whose ranged attack silently does nothing at all.
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.getCompound("NpcRangedProps").remove("ShotCount");
        assertEquals(1, NpcCombatProfile.fromTag(tag).npcRangedShotCount);
    }

    @Test
    void shotCountIsBoundedBothWays() {
        assertEquals(1, NpcCombatProfile.clampNpcShotCount(0));
        assertEquals(1, NpcCombatProfile.clampNpcShotCount(-4));
        assertEquals(16, NpcCombatProfile.clampNpcShotCount(999));
        assertEquals(7, NpcCombatProfile.clampNpcShotCount(7));
    }

    // ------------------------------------------------------------ projectile effect timing

    @Test
    void theProjectileEffectKeepsItsOwnDurationAndAmplifier() {
        // They used to read the melee pair, so lengthening a melee poison lengthened an unrelated
        // projectile effect with it. My NPCs keeps pDur and pEffAmp apart for that reason.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcMeleeEffectDuration = 40;
        profile.npcMeleeEffectAmplifier = 1;
        profile.npcProjectileEffectDuration = 200;
        profile.npcProjectileEffectAmplifier = 4;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(40, read.npcMeleeEffectDuration);
        assertEquals(1, read.npcMeleeEffectAmplifier);
        assertEquals(200, read.npcProjectileEffectDuration);
        assertEquals(4, read.npcProjectileEffectAmplifier);
    }

    @Test
    void anOlderProfileInheritsTheMeleeTimingItUsedToBorrow() {
        // Whatever an existing NPC's projectile effect was doing, it has to keep doing on load.
        // Reading an absent key through the normal clamp would floor the duration at one tick.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcMeleeEffectDuration = 300;
        profile.npcMeleeEffectAmplifier = 2;
        CompoundTag tag = profile.toTag();
        tag.getCompound("NpcProjectileProps").remove("EffectDuration");
        tag.getCompound("NpcProjectileProps").remove("EffectAmplifier");

        NpcCombatProfile read = NpcCombatProfile.fromTag(tag);
        assertEquals(300, read.npcProjectileEffectDuration);
        assertEquals(2, read.npcProjectileEffectAmplifier);
    }

    // ------------------------------------------------------------ flight flags

    @Test
    void theFlightFlagsSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.npcProjectileSpins = true;
        profile.npcProjectileSticks = true;
        profile.npcProjectileGlows = true;
        profile.npcProjectileTrail = "flame";

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertTrue(read.npcProjectileSpins);
        assertTrue(read.npcProjectileSticks);
        assertTrue(read.npcProjectileGlows);
        assertEquals("flame", read.npcProjectileTrail);
    }

    @Test
    void allThreeFlagsDefaultOffSoAnExistingNpcLooksUnchanged() {
        // They default false, so getBoolean on an absent key happens to be right - which is only
        // true because of the default. A default-true flag read this way is the trap that bit the
        // toggles that needed flagOrDefault.
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.getCompound("NpcProjectileProps").remove("Spins");
        tag.getCompound("NpcProjectileProps").remove("Sticks");
        tag.getCompound("NpcProjectileProps").remove("Glows");

        NpcCombatProfile read = NpcCombatProfile.fromTag(tag);
        assertFalse(read.npcProjectileSpins);
        assertFalse(read.npcProjectileSticks);
        assertFalse(read.npcProjectileGlows);
    }

    // ------------------------------------------------------------ trail

    @Test
    void anUnknownTrailReadsAsNoneRatherThanThrowing() {
        assertEquals("none", NpcCombatProfile.canonicalProjectileTrail("rainbow"));
        assertEquals("none", NpcCombatProfile.canonicalProjectileTrail(""));
        assertEquals("none", NpcCombatProfile.canonicalProjectileTrail(null));
        assertEquals("smoke", NpcCombatProfile.canonicalProjectileTrail("  SMOKE "));
    }

    @Test
    void noneIsTheFirstTrailSoTheDefaultIsTheOneThatChangesNothing() {
        // The editor cycles this list by index, so the entry that leaves DragonMineZ's own look
        // alone has to be where an unset profile lands.
        assertEquals("none", NpcCombatProfile.projectileTrailModes().get(0));
        assertEquals("none", new NpcCombatProfile().npcProjectileTrail);
    }

    @Test
    void everyOfferedTrailIsOneTheRuntimeCanDraw() {
        // A named trail with no particle behind it is a dead control wearing a cycle's clothes.
        // NpcKiProjectileEffects.particle answers null for anything it cannot draw and the entry
        // is then dropped, so the list and that switch have to agree entry for entry.
        for (String trail : NpcCombatProfile.projectileTrailModes()) {
            assertEquals(trail, NpcCombatProfile.canonicalProjectileTrail(trail),
                    trail + " must survive its own canonicaliser");
        }
    }
}
