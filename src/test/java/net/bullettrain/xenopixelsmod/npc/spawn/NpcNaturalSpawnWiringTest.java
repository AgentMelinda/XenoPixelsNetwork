package net.bullettrain.xenopixelsmod.npc.spawn;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.client.npc.spawn.ClientNaturalSpawns;
import net.bullettrain.xenopixelsmod.network.packet.SyncNaturalSpawnsPacket;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcWorldStore;
import net.minecraft.network.FriendlyByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
 * The parts of natural spawning that are not a decision about one rule: the store round trip, the
 * packet, the client mirror, and the wiring that makes the server tell the client anything at all.
 *
 * <p>The last group is checked by reading the source. There is no headless server here, and a
 * runtime claim about ambient spawning needs a running server and a log — which this file does not
 * pretend to provide. What a source guard does prove is the thing a refactor silently breaks: that
 * the write path validates, that every mutation pushes, that the mirror is cleared on disconnect,
 * and that respawn is switched off <em>after</em> the payload is applied rather than before it.
 */
class NpcNaturalSpawnWiringTest {

    private static NpcNaturalSpawn sample() {
        return new NpcNaturalSpawn("outpost", "Outpost",
                List.of("minecraft:plains", "minecraft:forest"), 12, 3, "guard",
                NpcNaturalSpawn.TIME_DAY);
    }

    @Test
    void aRuleWrittenToTheStoreReadsBackIdentically(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        NpcNaturalSpawn rule = sample();

        assertNull(store.put(XenoNpcStoreCategory.SPAWNS, "", rule.id(), rule.save()));
        assertEquals(rule, NpcNaturalSpawn.load(rule.id(),
                store.get(XenoNpcStoreCategory.SPAWNS, "", rule.id())));

        XenoNpcWorldStore reopened = new XenoNpcWorldStore(dir);
        reopened.loadAll();
        assertEquals(rule, NpcNaturalSpawn.load(rule.id(),
                reopened.get(XenoNpcStoreCategory.SPAWNS, "", rule.id())));
    }

    @Test
    void anEditHasToCarryTheRevisionTheStoreReached(@TempDir Path dir) {
        // Why the editor reads the revision out of the store index instead of sending 0: the second
        // edit of a rule would otherwise be refused as stale, and the page would look like it had
        // simply stopped working after one change.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        NpcNaturalSpawn rule = sample();

        assertNull(store.put(XenoNpcStoreCategory.SPAWNS, "", rule.id(), rule.save(), 0));
        assertEquals(1, store.revisionOf(XenoNpcStoreCategory.SPAWNS, "", rule.id()));
        assertNotNull(store.put(XenoNpcStoreCategory.SPAWNS, "", rule.id(), rule.save(), 0),
                "a write claiming the entry is new must not overwrite an existing one");
        assertNull(store.put(XenoNpcStoreCategory.SPAWNS, "", rule.id(), rule.save(), 1));
        assertEquals(2, store.revisionOf(XenoNpcStoreCategory.SPAWNS, "", rule.id()));
    }

    @Test
    void removingARuleTakesTheFileWithIt(@TempDir Path dir) {
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);
        store.put(XenoNpcStoreCategory.SPAWNS, "", "outpost", sample().save());

