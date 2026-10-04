package net.bullettrain.xenopixelsmod.npc.transport;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.XenoNpcRole;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Transporter role, end to end.
 *
 * <p>The menu is built on the dialogue bubbles rather than a screen of its own: they already float
 * above the NPC, already take a click without locking the mouse, and are already what a player
 * expects when an NPC has something to offer.
 */
class TransporterWiringTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static TransportDestination dest(String id, TransportDestination.Unlock unlock) {
        return new TransportDestination(id, id, "minecraft:overworld", 1, 64, 1, 0f, unlock);
    }

    @Test
    void theRoleExistsAndHasItsOwnEntityType() throws IOException {
        // A new registry id is additive: no existing world names it, so nothing is reclassified.
        assertEquals("transporter", XenoNpcRole.TRANSPORTER.id());
        String entities = code("src/main/java/net/bullettrain/xenopixelsmod/missile",
                "ModEntities.java");
        assertTrue(entities.contains("xeno_npc_transporter"));
        assertTrue(entities.contains("case TRANSPORTER -> XENO_NPC_TRANSPORTER.get()"));
    }

    @Test
    void theNpcRidesAReferenceAndSurvivesASaveAndLoad() {
        // Destinations moved into a shared network on 2026-09-23; the NPC carries only the id.
        // What it points at is TransportNetworkTest's subject, not this one's.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.transportNetwork = "cities";
        assertEquals("cities", NpcCombatProfile.fromTag(profile.toTag()).transportNetwork);
    }

    @Test
    void anOrdinaryNpcCarriesNoTransportTag() {
        assertFalse(new NpcCombatProfile().toTag().contains("Transports"));
    }

    @Test
    void theMenuOffersOneOptionPerVisibleDestinationPlusAWayOut() {
        // A player looks for an explicit "never mind"; sneak also closes the bubbles, but an
        // option is what a list is expected to have.
        XenoDialogue menu = TransportMenu.build(List.of(
                dest("a", TransportDestination.Unlock.ALWAYS),
                dest("b", TransportDestination.Unlock.ALWAYS)));
        XenoDialogue.Node root = menu.startNode();
        assertEquals(3, root.options().size());
        assertEquals(XenoDialogue.OptionType.QUIT,
                root.options().get(2).type(), "the last option leaves");
    }

    @Test
    void anOptionCarriesTheDestinationIdAndNothingAboutWhereItIs() {
        // Coordinates never reach the client, so the worst a crafted reply can ask for is a
        // destination this NPC genuinely offers.
        XenoDialogue menu = TransportMenu.build(List.of(
                dest("capital", TransportDestination.Unlock.ALWAYS)));
        XenoDialogue.Option option = menu.startNode().options().get(0);
        assertEquals(XenoDialogue.OptionType.TRANSPORT, option.type());
        assertEquals("capital", option.target());
    }

    @Test
    void theServerChecksEverythingTheClientCouldHaveLiedAbout() throws IOException {
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcTravelPacket.java");
        assertTrue(packet.contains("role() != XenoNpcRole.TRANSPORTER"), "the entity is a transporter");
        assertTrue(packet.contains("MAX_DISTANCE_SQ"), "the player is close enough");
        assertTrue(packet.contains("network.byId(destinationId)"),
                "the NPC's network is the authority for what it offers");
        assertTrue(packet.contains("Unlock.VISITED"), "and the player unlocked it");
    }

    @Test
    void theUnlockIsRecheckedServerSideEvenThoughTheClientWasFiltered() throws IOException {
        // The list the client was sent is a display, never a permission.
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcTravelPacket.java");
        int filter = packet.indexOf("unlockedFor(player)");
        int check = packet.indexOf("unlocked.contains(");
        assertTrue(filter >= 0 && check > filter);
    }

    @Test
    void arrivingUnlocksTheDestinationForNextTime() throws IOException {
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcTravelPacket.java");
        assertTrue(packet.contains("data.unlockTransport("),
                "otherwise a VISITED destination could never become reachable");
    }

    @Test
    void anEmptyTransporterTalksRatherThanOpeningAnEmptyMenu() throws IOException {
        String menu = code("src/main/java/net/bullettrain/xenopixelsmod/npc/transport",
                "TransportMenu.java");
        assertTrue(menu.contains("visible.isEmpty()"));
        assertTrue(menu.contains("return false"), "falling through lets the NPC speak");
    }

    @Test
    void theKeyIsWhitelistedAndTheEditorWritesIt() throws IOException {
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcSavePolicy.java").contains("\"TransportNetwork\""));
        assertTrue(code("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java").contains("profile.transportNetwork"));
    }

    @Test
    void theProtocolWasBumpedForTheNewPacket() throws IOException {
        String network = code("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        assertTrue(net.bullettrain.xenopixelsmod.network.ProtocolVersion.current() >= 85,
                "the travel packet needed at least protocol 85");
        assertTrue(network.contains("XenoNpcTravelPacket.class"));
    }
}
