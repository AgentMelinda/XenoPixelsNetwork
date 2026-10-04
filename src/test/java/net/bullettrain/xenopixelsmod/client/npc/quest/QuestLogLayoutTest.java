package net.bullettrain.xenopixelsmod.client.npc.quest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Grouping active quests into category buttons, and ordering those buttons the way a person reads. */
class QuestLogLayoutTest {

    private static ClientQuests.Entry quest(String id, String category) {
        return new ClientQuests.Entry(id, id, category, "", 0, 1, false, "");
    }

    @Test
    void sideTenSortsAfterSideTwo() {
        // Lexicographic order puts "Side 10" before "Side 2", which is the single most visible
        // wrongness a finished quest log can have. The spec calls for natural sort by name.
        List<String> categories = QuestLogLayout.categories(List.of(
                quest("a", "Side 10"), quest("b", "Side 2"), quest("c", "Side 1")));
        assertEquals(List.of("Side 1", "Side 2", "Side 10"), categories);
    }

    @Test
    void naturalSortHandlesNumbersThatAreNotAtTheEnd() {
        assertTrue(QuestLogLayout.naturalCompare("Chapter 2 - Dawn", "Chapter 10 - Dusk") < 0);
    }

    @Test
    void naturalSortIsCaseInsensitive() {
        // Two categories differing only in case are one category to a reader; a case-sensitive
        // tie-break would flip their order between openings of the screen.
        assertEquals(0, QuestLogLayout.naturalCompare("main", "Main"));
    }

    @Test
    void leadingZerosDoNotChangeTheOrder() {
        // "Side 02" and "Side 2" are the same chapter to a reader.
        assertEquals(0, QuestLogLayout.naturalCompare("Side 02", "Side 2"));
    }

    @Test
    void aVeryLongNumberDoesNotOverflow() {
        // Compared by digit-run length rather than parsed, so a category named with something
        // longer than an int survives instead of throwing.
        assertTrue(QuestLogLayout.naturalCompare("q 99999999999999999999", "q 3") > 0);
    }

    @Test
    void aQuestWithNoCategoryGetsTheGeneralOneAndItComesFirst() {
        // "category" is optional in the quest JSON, and every quest predating the field has none.
        // They must still be reachable, and should not be buried under named chapters.
        List<String> categories = QuestLogLayout.categories(List.of(
                quest("a", "Side"), quest("b", "")));
        assertEquals(List.of(QuestLogLayout.GENERAL, "Side"), categories);
    }

    @Test
    void eachCategoryAppearsOnce() {
        assertEquals(List.of("Main"), QuestLogLayout.categories(List.of(
                quest("a", "Main"), quest("b", "Main"), quest("c", "Main"))));
    }

    @Test
    void inCategoryKeepsServerOrderWithin() {
        // Quests inside a category stay in the order the server sent, which is start order. A
        // second sort here would reorder a list the player has already learned.
        List<ClientQuests.Entry> all = List.of(
                quest("c", "Main"), quest("a", "Main"), quest("b", "Side"));
        assertEquals(List.of("c", "a"),
                QuestLogLayout.inCategory(all, "Main").stream()
                        .map(ClientQuests.Entry::id).toList());
    }

    @Test
    void inCategoryFindsTheUncategorised() {
        List<ClientQuests.Entry> all = List.of(quest("a", ""), quest("b", "Main"));
        assertEquals(List.of("a"),
                QuestLogLayout.inCategory(all, QuestLogLayout.GENERAL).stream()
                        .map(ClientQuests.Entry::id).toList());
    }

    @Test
    void noQuestsMeansNoCategories() {
        assertTrue(QuestLogLayout.categories(List.of()).isEmpty());
    }

    @Test
    void questListsScrollWithinTheirVisibleRows() {
        assertEquals(1, QuestLogLayout.scrollOffset(0, -1.0, 20, 5));
        assertEquals(0, QuestLogLayout.scrollOffset(0, 1.0, 20, 5));
        assertEquals(15, QuestLogLayout.scrollOffset(19, -1.0, 20, 5));
        assertEquals(0, QuestLogLayout.scrollOffset(0, 0.0, 20, 5));
    }

    @Test
    void viewportReturnsOnlyRowsThatFit() {
        assertEquals(List.of("c", "d", "e"),
                QuestLogLayout.visibleSlice(List.of("a", "b", "c", "d", "e"), 2, 3));
        assertEquals(List.of("d", "e"),
                QuestLogLayout.visibleSlice(List.of("a", "b", "c", "d", "e"), 3, 8));
    }

    @Test
    void questPanelDocksToTheLeftOfInventoryTabs() {
        assertEquals(499, QuestLogLayout.panelLeft(785));
        assertEquals(4, QuestLogLayout.panelLeft(200));
    }

    // ------------------------------------------------------------ paging

    @Test
    void emptyLogTextIsOnePageNotZero() {
        // A quest with no journal entry must still show its objectives. Zero pages would mean the
        // page indicator read "1 / 0" and the body drew nothing at all.
        assertEquals(1, QuestLogLayout.paginate(List.of(), 5).size());
        assertTrue(QuestLogLayout.paginate(List.of(), 5).get(0).isEmpty());
    }

    @Test
    void theLastPageKeepsTheTail() {
        // Seven lines at three per page is three pages, the last holding one line - not two pages
        // with the seventh silently dropped.
        List<List<String>> pages =
                QuestLogLayout.paginate(List.of("1", "2", "3", "4", "5", "6", "7"), 3);
        assertEquals(3, pages.size());
        assertEquals(List.of("7"), pages.get(2));
    }

    @Test
    void anExactMultipleDoesNotProduceATrailingEmptyPage() {
        // Six lines at three per page is two pages. A third, blank one would look like the author
        // left the entry unfinished.
        assertEquals(2, QuestLogLayout.paginate(List.of("1", "2", "3", "4", "5", "6"), 3).size());
    }

    @Test
    void aNonsensePageSizeDoesNotDivideByZero() {
        // linesPerPage is derived from the panel's pixel height divided by the font's line height.
        // At a small GUI scale that arithmetic can reach zero, and an unguarded division there
        // would crash the whole inventory screen rather than just the quest tab.
        assertEquals(1, QuestLogLayout.paginate(List.of("a", "b"), 0).size());
        assertEquals(List.of("a", "b"), QuestLogLayout.paginate(List.of("a", "b"), 0).get(0));
        assertEquals(1, QuestLogLayout.paginate(List.of("a"), -4).size());
    }

    @Test
    void aQuestWithNoKnownDefinitionStillRenders() {
        // Quest definitions are datapack state. If a client is on a quest whose definition it was
        // never sent - a pack removed mid-session, or a sync that lost a race - the row must show
        // the id rather than a blank line that cannot be selected.
        ClientQuests.Entry unknown =
                new ClientQuests.Entry("wolf_trouble", "", "", "", 2, 5, false, "");
        assertEquals("wolf_trouble", unknown.title(), "the id is the fallback title");
        assertEquals(QuestLogLayout.GENERAL,
                QuestLogLayout.categories(List.of(unknown)).get(0),
                "and it is filed somewhere reachable");
    }
}
