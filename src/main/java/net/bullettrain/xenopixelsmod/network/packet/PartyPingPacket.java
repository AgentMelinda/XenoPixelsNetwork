package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;
import java.util.function.Supplier;

/** Validated party target marker. */
public record PartyPingPacket(UUID senderId, UUID targetId, int targetEntityId,
                              String targetName, long expiresAtGameTime) {
    public PartyPingPacket(FriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUUID(), buf.readVarInt(), buf.readUtf(96), buf.readLong());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(senderId);
        buf.writeUUID(targetId);
        buf.writeVarInt(targetEntityId);
        buf.writeUtf(targetName == null ? "Target" : targetName, 96);
        buf.writeLong(expiresAtGameTime);
    }

    public static void handle(PartyPingPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientScreens.receivePartyPing.accept(msg));
        ctx.get().setPacketHandled(true);
    }
}
