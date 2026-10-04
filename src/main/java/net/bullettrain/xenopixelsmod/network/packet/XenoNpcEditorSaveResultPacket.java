package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Reply for a native NPC editor save, scoped to the entity and revision that submitted it.
 * {@code newRevision} is the NPC's revision after the save, so an editor that stays open
 * (autosave) submits its next save against the right revision; -1 when unknown.
 */
public record XenoNpcEditorSaveResultPacket(int entityId, int expectedRevision, int newRevision,
                                            boolean saved, String reason) {
    public XenoNpcEditorSaveResultPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(expectedRevision);
        buf.writeVarInt(newRevision);
        buf.writeBoolean(saved);
        buf.writeUtf(reason == null ? "" : reason, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientPacketHandlers.handleXenoNpcEditorSaveResult(
                entityId, expectedRevision, newRevision, saved, reason));
        context.get().setPacketHandled(true);
    }
}
