package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/** Server-confirmed start/cancel state for the automatic X-X-X then A cinematic rush. */
public final class Bt3RushStatePacket {

    public enum Phase {
        START,
        CANCEL
    }

    private final Phase phase;
    private final int attackerId;
    private final int targetId;
    private final int sequenceId;
    private final String profileId;

    public Bt3RushStatePacket(Phase phase, int attackerId, int targetId, int sequenceId, String profileId) {
        this.phase = phase;
        this.attackerId = attackerId;
        this.targetId = targetId;
        this.sequenceId = sequenceId;
        this.profileId = profileId == null ? "universal" : profileId;
    }

    public Bt3RushStatePacket(FriendlyByteBuf buf) {
        this(buf.readEnum(Phase.class), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readUtf(64));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(phase);
        buf.writeVarInt(attackerId);
        buf.writeVarInt(targetId);
        buf.writeVarInt(sequenceId);
        buf.writeUtf(profileId, 64);
    }

    public static void handle(Bt3RushStatePacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() ->
                net.bullettrain.xenopixelsmod.client.combat.anim.Bt3CinematicRushClient.apply(
                        packet.phase, packet.attackerId, packet.targetId,
                        packet.sequenceId, packet.profileId));
        context.get().setPacketHandled(true);
    }
}
