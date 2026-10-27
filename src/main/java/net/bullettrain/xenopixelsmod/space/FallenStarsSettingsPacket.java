package net.bullettrain.xenopixelsmod.space;

import net.minecraft.network.FriendlyByteBuf;

/** Small additive clientbound packet for the server-authoritative fallen-stars master switch. */
public record FallenStarsSettingsPacket(boolean enabled) {
    public static void encode(FallenStarsSettingsPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enabled());
    }

    public static FallenStarsSettingsPacket decode(FriendlyByteBuf buffer) {
        return new FallenStarsSettingsPacket(buffer.readBoolean());
    }
}
