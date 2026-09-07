package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.combat.ClientChaseFlightState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Server to owner-client chase state, used to pause DMZ's local flight velocity writer. */
public record ChaseFlightStatePacket(boolean active) {
    public ChaseFlightStatePacket(FriendlyByteBuf buf) {
        this(buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(active);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientChaseFlightState.setActive(active));
        ctx.setPacketHandled(true);
    }
}
