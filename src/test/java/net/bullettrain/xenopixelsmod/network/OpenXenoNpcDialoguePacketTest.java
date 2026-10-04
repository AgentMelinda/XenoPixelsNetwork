package net.bullettrain.xenopixelsmod.network;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.packet.OpenXenoNpcDialoguePacket;
import net.bullettrain.xenopixelsmod.npc.dialog.XenoDialogue;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * The dialogue travels with the packet, because the client has no datapack to look it up in.
 *
 * <p>This packet used to carry only an id, with a comment saying "both sides load the same
 * datapack". They do not. {@code XenoDialogues} registers through {@code AddReloadListenerEvent},
 * which fires on the server only - so on a dedicated server the client's map is empty forever and
 * every conversation resolved to null. It worked in single-player purely because the integrated
 * server shares the JVM, and therefore the static map, with the client.
 *
 * <p>Sending the tree does not give the client authority over anything: choosing an option still
 * goes back as an index and the server re-reads its own copy before acting. What travels here is
 * what to draw.
 */
class OpenXenoNpcDialoguePacketTest {

    private static OpenXenoNpcDialoguePacket roundTrip(OpenXenoNpcDialoguePacket sent) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        OpenXenoNpcDialoguePacket received = new OpenXenoNpcDialoguePacket(buf);
        assertEquals(0, buf.readableBytes(),
                "the decoder left bytes on the buffer, so encode and decode disagree");
        return received;
    }

    private static XenoDialogue sample() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node("So you want to train, {player}?", List.of(
                new XenoDialogue.Option("Tell me more", XenoDialogue.OptionType.TEXT, "more", "",
                        ""),
                new XenoDialogue.Option("I will do it", XenoDialogue.OptionType.QUEST, "",
                        "xenopixelsmod:kill_mobs", "", 1),
                new XenoDialogue.Option("Not now", XenoDialogue.OptionType.QUIT, "", "", ""))));
        nodes.put("more", new XenoDialogue.Node("It is not easy.", List.of(
                new XenoDialogue.Option("Back", XenoDialogue.OptionType.TEXT, "root", "", ""),
                new XenoDialogue.Option("Do it", XenoDialogue.OptionType.COMMAND, "", "",
                        "say ready"))));
        return new XenoDialogue("root", nodes);
    }

    @Test
    void theWholeTreeSurvivesTheWire() {
        OpenXenoNpcDialoguePacket got = roundTrip(new OpenXenoNpcDialoguePacket(
                42, "xenopixelsmod:trainer", "Master Roshi", sample()));

        assertEquals(42, got.entityId());
        assertEquals("xenopixelsmod:trainer", got.dialogueId());
        assertEquals("Master Roshi", got.npcName());

        XenoDialogue dialogue = got.dialogue();
        assertNotNull(dialogue, "a dialogue with a valid start node should arrive");
        assertEquals("root", dialogue.start());
        assertEquals(2, dialogue.nodes().size());
        assertEquals("So you want to train, {player}?", dialogue.startNode().text());

        XenoDialogue.Option quest = dialogue.startNode().options().get(1);
        assertEquals(XenoDialogue.OptionType.QUEST, quest.type());
        assertEquals("xenopixelsmod:kill_mobs", quest.quest());
        assertEquals(1, quest.sourceIndex(), "the server option index must survive filtering and the wire");

        XenoDialogue.Option command = dialogue.nodes().get("more").options().get(1);
        assertEquals(XenoDialogue.OptionType.COMMAND, command.type());
        assertEquals("say ready", command.command());
    }

    @Test
    void nodeOrderIsPreserved() {
        // Order decides nothing functionally - nodes are looked up by key - but an editor that
        // lists them, and a payload compared between runs, both want it stable. The faction list
        // had the same Map.copyOf bug and there it did matter.
        XenoDialogue dialogue = roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", sample())).dialogue();
        assertEquals(List.of("root", "more"), new ArrayList<>(dialogue.nodes().keySet()));
    }

    @Test
    void aTextOptionKeepsItsTargetSoNavigationStaysClientSide() {
        // Walking between text nodes never reaches the server. If target did not survive the wire,
        // every "tell me more" would dead-end.
        XenoDialogue dialogue = roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", sample())).dialogue();
        assertEquals("more", dialogue.startNode().options().get(0).target());
        assertTrue(dialogue.nodes().containsKey("more"));
    }

    @Test
    void answerAndSpeakerPalettesSurviveTheWire() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node("Choose", List.of(
                new XenoDialogue.Option("Fight", XenoDialogue.OptionType.QUIT, "", "", "",
                        -1, "RED")), "GOLD"));

        XenoDialogue got = roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", new XenoDialogue("root", nodes))).dialogue();

        assertEquals("GOLD", got.startNode().palette(),
                "speaker bubble should retain its authored palette");
        assertEquals("RED", got.startNode().options().get(0).palette(),
                "answer bubble should retain its authored palette");
    }

    @Test
    void aNullDialogueEncodesAndComesBackNull() {
        // The handler treats null as "do not open". An NPC whose role names a dialogue the pack
        // does not define should show nothing, not an empty bubble.
        assertNull(roundTrip(new OpenXenoNpcDialoguePacket(1, "", "", null)).dialogue());
    }

    @Test
    void aTreeWhoseStartIsMissingDecodesAsNothingRatherThanAnEmptyScreen() {
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("somewhere", new XenoDialogue.Node("hi", List.of()));
        assertNull(roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", new XenoDialogue("nowhere", nodes))).dialogue());
    }

    @Test
    void anOversizedTreeIsTruncatedRatherThanSent() {
        // The payload is sized by data, so the caps have to hold on both sides. 201 nodes against
        // a cap of 128.
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node("start", List.of()));
        for (int i = 0; i < 200; i++) {
            nodes.put("n" + i, new XenoDialogue.Node("node " + i, List.of()));
        }
        XenoDialogue got = roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", new XenoDialogue("root", nodes))).dialogue();
        assertNotNull(got, "root is written first, so the start node survives the cut");
        assertEquals(128, got.nodes().size());
    }

    @Test
    void longTextIsClampedRatherThanRejected() {
        // writeUtf throws when a string is over its cap, which would drop the packet and open no
        // conversation at all. Cutting a wordy line is the better failure.
        String wall = "x".repeat(4000);
        Map<String, XenoDialogue.Node> nodes = new LinkedHashMap<>();
        nodes.put("root", new XenoDialogue.Node(wall, List.of(
                new XenoDialogue.Option(wall, XenoDialogue.OptionType.TEXT, "root", "", ""))));
        XenoDialogue got = roundTrip(new OpenXenoNpcDialoguePacket(
                1, "id", "name", new XenoDialogue("root", nodes))).dialogue();
        assertEquals(512, got.startNode().text().length());
        assertEquals(256, got.startNode().options().get(0).text().length());
    }

    @Test
    void theHandlerNoLongerAsksTheClientToResolveAnId() throws IOException {
        Path handler = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/client",
                "ClientPacketHandlers.java");
        String source = Files.readString(handler, StandardCharsets.UTF_8);
        int at = source.indexOf("public static void openNpcDialogue");
        assertTrue(at >= 0, "the handler should exist");
        String body = source.substring(at, Math.min(source.length(), at + 700));
        assertTrue(body.contains("XenoDialogue dialogue"),
                "the handler should take the tree the server sent");
        assertFalse(body.contains("XenoDialogues.get("),
                "and must not look it up in a datapack the client does not have");
    }

    @Test
    void theProtocolMovedBecauseTheWireShapeDid() {
        // Adding fields to an existing packet is as much a wire change as adding a packet: an old
        // client would read the new bytes as whatever used to follow.
        assertTrue(ProtocolVersion.current() >= 91,
                "carrying speaker and answer palettes requires protocol 91 or later");
    }
}
