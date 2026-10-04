package net.bullettrain.xenopixelsmod.client.npc.editor;

import java.util.ArrayList;
import java.util.List;

/**
 * Places editor rows into two columns and splits them across pages.
 *
 * <p>This exists because the editor previously drew rows at a fixed pitch with no height budget, so
 * a tab with more rows than fitted simply drew them past the bottom of the frame and underneath the
 * footer buttons. Here the body height is an input, so overflow is impossible: rows that do not fit
 * move to the next page instead of off the panel.
 *
 * <p>Placement follows the reference menu. Labelled controls pair up two per line, left column then
 * right; headings and informational text take a whole line. A pair is never split across a page
 * boundary.
 *
 * <p>Pure geometry, so it is unit-testable without a client: nothing here touches Minecraft.
 */
public final class EditorLayout {

    /** A row with its resolved on-screen rectangle. */
    public record Placed(EditorRow row, int x, int y, int columnWidth) {}

    /** One page of placed rows. */
    public record Page(List<Placed> rows) {}

    private final int bodyX;
    private final int bodyY;
    private final int bodyWidth;
    private final int bodyHeight;
    private final int rowHeight;
    private final int columnGap;

    private final List<Page> pages = new ArrayList<>();

    public EditorLayout(int bodyX, int bodyY, int bodyWidth, int bodyHeight,
                        int rowHeight, int columnGap) {
        this.bodyX = bodyX;
        this.bodyY = bodyY;
        this.bodyWidth = bodyWidth;
        this.bodyHeight = Math.max(rowHeight, bodyHeight);
        this.rowHeight = Math.max(1, rowHeight);
        this.columnGap = Math.max(0, columnGap);
    }

    /** Width of a single column, which is what a control is sized against. */
    public int columnWidth() {
        return Math.max(1, (bodyWidth - columnGap) / 2);
    }

    /** How many lines of {@link #rowHeight} fit in the body. */
    public int linesPerPage() {
        return Math.max(1, bodyHeight / rowHeight);
    }

    /**
     * Places {@code rows}, returning the resulting pages.
     *
     * <p>A full-width row always starts a fresh line and consumes it entirely. Narrow rows fill the
     * left column of a line, then the right; the line advances once both are used.
     */
    public List<Page> place(List<EditorRow> rows) {
        pages.clear();
        int lines = linesPerPage();

        List<Placed> current = new ArrayList<>();
        int line = 0;
        boolean rightFree = false;

        for (EditorRow row : rows) {
            boolean wide = row.fullWidth();

            // A full-width row cannot share a line, so close any half-used one first.
            if (wide && rightFree) {
                rightFree = false;
                line++;
            }

            if (line >= lines) {
                pages.add(new Page(List.copyOf(current)));
                current = new ArrayList<>();
                line = 0;
                rightFree = false;
            }

            int y = bodyY + line * rowHeight;
            if (wide) {
                current.add(new Placed(row, bodyX, y, bodyWidth));
                line++;
            } else if (!rightFree) {
                current.add(new Placed(row, bodyX, y, columnWidth()));
                rightFree = true;
            } else {
                current.add(new Placed(row, bodyX + columnWidth() + columnGap, y, columnWidth()));
                rightFree = false;
                line++;
            }
        }

        if (!current.isEmpty()) {
            pages.add(new Page(List.copyOf(current)));
        }
        if (pages.isEmpty()) {
            pages.add(new Page(List.of()));
        }
        return List.copyOf(pages);
    }

    /** Clamps a requested page index into the range a placement actually produced. */
    public static int clampPage(int requested, int pageCount) {
        if (pageCount <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(pageCount - 1, requested));
    }
}
