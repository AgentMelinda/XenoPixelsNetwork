package net.bullettrain.xenopixelsmod.space.hakai;

import net.minecraft.network.FriendlyByteBuf;

/** Bounded refresh request issued while the finite Galaxy Atlas is open. */
public record PlanetIntegrityAtlasRequest(long requestId) {
    public PlanetIntegrityAtlasRequest {
        if (requestId <= 0) throw new IllegalArgumentException("Atlas request id must be positive");
    }

    public static void encode(PlanetIntegrityAtlasRequest packet, FriendlyByteBuf buf) {
        buf.writeVarLong(packet.requestId);
    }

    public static PlanetIntegrityAtlasRequest decode(FriendlyByteBuf buf) {
        return new PlanetIntegrityAtlasRequest(buf.readVarLong());
    }
}
