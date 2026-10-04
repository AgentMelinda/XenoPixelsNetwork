package net.bullettrain.xenopixelsmod.network;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.RepoRoot;
import net.bullettrain.xenopixelsmod.network.packet.NpcScriptPacket;
import net.bullettrain.xenopixelsmod.network.packet.NpcScriptResultPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two packets behind the script editor, on the wire and in the registry.
 *
 * <p>{@code NpcScriptPacket} asks (fetch a stored script, or run this text); {@code NpcScriptResultPacket}
 * answers. Both directions are one request from one screen, so the interesting properties are that the
 * bytes agree with themselves and that the registrations exist at all - a packet class that nobody
 * registered compiles, passes every other test, and then silently does nothing in game.
 */
class NpcScriptPacketContractTest {

    private static FriendlyByteBuf wire() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }

    private static String modNetworkSource() {
        Path file = RepoRoot.of("src/main/java/net/bullettrain/xenopixelsmod/network",
                "ModNetwork.java");
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    @Test
    void aFetchRequestSurvivesTheRoundTrip() {
        NpcScriptPacket sent = new NpcScriptPacket("greet", "", "", false);
        FriendlyByteBuf buf = wire();
        sent.encode(buf);
        NpcScriptPacket read = new NpcScriptPacket(buf);
        assertEquals(sent, read);
        assertEquals(0, buf.readableBytes(),
                "the decoder left bytes on the buffer, so encode and decode disagree");
    }

    @Test
    void unsavedEditorTextAndAnEntrypointBothTravel() {
        NpcScriptPacket sent = new NpcScriptPacket("greet", "function onInteract() {\n  log.line('hi');\n}",
                "onInteract", true);
        FriendlyByteBuf buf = wire();
        sent.encode(buf);
        NpcScriptPacket read = new NpcScriptPacket(buf);
        assertEquals("greet", read.id());
        assertEquals(sent.source(), read.source(), "the point of the field is trying unsaved text");
        assertEquals("onInteract", read.entrypoint());
        assertTrue(read.run());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void aStoredEntryComesBackWholeAndARunComesBackAsText() {
        CompoundTag payload = new CompoundTag();
        payload.putString("Name", "Greet");
        payload.putString("Language", "ecmascript");
        payload.putByte("Enabled", (byte) 1);
        payload.putString("Script", "1 + 1");
        NpcScriptResultPacket fetched = new NpcScriptResultPacket("greet", "", "", payload, false, true);
        FriendlyByteBuf buf = wire();
        fetched.encode(buf);
        NpcScriptResultPacket read = new NpcScriptResultPacket(buf);
        assertEquals("greet", read.id());
        assertFalse(read.ran(), "this reply is an entry, not a verdict");
        assertTrue(read.ok());
        assertEquals("1 + 1", read.payload().getString("Script"));
        assertEquals(0, buf.readableBytes());

        // The run reply carries no payload, which the codec has to represent as itself rather than as
        // an empty tag the screen would then read as "an empty script".
        NpcScriptResultPacket ran = new NpcScriptResultPacket("greet", "line 2: boom", "hi", null,
                true, false);
        FriendlyByteBuf ranBuf = wire();
        ran.encode(ranBuf);
        NpcScriptResultPacket ranRead = new NpcScriptResultPacket(ranBuf);
        assertTrue(ranRead.ran());
        assertFalse(ranRead.ok());
        assertNull(ranRead.payload());
        assertEquals("line 2: boom", ranRead.status());
        assertEquals("hi", ranRead.output());
        assertEquals(0, ranBuf.readableBytes());
    }

    @Test
    void anAnswerTooLongToShowIsCutInsteadOfKillingTheConnection() {
        // Script output is whatever a script printed. Failing to encode it would disconnect the client
        // that asked, which turns a noisy script into a kick.
        NpcScriptResultPacket sent = new NpcScriptResultPacket("greet", "e".repeat(5000),
                "o".repeat(20000), null, true, false);
        FriendlyByteBuf buf = wire();
        sent.encode(buf);
        NpcScriptResultPacket read = new NpcScriptResultPacket(buf);
        assertEquals(256, read.status().length());
        assertEquals(4096, read.output().length());
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void bothPacketsAreRegisteredOnTheSequentialChannel() {
        // Positional ids mean these have to be appended, and a missing add() is invisible to the
        // compiler. Reading the source is the cheapest way to know the wiring is still there.
        String source = modNetworkSource();
        assertTrue(source.contains("packet.NpcScriptPacket.class"), "fetch/run packet not registered");
        assertTrue(source.contains("packet.NpcScriptPacket::decode")
                        || source.contains("packet.NpcScriptPacket::new"),
                "fetch/run packet has no decoder");
        assertTrue(source.contains("packet.NpcScriptResultPacket.class"), "reply packet not registered");
        assertTrue(source.contains("packet.NpcScriptResultPacket::new"), "reply packet has no decoder");
    }

    @Test
    void theWireChangeShippedWithAProtocolFloor() {
        // Two new packet types on a positional channel: an old client must not be allowed to connect
        // and read the wrong bytes.
        assertTrue(ProtocolVersion.current() >= 95,
                "script editor packets need protocol 95 or later, found " + ProtocolVersion.current());
    }
}
