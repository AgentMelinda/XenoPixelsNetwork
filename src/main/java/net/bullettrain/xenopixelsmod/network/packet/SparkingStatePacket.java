package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.client.combat.SparkingClientState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Tells every client tracking a player whether that player is Sparking.
 *
 * <p>Mob effects are not synced to trackers, only to the player who holds them, so the Sparking
 * aura needs its own signal to be visible on anyone but yourself.
 */
public record SparkingStatePacket(int entityId, boolean sparking) {

    public SparkingStatePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readBoolean());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeBoolean(sparking);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> SparkingClientState.set(entityId, sparking));
        ctx.setPacketHandled(true);
    }
}
