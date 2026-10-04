package net.bullettrain.xenopixelsmod.network;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.network.packet.NpcAppearancePacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The NPC appearance packet still decodes what it encoded, now that it carries the aura toggle.
 *
 * <p>This packet is the editor's only honest source for an NPC's state: the client entity's
 * persistent data is never replicated, so after a world reload the DMZ tab showed defaults — aura
 * and hair reading off while the NPC plainly still had both. Adding {@code auraOn} to the wire is
 * what fixes that, and it went on the end precisely because this codec is positional: a field
 * inserted in the middle is read as whatever used to follow it, and every field after it silently
 * becomes garbage.
 *
 * <p>So the test is a full round trip rather than a check of the new field alone — the failure mode
 * worth catching is the other twenty fields quietly shifting.
 */
class NpcAppearancePacketAuraTest {

    private static NpcAppearancePacket roundTrip(NpcAppearancePacket sent) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        sent.encode(buf);
        NpcAppearancePacket received = new NpcAppearancePacket(buf);
        assertEquals(0, buf.readableBytes(),
                "the decoder left bytes on the buffer, so encode and decode disagree");
        return received;
    }

    private static NpcAppearancePacket sample(boolean auraOn) {
        CompoundTag appearance = new CompoundTag();
        appearance.putString("Class", "warrior");
        CompoundTag visuals = new CompoundTag();
        visuals.putBoolean("Halo", true);
        return new NpcAppearancePacket(UUID.nameUUIDFromBytes(new byte[] {1, 2, 3}),
                "saiyan", "ssj", "ssj2",
                true, "HAIRCODE-1234", "#ff8800",
                111, 222, 333, 444, 555, 666, true,
                0x00CC33, 2.5f, appearance, visuals,
                "Dev", "https://example.invalid/skin.png", "uuid-here", auraOn);
    }

    @Test
    void theAuraToggleSurvivesTheWire() {
        assertTrue(roundTrip(sample(true)).auraOn(),
                "an NPC with its aura on would show the toggle off in the editor");
        assertFalse(roundTrip(sample(false)).auraOn());
    }

    /**
     * Everything that was already on the wire still arrives intact.
     *
     * <p>The point of the test: appending a field is only safe if nothing before it moved.
     */
    @Test
    void appendingTheFieldDidNotShiftAnythingBeforeIt() {
        NpcAppearancePacket got = roundTrip(sample(true));
        assertEquals("saiyan", got.race());
        assertEquals("ssj", got.formGroup());
        assertEquals("ssj2", got.form());
        assertTrue(got.hairEnabled());
        assertEquals("HAIRCODE-1234", got.hairCode());
        assertEquals(111, got.strength());
        assertEquals(222, got.strikePower());
        assertEquals(333, got.resistance());
        assertEquals(444, got.vitality());
        assertEquals(555, got.kiPower());
        assertEquals(666, got.energy());
        assertTrue(got.authoritative());
        assertEquals(0x00CC33, got.auraColor());
        assertEquals("Dev", got.skinPlayer());
    }

    /**
     * A long hair code still round-trips.
     *
     * <p>Hair is written in chunks, so it is the field most likely to break if the surrounding order
     * shifts — and hair is half of what was reported as not surviving a reload.
     */
    @Test
    void aLongHairCodeStillRoundTrips() {
        String code = "H".repeat(90_000);
        NpcAppearancePacket sent = new NpcAppearancePacket(UUID.randomUUID(), "human", "", "",
                true, code, "#ffffff", 1, 1, 1, 1, 1, 1, false,
                0, 1.0f, new CompoundTag(), new CompoundTag(), "", "", "", true);
        NpcAppearancePacket got = roundTrip(sent);
        assertEquals(code, got.hairCode());
        assertTrue(got.auraOn(), "the field after a chunked string is where a shift would show");
    }
}
