package net.bullettrain.xenopixelsmod.npc.store;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.store.ClientNpcStoreIndex;
import net.bullettrain.xenopixelsmod.network.ProtocolVersion;
import net.bullettrain.xenopixelsmod.network.packet.SyncNpcStoreIndexPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Getting the world store's contents to the client.
 *
 * <p>The store reads and writes files under the world folder and is server-side, so a client knows
 * nothing about it until told. That is the same trap the mark icons, the bubble palettes and the
 * faction list each hit; this is the fourth time, and the reason the editor's Global screens showed
 * nothing but disabled buttons for so long.
 *
 * <p>An <em>index</em> rather than the entries themselves: listing needs a name, editing needs the
 * whole thing and only for one entry at a time. Broadcasting every dialogue in the world to every
 * client would be paying continuously for something almost nobody opens.
 */
class NpcStoreIndexSyncTest {

    private static String source(String relative) throws IOException {
        Path path = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod").resolve(relative);
        assertTrue(Files.exists(path), path + " should exist");
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static SyncNpcStoreIndexPacket roundTrip(SyncNpcStoreIndexPacket sent) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        SyncNpcStoreIndexPacket received = new SyncNpcStoreIndexPacket(buf);
        assertEquals(0, buf.readableBytes(),
                "the decoder left bytes on the buffer, so encode and decode disagree");
        return received;
    }

    private static ClientNpcStoreIndex.Entry entry(XenoNpcStoreCategory category, String group,
                                                   String id, String label) {
        return new ClientNpcStoreIndex.Entry(category, group, id, label);
    }

    @Test
    void theIndexSurvivesTheWire() {
        SyncNpcStoreIndexPacket got = roundTrip(new SyncNpcStoreIndexPacket(List.of(
                entry(XenoNpcStoreCategory.FACTIONS, "", "guards", "Guards"),
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "greeting", "Greeting"))));

