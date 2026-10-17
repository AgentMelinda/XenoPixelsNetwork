package net.bullettrain.xenopixelsmod.space.universe;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound discovery delta for a single full-width logical-universe cell. */
public record InfiniteUniverseCellReveal(long fieldSeed, long x, long z) {
    public static void encode(InfiniteUniverseCellReveal packet, FriendlyByteBuf buf) {
        buf.writeLong(packet.fieldSeed);
        buf.writeLong(packet.x);
        buf.writeLong(packet.z);
    }

    public static InfiniteUniverseCellReveal decode(FriendlyByteBuf buf) {
        return new InfiniteUniverseCellReveal(buf.readLong(), buf.readLong(), buf.readLong());
    }
}
