package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3CameraBeat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatV3CameraPacketTest {
    private static V3CameraBeat beat() {
        return new V3CameraBeat(0, 20, new Vec3(4, 2, -3), Vec3.ZERO, 0.5f,
                V3CameraBeat.Easing.SMOOTH);
    }
    @Test void startAndStopRoundTripWithCastAndTargetIdentity() {
        UUID session = UUID.randomUUID(), cast = UUID.randomUUID(), target = UUID.randomUUID();
        var start = new CombatV3CameraPacket(session, cast, target, 42, 40, 3, List.of(beat()));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            start.encode(buf);
            assertEquals(start, new CombatV3CameraPacket(buf));
            assertEquals(0, buf.readableBytes());
            var stop = CombatV3CameraPacket.stop(session, cast, target, 42);
            stop.encode(buf);
            assertEquals(stop, new CombatV3CameraPacket(buf));
            assertTrue(stop.stopping());
        } finally { buf.release(); }
    }
    @Test void decoderBoundsListsBeforeAllocation() {
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeUUID(UUID.randomUUID()); buf.writeUUID(UUID.randomUUID()); buf.writeUUID(UUID.randomUUID());
            buf.writeVarInt(1); buf.writeVarInt(40); buf.writeVarInt(0); buf.writeBoolean(false); buf.writeVarInt(129);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3CameraPacket(buf));
            assertThrows(IllegalArgumentException.class, () -> new CombatV3CameraPacket(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 1, 40, 0,
                    Collections.nCopies(129, beat())));
        } finally { buf.release(); }
    }
    @Test void nonFiniteOffsetsOverlappingShotsAndOverlongTimelineAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new V3CameraBeat(0, 20,
                new Vec3(Double.NaN, 0, 0), Vec3.ZERO, 0.5f, V3CameraBeat.Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> new V3CameraBeat(0, 20,
                Vec3.ZERO, Vec3.ZERO, Float.POSITIVE_INFINITY, V3CameraBeat.Easing.LINEAR));
        assertThrows(IllegalArgumentException.class, () -> V3CameraBeat.validate(List.of(beat(), beat()), 40));
        assertThrows(IllegalArgumentException.class, () -> V3CameraBeat.validate(List.of(beat()), 19));
    }
    @Test void easingDependsOnTimelineTimeAndNotFrameCount() {
        assertEquals(0.5, beat().progress(10), 1e-9);
        assertEquals(0, beat().progress(-1), 1e-9);
        assertEquals(1, beat().progress(50), 1e-9);
        assertEquals(beat().progress(8.25), beat().progress(8.25), 0);
    }
}
