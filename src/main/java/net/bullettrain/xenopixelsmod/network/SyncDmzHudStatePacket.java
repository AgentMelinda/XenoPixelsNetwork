package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.ClientPacketHandlers;
import net.minecraft.network.FriendlyByteBuf;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncDmzHudStatePacket {
    private final boolean dmzHudEnabled;

    public SyncDmzHudStatePacket(boolean dmzHudEnabled) {
        this.dmzHudEnabled = dmzHudEnabled;
    }

    public static void encode(SyncDmzHudStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.dmzHudEnabled);
    }

    public static SyncDmzHudStatePacket decode(FriendlyByteBuf buf) {
        return new SyncDmzHudStatePacket(buf.readBoolean());
    }

    public static void handle(SyncDmzHudStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> ClientPacketHandlers.handleDmzHudState(msg.dmzHudEnabled));
        ctx.get().setPacketHandled(true);
    }
}
