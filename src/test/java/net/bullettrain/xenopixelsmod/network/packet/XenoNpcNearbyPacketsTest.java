package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.npc.NpcNearbyList;
import net.bullettrain.xenopixelsmod.npc.XenoNpcNearbyService;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class XenoNpcNearbyPacketsTest {
    @Test
    void theListRoundTripsAndLongNamesAreBounded() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var sent = new XenoNpcNearbyPackets.List_(List.of(
                    new NpcNearbyList.Entry(7, "Goku", "guard", 3.25, 12, true),
                    new NpcNearbyList.Entry(9, "x".repeat(80), "humanoid", 40.0, 1, false)));
            sent.encode(buf);
            var got = new XenoNpcNearbyPackets.List_(buf);
            assertEquals(sent.entries().get(0), got.entries().get(0));
            assertEquals(64, got.entries().get(1).name().length());
        } finally {
            buf.release();
        }
    }

    @Test
    void anActionRoundTrips() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var sent = new XenoNpcNearbyPackets.Action(XenoNpcNearbyService.Action.SPAWN, -1, "guard");
            sent.encode(buf);
            assertEquals(sent, new XenoNpcNearbyPackets.Action(buf));
        } finally {
            buf.release();
        }
    }
}
