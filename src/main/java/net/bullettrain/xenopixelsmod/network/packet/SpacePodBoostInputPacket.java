package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.space.pod.SpacePodFlightPass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/** Short-lived C2S boost intent; the server verifies the sender is bound to a real pod. */
public final class SpacePodBoostInputPacket {
    private final boolean boost;

    public SpacePodBoostInputPacket(boolean boost) { this.boost = boost; }
    public SpacePodBoostInputPacket(FriendlyByteBuf buffer) { this(buffer.readBoolean()); }
    public void encode(FriendlyByteBuf buffer) { buffer.writeBoolean(boost); }
    boolean boost() { return boost; }

    public static void handle(SpacePodBoostInputPacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender != null) SpacePodFlightPass.setBoost(sender, packet.boost);
        });
        context.setPacketHandled(true);
    }
}
