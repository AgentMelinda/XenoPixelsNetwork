package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.minecraft.network.FriendlyByteBuf;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Tells nearby clients which combat pose a player just performed.
 *
 * <p>Deliberately tiny: an entity id and one intent ordinal. No bone transforms, no animation
 * state, nothing per render tick — the client already knows how to render an intent from
 * {@code Bt3AnimationBinding}, so the wire only has to carry which one it was.
 *
 * <p>The server is the authority on the beat. The attacking client predicts the same intent
 * locally from the same pure {@code Bt3ComboChoreography} function for zero-latency feedback;
 * this packet is what every <em>other</em> client plays, and it is also what corrects the attacker
 * if their prediction disagreed.
 */
public class Bt3AnimIntentPacket {

    private final int entityId;
    private final byte intent;

    public Bt3AnimIntentPacket(int entityId, Bt3AnimationIntent intent) {
        this.entityId = entityId;
        this.intent = (byte) intent.ordinal();
    }

    private Bt3AnimIntentPacket(int entityId, byte intent) {
        this.entityId = entityId;
        this.intent = intent;
    }

    public static void encode(Bt3AnimIntentPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.entityId);
        buf.writeByte(msg.intent);
    }

    public static Bt3AnimIntentPacket decode(FriendlyByteBuf buf) {
        return new Bt3AnimIntentPacket(buf.readVarInt(), buf.readByte());
    }

    public static void handle(Bt3AnimIntentPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> net.bullettrain.xenopixelsmod.client.combat.anim.Bt3AnimIntentClient
                .apply(msg.entityId, Bt3AnimationIntent.byOrdinal(msg.intent)));
        ctx.get().setPacketHandled(true);
    }
}
