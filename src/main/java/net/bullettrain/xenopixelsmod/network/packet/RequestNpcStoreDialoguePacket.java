package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** Requests one complete world dialogue for an operator's editor. */
public record RequestNpcStoreDialoguePacket(String group, String id) {
    public RequestNpcStoreDialoguePacket(FriendlyByteBuf buf) {
        this(buf.readUtf(XenoNpcStorePaths.MAX_GROUP), buf.readUtf(XenoNpcStorePaths.MAX_ID));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(group, XenoNpcStorePaths.MAX_GROUP);
        buf.writeUtf(id, XenoNpcStorePaths.MAX_ID);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)
                    || XenoNpcStorePaths.rejectGroup(group) != null
                    || XenoNpcStorePaths.reject(id) != null) {
                return;
            }
            var store = XenoNpcStores.get();
            if (store == null) {
                return;
            }
            var tag = store.get(XenoNpcStoreCategory.DIALOGS, group, id);
            ModNetwork.sendToPlayer(player, new NpcStoreDialoguePacket(group, id,
                    store.revisionOf(XenoNpcStoreCategory.DIALOGS, group, id), tag));
        });
        ctx.setPacketHandled(true);
    }
}
