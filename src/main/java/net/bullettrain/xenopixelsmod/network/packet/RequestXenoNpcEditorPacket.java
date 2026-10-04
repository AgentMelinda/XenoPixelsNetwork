package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.XenoNpcEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** Client request for opening the native editor; the server remains authoritative. */
public final class RequestXenoNpcEditorPacket {
    private final int entityId;

    public RequestXenoNpcEditorPacket(int entityId) {
        this.entityId = entityId;
    }

    public RequestXenoNpcEditorPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
    }

    public static void handle(RequestXenoNpcEditorPacket packet,
                               Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            ServerPlayer player = context.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("You need operator permission to edit Xeno NPCs."));
                return;
            }
            if (!(player.level().getEntity(packet.entityId) instanceof XenoNpcEntity npc)
                    || player.distanceToSqr(npc) > 64.0 * 64.0) {
                return;
            }
            net.bullettrain.xenopixelsmod.compat.npc.NpcProfileWatchers.watch(player, npc);
            ModNetwork.sendToPlayer(player,
                    new OpenXenoNpcEditorPacket(npc.getId(),
                            net.bullettrain.xenopixelsmod.npc.XenoNpcData.editorPayload(
                                    npc, npc.npcData())));
        });
        context.get().setPacketHandled(true);
    }
}
