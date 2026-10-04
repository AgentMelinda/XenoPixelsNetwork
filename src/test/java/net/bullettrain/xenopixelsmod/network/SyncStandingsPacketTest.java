package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.faction.ClientStandings;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This player's faction standings, as the client knows them.
 *
 * <p>Looked up by id rather than iterated, so the server-side {@code Map.copyOf} behind
 * {@code factionStandings()} - which is unordered, and has silently scrambled documented-ordered
 * data three times in this subsystem - cannot affect the order rows are drawn in. The panel walks
 * {@code ClientFactions.all()}, which is ordered, and asks this holder one faction at a time.
 */
class SyncStandingsPacketTest {

    @Test
    void aStoredStandingIsReturned() {
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(250, ClientStandings.of("saiyan", 0));
    }

    @Test
    void anUnknownFactionFallsBackToItsDefault() {
        // The same rule XenoPlayerData.getFactionStanding applies server-side: a faction the
        // player has never interacted with reads as that faction's own default, not as zero -
        // defaulting to zero would read as "neutral" for a faction whose default is hostile.
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(-100, ClientStandings.of("frieza_force", -100));
    }

    @Test
    void lookupIsCaseInsensitive() {
        // Ids are lower-cased everywhere else in this subsystem; a screen that missed on case
        // would silently show the fallback for a standing the player actually has.
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(250, ClientStandings.of("Saiyan", 0));
    }

    @Test
    void aBlankOrNullIdIsTheFallback() {
        ClientStandings.accept(Map.of("saiyan", 250));
        assertEquals(7, ClientStandings.of("", 7));
        assertEquals(7, ClientStandings.of(null, 7));
    }

    @Test
    void disconnectForgetsThem() {
        // Otherwise world A's standings are shown against world B's factions.
        ClientStandings.accept(Map.of("saiyan", 250));
        ClientStandings.clear();
        assertEquals(0, ClientStandings.of("saiyan", 0));
    }

    @Test
    void moreStandingsThanTheCapAreTruncatedNotThrown() {
        Map<String, Integer> many = new HashMap<>();
        for (int i = 0; i < ClientStandings.MAX_STANDINGS + 10; i++) {
            many.put("f" + i, i);
        }
        ClientStandings.accept(many);
        // Truncation is by count, so which entries survive is unspecified. What matters is that a
        // hostile server cannot make the client allocate without bound.
        assertEquals(ClientStandings.MAX_STANDINGS, ClientStandings.size());
    }

    @Test
    void standingsRideTheSamePushAsTheQuestLog() throws IOException {
        // A separate trigger would fire at the same moments and open a window where one had
        // arrived and the other had not - the panel would then draw new factions against old
        // standings.
        String sync = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/features/progression",
                        "QuestSync.java"), StandardCharsets.UTF_8);
        assertTrue(sync.contains("SyncStandingsPacket.forPlayer(player)"));
    }

    @Test
    void disconnectClearsThemToo() throws IOException {
        String reset = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client/npc/quest",
                        "ClientQuestReset.java"), StandardCharsets.UTF_8);
        assertTrue(reset.contains("ClientStandings.clear()"));
    }
}
