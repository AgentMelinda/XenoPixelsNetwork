package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ordered attack slots, and raising how many there are.
 *
 * <p>The slots and {@link NpcMeleeAnimCycle} have existed for a long time - a complete, working,
 * ordered-attack system that nothing could author, because no editor row and no save key reached
 * them. That is the same dead-weight shape as a stored field nothing reads, approached from the
 * other side.
 *
 * <p>The count went from 20 to 40. The interesting part is not the number but that raising it must
 * not disturb an NPC saved at the old one.
 */
class NpcAttackSlotsTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void thereAreMoreSlotsThanBefore() {
        assertTrue(NpcCombatProfile.MELEE_SLOT_COUNT > 20,
                "the count was raised past the original twenty");
        assertEquals(40, NpcCombatProfile.MELEE_SLOT_COUNT);
    }

    @Test
    void theArraysFollowTheConstantRatherThanRepeatingTheNumber() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(NpcCombatProfile.MELEE_SLOT_COUNT, profile.meleeSlotClips.length);
        assertEquals(NpcCombatProfile.MELEE_SLOT_COUNT, profile.meleeSlotOn.length);
    }

    @Test
    void nativeXenoNpcDefaultsToTheFourRequestedDmzPunchesOnly() {
        NpcCombatProfile profile = new NpcCombatProfile();
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, profile);

        assertEquals("combat.xeno_dmz_punch_right_v4", profile.meleeSlotClip(0));
        assertEquals("combat.xeno_dmz_punch_left_v4", profile.meleeSlotClip(1));
        assertEquals("combat.xeno_dmz_punch_right_v4", profile.meleeSlotClip(2));
        assertEquals("combat.xeno_dmz_punch_left_v4", profile.meleeSlotClip(3));
        for (int i = 0; i < 4; i++) assertTrue(profile.meleeSlotOn(i));
        assertEquals("combat.xeno_dmz_punch_right_v4",
                NpcMeleeAnimCycle.next(profile, 0).clip());
        assertEquals("combat.xeno_dmz_punch_left_v4",
                NpcMeleeAnimCycle.next(profile, 1).clip());
        assertEquals("", profile.meleeSlotClip(4));
        assertTrue(!profile.meleeSlotOn(4));
    }

    @Test
    void defaultsDoNotTouchOtherNpcProfilesOrAuthoredNativeSlots() {
        NpcCombatProfile otherNpc = new NpcCombatProfile();
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(false, otherNpc);
        assertEquals("", otherNpc.meleeSlotClip(0));
        assertTrue(!otherNpc.meleeSlotOn(0));

        NpcCombatProfile authored = new NpcCombatProfile();
        authored.setMeleeSlot(0, "custom_attack", false);
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, authored);
        assertEquals("custom_attack", authored.meleeSlotClip(0));
        assertTrue(!authored.meleeSlotOn(0));

        NpcCombatProfile current = new NpcCombatProfile();
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, current);
        assertEquals("combat.xeno_dmz_punch_right_v4", current.meleeSlotClip(0));
        assertTrue(current.meleeSlotOn(0));
    }

    @Test
    void defaultsPersistOnceAndAnExplicitlyClearedSequenceStaysCleared() {
        NpcCombatProfile fresh = new NpcCombatProfile();
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, fresh);
        NpcCombatProfile loaded = NpcCombatProfile.fromTag(fresh.toTag());
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, loaded);
        assertEquals("combat.xeno_dmz_punch_right_v4", loaded.meleeSlotClip(0));

        for (int i = 0; i < 4; i++) loaded.setMeleeSlot(i, "", false);
        NpcCombatProfile cleared = NpcCombatProfile.fromTag(loaded.toTag());
        NpcCombatProfile.applyNativeMeleeAnimationDefaults(true, cleared);
        assertEquals("", cleared.meleeSlotClip(0));
        assertEquals("", cleared.meleeSlotClip(1));
        assertTrue(!cleared.meleeSlotOn(0));
    }

    @Test
    void anNpcSavedAtTwentySlotsLoadsUnchanged() {
        // The compatibility case. The reader clamps with Math.min over a stored list, so a shorter
        // list fills the front and leaves the rest empty - nobody loses their existing attacks.
        NpcCombatProfile old = new NpcCombatProfile();
        old.setMeleeSlot(0, "punch", true);
        old.setMeleeSlot(1, "kick", true);
        CompoundTag tag = old.toTag();

        ListTag slots = tag.getList("MeleeAnimSlots", Tag.TAG_COMPOUND);
        while (slots.size() > 20) {
            slots.remove(slots.size() - 1);
        }
        assertEquals(20, slots.size(), "this is what an older save looked like");

        NpcCombatProfile loaded = NpcCombatProfile.fromTag(tag);
        assertEquals("punch", loaded.meleeSlotClip(0));
        assertEquals("kick", loaded.meleeSlotClip(1));
        assertTrue(loaded.meleeSlotOn(0));
        assertEquals("", loaded.meleeSlotClip(25), "the new slots start empty");
    }

    @Test
    void everySlotRoundTrips() {
        NpcCombatProfile profile = new NpcCombatProfile();
        for (int i = 0; i < NpcCombatProfile.MELEE_SLOT_COUNT; i++) {
            profile.setMeleeSlot(i, "clip" + i, i % 2 == 0);
        }
        NpcCombatProfile back = NpcCombatProfile.fromTag(profile.toTag());
        for (int i = 0; i < NpcCombatProfile.MELEE_SLOT_COUNT; i++) {
            assertEquals("clip" + i, back.meleeSlotClip(i), "slot " + i);
            assertEquals(i % 2 == 0, back.meleeSlotOn(i), "slot " + i + " toggle");
        }
    }

    @Test
    void outOfRangeSlotsAreIgnoredRatherThanThrowing() {
        // Indices reach these from a packet and from scripts; a bad one is a no-op, not a crash.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(-1, "x", true);
        profile.setMeleeSlot(NpcCombatProfile.MELEE_SLOT_COUNT, "x", true);
        profile.setMeleeSlot(9999, "x", true);
        assertEquals("", profile.meleeSlotClip(-1));
        assertEquals("", profile.meleeSlotClip(NpcCombatProfile.MELEE_SLOT_COUNT));
    }

    @Test
    void theCyclerWalksTheSlotsInOrderAndSkipsBlanks() {
        // The order is the feature: slot 1, then 2, then 3. Blanks are stepped over so a gap in the
        // middle does not stall the rotation.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(0, "a", true);
        profile.setMeleeSlot(2, "c", true);

        NpcMeleeAnimCycle.Pick first = NpcMeleeAnimCycle.next(profile, 0);
        assertEquals("a", first.clip());

        NpcMeleeAnimCycle.Pick second = NpcMeleeAnimCycle.next(profile, first.nextCursor());
        assertEquals("c", second.clip(), "slot 2 is blank and should be skipped");

        NpcMeleeAnimCycle.Pick third = NpcMeleeAnimCycle.next(profile, second.nextCursor());
        assertEquals("a", third.clip(), "and it wraps back to the first");
    }

    @Test
    void aSlotThatIsOffIsNotPlayed() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setMeleeSlot(0, "a", false);
        profile.setMeleeSlot(1, "b", true);
        assertEquals("b", NpcMeleeAnimCycle.next(profile, 0).clip());
    }

    @Test
    void theEditorCanFinallyReachThem() throws IOException {
        // The gap this closes: a working system with no way to author it.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("attackSlotRows()"), "there should be an attack-slot screen");
        assertTrue(editor.contains("setMeleeSlotClip("), "with an editable clip per slot");
        assertTrue(editor.contains("setMeleeSlotOn("), "and a toggle per slot");
        assertTrue(editor.contains("MELEE_SLOT_COUNT"),
                "rows should follow the constant, not a repeated literal");
    }

    @Test
    void theSlotsAreSaveable() throws IOException {
        String policy = source("network/packet/XenoNpcSavePolicy.java");
        assertTrue(policy.contains("\"MeleeAnimSlots\""),
                "the key belongs on the whitelist now that the editor writes it");
        assertTrue(policy.contains("validateMeleeAnimSlots"),
                "and the list length should be bounded rather than trusted");
    }

    @Test
    void nativeCombatUsesTheOrderedClipBeforeItsNormalPunch() throws IOException {
        String damage = source("compat/npc/NpcMeleeDamage.java");
        int slots = damage.indexOf("NpcMeleeAnimCycle.next(profile");
        int normalPunch = damage.indexOf("CombatStateAnim.hasCustom(attacker");
        assertTrue(slots >= 0 && normalPunch > slots,
                "the ordered DMZ clip must have priority over the generic punch clip");
        int suppressionStart = damage.indexOf("public static boolean playsOwnAttackAnimation");
        int suppressionEnd = damage.indexOf("public static void onMeleeAttempt", suppressionStart);
        String suppression = damage.substring(suppressionStart, suppressionEnd);
        assertTrue(suppression.contains("XenoNpcEntity"),
                "native XenoNPC attacks need the same own-animation swing guard");
        assertTrue(suppression.contains("!nativeXenoNpc && !NpcCombatProfile.hasProfile(attacker)"),
                "native XenoNPC defaults must work before a profile is explicitly saved");
        assertTrue(damage.contains("(!nativeXenoNpc && !NpcCombatProfile.hasProfile(attacker))"),
                "the melee playback path must also accept an unsaved native default profile");
    }
}
