package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;

/**
 * Every managed physical planet in the receiving player's level, as chart id → Sable UUID. Identity only:
 * position and orientation come exclusively from Sable replication. Replaces the single-UUID
 * {@link PhysicalPlanetBinding} in the Voidsea, where Earth and the Moon are both physical.
 */
public record PhysicalPlanetBindings(List<Entry> entries, boolean radialGravityEnabled) {
    /** Charted physical bodies plus the bounded S11 small-planet incarnations. */
    public static final int MAX_ENTRIES = 64;
    public record Entry(String bodyId, UUID uuid, int radialGravityRadius) {
        public Entry(String bodyId, UUID uuid) { this(bodyId, uuid, 0); }
        public Entry {
            if (radialGravityRadius < 0 || radialGravityRadius > SmallPlanetField.WALKABLE_RADIUS_LIMIT)
                throw new IllegalArgumentException("radial gravity radius outside [0," + SmallPlanetField.WALKABLE_RADIUS_LIMIT + "]");
        }
    }

    /** Source compatibility for callers that only have physical-body identities. */
    public PhysicalPlanetBindings(List<Entry> entries) { this(entries, false); }

    public PhysicalPlanetBindings {
        if (entries == null || entries.size() > MAX_ENTRIES)
            throw new IllegalArgumentException("too many planet bindings: " + (entries == null ? "null" : entries.size()));
        entries = List.copyOf(entries);
    }

    public static void encode(PhysicalPlanetBindings packet, FriendlyByteBuf buf) {
        int count = packet.entries.size();
        buf.writeBoolean(packet.radialGravityEnabled);
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(packet.entries.get(i).bodyId(), 256);
            buf.writeUUID(packet.entries.get(i).uuid());
            buf.writeVarInt(packet.entries.get(i).radialGravityRadius());
        }
    }

    public static PhysicalPlanetBindings decode(FriendlyByteBuf buf) {
        boolean radialGravityEnabled = buf.readBoolean();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("too many planet bindings: " + count);
        var entries = new ArrayList<Entry>(count);
        for (int i = 0; i < count; i++) entries.add(new Entry(buf.readUtf(256), buf.readUUID(), buf.readVarInt()));
        return new PhysicalPlanetBindings(List.copyOf(entries), radialGravityEnabled);
    }
}
