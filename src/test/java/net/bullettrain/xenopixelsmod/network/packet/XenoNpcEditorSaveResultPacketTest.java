package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class XenoNpcEditorSaveResultPacketTest {
    @Test
    void replyKeepsTheSubmittedEntityRevisionAndTheNewRevision() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            XenoNpcEditorSaveResultPacket sent = new XenoNpcEditorSaveResultPacket(
                    1207, 34, 35, false, "stale revision");
            sent.encode(buffer);
            assertEquals(sent, new XenoNpcEditorSaveResultPacket(buffer));
        } finally {
            buffer.release();
        }
    }
}
