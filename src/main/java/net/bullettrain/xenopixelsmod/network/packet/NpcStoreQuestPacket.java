package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.XenoNpcEditorScreen;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** One requested quest definition, returned only to the operator who requested it. */
public record NpcStoreQuestPacket(String group, String id, int revision, String definitionJson) {
    public static final int MAX_DEFINITION_LENGTH = 32_767;

    public NpcStoreQuestPacket(FriendlyByteBuf buf) {
        this(buf.readUtf(XenoNpcStorePaths.MAX_GROUP), buf.readUtf(XenoNpcStorePaths.MAX_ID),
                buf.readVarInt(), buf.readUtf(MAX_DEFINITION_LENGTH));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(group == null ? "" : group, XenoNpcStorePaths.MAX_GROUP);
        buf.writeUtf(id == null ? "" : id, XenoNpcStorePaths.MAX_ID);
        buf.writeVarInt(Math.max(0, revision));
        buf.writeUtf(definitionJson == null ? "" : definitionJson, MAX_DEFINITION_LENGTH);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof XenoNpcEditorScreen editor) {
                editor.receiveStoreQuest(group, id, revision, definitionJson);
            }
        });
        ctx.setPacketHandled(true);
    }
}
