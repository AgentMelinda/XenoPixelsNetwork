package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

public final class OpenXenoNpcEditorPacket {
    private final int entityId;
    private final CompoundTag data;

    public OpenXenoNpcEditorPacket(int entityId, CompoundTag data) {
        this.entityId = entityId;
        this.data = data == null ? new CompoundTag() : data;
    }

    public OpenXenoNpcEditorPacket(FriendlyByteBuf buf) {
        entityId = buf.readVarInt();
        CompoundTag read = buf.readNbt();
        data = read == null ? new CompoundTag() : read;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(data);
    }

    public static void handle(OpenXenoNpcEditorPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientScreens.openXenoNpcEditor.accept(
                new ClientScreens.XenoNpcOpenData(packet.entityId, packet.data)));
        context.get().setPacketHandled(true);
    }
}
