package net.bullettrain.xenopixelsmod.space.dragonball;

import net.minecraft.network.FriendlyByteBuf;

/** Serverbound wish pick (channel {@code xenopixelsmod:wish_choice_v1}); the server re-validates everything. */
public record WishChoice(String wishId) {
    public static void encode(WishChoice packet, FriendlyByteBuf buf) { buf.writeUtf(packet.wishId, 256); }

    public static WishChoice decode(FriendlyByteBuf buf) { return new WishChoice(buf.readUtf(256)); }
}
