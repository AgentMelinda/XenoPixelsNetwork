package net.bullettrain.xenopixelsmod.npc.inventory;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.ProtocolVersion;
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
 * The Curios integration: the data file that gives NPCs slots, and the gate that keeps a server
 * without Curios from ever loading a Curios class.
 *
 * <p>Curios cannot be exercised here - it needs a loaded game and a datapack reload - so what is
 * checked is the two things that are checkable and that silently break otherwise: the entity binding
 * naming entity types that really exist, and no Curios symbol escaping {@code NpcCuriosImpl}.
 */
class NpcCuriosWiringTest {

    private static final String DATA = "src/main/resources/data/xenopixelsmod/curios/entities";
    private static final String INV = "src/main/java/net/bullettrain/xenopixelsmod/npc/inventory";
    private static final String ENTITIES = "src/main/java/net/bullettrain/xenopixelsmod/missile";

    private static String read(String dir, String file) throws IOException {
        return Files.readString(RepoRoot.of(dir, file), StandardCharsets.UTF_8);
    }

    private static JsonObject binding() throws IOException {
        return JsonParser.parseString(read(DATA, "xeno_npcs.json")).getAsJsonObject();
    }

    private static List<String> array(JsonObject object, String key) {
        JsonArray raw = object.getAsJsonArray(key);
        List<String> out = new ArrayList<>(raw.size());
        raw.forEach(element -> out.add(element.getAsString()));
        return out;
    }

    // ------------------------------------------------------------ the entity binding

    @Test
    void everyNpcEntityTypeGetsTheSlots() throws IOException {
        // DragonMineZ binds head_tech and weights to minecraft:player only - that is why a Xeno NPC
        // has none by default. This file is what gives them any, and an entity type left out of it
        // is an NPC whose Curios row silently never appears.
        String registrations = read(ENTITIES, "ModEntities.java");
        List<String> declared = array(binding(), "entities");

        int registered = 0;
        for (String line : registrations.split("\\R")) {
            int marker = line.indexOf("npc(\"xeno_npc");
            if (marker < 0) {
                continue;
            }
            registered++;
            String id = line.substring(marker + 5, line.indexOf('"', marker + 5));
            assertTrue(declared.contains("xenopixelsmod:" + id),
                    id + " is a registered NPC type with no Curios binding");
        }
        assertTrue(registered > 0, "the registration file must still declare NPC types");
        assertEquals(registered, declared.size(),
                "the binding must name exactly the registered NPC types");
    }

    @Test
    void theSlotsAreOnesThatAlreadyExistRatherThanNewOnes() throws IOException {
        // Reusing existing ids is the point twice over: DragonMineZ's scouters and weights drop
        // straight in, and a native Xeno NPC ends up with the same ten ordinary slots this mod has
        // always given a MyNPCs or CustomNPCs NPC. Minting our own would give NPCs empty slots
        // nothing could fill.
        assertEquals(NpcCurios.ALL_SLOTS, array(binding(), "slots"));
    }

    @Test
    void theStandardSlotsMatchTheOnesTheOtherNpcsAlreadyGet() throws IOException {
        // NpcCuriosInventory.SLOT_IDS drives the MyNPCs and CustomNPCs integration. Two lists of
        // slot names in two files is how a rename goes wrong, so they are checked against each
        // other rather than both against memory.
        String existing = read("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcCuriosInventory.java");
        for (String slot : NpcCurios.STANDARD_SLOTS) {
            assertTrue(existing.contains('"' + slot + '"'),
                    slot + " is not one of the slots the other NPC integration offers");
        }
        assertEquals(10, NpcCurios.STANDARD_SLOTS.size());
    }

    @Test
    void dragonMineZsTwoSlotsAreOffered() throws IOException {
        // The specific ask: weights, and the head slot its scouters use.
        assertTrue(array(binding(), "slots").containsAll(NpcCurios.DMZ_SLOTS));
    }

    @Test
    void theCodeAndTheDataFileAgreeOnTheSlotNames() throws IOException {
        // Two lists that must match and live in different files is exactly how a rename goes wrong.
        for (String slot : NpcCurios.DMZ_SLOTS) {
            assertTrue(read(DATA, "xeno_npcs.json").contains('"' + slot + '"'),
                    slot + " is named in code but not in the binding");
        }
    }

    // ------------------------------------------------------------ the gate

    /**
     * The files allowed to name a Curios class.
     *
     * <p>{@code NpcCuriosImpl} is this work's; {@code NpcCuriosInventory} is the older integration
     * that gives MyNPCs and CustomNPCs NPCs their slots and has its own {@code ModList} gate. An
     * explicit list rather than a pattern, so adding a third is a deliberate act with a reason
     * rather than something that slips in.
     */
    private static final List<String> MAY_NAME_CURIOS =
            List.of("NpcCuriosImpl.java", "NpcCuriosInventory.java");

