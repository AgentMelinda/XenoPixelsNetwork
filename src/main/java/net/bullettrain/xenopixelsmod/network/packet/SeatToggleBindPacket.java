package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.function.Supplier;

/**
 * Client &rarr; server: the seated pilot pressed the chair-bind toggle key.
 *
 * <p>Flips the sender's seat between bound (flying the nearest flight controller) and standalone
 * (the seat block itself is the host), going through {@link XenoPilotSeatEntity#toggleBinding}
 * so it takes exactly the same engage/disengage path a fresh mount would. Validated the same way
 * as {@link SeatFlightInputPacket}: the sender must be the seat's own controlling passenger.
 */
public class SeatToggleBindPacket {

    private final int seatEntityId;

    public SeatToggleBindPacket(int seatEntityId) {
        this.seatEntityId = seatEntityId;
    }

    public SeatToggleBindPacket(FriendlyByteBuf buf) {
        this.seatEntityId = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(seatEntityId);
    }

    public static void handle(SeatToggleBindPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity vehicle = player.getVehicle();
            if (!(vehicle instanceof XenoPilotSeatEntity seat) || seat.getId() != msg.seatEntityId) return;
            if (seat.getControllingPassenger() != player) return;

            seat.toggleBinding(player);
        });
        ctx.get().setPacketHandled(true);
    }
}
