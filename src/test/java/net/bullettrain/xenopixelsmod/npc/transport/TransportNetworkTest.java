package net.bullettrain.xenopixelsmod.npc.transport;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transport destinations move from each NPC's NBT into a shared network.
 *
 * <p>A transport network is shared by its nature — two transporters in one city offer the same
 * places — so a private copy per NPC meant renaming a destination required finding and editing
 * every NPC that listed it. The NPC now holds only the network's id.
 *
 * <p>This is the second worked example of the ownership rule in {@code docs/xeno-npc-schema.md}
 * §0; {@code NpcDialogSlots} was the first.
 */
class TransportNetworkTest {

    private static String code(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8)
                .replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static TransportDestination dest(String id, TransportDestination.Unlock unlock) {
        return new TransportDestination(id, id, "minecraft:overworld", 10, 64, -10, 90f, unlock);
    }

    // ------------------------------------------------------------ the network

    @Test
    void theIdComesFromTheFilenameNotTheContents() {
        // One fact, one place - the same rule the faction store follows.
        CompoundTag tag = new TransportNetwork("capital", "Capital").save();
        assertFalse(tag.contains("Id"));
        assertEquals("elsewhere", TransportNetwork.load("elsewhere", tag).id());
    }

    @Test
    void aNetworkSurvivesASaveAndLoadWithItsDestinationsInOrder() {
        TransportNetwork network = new TransportNetwork("cities", "Cities");
        network.add(dest("capital", TransportDestination.Unlock.ALWAYS));
        network.add(dest("harbour", TransportDestination.Unlock.VISITED));

        TransportNetwork read = TransportNetwork.load("cities", network.save());
        assertEquals("Cities", read.name());
        assertEquals(List.of("capital", "harbour"),
                read.all().stream().map(TransportDestination::id).toList());
    }

    @Test
    void theVisibilityRuleCameAcrossUnchanged() {
        TransportNetwork network = new TransportNetwork("n", "N");
        network.add(dest("open", TransportDestination.Unlock.ALWAYS));
        network.add(dest("secret", TransportDestination.Unlock.VISITED));

        assertEquals(List.of("open"),
                network.visibleTo(Set.of()).stream().map(TransportDestination::id).toList());
        assertEquals(2, network.visibleTo(Set.of("secret")).size());
    }

    @Test
    void theCapCameAcrossUnchanged() {
        TransportNetwork network = new TransportNetwork("n", "N");
        for (int i = 0; i < TransportNetwork.MAX_DESTINATIONS + 5; i++) {
            network.add(dest("d" + i, TransportDestination.Unlock.ALWAYS));
        }
        assertEquals(TransportNetwork.MAX_DESTINATIONS, network.size());
    }

    @Test
    void aTagClaimingMoreThanTheCapIsTruncatedOnRead() {
        CompoundTag tag = new CompoundTag();
        net.minecraft.nbt.ListTag oversized = new net.minecraft.nbt.ListTag();
        for (int i = 0; i < 100; i++) {
            oversized.add(dest("d" + i, TransportDestination.Unlock.ALWAYS).save());
        }
        tag.put("Destinations", oversized);
        assertEquals(TransportNetwork.MAX_DESTINATIONS,
                TransportNetwork.load("n", tag).size());
    }

    @Test
    void lookupMissesSafely() {
        TransportNetwork network = new TransportNetwork("n", "N");
        network.add(dest("capital", TransportDestination.Unlock.ALWAYS));
        assertEquals("capital", network.byId("CAPITAL").id());
        assertNull(network.byId("nowhere"));
    }

    // ------------------------------------------------------------ the profile

    @Test
    void anNpcHoldsAReferenceAndNotAList() {
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.transportNetwork = "cities";
        assertEquals("cities", NpcCombatProfile.fromTag(profile.toTag()).transportNetwork);
    }

    @Test
    void anNpcThatServesNothingWritesNothing() {
        assertFalse(new NpcCombatProfile().toTag().contains("TransportNetwork"));
    }

