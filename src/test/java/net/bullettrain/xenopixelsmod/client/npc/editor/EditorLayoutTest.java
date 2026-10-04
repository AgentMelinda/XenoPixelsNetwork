package net.bullettrain.xenopixelsmod.client.npc.editor;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression guard for the bug this layout exists to kill: editor rows drawing past the bottom
 * of the frame and underneath the footer buttons.
 */
class EditorLayoutTest {

    /** Body geometry close to the real editor: 382 wide, 228 tall, 24px rows. */
    private static EditorLayout layout() {
        return new EditorLayout(18, 54, 382, 228, 24, 6);
    }

    private static EditorRow.Toggle toggle(String label) {
        return new EditorRow.Toggle(label, false, v -> { });
    }

    @Test
    void noRowIsEverPlacedBelowTheBody() {
        List<EditorRow> rows = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            rows.add(toggle("row" + i));
        }
        EditorLayout layout = layout();
        int bottom = 54 + 228;
        for (EditorLayout.Page page : layout.place(rows)) {
            for (EditorLayout.Placed placed : page.rows()) {
                assertTrue(placed.y() + 24 <= bottom,
                        "row at y=" + placed.y() + " overflows the body bottom " + bottom);
            }
        }
    }

    @Test
    void everyRowSurvivesPagination() {
        List<EditorRow> rows = new ArrayList<>();
        for (int i = 0; i < 47; i++) {
            rows.add(toggle("row" + i));
        }
        int placed = layout().place(rows).stream().mapToInt(p -> p.rows().size()).sum();
        assertEquals(47, placed, "pagination must not drop or duplicate rows");
    }

    @Test
    void explicitFullWidthControlsOccupyTheirOwnLine() {
        EditorRow.Field wide = new EditorRow.Field("Name", "NPC", 64, value -> { }, true);
        EditorRow.Action action = new EditorRow.Action("Global", "mynpcs_button_row",
                () -> { }, true);

        List<EditorLayout.Placed> rows = layout().place(List.of(wide, toggle("next"), action))
                .get(0).rows();

        assertEquals(18, rows.get(0).x());
        assertEquals(382, rows.get(0).columnWidth());
        assertEquals(78, rows.get(1).y());
        assertEquals(102, rows.get(2).y());
        assertEquals(382, rows.get(2).columnWidth());
    }

    @Test
    void narrowRowsPairTwoPerLine() {
        List<EditorRow> rows = List.of(toggle("a"), toggle("b"), toggle("c"), toggle("d"));
        List<EditorLayout.Placed> first = layout().place(rows).get(0).rows();
        assertEquals(4, first.size());
        // a|b share a line, c|d share the next.
        assertEquals(first.get(0).y(), first.get(1).y());
        assertEquals(first.get(2).y(), first.get(3).y());
        assertTrue(first.get(2).y() > first.get(0).y());
        // The second of a pair sits in the right column.
        assertTrue(first.get(1).x() > first.get(0).x());
    }

    @Test
    void fullWidthRowsTakeAWholeLineAndNeverShare() {
        List<EditorRow> rows = List.of(
                toggle("a"),
                new EditorRow.Heading("Section"),
                toggle("b"));
        List<EditorLayout.Placed> first = layout().place(rows).get(0).rows();

        EditorLayout.Placed heading = first.get(1);
        assertEquals(382, heading.columnWidth(), "a heading spans the whole body");
        // The heading closes the half-used line above it rather than sitting beside "a".
        assertTrue(heading.y() > first.get(0).y());
        assertTrue(first.get(2).y() > heading.y());
    }

    @Test
    void informationalTextSpansButLabelledTextDoesNot() {
        assertTrue(new EditorRow.Text("", "explanation", 0).fullWidth());
        assertFalse(new EditorRow.Text("Role", "humanoid", 0).fullWidth());
    }

    @Test
    void aSinglePageIsProducedEvenWithNoRows() {
        List<EditorLayout.Page> pages = layout().place(List.of());
        assertEquals(1, pages.size());
        assertTrue(pages.get(0).rows().isEmpty());
    }

    @Test
    void theRealBrainTabFitsWithoutOverflowing() {
        // The Brain tab is the densest: 16 controls plus 3 headings.
        List<EditorRow> rows = new ArrayList<>();
        rows.add(toggle("Combat brain"));
        for (String section : List.of("Melee", "Ki", "Evasion")) {
            rows.add(new EditorRow.Heading(section));
            for (int i = 0; i < 5; i++) {
                rows.add(toggle(section + i));
            }
        }
        List<EditorLayout.Page> pages = layout().place(rows);
        assertTrue(pages.size() >= 1);
        int bottom = 54 + 228;
        for (EditorLayout.Page page : pages) {
            for (EditorLayout.Placed p : page.rows()) {
                assertTrue(p.y() + 24 <= bottom, "Brain row overflowed at y=" + p.y());
            }
        }
    }

    @Test
    void clampPageKeepsTheIndexInRange() {
        assertEquals(0, EditorLayout.clampPage(-3, 4));
        assertEquals(3, EditorLayout.clampPage(9, 4));
        assertEquals(2, EditorLayout.clampPage(2, 4));
        assertEquals(0, EditorLayout.clampPage(1, 0));
    }
}
