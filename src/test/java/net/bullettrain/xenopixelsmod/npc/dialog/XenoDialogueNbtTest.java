package net.bullettrain.xenopixelsmod.npc.dialog;

import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A dialogue stored on the NPC rather than in a datapack.
 *
 * <p>Authoring one from the editor was the last thing the dialogue system could not do, and the
 * obstacle was where it lived: {@code XenoDialogues} is a {@code SimpleJsonResourceReloadListener},
 * which reads a pack and has nothing to write back to. So an NPC's own dialogue goes on its combat
 * profile, which already persists with the entity and already travels to the editor and back
 * through the save whitelist - the same place the attack slots and the bubble palettes live.
 */
class XenoDialogueNbtTest {

    private static XenoDialogue sample() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node("Well?", List.of(
                new XenoDialogue.Option("Go on", XenoDialogue.OptionType.TEXT, "more", "", ""),
                new XenoDialogue.Option("Bye", XenoDialogue.OptionType.QUIT, "", "", ""))));
        nodes.put("more", new XenoDialogue.Node("Then train.", List.of(
                new XenoDialogue.Option("I will", XenoDialogue.OptionType.QUEST, "",
                        "xenopixelsmod:kill_mobs", ""))));
        return new XenoDialogue("root", nodes);
    }

    @Test
    void aDialogueSurvivesTheRoundTrip() {
        XenoDialogue got = XenoDialogueNbt.read(XenoDialogueNbt.write(sample()));
        assertNotNull(got);
        assertEquals("root", got.start());
        assertEquals(List.of("root", "more"), new ArrayList<>(got.nodes().keySet()));
        assertEquals("Then train.", got.nodes().get("more").text());
        assertEquals(XenoDialogue.OptionType.QUEST,
                got.nodes().get("more").options().get(0).type());
        assertEquals("xenopixelsmod:kill_mobs",
                got.nodes().get("more").options().get(0).quest());
    }

    @Test
    void nodesAreStoredAsAListBecauseCompoundsDoNotKeepOrder() {
        // Node order is what the editor lists and pages through. A compound keyed by node name
        // would have shuffled it, which is the same bug the faction list and XenoDialogue itself
        // both had with Map.copyOf.
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        for (int i = 0; i < 12; i++) {
            nodes.put("n" + i, new XenoDialogue.Node("line " + i, List.of()));
        }
        XenoDialogue got = XenoDialogueNbt.read(
                XenoDialogueNbt.write(new XenoDialogue("n0", nodes)));
        assertEquals(new ArrayList<>(nodes.keySet()), new ArrayList<>(got.nodes().keySet()));
    }

    @Test
    void anEmptyTagIsNoDialogueRatherThanAnEmptyOne() {
        // Null is what callers fall through on, to the dialogue the NPC's role names. An empty
        // dialogue that was merely present would shadow the role's and leave the NPC silent.
        assertNull(XenoDialogueNbt.read(null));
        assertNull(XenoDialogueNbt.read(new CompoundTag()));
        assertNull(XenoDialogueNbt.read(XenoDialogueNbt.empty()));
        assertFalse(XenoDialogueNbt.isPresent(XenoDialogueNbt.empty()));
    }

    @Test
    void aStartThatNamesNothingIsNoDialogue() {
        // Only reachable by renaming a node without moving the start. An NPC that says nothing is
        // a better outcome than one that opens an empty bubble.
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("somewhere", new XenoDialogue.Node("hi", List.of()));
        assertNull(XenoDialogueNbt.read(
                XenoDialogueNbt.write(new XenoDialogue("nowhere", nodes))));
    }

    @Test
    void anUnknownOptionTypeReadsAsQuitRatherThanBreakingTheDialogue() {
        CompoundTag tag = XenoDialogueNbt.write(sample());
        tag.getList("Nodes", 10).getCompound(0).getList("Options", 10).getCompound(0)
                .putString("Type", "TELEPORT_TO_THE_MOON");
        XenoDialogue got = XenoDialogueNbt.read(tag);
        assertNotNull(got);
        assertEquals(XenoDialogue.OptionType.QUIT, got.startNode().options().get(0).type());
    }

    @Test
    void oversizedContentIsClampedOnBothSides() {
        String wall = "x".repeat(4000);
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node(wall, List.of(
                new XenoDialogue.Option(wall, XenoDialogue.OptionType.TEXT, "root", "", ""))));
        XenoDialogue got = XenoDialogueNbt.read(
                XenoDialogueNbt.write(new XenoDialogue("root", nodes)));
        assertEquals(XenoDialogueNbt.MAX_TEXT, got.startNode().text().length());
        assertEquals(XenoDialogueNbt.MAX_OPTION_TEXT,
                got.startNode().options().get(0).text().length());
    }

    @Test
    void tooManyNodesAreCutRatherThanCarried() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node("start", List.of()));
        for (int i = 0; i < 300; i++) {
            nodes.put("n" + i, new XenoDialogue.Node("x", List.of()));
        }
        XenoDialogue got = XenoDialogueNbt.read(
                XenoDialogueNbt.write(new XenoDialogue("root", nodes)));
        assertNotNull(got, "root is written first, so the start node survives the cut");
        assertEquals(XenoDialogueNbt.MAX_NODES, got.nodes().size());
    }

    @Test
    void theProfileCarriesItThroughItsOwnTag() {
        NpcCombatProfile profile = new NpcCombatProfile();
        assertNull(profile.dialogue(), "a fresh NPC has no dialogue of its own");

        profile.setDialogue(XenoDialogueNbt.write(sample()));
        NpcCombatProfile reloaded = NpcCombatProfile.fromTag(profile.toTag());
        assertNotNull(reloaded.dialogue());
        assertEquals("Well?", reloaded.dialogue().startNode().text());
    }

    @Test
    void anNpcWithoutOneStaysAbsentAcrossASaveAndLoad() {
        // The absence has to round-trip too: a profile that came back with an empty-but-present
        // dialogue would shadow its role's.
        NpcCombatProfile reloaded = NpcCombatProfile.fromTag(new NpcCombatProfile().toTag());
        assertNull(reloaded.dialogue());
    }

    @Test
    void itIsNotInTheVisualOptionsTag() throws IOException {
        // Visual options replicate to every client tracking the NPC. A conversation is only needed
        // by the one player who opens it, and the open packet carries it then.
        NpcCombatProfile profile = new NpcCombatProfile();
        profile.setDialogue(XenoDialogueNbt.write(sample()));
        assertFalse(profile.visualOptionsTag().contains("Dialogue"),
                "the whole script should not ride along with every nearby NPC's visuals");
    }

    @Test
    void theNpcsOwnDialogueWinsOverItsRoles() throws IOException {
        // Otherwise editing one in game would be pointless - the role's copy would still show.
        String entity = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/npc",
                        "XenoNpcEntity.java"), StandardCharsets.UTF_8);
        int at = entity.indexOf("public static net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue dialogueFor(");
        assertTrue(at >= 0, "the shared resolver should exist");
        String body = entity.substring(at, Math.min(entity.length(), at + 1700));
        // The role's dialogue now resolves through XenoNpcDataSource, which layers the world store
        // over the datapack. The invariant is unchanged: the NPC's own copy is consulted first, and
        // only a null there falls through to whatever its role names.
        int own = body.indexOf("profile.dialogue()");
        int assigned = body.indexOf(".assignedDialogue(");
        int role = body.indexOf("XenoNpcDataSource.dialogue(");
        assertTrue(own >= 0, "the NPC's own dialogue should be read here");
        assertTrue(assigned >= 0, "assigned shared dialogues should be checked before the role");
        assertTrue(role >= 0, "and the role's should resolve through the data source");
        assertTrue(own < assigned && assigned < role,
                "resolution order is NPC-owned, assigned shared, then role");
    }

    @Test
    void theOptionHandlerResolvesTheSameDialogueTheOpenPathDid() throws IOException {
        // If the two disagreed, picking option 2 would run option 2 of a different tree.
        String packet = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcDialoguePacket.java"), StandardCharsets.UTF_8);
        assertTrue(packet.contains("XenoNpcEntity.dialogueFor(npc)"),
                "it should delegate rather than repeat the role lookup");
    }

    @Test
    void theSaveWhitelistBoundsIt() throws IOException {
        String policy = Files.readString(
                RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network/packet",
                        "XenoNpcSavePolicy.java"), StandardCharsets.UTF_8);
        assertTrue(policy.contains("\"Dialogue\""), "the key should be editor-writable");
        assertTrue(policy.contains("validateDialogue"),
                "and bounded, since a compound key would otherwise accept any depth");
        assertTrue(policy.contains("XenoDialogueNbt.MAX_NODES"),
                "against the same limits the open packet carries");
    }
}
