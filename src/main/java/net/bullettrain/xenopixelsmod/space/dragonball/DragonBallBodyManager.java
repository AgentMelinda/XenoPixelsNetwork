package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.dragonball.DragonBallBodyReconciliation.Action;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.SpaceBuildActivationGate;
import net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget;
import net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Startup and periodic reconciliation of the seven saved SDB slots against Sable bodies in the Voidsea.
 * Supported chart assignments can create one budgeted physical prototype at a time; restored bodies are
 * rebound instead of duplicated, and completed identities feed the orbit, renderer-binding, and tracking paths.
 *
 * <p>The pass runs on {@code ServerTickEvent.Post} because the cadences it honours are counted in game ticks;
 * a Sable physics callback fires once per physics system per step and would charge the same work repeatedly.
 *
 * <p>The current build radius is a bounded prototype, not a final moon-scale decision. Slots without a supported
 * published chart assignment remain deferred, and invalid host-orbit data fails closed rather than receiving an
 * invented period.
 *
 * <p>The scan deadline lives here rather than in the scheduler: {@link DragonBallBodyScan#tagged} enumerates
 * every loaded Sable sub-level and opens each one's user data, which is a per-pass cost the scheduler's interval
 * only expresses a preference for, never a limit on.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class DragonBallBodyManager {
    /**
     * How often the loaded-body and ledger scan runs when nothing else is urgent. A constant rather than a
     * config key for now, because the shared config file is not mine to extend without notice; it becomes a
     * config value with the build slice.
     */
    static final int SCAN_INTERVAL_TICKS = 40;
    /** First prototype is the design's one-chunk-scale sphere; exact voxel writer caps each tick's block work. */
    static final int PROTOTYPE_RADIUS = (int) DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS;
    static final int BUILD_VOXELS_PER_TICK = 512;

    private static DragonBallPlanetScheduler scheduler;
    /**
     * The tick the next whole-container scan may run. The scan enumerates every loaded Sable sub-level and opens
     * each one's user data, so it is paced here rather than left to the scheduler's rescan interval, which only
     * describes when the scheduler would like a fresh plan.
     */
    private static long nextScanTick;
    /** Stable conditions are reported once and re-reported when they change, so a long block is not per-pass spam. */
    private static String lastBlockedReport;
    private static boolean scanUnavailableReported;
    private static final Map<Integer, String> LAST_SLOT_CONFLICTS = new HashMap<>();
    private static PlotBodyBuilder.Job buildJob;
    private static int buildingSlot;
    private static int previousInstalled;

    private DragonBallBodyManager() {}

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        long gameTime = server.getTickCount();
        if (scheduler == null) {
            scheduler = new DragonBallPlanetScheduler(CosmicDragonBalls.COUNT, SCAN_INTERVAL_TICKS);
            nextScanTick = 0;
        }
        if (gameTime % 10 == 0 && XenoSpaceConfig.enabled() && XenoSpaceConfig.planetReleaseStaffLock())
            releasePhysicsStaffLocks(server);
        if (gameTime >= nextScanTick) {
            nextScanTick = SpaceActivationBudget.tickAfter(gameTime, SCAN_INTERVAL_TICKS);
            reconcile(server, gameTime);
        }
        advanceBuild(server, gameTime);
    }

    private static void releasePhysicsStaffLocks(MinecraftServer server) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) return;
        var container = SubLevelContainer.getContainer(level);
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            UUID id = saved.planetBodyUuid(slot);
            if (id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            if (PhysicalEarth.releaseStaffLock(level, body))
                XenoPixelsMod.LOGGER.info("Physics staff lock removed from managed Super Dragon Ball slot {}; its orbit controller resumes after the push",
                        slot);
        }
    }

    /** Drops the schedule with the server so an in-process restart cannot inherit a stale pending build. */
    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        scheduler = null;
        nextScanTick = 0;
        lastBlockedReport = null;
        scanUnavailableReported = false;
        LAST_SLOT_CONFLICTS.clear();
        if (buildJob != null) {
            try {
                ServerSubLevel body = buildJob.body();
                ServerLevel host = event.getServer().getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
                if (body != null && host != null) SubLevelContainer.getContainer(host).removeForceLoadTicket(
                        body, DragonBallPlanets.TICKET, body.getUniqueId().toString());
                buildJob.cancel();
            } catch (RuntimeException failure) {
                XenoPixelsMod.LOGGER.warn("Could not cancel half-built SDB slot {} during shutdown", buildingSlot, failure);
            }
            buildJob = null;
            buildingSlot = 0;
        }
        DragonBallBodyScan.forget(event.getServer());
    }

    /**
     * One scan, plan and bind pass. The scan lives in {@code level} only; a missing scan is reported as
     * unavailable, which blocks the cycle instead of reading "nothing is loaded" from an empty world.
     */
    static void reconcile(MinecraftServer server, long gameTime) {
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        int slots = CosmicDragonBalls.COUNT;
        Map<Integer, List<UUID>> loaded = DragonBallBodyScan.tagged(level, slots);
        if (loaded == null) {
            if (!scanUnavailableReported) {
                scanUnavailableReported = true;
                XenoPixelsMod.LOGGER.warn("SDB scan unavailable at tick {}; cycles stay unauthorized until the"
                        + " sub-level listing can be read, and this is re-reported only when it recovers", gameTime);
            }
        } else if (scanUnavailableReported) {
            scanUnavailableReported = false;
            XenoPixelsMod.LOGGER.info("SDB scan available again at tick {}", gameTime);
        }

        var savedBodyIds = new ArrayList<UUID>(slots);
        for (int slot = 1; slot <= slots; slot++) savedBodyIds.add(saved.planetBodyUuid(slot));
        var plan = DragonBallBodyPlan.plan(savedBodyIds, loaded);
        // Do not let an empty ledger slot become a future body-build request unless chart loading has
        // published a physically supported assignment for it. Existing/tagged bodies still reconcile.
        var supportedSlots = net.bullettrain.xenopixelsmod.space.SpaceChartService.state(server)
                .dragonBallPlanets.keySet();
        plan = DragonBallBodyPlan.deferUnassignedCreates(plan, supportedSlots);
        scheduler.reconcile(plan, gameTime);

        if (plan.overall() != DragonBallBodyPlan.Overall.PROCEED) {
            String report = plan.overall() + ": " + plan.reason();
            if (!report.equals(lastBlockedReport)) {
                lastBlockedReport = report;
                XenoPixelsMod.LOGGER.warn("SDB reconcile blocked at tick {}: {}", gameTime, report);
            }
            return;
        }
        if (lastBlockedReport != null) {
            lastBlockedReport = null;
            XenoPixelsMod.LOGGER.info("SDB reconcile is authorized again at tick {}", gameTime);
        }
        Set<Integer> conflictedSlots = new HashSet<>();
        for (DragonBallBodyPlan.Entry entry : plan.entries()) {
            if (entry.action() == Action.ADOPT) bind(saved, entry, gameTime);
            else if (entry.action() == Action.CONFLICT) {
                conflictedSlots.add(entry.slot());
                String signature = entry.reason();
                if (!signature.equals(LAST_SLOT_CONFLICTS.put(entry.slot(), signature)))
                    XenoPixelsMod.LOGGER.warn("SDB slot {} conflict at tick {}: {}", entry.slot(), gameTime,
                            entry.reason());
            } else if (LAST_SLOT_CONFLICTS.remove(entry.slot()) != null) {
                XenoPixelsMod.LOGGER.info("SDB slot {} conflict cleared at tick {}", entry.slot(), gameTime);
            }
        }
        LAST_SLOT_CONFLICTS.keySet().removeIf(slot -> !conflictedSlots.contains(slot));
        if (loaded != null && plan.overall() == DragonBallBodyPlan.Overall.PROCEED) {
            Map<Integer, UUID> boundBodies = new HashMap<>();
            for (int slot = 1; slot <= slots; slot++) {
                UUID bodyId = saved.planetBodyUuid(slot);
                if (bodyId != null) boundBodies.put(slot, bodyId);
            }
            DragonBallPlanetOrbitPass.sync(server, boundBodies,
                    net.bullettrain.xenopixelsmod.space.SpaceChartService.state(server).dragonBallPlanets);
        }
    }

    /** Advances one exact-voxel, bounded SDB build or starts the next fair, budgeted slot. */
    private static void advanceBuild(MinecraftServer server, long gameTime) {
        if (scheduler == null || (!XenoSpaceConfig.enabled() && buildJob == null)) return;
        if (buildJob == null) {
            if (!SpaceBuildWorkBudget.mayPrepare(server, gameTime,
                    SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL, BUILD_VOXELS_PER_TICK)) return;
            Integer slot = scheduler.takeCreateAttempt(gameTime);
            if (slot == null) return;
            if (!SpaceBuildActivationGate.tryAcquire(server,
                    SpaceActivationBudget.Owner.SUPER_DRAGON_BALL, gameTime)) {
                scheduler.onGateDenied(gameTime);
                return;
            }
            if (!startBuild(server, slot, gameTime)) return;
        }

        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) {
            failBuild(server, gameTime, "Voidsea level unavailable", null);
            return;
        }
        long started = System.nanoTime();
        boolean complete;
        try {
            if (!buildJob.prepare()) return;
            int voxelAllowance = SpaceBuildWorkBudget.allowance(server, gameTime,
                    SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL, buildJob, BUILD_VOXELS_PER_TICK);
            if (voxelAllowance <= 0) return;
            int before = buildJob.installed();
            double millis = XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0
                    ? SpaceBuildWorkBudget.remainingMillis(server, gameTime,
                        SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL) : 0.0;
            if (XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0 && millis <= 0.0) return;
            complete = millis > 0.0
                    ? buildJob.step(voxelAllowance, Math.min(millis, 2.0))
                    : buildJob.step(voxelAllowance);
            int installed = Math.max(0, buildJob.installed() - before);
            SpaceBuildWorkBudget.record(server, gameTime,
                    SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL, installed);
            SpaceBuildWorkBudget.recordMillis(server, gameTime,
                    SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL, (System.nanoTime() - started) / 1.0e6);
            previousInstalled = installed;
        } catch (RuntimeException failure) {
            failBuild(server, gameTime, "builder threw", failure);
            return;
        }
        if (buildJob.failed()) {
            failBuild(server, gameTime, "builder failed or found no plot", null);
            return;
        }
        if (!complete) return;

        PlotBodyBuilder.Job completed = buildJob;
        ServerSubLevel body = completed.body();
        DragonBallIdentity identity = DragonBallIdentity.forSlot(buildingSlot);
        CompoundTag user = body.getUserDataTag();
        if (user == null) { user = new CompoundTag(); body.setUserDataTag(user); }
        CompoundTag tag = new CompoundTag();
        tag.putInt(DragonBallBodyScan.SLOT_KEY, buildingSlot);
        tag.putInt(DragonBallBodyScan.IDENTITY_VERSION_KEY, DragonBallIdentity.VERSION);
        tag.putUUID("PlanetId", identity.planetId());
        tag.putString("PlanetKey", identity.planetKey());
        tag.putString("SurfaceCellKey", identity.surfaceCellKey());
        user.put(DragonBallBodyScan.TAG, tag);
        try {
            DragonBallSavedData saved = DragonBallSavedData.get(server);
            saved.bindPlanetBody(buildingSlot, body.getUniqueId());
            Map<Integer, UUID> bound = new HashMap<>();
            for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
                UUID id = saved.planetBodyUuid(slot);
                if (id != null) bound.put(slot, id);
            }
            DragonBallPlanetOrbitPass.sync(server, bound,
                    net.bullettrain.xenopixelsmod.space.SpaceChartService.state(server).dragonBallPlanets);
        } catch (RuntimeException bindFailure) {
            // The tag remains authoritative; the next complete census adopts this body instead of making a duplicate.
            XenoPixelsMod.LOGGER.error("SDB slot {} built body {} but could not bind its saved identity; next census will adopt it",
                    buildingSlot, body.getUniqueId(), bindFailure);
        }
        if (scheduler.inFlightSlot() != null) scheduler.onBuildSucceeded(gameTime);
        XenoPixelsMod.LOGGER.info("SDB physical body complete slot={} uuid={} radius={} voxels={} fillMs={} installMs={} maxSliceMs={} ticks={} installedLastTick={}",
                buildingSlot, body.getUniqueId(), PROTOTYPE_RADIUS, completed.result().blocks(),
                String.format("%.1f", completed.result().fillMillis()),
                String.format("%.1f", completed.result().installMillis()),
                String.format("%.1f", completed.maxServerSliceMillis()), gameTime - buildStartedTick,
                previousInstalled);
        buildJob = null;
        buildingSlot = 0;
    }

    private static long buildStartedTick;

    private static boolean startBuild(MinecraftServer server, int slot, long gameTime) {
        var state = net.bullettrain.xenopixelsmod.space.SpaceChartService.state(server);
        DragonBallPlanetDefinition authored = state.dragonBallPlanets.get(slot);
        if (authored == null) {
            scheduler.onBuildFailed(gameTime);
            XenoPixelsMod.LOGGER.warn("SDB slot {} create was queued but its supported chart assignment disappeared", slot);
            return false;
        }
        ServerLevel level = server.getLevel(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) {
            scheduler.onBuildFailed(gameTime);
            return false;
        }
        ChartRegistry chart = state.runtime.chart;
        int hostSlot = chart.slotOf(authored.hostChartId());
        if (hostSlot < 0) { scheduler.onBuildFailed(gameTime); return false; }
        DragonBallSavedData saved = DragonBallSavedData.get(server);
        DragonBallPlanetDefinition definition = DragonBallPlanetAssignments.effective(authored,
                saved.manualOrbit(slot), chart.body(hostSlot).radiusBlocks(),
                DragonBallPlanetAssignments.PROTOTYPE_BODY_RADIUS_BLOCKS);
        double mu = chart.body(hostSlot).mu();
        DragonBallPlanetOrbit orbit;
        try {
            orbit = definition.orbitPeriodSeconds() > 0.0
                    ? DragonBallPlanetOrbit.createWithPeriod(definition.orbitRadiusBlocks(),
                        definition.orbitPhaseRadians(), definition.orbitPeriodSeconds())
                    : DragonBallPlanetOrbit.create(definition.orbitRadiusBlocks(),
                        definition.orbitPhaseRadians(), mu);
        } catch (IllegalArgumentException invalid) {
            scheduler.onBuildFailed(gameTime);
            XenoPixelsMod.LOGGER.warn("SDB slot {} cannot start: invalid host orbit: {}", slot, invalid.getMessage());
            return false;
        }
        double[] position = new double[3], velocity = new double[3], acceleration = new double[3];
        double[] hostPosition = new double[3], hostVelocity = new double[3];
        double time = level.getGameTime() / 20.0 + XenoSpaceConfig.orbitEpochOffsetSeconds();
        if (!DragonBallPlanetOrbitPass.targetPosition(chart, definition, orbit, time,
                hostPosition, hostVelocity, position, velocity, acceleration)) {
            scheduler.onBuildFailed(gameTime);
            return false;
        }
        Vector3d centre = new Vector3d(position[0], position[1], position[2]);
        var shell = ModBlocks.COSMIC_BALL_SHELL.get().defaultBlockState();
        var star = ModBlocks.COSMIC_BALL_STAR.get().defaultBlockState();
        int radius = PROTOTYPE_RADIUS;
        int slotStars = slot;
        PlotBodyBuilder.Voxels voxels = (x, y, z) -> switch (DragonBallPlanetShape.voxel(slotStars, radius,
                2, x, y, z)) { case 1 -> shell; case 2 -> star; default -> null; };
        buildJob = PlotBodyBuilder.start(level, centre, DragonBallPlanetShape.extent(radius), voxels)
                .withoutEviction().withoutFrameFirst().withIncrementalSections()
                .onBodyAllocated(body -> SubLevelContainer.getContainer(level).addForceLoadTicket(
                        body, DragonBallPlanets.TICKET, body.getUniqueId().toString()));
        buildingSlot = slot;
        buildStartedTick = gameTime;
        previousInstalled = 0;
        XenoPixelsMod.LOGGER.info("SDB physical build started slot={} host={} radius={} perTickVoxels={} tick={}",
                slot, definition.hostChartId(), radius, BUILD_VOXELS_PER_TICK, gameTime);
        return true;
    }

    private static void failBuild(MinecraftServer server, long gameTime, String reason, Throwable cause) {
        int slot = buildingSlot;
        if (buildJob != null) {
            try {
                ServerSubLevel body = buildJob.body();
                ServerLevel host = server.getLevel(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.DIMENSION, PhysicalEarth.HOST));
                if (body != null && host != null) SubLevelContainer.getContainer(host).removeForceLoadTicket(
                        body, DragonBallPlanets.TICKET, body.getUniqueId().toString());
                buildJob.cancel();
            } catch (RuntimeException cleanupFailure) {
                XenoPixelsMod.LOGGER.error("SDB slot {} failed and cleanup also failed", slot, cleanupFailure);
            }
        }
        buildJob = null;
        buildingSlot = 0;
        if (scheduler != null && scheduler.inFlightSlot() != null) scheduler.onBuildFailed(gameTime);
        if (cause == null) XenoPixelsMod.LOGGER.warn("SDB physical build failed slot={} tick={} reason={}", slot, gameTime, reason);
        else XenoPixelsMod.LOGGER.error("SDB physical build failed slot={} tick={} reason={}", slot, gameTime, reason, cause);
    }

    /** Extends Sable's native tracking range for the seven persistent physical SDB bodies. */
    public static boolean extendsNativeTracking(ServerLevel level, net.minecraft.world.entity.player.Player player,
                                                 org.joml.Vector3dc centre) {
        if (level == null || player == null || centre == null || player.level() != level
                || !PhysicalEarth.HOST.equals(level.dimension().location())) return false;
        var container = SubLevelContainer.getContainer(level);
        DragonBallSavedData saved = DragonBallSavedData.get(level.getServer());
        for (int slot = 1; slot <= CosmicDragonBalls.COUNT; slot++) {
            UUID id = saved.planetBodyUuid(slot);
            if (id == null || !(container.getSubLevel(id) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            if (body.logicalPose().position() == centre)
                return centre.distanceSquared(player.getX(), player.getY(), player.getZ())
                        <= DragonBallPlanets.TRACKING_RANGE * DragonBallPlanets.TRACKING_RANGE;
        }
        return false;
    }

    /** Binds a restored body the ledger had not claimed. The ledger's own guard makes a double claim fail. */
    private static void bind(DragonBallSavedData saved, DragonBallBodyPlan.Entry entry, long gameTime) {
        UUID bodyId = entry.bodyId();
        if (bodyId == null) {
            XenoPixelsMod.LOGGER.warn("SDB slot {} adopted a body with no identity at tick {}; leaving it unbound",
                    entry.slot(), gameTime);
            return;
        }
        try {
            if (saved.bindPlanetBody(entry.slot(), bodyId))
                XenoPixelsMod.LOGGER.info("SDB slot {} adopted Sable body {} at tick {}", entry.slot(), bodyId,
                        gameTime);
        } catch (IllegalStateException alreadyClaimed) {
            XenoPixelsMod.LOGGER.warn("SDB slot {} could not adopt {}: {}", entry.slot(), bodyId,
                    alreadyClaimed.getMessage());
        }
    }
}
