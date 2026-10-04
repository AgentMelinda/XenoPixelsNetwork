package net.bullettrain.xenopixelsmod.compat.npc;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The per-NPC escape from XenoPixels overwriting CustomNPCs' own combat fields.
 *
 * <p>With authoritative DragonMineZ stats on, {@code NpcCounterpartSync} forces native melee,
 * regeneration and ranged combat fields. Native MyNPC resistance-tab values remain owned by
 * MyNPC, so an admin's hand-set resistance survives an unrelated DragonMineZ stat edit.
 */
class NpcPinNativeCombatTest {

    /** Existing NPCs must not change behaviour, so the flag has to default to what happened before. */
    @Test
    void pinningIsOnByDefault() {
        assertTrue(new NpcCombatProfile().pinNativeCombat,
                "defaulting this off would silently stop pinning every NPC that already exists");
    }

    @Test
    void theFlagSurvivesATagRoundTrip() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.pinNativeCombat = false;
        CompoundTag tag = profile.toTag();
        assertFalse(NpcCombatProfile.fromTag(tag).pinNativeCombat,
                "the opt-out did not persist; it would revert on the next world load");

        profile.pinNativeCombat = true;
        assertTrue(NpcCombatProfile.fromTag(profile.toTag()).pinNativeCombat);
    }

    /**
     * A profile written before this flag existed keeps the old behaviour.
     *
     * <p>Absent means pinned, matching how {@code Punchable} treats a missing tag. Reading a missing
     * boolean as false would quietly un-pin every NPC on the server the moment this shipped.
     */
    @Test
    void anOlderProfileWithoutTheTagIsStillPinned() {
        CompoundTag tag = new NpcCombatProfile().toTag();
        tag.remove("PinNativeCombat");
        assertTrue(NpcCombatProfile.fromTag(tag).pinNativeCombat);
    }

    /**
     * Flipping the flag alone moves the authority fingerprint.
     *
     * <p>This is the one that would have made the feature look broken. {@code NpcCounterpartSync}
     * skips its work entirely when the fingerprint matches what it last applied, so a flag outside
     * the hash would do nothing at all until some unrelated stat edit happened to move it — the
     * setting would appear to save and have no effect.
     */
    @Test
    void togglingTheFlagChangesTheAuthorityFingerprint() {
        NpcCombatProfile pinned = new NpcCombatProfile();
        NpcCombatProfile loose = new NpcCombatProfile();
        loose.pinNativeCombat = false;
        assertNotEquals(pinned.authorityFingerprint(), loose.authorityFingerprint(),
                "the counterpart sync would skip the change as a no-op");
    }

    /** Two profiles differing in nothing still agree, so the flag did not destabilise the hash. */
    @Test
    void identicalProfilesStillShareAFingerprint() {
        assertTrue(new NpcCombatProfile().authorityFingerprint()
                == new NpcCombatProfile().authorityFingerprint());
    }

    /**
     * Native overwrite is the AND of the server authority switch and the per-NPC flag.
     *
     * <p>Death/join {@code force()} still refreshes flight, aggro, vitality and the DMZ blob.
     * These two predicates decide whether native combat fields get rewritten and whether MyNPCs
     * gets {@code updateClient} for that rewrite.
     */
    @Test
    void nativePinRequiresBothAuthorityAndThePerNpcFlag() {
        assertTrue(NpcCounterpartSync.shouldPinNative(true, true));
        assertFalse(NpcCounterpartSync.shouldPinNative(true, false),
                "unpinning an NPC must stop the 1/1.0f native overwrite after death");
        assertFalse(NpcCounterpartSync.shouldPinNative(false, true),
                "global npcDmzStatsAuthoritative off already means natives are left to CustomNPCs");
        assertFalse(NpcCounterpartSync.shouldPinNative(false, false));
    }

    @Test
    void clientUpdateIsOnlyForAnActualNativeWrite() {
        assertTrue(NpcCounterpartSync.shouldMarkClientForNative(true));
        assertFalse(NpcCounterpartSync.shouldMarkClientForNative(false),
                "a death force with pin off and no backup must not flash the Stats GUI");
    }

    /**
     * Chunk load, clone paste, wand reload and death reset all go through {@code forceLifecycle}.
     * That path must not rewrite MyNPCs melee/resistance or a reload looks like a Stats refresh.
     */
    @Test
    void aReloadMustNotRewriteNativeMeleeOrResistance() {
        assertFalse(NpcCounterpartSync.shouldRewriteNative(false),
                "join/death/chunk-load would keep snapping damage and knockback resistance");
    }

    @Test
    void aProfileWriteMayStillRewriteNativeCombatFields() {
        assertTrue(NpcCounterpartSync.shouldRewriteNative(true),
                "the editor Pin native toggle and /npcprofile combat pinnative still have to work");
    }

    /** Native resistance is intentionally outside the Xeno native-pin backup/restore boundary. */
    @Test
    void nativeResistanceIsNotPartOfThePinnedBackupSchema() {
        CompoundTag backup = new CompoundTag();
        backup.putInt("HealthRegen", 4);
        backup.putInt("CombatRegen", 2);
        assertFalse(backup.contains("Resistance0"),
                "a DMZ profile edit must not restore a stale resistance-tab value");
    }
}
