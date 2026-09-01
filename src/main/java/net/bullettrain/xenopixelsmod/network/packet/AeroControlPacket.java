package net.bullettrain.xenopixelsmod.network.packet;

import com.dragonminez.compat.network.NetworkEvent;
import net.bullettrain.xenopixelsmod.aero.AeroAction;
import net.bullettrain.xenopixelsmod.aero.AeroAutopilotMode;
import net.bullettrain.xenopixelsmod.aero.AeroActionDispatcher;
import net.bullettrain.xenopixelsmod.aero.AeroStateSnapshot;
import net.bullettrain.xenopixelsmod.aero.AeroSubsystem;
import net.bullettrain.xenopixelsmod.aero.ControllerMode;
import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.Supplier;

/**
 * Client → server intent for the Aero flight controller.
 *
 * <p>This is a thin transport: it carries an encoded intent and hands it to
 * {@link AeroActionDispatcher}, which is the only place validation happens. Nothing here
 * trusts the client — out-of-range values are clamped by the bus, and the dispatcher refuses
 * commands that the controller's mode or power state does not permit.
 *
 * <p>Doubles travel as scaled ints (throttle ×1000, angles ×100) so the packet stays compact
 * and the wire format is exact rather than float-dependent.
 */
public class AeroControlPacket {

    public enum Kind {
        SET_MODE, SET_THROTTLE, SET_ATTITUDE, TOGGLE_SUBSYSTEM, LINK, EMERGENCY_STOP,
        SET_AUTOPILOT, REQUEST_STATE, SET_FLAP, TOGGLE_AUTO_FLAP
    }

    private final BlockPos bePos;
    private final Kind kind;
    private final int a;
    private final int b;
    private final int c;

    public AeroControlPacket(BlockPos bePos, Kind kind, int a, int b, int c) {
        this.bePos = bePos;
        this.kind = kind;
        this.a = a;
        this.b = b;
        this.c = c;
    }

    public static AeroControlPacket setMode(BlockPos pos, ControllerMode mode) {
        return new AeroControlPacket(pos, Kind.SET_MODE, mode.ordinal(), 0, 0);
    }

    /** Throttle 0..1, encoded in thousandths. */
    public static AeroControlPacket setThrottle(BlockPos pos, double throttle) {
        return new AeroControlPacket(pos, Kind.SET_THROTTLE,
                (int) Math.round(Math.max(0.0, Math.min(1.0, throttle)) * 1_000.0), 0, 0);
    }

    /** Angles in degrees, encoded in hundredths. */
    public static AeroControlPacket setAttitude(BlockPos pos, double yaw, double pitch, double roll) {
        return new AeroControlPacket(pos, Kind.SET_ATTITUDE,
                encodeAngle(yaw), encodeAngle(pitch), encodeAngle(roll));
    }

    public static AeroControlPacket toggle(BlockPos pos, AeroSubsystem subsystem, boolean enabled) {
        return new AeroControlPacket(pos, Kind.TOGGLE_SUBSYSTEM,
                subsystem.ordinal(), enabled ? 1 : 0, 0);
    }

    public static AeroControlPacket link(BlockPos pos, AeroAction.Link.Op op) {
        return new AeroControlPacket(pos, Kind.LINK, op.ordinal(), 0, 0);
    }

    public static AeroControlPacket emergencyStop(BlockPos pos) {
        return new AeroControlPacket(pos, Kind.EMERGENCY_STOP, 0, 0, 0);
    }

    public static AeroControlPacket setAutopilot(BlockPos pos, AeroAutopilotMode mode) {
        return new AeroControlPacket(pos, Kind.SET_AUTOPILOT, mode.ordinal(), 0, 0);
    }

    public static AeroControlPacket requestState(BlockPos pos) {
        return new AeroControlPacket(pos, Kind.REQUEST_STATE, 0, 0, 0);
    }

