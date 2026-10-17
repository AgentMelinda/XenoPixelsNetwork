package net.bullettrain.xenopixelsmod.space.smallplanet;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceVisualSync;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSync;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Shared, capped Sable incarnations for deterministic small planets (S11 C2/C3). */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SmallPlanetPhysicalizer {
    public static final String TAG = "xenopixelsmod_smallplanet_v1";
    private static final int PASS_TICKS = 20;
    private static final SubLevelLoadingTicketType<String> TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "small_planet"), Codec.STRING);
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();
    private static final ThreadLocal<Vector3d> GRAVITY_CENTER = ThreadLocal.withInitial(Vector3d::new);

    private static final class Active {
        final SmallPlanetField.Candidate candidate;
        final UUID body;
        final double originalMass;
        boolean ledgerPinned;
        Active(SmallPlanetField.Candidate candidate, UUID body, double originalMass) {
            this.candidate = candidate; this.body = body; this.originalMass = originalMass;
        }
    }

    private static final class Runtime {
        final Map<SmallPlanetField.ObjectId, Active> active = new LinkedHashMap<>();
        final List<SmallPlanetField.Candidate> wanted = new ArrayList<>();
        SmallPlanetField.Candidate building;
        PlotBodyBuilder.Job job;
        boolean buildTicketed, adopted;
        long nextPass, nextSpawn, maxBatchNanos;
        int built, removed, detached, failed;
    }

    private SmallPlanetPhysicalizer() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        ServerLevel host = host(event.getServer());
        if (host == null) return;
        Runtime runtime = RUNTIMES.computeIfAbsent(event.getServer(), ignored -> new Runtime());
        adopt(host, runtime);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = host(server);
        if (level == null) return;
        boolean enabled = XenoSpaceConfig.enabled() && XenoSpaceConfig.smallPlanetPhysical()
                && XenoSpaceConfig.smallPlanetBuildIncrementalSections()
                && XenoSpaceConfig.smallPlanetMaxGlobal() > 0 && XenoSpaceConfig.smallPlanetMaxPerPlayer() > 0;
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null) {
            if (!enabled) return;
            runtime = new Runtime();
            RUNTIMES.put(server, runtime);
            adopt(level, runtime);
        }

        long now = server.overworld().getGameTime();
        if (enabled && !runtime.adopted) adopt(level, runtime);
        if (!enabled) {
            runtime.wanted.clear();
            abandonBuild(level, runtime, "small-planet physicalization disabled");
            for (var entry : new ArrayList<>(runtime.active.entrySet()))
                release(level, runtime, entry.getKey(), entry.getValue(), "feature disabled");
            pruneRemoved(runtime, level);
            if (runtime.active.isEmpty() && runtime.job == null) RUNTIMES.remove(server);
            return;
        }

        if (now >= runtime.nextPass) {
            runtime.nextPass = now + PASS_TICKS;
            pass(server, level, runtime);
        }
        stepBuild(level, runtime);
        if (runtime.job == null && !runtime.wanted.isEmpty() && now >= runtime.nextSpawn
                && runtime.active.size() < XenoSpaceConfig.smallPlanetMaxGlobal()) {
            startNext(level, runtime);
        }
    }

    private static void pass(MinecraftServer server, ServerLevel level, Runtime runtime) {
        var state = SpaceChartService.state(server);
        var chart = state.runtime.chart;
        var charted = new ArrayList<SmallPlanetField.Sphere>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) {
            double x = chart.x(slot), y = chart.y(slot), z = chart.z(slot), radius = chart.body(slot).radiusBlocks();
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(radius) && radius > 0)
                charted.add(new SmallPlanetField.Sphere(x, y, z, radius));
        }
        List<AsteroidField.Belt> belts = AsteroidFieldSync.belts(state);
        long seed = net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(server);
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        Map<SmallPlanetField.ObjectId, SmallPlanetField.Candidate> candidates = new HashMap<>();
        Map<SmallPlanetField.ObjectId, Double> nearest = new HashMap<>();
        int perPlayer = XenoSpaceConfig.smallPlanetMaxPerPlayer();
        double releaseRange = XenoSpaceConfig.smallPlanetReleaseRange();
        for (ServerPlayer player : level.players()) {
            List<SmallPlanetField.Candidate> nearby = new ArrayList<>(SmallPlanetField.nearby(seed,
                    player.getX(), player.getY(), player.getZ(), releaseRange,
                    SmallPlanetField.MAX_PLANETS_PER_SYSTEM, charted, belts));
            nearby.removeIf(candidate -> saved.isDetached(candidate.id()));
            nearby.sort(Comparator.comparingDouble((SmallPlanetField.Candidate candidate) -> distanceSquared(player, candidate))
                    .thenComparing(SmallPlanetField.Candidate::id));
            for (int i = 0; i < Math.min(perPlayer, nearby.size()); i++) {
                SmallPlanetField.Candidate candidate = nearby.get(i);
                double distance = Math.sqrt(distanceSquared(player, candidate));
                candidates.putIfAbsent(candidate.id(), candidate);
                nearest.merge(candidate.id(), distance, Math::min);
            }
        }

        var global = new ArrayList<>(nearest.keySet());
        global.sort(Comparator.comparingDouble((SmallPlanetField.ObjectId id) -> nearest.get(id))
                .thenComparing(Comparator.naturalOrder()));
        int globalCap = XenoSpaceConfig.smallPlanetMaxGlobal();
        if (global.size() > globalCap) global.subList(globalCap, global.size()).clear();
        var selected = new java.util.HashSet<>(global);

        for (var entry : new ArrayList<>(runtime.active.entrySet()))
            if (!selected.contains(entry.getKey())) release(level, runtime, entry.getKey(), entry.getValue(), "outside selected proximity budget");
        if (runtime.building != null && !selected.contains(runtime.building.id()))
            abandonBuild(level, runtime, "candidate left selected proximity budget");

        runtime.wanted.clear();
        double activateRange = XenoSpaceConfig.smallPlanetActivateRange();
        for (SmallPlanetField.ObjectId id : global) {
            if (runtime.active.containsKey(id)) continue;
            SmallPlanetField.Candidate candidate = candidates.get(id);
            if (candidate != null && nearest.get(id) <= activateRange + candidate.radius()) runtime.wanted.add(candidate);
        }
    }

    /** Read-only admin preview of deterministic small-planet candidates around a Voidsea position. */
    public static String preview(MinecraftServer server, double x, double y, double z, double range) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || !Double.isFinite(range) || range < 0 || range > SmallPlanetField.MAX_NEARBY_RANGE)
            return "small planets: preview range/position is outside the supported bounds";
        ServerLevel level = host(server);
        if (level == null) return "small planets: Voidsea is not loaded";

        var state = SpaceChartService.state(server);
        var chart = state.runtime.chart;
        var charted = new ArrayList<SmallPlanetField.Sphere>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) {
            double cx = chart.x(slot), cy = chart.y(slot), cz = chart.z(slot), radius = chart.body(slot).radiusBlocks();
            if (Double.isFinite(cx) && Double.isFinite(cy) && Double.isFinite(cz)
                    && Double.isFinite(radius) && radius > 0)
                charted.add(new SmallPlanetField.Sphere(cx, cy, cz, radius));
        }
        var candidates = new ArrayList<>(SmallPlanetField.nearby(
                net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(server), x, y, z, range,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, charted, AsteroidFieldSync.belts(state)));
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        candidates.removeIf(candidate -> saved.isDetached(candidate.id()));
        candidates.sort(Comparator.comparingDouble((SmallPlanetField.Candidate candidate) ->
                        squaredDistance(candidate, x, y, z)).thenComparing(SmallPlanetField.Candidate::id));
        if (candidates.isEmpty())
            return "small planets: no candidate within " + Math.round(range) + " blocks of "
                    + Math.round(x) + "," + Math.round(y) + "," + Math.round(z);

        StringBuilder result = new StringBuilder("small planets near ")
                .append(Math.round(x)).append(',').append(Math.round(y)).append(',').append(Math.round(z))
                .append(" (range ").append(Math.round(range)).append("): ");
        int shown = Math.min(8, candidates.size());
        for (int i = 0; i < shown; i++) {
            SmallPlanetField.Candidate candidate = candidates.get(i);
            if (i > 0) result.append('\n');
            result.append(candidate.id().bodyId()).append(" profile=").append(candidate.profile().name().toLowerCase(java.util.Locale.ROOT))
                    .append(" radius=").append(candidate.radius()).append(" mode=").append(candidate.surfaceMode().name().toLowerCase(java.util.Locale.ROOT))
                    .append(" centre=").append(Math.round(candidate.x())).append(',').append(Math.round(candidate.y())).append(',')
                    .append(Math.round(candidate.z())).append(" distance=")
                    .append(Math.round(Math.sqrt(squaredDistance(candidate, x, y, z))));
        }
        if (candidates.size() > shown) result.append("\n... ").append(candidates.size() - shown).append(" more");
        return result.toString();
    }

    private static double squaredDistance(SmallPlanetField.Candidate candidate, double x, double y, double z) {
        double dx = candidate.x() - x, dy = candidate.y() - y, dz = candidate.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static double distanceSquared(ServerPlayer player, SmallPlanetField.Candidate candidate) {
        double dx = player.getX() - candidate.x(), dy = player.getY() - candidate.y(), dz = player.getZ() - candidate.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static void stepBuild(ServerLevel level, Runtime runtime) {
        if (runtime.job == null) return;
        long started = System.nanoTime();
        var job = runtime.job;
        var candidate = runtime.building;
        try {
            long tick = level.getServer().overworld().getGameTime();
            if (job.body() == null && !net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.mayPrepare(
                    level.getServer(), tick, net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET,
                    XenoSpaceConfig.smallPlanetBuildVoxelsPerTick())) return;
            if (!job.prepare()) {
                if (job.failed()) abandonBuild(level, runtime, "no free Sable plot");
                return;
            }
            // Ticket after preparation even if another manager consumed this tick's voxel allowance.
            if (!runtime.buildTicketed && job.body() != null && !job.body().isRemoved()) {
                SubLevelContainer.getContainer(level).addForceLoadTicket(job.body(), TICKET, candidate.id().bodyId());
                runtime.buildTicketed = true;
            }
            int stepBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.allowance(
                    level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET,
                    job, XenoSpaceConfig.smallPlanetBuildVoxelsPerTick());
            if (stepBudget <= 0) return;
            double millisBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.remainingMillis(
                    level.getServer(), tick, net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET);
            if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0 && millisBudget <= 0.0) return;
            int before = job.installed();
            long sliceStarted = System.nanoTime();
            boolean complete = job.step(stepBudget, millisBudget);
            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.record(level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET,
                    job.installed() - before);
            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.recordMillis(level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET,
                    (System.nanoTime() - sliceStarted) / 1e6);
            runtime.maxBatchNanos = Math.max(runtime.maxBatchNanos, System.nanoTime() - started);
            if (!complete) {
                if (job.failed()) abandonBuild(level, runtime, "builder reports failure");
                return;
            }
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.error("Small planet {} build failed", candidate.id(), error);
            runtime.failed++;
            abandonBuild(level, runtime, "build exception");
            return;
        }

        ServerSubLevel body = job.body();
        int blocks = job.installed();
        runtime.job = null; runtime.building = null; runtime.buildTicketed = false;
        CompoundTag identity = candidateTag(candidate);
        identity.putBoolean("building", false);
        identity.putDouble("mass", body.getMassTracker().getMass());
        identity.putInt("voxels", blocks);
        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
        body.getUserDataTag().put(TAG, identity);
        runtime.active.put(candidate.id(), new Active(candidate, body.getUniqueId(), identity.getDouble("mass")));
        var buildResult = job.result();
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordBuild(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime(), blocks,
                buildResult.fillMillis(), buildResult.installMillis(), job.maxServerSliceMillis());
        runtime.built++;
        XenoPixelsMod.LOGGER.info("Small planet built: id={} profile={} radius={} voxels={} at=({}, {}, {}) uuid={} maxBatchMs={}",
                candidate.id().bodyId(), candidate.profile(), candidate.radius(), blocks,
                Math.round(candidate.x()), Math.round(candidate.y()), Math.round(candidate.z()), body.getUniqueId(),
                String.format("%.2f", runtime.maxBatchNanos / 1_000_000.0));
        SpaceVisualSync.resendPlanetBindings(level);
    }

    private static void startNext(ServerLevel level, Runtime runtime) {
        var container = SubLevelContainer.getContainer(level);
        for (SmallPlanetField.Candidate candidate : runtime.wanted) {
            if (runtime.active.containsKey(candidate.id())) continue;
            int extent = candidate.radius();
            boolean blocked = false;
            for (var other : container.getAllSubLevels()) {
                var box = other.boundingBox();
                if (box.maxX() >= candidate.x() - extent && box.minX() <= candidate.x() + extent
                        && box.maxY() >= candidate.y() - extent && box.minY() <= candidate.y() + extent
                        && box.maxZ() >= candidate.z() - extent && box.minZ() <= candidate.z() + extent) {
                    blocked = true; break;
                }
            }
            if (blocked) continue;
            long now = level.getServer().overworld().getGameTime();
            if (!net.bullettrain.xenopixelsmod.space.physics.SpaceBuildActivationGate.tryAcquire(level.getServer(),
                    net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.Owner.SMALL_PLANET, now)) {
                runtime.nextSpawn = net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.denialRetryTick(now);
                return;
            }

            runtime.building = candidate;
            CompoundTag marker = candidateTag(candidate);
            marker.putBoolean("building", true);
            SmallPlanetVoxels voxels = new SmallPlanetVoxels(candidate);
            runtime.job = PlotBodyBuilder.start(level, new Vector3d(candidate.x(), candidate.y(), candidate.z()),
                    voxels.extent(), voxels.voxels()).withoutEviction().withoutFrameFirst().onBodyAllocated(body -> {
                        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
                        body.getUserDataTag().put(TAG, marker.copy());
                    });
            runtime.job.withIncrementalSections();
            runtime.nextSpawn = level.getServer().overworld().getGameTime()
                    + XenoSpaceConfig.smallPlanetSpawnIntervalTicks();
            return;
        }
    }

    private static void abandonBuild(ServerLevel level, Runtime runtime, String why) {
        PlotBodyBuilder.Job job = runtime.job;
        SmallPlanetField.Candidate candidate = runtime.building;
        runtime.job = null; runtime.building = null;
        boolean ticketed = runtime.buildTicketed; runtime.buildTicketed = false;
        if (job != null && job.body() != null && !job.body().isRemoved()) {
            var body = job.body();
            if (ticketed) SubLevelContainer.getContainer(level).removeForceLoadTicket(body, TICKET, candidate.id().bodyId());
            SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        if (candidate != null) XenoPixelsMod.LOGGER.info("Small planet build discarded ({}): {}", why, candidate.id().bodyId());
    }

    private static void release(ServerLevel level, Runtime runtime, SmallPlanetField.ObjectId id, Active active, String why) {
        if (active.ledgerPinned) return;
        var container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) {
            runtime.active.remove(id);
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime());
            return;
        }
        container.removeForceLoadTicket(body, TICKET, id.bodyId());
        double mass = body.getMassTracker().getMass();
        boolean touched = isTouched(body.getUserDataTag(), active.originalMass, mass);
        if (touched && SmallPlanetSavedData.get(level).detach(id)) {
            body.getUserDataTag().remove(TAG);
            runtime.active.remove(id);
            runtime.detached++;
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime());
            XenoPixelsMod.LOGGER.info("Small planet detached after edits ({}): id={} body={}", why, id.bodyId(), body.getUniqueId());
        } else if (touched) {
            // Keep the edited body and its ticket if the bounded duplicate-prevention ledger is full.
            container.addForceLoadTicket(body, TICKET, id.bodyId());
            active.ledgerPinned = true;
            XenoPixelsMod.LOGGER.error("Small planet {} kept active: edited-planet identity ledger reached {} entries",
                    id.bodyId(), SmallPlanetSavedData.MAX_DETACHED);
        } else {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            runtime.active.remove(id);
            runtime.removed++;
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime());
            XenoPixelsMod.LOGGER.info("Small planet released ({}): id={}", why, id.bodyId());
        }
        SpaceVisualSync.resendPlanetBindings(level);
    }

    private static boolean isTouched(CompoundTag userData, double originalMass, double currentMass) {
        boolean flagged = userData != null && userData.contains(TAG) && userData.getCompound(TAG).getBoolean("touched");
        return flagged || (Double.isFinite(originalMass) && Math.abs(currentMass - originalMass) > Math.max(1, originalMass * 1e-3));
    }

    private static boolean markTouched(LevelAccessor level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        var plot = SubLevelContainer.getContainer(serverLevel).getPlot(new ChunkPos(position));
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body)) return false;
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return false;
        data.getCompound(TAG).putBoolean("touched", true);
        return false;
    }

    @SubscribeEvent public static void blockBroken(BlockEvent.BreakEvent event) { markTouched(event.getLevel(), event.getPos()); }
    @SubscribeEvent public static void blockPlaced(BlockEvent.EntityPlaceEvent event) { markTouched(event.getLevel(), event.getPos()); }

    private static void adopt(ServerLevel level, Runtime runtime) {
        if (runtime.adopted) return;
        runtime.adopted = true;
        var container = SubLevelContainer.getContainer(level);
        for (var sub : new ArrayList<>(container.getAllSubLevels())) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || body.getUserDataTag() == null
                    || !body.getUserDataTag().contains(TAG)) continue;
            CompoundTag tag = body.getUserDataTag().getCompound(TAG);
            SmallPlanetField.Candidate candidate = readCandidate(tag);
            if (candidate == null || tag.getBoolean("building") || runtime.active.containsKey(candidate.id())) {
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                XenoPixelsMod.LOGGER.warn("Discarded incomplete/duplicate small-planet body {}", body.getUniqueId());
                continue;
            }
            container.addForceLoadTicket(body, TICKET, candidate.id().bodyId());
            runtime.active.put(candidate.id(), new Active(candidate, body.getUniqueId(), tag.getDouble("mass")));
            XenoPixelsMod.LOGGER.info("Small planet adopted from Sable storage: id={} uuid={}", candidate.id().bodyId(), body.getUniqueId());
        }
    }

    private static CompoundTag candidateTag(SmallPlanetField.Candidate candidate) {
        var tag = new CompoundTag();
        var id = candidate.id();
        tag.putLongArray("key", new long[]{id.systemCell().x(), id.systemCell().z(), id.slot(), id.sectorX(), id.sectorZ()});
        tag.putLong("seed", candidate.seed()); tag.putDouble("x", candidate.x()); tag.putDouble("y", candidate.y());
        tag.putDouble("z", candidate.z()); tag.putInt("radius", candidate.radius());
        tag.putInt("profile", candidate.profile().ordinal()); tag.putDouble("oreRichness", candidate.oreRichness());
        tag.putBoolean("nativeLife", false);
        return tag;
    }

    private static SmallPlanetField.Candidate readCandidate(CompoundTag tag) {
        long[] key = tag.getLongArray("key");
        int profile = tag.getInt("profile");
        if (key.length != 5 || key[0] < -UniverseField.MAX_CELL || key[0] > UniverseField.MAX_CELL
                || key[1] < -UniverseField.MAX_CELL || key[1] > UniverseField.MAX_CELL
                || key[2] < 0 || key[2] >= SmallPlanetField.MAX_PLANETS_PER_SYSTEM
                || key[3] < -SmallPlanetField.MAX_SECTOR_OFFSET || key[3] > SmallPlanetField.MAX_SECTOR_OFFSET
                || key[4] < -SmallPlanetField.MAX_SECTOR_OFFSET || key[4] > SmallPlanetField.MAX_SECTOR_OFFSET
                || profile < 0 || profile >= SmallPlanetField.Profile.values().length) return null;
        try {
            return new SmallPlanetField.Candidate(new SmallPlanetField.ObjectId(
                    new UniverseField.Cell((int) key[0], (int) key[1]), (int) key[2], (int) key[3], (int) key[4]),
                    tag.getLong("seed"), tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"),
                    tag.getInt("radius"), SmallPlanetField.Profile.values()[profile], tag.getDouble("oreRichness"),
                    tag.getBoolean("nativeLife"));
        } catch (IllegalArgumentException invalid) { return null; }
    }

    private static void pruneRemoved(Runtime runtime, ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        runtime.active.entrySet().removeIf(entry -> !(container.getSubLevel(entry.getValue().body) instanceof ServerSubLevel body)
                || body.isRemoved());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        Runtime runtime = RUNTIMES.get(event.getServer());
        ServerLevel level = host(event.getServer());
        if (runtime == null || level == null) return;
        abandonBuild(level, runtime, "server stopping");
        for (var entry : new ArrayList<>(runtime.active.entrySet()))
            release(level, runtime, entry.getKey(), entry.getValue(), "server stopping");
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { RUNTIMES.remove(event.getServer()); }

    public static Map<String, ServerSubLevel> bodies(ServerLevel level) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.active.isEmpty()) return Map.of();
        var out = new LinkedHashMap<String, ServerSubLevel>();
        var container = SubLevelContainer.getContainer(level);
        for (Active active : runtime.active.values())
            if (container.getSubLevel(active.body) instanceof ServerSubLevel body && !body.isRemoved())
                out.put(active.candidate.id().bodyId(), body);
        return out;
    }

    /** Radius communicated to clients for radial gravity; zero excludes enterable/invalid bodies. */
    public static int radialGravityRadius(ServerSubLevel body) {
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return 0;
        int radius = data.getCompound(TAG).getInt("radius");
        return radius >= SmallPlanetField.MIN_RADIUS && radius <= SmallPlanetField.WALKABLE_RADIUS_LIMIT ? radius : 0;
    }

    /** Finds the nearest active walkable body and writes its inward acceleration into a caller-owned vector. */
    public static boolean radialAcceleration(ServerLevel level, net.minecraft.world.entity.player.Player player,
                                             org.joml.Vector4d output) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || runtime.active.isEmpty()) return false;
        var container = SubLevelContainer.getContainer(level);
        double px = player.getX(), py = player.getY() + player.getBbHeight() * 0.5, pz = player.getZ();
        double nearest = Double.POSITIVE_INFINITY;
        boolean found = false;
        for (Active active : runtime.active.values()) {
            int radius = active.candidate.radius();
            if (active.candidate.surfaceMode() != SmallPlanetField.SurfaceMode.WALKABLE
                    || radius > SmallPlanetField.WALKABLE_RADIUS_LIMIT) continue;
            if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            var box = body.getPlot().getBoundingBox();
            if (box == null) continue;
            Vector3d center = body.logicalPose().transformPosition(GRAVITY_CENTER.get().set(
                    (box.minX() + box.maxX() + 1) * 0.5,
                    (box.minY() + box.maxY() + 1) * 0.5,
                    (box.minZ() + box.maxZ() + 1) * 0.5));
            double dx = center.x - px, dy = center.y - py, dz = center.z - pz;
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= nearest || distanceSquared >= 4.0 * radius * radius) continue;
            if (SmallPlanetRadialGravity.accelerationAt(px, py, pz, center.x, center.y, center.z,
                    radius, player.getGravity(), output)) {
                nearest = distanceSquared;
                found = true;
            }
        }
        return found;
    }

    public static String status(MinecraftServer server) {
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null) return "small planets: " + (XenoSpaceConfig.smallPlanetPhysical() ? "enabled, idle" : "off")
                + " radialGravity=" + XenoSpaceConfig.smallPlanetRadialGravity();
        int activeVoxels = 0, unknownVoxelBodies = runtime.active.size();
        ServerLevel activeLevel = host(server);
        if (activeLevel != null) for (Active active : runtime.active.values()) {
            if (SubLevelContainer.getContainer(activeLevel).getSubLevel(active.body) instanceof ServerSubLevel body
                    && body.getUserDataTag() != null && body.getUserDataTag().contains(TAG)
                    && body.getUserDataTag().getCompound(TAG).contains("voxels")) {
                activeVoxels += Math.max(0, body.getUserDataTag().getCompound(TAG).getInt("voxels"));
                unknownVoxelBodies--;
            }
        }
        return "small planets: " + (XenoSpaceConfig.smallPlanetPhysical()
                ? XenoSpaceConfig.smallPlanetBuildIncrementalSections() ? "on" : "waiting for incremental writer opt-in"
                : "off")
                + " active=" + runtime.active.size() + "/" + XenoSpaceConfig.smallPlanetMaxGlobal() + " activeVoxels=" + activeVoxels
                + " unknownVoxelBodies=" + unknownVoxelBodies
                + " radialGravity=" + XenoSpaceConfig.smallPlanetRadialGravity()
                + " wanted=" + runtime.wanted.size() + " building="
                + (runtime.building == null ? "-" : runtime.building.id().bodyId())
                + " built/removed/detached/failed=" + runtime.built + "/" + runtime.removed + "/"
                + runtime.detached + "/" + runtime.failed + " maxBatchMs="
                + String.format("%.2f", runtime.maxBatchNanos / 1_000_000.0);
    }

    private static ServerLevel host(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
    }
}