        assertNull(store.remove(XenoNpcStoreCategory.SPAWNS, "", "outpost"));
        assertNull(store.get(XenoNpcStoreCategory.SPAWNS, "", "outpost"));
        assertTrue(store.list(XenoNpcStoreCategory.SPAWNS).isEmpty());
    }

    @Test
    void spawnRulesAreNotGroupedSoAGroupIsRefused(@TempDir Path dir) {
        // The category is flat, like factions and banks. A caller that invented a group would
        // otherwise write files the reader never lists.
        XenoNpcWorldStore store = new XenoNpcWorldStore(dir);

        assertNotNull(store.put(XenoNpcStoreCategory.SPAWNS, "1", "outpost", sample().save()));
    }

    @Test
    void thePacketCarriesEveryFieldTheEditorShows() {
        List<NpcNaturalSpawn> rules = List.of(sample(),
                new NpcNaturalSpawn("roadside", "Roadside", List.of(), 1, 9, "traveller",
                        NpcNaturalSpawn.TIME_NIGHT));

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new SyncNaturalSpawnsPacket(rules).encode(buf);
        SyncNaturalSpawnsPacket decoded = new SyncNaturalSpawnsPacket(buf);

        assertEquals(rules, decoded.spawns());
    }

    @Test
    void thePacketIsBoundedRatherThanHuge() {
        List<NpcNaturalSpawn> many = new ArrayList<>();
        for (int i = 0; i < 400; i++) {
            many.add(new NpcNaturalSpawn("rule_" + i, "Rule " + i, List.of("minecraft:plains"),
                    5, 1, "clone", NpcNaturalSpawn.TIME_ANY));
        }

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new SyncNaturalSpawnsPacket(many).encode(buf);
        SyncNaturalSpawnsPacket decoded = new SyncNaturalSpawnsPacket(buf);

        assertEquals(256, decoded.spawns().size(), "a truncated list beats a rejected payload");
        assertEquals("rule_0", decoded.spawns().get(0).id());
    }

    @Test
    void anEmptyPacketDecodesToAnEmptyListNotCrash() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        new SyncNaturalSpawnsPacket(List.of()).encode(buf);

        assertTrue(new SyncNaturalSpawnsPacket(buf).spawns().isEmpty());
    }

    @Test
    void theMirrorKeepsOrderAnswersByIdAndForgetsOnDisconnect() {
        NpcNaturalSpawn first = sample();
        NpcNaturalSpawn second = new NpcNaturalSpawn("zulu", "Zulu", List.of(), 3, 1, "c",
                NpcNaturalSpawn.TIME_ANY);

        ClientNaturalSpawns.accept(List.of(first, second));

        assertEquals(List.of("outpost", "zulu"),
                ClientNaturalSpawns.all().stream().map(NpcNaturalSpawn::id).toList());
        assertEquals(first, ClientNaturalSpawns.get("OUTPOST"), "ids are case-folded like the store");
        assertFalse(ClientNaturalSpawns.isEmpty());

        ClientNaturalSpawns.accept(List.of(first, first));
        assertEquals(1, ClientNaturalSpawns.all().size(), "one id is one row");

        ClientNaturalSpawns.clear();
        assertTrue(ClientNaturalSpawns.isEmpty());
        assertNull(ClientNaturalSpawns.get("outpost"));
    }

    @Test
    void theSpawnerCapsHowManyRulesAreLiveAtOnce() {
        assertTrue(NpcNaturalSpawnService.allowedByCap(0));
        assertTrue(NpcNaturalSpawnService.allowedByCap(NpcNaturalSpawnService.MAX_ALIVE_PER_RULE - 1));
        assertFalse(NpcNaturalSpawnService.allowedByCap(NpcNaturalSpawnService.MAX_ALIVE_PER_RULE));
        assertFalse(NpcNaturalSpawnService.allowedByCap(9999));
    }

    @Test
    void theCategoryRetiredItsReservation() {
        assertTrue(XenoNpcStoreCategory.SPAWNS.hasRuntimeConsumer(),
                "NpcNaturalSpawnService is the reader the reservation asked for");
        assertFalse(XenoNpcStoreCategory.RECIPES.hasRuntimeConsumer(),
                "recipes still have no bench, and the label says so");
        assertFalse(XenoNpcStoreCategory.LINKED.hasRuntimeConsumer(),
                "shared linked templates still have no reader");
    }

    @Test
    void theWireChangeMovedTheProtocol() {
        // A new S2C packet is a wire change: an old client would read the new bytes as nothing at
        // all. Asserted as a floor, like every other protocol check here.
        assertTrue(net.bullettrain.xenopixelsmod.network.ProtocolVersion.current() >= 94,
                "SyncNaturalSpawnsPacket landed with protocol 94");
    }

    @Test
    void theSpawnerReadsTheStoreTurnsRespawnOffAfterApplyingAndCleansUp() throws IOException {
        String source = source("src/main/java/net/bullettrain/xenopixelsmod/npc/spawn",
                "NpcNaturalSpawnService.java");

        assertTrue(source.contains("NpcNaturalSpawns.eligibleAt("),
                "the service must ask the store, not a cache of its own");
        assertTrue(source.contains("GameRules.RULE_DOMOBSPAWNING"),
                "vanilla's natural-spawning switch governs these too");
        int applied = source.indexOf("XenoNpcPayload.apply(");
        int respawnOff = source.indexOf("setRespawnEnabled(false)");
        assertTrue(applied >= 0 && respawnOff > applied,
                "apply() restores RespawnEnabled from the template, so switching it off first is"
                        + " the bug that lets an ambient NPC come back forever");
        assertTrue(source.contains("getPersistentData().putString(MARKER"),
                "a natural spawn must be tellable from a hand-placed one");
        assertTrue(source.contains("discard()"), "and the far-away ones must actually go");
    }

    @Test
    void everyWritePathValidatesAndEveryMutationPushes() throws IOException {
        String write = source("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                "XenoNpcStoreWritePacket.java");
        assertTrue(write.contains("XenoNpcStoreCategory.SPAWNS"));
        assertTrue(write.contains("rejectPayload"),
                "a rule that can never place anything is refused rather than stored dead");
        assertTrue(write.contains("SyncNaturalSpawnsPacket.current()"),
                "docs/xeno-npc-schema.md requires a push at every server-side mutation");

        String join = source("src/main/java/net/bullettrain/xenopixelsmod/npc/faction",
                "XenoFactionSync.java");
        assertTrue(join.contains("SyncNaturalSpawnsPacket.current()"),
                "join and /reload are the other two moments a client learns the list");

        String network = source("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        assertTrue(network.contains("SyncNaturalSpawnsPacket.class"),
                "an unregistered packet is a payload nobody can decode");

        String disconnect = source("src/main/java/net/bullettrain/xenopixelsmod/client",
                "ClientConnectionState.java");
        assertTrue(disconnect.contains("ClientNaturalSpawns.clear()"),
                "one world's rules must not be listed in another");
    }

    @Test
    void theEditorPageAuthorsRulesRatherThanShowingPlaceholders() throws IOException {
        String editor = source("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");

        assertTrue(editor.contains("XenoNpcStoreCategory.SPAWNS.ordinal()"),
                "the page writes through the existing store-write packet");
        assertTrue(editor.contains("addNaturalSpawn") && editor.contains("removeNaturalSpawn"));
        assertTrue(editor.contains("spawnRevision(rule.id())"),
                "an edit must carry the revision the store reached; a literal 0 makes every edit"
                        + " after the first one fail as stale");
        assertTrue(editor.contains("ClientNaturalSpawns"),
                "and lists what the server actually stored");
        assertFalse(editor.contains("Natural Spawns(WIP)"),
                "the page is no longer a placeholder, so the label may not claim it is");
    }

    @Test
    void recipesStayDisabledAndSayWhy() throws IOException {
        // The honest half of this change: nothing invented a consumer for recipes, so the row stays
        // off and the category keeps its reservation.
        String category = source("src/main/java/net/bullettrain/xenopixelsmod/npc/store",
                "XenoNpcStoreCategory.java");
        int declaration = category.indexOf("RECIPES(\"");
        assertTrue(declaration > 0);
        assertTrue(category.substring(Math.max(0, declaration - 700), declaration)
                .contains("Reserved"), "RECIPES must still state its reservation");

        String editor = source("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        int recipes = editor.indexOf("case GLOBAL_RECIPES");
        assertTrue(editor.substring(recipes, recipes + 200).contains("unavailableListRows"),
                "the recipes page keeps its disabled rows");
    }

    @Test
    void linkedPointsAtTheLivePerNpcEditorAndClaimsNothingElse() throws IOException {
        String editor = source("src/main/java/net/bullettrain/xenopixelsmod/client/npc",
                "XenoNpcEditorScreen.java");
        int start = editor.indexOf("private List<EditorRow> globalLinkedRows()");
        String body = editor.substring(start, editor.indexOf("private String newSpawnId"));

        assertTrue(body.contains("ScreenId.ADVANCED_LINKED"),
                "the live per-NPC link editor is reachable from the global page");
        assertFalse(body.contains("XenoNpcStoreCategory.LINKED"),
                "and the page must not author into a category nothing reads");
    }

    private static String source(String directory, String file) throws IOException {
        return Files.readString(RepoRoot.of(directory, file), StandardCharsets.UTF_8);
    }
}