    /** Flap extension 0..1, encoded in thousandths. */
    public static AeroControlPacket setFlap(BlockPos pos, double level) {
        return new AeroControlPacket(pos, Kind.SET_FLAP,
                (int) Math.round(Math.max(0.0, Math.min(1.0, level)) * 1_000.0), 0, 0);
    }

    public static AeroControlPacket toggleAutoFlap(BlockPos pos) {
        return new AeroControlPacket(pos, Kind.TOGGLE_AUTO_FLAP, 0, 0, 0);
    }

    private static int encodeAngle(double degrees) {
        if (!Double.isFinite(degrees)) return 0;
        return (int) Math.round(Math.max(-360.0, Math.min(360.0, degrees)) * 100.0);
    }

    public AeroControlPacket(FriendlyByteBuf buf) {
        this.bePos = buf.readBlockPos();
        this.kind = buf.readEnum(Kind.class);
        this.a = buf.readVarInt();
        this.b = buf.readVarInt();
        this.c = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(bePos);
        buf.writeEnum(kind);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        buf.writeVarInt(c);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(bePos)) return;
            BlockEntity raw = level.getBlockEntity(bePos);
            if (!(raw instanceof ShipVlsGuidanceBlockEntity be)) {
                player.displayClientMessage(Component.literal("§cNo flight controller there"), true);
                return;
            }
            // Same world-space reach rule the guidance GUI uses; ship-local coords are far away.
            if (!GuidanceControlPacket.withinGuidanceReach(player, level, bePos)) {
                player.displayClientMessage(Component.literal("§cToo far from flight controller"), true);
                return;
            }

            if (kind == Kind.REQUEST_STATE) {
                ModNetwork.sendToPlayer(player, new AeroStatePacket(
                        AeroStateSnapshot.of(bePos, be.aeroBus())));
                return;
            }
            AeroAction action = toAction();
            if (action == null) {
                player.displayClientMessage(Component.literal("§cUnknown controller action"), true);
                return;
            }

            AeroActionDispatcher.Result result = AeroActionDispatcher.dispatch(be, action, player);
            player.displayClientMessage(Component.literal(
                    (result.accepted() ? "§b" : "§c") + result.message()), true);
            // Echo authoritative state back so the GUI never renders an optimistic value.
            ModNetwork.sendToPlayer(player, new AeroStatePacket(
                    AeroStateSnapshot.of(bePos, be.aeroBus())));
        });
        ctx.setPacketHandled(true);
    }

    private AeroAction toAction() {
        switch (kind) {
            case SET_MODE -> {
                ControllerMode[] modes = ControllerMode.values();
                if (a < 0 || a >= modes.length) return null;
                return new AeroAction.SetMode(modes[a]);
            }
            case SET_THROTTLE -> {
                return new AeroAction.SetThrottle(a / 1_000.0);
            }
            case SET_ATTITUDE -> {
                return new AeroAction.SetAttitude(a / 100.0, b / 100.0, c / 100.0);
            }
            case TOGGLE_SUBSYSTEM -> {
                AeroSubsystem[] subsystems = AeroSubsystem.values();
                if (a < 0 || a >= subsystems.length) return null;
                return new AeroAction.ToggleSubsystem(subsystems[a], b != 0);
            }
            case LINK -> {
                AeroAction.Link.Op[] ops = AeroAction.Link.Op.values();
                if (a < 0 || a >= ops.length) return null;
                return new AeroAction.Link(ops[a]);
            }
            case EMERGENCY_STOP -> {
                return new AeroAction.EmergencyStop();
            }
            case SET_AUTOPILOT -> {
                AeroAutopilotMode[] modes = AeroAutopilotMode.values();
                if (a < 0 || a >= modes.length) return null;
                return new AeroAction.SetAutopilot(modes[a]);
            }
            case SET_FLAP -> {
                return new AeroAction.SetFlap(a / 1_000.0);
            }
            case TOGGLE_AUTO_FLAP -> {
                return new AeroAction.ToggleAutoFlap();
            }
            case REQUEST_STATE -> {
                return null;
            }
            default -> {
                return null;
            }
        }
    }
}
