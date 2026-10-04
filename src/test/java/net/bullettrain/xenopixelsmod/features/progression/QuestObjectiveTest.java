package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quests say what they want, instead of the event handler switching on their id.
 *
 * <p>{@code ProgressionEvents.onDeath} used to read {@code case "kill_mobs"},
 * {@code case "kill_players"}, and a {@code default} that counted any non-player death. So the
 * three quests that existed were the only three that could: a fourth meant editing the handler, and
 * any quest whose id the switch did not know silently became "kill anything". That last part is the
 * dangerous half - it does not fail, it quietly finishes the wrong quest.
 */
class QuestObjectiveTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void aTypeThatIsNamedButUnknownIsRejectedRatherThanGuessedAt() {
        assertNull(QuestObjective.parse("teleport_to_the_moon"));
        assertNull(QuestObjective.parse(""));
        assertNull(QuestObjective.parse(null));
        assertEquals(QuestObjective.KILL_TYPE, QuestObjective.parse("  Kill_Type "));
    }

    @Test
    void dummyGoalsConvertAHitIntoProgressAndOthersIntoNone() {
        assertEquals(12, new QuestObjective.Goal(QuestObjective.DUMMY_DAMAGE).dummyProgress(11.6f));
        assertEquals(1, new QuestObjective.Goal(QuestObjective.DUMMY_HITS).dummyProgress(200f));
        assertEquals(0, new QuestObjective.Goal(QuestObjective.KILL_MOBS).dummyProgress(50f));
    }

    @Test
    void aDamageGoalNeverScoresZeroForAHitThatLanded() {
        // A hit worth no progress reads as the quest being broken.
        assertEquals(1, new QuestObjective.Goal(QuestObjective.DUMMY_DAMAGE).dummyProgress(0.1f));
    }

    @Test
    void talkGoalsMatchNameOrRoleAndABlankParameterMatchesAnyNpc() {
        QuestObjective.Goal any = new QuestObjective.Goal(QuestObjective.TALK_TO_NPC);
        assertTrue(any.countsTalk("Anyone", "villager"));

        QuestObjective.Goal masters =
                new QuestObjective.Goal(QuestObjective.TALK_TO_NPC, "Master");
        assertTrue(masters.countsTalk("Someone Else", "master"), "matches the role id");
        assertTrue(masters.countsTalk("master", "trader"), "and the name");
        assertFalse(masters.countsTalk("Bulma", "trader"));
    }

    @Test
    void aNonTalkGoalIsNeverAdvancedByTalking() {
        assertFalse(new QuestObjective.Goal(QuestObjective.KILL_MOBS).countsTalk("x", "y"));
    }

    @Test
    void aKillTypeWithAnUnparseableParameterMatchesNothing() {
        // The old default branch would have counted it as any kill. Matching nothing is the right
        // failure: the quest visibly does not advance rather than finishing on the wrong mob.
        assertFalse(new QuestObjective.Goal(QuestObjective.KILL_TYPE, "not a resource location")
                .countsKill(null, false));
    }

    @Test
    void namedNpcKillTargetsMatchTheVisibleNameIgnoringCaseAndOuterWhitespace() {
        QuestObjective.Goal goal = new QuestObjective.Goal(
                QuestObjective.KILL_NPC, "  Master Roshi the Turtle Hermit  ");

        assertTrue(goal.matchesNpcName("Master Roshi the Turtle Hermit"));
        assertTrue(goal.matchesNpcName("MASTER ROSHI THE TURTLE HERMIT"));
        assertFalse(goal.matchesNpcName("Master Roshi"));
        assertFalse(goal.matchesNpcName("Master Roshi the Turtle Hermit II"));
    }

    @Test
    void theBuiltInQuestsDeclareWhatTheyAlwaysMeant() {
        assertEquals(QuestObjective.KILL_MOBS,
                ParallelQuests.goalFor("kill_mobs").type());
        assertEquals(QuestObjective.KILL_PLAYERS,
                ParallelQuests.goalFor("kill_players").type());
        assertEquals(QuestObjective.DUMMY_DAMAGE,
                ParallelQuests.goalFor("dummy_session").type());
    }

    @Test
    void anUnknownQuestHasNoGoalRatherThanADefaultOne() {
        // Null is what the handlers treat as "not one of ours". A default goal here would bring
        // back exactly the behaviour this replaced.
        assertNull(ParallelQuests.goalFor("something_a_pack_removed"));
        assertNull(ParallelQuests.goalFor(""));
        assertNull(ParallelQuests.goalFor(null));
    }

    @Test
    void theHandlersAskTheQuestInsteadOfSwitchingOnItsId() throws IOException {
        String events = source("features/progression/ProgressionEvents.java");
        assertFalse(events.contains("case \"kill_mobs\""),
                "the id switch is what this replaced");
        assertFalse(events.contains("\"dummy_session\".equals"),
                "and so is the hardcoded dummy quest");
        // The call moved from the player's single quest to each active quest when a player gained
        // the ability to hold several. What it asks is unchanged: the quest's own goal.
        assertTrue(events.contains("ParallelQuests.goalFor(quest.id())"),
                "every handler should ask the quest what it wants");
        assertTrue(events.contains("getDirectEntity() instanceof ServerPlayer directPlayer"),
                "direct player damage must receive kill credit too");
        assertTrue(source("features/progression/QuestObjective.java")
                        .contains("QuestNpcTarget.matches(victim, parameter)"),
                "NPC goals must match supported NPC families by their saved visible name");
    }

    @Test
    void aPackQuestParsesWithItsObjectiveAndReward() {
        ParallelQuests.QuestDef def = XenoQuests.parse("wolf_trouble", JsonParser.parseString("""
                {
                  "title": "Wolf Trouble",
                  "description": "Drive off 5 wolves",
                  "target": 5,
                  "objective": "kill_type",
                  "parameter": "minecraft:wolf",
                  "reward": {
                    "skill_points": 1,
                    "experience": 40,
                    "items": [ { "id": "minecraft:cooked_beef", "count": 4 } ],
                    "faction_points": [ { "faction": "guards", "points": 25 } ]
                  }
                }
                """));
        assertEquals("Wolf Trouble", def.title());
        assertEquals(5, def.target());
        assertEquals(QuestObjective.KILL_TYPE, def.goal().type());
        assertEquals("minecraft:wolf", def.goal().parameter());
        assertEquals(1, def.reward().skillPoints());
        assertEquals(40, def.reward().experience());
        assertEquals(4, def.reward().items().get(0).count());
        assertEquals("guards", def.reward().factionPoints().get(0).factionId());
        assertEquals(25, def.reward().factionPoints().get(0).points());
    }

    @Test
    void aQuestThatDeclaresNoRewardPaysExactlyWhatItAlwaysDid() {
        ParallelQuests.QuestDef def = XenoQuests.parse("plain",
                JsonParser.parseString("{ \"target\": 3 }"));
        assertEquals(QuestReward.DEFAULT.skillPoints(), def.reward().skillPoints());
        assertEquals(QuestObjective.KILL_MOBS, def.goal().type());
    }

    @Test
    void aQuestNamingAnUnknownObjectiveFailsToLoadRatherThanCountingTheWrongThing() {
        assertThrows(IllegalArgumentException.class, () -> XenoQuests.parse("bad",
                JsonParser.parseString("{ \"objective\": \"befriend_a_wolf\" }")));
    }

    @Test
    void killTypeWithoutAParameterIsRefused() {
        // It would match nothing and be unfinishable, which is worse than refusing to load it.
        assertThrows(IllegalArgumentException.class, () -> XenoQuests.parse("bad",
                JsonParser.parseString("{ \"objective\": \"kill_type\", \"target\": 2 }")));
    }

    @Test
    void aTargetBelowOneIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> XenoQuests.parse("bad",
                JsonParser.parseString("{ \"target\": 0 }")));
    }

    @Test
    void theShippedPackQuestsParse() throws IOException {
        Path dir = RepoRoot.of("src/main/resources/data/xenopixelsmod/npcs/quests");
        assertTrue(Files.isDirectory(dir), "the shipped quests should exist");
        try (var files = Files.list(dir)) {
            List<Path> jsons = files.filter(f -> f.toString().endsWith(".json")).toList();
            assertFalse(jsons.isEmpty(), "at least one shipped quest");
            for (Path file : jsons) {
                String id = file.getFileName().toString().replace(".json", "");
                ParallelQuests.QuestDef def = XenoQuests.parse(id,
                        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)));
                assertNotNull(def.goal(), id + " should declare a goal");
            }
        }
    }

    @Test
    void talkingToTheSameNpcTwiceCountsOnce() {
        // "Speak to three masters" has to mean three masters. Without this it would mean clicking
        // one master three times, which reads as the same quest and is not.
        //
        // The visited set moved from the player onto each quest when a player gained the ability to
        // hold several: one shared set would have let two TALK_TO_NPC quests consume each other's
        // entries. The invariant is unchanged; only its owner is.
        XenoPlayerData data = new XenoPlayerData();
        data.quests().start("pay_respects", 3);
        ActiveQuest quest = data.quests().active("pay_respects");
        assertTrue(quest.markVisited("npc-a"));
        assertFalse(quest.markVisited("npc-a"));
        assertTrue(quest.markVisited("npc-b"));
        assertTrue(quest.hasVisited("npc-a"));
    }

    @Test
    void whatWasVisitedNeverOutlivesTheQuest() {
        XenoPlayerData data = new XenoPlayerData();
        data.quests().start("pay_respects", 3);
        data.quests().active("pay_respects").markVisited("npc-a");

        data.quests().complete("pay_respects", 1L);
        data.quests().start("pay_respects", 3);
        assertTrue(data.quests().active("pay_respects").markVisited("npc-a"),
                "a fresh run starts from nothing");
    }

    @Test
    void whatWasVisitedSurvivesASaveAndLoad() {
        // Otherwise a relog would reset a half-finished "speak to three" quest's memory and let
        // the same NPC count again.
        XenoPlayerData data = new XenoPlayerData();
        data.quests().start("pay_respects", 3);
        data.quests().active("pay_respects").markVisited("npc-a");

        CompoundTag tag = new CompoundTag();
        data.saveNBT(tag);
        XenoPlayerData reloaded = new XenoPlayerData();
        reloaded.loadNBT(tag);

        ActiveQuest quest = reloaded.quests().active("pay_respects");
        assertTrue(quest.hasVisited("npc-a"));
        assertFalse(quest.markVisited("npc-a"));
    }
}
