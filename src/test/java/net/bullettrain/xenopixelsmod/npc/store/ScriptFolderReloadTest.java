package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-29 owner: "i removed the script and its still there until a logon" / "or do a reload once
 * a script was removed?". The store read script files only when the world loaded, so a file deleted
 * or edited on disk kept running until the next world load.
 */
class ScriptFolderReloadTest {
    private static final XenoNpcStoreCategory CAT = XenoNpcStoreCategory.PLAYER_SCRIPTS;

    private static CompoundTag script(String text) {
        return new XenoNpcScripts.Script("events", "events", XenoNpcScripts.DEFAULT_LANGUAGE, true, text).toTag();
    }

    private static XenoNpcWorldStore opened(Path root) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(root);
        store.loadAll();
        return store;
    }

    @Test
    void aScriptDeletedOnDiskIsDroppedByTheNextCheck(@TempDir Path root) throws Exception {
        XenoNpcWorldStore store = opened(root);
        assertNull(store.put(CAT, "", "events", script("function broken(e) {}")));
        assertFalse(store.reloadIfChangedOnDisk(CAT), "our own write is not a change on disk");

        Files.delete(XenoNpcStorePaths.resolve(root, CAT, "", "events"));
        assertTrue(store.reloadIfChangedOnDisk(CAT));
        assertNull(store.get(CAT, "", "events"), "the deleted script no longer runs");
        assertFalse(store.reloadIfChangedOnDisk(CAT), "reloads once, not every check");
    }

    @Test
    void aScriptAddedOrEditedOnDiskIsPickedUp(@TempDir Path root) throws Exception {
        XenoNpcWorldStore store = opened(root);
        assertNull(store.put(CAT, "", "events", script("function a() {}")));
        store.reloadIfChangedOnDisk(CAT);

        XenoNpcWorldStore other = new XenoNpcWorldStore(root);   // e.g. a copy dropped into the folder
        assertNull(other.put(CAT, "", "second", script("function b() {}")));
        assertTrue(store.reloadIfChangedOnDisk(CAT));
        assertNotNull(store.get(CAT, "", "second"));

        Path file = XenoNpcStorePaths.resolve(root, CAT, "", "events");
        CompoundTag edited = script("function edited() {}");
        assertNull(other.put(CAT, "", "events", edited));
        Files.setLastModifiedTime(file, FileTime.fromMillis(Files.getLastModifiedTime(file).toMillis() + 5000));
        assertTrue(store.reloadIfChangedOnDisk(CAT));
        assertEquals("function edited() {}", store.get(CAT, "", "events").getString("Script"));
    }

    @Test
    void removingInGameDoesNotTriggerASecondReload(@TempDir Path root) {
        XenoNpcWorldStore store = opened(root);
        assertNull(store.put(CAT, "", "events", script("function a() {}")));
        assertNull(store.remove(CAT, "", "events"));
        assertFalse(store.reloadIfChangedOnDisk(CAT), "the editor path already rebuilt everything");
    }
}
