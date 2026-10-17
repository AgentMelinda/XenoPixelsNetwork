package net.bullettrain.xenopixelsmod.space.asteroid;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Clientbound set of asteroids that are physically present as Sable sub-levels in the receiving player's level
 * (S11 B1, channel {@code xenopixelsmod:asteroid_presence_v1}). Full replace. The far renderer skips these ids, since
 * Sable already draws them as blocks. Empty until near physicalization (B3) exists.
 */
public record AsteroidPresence(List<Entry> entries) {
    public static final int MAX_ENTRIES = 256;
    public record Entry(AsteroidField.ObjectId id, UUID subLevel) {}

    public AsteroidPresence {
        if (entries == null || entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("invalid asteroid presence bounds");
        entries = List.copyOf(entries);
    }

    public static void encode(AsteroidPresence packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entries.size());
        for (var entry : packet.entries) {
            buf.writeVarLong(entry.id().cellX()); buf.writeVarLong(entry.id().cellY()); buf.writeVarLong(entry.id().cellZ());
            buf.writeVarInt(entry.id().slot());
            buf.writeUUID(entry.subLevel());
        }
    }

    public static AsteroidPresence decode(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("too many present asteroids: " + count);
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++)
            entries.add(new Entry(new AsteroidField.ObjectId(buf.readVarLong(), buf.readVarLong(), buf.readVarLong(), buf.readVarInt()),
                    buf.readUUID()));
        return new AsteroidPresence(entries);
    }
}
