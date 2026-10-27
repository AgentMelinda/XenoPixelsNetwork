package net.bullettrain.xenopixelsmod.space.event;

import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceOrbitClock;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyScan;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanets;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodRiderBinding;
import net.bullettrain.xenopixelsmod.space.pod.transfer.PodTransferTransaction;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetPhysicalizer;
import net.bullettrain.xenopixelsmod.vs.ShipBallisticController;
import net.bullettrain.xenopixelsmod.vs.XenoThrusterControl;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.UUID;

/** Bounded analytic supernova front lookup shared by oxygen and ship-thruster effects (§23.1). */
@EventBusSubscriber(modid = net.bullettrain.xenopixelsmod.XenoPixelsMod.MOD_ID)
public final class StellarBlastWaveService {
    private static final int HULL_SCAN_SUBLEVELS_PER_TICK = 128;
    /** At most one scan window may wait for the slower per-tick damage budget. */
    private static final int HULL_DAMAGE_PENDING_CAP = HULL_SCAN_SUBLEVELS_PER_TICK;
    private static final int HULL_DAMAGE_TARGETS_PER_TICK = 4;
    /** A stale-only queue must still have a strict traversal ceiling if this policy changes later. */
    private static final int HULL_DAMAGE_QUEUE_ENTRIES_PER_TICK = HULL_DAMAGE_PENDING_CAP;
    private static final int HULL_DAMAGE_PROBES_PER_TARGET = 32;
    private static final int HULL_DAMAGE_MAX_DEPTH = 6;
    /** All access is server-thread confined; public entry points that can be called elsewhere check isSameThread. */
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new WeakHashMap<>();
    private static volatile String lastSendFailure;
    private static volatile String lastCapacityWarning;
    record ActiveWave(String bodyId, StellarBlastWaveMath.Wave wave,
                      double starRadius, List<StellarShadowMath.Occluder> occluders) {}
    private record HullDamageKey(UUID bodyId, String waveId) {}
    private record PendingHullDamage(ServerSubLevel body, ActiveWave wave, long dueTick) {}

    private static final class Runtime {
        long sampledAt = Long.MIN_VALUE;
        int chartRevision = Integer.MIN_VALUE;
        double speed = Double.NaN;
        int durationTicks = Integer.MIN_VALUE;
        int exposureTicks = Integer.MIN_VALUE;
        List<ActiveWave> waves = List.of();
        String lastFailure;
        final Set<String> shadowFailuresLogged = new HashSet<>();
        final Map<java.util.UUID, Map<String, Long>> exposedUntil = new HashMap<>();
        final Map<HullDamageKey, Long> hullDamageNextAt = new HashMap<>();
        final LinkedHashMap<UUID, PendingHullDamage> pendingHullDamage = new LinkedHashMap<>();
        int hullScanCursor;
    }

    private StellarBlastWaveService() {}

    /** Snapshot of the bounded active wave set; clients extrapolate radius locally from age and speed. */
    public static StellarBlastWaveSnapshot snapshot(MinecraftServer server) {
        int duration = XenoSpaceConfig.stellarWaveDurationTicks();
        double speed = XenoSpaceConfig.stellarWaveSpeedBlocksPerTick();
        boolean enabled = XenoSpaceConfig.enabled() && XenoSpaceConfig.supernovaEnabled();
        if (!enabled || server == null || server.overworld() == null)
            return StellarBlastWaveSnapshot.empty(false, duration, speed);
        long now = server.overworld().getGameTime();
        var chart = SpaceChartService.state(server).runtime.chart;
        // Packet construction is a read-only login/fanout path. Legacy origin migration belongs to the
        // server cadence (oxygenFactor), never to a packet build.
        var data = StellarLifecycleSavedData.get(server);
        List<StellarBlastWaveSnapshotPolicy.ChartStar> stars = new ArrayList<>();
        for (int slot = 0; slot < chart.size(); slot++) {
            var body = chart.body(slot);
            if (body.kind() == CelestialBody.BodyKind.STAR)
                stars.add(new StellarBlastWaveSnapshotPolicy.ChartStar(body.id(), body.displayName()));
        }
        var result = StellarBlastWaveSnapshotPolicy.build(data.entries(), stars, now, duration, speed, true);
        reportWaveCap(result.omitted());
        return result.snapshot();
    }

