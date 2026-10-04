package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Bounded undo/redo for {@link HairMakerDocument} snapshots. Client editor only —
 * no server tick registration (xeno-tps safe).
 */
public final class HairEditHistory {
    public static final int DEFAULT_LIMIT = 64;

    private final int limit;
    private final Deque<HairMakerDocument> undo = new ArrayDeque<>();
    private final Deque<HairMakerDocument> redo = new ArrayDeque<>();

    public HairEditHistory() {
        this(DEFAULT_LIMIT);
    }

    public HairEditHistory(int limit) {
        this.limit = Math.max(4, limit);
    }

    /** Push current document state before a mutating edit. Clears redo. */
    public void push(HairMakerDocument document) {
        if (document == null) {
            return;
        }
        undo.addLast(document.snapshot());
        while (undo.size() > limit) {
            undo.removeFirst();
        }
        redo.clear();
    }

    public boolean canUndo() {
        return !undo.isEmpty();
    }

    public boolean canRedo() {
        return !redo.isEmpty();
    }

    /** Restore previous snapshot into {@code document}. */
    public boolean undo(HairMakerDocument document) {
        if (document == null || undo.isEmpty()) {
            return false;
        }
        redo.addLast(document.snapshot());
        while (redo.size() > limit) {
            redo.removeFirst();
        }
        document.restoreSnapshot(undo.removeLast());
        return true;
    }

    public boolean redo(HairMakerDocument document) {
        if (document == null || redo.isEmpty()) {
            return false;
        }
        undo.addLast(document.snapshot());
        while (undo.size() > limit) {
            undo.removeFirst();
        }
        document.restoreSnapshot(redo.removeLast());
        return true;
    }

    public int undoSize() {
        return undo.size();
    }

    public int redoSize() {
        return redo.size();
    }

    public void clear() {
        undo.clear();
        redo.clear();
    }
}
