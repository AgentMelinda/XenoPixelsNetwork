package net.bullettrain.xenopixelsmod.space;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HandoffTransitionNoticeTest {
    @Test void packetHasAnEmptyStablePayload() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        HandoffTransitionNotice.encode(new HandoffTransitionNotice(), buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(new HandoffTransitionNotice(), HandoffTransitionNotice.decode(buffer));
        assertEquals(0, buffer.readableBytes());
        buffer.release();
    }
}
