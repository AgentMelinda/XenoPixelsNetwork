package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;

/** Clientbound handoff marker with overlay timing and an optional player re-entry shell. */
public record HandoffTransitionNoticeV3(int overlayMillis, boolean plasmaShell) {
    public HandoffTransitionNoticeV3 {
        if (overlayMillis < 0 || overlayMillis > 60_000)
            throw new IllegalArgumentException("overlayMillis outside [0,60000]");
    }

    public static void encode(HandoffTransitionNoticeV3 packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.overlayMillis());
        buffer.writeBoolean(packet.plasmaShell());
    }

    public static HandoffTransitionNoticeV3 decode(FriendlyByteBuf buffer) {
        return new HandoffTransitionNoticeV3(buffer.readVarInt(), buffer.readBoolean());
    }
}
