package net.bullettrain.xenopixelsmod.network;

import net.bullettrain.xenopixelsmod.client.DmzHudClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                DmzHudClientState.setDmzHudEnabled(msg.dmzHudEnabled)));
        ctx.get().setPacketHandled(true);
    }
}
