package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.combat.v2.UltimateFinisherRules;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UltimateFinisherCameraPacketTest {
    @Test void everyStageAndCancellationRoundTripWithSequenceIdentity() {
        for (var phase : UltimateFinisherRules.Phase.values()) {
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                var original = new UltimateFinisherCameraPacket(142, 91, phase);
                original.encode(buf);
                assertEquals(original, new UltimateFinisherCameraPacket(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }
}
