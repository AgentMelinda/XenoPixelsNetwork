package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server tells the client to open the script screen for one of its NPCs, carrying that NPC's
 * script container (tabs, loaded scripts, language, enabled). Script <em>text</em> is not here:
 * the screen fetches each tab through {@link NpcScriptPacket}, the operator-gated path.
 */
public final class OpenXenoNpcScriptPacket {
    private final int entityId;
    private final CompoundTag container;

    public OpenXenoNpcScriptPacket(int entityId, CompoundTag container) {
        this.entityId = entityId;
        this.container = container == null ? new CompoundTag() : container;
    }

    public OpenXenoNpcScriptPacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeNbt(container);
    }

    public static void handle(OpenXenoNpcScriptPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> ClientScreens.openXenoNpcScript.accept(
                new ClientScreens.XenoNpcScriptOpenData(packet.entityId, packet.container)));
        context.get().setPacketHandled(true);
    }
}
