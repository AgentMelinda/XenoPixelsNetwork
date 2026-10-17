package net.bullettrain.xenopixelsmod.space.blackhole;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/** Clientbound list of black holes for rendering (channel {@code xenopixelsmod:black_holes_v1}); empty when holes are off. */
public record BlackHoleSnapshot(int revision, List<BlackHoleRules.Hole> holes) {
    public static final int MAX_HOLES = 64;

    public BlackHoleSnapshot {
        if (holes == null || holes.size() > MAX_HOLES) throw new IllegalArgumentException("invalid black hole snapshot");
        holes = List.copyOf(holes);
    }

    public static void encode(BlackHoleSnapshot packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.revision);
        buf.writeVarInt(packet.holes.size());
        for (var h : packet.holes) {
            buf.writeUtf(h.id(), 256);
            buf.writeDouble(h.x()); buf.writeDouble(h.y()); buf.writeDouble(h.z());
            buf.writeDouble(h.horizon()); buf.writeDouble(h.tidal()); buf.writeDouble(h.lens()); buf.writeDouble(h.pull());
        }
    }

    public static BlackHoleSnapshot decode(FriendlyByteBuf buf) {
        int revision = buf.readVarInt(), count = buf.readVarInt();
        if (count < 0 || count > MAX_HOLES) throw new IllegalArgumentException("too many black holes: " + count);
        var holes = new ArrayList<BlackHoleRules.Hole>(count);
        for (int i = 0; i < count; i++)
            holes.add(new BlackHoleRules.Hole(buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()));
        return new BlackHoleSnapshot(revision, holes);
    }
}
