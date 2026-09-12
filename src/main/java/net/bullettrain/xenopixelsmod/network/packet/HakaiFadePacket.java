package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Tells every client tracking an entity how far Hakai has dissolved it.
 *
 * <p>Same reason as {@link SparkingStatePacket}: vanilla never sends a non-player living
 * entity's mob effects to tracker clients, and {@code forceAddEffect} can no-op through
 * {@code CommonHooks.canMobEffectBeApplied}. The fade therefore has its own signal.
 * Amplifier 0 means the body is solid again.
 */
public record HakaiFadePacket(int entityId, int amplifier) {

    public HakaiFadePacket(FriendlyByteBuf buf) {
        this(buf.readVarInt(), buf.readVarInt());
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(entityId);
        buf.writeVarInt(amplifier);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> net.bullettrain.xenopixelsmod.client.combat.HakaiFade.set(
                entityId, amplifier));
        ctx.setPacketHandled(true);
    }
}