    @Test
    void anInlineListFromAnOlderSaveIsHeldAsideRatherThanReadAsCurrentState() {
        // Reading it as current state would give one fact two homes; dropping it would delete an
        // operator's work. It is held for the migration and never written back.
        CompoundTag old = new NpcCombatProfile().toTag();
        NpcTransportList legacy = new NpcTransportList();
        legacy.add(dest("capital", TransportDestination.Unlock.ALWAYS));
        legacy.saveTo(old);

        NpcCombatProfile read = NpcCombatProfile.fromTag(old);
        assertTrue(read.transportNetwork.isEmpty(), "no network yet");
        assertFalse(read.legacyTransports == null, "but the destinations are not lost");
        assertEquals(1, read.legacyTransports.size());
    }

    @Test
    void aMigratedNpcStopsCarryingTheInlineList() {
        // One fact, one home. Re-saving must not write the old key back.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.transportNetwork = "cities";
        assertFalse(profile.toTag().contains("Transports"));
    }

    // ------------------------------------------------------------ the migration

    @Test
    void theMigrationIsNotOnThePerTickPath() throws IOException {
        // NpcCombatProfile.read is called for every profiled NPC every tick. A store lookup there
        // would be a per-NPC per-tick cost, forever, for something that happens once.
        String migration = code("src/main/java/net/bullettrain/xenopixelsmod/npc/transport",
                "TransportMigration.java");
        assertTrue(migration.contains("EntityJoinLevelEvent"), "once per entity, not per tick");
        assertTrue(migration.contains("isClientSide()"), "and server-side only");
    }

    @Test
    void aFailedMigrationLeavesTheDestinationsOnTheNpc() throws IOException {
        // Nothing is lost: the inline list stays in NBT and is retried on the next load.
        String migration = code("src/main/java/net/bullettrain/xenopixelsmod/npc/transport",
                "TransportMigration.java");
        int failed = migration.indexOf("written == null");
        assertTrue(failed >= 0);
        int cleared = migration.indexOf("profile.legacyTransports = null", failed);
        int returned = migration.indexOf("return;", failed);
        assertTrue(returned > 0 && (cleared < 0 || cleared > returned),
                "the legacy list must not be cleared on the failure path");
    }

    @Test
    void migrationNeverOverwritesANetworkSomebodyBuiltByHand() throws IOException {
        String networks = code("src/main/java/net/bullettrain/xenopixelsmod/npc/transport",
                "TransportNetworks.java");
        assertTrue(networks.contains("get(network.id()) != null"),
                "putIfAbsent must refuse an id that is already taken");
    }

    @Test
    void aNamelessNpcStillGetsATraceableId() {
        // Ugly and traceable beats random and unconnectable.
        assertEquals("", TransportMigration.sanitise("!!!"));
        assertEquals("town_guard", TransportMigration.sanitise("  Town  Guard!  "));
    }

    // ------------------------------------------------------------ the readers

    @Test
    void bothServerReadersResolveThroughTheStore() throws IOException {
        for (String[] where : new String[][]{
                {"src/main/java/net/bullettrain/xenopixelsmod/npc/transport", "TransportMenu.java"},
                {"src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcTravelPacket.java"}}) {
            String source = code(where[0], where[1]);
            assertTrue(source.contains("TransportNetworks"), where[1] + " resolves the network");
            assertFalse(source.contains("profile.transports."), where[1] + " reads no inline list");
        }
    }

    @Test
    void theTravelPacketStillChecksEverythingItDidBefore() throws IOException {
        // The move must not relax authority. The client still names an id and nothing else.
        String packet = code("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcTravelPacket.java");
        assertTrue(packet.contains("role() != XenoNpcRole.TRANSPORTER"));
        assertTrue(packet.contains("MAX_DISTANCE_SQ"));
        assertTrue(packet.contains("Unlock.VISITED"));
        assertTrue(packet.contains("network.byId(destinationId)"),
                "and the network is the authority for what this NPC offers");
    }

    @Test
    void theStoreCategoryIsTheOneThatWasSittingEmpty() {
        assertEquals("transport", XenoNpcStoreCategory.TRANSPORT.folder());
        assertFalse(XenoNpcStoreCategory.TRANSPORT.grouped(),
                "ungrouped, so the store takes an empty group");
    }
}
