package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Clientbound: a charted body started (or stopped) burning in planet Hakai (channel {@code xenopixelsmod:planet_hakai_burn_v1}).
 * The client cuts the body's HD sphere from the top down over {@code durationTicks}, with a violet rim at the cut.
 */
public record PlanetHakaiBurn(String bodyId, int durationTicks, boolean active) {
    public PlanetHakaiBurn {
        if (bodyId == null || bodyId.isEmpty() || bodyId.length() > 256 || durationTicks < 1 || durationTicks > 20 * 60 * 10)
            throw new IllegalArgumentException("invalid planet hakai burn");
    }

    public static void encode(PlanetHakaiBurn packet, FriendlyByteBuf buf) {
        buf.writeUtf(packet.bodyId, 256); buf.writeVarInt(packet.durationTicks); buf.writeBoolean(packet.active);
    }

    public static PlanetHakaiBurn decode(FriendlyByteBuf buf) {
        return new PlanetHakaiBurn(buf.readUtf(256), buf.readVarInt(), buf.readBoolean());
    }
}
