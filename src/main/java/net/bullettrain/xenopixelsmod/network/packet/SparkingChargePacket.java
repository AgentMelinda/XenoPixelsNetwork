package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.combat.SparkingChargeClientState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Server-to-owner progress for the full-ki charge that precedes Sparking. */
public record SparkingChargePacket(boolean charging, int litSegments) {
    public SparkingChargePacket(FriendlyByteBuf buf) {
        this(buf.readBoolean(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(charging);
        buf.writeVarInt(litSegments);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> SparkingChargeClientState.set(charging, litSegments));
        ctx.setPacketHandled(true);
    }
}
