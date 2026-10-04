package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStores;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/** Requests one stored quest definition for the operator's global quest editor. */
public record RequestNpcStoreQuestPacket(String group, String id) {
    public RequestNpcStoreQuestPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(XenoNpcStorePaths.MAX_GROUP), buf.readUtf(XenoNpcStorePaths.MAX_ID));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(group == null ? "" : group, XenoNpcStorePaths.MAX_GROUP);
        buf.writeUtf(id == null ? "" : id, XenoNpcStorePaths.MAX_ID);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || !player.hasPermissions(2)
                    || XenoNpcStorePaths.rejectGroup(group) != null
                    || XenoNpcStorePaths.reject(id) != null) return;
            var store = XenoNpcStores.get();
            var tag = store == null ? null : store.get(XenoNpcStoreCategory.QUESTS, group, id);
            String json = tag == null ? "" : tag.getString("DefinitionJson");
            if (json.length() > NpcStoreQuestPacket.MAX_DEFINITION_LENGTH) json = "";
            ModNetwork.sendToPlayer(player, new NpcStoreQuestPacket(group, id,
                    tag == null ? 0 : store.revisionOf(XenoNpcStoreCategory.QUESTS, group, id),
                    json));
        });
        ctx.setPacketHandled(true);
    }
}
