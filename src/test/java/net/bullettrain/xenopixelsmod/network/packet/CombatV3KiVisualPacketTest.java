package net.bullettrain.xenopixelsmod.network.packet;

import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.fx.ki.KiLook;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatV3KiVisualPacketTest {
    private static CombatV3KiVisualPacket packet(CombatV3KiVisualPacket.Phase phase, float size, Vec3 pos) {
        return new CombatV3KiVisualPacket(ResourceLocation.parse("minecraft:overworld"), UUID.randomUUID(),
                UUID.randomUUID(), phase, KiLook.Kind.WAVE, 1, 5240831, 5240831, size, pos,
                new Vec3(0, 0, 1), 96);
    }
    @Test void allPhasesPreserveIdentityAndNativeGeometryOverTheWire() {
        for (var phase : CombatV3KiVisualPacket.Phase.values()) {
            var sent = packet(phase, 2, new Vec3(4, 8, 12));
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                CombatV3KiVisualPacket.encode(sent, buf);
                assertEquals(sent, new CombatV3KiVisualPacket(buf));
                assertEquals(0, buf.readableBytes());
            } finally { buf.release(); }
        }
    }
    @Test void invalidNumbersAndUnknownPhaseAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> packet(CombatV3KiVisualPacket.Phase.FLIGHT, Float.NaN, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> packet(CombatV3KiVisualPacket.Phase.FLIGHT, 33, Vec3.ZERO));
        assertThrows(IllegalArgumentException.class, () -> packet(CombatV3KiVisualPacket.Phase.FLIGHT, 1, new Vec3(Double.NaN, 0, 0)));
        var buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeResourceLocation(ResourceLocation.parse("minecraft:overworld"));
            buf.writeUUID(UUID.randomUUID()); buf.writeUUID(UUID.randomUUID()); buf.writeVarInt(100);
            assertThrows(IllegalArgumentException.class, () -> new CombatV3KiVisualPacket(buf));
        } finally { buf.release(); }
    }
}
