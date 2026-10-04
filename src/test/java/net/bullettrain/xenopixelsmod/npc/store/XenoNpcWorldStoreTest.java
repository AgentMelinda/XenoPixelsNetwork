package net.bullettrain.xenopixelsmod.npc.store;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The writable per-world store.
 *
 * <p>It exists because the datapack loaders cannot be written to: a
 * {@code SimpleJsonResourceReloadListener} reads a pack and has nowhere to write back, which is why
 * every Global screen in the editor has been showing a disabled Add button.
 *
 * <p>Its layout mirrors My NPCs' world folder so a later import renames keys inside files instead of
 * working out which file is which. The one deliberate divergence is that every category here is a
 * folder of one file per entry, where theirs keeps factions, banks, transport, recipes and spawns as
 * a single blob each. A per-entry file can fail alone; a blob takes every faction with it.
 */
class XenoNpcWorldStoreTest {

    private static CompoundTag tagOf(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    // ------------------------------------------------------------ ids as filenames

    @Test
    void anIdThatWouldEscapeTheStoreIsRefused() {
        // Ids arrive from an operator's editor over the network and become filenames. This is a
        // security boundary, not tidiness.
        for (String bad : List.of("..", ".", "../evil", "a/b", "a\\b", "C:evil", "/abs", "")) {
            assertNotNull(XenoNpcStorePaths.reject(bad), "should have been refused: " + bad);
        }
    }

    @Test
    void windowsDeviceNamesAreRefused() {
        // A server on Windows cannot create these, and the failure would surface as an IO error
        // that says nothing about why a faction called "con" will not save.
        for (String bad : List.of("con", "nul", "com1", "lpt9", "aux.json", "prn")) {
            assertNotNull(XenoNpcStorePaths.reject(bad), "should have been refused: " + bad);
        }
    }

    @Test
    void mixedCaseIsRefusedRatherThanFolded() {
        // NTFS treats Guards and guards as one file; ext4 treats them as two. Accepting both
        // spellings means a world that behaves differently depending on the operator's filesystem.
        assertNotNull(XenoNpcStorePaths.reject("Guards"));
        assertNull(XenoNpcStorePaths.reject("guards"));
    }

    @Test
    void leadingDotsAndTrailingDotsAreRefused() {
        assertNotNull(XenoNpcStorePaths.reject(".hidden"));
        assertNotNull(XenoNpcStorePaths.reject("-lead"));
        assertNotNull(XenoNpcStorePaths.reject("trailing."));
        assertNull(XenoNpcStorePaths.reject("village.guards-2"));
    }

    @Test
    void anOverLongIdIsRefused() {
        assertNull(XenoNpcStorePaths.reject("a".repeat(XenoNpcStorePaths.MAX_ID)));
        assertNotNull(XenoNpcStorePaths.reject("a".repeat(XenoNpcStorePaths.MAX_ID + 1)));
    }

    @Test
    void aRefusalSaysWhyRatherThanMangingTheId(@TempDir Path dir) {
        // Rejecting beats rewriting: silently turning "My Faction" into "my_faction" means the
        // operator's next lookup misses and the entry looks like it vanished.
        String reason = XenoNpcStorePaths.reject("My Faction");
        assertNotNull(reason);
        assertTrue(reason.contains("lower case") || reason.contains("may only contain"), reason);
        assertNull(XenoNpcStorePaths.resolve(dir, XenoNpcStoreCategory.FACTIONS, "", "My Faction"));
    }

    @Test
    void everyAcceptedIdResolvesInsideTheRoot(@TempDir Path dir) {
        for (String id : List.of("guards", "a", "village.guards", "x-1", "x_1", "a".repeat(64))) {
            Path resolved = XenoNpcStorePaths.resolve(dir, XenoNpcStoreCategory.FACTIONS, "", id);
            assertNotNull(resolved, id);
            assertTrue(resolved.normalize().startsWith(dir.normalize()), id);
        }
    }

    @Test
    void agroupIsOnlyAcceptedWhereTheCategoryHasOne(@TempDir Path dir) {
        assertNotNull(XenoNpcStorePaths.resolve(dir, XenoNpcStoreCategory.DIALOGS, "villager", "1"));
        assertNull(XenoNpcStorePaths.resolve(dir, XenoNpcStoreCategory.DIALOGS, "", "1"),
                "a grouped category needs a group");
        assertNull(XenoNpcStorePaths.resolve(dir, XenoNpcStoreCategory.FACTIONS, "villager", "x"),
                "an ungrouped one must not silently invent a folder");
    }

    // ------------------------------------------------------------ round trip

    @Test
    void anEntryWrittenIsThereAfterReopening(@TempDir Path dir) {
        // The whole claim of the store in one test: it is on disk, not just in memory.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards")));

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertEquals("Guards",
                reopened.get(XenoNpcStoreCategory.FACTIONS, "", "guards").getString("Name"));
        assertEquals(List.of(), reopened.loadErrors());
    }

    @Test
    void aGroupedEntryKeepsItsGroup(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        assertNull(store.put(XenoNpcStoreCategory.DIALOGS, "villager", "1", tagOf("Greeting")));
        assertTrue(Files.isRegularFile(dir.resolve("dialogs/villager/1.json")));

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertEquals("Greeting",
                reopened.get(XenoNpcStoreCategory.DIALOGS, "villager", "1").getString("Name"));
        assertEquals(1, reopened.list(XenoNpcStoreCategory.DIALOGS).size());
        assertEquals("villager", reopened.list(XenoNpcStoreCategory.DIALOGS).get(0).group());
    }

    @Test
    void whatIsWrittenIsReadableSnbt(@TempDir Path dir) throws IOException {
        // The point of SNBT-in-.json over a binary blob: an operator can open it.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards"));
        String text = Files.readString(dir.resolve("factions/guards.json"), StandardCharsets.UTF_8);
        assertTrue(text.contains("Guards"), text);
        assertTrue(text.contains("Schema"), "and it carries its version");
    }

    @Test
    void removingAnEntryDeletesTheFileAndForgetsIt(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards"));
        assertNull(store.remove(XenoNpcStoreCategory.FACTIONS, "", "guards"));
        assertFalse(Files.exists(dir.resolve("factions/guards.json")));
        assertNull(store.get(XenoNpcStoreCategory.FACTIONS, "", "guards"));
    }

    @Test
    void aFailedWriteLeavesMemoryAlone(@TempDir Path dir) throws IOException {
        // If the two disagreed about what is on disk, the next read would resurrect something that
        // was never saved - or lose something that was.
        Files.createDirectories(dir.resolve("factions"));
        // A directory where the file needs to go: the write cannot succeed.
        Files.createDirectories(dir.resolve("factions/guards.json"));

        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        assertNotNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards")));
        assertNull(store.get(XenoNpcStoreCategory.FACTIONS, "", "guards"));
    }

    // ------------------------------------------------------------ failure isolation

    @Test
    void oneCorruptFileCostsOneEntry(@TempDir Path dir) throws IOException {
        // This is the reason every category is a folder rather than one blob. My NPCs keeps all its
        // factions in a single factions.dat; one bad byte there takes every faction with it.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards"));
        store.put(XenoNpcStoreCategory.FACTIONS, "", "bandits", tagOf("Bandits"));
        Files.writeString(dir.resolve("factions/bandits.json"), "{ this is not snbt",
                StandardCharsets.UTF_8);

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertNotNull(reopened.get(XenoNpcStoreCategory.FACTIONS, "", "guards"),
                "the good one still loads");
        assertNull(reopened.get(XenoNpcStoreCategory.FACTIONS, "", "bandits"));
        assertEquals(1, reopened.loadErrors().size(), "and the bad one is named");
        assertTrue(reopened.loadErrors().get(0).contains("bandits"));
    }

    @Test
    void aFileWhoseNameWeWouldNeverHaveWrittenIsNotRead(@TempDir Path dir) throws IOException {
        // A hand-dropped file cannot be trusted to have come from us, so it is reported and skipped
        // rather than loaded under a mangled id.
        Files.createDirectories(dir.resolve("factions"));
        Files.writeString(dir.resolve("factions/NotValid.json"), "{Name:\"x\"}",
                StandardCharsets.UTF_8);

        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.loadAll();
        assertTrue(store.isEmpty(XenoNpcStoreCategory.FACTIONS));
        assertEquals(1, store.loadErrors().size());
    }

    // ------------------------------------------------------------ versioning

    @Test
    void aFileFromTheFutureIsRefusedAndLocksTheCategory(@TempDir Path dir) throws IOException {
        // Reading it would drop the fields this build does not know about; writing over it would
        // then make that loss permanent. So: not read, not deleted, not overwritten.
        Files.createDirectories(dir.resolve("factions"));
        Files.writeString(dir.resolve("factions/guards.json"),
                "{Name:\"Guards\",Schema:999}", StandardCharsets.UTF_8);

        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.loadAll();
        assertNull(store.get(XenoNpcStoreCategory.FACTIONS, "", "guards"), "not read");
        assertTrue(Files.exists(dir.resolve("factions/guards.json")), "not deleted");
        assertTrue(store.isReadOnly(XenoNpcStoreCategory.FACTIONS), "and no longer writable");

        assertNotNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "other", tagOf("x")),
                "a write into a locked category is refused");
        assertEquals("{Name:\"Guards\",Schema:999}",
                Files.readString(dir.resolve("factions/guards.json"), StandardCharsets.UTF_8),
                "and the future file is byte-for-byte untouched");
    }

    @Test
    void oneCategoryGoingReadOnlyDoesNotLockTheOthers(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("factions"));
        Files.writeString(dir.resolve("factions/guards.json"), "{Schema:999}",
                StandardCharsets.UTF_8);

        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.loadAll();
        assertTrue(store.isReadOnly(XenoNpcStoreCategory.FACTIONS));
        assertFalse(store.isReadOnly(XenoNpcStoreCategory.DIALOGS));
        assertNull(store.put(XenoNpcStoreCategory.DIALOGS, "villager", "1", tagOf("ok")));
    }

    @Test
    void anUnstampedFileReadsAsVersionOneRatherThanZero() {
        // Zero would re-run migrations that had already been applied.
        assertEquals(1, XenoNpcStoreSchema.versionOf(new CompoundTag()));
        assertFalse(XenoNpcStoreSchema.isFromTheFuture(new CompoundTag()));
    }

    // ------------------------------------------------------------ bounds

    @Test
    void aCategoryWillNotGrowPastItsOwnReadCap(@TempDir Path dir) {
        // Enforced on write as well as read, or the store could hold more than it can load back.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        for (int i = 0; i < XenoNpcWorldStore.MAX_FACTIONS; i++) {
            assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "f" + i, tagOf("f" + i)));
        }
        assertNotNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "one-too-many", tagOf("x")),
                "past the cap it refuses rather than growing");
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "f0", tagOf("updated")),
                "but overwriting an existing entry is still fine");
    }

    @Test
    void theFactionCapMatchesWhatThePacketCanCarry() {
        // Otherwise the store would hold more than SyncFactionsPacket can send and the surplus
        // would vanish on the way to the client with nothing said about it.
        assertEquals(256, XenoNpcWorldStore.MAX_FACTIONS);
    }

    @Test
    void anOversizedFileIsRefusedWithoutBeingRead(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("big.json");
        Files.writeString(file, "{Name:\"" + "x".repeat((int) SnbtFiles.MAX_BYTES) + "\"}",
                StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> SnbtFiles.read(file));
    }

    @Test
    void anOverDeepFileIsRefusedBeforeTheParserSeesIt(@TempDir Path dir) throws IOException {
        int depth = SnbtFiles.MAX_DEPTH + 5;
        String text = "{a:".repeat(depth) + "1" + "}".repeat(depth);
        Path file = dir.resolve("deep.json");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        assertThrows(IOException.class, () -> SnbtFiles.read(file));
    }

    @Test
    void bracesInsideAStringDoNotCountTowardsDepth() {
        // {player} is the placeholder every shipped dialogue uses; counting it would refuse
        // perfectly ordinary files.
        assertEquals(1, SnbtFiles.depthOf("{Text:\"Hello {player}, welcome [home]\"}"));
        assertEquals(2, SnbtFiles.depthOf("{a:{b:1}}"));
        assertEquals(0, SnbtFiles.depthOf("\"{{{{{{\""));
    }

    @Test
    void noTemporaryFileIsLeftBehind(@TempDir Path dir) throws IOException {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", tagOf("Guards"));
        try (var files = Files.list(dir.resolve("factions"))) {
            assertTrue(files.noneMatch(f -> f.getFileName().toString().endsWith(".tmp")));
        }
    }

    // ------------------------------------------------------------ the categories themselves

    @Test
    void categoryFoldersMatchTheReferenceLayout() {
        assertEquals("clones", XenoNpcStoreCategory.CLONES.folder());
        assertEquals("dialogs", XenoNpcStoreCategory.DIALOGS.folder());
        assertEquals("quests", XenoNpcStoreCategory.QUESTS.folder());
        assertEquals("factions", XenoNpcStoreCategory.FACTIONS.folder());
        assertEquals(XenoNpcStoreCategory.DIALOGS, XenoNpcStoreCategory.byFolder("Dialogs"));
        assertNull(XenoNpcStoreCategory.byFolder("nope"));
    }

    @Test
    void aWireOrdinalIsBoundsCheckedRatherThanIndexed() {
        // Ordinals travel on the network; a crafted packet must not index past the array.
        assertNull(XenoNpcStoreCategory.byOrdinal(-1));
        assertNull(XenoNpcStoreCategory.byOrdinal(XenoNpcStoreCategory.values().length));
        assertEquals(XenoNpcStoreCategory.CLONES, XenoNpcStoreCategory.byOrdinal(0));
    }

    @Test
    void playerDataIsReservedAndWrittenByNothing() {
        // Ours already live on XenoPlayerData, which already persists. Writing them here as well
        // would give one fact two writers and no rule for which wins.
        assertFalse(XenoNpcStoreCategory.PLAYERDATA.hasRuntimeConsumer());
        assertEquals("playerdata", XenoNpcStoreCategory.PLAYERDATA.folder());
    }

    @Test
    void runtimeConsumerInventoryMatchesLiveStoreReaders() {
        assertTrue(XenoNpcStoreCategory.DIALOGS.hasRuntimeConsumer());
        assertTrue(XenoNpcStoreCategory.FACTIONS.hasRuntimeConsumer());
        assertTrue(XenoNpcStoreCategory.BANKS.hasRuntimeConsumer());
        assertTrue(XenoNpcStoreCategory.TRANSPORT.hasRuntimeConsumer());
        assertTrue(XenoNpcStoreCategory.SCENES.hasRuntimeConsumer());
        // NpcNaturalSpawns reads the category and NpcNaturalSpawnService consumes it; see
        // docs/xeno-npc-schema.md §4 for the keys.
        assertTrue(XenoNpcStoreCategory.SPAWNS.hasRuntimeConsumer());
        assertEquals(XenoNpcStoreCategory.SCENES,
                XenoNpcStoreCategory.byOrdinal(XenoNpcStoreCategory.PLAYERDATA.ordinal() + 1));
        assertFalse(XenoNpcStoreCategory.CLONES.hasRuntimeConsumer());
    }
}
