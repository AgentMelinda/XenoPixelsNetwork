package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.player_freezing.PlayerFreezeExtension;
import dev.ryanhcode.sable.network.packets.tcp.ClientboundFreezePlayerPacket;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodHandoffFrame;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.Map;
import java.util.List;
import java.util.UUID;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * §3's altitude handoff: crossing a charted body's {@code exit_altitude} moves you between that body's
 * own dimension and the Voidsea host, with no command and no portal, because the boundary is data.
 *
 * <p>This is the first real consumer of {@code exit_altitude} and {@code handoff_anchor_y} — the two
 * numbers the chart has parsed and printed since the groundwork without anything ever acting on them.
 * §19.2 calls {@code exit_altitude} the headline knob precisely because it is what makes Earth hand off
 * at 700 and King Kai's world at ~180 with no code between them.
 *
 * <p><b>Both directions are one rule, mirrored.</b> Up past the body's exit altitude leaves the body's
 * dimension; down past that same altitude inside the host enters whichever body you are above. "Above
 * which body" is what makes a round trip <em>and</em> a planet-to-planet hop fall out of the same
 * numbers: an outbound arrival is placed over the launching body's live physical/chart position.
 * Successful managed transfers persist planet-local entrance and departure XYZ per player. Returns
 * prefer the remembered departure X/Z, then entrance X/Z, then the origin. Vanilla teleports do not
 * update these records.
 *
 * <p><b>Why an arrival cannot re-trigger.</b> In surface-entry mode, the host arrival is one block
 * outside the selected sphere's configured contact margin; in altitude-entry mode it sits half a
 * {@code band_thickness} above the exit altitude. The body arrival sits {@value #RETURN_MARGIN} blocks
 * below the exit, so each side's arrival is strictly inside its new world rather than on the line that
 * fires the other half. Earth at exit 700 returns at Y675, including above the surface build ceiling.
 * The cooldown is a backstop against that arithmetic being wrong in some future rule, not the thing
 * that makes it right.
 *
 * <p><b>What this slice does not move.</b> Ships. A Sable vessel is a {@code ServerSubLevel} whose
 * {@code level} field is final, so carrying one across a dimension needs
 * {@code dev.egg.SubLevelWarper.WarpSubLevel} from the {@code dimensional_sable} addon — stage E of this
 * plan, deliberately not assumed here. A player riding anything transfers, and whether their vehicle
 * follows is <b>not verified</b> (§13's Q17 is exactly that question). A return-side destination anchor
 * chunk is now prewarmed near the configured altitude/surface-entry region, but both dimensions are not
 * maintained as a paired live camera/physics host; destination readiness and visible wait still require
 * fresh runtime verification.
 *
 * <p><b>Why a scheduler pass.</b> The same reasoning as {@link SpaceLodDriver} — one budgeted,
 * interval-throttled, exception-isolated sweep on the clock the rest of the space work already uses,
 * rather than another {@code ServerTickEvent} competing for the main tick. The pass only <em>decides</em>;
     * each transfer is deferred to the server tick Post event, because this runs inside Sable's physics
 * step, in the middle of a sub-level walk, and a dimension change unloads levels and removes entities.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceHandoffPass implements SpaceTickScheduler.Pass {
    private static final SpaceHandoffPass PASS = new SpaceHandoffPass();

    /**
     * How far below a body's own exit altitude its arrival must sit, so an arrival can never satisfy the
     * trigger it was just measured against. The alternative — comparing arrival to trigger with no slack —
     * makes a row that authors {@code handoff_anchor_y >= exit_altitude} oscillate on every sweep.
     */
    private static final double RETURN_MARGIN = 25.0;
    /** Ticks a player is left alone after one leg, so a wrong number cannot turn the boundary into a loop. */
    private static final int TRANSFER_COOLDOWN_TICKS = 20;
    /** A slow destination chunk must not leave the player or its ticket pending forever. */
    private static final int ANCHOR_WAIT_TIMEOUT_TICKS = 100;
    /** Distance 1 requests the anchor's block-ticking chunk; it does not promise a 3x3 loaded area. */
    private static final int ANCHOR_TICKET_DISTANCE = 1;
    private static final TicketType<UUID> HANDOFF_ANCHOR_TICKET = TicketType.create(
            "xenopixelsmod:handoff_anchor", Comparator.<UUID>naturalOrder(), ANCHOR_WAIT_TIMEOUT_TICKS);

    /** Game time until which the player is not eligible for another leg. */
    private static final Map<UUID, Long> COOLDOWN_UNTIL = new ConcurrentHashMap<>();
    private record Pending(String bodyId, ResourceLocation source, boolean outbound, long stagedAt) {}
    private record HandoffDestination(ServerLevel level, Vec3 position, Float yawDegrees) {
        private HandoffDestination(ServerLevel level, Vec3 position) { this(level, position, null); }
        private float yawOr(float current) { return yawDegrees == null ? current : yawDegrees; }
    }
    private record AnchorLease(ServerLevel level, ChunkPos chunk, long expiresAt, boolean prewarmed) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    /**
     * Pod legs whose rider has arrived but whose client has not yet been seen riding the destination pod, keyed by
     * the pod BODY so two pods in flight never share a watch; a repeat handoff of the same body replaces its own.
     * {@code seedCleared} records that the server-side tracking seed (restore + freeze) was wiped once the freeze
     * lifted, so only client-driven tracking can count afterwards.
     */
    private record PodArrival(UUID riderId, UUID podId, UUID bodyId, ResourceKey<Level> level, long startedAt,
                              int stableTicks, boolean seedCleared) {}
    private static final Map<UUID, PodArrival> POD_ARRIVALS = new ConcurrentHashMap<>();
    private static final Map<UUID, AnchorLease> ANCHOR_TICKETS = new ConcurrentHashMap<>();
    private static ResourceLocation reportedMissingHost;

    private SpaceHandoffPass() {}

    /**
     * Always registered so enabling space through a config reload can activate the handoff.
     * Both switches are checked before any chart or player work.
     */
    @SubscribeEvent public static void starting(ServerStartingEvent event) {
        SpaceTickScheduler.register(PASS);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SpaceTickScheduler.unregister(PASS);
        releaseAllAnchorTickets();
        HandoffIntangibility.clear();
        COOLDOWN_UNTIL.clear();
        PENDING.clear();
        // Unconfirmed arrivals keep their persisted transfer ticket; only the in-memory watch is dropped.
        POD_ARRIVALS.clear();
        reportedMissingHost = null;
    }

    /** Release temporary readiness tickets before the server closes its chunk sources. */
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        releaseAllAnchorTickets();
        HandoffIntangibility.clear();
        PENDING.clear();
        COOLDOWN_UNTIL.clear();
    }

    /** A departed player's cooldown entry would otherwise live until the next server stop. */
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        HandoffIntangibility.release(event.getEntity());
        COOLDOWN_UNTIL.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
        releaseAnchorTicket(event.getEntity().getUUID());
    }

    @Override public String name() { return "altitude-handoff"; }

    @Override public int intervalTicks() { return XenoSpaceConfig.handoffIntervalTicks(); }

    /**
     * One server-wide sweep over the player list, so exactly one physics system may drive it — the same
     * host-overworld clock key {@code SpaceLodDriver} uses. Two drivers would queue two legs per crossing,
     * and a transfer is not idempotent the way a LOD resolution is.
     */
    @Override
    public boolean appliesTo(SubLevelPhysicsSystem system) {
        ServerLevel level = system.getLevel();
        return level != null && level == level.getServer().overworld();
    }

    /**
     * One sweep: every online player is either climbing out of a charted body's dimension or descending
     * inside the host, and at most one of those two tests can be true for them.
     *
     * @return players examined, which is the work unit {@code TickBudget} defers on
     */
    @Override
    public int run(SubLevelPhysicsSystem system, double deltaSeconds) {
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.handoffEnabled()) {
            releaseAllPrewarmedAnchorTickets();
            return 0;
        }
        MinecraftServer server = system.getLevel().getServer();
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        if (chart.size() == 0) {
            releaseAllPrewarmedAnchorTickets();
            return 0;
        }
        ResourceLocation hostId = XenoSpaceConfig.handoffHost();
        ServerLevel host = server.getLevel(key(hostId));
        // getLevel is the startup-created dimension map, not a chunk-load check. Report once per
        // missing host rather than keeping a ticket that cannot create an absent dimension.
        if (host == null) {
            releaseAllPrewarmedAnchorTickets();
            if (!hostId.equals(reportedMissingHost)) {
                XenoPixelsMod.LOGGER.warn("Altitude handoff host {} is absent from this world's dimensions; correct the pack/config and restart the world", hostId);
                reportedMissingHost = hostId;
            }
            return 0;
        }
        reportedMissingHost = null;
        long clock = system.getLevel().getGameTime();
        double hostTop = topOf(host);

        int examined = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            examined++;
            if (PENDING.containsKey(player.getUUID())) continue;
            if (!(player.level() instanceof ServerLevel level)) continue;
            if (level.dimension().location().equals(hostId)) {
                advanceOutboundClearance(player, chart, level, hostId);
                prewarmInboundAnchor(player, chart, level, hostId);
            } else {
                prewarmOutboundAnchor(player, chart, level, hostId);
            }
            if (onCooldown(player.getUUID(), clock)) continue;
            if (level.dimension().location().equals(hostId)) {
                int body = entryBody(chart, player, hostId, level);
                if (body >= 0 && !HandoffOutboundClearance.blocksCandidate(player, chart.body(body).id()))
                    stage(player, body, clock, false);
                continue;
            }
            var block = player.blockPosition();
            int slot = ChartSurfaceBodyResolver.slotAt(chart, level.dimension().location().toString(),
                    block.getX(), block.getZ());
            if (slot < 0) continue;
            CelestialBody candidate = chart.body(slot);
            if (!candidate.enterable() || !sidecarSpaceEnabled(server, candidate)) continue;
            if (!leaving(player.getY(), chart.exitAltitude(slot))) continue;
            if (!Double.isFinite(hostArrivalY(chart.exitAltitude(slot),
                    bandThickness(server, candidate), hostTop))) continue;
            stage(player, slot, clock, true);
        }
        return examined;
    }

    /**
     * Queues one leg for the end of this tick and starts the cooldown now, so two sweeps cannot both
     * queue a leg for the same crossing.
     */
    private static void stage(ServerPlayer player, int slot, long clock, boolean outbound) {
        MinecraftServer server = player.server;
        COOLDOWN_UNTIL.put(player.getUUID(), clock + TRANSFER_COOLDOWN_TICKS);
        String bodyId = SpaceChartService.state(server).runtime.chart.body(slot).id();
        PENDING.put(player.getUUID(), new Pending(bodyId, player.level().dimension().location(), outbound, clock));
    }

    /** MinecraftServer.execute runs inline on the server thread; Post is the actual deferral. */
    @SubscribeEvent public static void finishTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long clock = server.overworld().getGameTime();
        HandoffIntangibility.tick(server.getTickCount());
        expireAnchorTickets();
        if (!POD_ARRIVALS.isEmpty()) checkPodArrivals(server, clock);
        if (PENDING.isEmpty()) return;
        for (var entry : PENDING.entrySet()) {
            UUID playerId = entry.getKey();
            Pending pending = entry.getValue();
            if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.handoffEnabled()) {
                cancelPending(playerId, pending);
                continue;
            }
            ServerPlayer stillHere = server.getPlayerList().getPlayer(playerId);
            // Re-resolved at execution: the player may have logged out, died, or been moved by a command
            // in between, and a stale transfer would put them somewhere they no longer asked to be. The
            // body is re-resolved by id for the same reason — a chart rebuild can have dropped it.
            if (stillHere == null || !stillHere.isAlive()
                    || !stillHere.level().dimension().location().equals(pending.source())) {
                cancelPending(playerId, pending);
                continue;
            }
            if (clock - pending.stagedAt() >= ANCHOR_WAIT_TIMEOUT_TICKS) {
                boolean physicalEarthMissing = requiresLiveEarthOrbitAnchor(pending.bodyId(), pending.outbound(),
                        net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isUnavailable(pending.bodyId()))
                        && !physicalDestinationReady(server, pending.bodyId(), pending.outbound());
                cancelPending(playerId, pending);
                COOLDOWN_UNTIL.put(playerId, clock + TRANSFER_COOLDOWN_TICKS);
                stillHere.displayClientMessage(Component.literal(physicalEarthMissing
                        ? "Space handoff cancelled: Earth's orbit body did not become ready; retry shortly"
                        : "Space handoff cancelled: destination chunk did not become ready"), true);
                continue;
            }
            ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
            int fresh = chart.slotOf(pending.bodyId());
            if (fresh < 0 || !chart.body(fresh).enterable() || !sidecarSpaceEnabled(server, chart.body(fresh))) {
                cancelPending(playerId, pending);
                continue;
            }
            if (pending.outbound()) {
                if (!pending.source().equals(bodyDimension(chart.body(fresh)))
                        || !leaving(stillHere.getY(), chart.exitAltitude(fresh))) {
                    cancelPending(playerId, pending);
                    continue;
                }
            } else if (!pending.source().equals(XenoSpaceConfig.handoffHost())
                    || entryBody(chart, stillHere, XenoSpaceConfig.handoffHost(), stillHere.serverLevel()) != fresh) {
                cancelPending(playerId, pending);
                continue;
            }
            HandoffDestination destination = destination(stillHere, chart, fresh, pending.outbound());
            if (destination == null) {
                cancelPending(playerId, pending);
                continue;
            }
            // A FULL Voidsea anchor chunk can exist before the permanent Earth sub-level has finished
            // assembling. Do not strand an Earth handoff at the chart coordinate with no planet beneath it.
            if (!physicalDestinationReady(server, pending.bodyId(), pending.outbound())) continue;
            if (!anchorChunkReady(playerId, destination, false)) continue;
            if (!safeArrival(stillHere, destination.level(), destination.position())) {
                cancelPending(playerId, pending);
                stillHere.displayClientMessage(Component.literal("Planet handoff refused: destination is obstructed or outside the world border"), true);
                continue;
            }
            if (!PENDING.remove(playerId, pending)) continue;
            try {
                transfer(stillHere, chart, fresh, pending.outbound(), destination);
            } finally {
                releaseAnchorTicket(playerId);
            }
        }
    }

    private static void cancelPending(UUID playerId, Pending pending) {
        PENDING.remove(playerId, pending);
        releaseAnchorTicket(playerId);
    }

    private static boolean physicalDestinationReady(MinecraftServer server, String bodyId, boolean outbound) {
        boolean unavailable = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isUnavailable(bodyId);
        if (!requiresLiveEarthOrbitAnchor(bodyId, outbound, unavailable)) return true;
        ServerLevel host = server.getLevel(key(XenoSpaceConfig.handoffHost()));
        return host != null && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, bodyId) != null;
    }

    static boolean requiresLiveEarthOrbitAnchor(String bodyId, boolean outbound, boolean bodyUnavailable) {
        return outbound && !bodyUnavailable
                && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.BODY_ID.equals(bodyId);
    }

    /** Convert between the moving source/target chart frames at the current server orbit time. */
    private static Vec3 transitionVelocity(MinecraftServer server, ChartRegistry chart, int sourceSlot,
                                           int targetSlot, Vec3 sourceLocalMotion) {
        if (!XenoSpaceConfig.velocityCarryover()) return Vec3.ZERO;
        chart.evaluate(SpaceOrbitClock.seconds(server));
        return SpaceHandoffVelocity.convert(sourceLocalMotion,
                orbitalVelocity(chart, sourceSlot), orbitalVelocity(chart, targetSlot), true);
    }

    private static Vec3 orbitalVelocity(ChartRegistry chart, int slot) {
        double[] velocity = chart.velocityOf(slot, new double[3]);
        return new Vec3(velocity[0], velocity[1], velocity[2]);
    }

    /** Performs one leg only after the destination's full anchor chunk is available. */
    private static void transfer(ServerPlayer player, ChartRegistry chart, int slot, boolean outbound,
                                 HandoffDestination destination) {
        CelestialBody body = chart.body(slot);
        ServerSubLevel pod = trackedPod(player);
        if (pod != null) {
            transferWithPod(player, chart, slot, outbound, destination, pod);
            return;
        }
        if (outbound) {
            // Directly over the body's live chart position — which is where the client is drawing its
            // proxy, and the same number the LOD ladder measures distance from. The planet you launched
            // from is below you on arrival, and its surface is at the altitude the design's §2.1 band
            // says it should visually be.
            Vec3 motion = transitionVelocity(player.server, chart, slot, -1, player.getDeltaMovement());
            ServerLevel source = player.serverLevel();
            Vec3 departure = player.position();
            HandoffTransitionNoticeV3 notice = transitionNotice(player.server, body, false);
            AddonNetwork.sendToPlayer(player, new HandoffTransitionNoticeV4(notice.overlayMillis(),
                    notice.plasmaShell(), destination.level().dimension().location(), destination.position(), motion));
            Entity moved = player.changeDimension(new DimensionTransition(destination.level(),
                    destination.position(), motion,
                    destination.yawOr(player.getYRot()), player.getXRot(), DimensionTransition.DO_NOTHING));
            if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != destination.level()) return;
            HandoffIntangibility.mark(arrived, arrived.server.getTickCount());
            land(arrived, body, motion, XenoSpaceConfig.velocityCarryover());
            PlanetTravelMemory.commit(arrived, body.id(), departure, null);
            if (XenoSpaceConfig.surfaceEntry()) HandoffOutboundClearance.arm(arrived, body.id());
            logSuccessfulHandoff(arrived, body, true, source, departure, motion);
            return;
        }
        // Convert the Voidsea inertial velocity back into the destination planet's moving frame. Arrival
        // altitude is exit-25, so re-entry effects can later own the descent without a dimension-change stop.
        Vec3 motion = transitionVelocity(player.server, chart, -1, slot, player.getDeltaMovement());
        AddonNetwork.sendToPlayer(player, transitionNotice(player.server, body, true));
        ServerLevel source = player.serverLevel();
        Vec3 departure = player.position();
        Entity moved = player.changeDimension(new DimensionTransition(destination.level(), destination.position(), motion,
                destination.yawOr(player.getYRot()), player.getXRot(), DimensionTransition.DO_NOTHING));
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != destination.level()) return;
        HandoffIntangibility.mark(arrived, arrived.server.getTickCount());
        notifyEntryAssistIfNeeded(arrived, body, motion);
        land(arrived, body, motion, XenoSpaceConfig.velocityCarryover());
        PlanetTravelMemory.commit(arrived, null, null, body.id());
        logSuccessfulHandoff(arrived, body, false, source, departure, motion);
    }

    /** One compact server-side trace lets runtime tests distinguish a failed crossing from an immediate return. */
    private static void logSuccessfulHandoff(ServerPlayer arrived, CelestialBody body, boolean outbound,
                                             ServerLevel source, Vec3 departure, Vec3 carriedMotion) {
        XenoPixelsMod.LOGGER.info("Space handoff complete: direction={}, body={}, player={}, source={} at {}, "
                        + "destination={} at {}, carriedVelocity={}",
                outbound ? "outbound" : "inbound", body.id(), arrived.getScoreboardName(),
                source.dimension().location(), departure, arrived.serverLevel().dimension().location(),
                arrived.position(), carriedMotion);
    }

    /** The body the player rides when it carries a pod identity in the player's own level, else null. */
    static ServerSubLevel trackedPod(Player player) {
        ServerSubLevel tracked = PodRiderBinding.trackedBody(player);
        return tracked != null && !tracked.isRemoved() && tracked.getLevel() == player.level()
                && PodTransferTransaction.podId(tracked) != null ? tracked : null;
    }

    /**
     * The same leg as {@link #transfer} for a player riding a pod: the pod moves first, and the player only
     * follows once it has committed in the destination, so a refused or rolled-back pod cancels the handoff
     * with the rider still aboard. Player motion keeps the existing blocks-per-tick conversion; the pod's body
     * velocity is converted in blocks per second by {@link PodHandoffFrame}.
     */
    private static void transferWithPod(ServerPlayer player, ChartRegistry chart, int slot, boolean outbound,
                                        HandoffDestination destination, ServerSubLevel pod) {
        CelestialBody body = chart.body(slot);
        if (!outbound && !shipsAllowedInAtmosphere(player.server, body)) {
            player.displayClientMessage(Component.literal("Space pods are not allowed in this planet's atmosphere"), true);
            return;
        }
        int sourceSlot = outbound ? slot : -1, targetSlot = outbound ? -1 : slot;
        boolean carried = XenoSpaceConfig.velocityCarryover();
        Vec3 motion = transitionVelocity(player.server, chart, sourceSlot, targetSlot, player.getDeltaMovement());
        ServerLevel source = player.serverLevel();
        Vec3 departure = player.position();
        // transitionVelocity evaluated the chart for this tick when carrying; read the same orbital frame.
        Vector3dc sourceOrbit = carried ? vector(orbitalVelocity(chart, sourceSlot)) : null;
        Vector3dc targetOrbit = carried ? vector(orbitalVelocity(chart, targetSlot)) : null;
        PodLeg leg = podLeg(player, pod, player.serverLevel(), destination.level(), destination.position(),
                sourceOrbit, targetOrbit, position -> {
                    AddonNetwork.sendToPlayer(player, transitionNotice(player.server, body, !outbound));
                    Entity moved = player.changeDimension(new DimensionTransition(destination.level(), position,
                            motion, destination.yawOr(player.getYRot()), player.getXRot(), DimensionTransition.DO_NOTHING));
                    return moved instanceof ServerPlayer arrived && arrived.serverLevel() == destination.level()
                            ? arrived : null;
                });
        if (!leg.moved()) {
            // A stranded pod was already reported at ERROR inside podLeg with its full location.
            if (!leg.stranded()) XenoPixelsMod.LOGGER.warn("Pod handoff to {} cancelled for {}: {}", body.id(),
                    player.getScoreboardName(), leg.problem());
            player.displayClientMessage(Component.literal((leg.stranded()
                    ? "Space handoff failed; your pod was left behind and its location was written to the server log: "
                    : "Space handoff cancelled: ") + leg.problem()), true);
            return;
        }
        ServerPlayer arrived = (ServerPlayer) leg.rider();
        HandoffIntangibility.mark(arrived, arrived.server.getTickCount());
        // restore() put back the rider's captured source-frame motion; the leg's motion replaces it.
        arrived.setDeltaMovement(carried ? motion : Vec3.ZERO);
        if (!outbound) notifyEntryAssistIfNeeded(arrived, body, motion);
        land(arrived, body, motion, carried);
        if (outbound) PlanetTravelMemory.commit(arrived, body.id(), departure, null);
        else PlanetTravelMemory.commit(arrived, null, null, body.id());
        if (outbound && XenoSpaceConfig.surfaceEntry()) HandoffOutboundClearance.arm(arrived, body.id());
        if (leg.awaitingArrival() && leg.transfer() != null) {
            ServerSubLevel copy = leg.transfer().destination();
            UUID podId = PodTransferTransaction.podId(copy);
            if (podId != null) {
                PodArrival previous = POD_ARRIVALS.put(copy.getUniqueId(), new PodArrival(arrived.getUUID(), podId,
                        copy.getUniqueId(), destination.level().dimension(), arrived.server.overworld().getGameTime(),
                        0, false));
                if (previous != null) XenoPixelsMod.LOGGER.warn("Pod {} body {} was handed off again before its arrival "
                        + "was confirmed; the new watch replaces the old one", podId, copy.getUniqueId());
            }
        }
        if (!leg.riderBound()) {
            XenoPixelsMod.LOGGER.warn("Pod handoff to {}: {} arrived but was not re-bound to the pod: {}",
                    body.id(), arrived.getScoreboardName(), leg.problem());
            arrived.displayClientMessage(Component.literal("Arrived, but could not re-board the pod: "
                    + leg.problem()), true);
        }
        logSuccessfulHandoff(arrived, body, outbound, source, departure, motion);
    }

    /** Moves the rider to the destination and returns the arrived instance, or null when it did not arrive there. */
    @FunctionalInterface
    interface RiderMover {
        Player move(Vec3 riderDestination);
    }

    /**
     * @param moved      the rider reached the destination level with the pod committed there
     * @param riderBound the rider is bound to the pod body that survived (destination copy, or the source after a
     *                   refusal/rollback)
     * @param rider      the rider instance to keep using (the arrived instance when moved)
     * @param transfer   the forward pod transaction result, or null when it never started
     * @param stranded   the pod committed in the destination, the rider did not follow, and returning the pod
     *                   failed: it is left in another level from its rider, still holding its transfer ticket
     * @param awaitingArrival a real player arrived and was frozen to the pod; the transfer ticket is held until
     *                   {@link #checkPodArrivals} sees the client riding it (riderBound is only the server seed)
     */
    record PodLeg(boolean moved, boolean riderBound, Player rider, PodTransferTransaction.Result transfer,
                  String problem, boolean stranded, boolean awaitingArrival) {
        PodLeg(boolean moved, boolean riderBound, Player rider, PodTransferTransaction.Result transfer, String problem) {
            this(moved, riderBound, rider, transfer, problem, false, false);
        }

        PodLeg(boolean moved, boolean riderBound, Player rider, PodTransferTransaction.Result transfer, String problem,
               boolean stranded) {
            this(moved, riderBound, rider, transfer, problem, stranded, false);
        }
    }

    /**
     * When the transfer ticket of an arrived pod may be released. Sable 2.0.3 derives a real player's tracking from
     * the client's move packets (ServerboundMovePlayerPacketMixin), and its server-side freeze sets tracking itself
     * and lifts on the next tick once the body exists (player_freezing ServerPlayerMixin.sable$kick), so tracking
     * the pod only counts once the player is not frozen and it has held for {@link #STABLE_TICKS} consecutive
     * ticks. The freeze timer is never a reason to release: a timeout keeps the ticket and reports it.
     */
    static final class PodArrivalPolicy {
        static final int STABLE_TICKS = 40;
        static final long TIMEOUT_TICKS = 600;

        enum Decision { WAIT, CONFIRMED, TIMED_OUT, POD_GONE, RIDER_GONE }

        private PodArrivalPolicy() {}

        /**
         * The new streak: a tick counts only after the server seed was cleared on an earlier tick, with no freeze
         * active and the (now client-driven) tracking on the pod.
         */
        static int nextStreak(int streak, boolean trackingPod, boolean frozen, boolean seedClearedBefore) {
            return seedClearedBefore && trackingPod && !frozen ? streak + 1 : 0;
        }

        static Decision decide(boolean riderOnline, boolean podLive, int streak, long waitedTicks) {
            if (!podLive) return Decision.POD_GONE;
            if (!riderOnline) return Decision.RIDER_GONE;
            if (streak >= STABLE_TICKS) return Decision.CONFIRMED;
            if (waitedTicks >= TIMEOUT_TICKS) return Decision.TIMED_OUT;
            return Decision.WAIT;
        }
    }

    /** Once per server tick: release confirmed arrivals, report the rest when they settle without confirmation. */
    private static void checkPodArrivals(MinecraftServer server, long clock) {
        for (var entry : POD_ARRIVALS.entrySet()) {
            UUID bodyKey = entry.getKey();
            PodArrival arrival = entry.getValue();
            UUID riderId = arrival.riderId();
            ServerPlayer rider = server.getPlayerList().getPlayer(riderId);
            ServerLevel level = server.getLevel(arrival.level());
            ServerSubLevel pod = level != null
                    && SubLevelContainer.getContainer(level).getSubLevel(arrival.bodyId()) instanceof ServerSubLevel body
                    && !body.isRemoved() ? body : null;
            boolean frozen = rider instanceof PlayerFreezeExtension freeze && freeze.sable$getFrozenToSubLevel() != null;
            boolean seedCleared = arrival.seedCleared();
            if (!seedCleared && rider != null && pod != null && !frozen) {
                // restore() and freezeTo/sable$kick set the server tracking themselves, and it would persist with no
                // client packet at all. Wipe it once: afterwards only ServerboundMovePlayerPacketMixin (a client
                // move packet reporting plot space on this pod) can set it back to the pod.
                if (PodRiderBinding.trackedBody(rider) == pod) PodRiderBinding.clear(rider);
                seedCleared = true;
            }
            boolean tracking = rider != null && pod != null && PodRiderBinding.trackedBody(rider) == pod;
            int streak = PodArrivalPolicy.nextStreak(arrival.stableTicks(), tracking, frozen, arrival.seedCleared());
            switch (PodArrivalPolicy.decide(rider != null, pod != null, streak, clock - arrival.startedAt())) {
                case WAIT -> POD_ARRIVALS.replace(bodyKey, arrival, new PodArrival(riderId, arrival.podId(),
                        arrival.bodyId(), arrival.level(), arrival.startedAt(), streak, seedCleared));
                case CONFIRMED -> {
                    POD_ARRIVALS.remove(bodyKey, arrival);
                    PodTransferTransaction.releaseTicket(pod, arrival.podId());
                    XenoPixelsMod.LOGGER.info("Pod handoff confirmed: {} rides pod {} in {}; transfer ticket released",
                            rider.getScoreboardName(), arrival.podId(), arrival.level().location());
                }
                case TIMED_OUT -> {
                    POD_ARRIVALS.remove(bodyKey, arrival);
                    XenoPixelsMod.LOGGER.error("POD ARRIVAL UNCONFIRMED: pod {} body {} in {} at {}: rider {} {} was not "
                                    + "seen riding it within {} ticks; transfer ticket RETAINED, pod stays loaded",
                            arrival.podId(), arrival.bodyId(), arrival.level().location(), pod.logicalPose().position(),
                            rider.getScoreboardName(), riderId, PodArrivalPolicy.TIMEOUT_TICKS);
                    rider.displayClientMessage(Component.literal("Your pod did not attach after the handoff; it is kept "
                            + "loaded where it arrived and its location was written to the server log"), true);
                }
                case RIDER_GONE -> {
                    POD_ARRIVALS.remove(bodyKey, arrival);
                    XenoPixelsMod.LOGGER.error("POD ARRIVAL UNCONFIRMED: rider {} left before riding pod {} body {} in {} "
                            + "at {}; transfer ticket RETAINED", riderId, arrival.podId(), arrival.bodyId(),
                            arrival.level().location(), pod.logicalPose().position());
                }
                case POD_GONE -> {
                    POD_ARRIVALS.remove(bodyKey, arrival);
                    XenoPixelsMod.LOGGER.error("POD ARRIVAL FAILED: pod {} body {} is no longer registered in {}",
                            arrival.podId(), arrival.bodyId(), arrival.level().location());
                }
            }
        }
    }

    /**
     * Holds a real player's client on the pod until it has the pod's sub-level, the same freeze Sable 2.0.3 applies
     * at login and respawn (player_freezing PlayerListMixin.sable$player: connection.send of a
     * ClientboundCustomPayloadPacket wrapping ClientboundFreezePlayerPacket(uuid, plot-local anchor)). Sable never
     * sends it on changeDimension, so a handoff has to.
     */
    private static void freezeOnArrival(ServerPlayer rider, ServerSubLevel pod, Vector3dc plotLocalSeat) {
        Vector3d anchor = new Vector3d(plotLocalSeat);
        ((PlayerFreezeExtension) rider).sable$freezeTo(pod.getUniqueId(), anchor);
        rider.connection.send(new ClientboundCustomPayloadPacket(
                new ClientboundFreezePlayerPacket(pod.getUniqueId(), new Vector3d(anchor))));
    }

    /**
     * One pod-and-rider leg, in an order that never leaves the rider bound to a removed body:
     * capture the rider's plot-local seat, unbind, transfer the pod so that seat lands on
     * {@code riderDestination}, and only after the pod has committed move the rider and re-bind it inside the
     * destination level. A refused or rolled-back pod re-binds the rider to the surviving body where it is. If
     * the rider then fails to arrive, the pod is transferred back to its departure position and re-boarded. The
     * pod's transfer ticket is released once the leg settles.
     *
     * @param sourceOrbitPerSecond chart velocity of the departure frame, or null for no frame carry (both null:
     *                             the pod arrives with zero linear velocity, like an uncarried player)
     */
    static PodLeg podLeg(Player rider, ServerSubLevel pod, ServerLevel sourceLevel, ServerLevel destinationLevel,
                         Vec3 riderDestination, Vector3dc sourceOrbitPerSecond, Vector3dc targetOrbitPerSecond,
                         RiderMover mover) {
        UUID podId = PodTransferTransaction.podId(pod);
        if (podId == null) return new PodLeg(false, false, rider, null, "the tracked body is not a pod");
        PodRiderBinding.Captured captured = PodRiderBinding.capture(rider, pod);
        Vector3d departurePosition = new Vector3d(pod.logicalPose().position());
        Vector3d target = PodHandoffFrame.bodyPositionFor(pod.logicalPose(), captured.plotLocalPosition(),
                vector(riderDestination), new Vector3d());
        RigidBodyHandle handle = RigidBodyHandle.of(pod);
        if (target == null || handle == null || !handle.isValid())
            return new PodLeg(false, true, rider, null, "the pod's placement or physics body is not ready");
        Vector3d departureVelocity = handle.getLinearVelocity(new Vector3d());
        Vector3d arrivalVelocity = sourceOrbitPerSecond == null || targetOrbitPerSecond == null ? new Vector3d()
                : PodHandoffFrame.bodyVelocityAfterFrameChange(departureVelocity, sourceOrbitPerSecond,
                        targetOrbitPerSecond, new Vector3d());
        if (arrivalVelocity == null)
            return new PodLeg(false, true, rider, null, "the pod's destination velocity is not finite");

        // The source body is about to be removed; never leave the rider tracking it.
        PodRiderBinding.clear(rider);
        PodTransferTransaction.Result forward = PodTransferTransaction.transfer(sourceLevel, destinationLevel, pod,
                null, target, arrivalVelocity);
        if (forward.status() != PodTransferTransaction.Status.TRANSFERRED) {
            ServerSubLevel survivor = forward.destination();
            boolean rebound = survivor != null && !survivor.isRemoved()
                    && PodRiderBinding.restore(rider, survivor, seatOn(survivor, captured, captured.worldPosition()));
            return new PodLeg(false, rebound, rider, forward, "the pod transfer was " + forward.status()
                    + " at " + forward.phase() + ": " + forward.reason());
        }

        ServerSubLevel copy = forward.destination();
        Player arrived;
        String moveFailure = null;
        try {
            arrived = mover.move(riderDestination);
        } catch (RuntimeException moveException) {
            arrived = null;
            moveFailure = moveException.toString();
        }
        if (arrived == null || arrived.level() != destinationLevel) {
            // The pod is committed in the destination but its rider is not; bring it back so the rider keeps it.
            PodTransferTransaction.Result back = PodTransferTransaction.transfer(destinationLevel, sourceLevel, copy,
                    null, departurePosition, departureVelocity);
            boolean returned = back.status() == PodTransferTransaction.Status.TRANSFERRED;
            ServerSubLevel home = back.destination();
            boolean rebound = home != null && !home.isRemoved()
                    && PodRiderBinding.restore(rider, home, seatOn(home, captured, captured.worldPosition()));
            String problem = "the rider did not reach the destination"
                    + (moveFailure == null ? "" : " (" + moveFailure + ")") + "; the pod "
                    + (returned ? "was returned to its departure position"
                    : "could not be returned (" + back.status() + ": " + back.reason() + ")");
            if (returned) {
                if (!home.isRemoved()) PodTransferTransaction.releaseTicket(home, podId);
                return new PodLeg(false, rebound, rider, forward, problem);
            }
            // Stranded: the transfer ticket is deliberately KEPT so the pod stays loaded (and is reloaded after a
            // restart) where an operator can find it; releasing it would let an unridden pod unload unseen.
            XenoPixelsMod.LOGGER.error(strandedPodReport(podId, home, destinationLevel, sourceLevel, rider, rebound,
                    back));
            return new PodLeg(false, rebound, rider, forward, problem, true);
        }
        // The copy may sit in another plot cell (PodTransferTransaction.Result.storageShift), so the source seat's
        // storage coordinates are not valid on it: derive the seat from where the rider must land.
        PodRiderBinding.Captured arrivalSeat = seatOn(copy, captured, riderDestination);
        boolean bound = PodRiderBinding.restore(arrived, copy, arrivalSeat);
        if (arrived instanceof ServerPlayer serverRider) {
            // A real client re-derives tracking from its own move packets, so the server seed above lasts one
            // packet; freeze the client onto the pod and keep the ticket until the client is seen riding it.
            freezeOnArrival(serverRider, copy, arrivalSeat.plotLocalPosition());
            return new PodLeg(true, bound, arrived, forward,
                    bound ? null : "the rider tracking did not bind to the pod", false, true);
        }
        // Not a networked player (GameTest stand-in): there is no client to wait for.
        PodTransferTransaction.releaseTicket(copy, podId);
        return new PodLeg(true, bound, arrived, forward, bound ? null : "the rider tracking did not bind to the pod");
    }

    /**
     * The operator line for a stranded pod: which body, in which level and where, why the return failed, and where
     * the rider was left. One self-contained line so it survives log filtering.
     */
    static String strandedPodReport(UUID podId, ServerSubLevel home, ServerLevel podLevel, ServerLevel riderLevel,
                                    Player rider, boolean riderBound, PodTransferTransaction.Result back) {
        boolean live = home != null && !home.isRemoved();
        String where = live ? home.getLevel().dimension().location() + " at " + home.logicalPose().position()
                + " (body " + home.getUniqueId() + ", transfer ticket kept)"
                : podLevel.dimension().location() + ", body not registered after the failed return";
        return "POD STRANDED: pod " + podId + " is in " + where + "; returning it to "
                + riderLevel.dimension().location() + " failed (" + back.status() + " at " + back.phase() + ": "
                + back.reason() + "); rider " + rider.getScoreboardName() + " " + rider.getUUID() + " is in "
                + rider.level().dimension().location() + " at " + rider.position()
                + (riderBound ? ", re-bound to the pod" : ", not bound to any pod")
                + ". Recover with PodTransferTransaction.transfer from that level, then releaseTicket.";
    }

    /**
     * {@code captured} re-expressed on {@code body}: the plot-local seat that puts the rider at {@code world} under
     * the body's CURRENT pose. Storage coordinates are frame-specific (a plot-cell move shifts them), so a seat is
     * never carried from one body to another; the original capture is left untouched for rollback.
     */
    static PodRiderBinding.Captured seatOn(ServerSubLevel body, PodRiderBinding.Captured captured, Vec3 world) {
        Vector3d seat = body.logicalPose().transformPositionInverse(vector(world), new Vector3d());
        return new PodRiderBinding.Captured(captured.playerId(), world, seat, captured.motion(), captured.yRot(),
                captured.xRot());
    }

    private static Vector3d vector(Vec3 value) {
        return new Vector3d(value.x, value.y, value.z);
    }

    private static void notifyEntryAssistIfNeeded(ServerPlayer player, CelestialBody body, Vec3 motion) {
        if (body.dimensionId() == null) return;
        var settings = SpaceChartService.state(player.server).sidecarSettings.get(body.dimensionId());
        if (settings == null || !SpaceEntrySpeed.requiresAssistPrompt(motion, settings.safeEntrySpeed())) return;
        player.displayClientMessage(Component.literal("High re-entry speed for " + body.displayName()
                + " (threshold " + settings.safeEntrySpeed() + " blocks/tick); slow your descent"), true);
    }

    private static HandoffDestination destination(ServerPlayer player, ChartRegistry chart, int slot, boolean outbound) {
        CelestialBody body = chart.body(slot);
        if (outbound) {
            ServerLevel host = player.server.getLevel(key(XenoSpaceConfig.handoffHost()));
            if (host == null) return null;
            double x = chart.x(slot), z = chart.z(slot);
            var center = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id());
            var homeFocus = SpaceHomeOrbitalFrame.resolve(host, chart);
            double centerY = center == null ? SpaceHomeOrbitalFrame.chartY(chart, slot,
                    homeFocus == null ? Double.NaN : homeFocus.y()) : center.y();
            double radius = center == null ? body.radiusBlocks()
                    : net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveRadius(host, body.id());
            if (center != null) { x = center.x(); z = center.z(); }
            double y = outboundHostArrivalY(XenoSpaceConfig.surfaceEntry(), centerY, radius,
                    XenoSpaceConfig.handoffSurfaceMargin(), chart.exitAltitude(slot),
                    bandThickness(player.server, body), topOf(host));
            if (!Double.isFinite(y) || y < host.getMinBuildHeight()) return null;
            // Map the planet-local X/Z displacement from its authored spatial anchor onto the current
            // chart/live-body centre. The same local X/Z is stored as LastLeavingPos and restored on return.
            var horizontal = SpaceHandoffCoordinates.outbound(player.getX(), player.getZ(),
                    body.spatialAnchorX(), body.spatialAnchorZ(), x, z);
            return horizontal == null ? null : new HandoffDestination(host,
                    new Vec3(horizontal.x(), y, horizontal.z()));
        }
        ResourceLocation dimension = bodyDimension(body);
        if (dimension == null || !(player.server.getLevel(key(dimension)) instanceof ServerLevel target)) return null;
        Vec3 authoredSurface = body.surfaceAnchor() == null ? null
                : new Vec3(body.surfaceAnchor().x(), body.surfaceAnchor().y(), body.surfaceAnchor().z());
        Vec3 surface = PlanetTravelMemory.arrival(PlanetTravelMemory.persisted(player), body.id(),
                chart.exitAltitude(slot), target.getMinBuildHeight(), target.getHeight(),
                body.spatialAnchorX(), body.spatialAnchorZ(), authoredSurface);
        Float yaw = body.surfaceAnchor() == null ? null : body.surfaceAnchor().yawDegrees();
        return surface == null ? null : new HandoffDestination(target, surface, yaw);
    }

    /** Add a short-lived ticket and check the non-blocking result of a FULL-status chunk request. */
    private static boolean anchorChunkReady(UUID playerId, HandoffDestination destination, boolean prewarmed) {
        ChunkPos chunk = HandoffAnchorMath.chunkAt(destination.position());
        AnchorLease previous = ANCHOR_TICKETS.get(playerId);
        if (previous != null && (!previous.level().equals(destination.level()) || !previous.chunk().equals(chunk)))
            releaseAnchorTicket(playerId);
        destination.level().getChunkSource().addRegionTicket(HANDOFF_ANCHOR_TICKET, chunk,
                ANCHOR_TICKET_DISTANCE, playerId);
        ANCHOR_TICKETS.put(playerId, new AnchorLease(destination.level(), chunk,
                destination.level().getGameTime() + ANCHOR_WAIT_TIMEOUT_TICKS, prewarmed));
        var fullChunk = destination.level().getChunkSource().getChunkFuture(
                chunk.x, chunk.z, ChunkStatus.FULL, false);
        return HandoffAnchorMath.fullChunkFutureReady(fullChunk);
    }

    /** Keeps the opposite-side anchor warm from the charted per-body altitude through the actual crossing. */
    private static void prewarmOutboundAnchor(ServerPlayer player, ChartRegistry chart, ServerLevel source,
                                              ResourceLocation hostId) {
        UUID playerId = player.getUUID();
        var block = player.blockPosition();
        int slot = ChartSurfaceBodyResolver.slotAt(chart, source.dimension().location().toString(),
                block.getX(), block.getZ());
        if (slot < 0 || !chart.body(slot).enterable() || !sidecarSpaceEnabled(player.server, chart.body(slot))
                || !warmUpReached(player.getY(), chart.body(slot).warmUpAltitude())) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        CelestialBody body = chart.body(slot);
        ServerLevel host = player.server.getLevel(key(hostId));
        if (host == null) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        if (body.id().equals(net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.BODY_ID)
                && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id()) == null) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        HandoffDestination destination = destination(player, chart, slot, true);
        if (destination == null) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        if (!destination.level().getWorldBorder().isWithinBounds(
                net.minecraft.core.BlockPos.containing(destination.position()))) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        anchorChunkReady(playerId, destination, true);
    }

    /**
     * Warms one return-side planet chunk while a player approaches that body's handoff window in the Voidsea.
     * The warm-up span mirrors the authored planet-side distance (exit - warm-up) around the host arrival
     * altitude (exit + half-band). At most one candidate is ticketed per player, and the existing lease
     * expiry/logout/server-stop paths own its cleanup.
     */
    private static void prewarmInboundAnchor(ServerPlayer player, ChartRegistry chart, ServerLevel host,
                                             ResourceLocation hostId) {
        UUID playerId = player.getUUID();
        int best = -1;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (!body.enterable() || !sidecarSpaceEnabled(player.server, body)) continue;
            ResourceLocation dimension = bodyDimension(body);
            if (dimension == null || dimension.equals(hostId)
                    || !(player.server.getLevel(key(dimension)) instanceof ServerLevel)) continue;
            boolean surfaceEntry = XenoSpaceConfig.surfaceEntry();
            // Altitude mode must use the chart coordinates because nearestBodyBelow uses them too;
            // surface mode mirrors bodyTouched and therefore uses the live physical sphere when present.
            var liveCenter = surfaceEntry
                    ? net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id()) : null;
            double centerX = liveCenter == null ? chart.x(slot) : liveCenter.x();
            var homeFocus = SpaceHomeOrbitalFrame.resolve(host, chart);
            double centerY = liveCenter == null ? SpaceHomeOrbitalFrame.chartY(chart, slot,
                    homeFocus == null ? Double.NaN : homeFocus.y()) : liveCenter.y();
            double centerZ = liveCenter == null ? chart.z(slot) : liveCenter.z();
            double dx = centerX - player.getX();
            double dz = centerZ - player.getZ();
            double distance = Math.hypot(dx, dz);
            double radius = liveCenter == null ? body.radiusBlocks()
                    : net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveRadius(host, body.id());
            double reach = Math.max(radius * 2.0, XenoSpaceConfig.handoffReach());
            if (!Double.isFinite(distance) || !Double.isFinite(reach)) continue;
            double score;
            if (surfaceEntry) {
                // Match bodyTouched's live 3D surface metric while starting the ticket up to handoffReach
                // blocks before contact, so the eventual touched body already has its arrival chunk warm.
                double gap = surfaceGap(player.getX(), player.getY(), player.getZ(), centerX, centerY, centerZ, radius);
                if (!withinInboundSurfaceWarmup(gap, XenoSpaceConfig.handoffReach()) || distance > reach) continue;
                score = gap;
            } else {
                // Altitude mode resolves nearestBodyBelow by horizontal chart distance; warm that same winner.
                if (!inboundPrewarmReached(player.getY(), reentryAltitude(player.server, body, chart.exitAltitude(slot)),
                        bandThickness(player.server, body), body.warmUpAltitude()) || distance > reach) continue;
                score = distance;
            }
            if (score >= bestDistance) continue;
            best = slot;
            bestDistance = score;
        }
        if (best < 0) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        HandoffDestination destination = destination(player, chart, best, false);
        if (destination == null) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        if (!destination.level().getWorldBorder().isWithinBounds(
                net.minecraft.core.BlockPos.containing(destination.position()))) {
            releasePrewarmedAnchorTicket(playerId);
            return;
        }
        anchorChunkReady(playerId, destination, true);
    }

    /** Host-side counterpart of {@link #warmUpReached(double, double)} for the mirrored return leg. */
    static boolean inboundPrewarmReached(double playerY, double exitAltitude, double bandThickness,
                                         double bodyWarmUpAltitude) {
        if (!Double.isFinite(playerY) || !Double.isFinite(exitAltitude)
                || !Double.isFinite(bandThickness) || bandThickness <= 0.0
                || !Double.isFinite(bodyWarmUpAltitude) || bodyWarmUpAltitude >= exitAltitude) return false;
        double bodyWarmUpSpan = exitAltitude - bodyWarmUpAltitude;
        double hostArrivalY = exitAltitude + bandThickness * 0.5;
        double startY = hostArrivalY + bodyWarmUpSpan;
        return Double.isFinite(startY) && playerY <= startY;
    }

    static boolean withinInboundSurfaceWarmup(double surfaceGap, double warmUpRange) {
        return Double.isFinite(surfaceGap) && Double.isFinite(warmUpRange)
                && warmUpRange >= 0.0 && surfaceGap <= warmUpRange;
    }

    static boolean warmUpReached(double playerY, double warmUpAltitude) {
        return Double.isFinite(playerY) && Double.isFinite(warmUpAltitude) && playerY >= warmUpAltitude;
    }

    public static String warmUpProblem(double warmUpAltitude, double exitAltitude) {
        if (!Double.isFinite(warmUpAltitude)) return "warm_up_altitude must be finite";
        if (!Double.isFinite(exitAltitude)) return "exit_altitude must be finite";
        if (warmUpAltitude >= exitAltitude) return "warm_up_altitude must be below exit_altitude";
        return null;
    }

    private static void expireAnchorTickets() {
        for (var entry : ANCHOR_TICKETS.entrySet()) {
            AnchorLease lease = entry.getValue();
            if (lease.level().getGameTime() >= lease.expiresAt()) releaseAnchorTicket(entry.getKey());
        }
    }

    private static void releaseAnchorTicket(UUID playerId) {
        AnchorLease lease = ANCHOR_TICKETS.remove(playerId);
        if (lease != null) lease.level().getChunkSource().removeRegionTicket(HANDOFF_ANCHOR_TICKET,
                lease.chunk(), ANCHOR_TICKET_DISTANCE, playerId);
    }

    private static void releasePrewarmedAnchorTicket(UUID playerId) {
        AnchorLease lease = ANCHOR_TICKETS.get(playerId);
        if (lease != null && lease.prewarmed()) releaseAnchorTicket(playerId);
    }

    private static void releaseAllPrewarmedAnchorTickets() {
        for (var entry : ANCHOR_TICKETS.entrySet()) {
            if (entry.getValue().prewarmed()) releaseAnchorTicket(entry.getKey());
        }
    }

    private static void releaseAllAnchorTickets() {
        for (UUID playerId : List.copyOf(ANCHOR_TICKETS.keySet())) releaseAnchorTicket(playerId);
    }

    /**
     * The two-frame conversion §3's step 6 asks for — "planet-local X/Z offsets from the body's
     * {@code spatial_anchor} — Voidsea (0,0,700) is above the planet's (0,0,0)", and §19.4 flags as the
     * thing still needing a spike. The offset direction is implemented as authored: a descent arrives at
     * its distance from the body's chart column, in the planet's own units.
     *
     * <p>The outbound direction is the conservative half. Carrying the launcher's planet-local position
     * <em>out</em> as well would put a player who lifted off from (5000, 700, -3000) thousands of blocks
     * beside the proxy the client is drawing, outside every body's reach, with no way back down except the
     * void rescue. Arriving in-column instead trades §3's "directly below their exit vector" continuity for
     * a round trip that always terminates, which is the better trade for a first slice.
     */
    static double toBody(double hostCoordinate, double bodyChartCoordinate) {
        return hostCoordinate - bodyChartCoordinate;
    }

    /**
     * Post-transfer cleanup on whichever instance the dimension change handed back. Vanilla clears
     * {@code DeltaMovement} on a dimension change, so carried motion is re-applied here rather than
     * trusted to survive, and the fall counter is reset because a player arriving from a climb has a fall
     * distance measured against the world they just left.
     */
    private static void land(Entity moved, CelestialBody body, Vec3 motion, boolean carried) {
        if (!(moved instanceof ServerPlayer player)) return;
        player.resetFallDistance();
        if (carried && motion != null && Double.isFinite(motion.y)) player.setDeltaMovement(motion);
        // A floorless host has no ground to land on, and vanilla's anti-fly watchdog would kick an
        // airborne survival player there after 80 ticks; the grant is a no-op where the switch is off.
        SpaceFlightGuard.grant(player);
        player.displayClientMessage(Component.literal("§d" + body.displayName()
                + (carried ? " — velocity carried" : " — arrival stationary")), true);
    }

    // --- The pure rule: no server, level, or event in sight ---

    /** Exit validation uses arrival bounds, not the build ceiling: players can fly above it. */
    public static String exitProblem(double exit, double anchor, int bodyMin, int bodyHeight,
                                     double band, int hostMin, int hostHeight) {
        if (!Double.isFinite(exit)) return "exit altitude must be finite";
        if (!Double.isFinite(anchor) || anchor < bodyMin || anchor >= bodyMin + (double) bodyHeight)
            return "surface anchor is outside its dimension's build volume";
        if (anchor >= exit) return "exit altitude must be above the surface anchor";
        if (!Double.isFinite(PlanetTravelMemory.reentryY(exit, bodyMin)))
            return "exit altitude minus 25 is below the dimension floor";
        double arrival = hostArrivalY(exit, band, hostMin + (double) hostHeight);
        if (!Double.isFinite(arrival) || arrival < hostMin)
            return "host arrival is below its minimum build Y";
        return null;
    }

    /** Command read/set; validation happens before either live state or persisted config changes. */
    public static String configureExit(MinecraftServer server, String bodyId, Double y) {
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(bodyId);
        if (slot < 0) throw new IllegalArgumentException("unknown chart body: " + bodyId);
        if (y == null) return bodyId + " exit=" + chart.exitAltitude(slot)
                + " (authored=" + chart.body(slot).exitAltitude() + ")";
        CelestialBody body = chart.body(slot);
        if (!body.enterable()) throw new IllegalArgumentException("body is not enterable");
        if (!sidecarSpaceEnabled(server, body)) throw new IllegalArgumentException("body is disabled by its dimension sidecar");
        String warmUpProblem = warmUpProblem(body.warmUpAltitude(), y);
        if (warmUpProblem != null) throw new IllegalArgumentException(warmUpProblem);
        ResourceLocation dimension = bodyDimension(body);
        ServerLevel target = dimension == null ? null : server.getLevel(key(dimension));
        ServerLevel host = server.getLevel(key(XenoSpaceConfig.handoffHost()));
        if (target == null || host == null) throw new IllegalArgumentException("body or host dimension is absent from this world");
        if (target == host) throw new IllegalArgumentException("body dimension must differ from host");
        String problem = exitProblem(y, surfaceAnchorY(body), target.getMinBuildHeight(), target.getHeight(),
                bandThickness(server, body), host.getMinBuildHeight(), host.getHeight());
        if (problem != null) throw new IllegalArgumentException(problem);
        XenoSpaceConfig.setHandoffExit(bodyId, y);
        chart.applyExitOverrides(XenoSpaceConfig.handoffExitOverrides());
        return bodyId + " exit=" + chart.exitAltitude(slot) + " (live override; saved to space config)";
    }

    /** Leaving: strictly above the body's exit altitude, so standing on the line is not a crossing. */
    public static boolean leaving(double playerY, double exitAltitude) {
        return Double.isFinite(playerY) && Double.isFinite(exitAltitude) && playerY > exitAltitude;
    }

    /** Returning: at or below the same altitude in the host — §19.3's mirror of {@code exit_altitude}. */
    public static boolean returning(double playerY, double exitAltitude) {
        return Double.isFinite(playerY) && Double.isFinite(exitAltitude) && playerY <= exitAltitude;
    }

    /**
     * The Y a climber arrives at inside the host: half a handoff band above the body's exit altitude.
     *
     * @return the arrival Y, or {@link Double#NaN} for invalid inputs. Players can fly above the host's
     *         block build ceiling, so the host's top build Y does not cap this transfer coordinate.
     */
    public static double hostArrivalY(double exitAltitude, double bandThickness, double hostTopY) {
        if (!Double.isFinite(exitAltitude) || !(bandThickness > 0.0) || !Double.isFinite(hostTopY)) {
            return Double.NaN;
        }
        double arrival = exitAltitude + bandThickness / 2.0;
        return Double.isFinite(arrival) && Math.abs(arrival) < 30_000_000 ? arrival : Double.NaN;
    }

    /**
     * Surface-entry mode arrives one block outside the configured surface-contact margin, above the
     * body's actual sphere. The planet-side exit altitude still controls when the player leaves the
     * planet dimension; it must not become an arbitrary thousands-of-blocks orbit altitude in Voidsea.
     */
    static double hostSurfaceArrivalY(double centerY, double radius, double contactMargin) {
        if (!Double.isFinite(centerY) || !Double.isFinite(radius) || radius <= 0.0
                || !Double.isFinite(contactMargin) || contactMargin < 0.0) return Double.NaN;
        double arrival = centerY + radius + contactMargin + 1.0;
        return Double.isFinite(arrival) && Math.abs(arrival) < 30_000_000 ? arrival : Double.NaN;
    }

    static double outboundHostArrivalY(boolean surfaceEntry, double centerY, double radius, double contactMargin,
                                       double exitAltitude, double bandThickness, double hostTopY) {
        return surfaceEntry ? hostSurfaceArrivalY(centerY, radius, contactMargin)
                : hostArrivalY(exitAltitude, bandThickness, hostTopY);
    }

    /**
     * Compatibility helper: returns exit altitude minus 25, allowing flight above build height.
     * @return arrival Y, or NaN if it is invalid or below the dimension floor
     */
    public static double bodyArrivalY(double anchorY, double exitAltitude, int minBuildHeight, int height) {
        // Compatibility helper for callers/tests: a flying arrival may be above build height.
        return PlanetTravelMemory.reentryY(exitAltitude, minBuildHeight);
    }

    /** True while a leg was already queued or performed for that player inside the cooldown. */
    public static boolean onCooldown(UUID player, long clock) {
        Long until = COOLDOWN_UNTIL.get(player);
        return until != null && clock < until;
    }

    /**
     * Which body a descending player in the host is above: the nearest enterable one whose column reaches
     * them, where a column is {@code max(2 x radius_blocks, }{@link XenoSpaceConfig#handoffReach()}{@code )}
     * of horizontal chart distance. Nearest wins, which is what makes "up from Earth, over to Namek, down"
     * land on Namek rather than back on Earth.
     *
     * @return the winning slot, or -1 when no enterable body reaches the player or none has an altitude
     *         mirror they are under
     */
    private static int entryBody(ChartRegistry chart, ServerPlayer player, ResourceLocation hostId, ServerLevel host) {
        return XenoSpaceConfig.surfaceEntry() ? bodyTouched(chart, player, hostId, host)
                : nearestBodyBelow(chart, player, hostId);
    }

    /** Update the persisted same-planet latch before evaluating the host's inbound contact trigger. */
    private static void advanceOutboundClearance(ServerPlayer player, ChartRegistry chart, ServerLevel host,
                                                 ResourceLocation hostId) {
        String guardedBody = HandoffOutboundClearance.armedBody(player);
        if (guardedBody == null) return;
        if (!XenoSpaceConfig.surfaceEntry()) {
            HandoffOutboundClearance.arm(player, null);
            return;
        }
        if (XenoSpaceConfig.handoffOutboundClearanceBlocks() <= 0.0) {
            HandoffOutboundClearance.arm(player, null);
            return;
        }
        int slot = chart.slotOf(guardedBody);
        if (slot < 0) return; // Keep the latch if a chart reload temporarily removes its measurement source.
        CelestialBody body = chart.body(slot);
        if (!body.enterable() || !sidecarSpaceEnabled(player.server, body)
                || hostId.equals(bodyDimension(body))) return;
        var center = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id());
        double cx = center == null ? chart.x(slot) : center.x();
        var homeFocus = SpaceHomeOrbitalFrame.resolve(host, chart);
        double cy = center == null ? SpaceHomeOrbitalFrame.chartY(chart, slot,
                homeFocus == null ? Double.NaN : homeFocus.y()) : center.y();
        double cz = center == null ? chart.z(slot) : center.z();
        double radius = center == null ? body.radiusBlocks()
                : net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveRadius(host, body.id());
        double gap = surfaceGap(player.getX(), player.getY(), player.getZ(), cx, cy, cz, radius);
        double requiredGap = XenoSpaceConfig.handoffSurfaceMargin()
                + XenoSpaceConfig.handoffOutboundClearanceBlocks();
        HandoffOutboundClearance.suppressCandidate(player, null, gap, requiredGap);
        if (HandoffOutboundClearance.armedBody(player) == null)
            XenoPixelsMod.LOGGER.info("Space handoff clearance released: body={}, player={}, surfaceGap={}, requiredGap={}",
                    guardedBody, player.getScoreboardName(), gap, requiredGap);
    }

    /**
     * Surface entry: the enterable body whose sphere the player is touching — within
     * {@link XenoSpaceConfig#handoffSurfaceMargin()} of its surface — measured in 3D from the live Sable
     * centre of a physical planet, or from the chart position for a body with no physical shell. The one
     * whose surface is closest wins.
     *
     * @return the winning slot, or -1 when the player is not touching any enterable body
     */
    private static int bodyTouched(ChartRegistry chart, ServerPlayer player, ResourceLocation hostId, ServerLevel host) {
        double margin = XenoSpaceConfig.handoffSurfaceMargin();
        int best = -1;
        double bestGap = Double.MAX_VALUE;
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (!body.enterable() || !sidecarSpaceEnabled(player.server, body)) continue;
            ResourceLocation dimension = bodyDimension(body);
            if (dimension == null || dimension.equals(hostId)) continue;
            var center = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id());
            double cx = center == null ? chart.x(slot) : center.x();
            var homeFocus = SpaceHomeOrbitalFrame.resolve(host, chart);
            double cy = center == null ? SpaceHomeOrbitalFrame.chartY(chart, slot,
                    homeFocus == null ? Double.NaN : homeFocus.y()) : center.y();
            double cz = center == null ? chart.z(slot) : center.z();
            double radius = center == null ? body.radiusBlocks()
                    : net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveRadius(host, body.id());
            double gap = surfaceGap(player.getX(), player.getY(), player.getZ(), cx, cy, cz, radius);
            if (!touching(gap, margin)) continue;
            if (gap < bestGap) { bestGap = gap; best = slot; }
        }
        return best;
    }

    /** Distance from a point to a sphere's surface; negative inside it. */
    public static double surfaceGap(double px, double py, double pz, double cx, double cy, double cz, double radius) {
        double dx = px - cx, dy = py - cy, dz = pz - cz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz) - radius;
    }

    /** True when a surface gap counts as touching the body: at or inside {@code margin} of its surface. */
    public static boolean touching(double gap, double margin) {
        return Double.isFinite(gap) && Double.isFinite(margin) && gap <= margin;
    }

    private static int nearestBodyBelow(ChartRegistry chart, ServerPlayer player, ResourceLocation hostId) {
        double reach = XenoSpaceConfig.handoffReach();
        int best = -1;
        double bestDistance = Double.MAX_VALUE;
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (!body.enterable() || !sidecarSpaceEnabled(player.server, body)) continue;
            ResourceLocation dimension = bodyDimension(body);
            // A body bound to the host is a dimension inside itself: handing off there would be a loop.
            if (dimension == null || dimension.equals(hostId)) continue;
            double dx = chart.x(slot) - player.getX();
            double dz = chart.z(slot) - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > Math.max(body.radiusBlocks() * 2.0, reach)) continue;
            if (!returning(player.getY(), reentryAltitude(player.server, body, chart.exitAltitude(slot)))) continue;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = slot;
            }
        }
        return best;
    }

    private static double topOf(ServerLevel level) {
        return level.getMinBuildHeight() + (double) Math.max(1, level.getHeight());
    }

    private static ResourceLocation bodyDimension(CelestialBody body) {
        String authored = body.dimensionId();
        return authored == null ? null : ResourceLocation.tryParse(authored);
    }

    private static ResourceKey<Level> key(ResourceLocation id) {
        return id == null ? null : ResourceKey.create(Registries.DIMENSION, id);
    }

    /**
     * §24.3's validation as a sentence: the host, the reach, and every bound body's band — with the reason
     * for each body that cannot hand off rather than a silent refusal at the boundary. This is the surface
     * that would have told the operator that a {@code min_y 0 / height 384} host cannot express Earth's 700
     * exit altitude.
     */
    public static String status(MinecraftServer server) {
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        ResourceLocation hostId = XenoSpaceConfig.handoffHost();
        ServerLevel host = server.getLevel(key(hostId));
        StringBuilder out = new StringBuilder();
        out.append("handoff ").append(XenoSpaceConfig.handoffEnabled() ? "on" : "off")
                .append(" (space ").append(XenoSpaceConfig.enabled() ? "on" : "off").append(")")
                .append("\nhost=").append(hostId)
                .append(host == null ? " ABSENT FROM WORLD - fix pack/config and restart the world"
                        : " y " + host.getMinBuildHeight() + ".." + (topOf(host) - 1))
                .append("\nband=").append((long) XenoSpaceConfig.handoffBandThickness())
                .append(" reach=").append((long) XenoSpaceConfig.handoffReach())
                .append(" every=").append(XenoSpaceConfig.handoffIntervalTicks()).append("t")
                .append(" velocity=").append(XenoSpaceConfig.velocityCarryover() ? "carry" : "zero")
                .append(" flightGrant=").append(XenoSpaceConfig.flightGrant());
        double hostTop = host == null ? Double.NaN : topOf(host);
        int ready = 0;
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (body.dimensionId() == null) continue;
            String why = whyNot(server, chart, slot, body, hostTop, true);
            if (why == null) ready++;
            out.append("\n").append(body.id()).append(" exit=").append(chart.exitAltitude(slot))
                    .append(" reentry=").append((long) reentryAltitude(server, body, chart.exitAltitude(slot)))
                    .append(" band=").append((long) bandThickness(server, body))
                    .append(" anchor=").append((long) surfaceAnchorY(body))
                    .append(why == null ? " -> ready" : " -> " + why);
        }
        out.append("\n").append(ready).append(" of ").append(chart.size())
                .append(" charted bodies can hand off (cooldowns active=")
                .append(COOLDOWN_UNTIL.size()).append(")");
        return out.toString();
    }

    /**
     * Why one body cannot complete a round trip, or null when it can.
     *
     * @param needsHost whether the host half of the band is part of the question — a direct
     *                  planet-to-planet transfer does not care how tall the Voidsea is
     */
    private static String whyNot(MinecraftServer server, ChartRegistry chart, int slot, CelestialBody body,
                                 double hostTop, boolean needsHost) {
        if (!body.enterable()) return "not enterable (" + body.kind() + ")";
        if (!sidecarSpaceEnabled(server, body)) return "disabled by dimension sidecar (space.enabled=false)";
        ResourceLocation dimension = bodyDimension(body);
        if (dimension == null) return "unparsable dimension id";
        if (!(server.getLevel(key(dimension)) instanceof ServerLevel level)) return "dimension not loaded";
        if (needsHost && !Double.isFinite(hostArrivalY(chart.exitAltitude(slot),
                bandThickness(server, body), hostTop)))
            return "host too short for exit+band/2";
        double anchor = surfaceAnchorY(body);
        if (!Double.isFinite(anchor) || anchor < level.getMinBuildHeight()
                || anchor >= level.getMinBuildHeight() + (double) level.getHeight())
            return "surface anchor is outside its dimension's build volume";
        if (anchor >= chart.exitAltitude(slot)) return "exit altitude must be above the surface anchor";
        if (!Double.isFinite(bodyArrivalY(anchor, chart.exitAltitude(slot),
                level.getMinBuildHeight(), level.getHeight())))
            return "no arrival below the exit altitude in this dimension";
        return null;
    }

    /** Per-dimension sidecar band wins; the global value remains the fallback for unconfigured bodies. */
    private static double bandThickness(MinecraftServer server, CelestialBody body) {
        if (body.dimensionId() == null) return XenoSpaceConfig.handoffBandThickness();
        var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
        return settings == null ? XenoSpaceConfig.handoffBandThickness()
                : settings.bandThicknessOr(XenoSpaceConfig.handoffBandThickness());
    }

    /** The dimension sidecar can explicitly opt a bound world out of space handoff behavior. */
    private static boolean sidecarSpaceEnabled(MinecraftServer server, CelestialBody body) {
        if (body.dimensionId() == null) return true;
        var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
        return settings == null || settings.enabledOrDefault(true);
    }

    /** A false dimension-sidecar flag blocks pod entry only; outbound ascent remains available. */
    private static boolean shipsAllowedInAtmosphere(MinecraftServer server, CelestialBody body) {
        if (body.dimensionId() == null) return true;
        var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
        return settings == null || settings.allowShipsInAtmosphereOrDefault();
    }

    private static double reentryAltitude(MinecraftServer server, CelestialBody body, double exitAltitude) {
        if (body.dimensionId() == null) return exitAltitude;
        var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
        return settings == null ? exitAltitude : settings.reentryAltitudeOr(exitAltitude);
    }

    private static double surfaceAnchorY(CelestialBody body) {
        return body.surfaceAnchor() == null ? body.handoffAnchorY() : body.surfaceAnchor().y();
    }

    private static HandoffTransitionNoticeV3 transitionNotice(MinecraftServer server, CelestialBody body,
                                                               boolean inboundHandoff) {
        int fallback = (int) HandoffOverlayTiming.FADE_MILLIS;
        if (body.dimensionId() == null) return new HandoffTransitionNoticeV3(fallback, false);
        var settings = SpaceChartService.state(server).sidecarSettings.get(body.dimensionId());
        return new HandoffTransitionNoticeV3(settings == null ? fallback : settings.handoffOverlayMillisOr(fallback),
                inboundHandoff && settings != null && settings.reentryPlasmaShellEnabled());
    }

    /**
     * Planet to planet on demand: transfers a player straight into a charted body's own dimension at its
     * arrival altitude, which is the same primitive the altitude watcher uses and therefore the same
     * screen treatment and the same arrival convention.
     *
     * <p>Direct rather than via the Voidsea, because two bodies' chart positions are thousands of blocks
     * apart and an arrival over the destination is the part a player is asking for. Once inside, that
     * body's own exit altitude still applies, so climbing out of Namek hands off to orbit as usual.
     *
     * @return a one-line result for the command source
     */
    public static String travel(MinecraftServer server, ServerPlayer player, String bodyId) {
        if (!XenoSpaceConfig.enabled()) {
            return "space is disabled (space.enabled=off), so there is no chart to travel through";
        }
        ChartRegistry chart = SpaceChartService.state(server).runtime.chart;
        int slot = chart.slotOf(bodyId);
        if (slot < 0) return "no charted body named " + bodyId + " (see /xenospace list bodies)";
        CelestialBody body = chart.body(slot);
        if (!sidecarSpaceEnabled(server, body)) return bodyId + " is disabled by its dimension sidecar (space.enabled=false)";
        String why = whyNot(server, chart, slot, body, Double.NaN, false);
        if (why != null) return bodyId + " is not reachable here: " + why;
        HandoffDestination destination = destination(player, chart, slot, false);
        if (destination == null) return "destination is outside the supported arrival range";
        ServerLevel target = destination.level();
        if (target.dimension().equals(player.level().dimension())) {
            return body.id() + " is the dimension you are already standing in";
        }
        Vec3 arrival = destination.position();
        if (!anchorChunkReady(player.getUUID(), destination, false)) return "destination anchor is warming; retry shortly";
        if (!safeArrival(player, target, arrival)) return "destination obstructed or outside the world border";
        Vec3 departure = player.position();
        var sourceBlock = player.blockPosition();
        int sourceSlot = ChartSurfaceBodyResolver.slotAt(chart, player.level().dimension().location().toString(),
                sourceBlock.getX(), sourceBlock.getZ());
        Vec3 motion = transitionVelocity(server, chart, sourceSlot, slot, player.getDeltaMovement());
        String leavingBody = sourceSlot < 0 ? null : chart.body(sourceSlot).id();
        COOLDOWN_UNTIL.put(player.getUUID(), target.getGameTime() + TRANSFER_COOLDOWN_TICKS);
        // Arrival uses remembered planet-local X/Z and the effective exit altitude minus 25.
        AddonNetwork.sendToPlayer(player, transitionNotice(server, body, false));
        Entity moved = player.changeDimension(new DimensionTransition(target,
                arrival,
                motion, destination.yawOr(player.getYRot()), player.getXRot(), DimensionTransition.DO_NOTHING));
        releaseAnchorTicket(player.getUUID());
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != target) return bodyId + " did not accept the transfer";
        notifyEntryAssistIfNeeded(arrived, body, motion);
        land(moved, body, motion, XenoSpaceConfig.velocityCarryover());
        PlanetTravelMemory.commit((ServerPlayer) moved, leavingBody, departure, body.id());
        return "travelled to " + body.id() + " at Y " + Math.round(arrival.y);
    }

    private static boolean safeArrival(ServerPlayer player, ServerLevel target, Vec3 arrival) {
        if (arrival == null) return false;
        boolean safe = target.getWorldBorder().isWithinBounds(net.minecraft.core.BlockPos.containing(arrival))
                && target.noCollision(player, player.getBoundingBox().move(arrival.subtract(player.position())));
        if (!safe) player.displayClientMessage(Component.literal("Planet travel refused: destination is obstructed or outside the world border"), true);
        return safe;
    }
}
