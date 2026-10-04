package net.bullettrain.xenopixelsmod.features.progression;

import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Quest definitions, advancing several at once, hand-in, and the gate that could never open.
 *
 * <p>Covers Tasks 4-7 of the quest-log server plan. The pure rules live in {@code QuestBookTest};
 * this is about the definitions those rules run against and the handlers that drive them.
 */
class QuestLogServerTest {

    private static String source(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static String progression(String file) throws IOException {
        return source("src/main/java/net/bullettrain/xenopixelsmod/features/progression", file);
    }

    // ------------------------------------------------------------ definitions

    @Test
    void aQuestThatDeclaresNoneOfTheNewFieldsIsUnchanged() {
        ParallelQuests.QuestDef def = XenoQuests.parse("plain",
                JsonParser.parseString("{ \"target\": 3 }"));
        assertEquals("", def.category());
        assertEquals("", def.logText());
        assertEquals("", def.completeText());
        assertEquals(QuestCompletionMode.INSTANT, def.completionMode());
        assertEquals("", def.completerNpc());
    }

    @Test
    void allNewFieldsParse() {
        ParallelQuests.QuestDef def = XenoQuests.parse("gold", JsonParser.parseString("""
                {
                  "title": "Gold",
                  "description": "Bring Stephanie something shiny",
                  "target": 10,
                  "category": "Main",
                  "log_text": "Stephanie has been asking around for something shiny.",
                  "complete_text": "I could make some earrings with this.",
                  "completion": "npc",
                  "completer_npc": "Stephanie"
                }
                """));
        assertEquals("Main", def.category());
        assertTrue(def.logText().startsWith("Stephanie has been"));
        assertEquals("I could make some earrings with this.", def.completeText());
        assertEquals(QuestCompletionMode.NPC, def.completionMode());
        assertEquals("Stephanie", def.completerNpc());
    }

    @Test
    void anNpcModeQuestWithNoCompleterFallsBackToInstant() {
        // Otherwise it would reach target and wait forever for an NPC that was never named.
        ParallelQuests.QuestDef def = XenoQuests.parse("bad",
                JsonParser.parseString("{ \"target\": 1, \"completion\": \"npc\" }"));
        assertEquals(QuestCompletionMode.INSTANT, def.completionMode());
    }

    @Test
    void theShippedPackQuestsStillParse() throws IOException {
        // Parsed from their files rather than through ParallelQuests.definition, which needs the
        // datapack loader a unit test has no server to run. Both predate every one of these
        // fields and must come through with the old behaviour.
        for (String id : java.util.List.of("wolf_trouble", "pay_respects")) {
            String json = Files.readString(
                    RepoRoot.of("src/main/resources/data/xenopixelsmod/npcs/quests",
                            id + ".json"), StandardCharsets.UTF_8);
            ParallelQuests.QuestDef def = XenoQuests.parse(id, JsonParser.parseString(json));
            assertEquals(QuestCompletionMode.INSTANT, def.completionMode(), id);
            assertEquals("", def.category(), id);
        }
    }

    @Test
    void theBuiltInQuestsAreStillInstant() {
        // These three are in a static map, so they resolve without a loader.
        for (String id : java.util.List.of("kill_mobs", "kill_players", "dummy_session")) {
            assertEquals(QuestCompletionMode.INSTANT,
                    ParallelQuests.definition(id).completionMode(), id);
        }
    }

    // ------------------------------------------------------------ advancing

    @Test
    void twoQuestsWithTheSameGoalBothAdvance() {
        QuestBook book = new QuestBook();
        book.start("hunt_a", 3);
        book.start("hunt_b", 3);
        for (ActiveQuest quest : book.actives()) {
            quest.addProgress(1);
        }
        assertEquals(1, book.active("hunt_a").progress());
        assertEquals(1, book.active("hunt_b").progress());
    }

    @Test
    void completingOneLeavesTheOthersAlone() {
        QuestBook book = new QuestBook();
        book.start("a", 1);
        book.start("b", 5);
        book.active("b").addProgress(2);
        book.complete("a", 1L);

        assertFalse(book.isActive("a"));
        assertEquals(2, book.active("b").progress(), "the survivor keeps its progress");
    }

    @Test
    void theHandlersLoopOverActivesRatherThanReadingOne() throws IOException {
        String events = progression("ProgressionEvents.java");
        assertFalse(events.contains("data.hasActiveQuest()"),
                "the single-quest read is what this replaced");
        assertFalse(events.contains("data.getQuestId()"));
        assertTrue(events.contains("for (ActiveQuest quest : data.quests().actives())"),
                "each handler should walk every active quest");
    }

    @Test
    void completeQuestTakesAnExplicitId() throws IOException {
        // It used to read the id off the player, which only worked while there was exactly one.
        assertTrue(progression("ProgressionEvents.java").contains(
                "public static void completeQuest(ServerPlayer player, XenoPlayerData data, String id)"));
    }

    // ------------------------------------------------------------ hand-in

    @Test
    void anNpcModeQuestIsMarkedReadyRatherThanCompleted() throws IOException {
        String events = progression("ProgressionEvents.java");
        assertTrue(events.contains("QuestCompletionMode.NPC"),
                "the handler must branch on completion mode");
        assertTrue(events.contains("quest.markReady()"), "and hold the quest open for hand-in");
    }

    @Test
    void aReadyQuestStopsAccumulatingProgress() {
        // Otherwise a quest waiting for hand-in would keep firing completions for kills it will
        // never use.
        ActiveQuest quest = new ActiveQuest("a", 2);
        assertTrue(quest.addProgress(2));
        quest.markReady();
        assertFalse(quest.addProgress(1), "at target, further progress is not a completion");
        assertEquals(2, quest.progress());
    }

    @Test
    void theServerChecksTheNpcMatchesTheCompleter() throws IOException {
        String events = progression("ProgressionEvents.java");
        assertTrue(events.contains("public static boolean tryHandIn("),
                "hand-in should be a server-side method");
        assertTrue(events.contains("completerNpc()"),
                "and must compare against the quest's named completer");
        assertTrue(events.contains("if (!quest.ready())"), "and require the quest to be ready");
    }

    @Test
    void handingInIsTriedBeforeTheNpcSpeaks() throws IOException {
        // Otherwise the NPC would deliver an ambient line instead of taking the quest.
        String entity = source("src/main/java/net/bullettrain/xenopixelsmod/npc",
                "XenoNpcEntity.java");
        int handIn = entity.indexOf("tryHandIn(");
        int speak = entity.indexOf("XenoNpcSpeech.speak(");
        assertTrue(handIn >= 0 && speak >= 0);
        assertTrue(handIn < speak, "hand-in comes first");
    }

    @Test
    void aQuestWhoseCompleterNoLongerExistsStaysAbandonable() {
        // The NPC can be deleted while the quest is ready. The player must not be stuck holding a
        // quest that can never be completed and never be dropped.
        QuestBook book = new QuestBook();
        book.start("gold", 1);
        book.active("gold").addProgress(1);
        book.active("gold").markReady();
        book.abandon("gold");
        assertFalse(book.isActive("gold"));
    }

    @Test
    void aReadyQuestPaysOnlyOnce() {
        QuestBook book = new QuestBook();
        book.start("gold", 1);
        book.active("gold").markReady();
        book.complete("gold", 5L);
        assertFalse(book.isActive("gold"),
                "it leaves the active list, so a second hand-in finds nothing");
        assertTrue(book.hasCompleted("gold"));
    }

    // ------------------------------------------------------------ the dead gate

    @Test
    void theGateReadsCompletedRatherThanTheCurrentQuest() throws IOException {
        // check() required the requirement's id to equal the CURRENT quest id AND progress to be at
        // target. Reaching target ran completeQuest, which blanked the id. So the gate failed while
        // the quest was unfinished and failed again the moment it finished - a master gated on a
        // quest was locked forever, silently.
        String gate = progression("MasterPrerequisites.java");
        assertTrue(gate.contains("hasCompleted("), "the gate should ask whether it was finished");
        assertFalse(gate.contains("data.getQuestId()"),
                "comparing against the current quest is the bug this fixes");
        assertFalse(gate.contains("data.getQuestProgress()"));
    }

    @Test
    void aFinishedQuestSatisfiesTheGateAndAnUnfinishedOneDoesNot() {
        QuestBook book = new QuestBook();
        book.start("trial", 5);
        book.active("trial").addProgress(3);
        assertFalse(book.hasCompleted("trial"), "part-way through is not finished");

        book.active("trial").addProgress(2);
        book.complete("trial", 10L);
        assertTrue(book.hasCompleted("trial"), "this is what the gate now reads");
    }

    // ------------------------------------------------------------ commands

    @Test
    void abortTakesAnIdAndRefusesOneThePlayerIsNotOn() throws IOException {
        // The old command took no argument. With several quests it would have had to guess which
        // to drop, and guessing wrong silently discards progress.
        String commands = source("src/main/java/net/bullettrain/xenopixelsmod/command",
                "ProgressionCommands.java");
        assertTrue(commands.contains("questAbort(CommandSourceStack src, String id)"),
                "abort should name a quest");
        assertTrue(commands.contains("You are not on that quest."),
                "and say so rather than passing silently");
        assertFalse(commands.contains("data.clearQuest()"), "the old blanket abort is gone");
    }

    @Test
    void thereIsAWayToSeeWhatWasCompleted() throws IOException {
        assertTrue(source("src/main/java/net/bullettrain/xenopixelsmod/command",
                "ProgressionCommands.java").contains("questLog(CommandSourceStack src)"));
    }
}
