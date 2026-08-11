package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.aero.AeroBus;
import net.bullettrain.xenopixelsmod.aero.AeroAutopilotMode;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.client.ClientScreens;
import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server → client Aero controller state.
 *
 * <p>The server is authoritative for every value here; the GUI renders this and never its own
 * prediction. Sent on open, after each accepted control action, and whenever the block entity
 * syncs.
 */
public class AeroStatePacket {
    private final AeroStateSnapshot state;

    public AeroStatePacket(AeroStateSnapshot state) {
        this.state = state;
    }

    public AeroStateSnapshot state() {
        return state;
    }

    public AeroStatePacket(FriendlyByteBuf buf) {
        this.state = new AeroStateSnapshot(
                buf.readBlockPos(),
                buf.readEnum(ControllerMode.class),
                buf.readVarInt(),
                buf.readVarInt() / 1_000.0,
                buf.readVarInt() / 100.0,
                buf.readVarInt() / 100.0,
                buf.readVarInt() / 100.0,
                buf.readBoolean(),
                buf.readEnum(AeroAutopilotMode.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readEnum(AeroBus.PowerTier.class),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readUtf(256));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(state.controllerPos());
        buf.writeEnum(state.mode());
        buf.writeVarInt(state.enabledMask());
        buf.writeVarInt((int) Math.round(state.throttle() * 1_000.0));
        buf.writeVarInt((int) Math.round(state.yawDeg() * 100.0));
        buf.writeVarInt((int) Math.round(state.pitchDeg() * 100.0));
        buf.writeVarInt((int) Math.round(state.rollDeg() * 100.0));
        buf.writeBoolean(state.flightEngaged());
        buf.writeEnum(state.autopilotMode());
        buf.writeVarInt(state.waypointIndex());
        buf.writeVarInt(state.waypointCount());
        buf.writeDouble(state.targetDistance());
        buf.writeDouble(state.actualSpeed());
        buf.writeEnum(state.powerTier());
        buf.writeVarInt(state.storedEnergy());
        buf.writeVarInt(state.drawFePerTick());
        buf.writeVarInt(state.linkCount());
        buf.writeVarInt(state.healthyLinkCount());
        // Truncate rather than risk exceeding the read limit on a long status string.
        String status = state.status();
        buf.writeUtf(status.length() > 256 ? status.substring(0, 256) : status, 256);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> ClientScreens.receiveAeroState.accept(state));
        ctx.setPacketHandled(true);
    }
}
