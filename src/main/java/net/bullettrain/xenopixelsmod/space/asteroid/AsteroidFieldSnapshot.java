package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * Clientbound asteroid field description (S11 B1, channel {@code xenopixelsmod:asteroid_field_v1}): everything a client
 * needs to compute the same deterministic field as the server with {@link AsteroidField#inCell}, and nothing per
 * asteroid. Full-replace semantics; an empty belt list means the field is off. {@code fieldSeed} is derived from the
 * world seed by {@link #fieldSeed(long)}, so the world seed itself never reaches clients.
 */
public record AsteroidFieldSnapshot(int revision, long fieldSeed, List<AsteroidField.Belt> belts) {
    public static final int MAX_BELTS = 16;

    public AsteroidFieldSnapshot {
        if (revision < 0 || belts == null || belts.size() > MAX_BELTS) throw new IllegalArgumentException("invalid asteroid field bounds");
        belts = List.copyOf(belts);
        var ids = new HashSet<String>();
        for (var belt : belts) if (!ids.add(belt.id())) throw new IllegalArgumentException("duplicate belt " + belt.id());
    }

    public static AsteroidFieldSnapshot off(int revision) { return new AsteroidFieldSnapshot(revision, 0, List.of()); }

    /** One-way: the shared field seed for a world seed. */
    public static long fieldSeed(long worldSeed) {
        return AsteroidField.mix64(worldSeed ^ 0x5DEECE66DA57E1B3L);
    }

    public static void encode(AsteroidFieldSnapshot packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.revision);
        buf.writeLong(packet.fieldSeed);
        buf.writeVarInt(packet.belts.size());
        for (var belt : packet.belts) {
            buf.writeUtf(belt.id(), 256);
            buf.writeDouble(belt.centerX()); buf.writeDouble(belt.centerY()); buf.writeDouble(belt.centerZ());
            buf.writeDouble(belt.innerRadius()); buf.writeDouble(belt.outerRadius()); buf.writeDouble(belt.halfThickness());
            buf.writeBoolean(belt.icy());
        }
    }

    public static AsteroidFieldSnapshot decode(FriendlyByteBuf buf) {
        int revision = buf.readVarInt();
        long seed = buf.readLong();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_BELTS) throw new IllegalArgumentException("too many asteroid belts: " + count);
        var belts = new ArrayList<AsteroidField.Belt>(count);
        for (int i = 0; i < count; i++)
            belts.add(new AsteroidField.Belt(buf.readUtf(256), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readBoolean()));
        return new AsteroidFieldSnapshot(revision, seed, belts);
    }
}
