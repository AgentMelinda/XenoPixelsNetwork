package net.bullettrain.xenopixelsmod.network.packet;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcAnimationPacketTest {

    @Test
    void flagsSurviveTheConstructor() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        NpcAnimationPacket hold = new NpcAnimationPacket(id, "combat.xeno_my_jab", 1.5f,
                NpcAnimationPacket.FLAG_HOLD);
        assertEquals(id, hold.entityUuid());
        assertEquals("combat.xeno_my_jab", hold.animation());
        assertEquals(1.5f, hold.speed(), 1.0e-6f);
        assertEquals(NpcAnimationPacket.FLAG_HOLD, hold.flags());
        assertTrue((hold.flags() & NpcAnimationPacket.FLAG_HOLD) != 0);

        NpcAnimationPacket stop = new NpcAnimationPacket(id, "xeno:stop", 1.0f,
                NpcAnimationPacket.FLAG_STOP);
        assertEquals(NpcAnimationPacket.FLAG_STOP, stop.flags());
    }
}