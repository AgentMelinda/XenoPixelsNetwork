package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound handoff marker with the destination body's configured transition fade duration. */
public record HandoffTransitionNoticeV2(int overlayMillis) {
    public HandoffTransitionNoticeV2 {
        if (overlayMillis < 0 || overlayMillis > 60_000) throw new IllegalArgumentException("overlayMillis outside [0,60000]");
    }

    public static void encode(HandoffTransitionNoticeV2 packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.overlayMillis());
    }

    public static HandoffTransitionNoticeV2 decode(FriendlyByteBuf buffer) {
        return new HandoffTransitionNoticeV2(buffer.readVarInt());
    }
}
