package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.aero.seat.XenoPilotSeatEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Client → server: one control frame from a seated pilot.
 *
 * <p>This is the only continuously-streamed packet in the mod, so it is built to be small and
 * cheap: attitude as hundredths of a degree in shorts, throttle and flaps as percent bytes, the
 * rest as one flag byte — about ten bytes on the wire. The client sends it at most
 * {@link #MIN_INTERVAL_TICKS}-spaced and only when something actually changed.
 *
 * <p><b>What the server trusts.</b> Nothing except "this player pressed things". The packet
 * cannot move a ship, cannot address a seat the sender is not sitting in, and is subject to a
 * per-player interval check before it is even decoded into an intent. The values then go through
 * {@link net.bullettrain.xenopixelsmod.aero.AeroActionDispatcher}, which re-applies flight-mode,
 * engagement, power-tier and range rules exactly as it does for the GUI.
 *
 * <p>Excess packets are dropped silently rather than treated as an offence: a laggy client
 * legitimately bursts, and kicking players over network timing is a worse failure than ignoring
 * a frame.
 */
public class SeatFlightInputPacket {

    /** Server-side floor on packet spacing. The client aims for 2 ticks; this allows a little jitter. */
    private static final int MIN_INTERVAL_TICKS = 1;
    /** Entries older than this are pruned; a player who stops flying should not be remembered. */
    private static final int PRUNE_AFTER_TICKS = 200;

    public static final int FLAG_AIR_BRAKE = 1;
    public static final int FLAG_AUTO_LEVEL = 1 << 1;
    /**
     * The pilot has just moved the flap control, so this frame's flap value is an instruction
     * rather than a report. Without it the seat would push the client's flap position every
     * tick, and since setting flaps by hand turns auto-flap off, auto-flap could never stay on
     * while anyone was sitting in the seat.
     */
    public static final int FLAG_FLAP_SET = 1 << 2;
    /** Pilot is in mouse-aim mode; see {@link net.bullettrain.xenopixelsmod.aero.AeroAction.SetAttitude}. */
    public static final int FLAG_MOUSE_AIM = 1 << 3;

    private static final Map<UUID, Integer> LAST_INPUT_TICK = new HashMap<>();

    private final int seatEntityId;
    private final short yawCentiDeg;
    private final short pitchCentiDeg;
    private final short rollCentiDeg;
    private final byte pitchStickPercent;
    private final byte rollStickPercent;
    private final byte yawStickPercent;
    private final byte throttlePercent;
    private final byte flapPercent;
    private final byte flags;

    public SeatFlightInputPacket(int seatEntityId, double yawDeg, double pitchDeg, double rollDeg,
                                 double pitchStick, double rollStick, double yawStick, boolean mouseAim,
                                 double throttle, double flap, boolean airBrake, boolean autoLevel,
                                 boolean flapCommanded) {
        this.seatEntityId = seatEntityId;
        this.yawCentiDeg = encodeAngle(yawDeg);
        this.pitchCentiDeg = encodeAngle(pitchDeg);
        this.rollCentiDeg = encodeAngle(rollDeg);
        this.pitchStickPercent = encodeStick(pitchStick);
        this.rollStickPercent = encodeStick(rollStick);
        this.yawStickPercent = encodeStick(yawStick);
        this.throttlePercent = encodeUnit(throttle);
        this.flapPercent = encodeUnit(flap);
        this.flags = (byte) ((airBrake ? FLAG_AIR_BRAKE : 0)
                | (autoLevel ? FLAG_AUTO_LEVEL : 0)
                | (flapCommanded ? FLAG_FLAP_SET : 0)
                | (mouseAim ? FLAG_MOUSE_AIM : 0));
    }

    public SeatFlightInputPacket(FriendlyByteBuf buf) {
        this.seatEntityId = buf.readVarInt();
        this.yawCentiDeg = buf.readShort();
        this.pitchCentiDeg = buf.readShort();
        this.rollCentiDeg = buf.readShort();
        this.pitchStickPercent = buf.readByte();
        this.rollStickPercent = buf.readByte();
        this.yawStickPercent = buf.readByte();
        this.throttlePercent = buf.readByte();
        this.flapPercent = buf.readByte();
        this.flags = buf.readByte();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeVarInt(seatEntityId);
        buf.writeShort(yawCentiDeg);
        buf.writeShort(pitchCentiDeg);
        buf.writeShort(rollCentiDeg);
        buf.writeByte(pitchStickPercent);
        buf.writeByte(rollStickPercent);
        buf.writeByte(yawStickPercent);
        buf.writeByte(throttlePercent);
        buf.writeByte(flapPercent);
        buf.writeByte(flags);
    }

    /** ±180° fits comfortably in a short at hundredths of a degree; anything else is clamped. */
    private static short encodeAngle(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        return (short) Math.round(Math.max(-180.0, Math.min(180.0, degrees)) * 100.0);
    }

    private static byte encodeUnit(double value) {
        if (!Double.isFinite(value)) return 0;
        return (byte) Math.round(Math.max(0.0, Math.min(1.0, value)) * 100.0);
    }

    /** -1..1 stick position as a signed percent; fits a byte with room to spare. */
    private static byte encodeStick(double value) {
        if (!Double.isFinite(value)) return 0;
        return (byte) Math.round(Math.max(-1.0, Math.min(1.0, value)) * 100.0);
    }

    public static void handle(SeatFlightInputPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            // You may only fly the seat you are sitting in. This replaces a reach check: being
            // the seat's passenger is a stronger claim than being near it.
            Entity vehicle = player.getVehicle();
            if (!(vehicle instanceof XenoPilotSeatEntity seat) || seat.getId() != msg.seatEntityId) return;
            if (seat.getControllingPassenger() != player) return;

            if (rateLimited(player)) return;

            seat.input().accept(
                    msg.throttlePercent / 100.0,
                    msg.yawCentiDeg / 100.0,
                    msg.pitchCentiDeg / 100.0,
                    msg.rollCentiDeg / 100.0,
                    msg.pitchStickPercent / 100.0,
                    msg.rollStickPercent / 100.0,
                    msg.yawStickPercent / 100.0,
                    (msg.flags & FLAG_MOUSE_AIM) != 0,
                    msg.flapPercent / 100.0,
                    (msg.flags & FLAG_AIR_BRAKE) != 0,
                    (msg.flags & FLAG_AUTO_LEVEL) != 0,
                    (msg.flags & FLAG_FLAP_SET) != 0);
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * @return true if this player's last frame was too recent to accept another
     */
    private static boolean rateLimited(ServerPlayer player) {
        int now = player.server.getTickCount();
        Integer previous = LAST_INPUT_TICK.put(player.getUUID(), now);
        if (LAST_INPUT_TICK.size() > 64) {
            LAST_INPUT_TICK.entrySet().removeIf(entry -> now - entry.getValue() > PRUNE_AFTER_TICKS);
        }
        return previous != null && now - previous < MIN_INTERVAL_TICKS;
    }
}
