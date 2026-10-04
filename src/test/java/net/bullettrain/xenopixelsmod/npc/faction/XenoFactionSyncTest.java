package net.bullettrain.xenopixelsmod.npc.faction;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientFactions;
import net.bullettrain.xenopixelsmod.network.ProtocolVersion;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getting the faction list to the client, and what the editor does with it.
 *
 * <p>Factions load from a datapack, which is a server-side reload listener - so a client knows
 * nothing about them until told. The editor is client-side, so without a sync its faction screens
 * would be empty forever and read as broken rather than as unloaded. That is the third time this
 * trap has come up here, after the mark icons and the bubble palettes.
 */
class XenoFactionSyncTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void theClientCacheStartsEmptyAndTakesWhatItIsGiven() {
        ClientFactions.accept(List.of());
        assertTrue(ClientFactions.isEmpty());

        ClientFactions.accept(List.of(
                new ClientFactions.Entry("guards", "Guards", 0x3366FF, List.of("bandits"), 0),
                new ClientFactions.Entry("bandits", "Bandits", 0xDD0000, List.of("guards"), -50)));
        assertFalse(ClientFactions.isEmpty());
        assertEquals(List.of("guards", "bandits"), ClientFactions.ids());
        assertEquals("Guards", ClientFactions.get("guards").name());
        assertEquals(-50, ClientFactions.get("bandits").defaultStanding());
    }

    @Test
    void lookupIgnoresCaseAndPadding() {
        ClientFactions.accept(List.of(
                new ClientFactions.Entry("Guards", "Guards", 0, List.of(), 0)));
        assertEquals("guards", ClientFactions.get("  GUARDS  ").id());
        assertNull(ClientFactions.get("nope"));
        assertNull(ClientFactions.get(null));
        assertNull(ClientFactions.get(""));
    }

    @Test
    void ablankIdIsDroppedRatherThanStored() {
        ClientFactions.accept(List.of(
                new ClientFactions.Entry("", "Nameless", 0, List.of(), 0),
                new ClientFactions.Entry("real", "Real", 0, List.of(), 0)));
        assertEquals(List.of("real"), ClientFactions.ids());
    }

    @Test
    void acceptReplacesRatherThanMerges() {
        // A reload that removes a faction must remove it here too, or the editor would keep
        // offering one the server no longer knows.
        ClientFactions.accept(List.of(
                new ClientFactions.Entry("old", "Old", 0, List.of(), 0)));
        ClientFactions.accept(List.of(
                new ClientFactions.Entry("new", "New", 0, List.of(), 0)));
        assertNull(ClientFactions.get("old"));
        assertEquals(1, ClientFactions.ids().size());
    }

    @Test
    void theSyncFiresOnReloadAsWellAsOnJoin() throws IOException {
        // OnDatapackSyncEvent rather than a login listener: an operator who writes a faction and
        // runs /reload should see it without rejoining, which is how packs are iterated on.
        String sync = source("npc/faction/XenoFactionSync.java");
        assertTrue(sync.contains("OnDatapackSyncEvent"), "the reload hook is the right one");
        assertTrue(sync.contains("event.getPlayer()"),
                "a join syncs one player; a reload has no player and goes to everyone");
        assertTrue(sync.contains("sendToAll("), "so a reload reaches every client");
    }

    @Test
    void negativeStandingSurvivesTheWire() throws IOException {
        // VarInt is unsigned-friendly; a faction that starts a player disliked would otherwise
        // arrive as a huge positive number.
        String packet = source("network/packet/SyncFactionsPacket.java");
        assertTrue(packet.contains("+ XenoFaction.MAX_STANDING"), "shifted on the way out");
        assertTrue(packet.contains("- XenoFaction.MAX_STANDING"), "and back on the way in");
    }

    @Test
    void thePacketIsBoundedInEveryDirection() throws IOException {
        // Everything read off the wire is capped, so a hostile server cannot make a client
        // allocate whatever it likes.
        String packet = source("network/packet/SyncFactionsPacket.java");
        assertTrue(packet.contains("MAX_FACTIONS"), "the list is capped");
        assertTrue(packet.contains("MAX_HOSTILE"), "and each hostility list");
        assertTrue(packet.contains("Math.min(MAX_FACTIONS, buf.readVarInt())"),
                "the cap must apply on read, not only on write");
    }

    @Test
    void theProtocolMovedBecauseTheWireShapeDid() throws IOException {
        // Packet ids are positional. A new one changes what an old client would read, so the
        // version has to move with it. A floor rather than an exact number, so a later unrelated
        // bump does not turn this into a check someone has to edit to get a green suite.
        assertTrue(ProtocolVersion.current() >= 78,
                "adding the faction packet required protocol 78 or later");
        assertTrue(source("network/ModNetwork.java").contains("ids are positional"),
                "and the append-only rule should be written next to it");
    }

    @Test
    void theEditorSplitsAssigningAFactionFromBrowsingThem() throws IOException {
        // The reference has two screens: Advanced > Factions assigns this NPC to one, Global >
        // Factions browses the registry. Both pointed at the browser, so an NPC's faction could
        // not be set at all.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("case ADVANCED_FACTIONS -> npcFactionRows()"),
                "Advanced should assign");
        assertTrue(editor.contains("case GLOBAL_FACTIONS -> factionListRows()"),
                "Global should browse");
    }

    @Test
    void addAndRemoveAreLiveNowThatSomewhereWritableExists() throws IOException {
        // They were disabled for a real reason: factions loaded only from a datapack, and a
        // SimpleJsonResourceReloadListener reads a pack and has nothing to write back to. A button
        // claiming otherwise would have been a lie.
        //
        // The world store is that somewhere, so the buttons do what they say. The whitelist rule is
        // unchanged and still satisfied - a control goes live only once something consumes what it
        // writes, and XenoNpcDataSource consumes this.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        int at = editor.indexOf("private List<EditorRow> factionListRows()");
        assertTrue(at >= 0, "the browser should exist");
        String body = editor.substring(at, Math.min(editor.length(), at + 2600));
        assertTrue(body.contains("new EditorRow.Action(\"Add\""), "Add is live");
        assertTrue(body.contains("this::addFaction"), "and writes through the store packet");
        assertTrue(body.contains("Remove "), "Remove is live for the open faction");
    }

    @Test
    void theGlobalFactionEditorExposesPreviewAndExplicitImportConfirmation() throws IOException {
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("Preview MyNPC factions"));
        assertTrue(editor.contains("Confirm faction import"));
        assertTrue(editor.contains("runFactionImport(false)"));
        assertTrue(editor.contains("runFactionImport(true)"));
        assertTrue(editor.contains("xenonpcimport factions"));
    }

    @Test
    void anIdIsRefusedWithAReasonRatherThanRewritten() throws IOException {
        // An id silently turned into something else is an entry the operator then cannot find.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("XenoNpcStorePaths.reject(newFactionId)"),
                "the screen should check before offering Add");
        assertTrue(editor.contains("disabledAction(\"Add\")"),
                "and disable it while the id is not usable");
    }

    @Test
    void aStoreWriteIsOperatorGatedAndAlwaysAnswers() throws IOException {
        // Same discipline as XenoNpcSavePacket: permission first, and no silent refusal - a write
        // that was rejected must not look like one that worked.
        String packet = source("network/packet/XenoNpcStoreWritePacket.java");
        assertTrue(packet.contains("player.hasPermissions(2)"), "operator only");
        assertTrue(packet.contains("XenoNpcStoreCategory.byOrdinal(category)"),
                "the wire ordinal is bounds-checked, not indexed");
        assertTrue(packet.contains("NpcProfileSaveResultPacket"),
                "every rejection answers the client");
    }

    @Test
    void writingAFactionResyncsEveryone() throws IOException {
        // The registry is server-wide, so everyone sees the change - not just whoever pressed Add.
        assertTrue(source("network/packet/XenoNpcStoreWritePacket.java")
                        .contains("sendToAll(SyncFactionsPacket.current())"),
                "a faction write should re-broadcast the list");
    }

    @Test
    void anEmptyListSaysWhatToDoAboutIt() throws IOException {
        // A blank screen is indistinguishable from a broken one. It now offers both routes: add
        // one here, or ship one in a pack.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("No factions yet."), "say it");
        assertTrue(editor.contains("npcs/factions/"), "and where a pack's would go");
        assertTrue(editor.contains("Add one below"), "and that one can be made here");
    }

    @Test
    void theStaleDisabledReasonIsGone() throws IOException {
        // "no native server registry exists" stopped being true the moment the store landed, and a
        // disabled control giving a reason that is no longer the reason is its own small lie.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        // The row literal, not the word - the comment above it explains what the reason used to
        // say, and a scan that cannot tell code from prose fires on the explanation.
        assertFalse(editor.contains("\"Unavailable - no native server registry exists.\""),
                "the reason string should have moved on with the code");
        assertTrue(editor.contains("The world store holds this; nothing reads it yet."),
                "and say what is actually true now");
    }
}
