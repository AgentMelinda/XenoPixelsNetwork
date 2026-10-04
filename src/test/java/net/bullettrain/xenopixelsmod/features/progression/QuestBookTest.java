package net.bullettrain.xenopixelsmod.features.progression;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The player's quests — many at once, and the ones they have finished.
 *
 * <p>Before this, a player held exactly one quest and finishing it erased every trace. That second
 * part is why {@code MasterPrerequisites} gates could never pass: the gate wants a quest id that is
 * both matched and complete, and completion blanked the id.
 */
class QuestBookTest {

    @Test
    void manyQuestsRunAtOnce() {
        QuestBook book = new QuestBook();
        assertNull(book.start("kill_mobs", 10));
        assertNull(book.start("wolf_trouble", 5));
        assertEquals(2, book.actives().size());
        assertTrue(book.isActive("kill_mobs"));
        assertTrue(book.isActive("wolf_trouble"));
    }

    @Test
    void startingOneAlreadyRunningIsRefused() {
        QuestBook book = new QuestBook();
        book.start("kill_mobs", 10);
        assertNotNull(book.start("kill_mobs", 10), "a duplicate should say why, not silently reset");
    }

    @Test
    void theActiveCapHolds() {
        QuestBook book = new QuestBook();
        for (int i = 0; i < QuestBook.MAX_ACTIVE; i++) {
            assertNull(book.start("q" + i, 1));
        }
        assertNotNull(book.start("one_too_many", 1));
        assertEquals(QuestBook.MAX_ACTIVE, book.actives().size());
    }

    @Test
    void progressIsPerQuest() {
        QuestBook book = new QuestBook();
        book.start("a", 3);
        book.start("b", 3);
        book.active("a").addProgress(2);
        assertEquals(2, book.active("a").progress());
        assertEquals(0, book.active("b").progress(), "quests must not share a counter");
    }

    @Test
    void visitedSetsAreNotShared() {
        // The whole reason questVisited moved inside each quest. A TALK_TO_NPC quest and a kill
        // quest running together must not consume each other's visited entries.
        QuestBook book = new QuestBook();
        book.start("talk", 3);
        book.start("kill", 3);
        assertTrue(book.active("talk").markVisited("npc-1"));
        assertFalse(book.active("talk").markVisited("npc-1"), "the same NPC counts once");
        assertTrue(book.active("kill").markVisited("npc-1"), "but only for that quest");
    }

    @Test
    void completingMovesItAndRemembersWhen() {
        QuestBook book = new QuestBook();
        book.start("hunt", 1);
        book.complete("hunt", 1234L);
        assertFalse(book.isActive("hunt"));
        assertTrue(book.hasCompleted("hunt"));
        assertEquals(1234L, book.completedAt("hunt"));
    }

    @Test
    void aCompletedQuestCanBeStartedAgain() {
        // Repeat rules are out of scope, so nothing should block a second run. Silently refusing
        // would read as a bug rather than as a rule.
        QuestBook book = new QuestBook();
        book.start("hunt", 1);
        book.complete("hunt", 1L);
        assertNull(book.start("hunt", 1), "history must not block a restart");
        assertTrue(book.isActive("hunt"));
        assertTrue(book.hasCompleted("hunt"), "and the history survives the restart");
    }

    @Test
    void theCompletedCapEvictsOldest() {
        QuestBook book = new QuestBook();
        for (int i = 0; i < QuestBook.MAX_COMPLETED + 10; i++) {
            book.start("q" + i, 1);
            book.complete("q" + i, i);
        }
        assertEquals(QuestBook.MAX_COMPLETED, book.completed().size());
        assertFalse(book.hasCompleted("q0"), "the oldest goes first");
        assertTrue(book.hasCompleted("q" + (QuestBook.MAX_COMPLETED + 9)));
    }

    @Test
    void everythingSurvivesASaveAndLoad() {
        QuestBook book = new QuestBook();
        book.start("a", 5);
        book.active("a").addProgress(2);
        book.active("a").markVisited("npc-1");
        book.start("b", 2);
        book.active("b").markReady();
        book.start("c", 1);
        book.complete("c", 99L);

        CompoundTag tag = new CompoundTag();
        book.saveTo(tag);
        QuestBook reloaded = new QuestBook();
        reloaded.loadFrom(tag);

        assertEquals(2, reloaded.actives().size());
        assertEquals(2, reloaded.active("a").progress());
        assertTrue(reloaded.active("a").hasVisited("npc-1"));
        assertTrue(reloaded.active("b").ready());
        assertTrue(reloaded.hasCompleted("c"));
        assertEquals(99L, reloaded.completedAt("c"));
    }

    @Test
    void aQuestWhoseDefinitionVanishedStillLoadsAndCanBeAbandoned() {
        // A pack can remove a quest between sessions. The book knows nothing about definitions, so
        // the entry must survive as data and stay abortable rather than wedging the player.
        QuestBook book = new QuestBook();
        book.start("removed_by_a_pack", 4);
        CompoundTag tag = new CompoundTag();
        book.saveTo(tag);

        QuestBook reloaded = new QuestBook();
        reloaded.loadFrom(tag);
        assertTrue(reloaded.isActive("removed_by_a_pack"));
        reloaded.abandon("removed_by_a_pack");
        assertFalse(reloaded.isActive("removed_by_a_pack"));
    }

    @Test
    void loadReplacesRatherThanMerges() {
        QuestBook book = new QuestBook();
        book.start("stale", 1);
        book.loadFrom(new CompoundTag());
        assertFalse(book.isActive("stale"), "loading an empty tag must clear, not keep");
    }
}
