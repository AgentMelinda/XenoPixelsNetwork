package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.npc.importer.QuestImport;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "Give XenoSkill points" and "Enable reward actions" toggles, end to end.
 *
 * <p>The bug this pins down: a quest switched off in the editor still minted skill points when it
 * was finished. The fix has three moving parts and a test for each, plus the seams between them:
 *
 * <ol>
 *   <li>the editor writes {@code reward.skill_points = 0} (and an empty {@code commands} array)
 *       rather than leaving the block out, which the toggle-off parse test below reproduces;
 *   <li>the store round-trip keeps that zero, so a re-save cannot fall back to the legacy default;
 *   <li>the completion decision zeroes declared points for every quest that was not offered by a
 *       native Xeno NPC.
 * </ol>
 *
 * <p>Everything here is the pure decision layer: no server, no player, no entity. The one thing
 * that cannot be built headless is the giver flag itself, which is set from an {@code Entity};
 * {@link ActiveQuest#load} reads the same NBT field a real Xeno NPC hand-off writes, so a tag
 * stands in for the entity without pretending to be one.
 */
class QuestRewardToggleTest {

    /** A quest as the editor saves it with both reward toggles OFF. */
    private static final String TOGGLE_OFF_QUEST = """
            {
              "title": "Sweep the yard",
              "description": "No reward, on purpose.",
              "log_text": "Kill 3 zombies.",
              "target": 3,
              "objective": "kill_mobs",
              "parameter": "minecraft:zombie",
              "category": "quests",
              "complete_text": "",
              "repeat": "NONE",
              "random_reward": false,
              "reward": { "skill_points": 0, "commands": [] }
            }
            """;

    /** The same quest with both toggles ON: two points and one reward action. */
    private static final String TOGGLE_ON_QUEST = """
            {
              "title": "Sweep the yard",
              "target": 3,
              "objective": "kill_mobs",
              "parameter": "minecraft:zombie",
              "reward": { "skill_points": 2, "commands": ["say {player} is done"] }
            }
            """;

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    private static ParallelQuests.QuestDef parse(String text) {
        return XenoQuests.parse("toggle_test", json(text));
    }

    /**
     * A stored quest as it would be after being offered by a native Xeno NPC. {@code setGiver} needs
     * a live entity; the persisted flag it writes is what the completion path actually reads.
     */
    private static ActiveQuest storedQuest(boolean xenoNpcGiver) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", "toggle_test");
        tag.putInt("Target", 3);
        tag.putBoolean("XenoNpcGiver", xenoNpcGiver);
        ActiveQuest quest = ActiveQuest.load(tag);
        assertEquals(xenoNpcGiver, quest.xenoNpcGiver(),
                "the giver flag must survive the load that a restart performs");
        return quest;
    }

    // ------------------------------------------------------------------ parsing

    @Test
    void aRewardThatDeclaresZeroPointsParsesAsZero() {
        QuestReward reward = QuestReward.fromJson(json("{ \"skill_points\": 0 }"));
        assertEquals(0, reward.skillPoints(),
                "an explicit zero must not be read as the two-point default");
    }

    @Test
    void aRewardThatDeclaresZeroPointsKeepsEverythingElseItDeclares() {
        QuestReward reward = QuestReward.fromJson(json("""
                { "skill_points": 0, "experience": 40,
                  "items": [ { "id": "minecraft:diamond", "count": 2 } ],
                  "faction_points": [ { "faction": "guards", "points": 25 } ],
                  "commands": ["say {player} is done"] }
                """));
        assertEquals(0, reward.skillPoints());
        assertEquals(40, reward.experience());
        assertEquals(List.of(new QuestReward.ItemGrant("minecraft:diamond", 2)), reward.items());
        assertEquals(List.of(new QuestReward.FactionGrant("guards", 25)), reward.factionPoints());
        assertEquals(1, reward.commands().size());
    }

    @Test
    void aRewardBlockThatOmitsSkillPointsStillPaysTheLegacyTwo() {
        // Deliberate, and load-bearing for every shipped pack: an absent member means "the reward
        // every quest has always paid", not "nothing". A toggle-off quest is saved with an explicit
        // zero, so this fallback can only be reached by content that never had the toggle. If this
        // assertion ever needs changing, that is a content decision, not a side effect.
        assertEquals(2, QuestReward.fromJson(json("{ \"experience\": 10 }")).skillPoints());
        assertEquals(2, QuestReward.fromJson(json("{}")).skillPoints());
        assertEquals(2, QuestReward.fromJson(json("null")).skillPoints());
        assertEquals(2, QuestReward.fromJson(null).skillPoints());
    }

    @Test
    void aToggleOffQuestDefinitionParsesToZeroPointsAndNoActions() {
        ParallelQuests.QuestDef def = parse(TOGGLE_OFF_QUEST);
        assertEquals(0, def.reward().skillPoints(),
                "a quest created with the XenoSkill toggle off must carry zero points");
        assertTrue(def.reward().commands().isEmpty(),
                "a quest created with the reward-action toggle off must carry no commands");
        assertEquals("Sweep the yard", def.title());
        assertEquals(3, def.target(), "only the reward changed; the quest itself still parses");
    }

    @Test
    void aToggleOnQuestDefinitionParsesToTheDeclaredPointsAndAction() {
        ParallelQuests.QuestDef def = parse(TOGGLE_ON_QUEST);
        assertEquals(2, def.reward().skillPoints());
        assertEquals(List.of("say {player} is done"), def.reward().commands());
    }

    // -------------------------------------------------------------- round trips

    @Test
    void theStoreRoundTripPreservesAToggleOffReward() {
        // The leak this guards: a writer that drops the reward object would send the quest back to
        // the DEFAULT of two points, and the toggle would look like it had stopped working.
        ParallelQuests.QuestDef def = parse(TOGGLE_OFF_QUEST);
        String written = QuestImport.toJson(def);
        assertTrue(written.contains("\"skill_points\":0"),
                "the serialized quest must say zero out loud, not stay silent: " + written);

        ParallelQuests.QuestDef reread = XenoQuests.parse("toggle_test", json(written));
        assertEquals(0, reread.reward().skillPoints(),
                "a toggle-off quest must still pay zero after being stored and loaded again");
        assertTrue(reread.reward().commands().isEmpty());
        assertEquals(def.title(), reread.title());
        assertEquals(def.target(), reread.target());
    }

    @Test
    void theStoreRoundTripPreservesDeclaredPointsAndActions() {
        ParallelQuests.QuestDef reread = XenoQuests.parse("toggle_test",
                json(QuestImport.toJson(parse(TOGGLE_ON_QUEST))));
        assertEquals(2, reread.reward().skillPoints());
        assertEquals(List.of("say {player} is done"), reread.reward().commands());
    }

    @Test
    void theGiverFlagSurvivesAPlayerDataSaveAndDefaultsToFalse() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", "toggle_test");
        tag.putInt("Target", 3);
        tag.putBoolean("XenoNpcGiver", true);
        ActiveQuest reloaded = ActiveQuest.load(ActiveQuest.load(tag).save());
        assertTrue(reloaded.xenoNpcGiver(),
                "a quest offered by a Xeno NPC must not lose its points when the player logs out");

        // Legacy and migrated saves predate the flag; a missing key must read as "no native giver",
        // which is the direction that cannot mint points.
        CompoundTag legacy = new CompoundTag();
        legacy.putString("Id", "toggle_test");
        legacy.putInt("Target", 3);
        assertFalse(ActiveQuest.load(legacy).xenoNpcGiver());
    }

    // ------------------------------------------------------------- origin policy

    @Test
    void theOriginPolicyZeroesPointsForEveryQuestWithoutANativeGiver() {
        QuestReward declared = QuestReward.ofSkillPoints(2);
        assertEquals(0, QuestRewardOriginPolicy.forCompletion(declared, false).skillPoints(),
                "a command-started or third-party quest cannot mint XenoSkill points");
        assertEquals(2, QuestRewardOriginPolicy.forCompletion(declared, true).skillPoints(),
                "a native Xeno NPC quest keeps what it declared");
        assertEquals(0, QuestRewardOriginPolicy.forCompletion(QuestReward.ofSkillPoints(0), true)
                .skillPoints(), "a declared zero stays zero");
    }

    @Test
    void theOriginPolicyPaysNothingForAnUnresolvedDefinition() {
        QuestReward paid = QuestRewardOriginPolicy.forCompletion(null, true);
        assertTrue(paid.isEmpty());
        assertEquals(0, paid.skillPoints(),
                "a quest whose definition is gone must not fall back to the legacy two");
    }

    @Test
    void theOriginPolicyLeavesTheOtherRewardKindsAlone() {
        // Documented semantics of the two toggles: "Give XenoSkill points" gates only skill points,
        // "Enable reward actions" gates only the commands, and the editor writes an empty command
        // list for the second one. The policy itself never resurrects anything and never strips the
        // vanilla half of a reward, so a third-party quest can still pay experience, items, faction
        // standing and its declared actions.
        QuestReward declared = new QuestReward(2, 40,
                List.of(new QuestReward.ItemGrant("minecraft:diamond", 1)),
                List.of("say {player} is done"),
                List.of(new QuestReward.FactionGrant("guards", 25)));
        QuestReward paid = QuestRewardOriginPolicy.forCompletion(declared, false);
        assertEquals(0, paid.skillPoints());
        assertEquals(40, paid.experience());
        assertEquals(declared.items(), paid.items());
        assertEquals(declared.commands(), paid.commands(),
                "reward actions are the other toggle's business, not the origin policy's");
        assertEquals(declared.factionPoints(), paid.factionPoints());
    }

    // ------------------------------------------------------- the payment decision

    @Test
    void rewardToPayPaysNothingForAToggleOffQuestEvenFromANativeGiver() {
        QuestReward paid = ProgressionEvents.rewardToPay(parse(TOGGLE_OFF_QUEST), storedQuest(true));
        assertEquals(0, paid.skillPoints(),
                "the reported bug: a toggle-off quest finished through a Xeno NPC paid 2 points");
        assertTrue(paid.commands().isEmpty(), "with reward actions off there is nothing to run");
        assertTrue(paid.isEmpty());
        assertEquals("nothing", paid.describe(),
                "the completion message must not claim points that were never paid");
    }

    @Test
    void rewardToPayZeroesDeclaredPointsForAQuestWithNoNativeGiver() {
        QuestReward paid = ProgressionEvents.rewardToPay(parse(TOGGLE_ON_QUEST), storedQuest(false));
        assertEquals(0, paid.skillPoints());
        assertEquals(List.of("say {player} is done"), paid.commands(),
                "the reward action belongs to the other toggle and stays declared");
    }

    @Test
    void rewardToPayPaysDeclaredPointsToANativeGiverQuest() {
        QuestReward paid = ProgressionEvents.rewardToPay(parse(TOGGLE_ON_QUEST), storedQuest(true));
        assertEquals(2, paid.skillPoints(),
                "quests that legitimately declare points must keep being paid");
        assertEquals(List.of("say {player} is done"), paid.commands());
    }

    @Test
    void rewardToPayPaysNothingWhenTheQuestIsGoneOrUnstarted() {
        assertTrue(ProgressionEvents.rewardToPay(null, storedQuest(true)).isEmpty(),
                "an unknown quest id must not pay the legacy default");
        QuestReward unstarted = ProgressionEvents.rewardToPay(parse(TOGGLE_ON_QUEST), null);
        assertEquals(0, unstarted.skillPoints(),
                "no stored giver means no native giver, which means no points");
        assertEquals(List.of("say {player} is done"), unstarted.commands(),
                "but the declared reward action is still the other toggle's to allow or not");
    }

    @Test
    void theRandomRewardBranchIsGatedTheSameWay() {
        // rewardToPay has two exits; a leak in only one of them would pass a test that checks the
        // other. Both must apply the origin policy.
        ParallelQuests.QuestDef random = XenoQuests.parse("toggle_random", json("""
                { "title": "Random", "target": 1, "random_reward": true,
                  "reward": { "skill_points": 2,
                    "items": [ { "id": "minecraft:diamond" }, { "id": "minecraft:gold_ingot" },
                               { "id": "minecraft:iron_ingot" } ] } }
                """));
        QuestReward paid = ProgressionEvents.rewardToPay(random, storedQuest(false));
        assertEquals(0, paid.skillPoints());
        assertEquals(1, paid.items().size(), "one of the three items is still picked");

        QuestReward nativePaid = ProgressionEvents.rewardToPay(random, storedQuest(true));
        assertEquals(2, nativePaid.skillPoints());
        assertEquals(1, nativePaid.items().size());
    }

    // ------------------------------------------------------------- source guards

    @Test
    void theCompletionPathPaysOnlyWhatThePolicyAllows() throws IOException {
        String events = progressionSource("ProgressionEvents.java");
        assertTrue(events.contains("QuestReward reward = rewardToPay(def, activeQuest);"),
                "completeQuest must pay the policy-adjusted reward, not the raw definition's");
        assertTrue(events.contains("if (reward.skillPoints() > 0)"),
                "skill points are granted through the guarded reward, not the definition");
        assertFalse(events.contains("addSkillPoints(2)"),
                "no hard-coded quest skill points may come back to the completion path");
        assertTrue(events.contains("new QuestReward(0, 0, def.mail().items()"),
                "mail items are paid as items, never as points");

        String policy = progressionSource("QuestRewardOriginPolicy.java");
        assertTrue(policy.contains("xenoNpcGiver ? declared.skillPoints() : 0"),
                "the origin gate is the only thing that decides whether points exist");
    }

    @Test
    void theEditorWritesAnExplicitZeroForAToggleOffQuest() throws IOException {
        // The writer half of the contract, and the reason a toggle-off quest cannot drift back to
        // two points: an absent "skill_points" member means the legacy default, so the editor has to
        // say zero out loud. Reading the file is the only way to check a click without a client.
        String editor = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                        "XenoNpcEditorScreen.java"),
                StandardCharsets.UTF_8);
        assertTrue(editor.contains("addProperty(\"skill_points\""),
                "saving a quest must always write reward.skill_points, never omit it");
        // And only where XenoSkill points exist: XenoNPCs writes zero (QuestSkillPointRewardTest).
        assertTrue(editor.contains("questSkillPointsEnabled && xenoSkillPointsOffered() ? questSkillPoints : 0"),
                "the XenoSkill toggle is what decides the value that gets written");
        assertTrue(editor.contains("if (questRewardActionsEnabled"),
                "the reward-action toggle is what decides whether a command is saved");
    }

    private static String progressionSource(String file) throws IOException {
        return Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/features/progression", file),
                StandardCharsets.UTF_8);
    }
}
