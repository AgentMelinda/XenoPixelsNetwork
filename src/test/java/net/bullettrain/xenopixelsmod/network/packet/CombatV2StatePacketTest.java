package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import net.bullettrain.xenopixelsmod.client.combat.v2.V2ClientState;
import net.bullettrain.xenopixelsmod.combat.v2.V2State;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatV2StatePacketTest {
    private static CombatV2StatePacket state(int ticks, int target) {
        return new CombatV2StatePacket(V2State.TRAVEL.ordinal(), 0, 0, 0, 0, 0, -1,
                true, 2.6f, 0, -1, ticks, target);
    }

    @Test void newDashFieldsAndClosedWindowRoundTripWithoutTrailingBytes() {
        for (var packet : new CombatV2StatePacket[]{state(24, 142), state(0, -1)}) {
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.encode(buf);
                assertEquals(packet, new CombatV2StatePacket(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }

    @Test void continuationRequiresTheServerTargetAndExpiresWithoutFurtherPackets() {
        V2ClientState.reset();
        try {
            V2ClientState.apply(state(2, 142));
            assertTrue(V2ClientState.dashOpenAgainst(142));
            assertFalse(V2ClientState.dashOpenAgainst(143));
            assertFalse(V2ClientState.dashOpenAgainst(-1));
            V2ClientState.tick();
            assertTrue(V2ClientState.dashOpenAgainst(142));
            V2ClientState.tick();
            assertFalse(V2ClientState.dashOpen());
            V2ClientState.apply(state(24, 142));
            V2ClientState.reset();
            assertFalse(V2ClientState.dashOpen());
        } finally { V2ClientState.reset(); }
    }
}