    @Test
    void curiosIsNamedOnlyWhereItIsGated() throws IOException {
        // Curios is on the compile classpath but is NOT declared in neoforge.mods.toml, which looks
        // like an undeclared optional dependency - the shape that crashes startup when a class
        // reference escapes its gate. It is not one today: this mod hard-requires dragonminez,
        // which hard-requires curios, so Curios cannot actually be absent. The containment is kept
        // because that chain is somebody else's to change, and keeping the imports in known files
        // makes it checkable by looking rather than by launching.
        List<String> offenders = new ArrayList<>();
        for (java.nio.file.Path file : Files.walk(RepoRoot.of("src/main/java"))
                .filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !MAY_NAME_CURIOS.contains(p.getFileName().toString()))
                .toList()) {
            if (Files.readString(file, StandardCharsets.UTF_8).contains("top.theillusivec4")) {
                offenders.add(file.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "these name a Curios class outside a gated file: " + offenders);
    }

    @Test
    void curiosIsReachedThroughAMandatoryChain() throws IOException {
        // Measured, not assumed: launching a dedicated server without DragonMineZ gets "Mod
        // xenopixelsmod requires dragonminez 2.1.3 or above", and one without Curios gets "Mod
        // dragonminez requires curios 9.5.1+1.21.1 or above". So a Xeno NPC always has Curios
        // available, and the slots this mod binds are always live.
        String toml = read("src/main/resources/META-INF", "neoforge.mods.toml");
        int dmz = toml.indexOf("modId=\"dragonminez\"");
        assertTrue(dmz > 0, "DragonMineZ must still be declared");
        assertTrue(toml.indexOf("type=\"required\"", dmz) - dmz < 120,
                "and required, which is what makes Curios transitively certain");
    }

    @Test
    void theOlderIntegrationIsGatedToo() throws IOException {
        // It predates this work and is not being changed, but it is on the same list, so the same
        // thing has to be true of it.
        String existing = read("src/main/java/net/bullettrain/xenopixelsmod/compat/npc",
                "NpcCuriosInventory.java");
        assertTrue(existing.contains("ModList"),
                "NpcCuriosInventory must check whether Curios is present");
    }

    @Test
    void theGateChecksTheModListBeforeTouchingTheImplementation() throws IOException {
        String gate = read(INV, "NpcCurios.java");
        assertTrue(gate.contains("ModList"), "presence must be checked, not assumed");
        assertTrue(gate.contains("NpcCuriosImpl"), "and the implementation reached only through it");
    }

    @Test
    void everyCallDegradesRatherThanThrowing() throws IOException {
        // A Curios version whose API moved must leave an NPC with no curios slots, not take the
        // screen down. Every public gate method gets its own guard.
        String gate = read(INV, "NpcCurios.java");
        assertEquals(5, gate.split("catch \\(Throwable ignored\\)", -1).length - 1,
                "every entry point into the implementation needs its own guard");
    }

    @Test
    void anAbsentCuriosAnswersEmptyForEveryQuestion() {
        // Driven directly rather than inferred from the source, because "returns empty" is the
        // contract the menu and the service both rely on.
        NpcCurios.overridePresence(Boolean.FALSE);
        try {
            assertTrue(NpcCurios.slots(null).isEmpty());
            assertTrue(NpcCurios.get(null, "weights", 0).isEmpty());
            assertFalse(NpcCurios.set(null, "weights", 0, null));
            assertEquals(0, NpcCurios.size(null, "weights"));
        } finally {
            NpcCurios.overridePresence(null);
        }
    }

    // ------------------------------------------------------------ the wire

    @Test
    void theProtocolWasBumpedForTheNewPacket() throws IOException {
        // A new packet is a wire change, and a client on the old protocol must be refused rather
        // than left sending an id the server reads as something else.
        assertTrue(ProtocolVersion.current() >= 88,
                "opening the inventory screen added a packet; the protocol is a floor, not a pin");
    }

    @Test
    void theNewPacketIsAppendedRatherThanInserted() throws IOException {
        // Ids are handed out sequentially by id++, so slotting one into the middle silently
        // repoints every packet after it.
        String network = read("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        int inventory = network.indexOf("XenoNpcInventoryOpenPacket.class");
        int lastExisting = network.lastIndexOf("NpcStoreDialoguePacket.class");
        assertTrue(inventory > lastExisting,
                "the new packet must be registered after every packet that predates it");
    }
}
