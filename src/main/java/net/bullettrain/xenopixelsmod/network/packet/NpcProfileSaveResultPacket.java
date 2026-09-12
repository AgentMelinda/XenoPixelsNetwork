package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * S2C: the outcome of an {@link NpcProfileSavePacket}.
 *
 * <p>Every guard in that packet used to return without an answer, so a wand edit rejected for
 * permissions, range, or a stale entity id was indistinguishable from one that saved. This carries
 * the reason back to the client that asked.
 */
public record NpcProfileSaveResultPacket(boolean saved, String reason) {
    public static final String OK = "NPC saved";
    public static final String NOT_PERMITTED = "NPC save rejected: operator permission level 2 required";
    public static final String TOO_FAR = "NPC save rejected: NPC is more than 64 blocks away";
    public static final String NOT_EDITABLE = "NPC save rejected: target is not an editable NPC";
    public static final String GONE = "NPC save rejected: NPC is no longer loaded";

    public NpcProfileSaveResultPacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(saved);
        buf.writeUtf(reason == null ? "" : reason, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientPacketHandlers.handleNpcProfileSaveResult(saved, reason));
        ctx.setPacketHandled(true);
    }
}