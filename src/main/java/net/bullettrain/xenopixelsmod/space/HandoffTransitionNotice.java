package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound marker ordered immediately before the server's vanilla respawn/dimension packet. */
public record HandoffTransitionNotice() {
    public static void encode(HandoffTransitionNotice packet, FriendlyByteBuf buffer) {
        // Deliberately empty: this only arms a one-shot client-side capture for the next respawn.
    }

    public static HandoffTransitionNotice decode(FriendlyByteBuf buffer) {
        return new HandoffTransitionNotice();
    }
}
