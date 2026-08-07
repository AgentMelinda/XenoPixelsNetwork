package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server control for the Ballistic Guidance Computer GUI.
 * Actions: SET target XYZ, CLEAR, LAUNCH ship/tubes, ABORT flight.
 */
public class GuidanceControlPacket {
    public enum Action {
        SET, CLEAR, LAUNCH, ABORT, PAIR_NEARBY, CLEAR_PAIRS, SET_SPEED, SET_APEX_Y, SET_CRUISE_Y,
        SET_FLEET, FLEET_SALVO, SET_PHYSICS, SET_STOP_DISTANCE
    }

    private final BlockPos bePos;
    private final Action action;
    private final int x;
    private final int y;
    private final int z;

    public GuidanceControlPacket(BlockPos bePos, Action action, int x, int y, int z) {
        this.bePos = bePos;
        this.action = action;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static GuidanceControlPacket setTarget(BlockPos bePos, int x, int y, int z) {
        return new GuidanceControlPacket(bePos, Action.SET, x, y, z);
    }

    public static GuidanceControlPacket clear(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.CLEAR, 0, 0, 0);
    }

    public static GuidanceControlPacket launch(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.LAUNCH, 0, 0, 0);
    }

    public static GuidanceControlPacket abort(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.ABORT, 0, 0, 0);
    }

    public static GuidanceControlPacket pairNearby(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.PAIR_NEARBY, 0, 0, 0);
    }

    public static GuidanceControlPacket clearPairs(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.CLEAR_PAIRS, 0, 0, 0);
    }

    /** x = speed level 1–20 (higher for heavy ships). */
    public static GuidanceControlPacket setSpeed(BlockPos bePos, int speedLevel) {
        return new GuidanceControlPacket(bePos, Action.SET_SPEED, speedLevel, 0, 0);
    }

    /** x = loft peak Y (climb to); 0 = auto. */
    public static GuidanceControlPacket setApexY(BlockPos bePos, int apexY) {
        return new GuidanceControlPacket(bePos, Action.SET_APEX_Y, apexY, 0, 0);
    }

    /** x = cruise altitude Y (level flight after loft); 0 = same as loft. */
    public static GuidanceControlPacket setCruiseY(BlockPos bePos, int cruiseY) {
        return new GuidanceControlPacket(bePos, Action.SET_CRUISE_Y, cruiseY, 0, 0);
    }

    /** channel 0 disables fleet control; interval is 1-200 ticks. */
    public static GuidanceControlPacket setFleet(BlockPos bePos, int channel, int intervalTicks) {
        return new GuidanceControlPacket(bePos, Action.SET_FLEET, channel, intervalTicks, 0);
    }

    public static GuidanceControlPacket fleetSalvo(BlockPos bePos) {
        return new GuidanceControlPacket(bePos, Action.FLEET_SALVO, 0, 0, 0);
    }

    /** gravity encoded in milli-m/s²; drag encoded in 1e-8 per block. */
    public static GuidanceControlPacket setPhysics(BlockPos bePos, double gravitySi, double drag) {
        int encodedGravity = (int) Math.round(Math.max(0.01, Math.min(100.0, gravitySi)) * 1_000.0);
        int encodedDrag = (int) Math.round(Math.max(0.0, Math.min(0.01, drag)) * 100_000_000.0);
        return new GuidanceControlPacket(bePos, Action.SET_PHYSICS, encodedGravity, encodedDrag, 0);
    }

    /** Distance encoded in hundredths of a block; 0 restores automatic hull-safe stopping. */
    public static GuidanceControlPacket setStopDistance(BlockPos bePos, double blocks) {
        int encoded = (int) Math.round(Math.max(0.0, Math.min(100_000.0, blocks)) * 100.0);
        return new GuidanceControlPacket(bePos, Action.SET_STOP_DISTANCE, encoded, 0, 0);
    }

    public GuidanceControlPacket(FriendlyByteBuf buf) {
        this.bePos = buf.readBlockPos();
        this.action = buf.readEnum(Action.class);
        this.x = buf.readVarInt();
        this.y = buf.readVarInt();
        this.z = buf.readVarInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(bePos);
        buf.writeEnum(action);
        buf.writeVarInt(x);
        buf.writeVarInt(y);
        buf.writeVarInt(z);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context c = ctx.get();
        c.enqueueWork(() -> {
            ServerPlayer player = c.getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(bePos)) return;
            BlockEntity raw = level.getBlockEntity(bePos);
            if (!(raw instanceof ShipVlsGuidanceBlockEntity be)) {
                player.displayClientMessage(Component.literal("§cNo guidance computer there"), true);
                return;
            }
            // World-space reach (shipyard XYZ must not be used — VS ships are far in shipyard)
            if (!withinGuidanceReach(player, level, bePos)) {
                player.displayClientMessage(Component.literal("§cToo far from guidance computer"), true);
                return;
            }

            switch (action) {
                case SET -> {
                    // Clamp to reasonable world bounds
                    int cx = Math.max(-30_000_000, Math.min(30_000_000, x));
                    int cy = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), y));
                    int cz = Math.max(-30_000_000, Math.min(30_000_000, z));
                    be.setTargetWorld(cx, cy, cz);
                    be.recomputeSolution();
                    int synced = be.broadcastFleetTarget();
                    String rangeErr = be.rangeLimitMessage();
                    if (rangeErr != null) {
                        player.displayClientMessage(Component.literal(
                                "§c" + rangeErr + " §8(/xenoperf set maxrange <blocks>)"), true);
                    } else {
                        double r = be.horizontalRangeToTarget();
                        var calc = be.getLastCalculation();
                        player.displayClientMessage(Component.literal(String.format(
                                "§bTarget §f%d %d %d §7| range §f%.0f §7| az/elev §f%s/%.1f° §7| ETA §f%ss §7| required §f%s m/s",
                                cx, cy, cz, r,
                                calc != null ? String.format("%.1f°", calc.azimuthDeg()) : "?",
                                be.getLastPitchDeg(),
                                be.getLastEtaTicks() > 0
                                        ? String.format("%.1f", be.getLastEtaTicks() / 20.0) : "?",
                                calc != null ? String.format("%.1f", calc.requiredSpeed() * 20.0) : "?")
                                + (synced > 0 ? " §7| synced §f" + synced : "")), true);
                    }
                }
                case CLEAR -> {
                    be.clearTarget();
                    player.displayClientMessage(Component.literal("§7Guidance target cleared"), true);
                }
                case LAUNCH -> {
                    if (be.getTarget() == null) {
                        player.displayClientMessage(Component.literal("§cSet a target XYZ first"), true);
                        return;
                    }
                    String rangeErr = be.rangeLimitMessage();
                    if (rangeErr != null) {
                        player.displayClientMessage(Component.literal(
                                "§c" + rangeErr + " §8(/xenoperf set maxrange <blocks>)"), true);
                        return;
                    }
                    int n = be.firePulse(level);
                    player.displayClientMessage(Component.literal(
                            "§6Launch §f" + n + " §7| " + be.getLastStatus()), true);
                }
                case ABORT -> {
                    if (be.abortShipFlight()) {
                        player.displayClientMessage(Component.literal("§cShip ballistic aborted"), true);
                    } else {
                        player.displayClientMessage(Component.literal("§7Nothing to abort"), true);
                    }
                }
                case PAIR_NEARBY -> {
                    int n = be.pairNearbyThrusters();
                    player.displayClientMessage(Component.literal(
                            "§aPaired §f" + n + " §anew thruster(s) §7(total "
                                    + be.getPairedThrusterCount() + ") §8— redstone launches"), true);
                }
                case CLEAR_PAIRS -> {
                    int n = be.clearPairedThrusters();
                    player.displayClientMessage(Component.literal(
                            "§7Unpaired §f" + n + " §7thruster(s)"), true);
                }
                case SET_SPEED -> {
                    int lvl = Math.max(1, Math.min(20, x));
                    be.setSpeedLevel(lvl);
                    player.displayClientMessage(Component.literal(String.format(
                            "§6Missile speed §f%d/20 §7| accel §f%.2f §7| burn §f%dt §8(%s)",
                            be.getSpeedLevel(),
                            be.getBoostAccel(),
                            be.getBoostTicks(),
                            speedHint(lvl))), true);
                }
                case SET_APEX_Y -> {
                    int ay = Math.max(0, x);
                    be.setDesiredApexY(ay);
                    if (ay <= 0) {
                        player.displayClientMessage(Component.literal(
                                "§bLoft Y §fAUTO §7(physics/range) §8— " + be.getLastStatus()), true);
                    } else {
                        player.displayClientMessage(Component.literal(String.format(
                                "§bLoft Y §f%d §7(climb peak) §8— %s",
                                ay, be.getLastStatus())), true);
                    }
                }
                case SET_CRUISE_Y -> {
                    int cy = Math.max(0, x);
                    be.setDesiredCruiseY(cy);
                    if (cy <= 0) {
                        player.displayClientMessage(Component.literal(
                                "§bCruise Y §f= loft §7(level at peak) §8— " + be.getLastStatus()), true);
                    } else {
                        player.displayClientMessage(Component.literal(String.format(
                                "§bCruise Y §f%d §7(level flight → target) §8— %s",
                                cy, be.getLastStatus())), true);
                    }
                }
                case SET_FLEET -> {
                    int channel = Math.max(0, Math.min(9_999, x));
                    int interval = Math.max(1, Math.min(200, y));
                    be.setFleetChannel(channel);
                    be.setSalvoIntervalTicks(interval);
                    player.displayClientMessage(Component.literal(channel == 0
                            ? "§7Fleet control disabled; this computer is standalone"
                            : "§aFleet channel §f" + channel + " §7| salvo interval §f"
                            + interval + "t"), true);
                }
                case FLEET_SALVO -> {
                    if (be.getTarget() == null) {
                        player.displayClientMessage(Component.literal("§cSet a target XYZ first"), true);
                        return;
                    }
                    int queued = be.queueFleetSalvo();
                    player.displayClientMessage(Component.literal(queued > 0
                            ? "§6Fleet salvo queued: §f" + queued + " §7vessel(s), channel §f"
                            + be.getFleetChannel()
                            : "§cNo loaded, in-range guidance computers available"), true);
                }
                case SET_PHYSICS -> {
                    double gravity = Math.max(10, Math.min(100_000, x)) / 1_000.0;
                    double drag = Math.max(0, Math.min(1_000_000, y)) / 100_000_000.0;
                    be.setFlightPhysics(gravity, drag);
                    player.displayClientMessage(Component.literal(String.format(
                            "§bFlight model §7| gravity §f%.4f m/s² §7| drag §f%.8f /m §7| %s",
                            be.getGravitySi(), be.getDragCoefficient(), be.getLastStatus())), true);
                }
                case SET_STOP_DISTANCE -> {
                    double blocks = Math.max(0, Math.min(10_000_000, x)) / 100.0;
                    be.setGuidanceStopDistance(blocks);
                    player.displayClientMessage(Component.literal(blocks <= 0.0
                            ? "§bGuidance stop distance §fAUTO §7(hull-safe)"
                            : String.format("§bGuidance stops §f%.2f blocks §7from target", blocks)), true);
                }
            }
        });
        c.setPacketHandled(true);
    }

    private static String speedHint(int lvl) {
        if (lvl <= 4) return "light craft";
        if (lvl <= 8) return "normal";
        if (lvl <= 12) return "heavy";
        if (lvl <= 16) return "very heavy";
        return "max / capital";
    }

    /**
     * Player ↔ computer distance in <b>world</b> space (VS shipyard coords are millions away).
     */
    private static boolean withinGuidanceReach(ServerPlayer player, ServerLevel level, BlockPos bePos) {
        double px = bePos.getX() + 0.5;
        double py = bePos.getY() + 0.5;
        double pz = bePos.getZ() + 0.5;
        try {
            var world = dev.ryanhcode.sable.companion.SableCompanion.INSTANCE
                    .projectOutOfSubLevel(level, new net.minecraft.world.phys.Vec3(px, py, pz));
            if (world != null) {
                px = world.x;
                py = world.y;
                pz = world.z;
            }
        } catch (Throwable ignored) {
        }
        return player.distanceToSqr(px, py, pz) <= 64.0 * 64.0;
    }
}
