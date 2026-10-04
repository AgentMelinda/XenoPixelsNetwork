package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * S2C: an NPC's full server-side DMZ profile. Sent when an editor or DMZ screen opens and whenever
 * the server changes the profile while that screen is watched. The client entity's persistent data
 * is never synced otherwise, so this is the only source of the real values on the client.
 */
public final class NpcProfileRefreshPacket {
    private final int entityId;
    private final CompoundTag profile;

    public NpcProfileRefreshPacket(int entityId, CompoundTag profile) {
        this.entityId = entityId;
        this.profile = profile == null ? new CompoundTag() : profile;
    }

    public NpcProfileRefreshPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
        CompoundTag read = buf.readNbt();
        this.profile = read == null ? new CompoundTag() : read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(profile);
    }

    public static void handle(NpcProfileRefreshPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.bullettrain.xenopixelsmod.client.ClientScreens.receiveNpcProfile
                .accept(msg.entityId, msg.profile));
        ctx.get().setPacketHandled(true);
    }
}
