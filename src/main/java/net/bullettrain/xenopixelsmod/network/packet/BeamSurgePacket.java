package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.combat.beam.BeamSurgeManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

/**
 * Client → server: "I am still holding the fire key on my beam."
 *
 * <p>Payloadless, like DragonMineZ's own {@code BeamClashInputC2S}. It carries no beam id, no
 * growth value and no position, because the server must not take any of those from a client:
 * ownership, resources and every applied value are re-derived in {@link BeamSurgeManager}. The
 * worst a forged packet can do is assert a key is down for a player who has no beam, which does
 * nothing at all.
 *
 * <p>Sent every other tick while a beam is being sustained, matching the cadence DMZ already uses
 * for {@code TechniqueChargeC2S.updateAim}. The manager treats a report as valid for a few ticks,
 * so the sparse cadence costs nothing in responsiveness and releasing the key simply lets the
 * grace window lapse.
 */
public class BeamSurgePacket {

    public BeamSurgePacket() {
    }

    public BeamSurgePacket(FriendlyByteBuf buf) {
    }

    public void encode(FriendlyByteBuf buf) {
    }

    public static void handle(BeamSurgePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BeamSurgeManager.reportFeeding(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
