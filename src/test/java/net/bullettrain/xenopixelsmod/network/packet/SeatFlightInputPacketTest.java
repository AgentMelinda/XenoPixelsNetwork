package net.bullettrain.xenopixelsmod.network.packet;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeatFlightInputPacketTest {
    @Test void duplicateFramesAreLimitedButRestartedServerClockIsNot() {
        assertFalse(SeatFlightInputPacket.rateLimitedAt(1, null));
        assertTrue(SeatFlightInputPacket.rateLimitedAt(100, 100));
        assertFalse(SeatFlightInputPacket.rateLimitedAt(101, 100));
        assertFalse(SeatFlightInputPacket.rateLimitedAt(1, 10000));
    }
}
