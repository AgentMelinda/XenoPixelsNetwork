package net.bullettrain.xenopixelsmod.npc.job;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcJob;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The job axis: My NPCs' second dimension, independent of the role.
 *
 * <p>Bard, Healer, Guard, Item Giver and Follower have server runtime paths. The remaining named jobs have explicit
 * reservations. Runtime state lives on the profile and each job returns early for other NPCs.
 */
class JobAxisTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    // ------------------------------------------------------------ the enum

    @Test
    void theListIsTheirVisibleOrder() {
        // An operator moving between the two editors finds the same list in the same place.
        assertEquals(List.of("No Job", "Bard", "Healer", "Guard", "Item Giver", "Follower",
                        "Spawner", "Conversation", "Chunk Loader", "Puppet", "Builder", "Farmer"),
                XenoNpcJob.labels());
    }

    @Test
    void noJobHasTheEmptyIdSoAnOrdinaryNpcWritesNoTag() {
        // The NBT of an NPC without a job is byte-identical to what it was before jobs existed.
        assertEquals("", XenoNpcJob.NONE.id());
        assertFalse(new NpcCombatProfile().toTag().contains("Job"));
    }

    @Test
    void anUnknownJobFoldsToNoneRatherThanThrowing() {
        // A world saved with a job a later build removed must still load.
        assertEquals(XenoNpcJob.NONE, XenoNpcJob.byId("blacksmith"));
        assertEquals(XenoNpcJob.NONE, XenoNpcJob.byId(null));
        assertEquals(XenoNpcJob.BARD, XenoNpcJob.byId("  BARD  "));
    }

    @Test
    void aJobRoundTripsAsAnIdAndNotAnOrdinal() {
        // So inserting a job into the middle of the list cannot reclassify saved NPCs.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = XenoNpcJob.FARMER.id();
        assertEquals("farmer", NpcCombatProfile.fromTag(profile.toTag()).job);
    }

    @Test
    void jobEnabledIsWritableAndLegacyJobsRemainEnabled() {
        NpcCombatProfile legacy = new NpcCombatProfile();
        legacy.job = XenoNpcJob.BARD.id();
        var oldTag = legacy.toTag();
        oldTag.remove("JobEnabled");
        assertTrue(NpcCombatProfile.fromTag(oldTag).jobEnabled);

        assertFalse(new NpcCombatProfile().toTag().contains("JobEnabled"));

        legacy.jobEnabled = false;
        assertFalse(NpcCombatProfile.fromTag(legacy.toTag()).jobEnabled);
    }

    @Test
    void anUnknownStoredJobIsNormalisedOnRead() {
        NpcCombatProfile profile = new NpcCombatProfile();
        CompoundTag tag = profile.toTag();
        tag.putString("Job", "blacksmith");
        assertEquals("", NpcCombatProfile.fromTag(tag).job);
    }

    /** The jobs that have a runtime today. Add to this list only when one actually gains one. */
    private static final java.util.Set<XenoNpcJob> LIVE =
            java.util.Set.of(XenoNpcJob.NONE, XenoNpcJob.BARD, XenoNpcJob.HEALER,
                    XenoNpcJob.GUARD, XenoNpcJob.FOLLOWER, XenoNpcJob.ITEM_GIVER);

    @Test
    void onlyTheJobsThatRunClaimToRun() {
        // A tripwire, deliberately: it fails when a job is switched on, which forces whoever did
        // it to come here and say so rather than leaving the editor claiming something runs.
        for (XenoNpcJob job : XenoNpcJob.values()) {
            assertEquals(LIVE.contains(job), job.implemented(),
                    job + " disagrees with the list of jobs that have a runtime");
        }
    }

    @Test
    void theGuardHasAJobClassBehindItsClaim() throws IOException {
        // implemented() is a claim; this is the thing that makes it true.
        assertTrue(java.nio.file.Files.exists(RepoRoot.of(
                "src/main/java/net/bullettrain/xenopixelsmod/npc/job", "NpcGuardJob.java")));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java")
                .contains("NpcGuardJob.tick("), "and something has to call it");
    }

    @Test
    void healerHasRuntimeAndKeepsItsConfiguredEffects() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java")
                .contains("NpcHealerJob.tick("));
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = XenoNpcJob.HEALER.id();
        profile.healerRange = 15;
        profile.healerType = 0;
        profile.healerSpeed = 40;
        profile.healerEffects.put("minecraft:regeneration", 2);
        NpcCombatProfile loaded = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(15, loaded.healerRange);
        assertEquals(0, loaded.healerType);
        assertEquals(40, loaded.healerSpeed);
        assertEquals(2, loaded.healerEffects.get("minecraft:regeneration"));
        assertTrue(NpcHealerJob.affects(0, false));
        assertFalse(NpcHealerJob.affects(0, true));
        assertTrue(NpcHealerJob.affects(1, true));
        assertFalse(NpcHealerJob.affects(1, false));
        assertTrue(NpcHealerJob.affects(2, true));
    }

    @Test
    void followerFindsNamesWithoutCaseAndPersistsItsTarget() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java")
                .contains("NpcFollowerJob.tick("));
        assertTrue(NpcFollowerJob.matchesName(" Goku ", "goku"));
        assertFalse(NpcFollowerJob.matchesName("Goku", "Gohan"));
        assertFalse(NpcFollowerJob.matchesName("", "Goku"));
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = XenoNpcJob.FOLLOWER.id();
        profile.followerName = "Goku";
        assertEquals("Goku", NpcCombatProfile.fromTag(profile.toTag()).followerName);
    }

    @Test
    void itemGiverHasRuntimeAndPersistsItsNineSlots() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcEntity.java")
                .contains("NpcItemGiverJob.tick("));
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.job = XenoNpcJob.ITEM_GIVER.id();
        profile.itemGiverMethod = 4;
        profile.itemGiverCooldownType = 2;
        profile.itemGiverCooldown = 25;
        profile.itemGiverLines.clear();
        profile.itemGiverLines.add("Take these, {player}!");
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putInt("count", 3);
        profile.itemGiverItems[8] =
                net.bullettrain.xenopixelsmod.npc.inventory.NpcSlotStack.load(item);
        NpcCombatProfile loaded = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals(4, loaded.itemGiverMethod);
        assertEquals(2, loaded.itemGiverCooldownType);
        assertEquals(25, loaded.itemGiverCooldown);
        assertEquals(item, loaded.itemGiverItems[8].saved());
        assertEquals(List.of("Take these, {player}!"), loaded.itemGiverLines);
        assertTrue(loaded.itemGiverItems[0].isEmpty());
        assertFalse(new NpcCombatProfile().toTag().contains("ItemGiverItems"));
    }

    @Test
    void itemGiverMethodsAndCooldownsFollowThePinnedJobRules() {
        List<Integer> slots = List.of(0, 3, 8);
        assertEquals(List.of(3), NpcItemGiverJob.selectSlots(0, slots, Set.of(), 0, 1));
        assertEquals(slots, NpcItemGiverJob.selectSlots(1, slots, Set.of(), 0, 0));
        assertEquals(List.of(0, 8), NpcItemGiverJob.selectSlots(2, slots, Set.of(3), 0, 0));
        assertEquals(List.of(), NpcItemGiverJob.selectSlots(3, slots, Set.of(3), 0, 0));
        assertEquals(slots, NpcItemGiverJob.selectSlots(3, slots, Set.of(), 0, 0));
        assertEquals(List.of(8), NpcItemGiverJob.selectSlots(4, slots, Set.of(), 4, 0));
        assertEquals(List.of(0), NpcItemGiverJob.selectSlots(4, slots, Set.of(), 9, 0));

        var previous = new net.bullettrain.xenopixelsmod.capability.XenoPlayerData
                .ItemGiverUse(10_000L, 4L, 3);
        assertFalse(NpcItemGiverJob.ready(0, 10, previous, 19_999L, 5));
        assertTrue(NpcItemGiverJob.ready(0, 10, previous, 20_000L, 5));
        assertFalse(NpcItemGiverJob.ready(1, 10, previous, 30_000L, 5));
        assertFalse(NpcItemGiverJob.ready(2, 10, previous, 30_000L, 4));
        assertTrue(NpcItemGiverJob.ready(2, 10, previous, 30_000L, 5));
    }

    @Test
    void itemGiverUseSurvivesPlayerSaveAndDeathCopy() {
        var original = new net.bullettrain.xenopixelsmod.capability.XenoPlayerData();
        var npc = java.util.UUID.randomUUID();
        original.recordItemGiverUse(npc, 10_000L, 4L, 8);
        CompoundTag saved = new CompoundTag();
        original.saveNBT(saved);
        var loaded = new net.bullettrain.xenopixelsmod.capability.XenoPlayerData();
        loaded.loadNBT(saved);
        assertEquals(original.itemGiverUse(npc), loaded.itemGiverUse(npc));
        var reborn = new net.bullettrain.xenopixelsmod.capability.XenoPlayerData();
        reborn.copyFrom(original);
        assertEquals(original.itemGiverUse(npc), reborn.itemGiverUse(npc));
    }

    @Test
    void everyJobWithNoRuntimeSaysWhatWouldUnblockIt() throws IOException {
        // The same treatment the empty store categories got, for the same reason: a name with no
        // explanation is indistinguishable from an oversight.
        String source = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/npc", "XenoNpcJob.java"),
                StandardCharsets.UTF_8);
        for (XenoNpcJob job : XenoNpcJob.values()) {
            if (job.implemented()) {
                continue;
            }
            int declaration = source.indexOf(job.name() + "(\"");
            assertTrue(declaration > 0, job + " should be declared");
            String javadoc = source.substring(Math.max(0, declaration - 700), declaration);
            assertTrue(javadoc.contains("Reserved"), job + " has no runtime and no reservation");
        }
    }

    // ------------------------------------------------------------ the bard

    @Test
    void theDefaultsAreTheirs() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertEquals(2, profile.bardOnDistance);
        assertEquals(64, profile.bardOffDistance);
        assertTrue(profile.bardHasOffDistance);
        assertTrue(profile.bardJukebox);
        assertFalse(profile.bardLoops);
    }

    @Test
    void aProfileWrittenBeforeBardsExistedLoadsWithThoseDefaults() {
        // Not false-and-zero, which would read as a bard that never plays.
        NpcCombatProfile read = NpcCombatProfile.fromTag(new CompoundTag());
        assertEquals(2, read.bardOnDistance);
        assertEquals(64, read.bardOffDistance);
        assertTrue(read.bardHasOffDistance);
        assertTrue(read.bardJukebox);
    }

    @Test
    void theBardSettingsSurviveASaveAndLoad() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bardSound = "minecraft:music_disc.cat";
        profile.bardJukebox = false;
        profile.bardLoops = true;
        profile.bardOnDistance = 12;
        profile.bardOffDistance = 30;

        NpcCombatProfile read = NpcCombatProfile.fromTag(profile.toTag());
        assertEquals("minecraft:music_disc.cat", read.bardSound);
        assertFalse(read.bardJukebox);
        assertTrue(read.bardLoops);
        assertEquals(12, read.bardOnDistance);
        assertEquals(30, read.bardOffDistance);
    }

    @Test
    void distancesAreBoundedOnRead() {
        NpcCombatProfile profile = new NpcCombatProfile();
        CompoundTag tag = profile.toTag();
        tag.putInt("BardOnDistance", 100_000);
        tag.putInt("BardOffDistance", -50);
        NpcCombatProfile read = NpcCombatProfile.fromTag(tag);
        assertEquals(NpcCombatProfile.MAX_BARD_DISTANCE, read.bardOnDistance);
        assertEquals(0, read.bardOffDistance);
    }

    @Test
    void offDistanceNeverFallsInsideOnDistance() {
        // Two radii exist to stop an NPC stuttering - in, out, in - as a player drifts across a
        // single boundary. An off distance inside the on distance would reintroduce exactly that.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bardOnDistance = 20;
        profile.bardOffDistance = 5;
        profile.bardHasOffDistance = true;
        assertEquals(20.0, NpcBardJob.offDistance(profile));
    }

    @Test
    void withoutASeparateOffDistanceTheOnDistanceIsUsed() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.bardOnDistance = 8;
        profile.bardOffDistance = 64;
        profile.bardHasOffDistance = false;
        assertEquals(8.0, NpcBardJob.offDistance(profile));
    }

    @Test
    void theJobLeavesEveryOtherNpcAlone() throws IOException {
        // It runs from aiStep, which every NPC calls every tick, so the early return is the whole
        // cost for the overwhelming majority that have no job.
        String job = code("src/main/java/net/bullettrain/xenopixelsmod/npc/job",
                "NpcBardJob.java");
        int guard = job.indexOf("!= XenoNpcJob.BARD");
        assertTrue(guard > 0);
        assertTrue(job.indexOf("return;", guard) > guard);
        assertTrue(job.contains("% CHECK_STRIDE != 0"),
                "and a bard does not scan for players every tick either");
    }

    @Test
    void playbackStateIsNeverSaved() throws IOException {
        // "Is somebody near enough to hear this right now" is only true within one server run,
        // and a tick count written to disk is the mistake XenoNpcRespawnData exists to remember.
        String entity = code("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        assertTrue(entity.contains("private boolean bardPlaying"));
        assertFalse(entity.contains("putBoolean(\"BardPlaying\""));
        assertFalse(new NpcCombatProfile().toTag().contains("BardPlaying"));
    }

    @Test
    void aBadSoundIsSilentRatherThanACrash() throws IOException {
        String job = code("src/main/java/net/bullettrain/xenopixelsmod/npc/job",
                "NpcBardJob.java");
        assertTrue(job.contains("sound == null"));
    }

    @Test
    void theEditorPageIsLiveAndItsKeysAreWhitelisted() throws IOException {
        String editor = code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        assertTrue(editor.contains("profile.bardOnDistance = v"));
        assertFalse(editor.contains("disabledToggle(\"Has Off Distance\", true)"),
                "the disabled mirror should be gone");
        String policy = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java");
        for (String key : new String[]{"BardSound", "BardJukebox", "BardLoops", "BardOnDistance",
                "BardHasOffDistance", "BardOffDistance", "Job"}) {
            assertTrue(policy.contains('"' + key + '"'), key + " must be whitelisted to save");
        }
    }
}