    /** Sends current wave state to a player; non-Voidsea clients receive an empty snapshot to clear stale state. */
    public static void send(ServerPlayer player) {
        if (player == null || player.getServer() == null) return;
        boolean inHost = PhysicalEarth.HOST.equals(player.level().dimension().location());
        StellarBlastWaveSnapshot packet;
        try {
            packet = inHost ? snapshot(player.getServer())
                : StellarBlastWaveSnapshot.empty(false, XenoSpaceConfig.stellarWaveDurationTicks(),
                        XenoSpaceConfig.stellarWaveSpeedBlocksPerTick());
        } catch (RuntimeException failure) {
            logSnapshotFailure(failure);
            return;
        }
        sendSafely(player, packet);
    }

    /** Refreshes wave state after lifecycle/config changes; no per-tick wave packets are emitted. */
    public static void syncAll(MinecraftServer server) {
        if (server == null) return;
        StellarBlastWaveSnapshot packet;
        try {
            packet = snapshot(server);
        } catch (RuntimeException failure) {
            logSnapshotFailure(failure);
            return;
        }
        boolean all = true;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PhysicalEarth.HOST.equals(player.level().dimension().location())) all &= sendSafely(player, packet);
            else all &= sendSafely(player, StellarBlastWaveSnapshot.empty(false, packet.durationTicks(),
                    packet.speedBlocksPerTick()));
        }
        if (all) lastSendFailure = null;
    }

    /** Periodically re-anchors client wave ages while at least one persisted, active wave exists. */
    public static void syncActiveAll(MinecraftServer server) {
        if (server == null) return;
        StellarBlastWaveSnapshot packet;
        try {
            packet = snapshot(server);
        } catch (RuntimeException failure) {
            logSnapshotFailure(failure);
            return;
        }
        if (packet.waves().isEmpty()) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!PhysicalEarth.HOST.equals(player.level().dimension().location())) continue;
            sendSafely(player, packet);
        }
    }

    private static void logSnapshotFailure(RuntimeException failure) {
        String message = failure.toString();
        if (!message.equals(lastSendFailure)) {
            lastSendFailure = message;
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn("Stellar wave snapshot not sent: {}", message);
        }
    }

    private static void reportWaveCap(int omitted) {
        if (omitted <= 0) {
            lastCapacityWarning = null;
            return;
        }
        if (lastCapacityWarning == null) {
            String message = "Active stellar waves exceed the shared limit of "
                    + StellarBlastWaveSnapshot.MAX_WAVES + "; some higher-ID wave(s) are omitted from effects and visuals";
            lastCapacityWarning = message;
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(message);
        }
    }

    private static boolean sendSafely(ServerPlayer player, StellarBlastWaveSnapshot packet) {
        try {
            AddonNetwork.sendToPlayer(player, packet);
            return true;
        } catch (RuntimeException failure) {
            String message = failure.toString();
            if (!message.equals(lastSendFailure)) {
                lastSendFailure = message;
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                        "Could not send stellar wave snapshot to {}: {}", player.getScoreboardName(), message);
            }
            return false;
        }
    }

    /** Returns 2 while the configured supernova shell is crossing this exposed Voidsea player, otherwise 1. */
    public static int oxygenFactor(ServerPlayer player) {
        if (player == null) return 1;
        MinecraftServer server = player.getServer();
        if (server == null || server.overworld() == null || !server.isSameThread()) return 1;
        long now = server.overworld().getGameTime();
        if (!XenoSpaceConfig.enabled() || !XenoSpaceConfig.supernovaEnabled()) {
            Runtime runtime = RUNTIMES.get(server);
            if (runtime != null) {
                runtime.exposedUntil.clear();
                runtime.sampledAt = Long.MIN_VALUE;
            }
            return 1;
        }
        Runtime runtime = RUNTIMES.get(server);
        Map<String, Long> playerExposures = runtime == null ? null : pruneExposures(runtime, player.getUUID(), now);
        if (!PhysicalEarth.HOST.equals(player.level().dimension().location())) {
            // Exposure is an event deadline, not a host-dimension attachment. Keep it through
            // dimension transfers and let it expire on the server clock (logout still clears it).
            return 1;
        }
        runtime = sampleRuntime(server, now);
        playerExposures = pruneExposures(runtime, player.getUUID(), now);
        boolean exposed = false;
        for (var active : runtime.waves) {
            boolean crossing = StellarBlastWaveMath.exposes(active.wave(),
                    player.getX(), player.getY(), player.getZ(), now);
            if (StellarShadowMath.blocksWave(crossing, active.wave().originX(), active.wave().originY(),
                    active.wave().originZ(), active.starRadius(), player.getX(), player.getY(), player.getZ(),
                    active.occluders())) {
                if (playerExposures != null) playerExposures.remove(active.bodyId());
                continue;
            }
            if (!crossing) continue;
            if (playerExposures == null) {
                playerExposures = new HashMap<>();
                runtime.exposedUntil.put(player.getUUID(), playerExposures);
            }
            long prior = playerExposures.getOrDefault(active.bodyId(), Long.MIN_VALUE);
            playerExposures.put(active.bodyId(), StellarBlastWaveMath.exposureDeadline(prior, now,
                    active.wave().exposureTicks()));
            exposed = true;
        }
        if (playerExposures != null && playerExposures.isEmpty()) {
            runtime.exposedUntil.remove(player.getUUID());
            playerExposures = null;
        }
        exposed |= playerExposures != null;
        return exposed ? 2 : 1;
    }

    private static Map<String, Long> pruneExposures(Runtime runtime, UUID playerId, long now) {
        Map<String, Long> exposures = runtime.exposedUntil.get(playerId);
        if (exposures == null) return null;
        exposures.values().removeIf(deadline -> deadline <= now);
        if (exposures.isEmpty()) {
            runtime.exposedUntil.remove(playerId);
            return null;
        }
        return exposures;
    }

    /**
     * Returns true only while the shell crosses a chart-host ship's center. Unlike player oxygen exposure, this
     * instantaneous physics gate does not retain a post-crossing exposure window; the force effect ends as the
     * trailing edge clears. The wave list is shared with oxygen processing and sampled at most once per server tick.
     */
    public static boolean suppressShipThrust(ServerLevel level,
            dev.ryanhcode.sable.sublevel.ServerSubLevel ship) {
        if (level == null || ship == null || !PhysicalEarth.HOST.equals(level.dimension().location())) return false;
        MinecraftServer server = level.getServer();
        if (server == null || server.overworld() == null || !server.isSameThread()
                || !XenoSpaceConfig.enabled() || !XenoSpaceConfig.supernovaEnabled()) return false;
        long now = server.overworld().getGameTime();
        Runtime runtime = sampleRuntime(server, now);
        var center = ship.logicalPose().position();
        for (var active : runtime.waves) {
            boolean crossing = StellarBlastWaveMath.exposes(active.wave(), center.x(), center.y(), center.z(), now);
            if (crossing && !StellarShadowMath.blocksWave(crossing,
                    active.wave().originX(), active.wave().originY(), active.wave().originZ(), active.starRadius(),
                    center.x(), center.y(), center.z(), active.occluders())) return true;
        }
        return false;
    }

    /**
     * Bounded server-thread work for §23.1 hull damage. It scans at most 128 loaded sub-levels and mutates at most
     * four identified ship bodies per tick; each mutation probes at most 32 positions, six blocks deep.
     */
    @SubscribeEvent public static void hullDamageTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server == null || server.overworld() == null || !server.isSameThread()
                || !XenoSpaceConfig.enabled() || !XenoSpaceConfig.supernovaEnabled()
                || !XenoSpaceConfig.stellarHullDamageEnabled()) return;
        ServerLevel host = null;
        for (ServerLevel level : server.getAllLevels()) {
            if (PhysicalEarth.HOST.equals(level.dimension().location())) { host = level; break; }
        }
        if (host == null) return;
        long now = server.overworld().getGameTime();
        Runtime runtime = sampleRuntime(server, now);
        if (runtime.waves.isEmpty()) {
            runtime.pendingHullDamage.clear();
            runtime.hullDamageNextAt.clear();
            runtime.hullScanCursor = 0;
            return;
        }
        runtime.hullDamageNextAt.values().removeIf(due -> due <= now);
        ServerSubLevelContainer container = SubLevelContainer.getContainer(host);
        if (container == null) return;
        List<? extends dev.ryanhcode.sable.sublevel.SubLevel> bodies = container.getAllSubLevels();
        scanHullCandidates(runtime, host, bodies, now);
        applyQueuedHullDamage(runtime, now);
    }

    private static void scanHullCandidates(Runtime runtime, ServerLevel host,
            List<? extends dev.ryanhcode.sable.sublevel.SubLevel> bodies, long now) {
        int size = bodies.size();
        if (size == 0) { runtime.hullScanCursor = 0; return; }
        int start = Math.floorMod(runtime.hullScanCursor, size);
        int examined = Math.min(size, HULL_SCAN_SUBLEVELS_PER_TICK);
        for (int i = 0; i < examined; i++) {
            var candidate = bodies.get((start + i) % size);
            if (!(candidate instanceof ServerSubLevel body) || body.isRemoved()
                    || !isIdentifiedShip(host, body)) continue;
            ActiveWave wave = exposedWave(runtime.waves, body, now);
            if (wave == null) continue;
            HullDamageKey key = new HullDamageKey(body.getUniqueId(), wave.bodyId());
            if (runtime.hullDamageNextAt.containsKey(key)) continue;
            UUID id = body.getUniqueId();
            if (StellarHullDamagePolicy.mayQueue(runtime.pendingHullDamage.size(),
                    runtime.pendingHullDamage.containsKey(id), HULL_DAMAGE_PENDING_CAP))
                runtime.pendingHullDamage.putIfAbsent(id, new PendingHullDamage(body, wave, now));
        }
        runtime.hullScanCursor = (start + examined) % size;
    }

    private static boolean isIdentifiedShip(ServerLevel host, ServerSubLevel body) {
        if (body.getLevel() != host || managedCelestialBody(body)) return false;
        return PodTransferTransaction.podId(body) != null || XenoThrusterControl.get(body) != null
                || ShipBallisticController.get(body) != null || !body.getTrackingPlayers().isEmpty();
    }

    private static boolean managedCelestialBody(ServerSubLevel body) {
        if (PhysicalEarth.isManaged(body) || AsteroidPhysicalizer.isAsteroidFamilyBody(body)
                || SmallPlanetPhysicalizer.taggedBodyId(body) != null) return true;
        var data = body.getUserDataTag();
        return data != null && (data.contains(SmallPlanetPhysicalizer.TAG)
                || data.contains(DragonBallBodyScan.TAG) || data.contains(DragonBallPlanets.TAG));
    }

    private static ActiveWave exposedWave(List<ActiveWave> waves, ServerSubLevel body, long now) {
        var center = body.logicalPose().position();
        for (ActiveWave active : waves) {
            boolean crossing = StellarBlastWaveMath.exposes(active.wave(), center.x(), center.y(), center.z(), now);
            if (crossing && !StellarShadowMath.blocksWave(crossing,
                    active.wave().originX(), active.wave().originY(), active.wave().originZ(), active.starRadius(),
                    center.x(), center.y(), center.z(), active.occluders())) return active;
        }
        return null;
    }

    private static void applyQueuedHullDamage(Runtime runtime, long now) {
        Iterator<Map.Entry<UUID, PendingHullDamage>> iterator = runtime.pendingHullDamage.entrySet().iterator();
        int processed = 0;
        int examined = 0;
        while (iterator.hasNext() && processed < HULL_DAMAGE_TARGETS_PER_TICK
                && examined < HULL_DAMAGE_QUEUE_ENTRIES_PER_TICK) {
            var entry = iterator.next();
            examined++;
            PendingHullDamage pending = entry.getValue();
            ServerSubLevel body = pending.body();
            boolean bodyEligible = !body.isRemoved() && body.getLevel() instanceof ServerLevel level
                    && PhysicalEarth.HOST.equals(level.dimension().location()) && !managedCelestialBody(body);
            if (!bodyEligible) {
                iterator.remove();
                continue;
            }
            ActiveWave active = runtime.waves.stream().filter(w -> w.bodyId().equals(pending.wave().bodyId()))
                    .findFirst().orElse(null);
            boolean cooldownReady = !runtime.hullDamageNextAt.containsKey(
                    new HullDamageKey(body.getUniqueId(), pending.wave().bodyId()));
            boolean current = StellarHullDamagePolicy.pendingCandidateCurrent(bodyEligible, active != null,
                    active != null && exposedWave(List.of(active), body, now) != null, cooldownReady);
            if (!current) {
                iterator.remove();
                continue;
            }
            iterator.remove();
            processed++;
            HullDamageKey key = new HullDamageKey(body.getUniqueId(), active.bodyId());
            BlockPos damaged = damageOneHullVoxel(body, active, now);
            if (damaged != null)
                runtime.hullDamageNextAt.put(key, StellarHullDamagePolicy.nextDamageTick(
                        now, XenoSpaceConfig.stellarHullDamageIntervalTicks()));
        }
    }

    /** Removes one non-block-entity, breakable hull voxel facing the active wave source, or returns null. */
    static BlockPos damageOneHullVoxel(ServerSubLevel body, ActiveWave active, long now) {
        if (body == null || active == null || body.isRemoved()
                || !(body.getLevel() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || managedCelestialBody(body)) return null;
        var plot = body.getPlot();
        var box = plot.getBoundingBox();
        if (box == null) return null;
        BlockPos centerBlock = plot.getCenterBlock();
        var localBounds = new StellarHullDamagePolicy.Bounds(
                box.minX() - centerBlock.getX(), box.minY() - centerBlock.getY(), box.minZ() - centerBlock.getZ(),
                box.maxX() - centerBlock.getX(), box.maxY() - centerBlock.getY(), box.maxZ() - centerBlock.getZ());
        var worldCenter = body.logicalPose().position();
        org.joml.Vector3d localDirection = new org.joml.Vector3d(
                active.wave().originX() - worldCenter.x(), active.wave().originY() - worldCenter.y(),
                active.wave().originZ() - worldCenter.z());
        body.logicalPose().orientation().transformInverse(localDirection);
        var face = StellarHullDamagePolicy.faceTowardSource(localDirection.x, localDirection.y, localDirection.z);
        if (face == null) return null;
        long seed = body.getUniqueId().getMostSignificantBits() ^ body.getUniqueId().getLeastSignificantBits()
                ^ ((long) active.bodyId().hashCode() << 32) ^ (now / XenoSpaceConfig.stellarHullDamageIntervalTicks());
        var accessor = plot.getEmbeddedLevelAccessor();
        for (var candidate : StellarHullDamagePolicy.probes(localBounds, face, seed,
                HULL_DAMAGE_PROBES_PER_TARGET, HULL_DAMAGE_MAX_DEPTH)) {
            BlockPos pos = new BlockPos(candidate.x(), candidate.y(), candidate.z());
            var state = accessor.getBlockState(pos);
            if (state.isAir() || state.hasBlockEntity() || state.getDestroySpeed(accessor, pos) < 0.0f) continue;
            // Preserve the direct neighbor-shape pass so attached blocks react to lost support, but cap
            // recursive propagation. A full vanilla recursion budget can fan out from a hull edit into
            // host-level neighbor updates beyond this plot; Sable's block-change callback still sees the write.
            if (StellarHullMutationContext.run(() -> accessor.setBlock(pos, Blocks.AIR.defaultBlockState(), 3, 1))
                    && accessor.getBlockState(pos).isAir()) {
                releaseRidersIfBodyDestroyed(level, body);
                return pos;
            }
        }
        return null;
    }

    /**
     * Sable removes a body when its plot becomes empty or its mass becomes invalid. Its remove packet clears the
     * client tracking view, but players need their server-side Sable binding cleared too; preserve their current
     * host-world position and motion so they are left in space where the hull disappeared.
     */
    private static void releaseRidersIfBodyDestroyed(ServerLevel level, ServerSubLevel body) {
        var mass = body.getMassTracker();
        if (!body.isRemoved() && (mass == null || !mass.isInvalid())) return;
        // Use the level player list rather than Sable's network-tracking set: a rider can be rebound during the
        // same tick before the tracking system's next population pass. This scan runs only on terminal hull loss.
        for (ServerPlayer player : level.players()) {
            if (PodRiderBinding.trackedBody(player) == body)
                PodRiderBinding.clear(player);
        }
    }

    private static Runtime sampleRuntime(MinecraftServer server, long now) {
        Runtime runtime = RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
        var chartState = SpaceChartService.state(server).runtime;
        var chart = chartState.chart;
        int revision = chartState.revision;
        double speed = XenoSpaceConfig.stellarWaveSpeedBlocksPerTick();
        int durationTicks = XenoSpaceConfig.stellarWaveDurationTicks();
        int exposureTicks = XenoSpaceConfig.stellarWaveExposureTicks();
        boolean contextChanged = runtime.chartRevision != revision || Double.compare(runtime.speed, speed) != 0
                || runtime.durationTicks != durationTicks || runtime.exposureTicks != exposureTicks;
        if (runtime.sampledAt != now || contextChanged) {
            try {
                runtime.waves = activeWaves(server, chart, now, speed, exposureTicks);
                runtime.lastFailure = null;
            } catch (RuntimeException failure) {
                runtime.waves = List.of();
                String message = failure.toString();
                if (!message.equals(runtime.lastFailure)) {
                    runtime.lastFailure = message;
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                            "Supernova wave sample skipped at game tick {}: {}", now, message);
                }
            }
            runtime.sampledAt = now;
            runtime.chartRevision = revision;
            runtime.speed = speed;
            runtime.durationTicks = durationTicks;
            runtime.exposureTicks = exposureTicks;
        }
        return runtime;
    }

    private static List<ActiveWave> activeWaves(MinecraftServer server,
            net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart, long now,
            double speedBlocksPerTick, int exposureTicks) {
        if (chart.size() == 0) return List.of();
        var data = StellarLifecycleSavedData.get(server);
        long duration = XenoSpaceConfig.stellarWaveDurationTicks();
        boolean hasActiveStar = false;
        for (var saved : data.entries().entrySet()) {
            if (!StellarStage.SUPERNOVA.name().equals(saved.getValue().stage())
                    || !StellarBlastWaveMath.activeAt(saved.getValue().startedAtTick(), now, duration)) continue;
            int slot = chart.slotOf(saved.getKey());
            if (slot >= 0 && chart.body(slot).kind() == CelestialBody.BodyKind.STAR) {
                hasActiveStar = true;
                break;
            }
        }
        if (!hasActiveStar) return List.of();
        // Chart evaluation is timestamp-idempotent; update positions once before deriving any
        // planetary shadow geometry, sharing the same orbit clock as the rest of the space system.
        chart.evaluate(SpaceOrbitClock.seconds(now));
        List<String> legacyOrigins = new ArrayList<>();
        for (var saved : data.entries().entrySet()) {
            if (!StellarStage.SUPERNOVA.name().equals(saved.getValue().stage()) || saved.getValue().origin() != null)
                continue;
            long started = saved.getValue().startedAtTick();
            int slot = chart.slotOf(saved.getKey());
            if (StellarBlastWaveMath.activeAt(started, now, duration) && slot >= 0
                    && chart.body(slot).kind() == CelestialBody.BodyKind.STAR) legacyOrigins.add(saved.getKey());
        }
        // Older saved SUPERNOVA rows have no frozen position. Evaluate once, then persist a one-time migration.
        if (!legacyOrigins.isEmpty() && !data.mutationsLocked()) {
            for (String id : legacyOrigins) {
                int slot = chart.slotOf(id);
                if (slot < 0 || chart.body(slot).kind() != CelestialBody.BodyKind.STAR) continue;
                try {
                    var origin = new StellarLifecycleSavedData.Origin(chart.x(slot), chart.y(slot), chart.z(slot));
                    var entry = data.entry(id);
                    if (entry != null && entry.origin() == null)
                        data.setSupernovaOrigin(id, origin);
                } catch (IllegalArgumentException malformedPosition) {
                    // An invalid legacy chart position remains visual-only; it cannot create a poisoned wave.
                }
            }
        }
        Runtime runtime = RUNTIMES.computeIfAbsent(server, ignored -> new Runtime());
        Map<String, List<StellarShadowMath.Occluder>> shadowsByStar = planetaryShadowsByStar(chart, runtime, now);
        List<ActiveWave> candidates = new ArrayList<>();
        for (var saved : data.entries().entrySet()) {
            if (!StellarStage.SUPERNOVA.name().equals(saved.getValue().stage())) continue;
            long started = saved.getValue().startedAtTick();
            if (!StellarBlastWaveMath.activeAt(started, now, duration)) continue;
            int slot = chart.slotOf(saved.getKey());
            if (slot < 0 || chart.body(slot).kind() != net.bullettrain.xenopixelsmod.space.chart.CelestialBody.BodyKind.STAR)
                continue;
            var origin = saved.getValue().origin();
            if (origin == null) continue;
            try {
                List<StellarShadowMath.Occluder> occluders = shadowsByStar.getOrDefault(saved.getKey(), List.of());
                candidates.add(new ActiveWave(saved.getKey(),
                        new StellarBlastWaveMath.Wave(origin.x(), origin.y(), origin.z(), started,
                                speedBlocksPerTick, exposureTicks), chart.body(slot).radiusBlocks(), occluders));
            } catch (IllegalArgumentException malformed) {
                // Malformed authored coordinates cannot disable oxygen processing or poison this player's pass.
            }
        }
        var selected = StellarBlastWaveSelection.select(candidates, StellarBlastWaveSnapshot.MAX_WAVES,
                java.util.Comparator.comparing(ActiveWave::bodyId));
        reportWaveCap(selected.omitted());
        return selected.values();
    }

    /** Builds star-keyed planet/moon occluders from the current chart pose, including nested moons. */
    static Map<String, List<StellarShadowMath.Occluder>> planetaryShadowsByStar(
            net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart) {
        return planetaryShadowsByStar(chart, null, -1L);
    }

    private static Map<String, List<StellarShadowMath.Occluder>> planetaryShadowsByStar(
            net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart, Runtime runtime, long gameTick) {
        Map<String, List<StellarShadowMath.Occluder>> mutable = new HashMap<>();
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody body = chart.body(slot);
            if (body.kind() != CelestialBody.BodyKind.PLANET && body.kind() != CelestialBody.BodyKind.MOON) continue;
            String starId = starAncestor(chart, body);
            if (starId == null) continue;
            try {
                int bodySlot = slot;
                mutable.computeIfAbsent(starId, ignored -> new ArrayList<>()).add(
                        new StellarShadowMath.Occluder(chart.x(bodySlot), chart.y(bodySlot), chart.z(bodySlot), body.radiusBlocks()));
            } catch (IllegalArgumentException malformedPosition) {
                // An invalid chart row must not disable the other valid shelter bodies or the wave.
                String message = body.id() + ": " + malformedPosition.getMessage();
                if (runtime != null && runtime.shadowFailuresLogged.add(message))
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn(
                            "Stellar shadow occluder skipped at game tick {}: {}", gameTick, message);
            }
        }
        if (mutable.isEmpty()) return Map.of();
        Map<String, List<StellarShadowMath.Occluder>> immutable = new HashMap<>(mutable.size());
        mutable.forEach((starId, occluders) -> immutable.put(starId, List.copyOf(occluders)));
        return Map.copyOf(immutable);
    }

    private static String starAncestor(net.bullettrain.xenopixelsmod.space.chart.ChartRegistry chart,
            CelestialBody body) {
        String parentId = body.parentId();
        for (int depth = 0; parentId != null && depth < chart.size(); depth++) {
            int parentSlot = chart.slotOf(parentId);
            if (parentSlot < 0) return null;
            CelestialBody parent = chart.body(parentSlot);
            if (parent.kind() == CelestialBody.BodyKind.STAR) return parent.id();
            parentId = parent.parentId();
        }
        return null;
    }

    @SubscribeEvent public static void loggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        MinecraftServer server = event.getEntity().getServer();
        if (server == null) return;
        Runtime runtime = RUNTIMES.get(server);
        if (runtime != null) runtime.exposedUntil.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        RUNTIMES.remove(event.getServer());
        lastSendFailure = null;
        lastCapacityWarning = null;
    }
}
