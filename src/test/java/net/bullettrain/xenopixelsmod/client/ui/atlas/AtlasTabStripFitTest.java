package net.bullettrain.xenopixelsmod.client.ui.atlas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tab strip has to fit the frame it is drawn in.
 *
 * <p>Narrowing the editor frame to 420, so the visualizer could sit beside it, left the ten tabs
 * about one cell wider than the frame - and the cell that fell off the end was X. This is the
 * arithmetic that gives the slack back.
 *
 * <p>Only {@code shrinkToFit} is tested: the rest of {@code AtlasTabStrip} needs a client font, and
 * this is the part worth pinning.
 */
class AtlasTabStripFitTest {

    /** The generated tab widths, ascending. Must match {@code AtlasTabStrip.TAB_WIDTHS}. */
    private static final int[] LADDER = {20, 28, 36, 44, 52, 60, 68};

    private static int total(int[] cells) {
        int sum = 0;
        for (int c : cells) {
            sum += c;
        }
        return sum;
    }

    private static void assertOnLadder(int[] cells) {
        for (int c : cells) {
            boolean found = false;
            for (int rung : LADDER) {
                found |= rung == c;
            }
            assertTrue(found, c + " is not a generated tab width; a stretched cell distorts the border");
        }
    }

    @Test
    void aStripThatAlreadyFitsIsLeftAlone() {
        int[] cells = {36, 44, 36};
        int[] before = cells.clone();
        AtlasTabStrip.shrinkToFit(cells, 500);
        assertEquals(before[0], cells[0]);
        assertEquals(before[1], cells[1]);
        assertEquals(before[2], cells[2]);
    }

    @Test
    void theWidestCellGivesUpItsPaddingFirst() {
        // Long labels have the most slack, so they should lose it before short ones do.
        int[] cells = {68, 36};
        AtlasTabStrip.shrinkToFit(cells, 96);
        assertTrue(cells[0] < 68, "the wide cell should have shrunk");
        assertEquals(36, cells[1], "the narrow one should not have been touched first");
    }

    @Test
    void theTenEditorTabsFitTheNarrowFrame() {
        // The real case. Widths are the ladder choices for the editor's labels; the budget is the
        // 420 frame less its 16px of inset and the 8px gap before the detached Delete/X tail.
        int[] cells = {52, 36, 36, 60, 60, 44, 36, 36, 44, 36};
        int budget = 420 - 16 - 8;

        assertTrue(total(cells) > budget, "this is only interesting because it starts too wide");
        AtlasTabStrip.shrinkToFit(cells, budget);
        assertTrue(total(cells) <= budget,
                "the strip is still " + total(cells) + " wide against a budget of " + budget);
        assertOnLadder(cells);
    }

    @Test
    void longLabelsCanUseMinimumCellsAndAreFittedByTheSharedStripScale() {
        int[] cells = {68, 68, 68};
        AtlasTabStrip.shrinkToFit(cells, 60);
        assertEquals(36, cells[0]);
        assertEquals(36, cells[1]);
        assertEquals(36, cells[2]);
    }

    @Test
    void anImpossibleBudgetShrinksToMinimumGeneratedCellsAndStops() {
        int[] cells = {68, 68};
        AtlasTabStrip.shrinkToFit(cells, 10);
        assertEquals(36, cells[0]);
        assertEquals(36, cells[1]);
    }

    @Test
    void aNonsenseBudgetIsIgnoredRatherThanCollapsingTheStrip() {
        int[] cells = {44, 44};
        AtlasTabStrip.shrinkToFit(cells, 0);
        assertEquals(44, cells[0]);
        AtlasTabStrip.shrinkToFit(cells, -100);
        assertEquals(44, cells[1]);
    }

    @Test
    void everyCellStaysOnTheGeneratedLadder() {
        // The whole reason for stepping rather than computing a width: the atlas bakes its border
        // proportions in at generation, so an off-ladder width would have to be stretched.
        int[] cells = {68, 60, 52, 44};
        AtlasTabStrip.shrinkToFit(cells, 100);
        assertOnLadder(cells);
    }
}
