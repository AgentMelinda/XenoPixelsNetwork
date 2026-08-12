package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Server-approved request to open the local party screen. */
public final class OpenPartyScreenPacket {
    public OpenPartyScreenPacket() {}
    public OpenPartyScreenPacket(FriendlyByteBuf ignored) {}
    public void encode(FriendlyByteBuf ignored) {}

    public static void handle(OpenPartyScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(ClientScreens.openParty);
        ctx.get().setPacketHandled(true);
    }
}
