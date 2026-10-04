package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogueNbt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AssignedDialogueTest {
    @Test
    void slotUsesItsExactCategoryEvenWhenIdsCollide(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        var village = XenoDialogueNbt.write(new XenoDialogue("start",
                Map.of("start", new XenoDialogue.Node("Village greeting", List.of()))));
        var castle = XenoDialogueNbt.write(new XenoDialogue("start",
                Map.of("start", new XenoDialogue.Node("Castle greeting", List.of()))));
        assertNull(store.put(XenoNpcStoreCategory.DIALOGS, "village", "hello", village));
        assertNull(store.put(XenoNpcStoreCategory.DIALOGS, "castle", "hello", castle));

        assertEquals("Village greeting", XenoNpcDataSource.assignedDialogue(
                store, "village", "hello").nodes().get("start").text());
        assertEquals("Castle greeting", XenoNpcDataSource.assignedDialogue(
                store, "castle", "hello").nodes().get("start").text());
        assertNull(XenoNpcDataSource.assignedDialogue(store, "missing", "hello"));
    }
}
