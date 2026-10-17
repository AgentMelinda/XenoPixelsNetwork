package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

/** Serverbound Galaxy Atlas warp request (channel {@code xenopixelsmod:universe_warp_v1}); the server re-checks everything. */
public record UniverseWarpRequest(int x, int z) {
    public static void encode(UniverseWarpRequest packet, FriendlyByteBuf buf) { buf.writeVarInt(packet.x); buf.writeVarInt(packet.z); }

    public static UniverseWarpRequest decode(FriendlyByteBuf buf) { return new UniverseWarpRequest(buf.readVarInt(), buf.readVarInt()); }
}
