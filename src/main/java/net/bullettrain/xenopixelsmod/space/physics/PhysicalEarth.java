package net.bullettrain.xenopixelsmod.space.physics;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.SubLevelAssemblyHelper;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.block.ModBlocks;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.bullettrain.xenopixelsmod.space.math.KeplerSolver;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent native Sable planets in the Voidsea — Earth and, when the space pack charts it, the Moon — with
 * chart coordinates derived from their physical poses.
 *
 * <p>Each planet is a hollow shell of invisible {@link ModBlocks#PLANET_SHELL} voxels: collision and mass for
 * Sable, while the client draws the HD sphere. A station-keeping controller ({@link PlanetOrbitHold}) holds
 * each body on its orbit (or pinned offset) with a bounded pull, so ships can tow a planet and it returns
 * when released. The class keeps its historical name; its Earth-only static API is unchanged.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class PhysicalEarth implements SpaceTickScheduler.Pass {
    public static final String BODY_ID = "xenopixelsmod:earth";
    public static final String MOON_ID = "xenospace_spike:moon_surface";
    public static final int RADIUS = 100;
    public static final ResourceLocation HOST = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "voidsea");
    /** Shared "managed planet" marker. Earth's saved bodies carry it, so it is never renamed. */
    private static final String TAG = "xenopixelsmod_physical_earth_v1";
    /** Set once a body's shell is {@code planet_shell}; absent on Earths assembled from bedrock. */
    private static final String SHELL_V2 = TAG + "_shell_v2";

    /** One managed planet: chart id, shell radius, persistence keys and display name. */
    record Spec(String id, int radius, String ledgerName, SubLevelLoadingTicketType<String> ticket, String displayName) {}

    static final Spec EARTH = new Spec(BODY_ID, RADIUS, "xenopixelsmod_physical_earth",
            SubLevelLoadingTicketType.create(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "physical_earth"), Codec.STRING),
            "Earth");
    static final Spec MOON = new Spec(MOON_ID, 32, "xenopixelsmod_physical_moon",
            SubLevelLoadingTicketType.create(ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "physical_moon"), Codec.STRING),
            "Moon");
    /** Every other charted body shares one ticket type, keyed by its chart id (types must exist before load). */
    static final SubLevelLoadingTicketType<String> PLANET_TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "physical_planet"), Codec.STRING);
    /**
     * Managed bodies for the current {@link XenoSpaceConfig#physicalPlanets} mode, in creation order: chart
     * order is topological, so a child is always staged after (and relative to) its parent.
     */
    private static volatile List<Spec> SPECS = List.of(EARTH, MOON);

    /** The managed set a mode asks for. Earth is always physical; {@code earth_moon} is the S9 behaviour. */
    static List<Spec> specsFor(ChartRegistry chart, String mode) {
        var out = new ArrayList<Spec>();
        // Earth is always physical unless planet Hakai erased it (owner 2026-10-16: "hakai earth").
        if (!net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isErased(BODY_ID)) out.add(EARTH);
        boolean all = "all".equals(mode) || "proximity".equals(mode), planets = all || "planets".equals(mode);
        var excluded = XenoSpaceConfig.physicalPlanetExclude();
        for (int slot = 0; slot < chart.size(); slot++) {
            CelestialBody row = chart.body(slot);
            String id = row.id();
            if (id.equals(BODY_ID)) continue;
            if (id.equals(MOON_ID)) { if (!excluded.contains(id)) out.add(MOON); continue; }
            if (!planets || excluded.contains(id)) continue;
            if (row.kind() == CelestialBody.BodyKind.STAR && !all) continue;
            int radius = (int) Math.round(row.radiusBlocks());
            if (radius < 4 || radius > XenoSpaceConfig.physicalPlanetMaxRadius()) continue;
            out.add(new Spec(id, radius, "xenopixelsmod_physical_" + id.replace(':', '_').replace('/', '_'),
                    PLANET_TICKET, row.displayName()));
        }
        return List.copyOf(out);
    }
    static List<Spec> specs() { return SPECS; }

    private static final PhysicalEarth PASS = new PhysicalEarth();
    private static final Map<ServerLevel, Map<String, UUID>> IDS = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Map<String, String>> DIAGNOSTICS = new IdentityHashMap<>();

    /** Per-body controller memory: last pose sample for a velocity estimate, and the last correction. */
    private static final class Track {
        final double[] previous = new double[3];
        final double[] velocity = new double[3];
        final double[] previousVelocity = new double[3];
        final double[] command = new double[3], previousCommand = new double[3], measured = new double[3];
        final PlanetOrbitHold.ReleaseDetector release = new PlanetOrbitHold.ReleaseDetector();
        long previousTick = Long.MIN_VALUE;
        boolean hasVelocity, held;
        double lastError, lastCorrection;
        void reset() {
            previousTick = Long.MIN_VALUE; hasVelocity = false; held = false;
            java.util.Arrays.fill(velocity, 0); java.util.Arrays.fill(command, 0); java.util.Arrays.fill(previousCommand, 0);
            release.reset();
        }
    }
    private final Map<UUID, Track> tracks = new HashMap<>();
    private final Vector3d impulse = new Vector3d(), gravity = new Vector3d(), teleportTarget = new Vector3d(),
            linear = new Vector3d(), angular = new Vector3d();
    private final double[] position = new double[3], target = new double[3], targetVelocity = new double[3],
            feedForward = new double[3], offset = new double[3], offsetVelocity = new double[3];
    private final java.util.Set<UUID> reportedPhysics = new java.util.HashSet<>();
    private PhysicalEarth() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        // Register even when disabled: the cheap run gate supports config reload without duplicate registration.
        SpaceTickScheduler.register(PASS);
        if (!XenoSpaceConfig.enabled()) return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, HOST));
        if (level == null) { refuse(server, EARTH, "Voidsea dimension is missing; Earth was not created."); return; }
        var chart = SpaceChartService.state(server).runtime.chart;
        SPECS = specsFor(chart, XenoSpaceConfig.physicalPlanets());
        prune(level);
        removeOrphanShellFragments(level);
        long began = System.nanoTime();
        for (Spec spec : SPECS) {
            if (spec != EARTH && chart.slotOf(spec.id()) < 0) {
                diagnose(server, spec, spec.displayName() + " is not charted (space pack disabled?); not created");
                continue;
            }
            try { initialize(level, spec, !onDemand(spec)); }
            catch (RuntimeException error) {
                refuse(server, spec, spec.displayName() + " initialization refused: " + error.getMessage());
                Ledger ledger = Ledger.get(level, spec.ledgerName());
                if (!ledger.attempted && ledger.id == null) {
                    // Refused before anything was placed (parent out of place, players in the way): safe to
                    // try again later from the server tick.
                    PENDING.computeIfAbsent(server, ignored -> new java.util.LinkedHashSet<>()).add(spec);
                    XenoPixelsMod.LOGGER.warn("Physical {} not created yet; nothing was placed, retrying every {} ticks",
                            spec.displayName(), RETRY_TICKS);
                } else {
                    XenoPixelsMod.LOGGER.error("Physical {} initialization failed; no automatic retry or deletion", spec.displayName(), error);
                }
            }
        }
        XenoPixelsMod.LOGGER.info("Physical planets ready: mode={}, managed={}, loaded={}, startup ms={}",
                XenoSpaceConfig.physicalPlanets(), SPECS.size(), bodies(level).size(),
                String.format("%.0f", (System.nanoTime() - began) / 1_000_000.0));
    }

    /**
     * Applies a mode change live: bodies the new mode drops are deleted from the world (that is the lag
     * the switch exists to remove), and bodies it adds are queued and built one per retry, away from
     * players. Returns a one-line summary for the command.
     */
    public static String applyMode(MinecraftServer server) {
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, HOST));
        if (level == null || !XenoSpaceConfig.enabled()) return "space is off or Voidsea is missing; applies on next start";
        var chart = SpaceChartService.state(server).runtime.chart;
        SPECS = specsFor(chart, XenoSpaceConfig.physicalPlanets());
        int removed = prune(level);
        int queued = 0;
        var pending = PENDING.computeIfAbsent(server, ignored -> new java.util.LinkedHashSet<>());
        pending.removeIf(spec -> !SPECS.contains(spec));
        for (Spec spec : SPECS) if (getBody(level, spec.id()) == null && pending.add(spec)) queued++;
        return "physical planets=" + XenoSpaceConfig.physicalPlanets() + ": " + SPECS.size() + " managed, "
                + removed + " removed, " + queued + " queued (built one at a time while no player is in the Voidsea)";
    }

    /** Deletes managed bodies the current mode no longer lists, and frees their ledger for a later rebuild. */
    private static int prune(ServerLevel level) {
        var keep = new java.util.HashSet<String>();
        for (Spec spec : SPECS) keep.add(spec.id());
        var container = SubLevelContainer.getContainer(level);
        int removed = 0;
        for (var body : new ArrayList<>(container.getAllSubLevels())) {
            if (!isManaged(body) || body.isRemoved()) continue;
            String id = bodyIdOf(body);
            if (keep.contains(id) || (id.equals(BODY_ID) && !net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isErased(BODY_ID))) continue;
            Spec own = id.equals(BODY_ID) ? EARTH : id.equals(MOON_ID) ? MOON : null;
            release(level, id, own != null ? own.ticket() : PLANET_TICKET,
                    own != null ? own.ledgerName() : ledgerName(id), body, "mode " + XenoSpaceConfig.physicalPlanets());
            removed++;
        }
        if (removed > 0) net.bullettrain.xenopixelsmod.space.SpaceVisualSync.resendPlanetBindings(level);
        return removed;
    }

    static String ledgerName(String id) { return "xenopixelsmod_physical_" + id.replace(':', '_').replace('/', '_'); }

    /**
     * Removes one managed body from the world and frees its ledger so it can be built again. Shells cannot be
     * modified (breaking is cancelled), so a rebuild is identical: nothing needs saving.
     */
    private static void release(ServerLevel level, String id, SubLevelLoadingTicketType<String> ticket, String ledgerName,
                                ServerSubLevel body, String why) {
        var container = SubLevelContainer.getContainer(level);
        container.removeForceLoadTicket(body, ticket, id);
        container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
        ids(level).remove(id);
        Ledger ledger = Ledger.get(level, ledgerName);
        ledger.id = null; ledger.attempted = false; ledger.setDirty();
        PASS.tracks.remove(body.getUniqueId());
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.CHART_PLANET, System.nanoTime());
        XenoPixelsMod.LOGGER.info("Physical planet released ({}): body={}, UUID={}", why, id, body.getUniqueId());
    }

    /** True for a body that {@code proximity} mode builds only when a player comes near. Earth never is. */
    static boolean onDemand(Spec spec) { return spec != EARTH && XenoSpaceConfig.proximityPlanets(); }

    /** Proximity decision, pure: {@code +1} activate, {@code -1} release, {@code 0} keep the current state. */
    static int proximity(double nearestPlayerDistance, double radius, boolean loaded, double activate, double release) {
        if (!loaded && nearestPlayerDistance <= activate + radius) return 1;
        if (loaded && nearestPlayerDistance > release + radius) return -1;
        return 0;
    }

    static double distance3d(double ax, double ay, double az, double bx, double by, double bz) {
        return Math.hypot(Math.hypot(ax - bx, ay - by), az - bz);
    }

    private static final Map<MinecraftServer, int[]> PROXIMITY_STATS = new IdentityHashMap<>();

    /** S11 batched-build test (debug only): bodies the proximity pass treats as if a player stood at them. */
    private static final java.util.Set<String> DEBUG_NEAR = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** S11 batched-build test (debug only, never called by gameplay): simulate or clear player proximity for one body. */
    public static String debugForceNear(String bodyId, boolean near) {
        if (near) DEBUG_NEAR.add(bodyId); else DEBUG_NEAR.remove(bodyId);
        String report = "debug proximity " + bodyId + " near=" + near + " budget=" + XenoSpaceConfig.planetBuildVoxelsPerTick();
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    /**
     * One proximity pass: each on-demand body is compared with the nearest Voidsea player (chart position
     * for unbuilt bodies, which keep moving on their analytic orbit; live pose for built ones). Far bodies
     * are released at once; near ones are queued, and the queue builds one per pass.
     */
    private static void proximityPass(MinecraftServer server, ServerLevel level) {
        var chart = SpaceChartService.state(server).runtime.chart;
        var players = level.players();
        int released = 0;
        for (Spec spec : SPECS) {
            if (!onDemand(spec)) continue;
            int slot = chart.slotOf(spec.id());
            if (slot < 0) continue;
            ServerSubLevel body = getBody(level, spec.id());
            double x, y, z;
            if (body != null) { var p = body.logicalPose().position(); x = p.x(); y = p.y(); z = p.z(); }
            else { x = chart.x(slot); y = chart.y(slot); z = chart.z(slot); }
            double nearest = DEBUG_NEAR.contains(spec.id()) ? 0 : Double.POSITIVE_INFINITY;
            for (var player : players) {
                // For unbuilt bodies, chart Y is still their current analytic orbit plane and belongs
                // in the same 3D activation distance as a built body's live pose.
                nearest = Math.min(nearest, distance3d(player.getX(), player.getY(), player.getZ(), x, y, z));
            }
            boolean underway = building(server, spec);
            int decision = proximity(nearest, spec.radius(), body != null || underway,
                    XenoSpaceConfig.proximityActivateRange(), XenoSpaceConfig.proximityReleaseRange());
            var pending = PENDING.computeIfAbsent(server, ignored -> new java.util.LinkedHashSet<>());
            if (decision < 0 && underway) { cancelBuild(server, spec); continue; }
            if (decision > 0) pending.add(spec);
            else if (decision < 0) {
                release(level, spec.id(), spec.ticket(), spec.ledgerName(), body, "no player within "
                        + (long) (XenoSpaceConfig.proximityReleaseRange() + spec.radius()));
                released++;
            } else if (body == null && nearest > XenoSpaceConfig.proximityActivateRange() + spec.radius()) {
                pending.remove(spec);
            }
        }
        if (released > 0) net.bullettrain.xenopixelsmod.space.SpaceVisualSync.resendPlanetBindings(level);
        PROXIMITY_STATS.computeIfAbsent(server, ignored -> new int[2])[1] += released;
    }

    private static final Map<MinecraftServer, java.util.Set<Spec>> PENDING = new IdentityHashMap<>();
    /** Early retry after the shared build gate denies a pending chart body; otherwise wait-poll may be 100 ticks. */
    private static final Map<MinecraftServer, Long> PENDING_BUDGET_RETRY_TICK = new IdentityHashMap<>();
    private static final int RETRY_TICKS = 100;

    /**
     * Server-tick upkeep outside the physics step: physics-staff locks come off managed planets (a locked
     * planet cannot be returned to its orbit), and a body refused before placement is retried.
     */
    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        if (!XenoSpaceConfig.enabled()) return;
        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, HOST));
        if (level == null) return;
        stepBuilds(server, level);
        if (tick % 200 == 0) removeOrphanShellFragments(level);
        if (tick % 200 == 0 && XenoSpaceConfig.planetRecoverLost()) removeLateDuplicates(level);
        if (tick % 10 != 0) return;
        if (XenoSpaceConfig.planetReleaseStaffLock())
            for (var entry : bodies(level).entrySet())
                if (StaffLocks.release(level, entry.getValue()))
                    XenoPixelsMod.LOGGER.info("Physics staff lock removed from managed planet {}; it returns to its orbit",
                            entry.getKey());
        int interval = XenoSpaceConfig.proximityPlanets() ? XenoSpaceConfig.proximityIntervalTicks() : RETRY_TICKS;
        if (XenoSpaceConfig.proximityPlanets() && tick % interval == 0) proximityPass(server, level);
        var pending = PENDING.get(server);
        if (pending == null || pending.isEmpty()) {
            PENDING_BUDGET_RETRY_TICK.remove(server);
            return;
        }
        long gameTime = server.overworld().getGameTime();
        long retryTick = PENDING_BUDGET_RETRY_TICK.getOrDefault(server, Long.MAX_VALUE);
        if (!pendingAttemptDue(gameTime, interval, retryTick)) return;
        if (gameTime >= retryTick) PENDING_BUDGET_RETRY_TICK.remove(server);
        for (var iterator = pending.iterator(); iterator.hasNext(); ) {
            Spec spec = iterator.next();
            try {
                if (getBody(level, spec.id()) != null || building(server, spec)) { iterator.remove(); continue; }
                if (!SpaceBuildActivationGate.tryAcquire(server, SpaceActivationBudget.Owner.CHART_PLANET,
                        gameTime)) {
                    PENDING_BUDGET_RETRY_TICK.put(server, nextPendingBudgetRetry(gameTime));
                    return;
                }
                initialize(level, spec);
                iterator.remove();
                if (building(server, spec)) {
                    XenoPixelsMod.LOGGER.info("Physical {} build started (batched, {} voxels per tick)",
                            spec.displayName(), XenoSpaceConfig.planetBuildVoxelsPerTick());
                    return;
                }
                XenoPixelsMod.LOGGER.info("Physical {} created {}", spec.displayName(),
                        onDemand(spec) ? "because a player came near" : "on retry");
                if (onDemand(spec)) PROXIMITY_STATS.computeIfAbsent(server, ignored -> new int[2])[0]++;
                net.bullettrain.xenopixelsmod.space.SpaceVisualSync.resendPlanetBindings(level);
                return;
            } catch (RuntimeException error) {
                String why = spec.displayName() + " waiting to be created: " + error.getMessage();
                if (!why.equals(DIAGNOSTICS.getOrDefault(server, Map.of()).get(spec.id()))) XenoPixelsMod.LOGGER.info("{}", why);
                diagnose(server, spec, why);
                if (Ledger.get(level, spec.ledgerName()).attempted) {
                    iterator.remove();
                    XenoPixelsMod.LOGGER.error("Physical {} retry failed after placement began; no further retry", spec.displayName(), error);
                }
            }
        }
    }

    /** The normal chart retry can be 100 ticks; a budget denial retries after the next 10-tick gate interval. */
    static boolean pendingAttemptDue(long gameTime, int normalInterval, long budgetRetryTick) {
        return gameTime % Math.max(10, normalInterval) == 0 || gameTime >= budgetRetryTick;
    }

    static long nextPendingBudgetRetry(long gameTime) {
        return SpaceActivationBudget.denialRetryTick(gameTime);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        SpaceBuildActivationGate.forget(event.getServer());
        SpaceBuildWorkBudget.forget(event.getServer());
        SpaceTickScheduler.unregister(PASS);
        IDS.keySet().removeIf(level -> level.getServer() == event.getServer());
        DIAGNOSTICS.remove(event.getServer());
        PENDING.remove(event.getServer());
        PENDING_BUDGET_RETRY_TICK.remove(event.getServer());
        PROXIMITY_STATS.remove(event.getServer());
        DEBUG_NEAR.clear();
        ORPHAN_CHECKED.remove(event.getServer());
        var builds = BUILDS.remove(event.getServer());
        if (builds != null) builds.values().forEach(build -> build.job().cancel());
        PASS.reportedPhysics.clear();
        PASS.tracks.clear();
    }

    private static void diagnose(MinecraftServer server, Spec spec, String message) {
        DIAGNOSTICS.computeIfAbsent(server, ignored -> new LinkedHashMap<>()).put(spec.id(), message);
    }
    private static void refuse(MinecraftServer server, Spec spec, String message) {
        diagnose(server, spec, message);
        XenoPixelsMod.LOGGER.warn("{}", message);
    }
    private static Map<String, UUID> ids(ServerLevel level) {
        return IDS.computeIfAbsent(level, ignored -> new HashMap<>());
    }
    private static String bodyIdOf(ServerSubLevel body) {
        String id = body.getUserDataTag().getString(TAG + "_body");
        return id.isEmpty() ? BODY_ID : id;
    }

    private static void initialize(ServerLevel level, Spec spec) { initialize(level, spec, true); }

    /** {@code create=false} only adopts a saved body; proximity mode builds the rest on demand. */
    private static void initialize(ServerLevel level, Spec spec, boolean create) {
        var container = SubLevelContainer.getContainer(level);
        Ledger ledger = Ledger.get(level, spec.ledgerName());
        UUID tagged = null;
        // Only the startup recovery path scans. Every tick/player lookup below uses the UUID index.
        for (var body : container.getAllSubLevels()) if (isManaged(body) && spec.id().equals(bodyIdOf(body))) {
            if (tagged != null) throw new IllegalStateException("multiple tagged " + spec.displayName() + " bodies exist; inspect saved world");
            tagged = body.getUniqueId();
        }
        if (tagged != null) {
            if (ledger.id != null && !ledger.id.equals(tagged))
                throw new IllegalStateException(spec.displayName() + " ledger and loaded body UUID disagree; no body replaced");
            ledger.record(tagged);
            ids(level).put(spec.id(), tagged);
            var body = getBody(level, spec.id());
            container.addForceLoadTicket(body, spec.ticket(), spec.id());
            migrateShell(level, spec, body);
            savePhysicalIdentity(level, body);
            diagnose(level.getServer(), spec, "Reused " + spec.displayName() + " " + tagged);
            return;
        }
        UUID lost = null;
        if (ledger.id != null && XenoSpaceConfig.planetRecoverLost()) {
            // Sable restores force-loaded bodies inside ServerSubLevelContainer.initialize, before this runs. A ledgered
            // body that is still absent was not restored (S11: a crash mid-autosave left Earth's holding record empty and
            // Sable logged "Attempted to snatch ... wasn't present"), so waiting would never end.
            XenoPixelsMod.LOGGER.error("Saved {} {} was not restored by Sable; clearing its ledger and rebuilding it",
                    spec.displayName(), ledger.id);
            lost = ledger.id;
            ledger.id = null; ledger.attempted = false; ledger.setDirty();
        }
        final UUID lostId = lost;
        if (ledger.id != null) {
            // Sable owns restoring force-loaded bodies. A saved identity is never permission to create another.
            ids(level).put(spec.id(), ledger.id);
            diagnose(level.getServer(), spec, "Waiting for saved " + spec.displayName() + " " + ledger.id + "; replacement creation disabled");
            return;
        }
        if (!create) {
            diagnose(level.getServer(), spec, spec.displayName() + " is built when a player comes within "
                    + (long) (XenoSpaceConfig.proximityActivateRange() + spec.radius()) + " blocks");
            return;
        }
        // The lost body's own stale ticket cannot be removed while it is not loaded (Codex C-044) and must not block
        // its replacement; any other persistent ticket for this body id still does.
        if (ledger.attempted || container.getAllTickets().entrySet().stream()
                .filter(entry -> !entry.getKey().equals(lostId)).map(java.util.Map.Entry::getValue)
                .anyMatch(info -> info.tickets().stream().anyMatch(ticket -> ticket.getType().equals(spec.ticket())
                        && spec.id().equals(ticket.getKey()))))
            throw new IllegalStateException("an earlier assembly or persistent " + spec.displayName() + " ticket exists; inspect before retrying");
        boolean atomic = XenoSpaceConfig.atomicPlanetAssembly();
        if (!atomic && !level.players().isEmpty()) throw new IllegalStateException("players are present in Voidsea during initialization");
        BlockPos center = stagingCenter(level, spec);
        int radius = spec.radius();
        int pad = radius + 1;
        if (center.getY() - pad < level.getMinBuildHeight() || center.getY() + pad >= level.getMaxBuildHeight())
            throw new IllegalStateException("Voidsea build volume cannot contain the " + spec.displayName() + " shell");
        AABB area = new AABB(center.getX()-pad,center.getY()-pad,center.getZ()-pad,
                center.getX()+pad+1,center.getY()+pad+1,center.getZ()+pad+1);
        // A body spawned inside another one is flung apart by the solver, on either path. A batched build with
        // planetBuildEvictIntruders moves ordinary bodies out of the shell band itself before it completes (owner
        // decision: move the ship out), so only another planet blocks it; otherwise a ship parked in a corner of the
        // planet's box, clear of the shell, kept the planet from being rebuilt (S11 ship-at-Sol restart test, CL-069).
        boolean evicts = atomic && onDemand(spec) && XenoSpaceConfig.planetBuildVoxelsPerTick() > 0
                && XenoSpaceConfig.planetBuildEvictIntruders();
        requireNoBody(container, area, spec, "another", evicts);
        long started = System.nanoTime();
        BlockState shell = ModBlocks.PLANET_SHELL.get().defaultBlockState();
        ServerSubLevel body;
        int shellVoxels;
        double fillMillis = 0, installMillis = 0, maxServerSliceMillis = 0;
        Vector3d requested = new Vector3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
        if (PlotBodyBuilder.shouldBatchAssembly(atomic, XenoSpaceConfig.planetBuildVoxelsPerTick())) {
            // Permanent anchors (including Earth) and proximity activations use the same bounded tick job.
            // A positive voxel budget prevents the 186k-voxel Earth shell from loading synchronously at startup.
            BUILDS.computeIfAbsent(level.getServer(), ignored -> new LinkedHashMap<>()).put(spec.id(),
                    new Build(spec, PlotBodyBuilder.start(level, requested, radius, PlotBodyBuilder.shell(radius, shell),
                                    XenoSpaceConfig.planetBuildDeferFinalize())
                                    .withoutFrameFirst(),
                            requested.y, started));
            return;
        }
        if (atomic) {
            // Built in plot space and installed whole: nothing is placed in the world, so neither players
            // nor entities in the staging volume matter, and a failed build removes its own plot (no
            // attempted marker needed; it may be retried).
            var built = PlotBodyBuilder.build(level, requested, radius, PlotBodyBuilder.shell(radius, shell));
            if (built == null) throw new IllegalStateException("no free Sable plot for the " + spec.displayName());
            body = built.body();
            shellVoxels = built.blocks();
            fillMillis = built.fillMillis();
            installMillis = built.installMillis();
            maxServerSliceMillis = built.installMillis();
            XenoPixelsMod.LOGGER.info("Physical {} assembled atomically: voxels={}, fill ms={}, install ms={}, pose={}",
                    spec.displayName(), shellVoxels, String.format("%.1f", built.fillMillis()),
                    String.format("%.1f", built.installMillis()), body.logicalPose());
        } else {
        if (!level.getEntities(null, area).isEmpty()) throw new IllegalStateException("entities occupy " + spec.displayName() + " staging volume");
        // Load just the finite staging footprint; no world chunk tickets are retained.
        for (int cx=(center.getX()-pad)>>4;cx<=(center.getX()+pad)>>4;cx++)
            for(int cz=(center.getZ()-pad)>>4;cz<=(center.getZ()+pad)>>4;cz++) level.getChunk(cx,cz);
        if (!level.getEntities(null,area).isEmpty()) throw new IllegalStateException("loaded entities occupy " + spec.displayName() + " staging volume");
        requireNoBody(container, area, spec, "loaded");
        BlockPos.MutableBlockPos scan = new BlockPos.MutableBlockPos();
        for (int x=-pad;x<=pad;x++) for(int z=-pad;z<=pad;z++) {
            var chunk=level.getChunk((center.getX()+x)>>4,(center.getZ()+z)>>4);
            for(int y=-pad;y<=pad;y++) {
                scan.set(center.getX()+x,center.getY()+y,center.getZ()+z);
                if (!chunk.getBlockState(scan).isAir()) throw new IllegalStateException("occupied " + spec.displayName() + " staging block " + scan);
            }
        }
        var blocks = new ArrayList<BlockPos>();
        for(int x=-radius;x<=radius;x++) for(int y=-radius;y<=radius;y++) for(int z=-radius;z<=radius;z++)
            if(PlanetShell.contains(x,y,z,radius)) blocks.add(center.offset(x,y,z));
        // Persist the attempted marker before mutation. Partial assembly is deliberately not retried or erased.
        ledger.attempted = true;
        ledger.setDirty();
        level.getDataStorage().save();
        for(var pos:blocks) if (!level.setBlock(pos,shell,2))
            throw new IllegalStateException("could not place shell voxel at " + pos);
        body = SubLevelAssemblyHelper.assembleBlocks(level,center,blocks,
                new BoundingBox3i(center.offset(-radius,-radius,-radius),center.offset(radius,radius,radius)));
        shellVoxels = blocks.size();
        installMillis = (System.nanoTime() - started) / 1_000_000.0;
        maxServerSliceMillis = installMillis;
        }
        register(level, spec, body, shellVoxels, body.logicalPose().position().y(), started, fillMillis, installMillis, maxServerSliceMillis);
    }

    private static final Map<MinecraftServer, java.util.Set<UUID>> ORPHAN_CHECKED = new IdentityHashMap<>();

    /**
     * Guard for planetRecoverLost (Codex C-051): Sable can restore a stored record later than ServerStarted
     * (ServerSubLevelContainer.tick -> SubLevelHoldingChunkMap.processChanges -> loadHoldingSubLevel), after a lost body
     * was already rebuilt under a new UUID. A managed body whose planet id is already owned by a different UUID in the
     * index is such a late duplicate and is removed.
     */
    private static int removeLateDuplicates(ServerLevel level) {
        var container = SubLevelContainer.getContainer(level);
        var index = ids(level);
        int removed = 0;
        for (var body : new ArrayList<>(container.getAllSubLevels())) {
            if (body.isRemoved() || !isManaged(body)) continue;
            UUID owner = index.get(bodyIdOf(body));
            if (owner == null || owner.equals(body.getUniqueId())) continue;
            XenoPixelsMod.LOGGER.error("Removing late duplicate of {}: {} (the index owner is {})", bodyIdOf(body), body.getUniqueId(), owner);
            container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            removed++;
        }
        return removed;
    }

    /** For SablePlanetEphemeralSaveMixin: an on-demand chart planet that is rebuilt from the chart instead of stored. */
    public static boolean isEphemeralPlanet(ServerSubLevel body) {
        if (!XenoSpaceConfig.planetEphemeral() || body == null || !isManaged(body)) return false;
        String id = bodyIdOf(body);
        for (Spec spec : SPECS) if (spec.id().equals(id)) return onDemand(spec);
        return false;
    }

    /** For SablePlanetEphemeralSaveMixin: the dependency list without ephemeral planets (they are never stored). */
    public static List<UUID> withoutEphemeralPlanets(ServerLevel level, List<UUID> dependencies) {
        if (!XenoSpaceConfig.planetEphemeral() || dependencies == null || dependencies.isEmpty()) return dependencies;
        var container = SubLevelContainer.getContainer(level);
        List<UUID> kept = null;
        for (int i = 0; i < dependencies.size(); i++) {
            UUID id = dependencies.get(i);
            boolean drop = container.getSubLevel(id) instanceof ServerSubLevel body && isEphemeralPlanet(body);
            if (drop && kept == null) kept = new ArrayList<>(dependencies.subList(0, i));
            else if (!drop && kept != null) kept.add(id);
        }
        return kept == null ? dependencies : kept;
    }

    /**
     * Reconstructible planets, clean stop (S3): ServerStoppingEvent is posted before MinecraftServer.stopServer saves and
     * closes the levels (Codex C-042), so ephemeral planets are removed here; REMOVED also prunes their force-load
     * ticket, leaving nothing stored for them.
     */
    @SubscribeEvent public static void releaseEphemeralPlanets(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        if (!XenoSpaceConfig.planetEphemeral()) return;
        ServerLevel level = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, HOST));
        if (level == null) return;
        for (Spec spec : SPECS) {
            if (!onDemand(spec)) continue;
            cancelBuild(event.getServer(), spec);
            ServerSubLevel body = getBody(level, spec.id());
            if (body != null && !body.isRemoved())
                release(level, spec.id(), spec.ticket(), spec.ledgerName(), body, "server stopping (ephemeral planet)");
        }
    }

    /** S11 duplicate-guard test (debug only): tags an existing sub-level as a managed body with the given planet id. */
    public static String debugTagManaged(ServerLevel level, UUID uuid, String bodyId) {
        var sub = SubLevelContainer.getContainer(level).getSubLevel(uuid);
        if (!(sub instanceof ServerSubLevel body)) return "no sub-level " + uuid;
        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
        body.getUserDataTag().putBoolean(TAG, true);
        body.getUserDataTag().putString(TAG + "_body", bodyId);
        String report = "tagged " + uuid + " as managed " + bodyId;
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    /** S11 proof probe (debug only): the half-built body of an in-progress batched build, or null. */
    public static ServerSubLevel debugBuildingBody(MinecraftServer server, String bodyId) {
        var builds = BUILDS.get(server);
        Build build = builds == null ? null : builds.get(bodyId);
        return build == null ? null : build.job().body();
    }

    /** For SableBuildingBodyMassSkipMixin: true for the body of an unfinished batched build while its finalize is deferred. */
    public static boolean defersBuildMass(ServerSubLevel body) {
        if (body == null) return false;
        var builds = BUILDS.get(body.getLevel().getServer());
        if (builds == null) return false;
        for (Build build : builds.values()) {
            if (build.job().body() == body
                    && BuildMassDeferralPolicy.deferPhysicsTickMass(
                    build.job().deferFinalizeEnabled(), build.job().incrementalSections())) return true;
        }
        return false;
    }

    /** For SablePlanetChunkTicketSkipMixin: true for managed planet shells and bodies of an in-progress batched build. */
    public static boolean skipsChunkTickets(ServerSubLevel body) {
        if (net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer.manages(body)) return true;
        if (!XenoSpaceConfig.planetSkipChunkTickets() || body == null) return false;
        if (isManaged(body)) return true;
        var builds = BUILDS.get(body.getLevel().getServer());
        if (builds == null) return false;
        for (Build build : builds.values()) if (build.job().body() == body) return true;
        return false;
    }

    /**
     * For SablePlanetHeatMapSkipMixin: true for managed planet shells and bodies of an in-progress batched build.
     * The mixin latches a true answer per heat-map manager, so this is only asked until it first says yes. Called
     * per voxel while a body loads, so the switch is checked first and nothing here locks.
     */
    public static boolean skipsSplitHeatMap(ServerSubLevel body) {
        if (net.bullettrain.xenopixelsmod.space.asteroid.AsteroidPhysicalizer.manages(body)) return true;
        if (!XenoSpaceConfig.planetSkipSplitHeatMap() || body == null) return false;
        if (isManaged(body)) return true;
        var builds = BUILDS.get(body.getLevel().getServer());
        if (builds == null) return false;
        for (Build build : builds.values()) if (build.job().body() == body) return true;
        return false;
    }

    /**
     * Removes orphaned planet-shell fragments: Voidsea sub-levels that are NOT managed and contain nothing but
     * {@code planet_shell} blocks. Found in the S11 proof world: 26+ untagged bodies of exactly 6269 shell voxels
     * inside the Sun's volume, left by an earlier batched build that Sable split. A rebuilt Sun overlapping them is
     * the leading suspect for the native-step stalls. {@code planet_shell} has no item, so no player-built ship can
     * consist of it. Each body is checked once per server run; Sable may load more later, so this runs periodically.
     */
    private static int removeOrphanShellFragments(ServerLevel level) {
        if (!XenoSpaceConfig.planetOrphanCleanup()) return 0;
        var container = SubLevelContainer.getContainer(level);
        var checked = ORPHAN_CHECKED.computeIfAbsent(level.getServer(), ignored -> new java.util.HashSet<>());
        var shell = ModBlocks.PLANET_SHELL.get();
        // A batched build's half-built body is not tagged until it completes; it is not an orphan (S11 batch test:
        // the cleanup removed the Sun mid-build, the build failed and restarted).
        var building = new java.util.HashSet<UUID>();
        var builds = BUILDS.get(level.getServer());
        if (builds != null) for (Build build : builds.values()) {
            ServerSubLevel partial = build.job().body();
            if (partial != null) building.add(partial.getUniqueId());
        }
        int removed = 0;
        for (var body : new ArrayList<>(container.getAllSubLevels())) {
            if (body.isRemoved() || isManaged(body) || building.contains(body.getUniqueId())) continue;
            if (!checked.add(body.getUniqueId())) continue;
            var box = body.getPlot().getBoundingBox();
            if (box == null) continue;
            long volume = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
            if (volume <= 0 || volume > 64_000_000L) continue;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int shellVoxels = 0;
            boolean other = false;
            for (int x = box.minX(); x <= box.maxX() && !other; x++)
                for (int y = box.minY(); y <= box.maxY() && !other; y++)
                    for (int z = box.minZ(); z <= box.maxZ(); z++) {
                        var state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir()) continue;
                        if (state.is(shell)) shellVoxels++;
                        else { other = true; break; }
                    }
            if (other || shellVoxels == 0) continue;
            var world = body.boundingBox();
            XenoPixelsMod.LOGGER.warn("Removing orphaned planet-shell fragment: uuid={}, shell voxels={}, mass={}, world bounds=[{} {} {} .. {} {} {}]",
                    body.getUniqueId(), shellVoxels, body.getMassTracker().getMass(),
                    (long) world.minX(), (long) world.minY(), (long) world.minZ(), (long) world.maxX(), (long) world.maxY(), (long) world.maxZ());
            container.removeSubLevel(body, dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason.REMOVED);
            removed++;
        }
        return removed;
    }

    /**
     * S11 experiment E9-lite (debug only, never called by gameplay): creates ONE unmanaged planet_shell body that is the
     * top cap ({@code y >= radius - capRows}) of the given managed body's shell, placed exactly on that body's live centre
     * so its voxels coincide with the planet's own. Untagged on purpose, like the orphaned fragments found in the proof
     * world; kept loaded with a debug ticket so Sable does not drop it. Returns a one-line report.
     */
    public static String debugSpawnOverlappingCap(ServerLevel level, String bodyId, int capRows) {
        return debugSpawnOverlappingCap(level, bodyId, capRows, 0, 0, 0);
    }

    /**
     * S11 experiment BO-5 (debug only): the same cap as {@link #debugSpawnOverlappingCap(ServerLevel, String, int)},
     * shifted by (dx, dy, dz) blocks so it can be placed clear of the planet (same body, no contact).
     */
    /**
     * S11 build-contact test (debug only): the same cap as {@link #debugSpawnOverlappingCap(ServerLevel, String, int)}
     * (shape from the body's spec radius) placed at an absolute world centre, so it can exist while that body is still
     * being built and is not yet managed.
     */
    public static String debugSpawnCapAt(ServerLevel level, String bodyId, int capRows, Vector3d centre) {
        return debugSpawnCapAt(level, bodyId, capRows, centre, ModBlocks.PLANET_SHELL.get().defaultBlockState());
    }

    /** S11 ship-at-planet restart test (debug only): the same cap built from an ordinary block, so orphan cleanup keeps it. */
    public static String debugSpawnCapAt(ServerLevel level, String bodyId, int capRows, Vector3d centre,
                                         net.minecraft.world.level.block.state.BlockState state) {
        Spec spec = SPECS.stream().filter(s -> s.id().equals(bodyId)).findFirst().orElse(null);
        if (spec == null) return "no spec " + bodyId;
        int radius = spec.radius();
        PlotBodyBuilder.Voxels cap = (x, y, z) -> y >= radius - capRows && PlanetShell.contains(x, y, z, radius) ? state : null;
        var built = PlotBodyBuilder.build(level, centre, radius, cap);
        if (built == null) return "no free plot";
        SubLevelContainer.getContainer(level).addForceLoadTicket(built.body(), PLANET_TICKET, "debug_overlap_" + built.body().getUniqueId());
        String report = "build-contact cap: uuid=" + built.body().getUniqueId() + " voxels=" + built.blocks() + " centre=" + centre;
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    public static String debugSpawnOverlappingCap(ServerLevel level, String bodyId, int capRows, double dx, double dy, double dz) {
        Spec spec = SPECS.stream().filter(s -> s.id().equals(bodyId)).findFirst().orElse(null);
        ServerSubLevel planet = getBody(level, bodyId);
        if (spec == null || planet == null) return "no loaded managed body " + bodyId;
        int radius = spec.radius();
        var state = ModBlocks.PLANET_SHELL.get().defaultBlockState();
        PlotBodyBuilder.Voxels cap = (x, y, z) -> y >= radius - capRows && PlanetShell.contains(x, y, z, radius) ? state : null;
        // Sable anchors a new body's CENTRE OF MASS at the requested point (seen in the first E9-lite run: the cap landed
        // at the planet's centre, inside the hollow shell). Offset by the cap's own voxel centroid so its voxels coincide
        // with the planet's shell voxels.
        double sx = 0, sy = 0, sz = 0;
        int n = 0;
        for (int x = -radius; x <= radius; x++) for (int y = radius - capRows; y <= radius; y++) for (int z = -radius; z <= radius; z++)
            if (PlanetShell.contains(x, y, z, radius)) { sx += x + 0.5; sy += y + 0.5; sz += z + 0.5; n++; }
        if (n == 0) return "empty cap";
        var centre = new Vector3d(planet.logicalPose().position()).add(sx / n - 0.5 + dx, sy / n - 0.5 + dy, sz / n - 0.5 + dz);
        var built = PlotBodyBuilder.build(level, centre, radius, cap);
        if (built == null) return "no free plot";
        SubLevelContainer.getContainer(level).addForceLoadTicket(built.body(), PLANET_TICKET, "debug_overlap_" + built.body().getUniqueId());
        String report = "E9-lite overlapping cap: uuid=" + built.body().getUniqueId() + " voxels=" + built.blocks()
                + " capRows=" + capRows + " onto " + bodyId + " offset=(" + dx + "," + dy + "," + dz + ") centre=" + centre;
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    /**
     * S11 experiment E10b (debug only): sets Sable's physics substeps for this level and pushes the config to the
     * native pipeline. Uses only javap-verified 2.0.3 API: ServerSubLevelContainer.physicsSystem(),
     * SubLevelPhysicsSystem.getConfig() (public PhysicsConfigData fields), PhysicsPipeline.updateConfigFrom(...).
     */
    public static String debugSetSubsteps(ServerLevel level, int substeps) {
        var system = SubLevelContainer.getContainer(level).physicsSystem();
        if (system == null) return "no physics system";
        var config = system.getConfig();
        int before = config.substepsPerTick;
        config.substepsPerTick = Math.max(1, substeps);
        system.getPipeline().updateConfigFrom(config);
        String report = "E10b substepsPerTick " + before + " -> " + config.substepsPerTick
                + " (solverIterations=" + config.solverIterations + ", minDynamicBodiesPerIsland=" + config.minDynamicBodiesPerIsland + ")";
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    /** A batched on-demand build in progress. */
    private record Build(Spec spec, PlotBodyBuilder.Job job, double planeY, long started) {}
    private static final Map<MinecraftServer, Map<String, Build>> BUILDS = new IdentityHashMap<>();

    /** Advances batched builds within the per-tick voxel budget; registers each body when complete. */
    private static void stepBuilds(MinecraftServer server, ServerLevel level) {
        var builds = BUILDS.get(server);
        if (builds == null || builds.isEmpty()) return;
        int budget = XenoSpaceConfig.planetBuildVoxelsPerTick();
        for (var iterator = builds.values().iterator(); iterator.hasNext() && budget > 0; ) {
            Build build = iterator.next();
            int before = build.job().installed();
            try {
                long tick = server.overworld().getGameTime();
                if (build.job().body() == null && !SpaceBuildWorkBudget.mayPrepare(server, tick,
                        SpaceBuildWorkBudget.Owner.CHART_PLANET, budget)) continue;
                if (!build.job().prepare()) {
                    if (build.job().failed()) {
                        iterator.remove();
                        XenoPixelsMod.LOGGER.warn("Physical {} batched build stopped: no free Sable plot", build.spec().displayName());
                    }
                    continue;
                }
                // Preparation allocates the empty plot and computes its exact indivisible plot.load group size.
                // Ticket it before a possible budget deferral so Sable cannot unload it before the next tick.
                ServerSubLevel partial = build.job().body();
                if (partial != null && !partial.isRemoved())
                    SubLevelContainer.getContainer(level).addForceLoadTicket(partial, build.spec().ticket(), build.spec().id());
                int stepBudget = SpaceBuildWorkBudget.allowance(server, tick,
                        SpaceBuildWorkBudget.Owner.CHART_PLANET, build.job(), budget);
                if (stepBudget <= 0) continue;
                double millisBudget = SpaceBuildWorkBudget.remainingMillis(server, tick, SpaceBuildWorkBudget.Owner.CHART_PLANET);
                if (XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0 && millisBudget <= 0.0) continue;
                long sliceStarted = System.nanoTime();
                boolean complete = build.job().step(stepBudget, millisBudget);
                SpaceBuildWorkBudget.recordMillis(server, tick, SpaceBuildWorkBudget.Owner.CHART_PLANET,
                        (System.nanoTime() - sliceStarted) / 1e6);
                if (complete) {
                    iterator.remove();
                    var result = build.job().result();
                    XenoPixelsMod.LOGGER.info("Physical {} assembled in batches: voxels={}, fill ms={} (off-thread), install+prepare ms={} in {} batches, slowest server-thread slice ms={}, pose={}",
                            build.spec().displayName(), result.blocks(), String.format("%.1f", result.fillMillis()),
                            String.format("%.1f", result.installMillis()), build.job().batches(),
                            String.format("%.1f", build.job().maxServerSliceMillis()), result.body().logicalPose());
                    register(level, build.spec(), result.body(), result.blocks(), build.planeY(), build.started(),
                            result.fillMillis(), result.installMillis(), build.job().maxServerSliceMillis());
                    XenoPixelsMod.LOGGER.info("Physical {} build completed ({})", build.spec().displayName(),
                            onDemand(build.spec()) ? "proximity activation" : "persistent anchor");
                    if (onDemand(build.spec()))
                        PROXIMITY_STATS.computeIfAbsent(server, ignored -> new int[2])[0]++;
                    net.bullettrain.xenopixelsmod.space.SpaceVisualSync.resendPlanetBindings(level);
                } else if (build.job().failed()) {
                    iterator.remove();
                    XenoPixelsMod.LOGGER.warn("Physical {} batched build stopped: no free Sable plot", build.spec().displayName());
                }
            } catch (RuntimeException error) {
                iterator.remove();
                build.job().cancel();
                XenoPixelsMod.LOGGER.error("Physical {} batched build failed; partial body removed", build.spec().displayName(), error);
            }
            int installed = build.job().installed() - before;
            SpaceBuildWorkBudget.record(server, server.overworld().getGameTime(),
                    SpaceBuildWorkBudget.Owner.CHART_PLANET, installed);
            budget -= installed;
        }
    }

    /** Stops a batched build whose players left; the half-built plot is removed. */
    private static boolean cancelBuild(MinecraftServer server, Spec spec) {
        var builds = BUILDS.get(server);
        Build build = builds == null ? null : builds.remove(spec.id());
        if (build == null) return false;
        ServerSubLevel partial = build.job().body();
        if (partial != null && !partial.isRemoved())
            SubLevelContainer.getContainer(partial.getLevel()).removeForceLoadTicket(partial, spec.ticket(), spec.id());
        build.job().cancel();
        XenoPixelsMod.LOGGER.info("Physical {} build cancelled: no player near any more", spec.displayName());
        return true;
    }
    private static boolean building(MinecraftServer server, Spec spec) {
        var builds = BUILDS.get(server);
        return builds != null && builds.containsKey(spec.id());
    }

    private static void register(ServerLevel level, Spec spec, ServerSubLevel body, int shellVoxels, double planeY, long started,
                                 double fillMillis, double installMillis, double maxServerSliceMillis) {
        var container = SubLevelContainer.getContainer(level);
        Ledger ledger = Ledger.get(level, spec.ledgerName());
        int radius = spec.radius();
        if(body.getUserDataTag()==null) body.setUserDataTag(new CompoundTag());
        body.getUserDataTag().putBoolean(TAG,true);
        body.getUserDataTag().putString(TAG+"_body",spec.id());
        body.getUserDataTag().putInt(TAG+"_radius",radius);
        body.getUserDataTag().putInt(TAG+"_voxels",shellVoxels);
        body.getUserDataTag().putBoolean(SHELL_V2,true);
        // The shell is staged inside the build volume; a root body's orbital plane is that physical Y.
        body.getUserDataTag().putDouble(TAG+"_plane_y",planeY);
        body.setName(spec.displayName() + " — XenoPixels managed planet");
        ids(level).put(spec.id(),body.getUniqueId());
        ledger.record(body.getUniqueId());
        container.addForceLoadTicket(body,spec.ticket(),spec.id());
        // A permanent body's identity is flushed to disk at once. An on-demand body is rebuilt identically on
        // the next approach, so it skips the forced full save of every Sable body (a server-thread stall).
        if (!onDemand(spec)) savePhysicalIdentity(level,body);
        double millis=(System.nanoTime()-started)/1_000_000.0;
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordBuild(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.CHART_PLANET, System.nanoTime(), shellVoxels,
                fillMillis, installMillis, maxServerSliceMillis);
        diagnose(level.getServer(),spec,spec.displayName()+" "+body.getUniqueId()+", r="+radius+", shell="+shellVoxels+", startupMs="+millis);
        XenoPixelsMod.LOGGER.info("Physical {} created: UUID={}, shell voxels={}, radius={}, pose={}, mass={}, startup ms={}",
                spec.displayName(),body.getUniqueId(),shellVoxels,radius,body.logicalPose(),body.getMassTracker().getMass(),millis);
    }

    /**
     * Earth stays at its historical staging block. A child body is staged at its parent's live centre plus
     * its charted offset, so the Moon starts on its station rather than being dragged there.
     */
    private static BlockPos stagingCenter(ServerLevel level, Spec spec) {
        if (spec == EARTH) return new BlockPos(1000, 128, 0);
        var chart = SpaceChartService.state(level.getServer()).runtime.chart;
        CelestialBody row = chart.byId(spec.id());
        if (row == null) throw new IllegalStateException(spec.displayName() + " is not charted");
        // A moon is staged on its loaded physical parent. A planet whose parent (the Sun) is not physical, or
        // not built, starts at its chart position and the orbit controller handles the rest.
        Vector3dc parent = row.parentId() == null ? null : liveCenter(level, row.parentId());
        if (parent != null && row.kind() == CelestialBody.BodyKind.MOON)
            return BlockPos.containing(parent.x() + row.pinnedX(), parent.y() + row.pinnedY(), parent.z() + row.pinnedZ());
        if (parent == null && row.kind() == CelestialBody.BodyKind.MOON
                && SPECS.stream().anyMatch(other -> other.id().equals(row.parentId())))
            throw new IllegalStateException(spec.displayName() + " needs its physical parent loaded first");
        // A root (or the child of a body that is not physical) starts where the chart has it now, on the
        // same 128 plane as Earth unless its shell needs more room below.
        int slot = chart.slotOf(spec.id());
        int y = Math.max(128, level.getMinBuildHeight() + spec.radius() + 2);
        return BlockPos.containing(chart.x(slot), y, chart.z(slot));
    }

    private static void requireNoBody(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container, AABB area, Spec spec, String which) {
        requireNoBody(container, area, spec, which, false);
    }

    private static void requireNoBody(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container, AABB area, Spec spec,
                                      String which, boolean planetsOnly) {
        for (var other : container.getAllSubLevels()) {
            if (planetsOnly && !isManaged(other)) continue;
            var box = other.boundingBox();
            if (area.intersects(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ()))
                throw new IllegalStateException(which + " Sable body occupies " + spec.displayName() + " staging volume");
        }
    }

    /**
     * One-time swap of a bedrock-assembled shell to {@code planet_shell}: removes the visible voxels that
     * pierced the HD sphere and replaces Sable's 1000-per-bedrock mass with the shell block's datapack mass.
     * One block at a time, so the body's total mass stays positive throughout.
     */
    private static void migrateShell(ServerLevel level, Spec spec, ServerSubLevel body) {
        if (body == null || body.getUserDataTag().getBoolean(SHELL_V2)) return;
        double before = body.getMassTracker().getMass();
        var box = body.getPlot().getBoundingBox();
        BlockState shell = ModBlocks.PLANET_SHELL.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int swapped = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
            for (int y = box.minY(); y <= box.maxY(); y++)
                for (int z = box.minZ(); z <= box.maxZ(); z++) {
                    pos.set(x, y, z);
                    if (level.getBlockState(pos).is(Blocks.BEDROCK) && level.setBlock(pos, shell, 2 | 16)) swapped++;
                }
        body.getUserDataTag().putBoolean(SHELL_V2, true);
        XenoPixelsMod.LOGGER.info("Physical {} shell migrated to planet_shell: UUID={}, voxels={}, mass {} -> {}",
                spec.displayName(), body.getUniqueId(), swapped, before, body.getMassTracker().getMass());
    }

    /** Sable must assign and flush its real storage pointer before the ticket SavedData is written. */
    private static void savePhysicalIdentity(ServerLevel level,ServerSubLevel body) {
        SubLevelContainer.getContainer(level).getHoldingChunkMap().saveAll();
        if(body.getLastSerializationPointer()==null)
            throw new IllegalStateException("Sable did not assign a saved planet pointer; body preserved for inspection");
        level.getDataStorage().save();
        XenoPixelsMod.LOGGER.info("Physical planet durable identity saved: UUID={}, pointer={}",
                body.getUniqueId(),body.getLastSerializationPointer());
    }

    public static boolean isManaged(ServerSubLevel body) {
        return body.getUserDataTag()!=null && body.getUserDataTag().getBoolean(TAG);
    }
    public static ServerSubLevel getBody(ServerLevel level, String bodyId) {
        var map=IDS.get(level);
        UUID id=map==null?null:map.get(bodyId);
        if(id==null) return null;
        var body=SubLevelContainer.getContainer(level).getSubLevel(id);
        return body instanceof ServerSubLevel serverBody && !serverBody.isRemoved() && isManaged(serverBody) ? serverBody:null;
    }
    public static ServerSubLevel getBody(ServerLevel level) { return getBody(level, BODY_ID); }
    /** Loaded managed bodies in this level by chart id, in creation order. */
    public static Map<String, ServerSubLevel> bodies(ServerLevel level) {
        var out = new LinkedHashMap<String, ServerSubLevel>();
        for (Spec spec : SPECS) { var body = getBody(level, spec.id()); if (body != null) out.put(spec.id(), body); }
        return out;
    }
    /** Shared optional physics-staff unlock for other managed body systems outside this package. */
    public static boolean releaseStaffLock(ServerLevel level, ServerSubLevel body) {
        return level != null && body != null && StaffLocks.release(level, body);
    }
    public static UUID loadedBodyId(ServerLevel level) { var body=getBody(level); return body==null?null:body.getUniqueId(); }
    public static Vector3dc liveCenter(ServerLevel level, String bodyId) {
        var body=getBody(level, bodyId); return body==null?null:body.logicalPose().position();
    }
    public static Vector3dc liveCenter(ServerLevel level) { return liveCenter(level, BODY_ID); }
    /** Shell radius of a loaded managed body, or 0 when it is not physical here. */
    public static double liveRadius(ServerLevel level, String bodyId) {
        if (getBody(level, bodyId) == null) return 0;
        for (Spec spec : SPECS) if (spec.id().equals(bodyId)) return spec.radius();
        return 0;
    }
    public static double liveRadius(ServerLevel level) { return liveRadius(level, BODY_ID); }

    /** The exact Sable tracking caller passes the body's pose.position object, not a copied vector. */
    public static boolean extendsNativeTracking(ServerLevel level,net.minecraft.world.entity.player.Player player,
                                                Vector3dc candidateCentre) {
        if(player.level()!=level || !HOST.equals(level.dimension().location())) return false;
        for (var body : bodies(level).values()) {
            if (body.logicalPose().position() == candidateCentre)
                return PlanetTrackingRange.contains(player.getX(),player.getY(),player.getZ(),
                        candidateCentre.x(),candidateCentre.y(),candidateCentre.z());
        }
        return false;
    }

    public static String status(MinecraftServer server) {
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,HOST));
        var notes=DIAGNOSTICS.getOrDefault(server, Map.of());
        StringBuilder out=new StringBuilder();
        int activeBodies = 0, activeVoxels = 0, unknownVoxelBodies = 0;
        for (Spec spec : SPECS) {
            if(out.length()>0) out.append(" | ");
            String diagnostic=notes.getOrDefault(spec.id(),"Physical "+spec.displayName()+" not initialized (enable space and restart)");
            var body=level==null?null:getBody(level,spec.id());
            out.append(diagnostic);
            if(body!=null) {
                activeBodies++;
                if (body.getUserDataTag() != null && body.getUserDataTag().contains(TAG+"_voxels"))
                    activeVoxels += Math.max(0, body.getUserDataTag().getInt(TAG+"_voxels"));
                else unknownVoxelBodies++;
                Track track=PASS.tracks.get(body.getUniqueId());
                out.append(" position=").append(body.logicalPose().position())
                        .append(" mass=").append(body.getMassTracker().getMass())
                        .append(" shellV2=").append(body.getUserDataTag().getBoolean(SHELL_V2));
                if(track!=null) out.append(String.format(" velocity=(%.3f %.3f %.3f) holdError=%.2f correction=%.3f %s outside=%.3f",
                        track.velocity[0],track.velocity[1],track.velocity[2],track.lastError,track.lastCorrection,
                        track.held?"held":"released",track.release.outside()));
                if(StaffLocks.isLocked(level,body)) out.append(" staffLocked");
            }
        }
        out.insert(0, "chartPhysical active=" + activeBodies + " voxels=" + activeVoxels
                + " unknownVoxelBodies=" + unknownVoxelBodies + " | ");
        return out.toString();
    }

    /**
     * Debug/test push: adds {@code velocity} (blocks/s, world axes) to a managed body as one impulse, so
     * release-return can be observed without a ship. Returns false when the body or its handle is absent.
     */
    public static boolean push(ServerLevel level, String bodyId, Vector3dc velocity) {
        var body = getBody(level, bodyId);
        if (body == null) return false;
        var handle = SubLevelContainer.getContainer(level).physicsSystem().getPhysicsHandle(body);
        if (handle == null || !handle.isValid()) return false;
        Vector3d kick = new Vector3d(velocity).mul(body.getMassTracker().getMass());
        body.logicalPose().orientation().transformInverse(kick);
        handle.applyLinearImpulse(kick);
        return true;
    }

    /** Called before chart evaluation, so descendants and handoff see physics rather than analytic orbits. */
    public static void publishPosition(MinecraftServer server, ChartRegistry chart) {
        ServerLevel level=server.getLevel(ResourceKey.create(Registries.DIMENSION,HOST));
        if(level==null) return;
        for (var entry : bodies(level).entrySet()) {
            if(chart.slotOf(entry.getKey())<0) continue;
            var pos=entry.getValue().logicalPose().position();
            if(Double.isFinite(pos.x())&&Double.isFinite(pos.y())&&Double.isFinite(pos.z()))
                chart.moveBody(entry.getKey(),pos.x(),pos.y(),pos.z());
        }
    }

    @Override public String name() { return "physical-planets"; }
    @Override public boolean appliesTo(SubLevelPhysicsSystem system) { return HOST.equals(system.getLevel().dimension().location()); }
    @Override public int run(SubLevelPhysicsSystem system,double seconds) {
        if(!XenoSpaceConfig.enabled()) return 0;
        ServerLevel level=system.getLevel();
        var chart=SpaceChartService.state(level.getServer()).runtime.chart;
        long tick=level.getGameTime();
        int worked=0;
        for (var entry : bodies(level).entrySet()) {
            if (step(system, level, chart, tick, entry.getKey(), entry.getValue(), seconds)) worked++;
        }
        return worked;
    }

    private boolean step(SubLevelPhysicsSystem system, ServerLevel level, ChartRegistry chart, long tick,
                         String bodyId, ServerSubLevel body, double seconds) {
        var handle=system.getPhysicsHandle(body);
        if(handle==null || !handle.isValid()) return false;
        // Scheduler dispatch is once per game tick even when Sable substeps at 40 Hz.
        double mass=body.getMassTracker().getMass(),step=PrototypeOrbit.step(Math.max(seconds,0.05));
        if(!Double.isFinite(mass)||mass<=0||step<=0) return false;
        var pos=body.logicalPose().position();
        position[0]=pos.x();position[1]=pos.y();position[2]=pos.z();
        Track track=tracks.computeIfAbsent(body.getUniqueId(), ignored -> new Track());
        // Velocity from successive poses: frame-free, unlike reading the native body's velocity vector.
        int elapsed=0;
        if(track.previousTick!=Long.MIN_VALUE && tick>track.previousTick) {
            elapsed=(int)Math.min(Integer.MAX_VALUE,tick-track.previousTick);
            double dt=elapsed/20.0;
            System.arraycopy(track.velocity,0,track.previousVelocity,0,3);
            for(int i=0;i<3;i++) track.velocity[i]=(position[i]-track.previous[i])/dt;
            if(track.hasVelocity) {
                for(int i=0;i<3;i++) track.measured[i]=(track.velocity[i]-track.previousVelocity[i])/dt;
                track.held=track.release.update(track.measured,track.command,track.previousCommand,
                        XenoSpaceConfig.orbitHeldThreshold(),XenoSpaceConfig.orbitReleaseTicks(),elapsed);
            }
            track.hasVelocity=true;
        }
        System.arraycopy(position,0,track.previous,0,3);
        track.previousTick=tick;
        DimensionPhysicsData.getGravity(level,pos,gravity);
        if(!reportedPhysics.contains(body.getUniqueId()))
            PlotBodyBuilder.snapshot("first physics step (before native step)", body, null, -1,
                    XenoSpaceConfig.planetBuildDeferFinalize());
        if(reportedPhysics.add(body.getUniqueId()))
            XenoPixelsMod.LOGGER.info("Physical planet physics first step: body={}, UUID={}, nativeDelta={}, scheduledStep={}, gravity={}, mass={}, pose={}",
                    bodyId,body.getUniqueId(),seconds,step,gravity,mass,pos);
        // Cancel the dimension's own gravity; the controller below owns where the planet goes.
        impulse.set(gravity).negate();
        System.arraycopy(track.command,0,track.previousCommand,0,3);
        java.util.Arrays.fill(track.command,0);
        if(target(level, chart, bodyId, body, tick)) {
            double ex=target[0]-position[0],ey=target[1]-position[1],ez=target[2]-position[2];
            track.lastError=Math.sqrt(ex*ex+ey*ey+ez*ez);
            boolean released=!track.held;
            double recover=XenoSpaceConfig.orbitRecoverDistance();
            if(released && stationMoved(chart, bodyId, body, track.lastError)) {
                // The chart moved this body's station (a datapack edit): hop there instead of flying
                // straight across, which could plough through its parent.
                handle.teleport(teleportTarget.set(target[0],target[1],target[2]),body.logicalPose().orientation());
                handle.addLinearAndAngularVelocity(handle.getLinearVelocity(linear).negate(),handle.getAngularVelocity(angular).negate());
                XenoPixelsMod.LOGGER.info("Physical planet moved to its new charted station: body={}, to ({}, {}, {})",
                        bodyId,target[0],target[1],target[2]);
                track.reset();
                return true;
            }
            if(released && recover>0 && track.lastError>recover) {
                // Lost far beyond any tow (flung, or dragged out of the system): a slow flight home would
                // take many minutes, so put it back on its target at rest.
                handle.teleport(teleportTarget.set(target[0],target[1],target[2]),body.logicalPose().orientation());
                handle.addLinearAndAngularVelocity(handle.getLinearVelocity(linear).negate(),handle.getAngularVelocity(angular).negate());
                XenoPixelsMod.LOGGER.info("Physical planet recovered: body={}, error={} blocks, from ({}, {}, {}) to ({}, {}, {})",
                        bodyId,String.format("%.1f",track.lastError),position[0],position[1],position[2],target[0],target[1],target[2]);
                track.reset();
                return true;
            }
            double correction=released && XenoSpaceConfig.orbitReturnBoost()
                    ? PlanetOrbitHold.returnAcceleration(position,track.velocity,target,targetVelocity,feedForward,
                            XenoSpaceConfig.orbitReturnStiffness(),XenoSpaceConfig.orbitReturnDamping(),
                            XenoSpaceConfig.orbitReturnMaxAccel(),XenoSpaceConfig.orbitReturnMaxSpeed(),track.command)
                    : PlanetOrbitHold.acceleration(position,track.velocity,target,targetVelocity,feedForward,
                            XenoSpaceConfig.orbitHoldStiffness(),XenoSpaceConfig.orbitHoldDamping(),XenoSpaceConfig.orbitHoldMaxAccel(),track.command);
            impulse.add(track.command[0],track.command[1],track.command[2]);
            track.lastCorrection=correction;
        }
        if(!track.held && XenoSpaceConfig.planetSpinDamping()>0) {
            // A released planet stops tumbling. Removing a fraction of its own spin is frame-independent.
            double fraction=Math.min(1.0,XenoSpaceConfig.planetSpinDamping()*step);
            handle.getAngularVelocity(angular);
            if(angular.lengthSquared()>1e-10) handle.addLinearAndAngularVelocity(linear.zero(),angular.mul(-fraction));
        }
        impulse.mul(mass*step);
        body.logicalPose().orientation().transformInverse(impulse);
        if(impulse.isFinite() && impulse.lengthSquared()>1e-12) handle.applyLinearImpulse(impulse);
        return true;
    }

    /**
     * Fills {@link #target}, {@link #targetVelocity} and {@link #feedForward} for one body.
     *
     * <p>Parent: a loaded managed body (the Moon's Earth) contributes its live pose and measured velocity; a
     * charted root such as Sol contributes its chart X/Z at this body's saved orbital plane Y. Offset: the
     * Kepler track when real orbits are on and the body moves (phase-locked to where the planet was when the
     * orbit was first enabled, so switching orbits on does not drag it across the system), otherwise the
     * charted pinned offset.
     *
     * @return false when the body is not charted, so only gravity cancellation applies
     */
    private boolean target(ServerLevel level, ChartRegistry chart, String bodyId, ServerSubLevel body, long tick) {
        int slot=chart.slotOf(bodyId);
        if(slot<0) return false;
        CelestialBody row=chart.body(slot);
        var tag=body.getUserDataTag();
        double planeY=tag.contains(TAG+"_plane_y")?tag.getDouble(TAG+"_plane_y"):position[1];
        double px,py,pz,pvx=0,pvy=0,pvz=0;
        String parentId=row.parentId();
        ServerSubLevel parentBody=parentId==null?null:getBody(level,parentId);
        if(parentBody!=null) {
            var p=parentBody.logicalPose().position();
            // Each body keeps the orbital plane it was staged on; the Sun's centre sits higher than 128 to
            // fit its shell, and Earth must not climb to it.
            px=p.x();py=tag.contains(TAG+"_plane_y")?planeY:p.y();pz=p.z();
            Track parentTrack=tracks.get(parentBody.getUniqueId());
            if(parentTrack!=null){pvx=parentTrack.velocity[0];pvy=parentTrack.velocity[1];pvz=parentTrack.velocity[2];}
        } else {
            int parent=parentId==null?-1:chart.slotOf(parentId);
            px=parent<0?0:chart.x(parent);
            pz=parent<0?0:chart.z(parent);
            py=planeY;
            if(parent>=0){pvx=chart.velocityX(parent);pvz=chart.velocityZ(parent);}
        }
        var orbit=row.orbit();
        if(XenoSpaceConfig.realOrbits() && row.moves() && orbit!=null) {
            double mu=PlanetShell.orbitMu(orbit.semiMajorAxis(),orbit.periodSeconds());
            double now=tick/20.0;
            if(!tag.contains(TAG+"_orbit_shift")) tag.putDouble(TAG+"_orbit_shift",
                    phaseShift(orbit, now, position[0]-px, position[2]-pz));
            PlanetOrbitHold.orbitTrack(orbit,mu,now+tag.getDouble(TAG+"_orbit_shift"),offset,offsetVelocity,feedForward);
        } else {
            offset[0]=row.pinnedX();offset[1]=row.pinnedY();offset[2]=row.pinnedZ();
            offsetVelocity[0]=offsetVelocity[1]=offsetVelocity[2]=0;
            feedForward[0]=feedForward[1]=feedForward[2]=0;
        }
        target[0]=px+offset[0];target[1]=py+offset[1];target[2]=pz+offset[2];
        targetVelocity[0]=pvx+offsetVelocity[0];targetVelocity[1]=pvy+offsetVelocity[1];targetVelocity[2]=pvz+offsetVelocity[2];
        return Double.isFinite(target[0])&&Double.isFinite(target[1])&&Double.isFinite(target[2]);
    }

    /**
     * True once when the charted station (pinned offset, orbit size) differs from the one this body last
     * saw. A body that never recorded one (saved before this check existed) hops only when it is well off
     * its target, so an upgrade does not move planets that are already in place.
     */
    private static boolean stationMoved(ChartRegistry chart, String bodyId, ServerSubLevel body, double error) {
        int slot=chart.slotOf(bodyId);
        if(slot<0) return false;
        CelestialBody row=chart.body(slot);
        String station=row.pinnedX()+","+row.pinnedY()+","+row.pinnedZ()+","+(row.orbit()==null?"-":row.orbit().semiMajorAxis());
        var tag=body.getUserDataTag();
        String saved=tag.getString(TAG+"_station");
        if(station.equals(saved)) return false;
        tag.putString(TAG+"_station",station);
        return !saved.isEmpty() || error>64;
    }

    /**
     * Time shift that puts the orbit's track at the planet's current bearing around its parent. Exact for a
     * circular orbit; for an eccentric one the mean motion is used, which the controller then corrects.
     */
    static double phaseShift(net.bullettrain.xenopixelsmod.space.math.OrbitalElements orbit, double now, double dx, double dz) {
        double[] p=KeplerSolver.position(orbit,now,new double[3]);
        // Kepler XY maps to world XZ.
        double current=Math.atan2(dz,dx), track=Math.atan2(p[1],p[0]);
        double delta=current-track;
        delta=Math.atan2(Math.sin(delta),Math.cos(delta));
        double n=2*Math.PI/orbit.periodSeconds();
        return Double.isFinite(delta/n)?delta/n:0;
    }

    private static boolean protectedBlock(ServerLevel level,BlockPos pos) {
        var container=SubLevelContainer.getContainer(level);
        if(!container.inBounds(pos)) return false;
        var plot=container.getPlot(new ChunkPos(pos));
        return plot!=null && plot.getSubLevel() instanceof ServerSubLevel body && isManaged(body);
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if(event.getLevel() instanceof ServerLevel level && protectedBlock(level,event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if(event.getLevel() instanceof ServerLevel level) event.getAffectedBlocks().removeIf(pos->protectedBlock(level,pos));
    }

    /** Safety ledger, not a second serializer of Sable geometry/pose. One per managed body. */
    static final class Ledger extends SavedData {
        UUID id;
        boolean attempted;
        static Ledger get(ServerLevel level, String name) {
            return level.getDataStorage().computeIfAbsent(new Factory<>(Ledger::new,Ledger::load),name);
        }
        private static Ledger load(CompoundTag tag,HolderLookup.Provider registries) {
            var data=new Ledger();
            data.id=tag.hasUUID("Body")?tag.getUUID("Body"):null;
            data.attempted=tag.getBoolean("Attempted");
            return data;
        }
        void record(UUID value) {id=value;attempted=true;setDirty();}
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
            if(id!=null) tag.putUUID("Body",id);
            tag.putBoolean("Attempted",attempted);
            return tag;
        }
    }
}
