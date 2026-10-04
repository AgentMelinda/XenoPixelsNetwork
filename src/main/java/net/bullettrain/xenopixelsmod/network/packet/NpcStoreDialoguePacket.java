package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.npc.XenoNpcEditorScreen;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStorePaths;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** A single stored dialogue, sent only to the operator who requested it. */
public record NpcStoreDialoguePacket(String group, String id, int revision, CompoundTag tag) {
    public NpcStoreDialoguePacket(FriendlyByteBuf buf) {
        this(buf.readUtf(XenoNpcStorePaths.MAX_GROUP), buf.readUtf(XenoNpcStorePaths.MAX_ID),
                buf.readVarInt(), buf.readNbt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(group, XenoNpcStorePaths.MAX_GROUP);
        buf.writeUtf(id, XenoNpcStorePaths.MAX_ID);
        buf.writeVarInt(revision);
        buf.writeNbt(tag);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            if (Minecraft.getInstance().screen instanceof XenoNpcEditorScreen editor) {
                editor.receiveStoreDialogue(group, id, revision, tag);
            }
        });
        ctx.setPacketHandled(true);
    }
}
