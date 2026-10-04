package net.bullettrain.xenopixelsmod.network;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.network.packet.QuestCompletionPopupPacket;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestCompletionPopupPacketTest {
    @Test
    void serializesTheChosenDescriptionRewardPaletteAndFrame() {
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        new QuestCompletionPopupPacket("Defeat Nappa", "First one down, on to the Prince.",
                "+2 skill points", "gold", "banner").encode(wire);
        QuestCompletionPopupPacket decoded = new QuestCompletionPopupPacket(wire);
        assertEquals("Defeat Nappa", decoded.title());
        assertEquals("First one down, on to the Prince.", decoded.description());
        assertEquals("+2 skill points", decoded.reward());
        assertEquals("gold", decoded.palette());
        assertEquals("banner", decoded.frame());
    }
}
