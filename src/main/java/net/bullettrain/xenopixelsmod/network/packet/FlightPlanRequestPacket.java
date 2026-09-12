package net.bullettrain.xenopixelsmod.network.packet;

import net.bullettrain.xenopixelsmod.block.entity.ShipVlsGuidanceBlockEntity;
import net.bullettrain.xenopixelsmod.missile.BallisticFlightPlan;
import net.bullettrain.xenopixelsmod.missile.BallisticPlanOptimizer;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.vs.VsShipHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;
import com.dragonminez.compat.network.NetworkEvent;

import java.util.function.Supplier;

/** Loads, previews, or atomically applies one advanced flight plan. */
public final class FlightPlanRequestPacket {
    public enum Action { LOAD, PREVIEW, APPLY, APPLY_AND_LAUNCH }

    private final BlockPos computerPos;
    private final int revision;
    private final Action action;
    private final BlockPos target;
    private final long targetShipId;
    private final CompoundTag settings;

    public FlightPlanRequestPacket(BlockPos computerPos, int revision, Action action,
                                   BlockPos target, BallisticFlightPlan.Settings settings) {
        this(computerPos, revision, action, target, -1L, settings);
    }

    public FlightPlanRequestPacket(BlockPos computerPos, int revision, Action action,
                                   BlockPos target, long targetShipId, BallisticFlightPlan.Settings settings) {
        this.computerPos = computerPos;
        this.revision = Math.max(0, revision);
        this.action = action;
        this.target = target == null ? BlockPos.ZERO : target;
        this.targetShipId = targetShipId;
        this.settings = settings == null ? new CompoundTag() : settings.save();
    }

    public FlightPlanRequestPacket(FriendlyByteBuf buf) {
        computerPos = buf.readBlockPos();
        int decodedRevision = buf.readVarInt();
        if (decodedRevision < 0) throw new IllegalArgumentException("revision must not be negative");
        revision = decodedRevision;
        action = buf.readEnum(Action.class);
        target = buf.readBlockPos();
        targetShipId = buf.readLong();
        CompoundTag decoded = buf.readNbt();
        settings = decoded == null ? new CompoundTag() : decoded;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(computerPos);
        buf.writeVarInt(revision);
        buf.writeEnum(action);
        buf.writeBlockPos(target);
        buf.writeLong(targetShipId);
        buf.writeNbt(settings);
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();
            if (!level.hasChunkAt(computerPos)
                    || !(level.getBlockEntity(computerPos) instanceof ShipVlsGuidanceBlockEntity guidance)) return;
            if (!withinReach(player, guidance)) {
                player.displayClientMessage(Component.literal("§cToo far from guidance computer"), true);
                return;
            }

            if (action == Action.LOAD) {
                BallisticFlightPlan.Result loaded = guidance.calculateAdvancedPlan(revision);
                if (loaded != null) ModNetwork.sendToPlayer(player, new FlightPlanResultPacket(loaded));
                return;
            }
            BallisticFlightPlan.Settings requested = BallisticFlightPlan.Settings.load(settings);
            BlockPos requestedTarget = action == Action.LOAD && guidance.getTarget() != null
                    ? guidance.getTarget() : clampTarget(level, target);
            Vec3 launch = guidance.getLaunchWorldPosition();
            Vec3 resolvedTarget = Vec3.atCenterOf(requestedTarget);
            Vec3 targetVelocity = Vec3.ZERO;
            long resolvedShipId = -1L;
            if (targetShipId >= 0 && targetShipId == guidance.getTargetShipId()) {
                var moving = VsShipHelper.getLoadedShipById(level, targetShipId);
                if (moving != null) {
                    var p = VsShipHelper.worldPosition(moving);
                    var v = VsShipHelper.velocity(level, moving);
                    resolvedTarget = new Vec3(p.x(), p.y(), p.z());
                    targetVelocity = v == null ? Vec3.ZERO : new Vec3(v.x(), v.y(), v.z());
                    requestedTarget = BlockPos.containing(resolvedTarget);
                    resolvedShipId = targetShipId;
                }
            }
            BallisticFlightPlan.Result result = BallisticPlanOptimizer.optimize(
                    revision, launch, resolvedTarget, targetVelocity, resolvedShipId, requested,
                    guidance.getBoostAccel(), guidance.getBoostTicks(),
                    guidance.getGravitySi(), guidance.getDragCoefficient(),
                    (x, z) -> level.hasChunk(x >> 4, z >> 4)
                            ? level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) : Double.NaN,
                    guidance.getObservedTickSeconds());
            if (action == Action.APPLY || action == Action.APPLY_AND_LAUNCH) {
                if (resolvedShipId >= 0) guidance.setMovingTarget(resolvedShipId, requestedTarget);
                else guidance.setTargetWorld(requestedTarget.getX(), requestedTarget.getY(), requestedTarget.getZ());
                guidance.setPlannerSettings(requested);
                guidance.applyAdvancedPlan(result);
            }
            ModNetwork.sendToPlayer(player, new FlightPlanResultPacket(result));
            if (action == Action.APPLY_AND_LAUNCH) {
                int launched = guidance.firePulse(level);
                player.displayClientMessage(Component.literal((launched > 0 ? "§a" : "§c")
                        + "Launch " + (launched > 0 ? "accepted" : "failed")
                        + (result.feasible() ? "" : " §6(plan warning)")
                        + " §7| " + guidance.getLastStatus()), true);
            }
        });
        ctx.setPacketHandled(true);
    }

    private static BlockPos clampTarget(ServerLevel level, BlockPos target) {
        return new BlockPos(Math.max(-30_000_000, Math.min(30_000_000, target.getX())),
                Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight(), target.getY())),
                Math.max(-30_000_000, Math.min(30_000_000, target.getZ())));
    }

    private static boolean withinReach(ServerPlayer player, ShipVlsGuidanceBlockEntity guidance) {
        Vec3 world = guidance.getLaunchWorldPosition();
        return player.distanceToSqr(world.x, world.y, world.z) <= 64.0 * 64.0;
    }
}