        assertEquals(2, got.entries().size());
        assertEquals(XenoNpcStoreCategory.FACTIONS, got.entries().get(0).category());
        assertEquals("guards", got.entries().get(0).id());
        assertEquals("Guards", got.entries().get(0).label());
        assertEquals("npc", got.entries().get(1).group());
    }

    @Test
    void anUnknownCategorySkipsItsEntryRatherThanTheRest() {
        // Bounds-checked rather than indexed, because the ordinal came off the wire. Dropping the
        // remaining entries with it would turn one unknown category into an empty screen.
        assertNull(XenoNpcStoreCategory.byOrdinal(99));
        assertNull(XenoNpcStoreCategory.byOrdinal(-1));
    }

    @Test
    void aLongLabelIsClampedRatherThanRejected() {
        // writeUtf throws past its cap, which would drop the whole packet and leave every Global
        // screen empty because one entry had a wordy name.
        SyncNpcStoreIndexPacket got = roundTrip(new SyncNpcStoreIndexPacket(List.of(
                entry(XenoNpcStoreCategory.FACTIONS, "", "guards", "x".repeat(4000)))));
        assertEquals(128, got.entries().get(0).label().length());
    }

    @Test
    void anEntryWithNoNameFallsBackToItsId() {
        // A blank row is one the operator cannot tell from any other.
        assertEquals("greeting",
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "greeting", "").label());
        assertEquals("greeting",
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "greeting", null).label());
    }

    @Test
    void theClientCacheStartsEmptyAndTakesWhatItIsGiven() {
        ClientNpcStoreIndex.clear();
        assertTrue(ClientNpcStoreIndex.isEmpty(XenoNpcStoreCategory.DIALOGS));

        ClientNpcStoreIndex.accept(List.of(
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "a", "A"),
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "b", "B"),
                entry(XenoNpcStoreCategory.FACTIONS, "", "guards", "Guards")));

        assertEquals(2, ClientNpcStoreIndex.of(XenoNpcStoreCategory.DIALOGS).size());
        assertEquals(1, ClientNpcStoreIndex.of(XenoNpcStoreCategory.FACTIONS).size());
        assertTrue(ClientNpcStoreIndex.isEmpty(XenoNpcStoreCategory.BANKS));
    }

    @Test
    void orderIsPreserved() {
        // The server sends them sorted. A list that reshuffled between reloads would be worse than
        // one that is merely long - and this is the third place that Map.copyOf bug has surfaced.
        ClientNpcStoreIndex.clear();
        List<ClientNpcStoreIndex.Entry> sent = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            sent.add(entry(XenoNpcStoreCategory.DIALOGS, "npc", "d" + i, "D" + i));
        }
        ClientNpcStoreIndex.accept(sent);

        List<String> ids = new ArrayList<>();
        ClientNpcStoreIndex.of(XenoNpcStoreCategory.DIALOGS).forEach(e -> ids.add(e.id()));
        assertEquals(List.of("d0", "d1", "d2", "d3", "d4", "d5",
                "d6", "d7", "d8", "d9", "d10", "d11"), ids);
    }

    @Test
    void acceptReplacesRatherThanMerges() {
        // An entry the operator deleted has to disappear here too, or the editor keeps offering
        // one the server no longer has and fails confusingly when it is opened.
        ClientNpcStoreIndex.clear();
        ClientNpcStoreIndex.accept(List.of(
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "old", "Old")));
        ClientNpcStoreIndex.accept(List.of(
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "new", "New")));

        assertEquals(1, ClientNpcStoreIndex.of(XenoNpcStoreCategory.DIALOGS).size());
        assertNull(ClientNpcStoreIndex.find(XenoNpcStoreCategory.DIALOGS, "npc", "old"));
        assertNotNull(ClientNpcStoreIndex.find(XenoNpcStoreCategory.DIALOGS, "npc", "new"));
    }

    @Test
    void clearingLeavesNothingBehind() {
        // On disconnect, so one world's content cannot show up in the next.
        ClientNpcStoreIndex.accept(List.of(
                entry(XenoNpcStoreCategory.DIALOGS, "npc", "a", "A")));
        ClientNpcStoreIndex.clear();
        for (XenoNpcStoreCategory category : XenoNpcStoreCategory.values()) {
            assertTrue(ClientNpcStoreIndex.isEmpty(category), category.folder());
        }
    }

    @Test
    void theIndexRidesTheSameHookAsTheFactionSync() throws IOException {
        // Both reach a client for the same reason and at the same two moments - on join and after
        // a reload. A second event would only create a window where one had arrived and the other
        // had not.
        String sync = source("npc/faction/XenoFactionSync.java");
        assertTrue(sync.contains("SyncNpcStoreIndexPacket.current()"));
        assertTrue(sync.contains("sendToAll(index)"), "a reload reaches everyone");
        assertTrue(sync.contains("sendToPlayer(joining, index)"), "and a join reaches the joiner");
    }

    @Test
    void anyWriteResyncsEveryone() throws IOException {
        String packet = source("network/packet/XenoNpcStoreWritePacket.java");
        assertTrue(packet.contains("sendToAll(SyncNpcStoreIndexPacket.current())"),
                "a second operator's screen must not keep listing what the first deleted");
    }

    @Test
    void globalDialogsIsLiveBecauseSomethingReadsIt() throws IOException {
        // The rule this codebase holds to: a control goes live once its value is consumed, and not
        // before. XenoNpcDataSource.dialogue resolves a role's dialogue through the store, so the
        // screen has a real consumer behind it.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        assertTrue(editor.contains("case GLOBAL_DIALOGS -> globalDialogRows()"));
        assertFalse(editor.contains("unavailableListRows(\"Dialogs\")"),
                "the disabled placeholder should be gone");
        assertTrue(source("npc/store/XenoNpcDataSource.java").contains("storedDialogue(id)"),
                "and the consumer should exist");
    }

    @Test
    void aNewDialogStartsEmptySoItShadowsNothingYet() throws IOException {
        // XenoDialogueNbt.read answers null for a dialogue with no usable start node, and every
        // caller falls through on null. An Add that immediately silenced every NPC of a role would
        // be a worse first step than one that makes an empty shell to fill.
        String editor = source("client/npc/XenoNpcEditorScreen.java");
        int at = editor.indexOf("private void addStoreDialog()");
        assertTrue(at >= 0);
        assertTrue(editor.substring(at, at + 700).contains("XenoDialogueNbt.empty()"));
    }

    @Test
    void bothClientMirrorsAreClearedOnDisconnect() throws IOException {
        // Neither was. Quitting to the menu and opening a different save left the previous world's
        // factions and store entries listed in the editor - worse than an empty screen, because
        // they look real until something is opened.
        String state = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client",
                        "ClientConnectionState.java"), StandardCharsets.UTF_8);
        assertTrue(state.contains("ClientNpcStoreIndex.clear()"), "the store index");
        assertTrue(state.contains("ClientFactions.accept("), "and the faction list");
    }

    @Test
    void theProtocolMovedBecauseTheWireShapeDid() {
        assertTrue(ProtocolVersion.current() >= 82,
                "carrying the entry revision required protocol 82 or later");
    }
}
