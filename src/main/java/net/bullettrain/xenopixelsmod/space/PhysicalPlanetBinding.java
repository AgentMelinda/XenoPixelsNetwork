package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

/** Identity only. Position and orientation come exclusively from Sable replication. */
public record PhysicalPlanetBinding(UUID bodyId) {
    public static void encode(PhysicalPlanetBinding packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.bodyId != null);
        if (packet.bodyId != null) buf.writeUUID(packet.bodyId);
    }
    public static PhysicalPlanetBinding decode(FriendlyByteBuf buf) {
        return new PhysicalPlanetBinding(buf.readBoolean() ? buf.readUUID() : null);
    }
}
