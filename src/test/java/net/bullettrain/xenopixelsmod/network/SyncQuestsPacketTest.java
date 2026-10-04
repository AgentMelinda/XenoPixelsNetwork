package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.quest.ClientQuests;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client's quest mirror.
 *
 * <p>Wire encode/decode needs a {@code FriendlyByteBuf}, which needs Netty buffers a plain unit
 * test has no server to supply; the caps and the mirror's own rules are what these pin. The round
 * trip itself is an in-game check, listed in the plan's verification section.
 */
class SyncQuestsPacketTest {

    private static ClientQuests.Entry entry(String id) {
        return new ClientQuests.Entry(id, id, "", "", 0, 1, false, "");
    }

    @Test
    void neverSyncedIsDistinctFromNoQuests() {
        // Two states that must not read the same on screen: "the server said you have none" and
        // "the server has not said anything yet". ClientFactions draws the same distinction for
        // the same reason.
        ClientQuests.clear();
        assertFalse(ClientQuests.isSynced(), "nothing has arrived yet");

        ClientQuests.accept(List.of());
        assertTrue(ClientQuests.isSynced(), "an empty sync is still a sync");
        assertTrue(ClientQuests.all().isEmpty());
    }

    @Test
    void disconnectClearsTheLog() {
        // Otherwise logging into world B shows world A's quests, because this holder is static.
        ClientQuests.accept(List.of(entry("wolf_trouble")));
        assertFalse(ClientQuests.all().isEmpty());

        ClientQuests.clear();
        assertTrue(ClientQuests.all().isEmpty());
        assertFalse(ClientQuests.isSynced(), "a cleared log is back to not-yet-synced");
    }

    @Test
    void theOrderTheServerSentIsTheOrderKept() {
        // Map.copyOf is unordered and has silently scrambled documented-ordered data three times
        // in this subsystem already. The server sends quests in start order and the log walks
        // them in that order.
        ClientQuests.clear();
        ClientQuests.accept(List.of(entry("c"), entry("a"), entry("b")));
        assertEquals(List.of("c", "a", "b"),
                ClientQuests.all().stream().map(ClientQuests.Entry::id).toList());
    }

    @Test
    void aBlankIdIsDropped() {
        // It would render as an empty row that cannot be selected or completed.
        ClientQuests.clear();
        ClientQuests.accept(List.of(entry("real"), entry("  ")));
        assertEquals(1, ClientQuests.all().size());
    }

    @Test
    void moreQuestsThanTheCapAreTruncatedNotThrown() {
        // A hostile or buggy server must not be able to make the client allocate without bound.
        ClientQuests.clear();
        List<ClientQuests.Entry> many = new ArrayList<>();
        for (int i = 0; i < ClientQuests.MAX_QUESTS + 10; i++) {
            many.add(entry("q" + i));
        }
        ClientQuests.accept(many);
        assertEquals(ClientQuests.MAX_QUESTS, ClientQuests.all().size());
    }

    @Test
    void anEntryWithNoTitleFallsBackToItsId() {
        // Quest definitions are datapack state. A client on a quest whose definition it was never
        // sent must get a row it can read and select, not a blank line.
        ClientQuests.Entry unknown =
                new ClientQuests.Entry("wolf_trouble", "", "", "", 2, 5, false, "");
        assertEquals("wolf_trouble", unknown.title());
    }

    @Test
    void progressIsClampedIntoItsTarget() {
        // A server that says 9/5 would draw a bar past its own end.
        ClientQuests.Entry silly = new ClientQuests.Entry("q", "q", "", "", 9, 5, false, "");
        assertEquals(5, silly.progress());
        ClientQuests.Entry negative = new ClientQuests.Entry("q", "q", "", "", -3, 5, false, "");
        assertEquals(0, negative.progress());
    }

    @Test
    void theProtocolWasBumpedAndBothPacketsAppended() throws IOException {
        // A wire change without a protocol bump lets a stale client connect and misread every
        // packet after the new one, because ids are positional.
        String network = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network", "ModNetwork.java"),
                StandardCharsets.UTF_8);
        // A floor, not a pin: the quest packets required 83, and what this must catch is a wire
        // change shipped without touching the version at all. Pinning the digit only guaranteed
        // this test broke on somebody else's unrelated bump - see ProtocolVersion.
        assertTrue(ProtocolVersion.current() >= 83,
                "quest packets needed at least protocol 83");

        int factions = network.indexOf("SyncFactionsPacket.class, id++");
        int quests = network.indexOf("SyncQuestsPacket.class,");
        int standings = network.indexOf("SyncStandingsPacket.class,");
        assertTrue(factions >= 0 && quests >= 0 && standings >= 0);
        assertTrue(quests > factions, "the new packets are appended, never inserted");
        assertTrue(standings > quests, "and appended in a fixed order relative to each other");
    }
}
