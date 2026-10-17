package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.entity.Entity;
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
    private record HandoffDestination(ServerLevel level, Vec3 position) {}
    private record AnchorLease(ServerLevel level, ChunkPos chunk, long expiresAt, boolean prewarmed) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
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
        COOLDOWN_UNTIL.clear();
        PENDING.clear();
        reportedMissingHost = null;
    }

    /** Release temporary readiness tickets before the server closes its chunk sources. */
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        releaseAllAnchorTickets();
        PENDING.clear();
        COOLDOWN_UNTIL.clear();
    }

    /** A departed player's cooldown entry would otherwise live until the next server stop. */
    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
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
                prewarmInboundAnchor(player, chart, level, hostId);
            } else {
                prewarmOutboundAnchor(player, chart, level, hostId);
            }
            if (onCooldown(player.getUUID(), clock)) continue;
            if (level.dimension().location().equals(hostId)) {
                int body = entryBody(chart, player, hostId, level);
                if (body >= 0) stage(player, body, clock, false);
                continue;
            }
            int slot = chart.slotFor(level.dimension().location());
            if (slot < 0) continue;
            CelestialBody candidate = chart.body(slot);
            if (!candidate.enterable()) continue;
            if (!leaving(player.getY(), chart.exitAltitude(slot))) continue;
            if (!Double.isFinite(hostArrivalY(chart.exitAltitude(slot),
                    XenoSpaceConfig.handoffBandThickness(), hostTop))) continue;
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
        expireAnchorTickets();
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
                        net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isErased(pending.bodyId()))
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
            if (fresh < 0 || !chart.body(fresh).enterable()) {
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
        boolean erased = net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isErased(bodyId);
        if (!requiresLiveEarthOrbitAnchor(bodyId, outbound, erased)) return true;
        ServerLevel host = server.getLevel(key(XenoSpaceConfig.handoffHost()));
        return host != null && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, bodyId) != null;
    }

    static boolean requiresLiveEarthOrbitAnchor(String bodyId, boolean outbound, boolean earthErased) {
        return outbound && !earthErased
                && net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.BODY_ID.equals(bodyId);
    }

    /** Convert between the moving source/target chart frames at the current server orbit time. */
    private static Vec3 transitionVelocity(MinecraftServer server, ChartRegistry chart, int sourceSlot,
                                           int targetSlot, Vec3 sourceLocalMotion) {
        if (!XenoSpaceConfig.velocityCarryover()) return Vec3.ZERO;
        long gameTime = server.overworld().getGameTime();
        chart.evaluate(gameTime / 20.0 + XenoSpaceConfig.orbitEpochOffsetSeconds());
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
        if (outbound) {
            // Directly over the body's live chart position — which is where the client is drawing its
            // proxy, and the same number the LOD ladder measures distance from. The planet you launched
            // from is below you on arrival, and its surface is at the altitude the design's §2.1 band
            // says it should visually be.
            Vec3 motion = transitionVelocity(player.server, chart, slot, -1, player.getDeltaMovement());
            Vec3 departure = player.position();
            AddonNetwork.sendToPlayer(player, new HandoffTransitionNotice());
            Entity moved = player.changeDimension(new DimensionTransition(destination.level(),
                    destination.position(), motion,
                    player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
            if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != destination.level()) return;
            land(arrived, body, motion, XenoSpaceConfig.velocityCarryover());
            PlanetTravelMemory.commit(arrived, body.id(), departure, null);
            return;
        }
        // Convert the Voidsea inertial velocity back into the destination planet's moving frame. Arrival
        // altitude is exit-25, so re-entry effects can later own the descent without a dimension-change stop.
        Vec3 motion = transitionVelocity(player.server, chart, -1, slot, player.getDeltaMovement());
        AddonNetwork.sendToPlayer(player, new HandoffTransitionNotice());
        Entity moved = player.changeDimension(new DimensionTransition(destination.level(), destination.position(), motion,
                player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != destination.level()) return;
        land(arrived, body, motion, XenoSpaceConfig.velocityCarryover());
        PlanetTravelMemory.commit(arrived, null, null, body.id());
    }

    private static HandoffDestination destination(ServerPlayer player, ChartRegistry chart, int slot, boolean outbound) {
        CelestialBody body = chart.body(slot);
        if (outbound) {
            ServerLevel host = player.server.getLevel(key(XenoSpaceConfig.handoffHost()));
            if (host == null) return null;
            double x = chart.x(slot), z = chart.z(slot);
            var center = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id());
            double centerY = center == null ? chart.y(slot) : center.y();
            double radius = center == null ? body.radiusBlocks()
                    : net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveRadius(host, body.id());
            if (center != null) { x = center.x(); z = center.z(); }
            double y = outboundHostArrivalY(XenoSpaceConfig.surfaceEntry(), centerY, radius,
                    XenoSpaceConfig.handoffSurfaceMargin(), chart.exitAltitude(slot),
                    XenoSpaceConfig.handoffBandThickness(), topOf(host));
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
        Vec3 surface = PlanetTravelMemory.arrival(PlanetTravelMemory.persisted(player), body.id(),
                chart.exitAltitude(slot), target.getMinBuildHeight(), body.spatialAnchorX(), body.spatialAnchorZ());
        return surface == null ? null : new HandoffDestination(target, surface);
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
        int slot = chart.slotFor(source.dimension().location());
        if (slot < 0 || !chart.body(slot).enterable()
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
            if (!body.enterable()) continue;
            ResourceLocation dimension = bodyDimension(body);
            if (dimension == null || dimension.equals(hostId)
                    || !(player.server.getLevel(key(dimension)) instanceof ServerLevel)) continue;
            boolean surfaceEntry = XenoSpaceConfig.surfaceEntry();
            // Altitude mode must use the chart coordinates because nearestBodyBelow uses them too;
            // surface mode mirrors bodyTouched and therefore uses the live physical sphere when present.
            var liveCenter = surfaceEntry
                    ? net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id()) : null;
            double centerX = liveCenter == null ? chart.x(slot) : liveCenter.x();
            double centerY = liveCenter == null ? chart.y(slot) : liveCenter.y();
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
                if (!inboundPrewarmReached(player.getY(), chart.exitAltitude(slot),
                        XenoSpaceConfig.handoffBandThickness(), body.warmUpAltitude()) || distance > reach) continue;
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
        String warmUpProblem = warmUpProblem(body.warmUpAltitude(), y);
        if (warmUpProblem != null) throw new IllegalArgumentException(warmUpProblem);
        ResourceLocation dimension = bodyDimension(body);
        ServerLevel target = dimension == null ? null : server.getLevel(key(dimension));
        ServerLevel host = server.getLevel(key(XenoSpaceConfig.handoffHost()));
        if (target == null || host == null) throw new IllegalArgumentException("body or host dimension is absent from this world");
        if (target == host) throw new IllegalArgumentException("body dimension must differ from host");
        String problem = exitProblem(y, body.handoffAnchorY(), target.getMinBuildHeight(), target.getHeight(),
                XenoSpaceConfig.handoffBandThickness(), host.getMinBuildHeight(), host.getHeight());
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
            if (!body.enterable()) continue;
            ResourceLocation dimension = bodyDimension(body);
            if (dimension == null || dimension.equals(hostId)) continue;
            var center = net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth.liveCenter(host, body.id());
            double cx = center == null ? chart.x(slot) : center.x();
            double cy = center == null ? chart.y(slot) : center.y();
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
            if (!body.enterable()) continue;
            ResourceLocation dimension = bodyDimension(body);
            // A body bound to the host is a dimension inside itself: handing off there would be a loop.
            if (dimension == null || dimension.equals(hostId)) continue;
            double dx = chart.x(slot) - player.getX();
            double dz = chart.z(slot) - player.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > Math.max(body.radiusBlocks() * 2.0, reach)) continue;
            if (!returning(player.getY(), chart.exitAltitude(slot))) continue;
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
                    .append(" anchor=").append((long) body.handoffAnchorY())
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
        ResourceLocation dimension = bodyDimension(body);
        if (dimension == null) return "unparsable dimension id";
        if (!(server.getLevel(key(dimension)) instanceof ServerLevel level)) return "dimension not loaded";
        if (needsHost && !Double.isFinite(hostArrivalY(chart.exitAltitude(slot),
                XenoSpaceConfig.handoffBandThickness(), hostTop)))
            return "host too short for exit+band/2";
        if (!Double.isFinite(bodyArrivalY(body.handoffAnchorY(), chart.exitAltitude(slot),
                level.getMinBuildHeight(), level.getHeight())))
            return "no arrival below the exit altitude in this dimension";
        return null;
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
        String why = whyNot(server, chart, slot, body, Double.NaN, false);
        if (why != null) return bodyId + " is not reachable here: " + why;
        ServerLevel target = server.getLevel(key(bodyDimension(body)));
        if (target.dimension().equals(player.level().dimension())) {
            return body.id() + " is the dimension you are already standing in";
        }
        Vec3 arrival = PlanetTravelMemory.arrival(PlanetTravelMemory.persisted(player), body.id(),
                chart.exitAltitude(slot), target.getMinBuildHeight());
        HandoffDestination destination = arrival == null ? null : new HandoffDestination(target, arrival);
        if (destination == null) return "destination is outside the supported arrival range";
        if (!anchorChunkReady(player.getUUID(), destination, false)) return "destination anchor is warming; retry shortly";
        if (!safeArrival(player, target, arrival)) return "destination obstructed or outside the world border";
        Vec3 departure = player.position();
        int sourceSlot = chart.slotFor(player.level().dimension().location());
        Vec3 motion = transitionVelocity(server, chart, sourceSlot, slot, player.getDeltaMovement());
        String leavingBody = sourceSlot < 0 ? null : chart.body(sourceSlot).id();
        COOLDOWN_UNTIL.put(player.getUUID(), target.getGameTime() + TRANSFER_COOLDOWN_TICKS);
        // Arrival uses remembered planet-local X/Z and the effective exit altitude minus 25.
        AddonNetwork.sendToPlayer(player, new HandoffTransitionNotice());
        Entity moved = player.changeDimension(new DimensionTransition(target,
                arrival,
                motion, player.getYRot(), player.getXRot(), DimensionTransition.DO_NOTHING));
        releaseAnchorTicket(player.getUUID());
        if (!(moved instanceof ServerPlayer arrived) || arrived.serverLevel() != target) return bodyId + " did not accept the transfer";
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
