package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.bullettrain.xenopixelsmod.hair.HairStrandModel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HairStudioToolsTest {
    @Test
    void growToolChangesLength() {
        HairStrandModel strand = new HairStrandModel(1);
        strand.length(4);
        HairViewportTool.GROW.applyDrag(strand, 0, -24); // up
        assertEquals(7, strand.length());
        HairViewportTool.GROW.applyDrag(strand, 0, 40); // down
        assertEquals(2, strand.length());
    }

    @Test
    void historyUndoRedo() {
        HairMakerDocument doc = new HairMakerDocument();
        doc.face("TOP");
        doc.createSegment();
        HairEditHistory history = new HairEditHistory(8);
        history.push(doc);
        doc.selected().length(12);
        assertTrue(history.undo(doc));
        assertEquals(4, doc.selected().length());
        assertTrue(history.redo(doc));
        assertEquals(12, doc.selected().length());
    }

    @Test
    void snapshotRestoresFaces() {
        HairMakerDocument doc = HairMakerDocument.oneStrandDemo();
        doc.face("BACK");
        doc.createSegment();
        int idx = doc.strandIndex();
        HairMakerDocument snap = doc.snapshot();
        doc.deleteSegment();
        doc.restoreSnapshot(snap);
        assertEquals("BACK", doc.face());
        assertEquals(idx, doc.strandIndex());
        assertTrue(doc.selected().visible());
    }
}
