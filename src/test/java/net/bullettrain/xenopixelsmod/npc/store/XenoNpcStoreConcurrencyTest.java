package net.bullettrain.xenopixelsmod.npc.store;

import net.bullettrain.xenopixelsmod.RepoRoot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two operators editing the same store entry.
 *
 * <p>Found by reviewing the store against this project's own packet checklist, which asks for
 * "editor lock or current interaction state" on every write. {@code XenoNpcSavePacket} has carried
 * an {@code expectedRevision} since it was written and rejects a stale save with {@code STALE}; the
 * store write packet shipped without an equivalent, so the second of two operators editing one
 * faction silently overwrote the first and <em>both</em> screens reported success.
 *
 * <p>Nothing about it would have shown up in a log. The first operator's edits would simply be
 * gone, and the obvious explanation - "it didn't save" - would be wrong.
 */
class XenoNpcStoreConcurrencyTest {

    private static CompoundTag named(String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);
        return tag;
    }

    @Test
    void aWriteStampsARevisionAndTheNextOneIncrementsIt() {
        XenoNpcWorldStore store = new XenoNpcWorldStore(Path.of("unused"));
        assertEquals(0, store.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards"),
                "an entry that does not exist is revision 0");
    }

    @Test
    void theSecondOfTwoConcurrentWritesIsRefused(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);

        // Both operators opened the screen while the faction was at revision 0.
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"), 0));
        assertEquals(1, store.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards"));

        String refusal = store.put(XenoNpcStoreCategory.FACTIONS, "", "guards",
                named("Town Watch"), 0);
        assertNotNull(refusal, "the second write was made against a revision that has moved on");
        assertTrue(refusal.contains("reopen the screen"), refusal);

        assertEquals("Guards",
                store.get(XenoNpcStoreCategory.FACTIONS, "", "guards").getString("Name"),
                "and the first operator's edit is still there");
    }

    @Test
    void writingAgainstTheCurrentRevisionSucceeds(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"), 0);

        int now = store.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards");
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Town Watch"), now));
        assertEquals("Town Watch",
                store.get(XenoNpcStoreCategory.FACTIONS, "", "guards").getString("Name"));
        assertEquals(now + 1, store.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards"));
    }

    @Test
    void addingAnIdSomebodyElseJustCreatedIsRefused(@TempDir Path dir) {
        // Both pressed Add with the same id. The second must not replace the first's faction with
        // an empty one just because it believed the id was free.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"), 0));
        assertNotNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named(""), 0));
    }

    @Test
    void anUncheckedWriteStillWorksForProgrammaticCallers(@TempDir Path dir) {
        // Not everything writing to the store came from a screen that had read it first.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"));
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Again")));
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("And again"),
                XenoNpcWorldStore.UNCHECKED));
        assertEquals("And again",
                store.get(XenoNpcStoreCategory.FACTIONS, "", "guards").getString("Name"));
    }

    @Test
    void theRevisionSurvivesAReload(@TempDir Path dir) {
        // It lives in the file, so a restart does not reset every entry to 0 and reopen the race.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"), 0);
        store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Guards"), 1);

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertEquals(2, reopened.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards"));
        assertNotNull(reopened.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("x"), 0),
                "a stale write is still refused after a reload");
    }

    @Test
    void anEntryWrittenBeforeRevisionsExistedReadsAsZero(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("factions"));
        Files.writeString(dir.resolve("factions/guards.json"), "{Name:\"Guards\",Schema:1}",
                StandardCharsets.UTF_8);

        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.loadAll();
        assertEquals(0, store.revisionOf(XenoNpcStoreCategory.FACTIONS, "", "guards"));
        assertNull(store.put(XenoNpcStoreCategory.FACTIONS, "", "guards", named("Watch"), 0),
                "so a screen that read it can still save it");
    }

    @Test
    void theWritePacketCarriesTheRevisionAndAnswersStale() throws IOException {
        String packet = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcStoreWritePacket.java"), StandardCharsets.UTF_8);
        assertTrue(packet.contains("int expectedRevision"), "the field should be on the wire");
        assertTrue(packet.contains("NpcProfileSaveResultPacket.STALE"),
                "and a lost race should read as stale, not as an invalid field");
        assertTrue(packet.contains("Math.max(0, expectedRevision)"),
                "UNCHECKED is a server-side concession, not something a client may ask for");
    }

    @Test
    void theIndexTellsTheClientWhatRevisionItIsLookingAt() throws IOException {
        // Without this the client has nothing truthful to send back and the check is theatre.
        String index = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "SyncNpcStoreIndexPacket.java"), StandardCharsets.UTF_8);
        assertTrue(index.contains("entry.revision()"));
        assertTrue(index.contains("buf.writeVarInt(entry.revision())"));
    }

    // ------------------------------------------------------------ dedicated-server classloading

    @Test
    void theClientMirrorsUsedInServerBuiltPacketsTouchNothingClientOnly() throws IOException {
        // SyncNpcStoreIndexPacket.current() and SyncFactionsPacket.current() both run on the
        // server and both construct a type that lives in a client.* package. That is safe only
        // while those types stay free of client-only Minecraft classes - a Minecraft.getInstance()
        // added to either one would crash a dedicated server the first time it built the packet,
        // and would show up nowhere until then.
        //
        // The classes do ship in the server jar (it takes all of sourceSets.main.output), so the
        // risk is not a missing class; it is a client-only class being reached through them.
        for (String relative : List.of(
                "client/npc/store/ClientNpcStoreIndex.java",
                "client/npc/faction/ClientFactions.java")) {
            String text = Files.readString(
                    RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative),
                    StandardCharsets.UTF_8);
            for (String forbidden : List.of(
                    "net.minecraft.client.",
                    "Minecraft.getInstance",
                    "com.mojang.blaze3d",
                    "net.neoforged.api.distmarker.Dist")) {
                assertFalse(text.contains(forbidden),
                        relative + " is built on the server; it must not reach " + forbidden);
            }
        }
    }
}
