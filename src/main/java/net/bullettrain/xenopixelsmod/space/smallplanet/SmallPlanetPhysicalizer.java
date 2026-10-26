package net.bullettrain.xenopixelsmod.space.smallplanet;

import net.bullettrain.xenopixelsmod.compat.sable.SableLoadingTickets;
import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicket;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.SpaceHomeOrbitalFrame;
import net.bullettrain.xenopixelsmod.space.SpaceOrbitClock;
import net.bullettrain.xenopixelsmod.space.SpaceTickScheduler;
import net.bullettrain.xenopixelsmod.space.SpaceVisualSync;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidField;
import net.bullettrain.xenopixelsmod.space.asteroid.AsteroidFieldSync;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.bullettrain.xenopixelsmod.space.physics.PlanetOrbitHold;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.bullettrain.xenopixelsmod.space.physics.SableRemovalBand;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.bullettrain.xenopixelsmod.space.universe.FrontierBindings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Shared, capped Sable incarnations for deterministic small planets (S11 C2/C3). */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SmallPlanetPhysicalizer {
    public static final String TAG = "xenopixelsmod_smallplanet_v1";
    private static final String QUARANTINE_RELEASED_TAG = TAG + "_quarantine_released";
    private static final int PASS_TICKS = 20;
    private static final int RELEASE_MARKER_AUDIT_CANDIDATES_PER_SAVE = 32;
    private static final int RELEASE_MARKER_STARTUP_AUDIT_CANDIDATES = 1;
    private static final int RELEASE_MARKER_UNRESOLVED_POINTER_SAMPLE_LIMIT = 3;
    private static final long RELEASE_MARKER_AUDIT_WARNING_COOLDOWN_TICKS = 1200;
    private static final double OCCUPANT_RELEASE_MARGIN = 8.0;
    static final ResourceLocation TICKET_ID = ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "small_planet");
    private static final SubLevelLoadingTicketType<String> TICKET = SubLevelLoadingTicketType.create(TICKET_ID, Codec.STRING);
    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();
    private static final Map<ServerLevel, MinecraftServer> RELEASE_WAKEUP_REGISTRATIONS = new IdentityHashMap<>();
    private static final ThreadLocal<Vector3d> GRAVITY_CENTER = ThreadLocal.withInitial(Vector3d::new);
    private static final SmallPlanetOrbitPass ORBIT_PASS = new SmallPlanetOrbitPass();
    private static final int MAX_PENDING_PLACEMENTS = 4096;

    /** Forces the quarantine/planet ticket codec to register before Sable decodes SavedData on world load. */
    public static void bootstrap() {
        XenoPixelsMod.LOGGER.info("Small-planet Sable ticket codec registered before world data load");
    }

    static void reconcileForGameTest(ServerLevel level) {
        Runtime runtime = RUNTIMES.computeIfAbsent(level.getServer(), ignored -> new Runtime());
        runtime.adopted = false;
        adopt(level, runtime);
    }

    static int runQuarantineHoldForGameTest(ServerLevel level, double seconds) {
        var container = SubLevelContainer.getContainer(level);
        return ORBIT_PASS.run(container.physicsSystem(), seconds);
    }

    static Set<String> smallPlanetTicketKeysForGameTest(ServerLevel level, UUID bodyId) {
        var container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(bodyId) instanceof ServerSubLevel body) || body.isRemoved()) return Set.of();
        return ownedTicketKeys(container, body);
    }

    static void tagAdoptionGameTestBody(ServerSubLevel body, SmallPlanetField.Candidate candidate,
                                        boolean edited, boolean building) {
        CompoundTag identity = candidateTag(candidate);
        identity.putBoolean("touched", edited);
        identity.putBoolean("building", building);
        identity.putDouble("mass", body.getMassTracker().getMass());
        CompoundTag data = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
        data.put(TAG, identity);
        body.setUserDataTag(data);
    }

    static void addSmallPlanetTicketForGameTest(ServerLevel level, ServerSubLevel body, String key) {
        SubLevelContainer.getContainer(level).addForceLoadTicket(body, TICKET, key);
    }

    static boolean removeSmallPlanetTicketForGameTest(ServerLevel level, ServerSubLevel body, String key) {
        return SubLevelContainer.getContainer(level).removeForceLoadTicket(body, TICKET, key);
    }

    static void cleanupAdoptionGameTest(ServerLevel level, SmallPlanetField.ObjectId identity, UUID... bodyIds) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime != null) {
            runtime.active.remove(identity);
            for (UUID id : bodyIds) {
                runtime.quarantined.remove(id);
                runtime.releasePending.remove(id);
            }
        }
        var container = SubLevelContainer.getContainer(level);
        for (UUID id : bodyIds)
            if (container.getSubLevel(id) instanceof ServerSubLevel body && !body.isRemoved())
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
    }

    private record Wanted(SmallPlanetField.Candidate anchor, SmallPlanetField.Candidate current,
                          UniverseField.StarSystem system, SmallPlanetOrbit orbit,
                          double starX, double starY, double starZ) {}
    private record PlacementKey(ResourceLocation dimension, BlockPos position) {}
    private record CapturedMutation(UUID body, BlockState before, BlockState requested, long tick) {}
    private record PendingPlacement(UUID body, BlockState before, BlockState expectedState,
                                   long queuedAtTick, boolean locationWarningLogged) {
        PendingPlacement withLocationWarningLogged() {
            return new PendingPlacement(body, before, expectedState, queuedAtTick, true);
        }
    }
    private record EditDecision(SmallPlanetEditedBodyPolicy.Admission admission, boolean alreadyPinned) {}

    private static final class Active {
        final SmallPlanetField.Candidate candidate;
        final UUID body;
        final double originalMass;
        SmallPlanetOrbit orbit;
        UniverseField.StarSystem system;
        double starX, starY, starZ;
        final OrbitTrack track = new OrbitTrack();
        boolean editedPinned;
        boolean unsafeOrbitReported;
        boolean orbitRetryPending, orbitRetryFailureReported;
        long nextOrbitRetryTick;
        Active(SmallPlanetField.Candidate candidate, UUID body, double originalMass,
               SmallPlanetOrbit orbit, UniverseField.StarSystem system, double starX, double starY, double starZ) {
            this.candidate = candidate; this.body = body; this.originalMass = originalMass;
            this.orbit = orbit; this.system = system; this.starX = starX; this.starY = starY; this.starZ = starZ;
        }
    }

    /** Ambiguous persisted bodies are held at their loaded pose until an administrator repairs them. */
    private static final class Quarantined {
        final UUID body;
        final String reason;
        final boolean edited;
        final OrbitTrack track = new OrbitTrack();
        boolean holdAttempted, holdAvailable, holdFailureLogged;
        Quarantined(UUID body, String reason, boolean edited) {
            this.body = body; this.reason = reason; this.edited = edited;
        }
    }

    /** Allocation-free motion samples for one capped, already-physicalized body. */
    private static final class OrbitTrack {
        final double[] previous = new double[3], velocity = new double[3], previousVelocity = new double[3];
        final double[] command = new double[3], previousCommand = new double[3], measured = new double[3];
        final double[] target = new double[3], targetVelocity = new double[3], feedForward = new double[3];
        final double[] bodyPosition = new double[3];
        final double[] acceleration = new double[3], gravity = new double[3];
        final Vector3d gravityVector = new Vector3d(), impulse = new Vector3d();
        final PlanetOrbitHold.ReleaseDetector release = new PlanetOrbitHold.ReleaseDetector();
        long previousTick = Long.MIN_VALUE;
        boolean hasVelocity, held, stationaryTargetCaptured;
        void reset() {
            previousTick = Long.MIN_VALUE; hasVelocity = false; held = false; stationaryTargetCaptured = false;
            java.util.Arrays.fill(velocity, 0); java.util.Arrays.fill(command, 0);
            java.util.Arrays.fill(previousCommand, 0); release.reset();
        }
    }

    private static final class Runtime {
        final SmallPlanetFocusDiagnostics focusDiagnostics = new SmallPlanetFocusDiagnostics();
        final Map<SmallPlanetField.ObjectId, Active> active = new LinkedHashMap<>();
        final SmallPlanetKiAdmissionCache kiAdmissionCache = new SmallPlanetKiAdmissionCache();
        final SmallPlanetEditReservationLedger editReservations = new SmallPlanetEditReservationLedger();
        final Map<PlacementKey, CapturedMutation> capturedMutations = new LinkedHashMap<>();
        final Map<PlacementKey, PendingPlacement> pendingPlacements = new LinkedHashMap<>();
        final Map<UUID, Quarantined> quarantined = new LinkedHashMap<>();
        final Set<UUID> releasePending = new LinkedHashSet<>();
        final Set<UUID> releasedQuarantineIds = new LinkedHashSet<>();
        final SmallPlanetReleaseMarkerAuditQueue releaseMarkerAuditQueue = new SmallPlanetReleaseMarkerAuditQueue();
        final LinkedHashMap<UUID, Long> unresolvedReleasePointerSample = new LinkedHashMap<>();
        final Set<UUID> placementTestBodies = new HashSet<>();
        final List<Wanted> wanted = new ArrayList<>();
        ResourceLocation placementTestDimension;
        SmallPlanetField.Candidate building;
        SmallPlanetOrbit buildingOrbit;
        UniverseField.StarSystem buildingSystem;
        double buildingStarX, buildingStarY, buildingStarZ;
        PlotBodyBuilder.Job job;
        boolean buildTicketed, adopted;
        boolean releaseDecisionsLoaded;
        boolean startupReleaseMarkerAuditDone;
        boolean releaseObserverWarningLogged;
        boolean stopping;
        long nextPass, nextSpawn, nextAdoptRetryTick, maxBatchNanos;
        long lastReleaseMarkerAuditWarningTick = Long.MIN_VALUE;
        long lastUnresolvedReleasePointerWarningTick = Long.MIN_VALUE;
        long lastUnresolvedReleasePointerAuditTick = Long.MIN_VALUE;
        int lastUnresolvedReleasePointerCount;
        int built, removed, detached, failed;
    }

    record ReleaseWakeupGameTestState(boolean adopted, long nextAdoptRetryTick, boolean releasedDecisionCached) {}

    private SmallPlanetPhysicalizer() {}

    private static void queueReleaseMarkerAudit(Runtime runtime, UUID bodyId) {
        if (runtime.releasedQuarantineIds.contains(bodyId)) runtime.releaseMarkerAuditQueue.enqueue(bodyId);
    }

    /** Prioritize a released body without searching/removing it from the potentially large fallback ledger. */
    private static void prioritizeReleaseMarkerAudit(Runtime runtime, UUID bodyId) {
        if (SmallPlanetReleaseMarkerAuditPolicy.shouldPrioritize(
                runtime.releasedQuarantineIds.contains(bodyId), runtime.releasePending.contains(bodyId)))
            runtime.releaseMarkerAuditQueue.prioritize(bodyId);
    }

    /** Package-private GameTest seam: register a tagged fixture as live and deliberately unpinned. */
    static void registerUnpinnedPlacementTestBody(ServerLevel level, ServerSubLevel body,
                                                  SmallPlanetField.Candidate candidate) {
        Runtime runtime = RUNTIMES.computeIfAbsent(level.getServer(), ignored -> new Runtime());
        if (runtime.active.containsKey(candidate.id()))
            throw new IllegalStateException("small-planet GameTest candidate is already active");
        ResourceLocation testDimension = level.dimension().location();
        if (runtime.placementTestDimension != null && !runtime.placementTestDimension.equals(testDimension))
            throw new IllegalStateException("small-planet placement GameTest fixtures must share a dimension");
        runtime.placementTestDimension = testDimension;
        CompoundTag identity = candidateTag(candidate);
        identity.putBoolean("building", false);
        double mass = body.getMassTracker().getMass();
        identity.putDouble("mass", mass);
        CompoundTag userData = body.getUserDataTag() == null ? new CompoundTag() : body.getUserDataTag().copy();
        userData.put(TAG, identity);
        body.setUserDataTag(userData);
        runtime.active.put(candidate.id(), new Active(candidate, body.getUniqueId(), mass, null, null, 0, 0, 0));
        runtime.placementTestBodies.add(body.getUniqueId());
        runtime.adopted = true;
    }

    /** Cleans the package-private placement GameTest fixture and its transient reservation state. */
    static void unregisterPlacementTestBody(ServerLevel level, ServerSubLevel body,
                                            SmallPlanetField.Candidate candidate) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || body == null) return;
        runtime.placementTestBodies.remove(body.getUniqueId());
        runtime.active.remove(candidate.id());
        runtime.pendingPlacements.entrySet().removeIf(entry -> entry.getValue().body().equals(body.getUniqueId()));
        runtime.capturedMutations.entrySet().removeIf(entry -> entry.getValue().body().equals(body.getUniqueId()));
        runtime.editReservations.releaseAllPlacements(body.getUniqueId());
        if (runtime.placementTestBodies.isEmpty()) runtime.placementTestDimension = null;
    }

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        SpaceTickScheduler.register(ORBIT_PASS);
        ServerLevel host = host(event.getServer());
        if (host == null) return;
        Runtime runtime = RUNTIMES.computeIfAbsent(event.getServer(), ignored -> new Runtime());
        if (!registerReleaseWakeup(event.getServer(), host)) {
            XenoPixelsMod.LOGGER.warn("Sable container is not ready for small-planet release observer registration; adoption will retry on server ticks");
            runtime.releaseObserverWarningLogged = true;
            // Adoption itself requires the container too. Leave the runtime unadopted so tick can retry.
            if (SubLevelContainer.getContainer(host) == null) return;
        } else {
            runtime.releaseObserverWarningLogged = false;
        }
        adopt(host, runtime);
    }

    /** Wake a pending release only when Sable actually restores that sublevel into memory. */
    private static boolean registerReleaseWakeup(MinecraftServer server, ServerLevel host) {
        if (RELEASE_WAKEUP_REGISTRATIONS.containsKey(host)) return true;
        var container = SubLevelContainer.getContainer(host);
        if (container == null) return false;
        container.addObserver(new SubLevelObserver() {
            @Override public void onSubLevelAdded(SubLevel subLevel) {
                if (!(subLevel instanceof ServerSubLevel body)) return;
                if (!server.isSameThread()) {
                    UUID bodyId = body.getUniqueId();
                    try {
                        server.execute(() -> wakePendingRelease(server, bodyId));
                    } catch (RuntimeException failure) {
                        XenoPixelsMod.LOGGER.error("Could not schedule release reconciliation for newly loaded small-planet body {}; its persistent release remains pending",
                                bodyId, failure);
                    }
                    return;
                }
                wakePendingRelease(server, body.getUniqueId());
            }
        });
        RELEASE_WAKEUP_REGISTRATIONS.put(host, server);
        return true;
    }

    static ReleaseWakeupGameTestState prepareReleaseWakeupGameTest(ServerLevel level, UUID bodyId) {
        Runtime runtime = RUNTIMES.computeIfAbsent(level.getServer(), ignored -> new Runtime());
        ReleaseWakeupGameTestState state = new ReleaseWakeupGameTestState(
                runtime.adopted, runtime.nextAdoptRetryTick, runtime.releasedQuarantineIds.contains(bodyId));
        // Model the durable-decision cache after ticket cleanup: this UUID is no longer pending,
        // but a later Sable restore still has to wake adoption and re-queue its cleanup.
        runtime.releasedQuarantineIds.add(bodyId);
        runtime.adopted = true;
        runtime.nextAdoptRetryTick = Long.MAX_VALUE;
        registerReleaseWakeup(level.getServer(), level);
        return state;
    }

    static boolean releaseWakeupDueForGameTest(ServerLevel level) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && !runtime.adopted
                && runtime.nextAdoptRetryTick <= SpaceOrbitClock.gameTick(level.getServer());
    }

    static void finishReleaseWakeupGameTest(ServerLevel level, UUID bodyId, ReleaseWakeupGameTestState state) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null) return;
        runtime.releasePending.remove(bodyId);
        if (!state.releasedDecisionCached()) runtime.releasedQuarantineIds.remove(bodyId);
        // A real release may wake adoption while this GameTest is running; don't restore over it.
        runtime.adopted = state.adopted() && runtime.adopted;
        runtime.nextAdoptRetryTick = Math.min(state.nextAdoptRetryTick(), runtime.nextAdoptRetryTick);
    }

    private static void wakePendingRelease(MinecraftServer server, UUID bodyId) {
        if (!server.isSameThread())
            throw new IllegalStateException("small-planet release wakeup must run on the server thread");
        Runtime current = RUNTIMES.get(server);
        if (current == null || (!current.releasePending.contains(bodyId)
                && !current.releasedQuarantineIds.contains(bodyId))) return;
        // A released body's UUID remains a durable decision after ticket cleanup. If Sable restores
        // it again later, wake reconciliation so the release decision is applied to that incarnation.
        current.releasePending.add(bodyId);
        prioritizeReleaseMarkerAudit(current, bodyId);
        current.adopted = false;
        current.nextAdoptRetryTick = Math.min(current.nextAdoptRetryTick, SpaceOrbitClock.gameTick(server));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        Runtime runtime = RUNTIMES.get(server);
        ServerLevel level = host(server);
        if (level == null) {
            if (runtime != null) flushPlacementTestDimension(server, runtime, null, false);
            return;
        }
        SmallPlanetActivationPolicy.Gate activationGate = activationGate();
        boolean enabled = activationGate.enabled();
        if (runtime == null) {
            runtime = new Runtime();
            RUNTIMES.put(server, runtime);
        }
        // Also cover servers where the host dimension or its Sable container becomes available after startup.
        // Retry regardless of physicalization mode: existing quarantined edited bodies still need their safety hold.
        if (!RELEASE_WAKEUP_REGISTRATIONS.containsKey(level)) {
            if (registerReleaseWakeup(server, level)) runtime.releaseObserverWarningLogged = false;
            else if (!runtime.releaseObserverWarningLogged) {
                XenoPixelsMod.LOGGER.warn("Sable container is not ready for small-planet release observer registration; retrying on a later server tick");
                runtime.releaseObserverWarningLogged = true;
            }
            if (!RELEASE_WAKEUP_REGISTRATIONS.containsKey(level)) return;
        }

        long now = SpaceOrbitClock.gameTick(server);
        flushPendingPlacements(level, runtime, level.getGameTime(), false);
        flushPlacementTestDimension(server, runtime, level, false);
        SmallPlanetTickPolicy.Decision tickPlan = SmallPlanetTickPolicy.plan(enabled, runtime.adopted,
                SmallPlanetOrbitAdmission.retryDue(now, runtime.nextAdoptRetryTick), now >= runtime.nextPass);
        if (tickPlan.retryAdoption())
            adopt(level, runtime);
        auditReleasedMarkersAfterAdoption(level, runtime);
        if (!enabled) {
            runtime.wanted.clear();
            abandonBuild(level, runtime, "small-planet physicalization disabled");
            for (var entry : new ArrayList<>(runtime.active.entrySet()))
                release(level, runtime, entry.getKey(), entry.getValue(), "feature disabled");
            pruneRemoved(runtime, level);
            // Keep adoption/retry state for this loaded host. Removing an empty runtime here
            // recreates it next tick and repeats the full saved-body scan at 20 Hz while disabled.
            return;
        }
        // Adoption may have completed in the retry above, so derive generation admission from its result.
        tickPlan = SmallPlanetTickPolicy.plan(enabled, runtime.adopted, false, now >= runtime.nextPass);

        if (tickPlan.runPass()) {
            runtime.nextPass = now + PASS_TICKS;
            pass(server, level, runtime);
        }
        if (tickPlan.advanceExistingBuild()) stepBuild(level, runtime);
        if (!tickPlan.startNewBody()) {
            // Keep releases and already-started build jobs progressing during reconciliation,
            // but never start another incarnation until the persisted identity set is settled.
            runtime.wanted.clear();
            return;
        }
        if (runtime.job == null && !runtime.wanted.isEmpty() && now >= runtime.nextSpawn
                && normalActiveCount(runtime) < XenoSpaceConfig.smallPlanetMaxGlobal()
                && runtime.active.size() + runtime.quarantined.size()
                        < XenoSpaceConfig.smallPlanetMaxGlobal() + XenoSpaceConfig.smallPlanetMaxEditedGlobal()) {
            startNext(level, runtime);
        }
    }

    /**
     * Sable serializes sub-levels during saveLevelData, before NeoForge posts LevelEvent.Save.
     * Read back a small bounded batch of released markers; uncertain/missing pointers remain in SavedData.
     */
    @SubscribeEvent public static void auditReleasedMarkersAfterSave(LevelEvent.Save event) {
        if (!(event.getLevel() instanceof ServerLevel level) || host(level.getServer()) != level) return;
        auditReleasedMarkers(level);
    }

    private static int auditReleasedMarkers(ServerLevel level) {
        return auditReleasedMarkers(level, RELEASE_MARKER_AUDIT_CANDIDATES_PER_SAVE);
    }

    private static int auditReleasedMarkers(ServerLevel level, int maxCandidates) {
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || !runtime.releaseDecisionsLoaded || runtime.releaseMarkerAuditQueue.isEmpty()) return 0;

        var container = SubLevelContainer.getContainer(level);
        var holding = container.getHoldingChunkMap();
        var storage = holding.getStorage();
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        int pruned = 0;
        int storageReadAttempts = 0;
        int failedStorageReadbacks = 0;
        int unresolvedPointers = 0;
        long auditTick = SpaceOrbitClock.gameTick(level.getServer());
        SmallPlanetReleaseMarkerAuditQueue.Batch batch =
                runtime.releaseMarkerAuditQueue.beginBatch(maxCandidates);
        SmallPlanetReleaseMarkerAuditQueue.Candidate candidate;
        while ((candidate = batch.pollNext()) != null) {
            UUID bodyId = candidate.bodyId();
            if (!runtime.releasedQuarantineIds.contains(bodyId)) {
                runtime.unresolvedReleasePointerSample.remove(bodyId);
                continue;
            }
            if (runtime.releasePending.contains(bodyId)) {
                runtime.unresolvedReleasePointerSample.remove(bodyId);
                batch.retry(candidate);
                continue;
            }

            dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer pointer = null;
            if (container.getSubLevel(bodyId) instanceof ServerSubLevel body && !body.isRemoved()) {
                pointer = body.getLastSerializationPointer();
            } else {
                var held = holding.getHoldingSubLevel(bodyId);
                if (held != null) pointer = held.pointer();
            }
            if (pointer == null) {
                unresolvedPointers++;
                if (runtime.unresolvedReleasePointerSample.size() < RELEASE_MARKER_UNRESOLVED_POINTER_SAMPLE_LIMIT)
                    runtime.unresolvedReleasePointerSample.putIfAbsent(bodyId, auditTick);
                batch.retry(candidate);
                continue;
            }
            runtime.unresolvedReleasePointerSample.remove(bodyId);

            dev.ryanhcode.sable.sublevel.storage.serialization.SubLevelData stored;
            storageReadAttempts++;
            try {
                stored = storage.attemptLoadSubLevel(pointer.chunkPos(), pointer.local());
            } catch (RuntimeException failure) {
                failedStorageReadbacks++;
                XenoPixelsMod.LOGGER.debug("Could not read Sable storage slot for released small-planet body {}; keeping its SavedData decision",
                        bodyId, failure);
                batch.retry(candidate);
                continue;
            }
            boolean marker = stored != null && stored.fullTag().getCompound("user_data")
                    .getBoolean(QUARANTINE_RELEASED_TAG);
            UUID storedBody = stored == null ? null : stored.uuid();
            if (!bodyId.equals(storedBody) || !marker) failedStorageReadbacks++;
            boolean liveBody = container.getSubLevel(bodyId) instanceof ServerSubLevel live && !live.isRemoved();
            if (SmallPlanetReleaseMarkerReadbackPolicy.mayRepairMarker(bodyId, storedBody, marker, liveBody)) {
                try {
                    ServerSubLevel live = (ServerSubLevel) container.getSubLevel(bodyId);
                    CompoundTag repaired = live.getUserDataTag() == null
                            ? new CompoundTag() : live.getUserDataTag().copy();
                    repaired.putBoolean(QUARANTINE_RELEASED_TAG, true);
                    live.setUserDataTag(repaired);
                    XenoPixelsMod.LOGGER.warn("Restored missing serialized release marker on live small-planet body {}; retaining SavedData decision until a later save verifies it",
                            bodyId);
                } catch (RuntimeException failure) {
                    XenoPixelsMod.LOGGER.error("Could not restore missing release marker on live small-planet body {}; keeping its SavedData decision",
                            bodyId, failure);
                }
                batch.retry(candidate);
                continue;
            }
            if (!SmallPlanetReleaseMarkerReadbackPolicy.mayPrune(bodyId,
                    storedBody, marker, runtime.releasePending.contains(bodyId))) {
                batch.retry(candidate);
                continue;
            }
            if (saved.pruneQuarantineReleasedAfterVerifiedBodyReadback(bodyId)) {
                runtime.releasedQuarantineIds.remove(bodyId);
                runtime.releasePending.remove(bodyId);
                runtime.unresolvedReleasePointerSample.remove(bodyId);
                pruned++;
            } else {
                runtime.releasedQuarantineIds.remove(bodyId);
                runtime.unresolvedReleasePointerSample.remove(bodyId);
            }
        }
        if (pruned > 0)
            XenoPixelsMod.LOGGER.info("Pruned {} small-planet release decision(s) after exact Sable storage read-back; queued fallback={}, prioritized={}",
                    pruned, runtime.releaseMarkerAuditQueue.fallbackSize(),
                    runtime.releaseMarkerAuditQueue.prioritySize());
        runtime.lastUnresolvedReleasePointerCount = unresolvedPointers;
        runtime.lastUnresolvedReleasePointerAuditTick = auditTick;
        if (SmallPlanetReleaseMarkerAuditPolicy.shouldWarnUnresolvablePointer(unresolvedPointers,
                runtime.lastUnresolvedReleasePointerWarningTick, auditTick,
                RELEASE_MARKER_AUDIT_WARNING_COOLDOWN_TICKS)) {
            runtime.lastUnresolvedReleasePointerWarningTick = auditTick;
            XenoPixelsMod.LOGGER.warn("Small-planet release-marker audit could not resolve Sable storage pointers for {} candidate(s); retaining all release decisions; retained={}, oldest sampled age={} ticks, sample={}",
                    unresolvedPointers, runtime.releasedQuarantineIds.size(),
                    unresolvedPointerSampleOldestAge(runtime, auditTick), runtime.unresolvedReleasePointerSample.keySet());
        }
        long now = auditTick;
        if (SmallPlanetReleaseMarkerAuditPolicy.shouldWarnReadbackFailure(storageReadAttempts,
                failedStorageReadbacks, pruned, runtime.lastReleaseMarkerAuditWarningTick,
                now, RELEASE_MARKER_AUDIT_WARNING_COOLDOWN_TICKS)) {
            runtime.lastReleaseMarkerAuditWarningTick = now;
            XenoPixelsMod.LOGGER.warn("All {} small-planet release-marker Sable storage readbacks failed in this save audit; failed={}, retained release decisions={}, queued fallback={}, prioritized={}",
                    storageReadAttempts, failedStorageReadbacks, runtime.releasedQuarantineIds.size(),
                    runtime.releaseMarkerAuditQueue.fallbackSize(), runtime.releaseMarkerAuditQueue.prioritySize());
        }
        return pruned;
    }

    /** One minimal storage read-back after release decisions have been loaded and body adoption has settled. */
    private static int auditReleasedMarkersAfterAdoption(ServerLevel level, Runtime runtime) {
        if (!SmallPlanetReleaseMarkerReadbackPolicy.shouldRunStartupAudit(
                runtime.adopted, runtime.startupReleaseMarkerAuditDone)) return 0;
        int pruned = auditReleasedMarkers(level, RELEASE_MARKER_STARTUP_AUDIT_CANDIDATES);
        runtime.startupReleaseMarkerAuditDone = true;
        XenoPixelsMod.LOGGER.info("Small planet startup release-marker audit completed: pruned={} fallbackQueued={} prioritizedQueued={} tick={}",
                pruned, runtime.releaseMarkerAuditQueue.fallbackSize(),
                runtime.releaseMarkerAuditQueue.prioritySize(), SpaceOrbitClock.gameTick(level.getServer()));
        return pruned;
    }

    /** GameTest seam for the exact once-after-adoption startup audit path. */
    public static int auditReleasedMarkerReadbackForGameTest(ServerLevel level, UUID bodyId) {
        MinecraftServer server = level.getServer();
        Runtime runtime = RUNTIMES.get(server);
        boolean created = runtime == null;
        if (created) {
            runtime = new Runtime();
            RUNTIMES.put(server, runtime);
        }
        boolean wasLoaded = runtime.releaseDecisionsLoaded;
        boolean wasAdopted = runtime.adopted;
        boolean wasStartupAuditDone = runtime.startupReleaseMarkerAuditDone;
        boolean hadDecision = runtime.releasedQuarantineIds.contains(bodyId);
        boolean wasPending = runtime.releasePending.contains(bodyId);
        if (!SmallPlanetSavedData.get(level).markQuarantineReleased(bodyId))
            throw new IllegalStateException("could not persist GameTest release decision");
        runtime.releaseDecisionsLoaded = true;
        runtime.adopted = true;
        runtime.startupReleaseMarkerAuditDone = false;
        runtime.releasedQuarantineIds.add(bodyId);
        runtime.releasePending.remove(bodyId);
        runtime.releaseMarkerAuditQueue.enqueue(bodyId);
        try {
            int pruned = auditReleasedMarkersAfterAdoption(level, runtime);
            if (auditReleasedMarkersAfterAdoption(level, runtime) != 0)
                throw new IllegalStateException("startup release-marker audit ran more than once");
            return pruned;
        } finally {
            runtime.releaseMarkerAuditQueue.remove(bodyId);
            if (hadDecision) runtime.releasedQuarantineIds.add(bodyId);
            else runtime.releasedQuarantineIds.remove(bodyId);
            if (wasPending) runtime.releasePending.add(bodyId);
            else runtime.releasePending.remove(bodyId);
            runtime.releaseDecisionsLoaded = wasLoaded;
            runtime.adopted = wasAdopted;
            runtime.startupReleaseMarkerAuditDone = wasStartupAuditDone;
            if (created) RUNTIMES.remove(server);
        }
    }

    /** Seed one already-serialized test candidate so a real MinecraftServer save exercises LevelEvent.Save. */
    public static void queueReleaseMarkerForSaveEventGameTest(ServerLevel level, UUID bodyId) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker save-event GameTest must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime == null || !runtime.adopted || !runtime.releaseDecisionsLoaded)
            throw new IllegalStateException("small-planet release adoption must finish before save-event GameTest setup");
        if (!runtime.releaseMarkerAuditQueue.isEmpty())
            throw new IllegalStateException("release-marker audit queue must be empty before save-event GameTest setup");
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        if (saved.isQuarantineReleased(bodyId) || runtime.releasedQuarantineIds.contains(bodyId)
                || runtime.releasePending.contains(bodyId))
            throw new IllegalStateException("GameTest body UUID already has release-audit state");
        if (!saved.markQuarantineReleased(bodyId))
            throw new IllegalStateException("could not persist GameTest release decision");
        runtime.releasedQuarantineIds.add(bodyId);
        runtime.releasePending.remove(bodyId);
        prioritizeReleaseMarkerAudit(runtime, bodyId);
    }

    /** Rolls back only the UUID created by a failed, isolated save-event GameTest fixture. */
    public static void cleanupReleaseMarkerSaveEventGameTest(ServerLevel level, UUID bodyId) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker save-event GameTest cleanup must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        if (runtime != null) {
            runtime.releaseMarkerAuditQueue.remove(bodyId);
            runtime.releasePending.remove(bodyId);
            runtime.releasedQuarantineIds.remove(bodyId);
            runtime.quarantined.remove(bodyId);
            runtime.active.entrySet().removeIf(entry -> entry.getValue().body.equals(bodyId));
        }
        SmallPlanetSavedData.get(level).clearQuarantineReleaseForGameTest(bodyId);
    }

    /** Read-only queue assertion for UUID-scoped release-marker save-event GameTests. */
    public static boolean isReleaseMarkerAuditQueuedForGameTest(ServerLevel level, UUID bodyId) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker save-event GameTest inspection must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.releaseMarkerAuditQueue.contains(bodyId);
    }

    /** Queue-class assertions for an isolated UUID-scoped release-marker GameTest. */
    public static boolean isReleaseMarkerAuditFallbackQueuedForGameTest(ServerLevel level, UUID bodyId) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker save-event GameTest inspection must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.releaseMarkerAuditQueue.isFallback(bodyId);
    }

    public static boolean isReleaseMarkerAuditPrioritizedForGameTest(ServerLevel level, UUID bodyId) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker save-event GameTest inspection must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.releaseMarkerAuditQueue.isPrioritized(bodyId);
    }

    /** Read-only startup/queue checks for the isolated two-process release-audit acceptance fixture. */
    public static boolean isStartupReleaseMarkerAuditDoneForGameTest(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("startup release-marker audit GameTest inspection must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.startupReleaseMarkerAuditDone;
    }

    public static boolean isReleaseMarkerAuditQueueEmptyForGameTest(ServerLevel level) {
        if (!level.getServer().isSameThread())
            throw new IllegalStateException("release-marker audit GameTest inspection must run on the server thread");
        Runtime runtime = RUNTIMES.get(level.getServer());
        // A missing runtime is not evidence that an audit queue was initialized and drained.
        return runtime != null && runtime.releaseMarkerAuditQueue.isEmpty();
    }

    private static void pass(MinecraftServer server, ServerLevel level, Runtime runtime) {
        var state = SpaceChartService.state(server);
        var chart = state.runtime.chart;
        SpaceHomeOrbitalFrame.Focus homeFocus = SpaceHomeOrbitalFrame.resolve(level, chart);
        if (homeFocus == null) {
            // Do not start a queued candidate selected against an earlier, now unavailable focus.
            runtime.wanted.clear();
            String reason;
            if (chart.slotOf(SpaceHomeOrbitalFrame.SOL_ID) >= 0)
                reason = "Sol has a non-finite X/Z position";
            else if (state.runtime.revision == 0 && chart.size() == 0)
                reason = "chart has not been published yet";
            else if (net.bullettrain.xenopixelsmod.space.hakai.PlanetHakai.isUnavailable(SpaceHomeOrbitalFrame.SOL_ID))
                reason = "Sol is excluded by saved Hakai/destruction state; inspect /xenospace hakai status";
            else if (!state.definitions.containsKey(SpaceHomeOrbitalFrame.SOL_ID))
                reason = "chart resources contain no xenopixelsmod:sol definition";
            else
                reason = "Sol definition was not admitted to the active chart";
            if (runtime.focusDiagnostics.missing(state.runtime.revision, reason))
                XenoPixelsMod.LOGGER.warn("Small-planet proximity paused: {} (chart revision {}, bodies {}, diagnostics {}). "
                                + "Check /xenospace status and preceding Space chart messages. Further identical warnings are suppressed.",
                        reason, state.runtime.revision, chart.size(), state.errors.stream().limit(3).toList());
            return;
        }
        if (runtime.focusDiagnostics.recovered())
            XenoPixelsMod.LOGGER.info("Small-planet proximity resumed: Sol focus is available (chart revision {})",
                    state.runtime.revision);
        var charted = new ArrayList<SmallPlanetField.Sphere>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) {
            var live = PhysicalEarth.liveCenter(level, chart.body(slot).id());
            double x = live == null ? chart.x(slot) : live.x();
            double y = live == null ? SpaceHomeOrbitalFrame.chartY(chart, slot, homeFocus.y()) : live.y();
            double z = live == null ? chart.z(slot) : live.z();
            double radius = live == null ? chart.body(slot).radiusBlocks()
                    : PhysicalEarth.liveRadius(level, chart.body(slot).id());
            if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && Double.isFinite(radius) && radius > 0)
                charted.add(new SmallPlanetField.Sphere(x, y, z, radius));
        }
        List<AsteroidField.Belt> belts = AsteroidFieldSync.belts(state);
        long seed = net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(server);
        var frontier = net.bullettrain.xenopixelsmod.space.universe.FrontierBindings.get(level);
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        Map<SmallPlanetField.ObjectId, Wanted> candidates = new HashMap<>();
        Map<SmallPlanetField.ObjectId, Double> nearest = new HashMap<>();
        int perPlayer = XenoSpaceConfig.smallPlanetMaxPerPlayer();
        double releaseRange = XenoSpaceConfig.smallPlanetReleaseRange();
        double orbitSeconds = SpaceOrbitClock.seconds(server);
        double homeX = homeFocus.x(), homeY = homeFocus.y(), homeZ = homeFocus.z();
        Map<SmallPlanetField.ObjectId, Wanted> protectedActive = new HashMap<>();
        Map<SmallPlanetField.ObjectId, Double> protectedDistances = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            List<Wanted> nearby;
            if (XenoSpaceConfig.realOrbits()) {
                List<SmallPlanetField.OrbitingCandidate> orbiting = new ArrayList<>(SmallPlanetField.orbitingNearby(seed,
                        player.getX(), player.getY(), player.getZ(), releaseRange,
                        SmallPlanetField.MAX_PLANETS_PER_SYSTEM, charted, belts,
                        cell -> frontier.systemAt(seed, cell), homeX, homeY, homeZ, orbitSeconds));
                nearby = new ArrayList<>(orbiting.size());
                for (var orbitingCandidate : orbiting) {
                    SmallPlanetField.Candidate anchor = orbitingCandidate.anchor();
                    if (saved.isDetached(anchor.id())) continue;
                    UniverseField.StarSystem system = frontier.systemAt(seed, anchor.id().systemCell());
                    if (system != null) nearby.add(new Wanted(anchor, orbitingCandidate.at(orbitSeconds), system,
                            orbitingCandidate.orbit(), system.cell().home() ? homeX : system.x(),
                            system.cell().home() ? homeY : system.y(), system.cell().home() ? homeZ : system.z()));
                }
            } else {
                List<SmallPlanetField.Candidate> stationary = SmallPlanetField.nearby(seed,
                        player.getX(), player.getY(), player.getZ(), releaseRange,
                        SmallPlanetField.MAX_PLANETS_PER_SYSTEM, charted, belts, cell -> frontier.systemAt(seed, cell));
                nearby = new ArrayList<>(stationary.size());
                for (SmallPlanetField.Candidate anchor : stationary) {
                    if (saved.isDetached(anchor.id())) continue;
                    UniverseField.StarSystem system = frontier.systemAt(seed, anchor.id().systemCell());
                    if (system == null) continue;
                    try {
                        double sx = system.cell().home() ? homeX : system.x();
                        double sy = system.cell().home() ? homeY : system.y();
                        double sz = system.cell().home() ? homeZ : system.z();
                        nearby.add(new Wanted(anchor, anchor, system, SmallPlanetOrbit.fromCandidate(anchor, sx, sy, sz), sx, sy, sz));
                    } catch (IllegalArgumentException invalid) {
                        XenoPixelsMod.LOGGER.warn("Small planet {} has invalid static orbit identity: {}", anchor.id().bodyId(), invalid.getMessage());
                    }
                }
            }
            Map<SmallPlanetField.ObjectId, Wanted> byId = new HashMap<>();
            Map<SmallPlanetField.ObjectId, Double> distanceById = new HashMap<>();
            for (Wanted candidate : nearby) {
                double distance = distanceSquared(player, candidate.current());
                byId.put(candidate.anchor().id(), candidate);
                distanceById.put(candidate.anchor().id(), distance);
            }

            // An active body can lag its ephemeris while parked or rejoining the canonical orbit. Keep it
            // selectable by either position, and include parked ids whose star-system lookup is unavailable.
            var container = SubLevelContainer.getContainer(level);
            for (var entry : runtime.active.entrySet()) {
                Active active = entry.getValue();
                if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) continue;
                var pose = body.logicalPose().position();
                SmallPlanetField.Candidate live = active.candidate.atPosition(pose.x(), pose.y(), pose.z());
                Wanted analyticEntry = byId.get(entry.getKey());
                SmallPlanetField.Candidate analytic = analyticEntry == null
                        ? analyticPosition(active, orbitSeconds) : analyticEntry.current();
                double liveDistance = distanceSquared(player, live);
                double analyticDistance = analytic == null ? Double.POSITIVE_INFINITY : distanceSquared(player, analytic);
                boolean tracking = body.getTrackingPlayers().contains(player.getUUID());
                boolean intersects = intersectsInflated(player, body, OCCUPANT_RELEASE_MARGIN);
                if (!SmallPlanetProximityPolicy.shouldSelect(analyticDistance, liveDistance, releaseRange,
                        active.candidate.radius(), tracking, intersects)) continue;

                double nearestDistance = SmallPlanetProximityPolicy.nearestDistanceSquared(analyticDistance, liveDistance);
                SmallPlanetField.Candidate selectedPosition = liveDistance <= analyticDistance ? live : analytic;
                if (selectedPosition == null) selectedPosition = live;
                Wanted activeCandidate = new Wanted(active.candidate, selectedPosition, active.system, active.orbit,
                        active.starX, active.starY, active.starZ);
                byId.put(entry.getKey(), activeCandidate);
                distanceById.put(entry.getKey(), nearestDistance);
                if (tracking || intersects) {
                    protectedActive.put(entry.getKey(), activeCandidate);
                    protectedDistances.merge(entry.getKey(), nearestDistance, Math::min);
                }
            }

            nearby = new ArrayList<>(byId.values());
            nearby.sort(Comparator.comparingDouble((Wanted candidate) -> distanceById.getOrDefault(
                            candidate.anchor().id(), distanceSquared(player, candidate.current())))
                    .thenComparing(candidate -> candidate.anchor().id()));
            for (int i = 0; i < Math.min(perPlayer, nearby.size()); i++) {
                Wanted candidate = nearby.get(i);
                double distance = Math.sqrt(distanceById.getOrDefault(candidate.anchor().id(),
                        distanceSquared(player, candidate.current())));
                candidates.putIfAbsent(candidate.anchor().id(), candidate);
                nearest.merge(candidate.anchor().id(), distance, Math::min);
            }
        }

        // An occupant or a player still tracking the body outranks proximity caps: release must never remove
        // the ground beneath a player. Active incarnations already consume their physicalization slots.
        for (var entry : protectedActive.entrySet()) {
            candidates.put(entry.getKey(), entry.getValue());
            nearest.merge(entry.getKey(), Math.sqrt(protectedDistances.getOrDefault(entry.getKey(), Double.POSITIVE_INFINITY)), Math::min);
        }

        var global = new ArrayList<>(nearest.keySet());
        global.sort(Comparator.comparingDouble((SmallPlanetField.ObjectId id) -> nearest.get(id))
                .thenComparing(Comparator.naturalOrder()));
        int globalCap = XenoSpaceConfig.smallPlanetMaxGlobal();
        if (global.size() > globalCap) global.subList(globalCap, global.size()).clear();
        var selected = new java.util.HashSet<>(global);
        selected.addAll(protectedActive.keySet());

        for (var entry : new ArrayList<>(runtime.active.entrySet()))
            if (!selected.contains(entry.getKey())) release(level, runtime, entry.getKey(), entry.getValue(), "outside selected proximity budget");
        if (runtime.building != null && !selected.contains(runtime.building.id()))
            abandonBuild(level, runtime, "candidate left selected proximity budget");

        runtime.wanted.clear();
        double activateRange = XenoSpaceConfig.smallPlanetActivateRange();
        for (SmallPlanetField.ObjectId id : global) {
            if (runtime.active.containsKey(id)) continue;
            Wanted candidate = candidates.get(id);
            if (candidate != null && nearest.get(id) <= activateRange + candidate.anchor().radius()) runtime.wanted.add(candidate);
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
        SpaceHomeOrbitalFrame.Focus homeFocus = SpaceHomeOrbitalFrame.resolve(level, chart);
        double homeFrameY = homeFocus == null ? Double.NaN : homeFocus.y();
        var charted = new ArrayList<SmallPlanetField.Sphere>(chart.size());
        for (int slot = 0; slot < chart.size(); slot++) {
            var live = PhysicalEarth.liveCenter(level, chart.body(slot).id());
            double cx = live == null ? chart.x(slot) : live.x();
            double cy = live == null ? SpaceHomeOrbitalFrame.chartY(chart, slot, homeFrameY) : live.y();
            double cz = live == null ? chart.z(slot) : live.z();
            double radius = live == null ? chart.body(slot).radiusBlocks()
                    : PhysicalEarth.liveRadius(level, chart.body(slot).id());
            if (Double.isFinite(cx) && Double.isFinite(cy) && Double.isFinite(cz)
                    && Double.isFinite(radius) && radius > 0)
                charted.add(new SmallPlanetField.Sphere(cx, cy, cz, radius));
        }
        long seed = net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(server);
        var frontier = net.bullettrain.xenopixelsmod.space.universe.FrontierBindings.get(level);
        var candidates = new ArrayList<>(SmallPlanetField.nearby(
                seed, x, y, z, range,
                SmallPlanetField.MAX_PLANETS_PER_SYSTEM, charted, AsteroidFieldSync.belts(state),
                cell -> frontier.systemAt(seed, cell)));
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

    private static SmallPlanetField.Candidate analyticPosition(Active active, double seconds) {
        if (active.orbit == null || active.system == null) return null;
        double[] position = new double[3];
        if (XenoSpaceConfig.realOrbits()) active.orbit.positionAt(seconds, position);
        else active.orbit.positionAt(active.orbit.epochSeconds(), position);
        return active.candidate.atPosition(position[0], position[1], position[2]);
    }

    private static boolean intersectsInflated(ServerPlayer player, ServerSubLevel body, double margin) {
        var bodyBounds = body.boundingBox();
        var playerBounds = player.getBoundingBox();
        return playerBounds.maxX >= bodyBounds.minX() - margin && playerBounds.minX <= bodyBounds.maxX() + margin
                && playerBounds.maxY >= bodyBounds.minY() - margin && playerBounds.minY <= bodyBounds.maxY() + margin
                && playerBounds.maxZ >= bodyBounds.minZ() - margin && playerBounds.minZ <= bodyBounds.maxZ() + margin;
    }

    private static boolean hasNearbyPlayer(ServerLevel level, ServerSubLevel body) {
        for (ServerPlayer player : level.players())
            if (body.getTrackingPlayers().contains(player.getUUID())
                    || intersectsInflated(player, body, OCCUPANT_RELEASE_MARGIN)) return true;
        return false;
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
            if (!net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.prepare(
                    level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET, job)) {
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
            if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0
                    && millisBudget <= 0.0 && job.installed() > 0) return;
            boolean complete = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.step(
                    level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.SMALL_PLANET,
                    job, stepBudget, millisBudget);
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
        runtime.active.put(candidate.id(), new Active(candidate, body.getUniqueId(), identity.getDouble("mass"),
                runtime.buildingOrbit, runtime.buildingSystem, runtime.buildingStarX, runtime.buildingStarY, runtime.buildingStarZ));
        runtime.buildingOrbit = null; runtime.buildingSystem = null;
        var buildResult = job.result();
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordBuild(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime(), blocks,
                buildResult.fillMillis(), buildResult.installMillis(), job.maxServerSliceMillis());
        runtime.built++;
        var builtAt = body.logicalPose().position();
        XenoPixelsMod.LOGGER.info("Small planet built: id={} profile={} radius={} voxels={} at=({}, {}, {}) uuid={} maxBatchMs={}",
                candidate.id().bodyId(), candidate.profile(), candidate.radius(), blocks,
                Math.round(builtAt.x()), Math.round(builtAt.y()), Math.round(builtAt.z()), body.getUniqueId(),
                String.format("%.2f", runtime.maxBatchNanos / 1_000_000.0));
        SpaceVisualSync.resendPlanetBindings(level);
    }

    private static void startNext(ServerLevel level, Runtime runtime) {
        var container = SubLevelContainer.getContainer(level);
        var chart = SpaceChartService.state(level.getServer()).runtime.chart;
        int sol = chart.slotOf("xenopixelsmod:sol");
        if (sol < 0) return;
        double seconds = SpaceOrbitClock.seconds(level.getServer());
        var band = SableRemovalBand.forDimension(level.dimension().location());
        for (Wanted wanted : runtime.wanted) {
            SmallPlanetField.Candidate candidate = wanted.anchor();
            if (runtime.active.containsKey(candidate.id())) continue;
            UniverseField.StarSystem star = wanted.system();
            SmallPlanetOrbit orbit = wanted.orbit();
            double[] buildPosition = new double[3];
            if (XenoSpaceConfig.realOrbits()) orbit.positionAt(seconds, buildPosition);
            else { buildPosition[0] = candidate.x(); buildPosition[1] = candidate.y(); buildPosition[2] = candidate.z(); }
            SmallPlanetField.Candidate buildAt = candidate.atPosition(buildPosition[0], buildPosition[1], buildPosition[2]);
            if (XenoSpaceConfig.realOrbits()) {
                double bodyHalfHeight = SmallPlanetOrbitSafety.rotatedVoxelHalfExtent(candidate.radius());
                if (!SmallPlanetOrbitSafety.orbitFitsRemovalBand(orbit, bodyHalfHeight,
                        band.minY(), band.maxY(), level.getMinBuildHeight(), band.voidseaExtended())) continue;
            }
            int extent = candidate.radius();
            boolean blocked = false;
            for (var other : container.getAllSubLevels()) {
                var box = other.boundingBox();
                if (box.maxX() >= buildAt.x() - extent && box.minX() <= buildAt.x() + extent
                        && box.maxY() >= buildAt.y() - extent && box.minY() <= buildAt.y() + extent
                        && box.maxZ() >= buildAt.z() - extent && box.minZ() <= buildAt.z() + extent) {
                    blocked = true; break;
                }
            }
            if (blocked) continue;
            // Reserve the rotated voxel-envelope bounds too; collision or torque can rotate the body after build.
            double half = SmallPlanetOrbitSafety.rotatedVoxelHalfExtent(extent);
            if (!SmallPlanetOrbitSafety.currentBoundsFitRemovalBand(buildAt.y() - half, buildAt.y() + half,
                    band.minY(), band.maxY(), level.getMinBuildHeight(), band.voidseaExtended())) continue;
            long now = level.getServer().overworld().getGameTime();
            if (!net.bullettrain.xenopixelsmod.space.physics.SpaceBuildActivationGate.tryAcquire(level.getServer(),
                    net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.Owner.SMALL_PLANET, now)) {
                runtime.nextSpawn = net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.denialRetryTick(now);
                return;
            }

            runtime.building = candidate;
            runtime.buildingOrbit = orbit;
            runtime.buildingSystem = star;
            runtime.buildingStarX = wanted.starX(); runtime.buildingStarY = wanted.starY(); runtime.buildingStarZ = wanted.starZ();
            CompoundTag marker = candidateTag(candidate);
            marker.putBoolean("building", true);
            SmallPlanetVoxels voxels = new SmallPlanetVoxels(candidate);
            runtime.job = PlotBodyBuilder.start(level, new Vector3d(buildAt.x(), buildAt.y(), buildAt.z()),
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
        runtime.job = null; runtime.building = null; runtime.buildingOrbit = null; runtime.buildingSystem = null;
        boolean ticketed = runtime.buildTicketed; runtime.buildTicketed = false;
        if (job != null && job.body() != null && !job.body().isRemoved()) {
            var body = job.body();
            if (ticketed) SubLevelContainer.getContainer(level).removeForceLoadTicket(body, TICKET, candidate.id().bodyId());
            SubLevelContainer.getContainer(level).removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        }
        if (candidate != null) XenoPixelsMod.LOGGER.info("Small planet build discarded ({}): {}", why, candidate.id().bodyId());
    }

    private static void release(ServerLevel level, Runtime runtime, SmallPlanetField.ObjectId id, Active active, String why) {
        if (!SmallPlanetTickPolicy.mayRelease(runtime.placementTestBodies.contains(active.body),
                active.editedPinned, hasPendingPlacement(runtime, active.body))) return;
        var container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) {
            runtime.active.remove(id);
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime());
            return;
        }
        if (hasNearbyPlayer(level, body)) return;
        double mass = body.getMassTracker().getMass();
        CompoundTag userData = body.getUserDataTag();
        boolean explicitTouch = hasTouchedFlag(userData);
        boolean massChanged = massChanged(active.originalMass, mass);
        if (massChanged && !explicitTouch) {
            if (!persistTouchedFlag(body)) {
                XenoPixelsMod.LOGGER.error("Small planet {} has an unmarked mass delta and its edited marker could not be persisted; retaining and retrying",
                        id.bodyId());
                return;
            }
            explicitTouch = true;
        }
        boolean touched = explicitTouch || massChanged;
        if (touched) {
            // Preserve edited planets as tagged, ticketed bodies on the canonical orbit. Player break/place events
            // reserve a separate bounded slot before editing; this fallback catches external block mutations.
            active.editedPinned = true;
            if (editedActiveCount(runtime) > XenoSpaceConfig.smallPlanetMaxEditedGlobal())
                XenoPixelsMod.LOGGER.error("Edited small planet {} exceeded configured edited-body cap {} through an external mutation; preserving it instead of discarding player data",
                        id.bodyId(), XenoSpaceConfig.smallPlanetMaxEditedGlobal());
            return;
        }
        container.removeForceLoadTicket(body, TICKET, id.bodyId());
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        runtime.active.remove(id);
        runtime.removed++;
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.SMALL_PLANET, System.nanoTime());
        XenoPixelsMod.LOGGER.info("Small planet released ({}): id={}", why, id.bodyId());
        SpaceVisualSync.resendPlanetBindings(level);
    }

    private static boolean hasTouchedFlag(CompoundTag userData) {
        return userData != null && userData.contains(TAG) && userData.getCompound(TAG).getBoolean("touched");
    }

    private static boolean persistTouchedFlag(ServerSubLevel body) {
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return false;
        if (hasTouchedFlag(data)) return true;
        CompoundTag updated = data.copy();
        updated.getCompound(TAG).putBoolean("touched", true);
        body.setUserDataTag(updated);
        return hasTouchedFlag(body.getUserDataTag());
    }

    private static void flushPlacementTestDimension(MinecraftServer server, Runtime runtime,
                                                     ServerLevel alreadyFlushed, boolean saveBoundary) {
        if (runtime.placementTestBodies.isEmpty() || runtime.placementTestDimension == null) return;
        ServerLevel testLevel = server.getLevel(
                ResourceKey.create(Registries.DIMENSION, runtime.placementTestDimension));
        if (testLevel != null && testLevel != alreadyFlushed)
            flushPendingPlacements(testLevel, runtime,
                    saveBoundary ? Long.MAX_VALUE : testLevel.getGameTime(), saveBoundary);
    }

    private static boolean isPlacementHost(ServerLevel level) {
        if (PhysicalEarth.HOST.equals(level.dimension().location())) return true;
        Runtime runtime = RUNTIMES.get(level.getServer());
        return runtime != null && runtime.placementTestDimension != null
                && runtime.placementTestDimension.equals(level.dimension().location());
    }

    private static boolean massChanged(double originalMass, double currentMass) {
        return Double.isFinite(originalMass) && Double.isFinite(currentMass)
                && Math.abs(currentMass - originalMass) > Math.max(1, originalMass * 1e-3);
    }

    private static SmallPlanetEditedBodyPolicy.Admission reserveEdit(LevelAccessor level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)) return SmallPlanetEditedBodyPolicy.Admission.UNMANAGED;
        var container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return SmallPlanetEditedBodyPolicy.Admission.UNMANAGED;
        var plot = plotAtBlock(container, position);
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body)) return SmallPlanetEditedBodyPolicy.Admission.UNMANAGED;
        return reserveEdit(serverLevel, body, position);
    }

    /** Sable's ChunkPos overload forwards chunk X/Z to getPlot(int,int); avoid allocating a ChunkPos per voxel. */
    private static dev.ryanhcode.sable.sublevel.plot.LevelPlot plotAtBlock(
            SubLevelContainer container, BlockPos position) {
        return container.getPlot(position.getX() >> 4, position.getZ() >> 4);
    }

    private static SmallPlanetEditedBodyPolicy.Admission reserveEdit(ServerLevel serverLevel, ServerSubLevel body,
                                                                      BlockPos position) {
        EditDecision decision = reserveEditDecision(serverLevel, body, position, body.getUserDataTag());
        Runtime runtime = RUNTIMES.get(serverLevel.getServer());
        if (runtime != null)
            runtime.kiAdmissionCache.put(serverLevel.getGameTime(), body.getUniqueId(),
                    decision.admission(), decision.alreadyPinned());
        return decision.admission();
    }

    /** Fast host-write gate used by the LevelChunk RETURN mixin before any plot or user-data lookup. */
    public static boolean hasPendingEditReservations(ServerLevel level) {
        if (!isPlacementHost(level)) return false;
        Runtime runtime = RUNTIMES.get(level.getServer());
        // Snapshot-captured placements reach LevelChunk.setBlockState before NeoForge posts
        // EntityPlaceEvent, which is where their edit reservation is created. Keep recording
        // pre-event states during that capture window even when the ledger is still empty.
        boolean pending = runtime != null
                && runtime.editReservations.pendingPositionCount(level.getGameTime()) > 0;
        return runtime != null && SmallPlanetBlockMutationGate.shouldInspect(
                level.restoringBlockSnapshots, level.captureBlockSnapshots, pending);
    }

    private static EditDecision reserveEditDecision(ServerLevel serverLevel, ServerSubLevel body,
                                                    BlockPos position, CompoundTag data) {
        if (data == null || !data.contains(TAG))
            return new EditDecision(SmallPlanetEditedBodyPolicy.Admission.UNMANAGED, false);
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        Runtime runtime = RUNTIMES.get(serverLevel.getServer());
        Active active = candidate == null || runtime == null ? null : runtime.active.get(candidate.id());
        if (active == null || !active.body.equals(body.getUniqueId()))
            return new EditDecision(XenoSpaceConfig.enabled() && XenoSpaceConfig.smallPlanetPhysical()
                    ? SmallPlanetEditedBodyPolicy.Admission.NOT_ACTIVE
                    : SmallPlanetEditedBodyPolicy.Admission.FEATURE_DISABLED, false);
        boolean alreadyPinned = active.editedPinned;
        long tick = serverLevel.getGameTime();
        if (!runtime.editReservations.reserve(tick, body.getUniqueId(), position, editedActiveCount(runtime),
                XenoSpaceConfig.smallPlanetMaxEditedGlobal(), alreadyPinned))
            return new EditDecision(SmallPlanetEditedBodyPolicy.Admission.CAP_REACHED, alreadyPinned);
        return new EditDecision(SmallPlanetEditedBodyPolicy.Admission.ALLOWED, alreadyPinned);
    }

    /** Commits an edit only after the host chunk reports a changed block outside snapshot capture/rollback. */
    public static void blockMutationCommitted(LevelAccessor level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel) || !isPlacementHost(serverLevel)) return;
        if (serverLevel.captureBlockSnapshots || serverLevel.restoringBlockSnapshots) return;
        Runtime runtime = RUNTIMES.get(serverLevel.getServer());
        if (runtime == null) return;
        var container = SubLevelContainer.getContainer(serverLevel);
        if (container == null) return;
        var plot = plotAtBlock(container, position);
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body) || body.isRemoved()) return;
        commitReservedMutation(serverLevel, runtime, body, position);
    }

    /** Captures Forge's pre-event block state so a later canceled placement can be distinguished from a commit. */
    public static void captureBlockMutation(LevelAccessor accessor, BlockPos position,
                                            BlockState previous, BlockState requested) {
        if (!(accessor instanceof ServerLevel level) || !isPlacementHost(level) || level.restoringBlockSnapshots) return;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return;
        var plot = plotAtBlock(container, position);
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body) || body.isRemoved()) return;
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return;
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        Runtime runtime = RUNTIMES.get(level.getServer());
        Active active = candidate == null || runtime == null ? null : runtime.active.get(candidate.id());
        if (active == null || !active.body.equals(body.getUniqueId())) return;
        PlacementKey key = new PlacementKey(level.dimension().location(), position.immutable());
        if (runtime.capturedMutations.size() >= MAX_PENDING_PLACEMENTS
                && !runtime.capturedMutations.containsKey(key)) return;
        CapturedMutation existing = runtime.capturedMutations.get(key);
        runtime.capturedMutations.put(key, new CapturedMutation(body.getUniqueId(),
                existing == null ? previous : existing.before(), requested, level.getGameTime()));
    }

    private static void commitReservedMutation(ServerLevel level, Runtime runtime, ServerSubLevel body, BlockPos position) {
        long tick = level.getGameTime();
        if (!runtime.editReservations.mayCommit(tick, body.getUniqueId(), position)) return;
        SmallPlanetKiAdmissionCache.Entry cached = runtime.kiAdmissionCache.get(tick, body.getUniqueId());
        if (cached != null && (cached.admission() != SmallPlanetEditedBodyPolicy.Admission.ALLOWED
                || cached.alreadyPinned())) return;
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return;
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        Active active = candidate == null ? null : runtime.active.get(candidate.id());
        if (active == null || !active.body.equals(body.getUniqueId()) || active.editedPinned) return;
        if (!runtime.editReservations.consume(tick, body.getUniqueId(), position)) return;

        CompoundTag updated = data.copy();
        updated.getCompound(TAG).putBoolean("touched", true);
        body.setUserDataTag(updated);
        active.editedPinned = true;
        runtime.editReservations.releaseAllPlacements(body.getUniqueId());
        runtime.kiAdmissionCache.markPinned(level.getGameTime(), body.getUniqueId());
    }

    /** Commits an admitted snapshot placement after its final state survives the tick boundary. */
    private static void commitDeferredPlacement(ServerLevel level, Runtime runtime, ServerSubLevel body,
                                                UUID admittedBody) {
        if (!body.getUniqueId().equals(admittedBody) || body.isRemoved()) {
            runtime.editReservations.releasePlacement(admittedBody);
            return;
        }
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) {
            runtime.editReservations.releasePlacement(admittedBody);
            return;
        }
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        Active active = candidate == null ? null : runtime.active.get(candidate.id());
        if (active == null || !active.body.equals(admittedBody)) {
            runtime.editReservations.releasePlacement(admittedBody);
            return;
        }
        if (!persistTouchedFlag(body))
            throw new IllegalStateException("could not persist the small-planet edited marker");
        active.editedPinned = true;
        runtime.kiAdmissionCache.markPinned(level.getGameTime(), admittedBody);
        runtime.editReservations.releaseAllPlacements(admittedBody);
    }

    private static boolean queuePlacementCommit(LevelAccessor accessor, BlockPos position) {
        if (!(accessor instanceof ServerLevel level) || !isPlacementHost(level)) return false;
        var container = SubLevelContainer.getContainer(level);
        if (container == null) return false;
        var plot = plotAtBlock(container, position);
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body)) return false;
        CompoundTag data = body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return false;
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        Runtime runtime = RUNTIMES.get(level.getServer());
        Active active = candidate == null || runtime == null ? null : runtime.active.get(candidate.id());
        if (active == null || !active.body.equals(body.getUniqueId())) return false;
        if (!SmallPlanetPlacementCommitPolicy.needsDeferredReservation(active.editedPinned)) return true;
        // The stop hook runs before Minecraft drains its final chunk-save work queue. Refuse a
        // placement that arrives after shutdown began; the save-head hook below handles anything
        // already accepted and pending before that boundary.
        if (runtime.stopping) return false;
        PlacementKey key = new PlacementKey(level.dimension().location(), position.immutable());
        CapturedMutation captured = runtime.capturedMutations.get(key);
        // Bucket use and other non-snapshot placements have no pre-event state to compare. Keep them
        // playable, but do not turn their permission reservation into a persistent edit marker. A
        // snapshot-captured placement without a record means overflow or a missed hook: refuse it rather
        // than silently losing the edit marker.
        if (captured == null || !captured.body().equals(body.getUniqueId()))
            return SmallPlanetPlacementCommitPolicy.allowWithoutCapture(level.captureBlockSnapshots);
        if (runtime.pendingPlacements.size() >= MAX_PENDING_PLACEMENTS
                && !runtime.pendingPlacements.containsKey(key)) return false;
        PendingPlacement previous = runtime.pendingPlacements.get(key);
        boolean sameBodyPending = previous != null && previous.body().equals(body.getUniqueId());
        runtime.pendingPlacements.put(key, new PendingPlacement(body.getUniqueId(), captured.before(),
                captured.requested(), sameBodyPending ? previous.queuedAtTick() : level.getGameTime(),
                sameBodyPending && previous.locationWarningLogged()));
        if (previous == null) runtime.editReservations.holdPlacement(body.getUniqueId());
        else if (!previous.body().equals(body.getUniqueId())) {
            runtime.editReservations.releasePlacement(previous.body());
            runtime.editReservations.holdPlacement(body.getUniqueId());
        }
        return true;
    }

    private static void flushPendingPlacements(ServerLevel level, Runtime runtime, long tick, boolean saveBoundary) {
        var iterator = runtime.pendingPlacements.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            PendingPlacement pending = entry.getValue();
            PlacementKey key = entry.getKey();
            if (!key.dimension().equals(level.dimension().location())) continue;
            BlockPos position = key.position();
            try {
                if (level.isOutsideBuildHeight(position)) {
                    if (!pending.locationWarningLogged()) {
                        XenoPixelsMod.LOGGER.error("Pending small-planet placement is outside host build height at {} for body {}; retaining it and edit-slot hold",
                                position, pending.body());
                        entry.setValue(pending.withLocationWarningLogged());
                    }
                    if (!saveBoundary) continue;
                    throw new IllegalStateException("placement position is outside host build height");
                }
                if (!level.hasChunkAt(position)) {
                    if (!saveBoundary) {
                        if (!pending.locationWarningLogged() && tick >= pending.queuedAtTick()
                                && tick - pending.queuedAtTick() >= 200) {
                            XenoPixelsMod.LOGGER.warn("Pending small-planet placement has waited {} ticks for an unloaded host chunk at {} for body {}; retaining it and its edit-slot hold",
                                    tick - pending.queuedAtTick(), position, pending.body());
                            entry.setValue(pending.withLocationWarningLogged());
                        }
                        continue;
                    }
                    throw new IllegalStateException("placement chunk is unloaded; avoiding a blocking save-boundary read");
                }
                BlockState current = level.getBlockState(position);
                if (SmallPlanetPlacementCommitPolicy.survived(pending.before(), pending.expectedState(), current)) {
                    var container = SubLevelContainer.getContainer(level);
                    if (container != null && container.getSubLevel(pending.body()) instanceof ServerSubLevel body
                            && !body.isRemoved()) {
                        commitDeferredPlacement(level, runtime, body, pending.body());
                    } else runtime.editReservations.releasePlacement(pending.body());
                } else runtime.editReservations.releasePlacement(pending.body());
                iterator.remove();
            } catch (RuntimeException error) {
                XenoPixelsMod.LOGGER.error("Could not finalize pending small-planet placement at {} for body {}; retaining it for retry",
                        position, pending.body(), error);
                if (saveBoundary) {
                    if (forcePinPendingPlacement(level, runtime, pending, position)) {
                        iterator.remove();
                        runtime.editReservations.releaseAllPlacements(pending.body());
                        XenoPixelsMod.LOGGER.warn("Conservatively pinned small planet {} after placement commit failed at {}; continuing save",
                                pending.body(), position);
                    } else {
                        // Keep both the pending record and its edit-slot hold. The live body must not be
                        // released just because a best-effort user-data write failed. A later tick/save
                        // can retry; clean-restart durability still depends on one of those writes succeeding.
                        XenoPixelsMod.LOGGER.error("Could not force-pin small planet {} after placement commit failed at {}; retaining pending record and edit-slot hold for retry while allowing world save",
                                pending.body(), position);
                    }
                }
            }
        }
        ResourceLocation flushedDimension = level.dimension().location();
        runtime.capturedMutations.entrySet().removeIf(entry -> entry.getKey().dimension().equals(flushedDimension)
                && entry.getValue().tick() < tick);
    }

    private static boolean forcePinPendingPlacement(ServerLevel level, Runtime runtime, PendingPlacement pending,
                                                    BlockPos position) {
        try {
            var container = SubLevelContainer.getContainer(level);
            if (container == null || !(container.getSubLevel(pending.body()) instanceof ServerSubLevel body)
                    || body.isRemoved()) return false;
            if (!persistTouchedFlag(body)) return false;
            SmallPlanetField.Candidate candidate = readCandidate(body.getUserDataTag().getCompound(TAG));
            Active active = candidate == null ? null : runtime.active.get(candidate.id());
            if (active != null && active.body.equals(pending.body())) active.editedPinned = true;
            runtime.kiAdmissionCache.markPinned(level.getGameTime(), pending.body());
            return true;
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.error("Could not force-pin small planet {} for pending placement at {}",
                    pending.body(), position, error);
            return false;
        }
    }

    private static boolean hasPendingPlacement(Runtime runtime, UUID body) {
        if (runtime.pendingPlacements.isEmpty()) return false;
        for (PendingPlacement pending : runtime.pendingPlacements.values())
            if (pending.body().equals(body)) return true;
        return false;
    }

    /** Called at ServerLevel.save HEAD, before Sable serializes plot data from saveLevelData(). */
    public static void beforeHostSave(ServerLevel level) {
        try {
            if (level == null || !isPlacementHost(level)) return;
            Runtime runtime = RUNTIMES.get(level.getServer());
            if (runtime != null) flushPendingPlacements(level, runtime, Long.MAX_VALUE, true);
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.error("Unexpected small-planet placement flush failure at host save boundary; allowing world save to continue",
                    error);
        }
    }

    /** Admission hook for DMZ's common Ki-grief permission check on host plot coordinates. */
    public static boolean allowKiMutation(LevelAccessor level, BlockPos position) {
        if (level instanceof ServerLevel serverLevel) {
            var container = SubLevelContainer.getContainer(serverLevel);
            if (container != null) {
                var plot = plotAtBlock(container, position);
                if (plot != null && plot.getSubLevel() instanceof ServerSubLevel body) {
                    Runtime runtime = RUNTIMES.get(serverLevel.getServer());
                    if (runtime != null) {
                        long tick = serverLevel.getGameTime();
                        SmallPlanetKiAdmissionCache.Entry cached = runtime.kiAdmissionCache.get(tick, body.getUniqueId());
                    if (cached != null && cached.admission() != SmallPlanetEditedBodyPolicy.Admission.ALLOWED)
                        return SmallPlanetEditedBodyPolicy.allowMutation(cached.admission());
                        if (cached != null) {
                            // Cache the per-body decision before touching user data: register every actual
                            // write position without an NBT lookup on each voxel of a large Ki blast.
                            boolean reserved = runtime.editReservations.reserve(tick, body.getUniqueId(), position,
                                    editedActiveCount(runtime), XenoSpaceConfig.smallPlanetMaxEditedGlobal(), cached.alreadyPinned());
                            if (!reserved) {
                                runtime.kiAdmissionCache.put(tick, body.getUniqueId(),
                                        SmallPlanetEditedBodyPolicy.Admission.CAP_REACHED, cached.alreadyPinned());
                                return false;
                            }
                            return true;
                        }
                    }
                    CompoundTag data = body.getUserDataTag();
                    if (data == null || !data.contains(TAG)) {
                        if (runtime != null)
                            runtime.kiAdmissionCache.put(serverLevel.getGameTime(), body.getUniqueId(),
                                    SmallPlanetEditedBodyPolicy.Admission.UNMANAGED, false);
                        return true;
                    }
                    if (runtime != null) {
                        long tick = serverLevel.getGameTime();
                        EditDecision decision = reserveEditDecision(serverLevel, body, position, data);
                        runtime.kiAdmissionCache.put(tick, body.getUniqueId(), decision.admission(), decision.alreadyPinned());
                        return SmallPlanetEditedBodyPolicy.allowMutation(decision.admission());
                    }
                }
            }
        }
        return SmallPlanetEditedBodyPolicy.allowMutation(reserveEdit(level, position));
    }

    @SubscribeEvent public static void blockBroken(BlockEvent.BreakEvent event) {
        if (event.isCanceled()) return;
        SmallPlanetEditedBodyPolicy.Admission admission = reserveEdit(event.getLevel(), event.getPos());
        if (!SmallPlanetEditedBodyPolicy.allowMutation(admission)) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal(SmallPlanetEditedBodyPolicy.refusalMessage(admission))
                    .withStyle(ChatFormatting.RED));
        }
    }

    @SubscribeEvent public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled()) return;
        SmallPlanetEditedBodyPolicy.Admission admission = reserveEdit(event.getLevel(), event.getPos());
        if (!SmallPlanetEditedBodyPolicy.allowMutation(admission)) {
            event.setCanceled(true);
            if (event.getEntity() instanceof ServerPlayer player)
                player.sendSystemMessage(Component.literal(SmallPlanetEditedBodyPolicy.refusalMessage(admission))
                        .withStyle(ChatFormatting.RED));
            return;
        }
        if (admission == SmallPlanetEditedBodyPolicy.Admission.ALLOWED
                && !queuePlacementCommit(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            if (event.getLevel() instanceof ServerLevel level) {
                var container = SubLevelContainer.getContainer(level);
                if (container != null) {
                    var plot = plotAtBlock(container, event.getPos());
                    if (plot != null && plot.getSubLevel() instanceof ServerSubLevel body) {
                        Runtime runtime = RUNTIMES.get(level.getServer());
                        if (runtime != null) runtime.editReservations.release(level.getGameTime(), body.getUniqueId(), event.getPos());
                    }
                }
            }
        }
    }

    /**
     * Player-held BucketItem writes from its innate use() path, outside placement snapshots; reserve the same ray
     * target first. Dispensers and third-party fluid automation do not post RightClickItem and are not covered here.
     */
    @SubscribeEvent public static void bucketRightClick(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getItemStack().getItem() instanceof net.minecraft.world.item.BucketItem bucket)
                || !(event.getLevel() instanceof ServerLevel level)
                || !PhysicalEarth.HOST.equals(level.dimension().location())
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        Vec3 start = player.getEyePosition();
        Vec3 end = start.add(player.calculateViewVector(player.getXRot(), player.getYRot())
                .scale(player.blockInteractionRange()));
        ClipContext.Fluid fluidMode = bucket.content == Fluids.EMPTY
                ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, fluidMode, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;
        for (BlockPos position : SmallPlanetBucketMutationPolicy.candidatePositions(hit.getBlockPos(), hit.getDirection())) {
            SmallPlanetEditedBodyPolicy.Admission admission = reserveEdit(level, position);
            if (SmallPlanetEditedBodyPolicy.allowMutation(admission)) continue;
            event.setCanceled(true);
            event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
            player.sendSystemMessage(Component.literal(SmallPlanetEditedBodyPolicy.refusalMessage(admission))
                    .withStyle(ChatFormatting.RED));
            return;
        }
    }

    /** Explosions (including Ki attacks) bypass BreakEvent, so reserve the edited-body cap before allowing blocks out. */
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        event.getAffectedBlocks().removeIf(position -> !allowKiMutation(level, position));
    }

    private static void adopt(ServerLevel level, Runtime runtime) {
        if (runtime.adopted) return;
        boolean retry = false;
        var container = SubLevelContainer.getContainer(level);
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        if (!runtime.releaseDecisionsLoaded) {
            runtime.releasedQuarantineIds.addAll(saved.releasedQuarantineIds());
            for (UUID bodyId : runtime.releasedQuarantineIds) queueReleaseMarkerAudit(runtime, bodyId);
            runtime.releasePending.addAll(runtime.releasedQuarantineIds);
            runtime.releaseDecisionsLoaded = true;
        }
        var taggedBodies = new ArrayList<ServerSubLevel>();
        var winners = new HashMap<SmallPlanetField.ObjectId, ServerSubLevel>();
        for (var sub : new ArrayList<>(container.getAllSubLevels())) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved()) continue;
            prioritizeReleaseMarkerAudit(runtime, body.getUniqueId());
            CompoundTag userData = body.getUserDataTag();
            if (runtime.releasePending.contains(body.getUniqueId()) && (userData == null || !userData.contains(TAG))) {
                try {
                    reconcileOwnedTickets(container, body, null);
                    runtime.releasePending.remove(body.getUniqueId());
                    prioritizeReleaseMarkerAudit(runtime, body.getUniqueId());
                    runtime.quarantined.remove(body.getUniqueId());
                } catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not remove Xeno small-planet tickets from released body {}",
                            body.getUniqueId(), failure);
                }
            }
            if (userData == null || !userData.contains(TAG)) continue;
            taggedBodies.add(body);
            CompoundTag tag = userData.getCompound(TAG);
            SmallPlanetField.Candidate candidate = readCandidate(tag);
            if (candidate == null) continue;
            if (!tag.getBoolean("building") && !hasTouchedFlag(userData)
                    && massChanged(tag.getDouble("mass"), body.getMassTracker().getMass())) {
                try {
                    if (!persistTouchedFlag(body)) {
                        retry = true;
                        XenoPixelsMod.LOGGER.error("Could not persist edited marker for small-planet body {}; keeping its election priority conservative and retrying adoption",
                                body.getUniqueId());
                    }
                    CompoundTag currentData = body.getUserDataTag();
                    if (currentData != null && currentData.contains(TAG)) tag = currentData.getCompound(TAG);
                } catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not persist edited marker for small-planet body {}; keeping its election priority conservative and retrying adoption",
                            body.getUniqueId(), failure);
                }
            }
            ServerSubLevel incumbent = winners.get(candidate.id());
            if (incumbent == null || preferAdoptionBody(body, tag, incumbent, runtime))
                winners.put(candidate.id(), body);
        }
        // A previous retry may already have adopted a body before another body's ticket failed.
        // Remove a losing in-memory mapping before processing the complete group, so iteration
        // order cannot preserve an unedited incumbent over a newly discovered edited copy.
        for (var entry : new ArrayList<>(runtime.active.entrySet())) {
            ServerSubLevel winner = winners.get(entry.getKey());
            if (winner != null && !winner.getUniqueId().equals(entry.getValue().body))
                runtime.active.remove(entry.getKey());
        }
        for (ServerSubLevel body : taggedBodies) {
            if (body.isRemoved() || body.getUserDataTag() == null || !body.getUserDataTag().contains(TAG)) continue;
            CompoundTag userData = body.getUserDataTag();
            boolean released = quarantineReleased(saved, body);
            CompoundTag tag = userData.getCompound(TAG);
            SmallPlanetField.Candidate candidate = readCandidate(tag);
            boolean building = tag.getBoolean("building");
            boolean edited = editedForAdoption(body, tag, candidate, runtime);
            Active existing = candidate == null ? null : runtime.active.get(candidate.id());
            ServerSubLevel winner = candidate == null ? null : winners.get(candidate.id());
            boolean duplicate = winner != null && !winner.getUniqueId().equals(body.getUniqueId());
            if (released) {
                try {
                    // The durable release marker remains after the operator decision. Reconcile
                    // idempotently to this body's desired state: canonical bodies retain one
                    // identity ticket, while malformed/losing bodies retain no Xeno ticket.
                    String keepKey = candidate != null && winner != null
                            && winner.getUniqueId().equals(body.getUniqueId()) ? candidate.id().bodyId() : null;
                    reconcileOwnedTickets(container, body, keepKey);
                    Set<String> desiredKeys = keepKey == null ? Set.of() : Set.of(keepKey);
                    if (keepKey != null && !ownedTicketKeys(container, body).contains(keepKey))
                        container.addForceLoadTicket(body, TICKET, keepKey);
                    if (!desiredKeys.equals(ownedTicketKeys(container, body)))
                        throw new IllegalStateException("released body ticket set does not match its canonical disposition");
                    runtime.quarantined.remove(body.getUniqueId());
                    runtime.releasePending.remove(body.getUniqueId());
                    prioritizeReleaseMarkerAudit(runtime, body.getUniqueId());
                } catch (RuntimeException failure) {
                    retry = true;
                    runtime.quarantined.putIfAbsent(body.getUniqueId(),
                            new Quarantined(body.getUniqueId(), "release-ticket-pending", true));
                    XenoPixelsMod.LOGGER.error("Released small-planet quarantine {} still has a ticket-reconciliation failure; retaining its safety hold and retrying",
                            body.getUniqueId(), failure);
                    continue;
                }
            }
            if (candidate == null && released) continue;
            if (released && candidate != null && duplicate) {
                // An explicit release is never undone. A released duplicate that loses to the
                // deterministic canonical copy is preserved without a hold or Xeno ticket.
                try { reconcileOwnedTickets(container, body, null); }
                catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not purge Xeno tickets from released duplicate small-planet body {}",
                            body.getUniqueId(), failure);
                }
                continue;
            }
            if (existing != null && !duplicate) continue;
            SmallPlanetAdoptionPolicy.Disposition disposition = released && candidate != null && !duplicate
                    ? SmallPlanetAdoptionPolicy.Disposition.ADOPT
                    : SmallPlanetAdoptionPolicy.classify(candidate != null, building, edited, duplicate);
            if (disposition == SmallPlanetAdoptionPolicy.Disposition.REMOVE) {
                boolean removed = false;
                try {
                    reconcileOwnedTickets(container, body, null);
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    removed = true;
                } catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not safely remove incomplete small-planet body {}",
                            body.getUniqueId(), failure);
                }
                if (removed)
                    XenoPixelsMod.LOGGER.warn("Discarded unedited incomplete/duplicate small-planet body {}", body.getUniqueId());
                continue;
            }

            if (disposition == SmallPlanetAdoptionPolicy.Disposition.QUARANTINE) {
                // Bad identity or an edited duplicate is not evidence that its plot can be
                // deleted. Give it a stable UUID-keyed Sable ticket and leave its plot data
                // untouched for repair; never assign it a guessed logical planet identity.
                String quarantineKey = "quarantine/" + body.getUniqueId();
                try {
                    reconcileOwnedTickets(container, body, quarantineKey);
                } catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not remove stale Xeno tickets from quarantined small-planet body {}",
                            body.getUniqueId(), failure);
                }
                try { container.addForceLoadTicket(body, TICKET, quarantineKey); }
                catch (RuntimeException failure) {
                    retry = true;
                    XenoPixelsMod.LOGGER.error("Could not add quarantine ticket for small-planet body {}; retaining it in the current container and retrying",
                            body.getUniqueId(), failure);
                }
                String reason = candidate == null ? "unreadable-identity"
                        : "edited-duplicate:" + candidate.id().bodyId();
                runtime.quarantined.putIfAbsent(body.getUniqueId(),
                        new Quarantined(body.getUniqueId(), reason, edited || candidate == null));
                XenoPixelsMod.LOGGER.error(
                        "Preserving and holding quarantined small-planet body uuid={} candidate={} edited={} building={} ticketKey={}",
                        body.getUniqueId(), candidate == null ? "unreadable" : candidate.id().bodyId(),
                        edited, building, quarantineKey);
                continue;
            }

            Active restored;
            try {
                restored = active(level, candidate, body, tag.getDouble("mass"));
            } catch (RuntimeException failure) {
                runtime.failed++;
                retry = true;
                XenoPixelsMod.LOGGER.error("Could not restore orbital state for small planet {}; no ticket was acquired",
                        candidate.id().bodyId(), failure);
                continue;
            }
            try {
                reconcileOwnedTickets(container, body, candidate.id().bodyId());
                container.addForceLoadTicket(body, TICKET, candidate.id().bodyId());
            } catch (RuntimeException failure) {
                try { container.removeForceLoadTicket(body, TICKET, candidate.id().bodyId()); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                runtime.failed++;
                retry = true;
                XenoPixelsMod.LOGGER.error("Could not acquire force-load ticket for adopted small planet {}",
                        candidate.id().bodyId(), failure);
                continue;
            }
            if (building) {
                // A valid interrupted body that carries an edit marker is now being
                // deliberately retained. Mark the recovery complete so later starts do
                // not repeatedly treat it as an abandoned construction.
                CompoundTag repaired = body.getUserDataTag().copy();
                repaired.getCompound(TAG).putBoolean("building", false);
                repaired.getCompound(TAG).putBoolean("touched", true);
                body.setUserDataTag(repaired);
            }
            boolean restoredFlag = hasTouchedFlag(body.getUserDataTag());
            double restoredMass = body.getMassTracker().getMass();
            boolean restoredMassChanged = massChanged(restored.originalMass, restoredMass);
            if (restoredMassChanged && !restoredFlag) {
                restoredFlag = persistTouchedFlag(body);
                if (!restoredFlag)
                    XenoPixelsMod.LOGGER.error("Adopted small planet {} has a mass delta but could not persist its edited marker; retrying on release (originalMass={}, currentMass={})",
                            candidate.id().bodyId(), restored.originalMass, restoredMass);
                else
                    XenoPixelsMod.LOGGER.warn("Adopted small planet {} as edited from mass delta and persisted its edited marker (originalMass={}, currentMass={})",
                            candidate.id().bodyId(), restored.originalMass, restoredMass);
            }
            restored.editedPinned = restoredFlag;
            if (restored.editedPinned && editedActiveCount(runtime) >= XenoSpaceConfig.smallPlanetMaxEditedGlobal())
                XenoPixelsMod.LOGGER.error("Adopted edited small planet {} exceeds the configured edited-body cap {}; preserving its identity and orbit anyway",
                        candidate.id().bodyId(), XenoSpaceConfig.smallPlanetMaxEditedGlobal());
            runtime.active.put(candidate.id(), restored);
            XenoPixelsMod.LOGGER.info("Small planet adopted from Sable storage: id={} uuid={}", candidate.id().bodyId(), body.getUniqueId());
        }
        for (UUID bodyId : new ArrayList<>(runtime.releasePending)) {
            if (container.getSubLevel(bodyId) instanceof ServerSubLevel body && !body.isRemoved()) {
                retry = true;
            } else if (!container.getAllTickets().containsKey(bodyId)) {
                runtime.releasePending.remove(bodyId);
            }
        }
        runtime.adopted = !retry;
        runtime.nextAdoptRetryTick = SpaceOrbitClock.gameTick(level.getServer()) + PASS_TICKS;
    }

    private static boolean preferAdoptionBody(ServerSubLevel candidateBody, CompoundTag candidateTag,
                                              ServerSubLevel incumbentBody, Runtime runtime) {
        CompoundTag incumbentData = incumbentBody.getUserDataTag();
        if (incumbentData == null || !incumbentData.contains(TAG)) return true;
        CompoundTag incumbentTag = incumbentData.getCompound(TAG);
        SmallPlanetField.Candidate candidate = readCandidate(candidateTag);
        SmallPlanetField.Candidate incumbent = readCandidate(incumbentTag);
        if (candidate == null) return false;
        if (incumbent == null) return true;
        return SmallPlanetAdoptionPolicy.preferCandidate(
                editedForAdoption(candidateBody, candidateTag, candidate, runtime), candidateTag.getBoolean("building"),
                candidateBody.getUniqueId(),
                editedForAdoption(incumbentBody, incumbentTag, incumbent, runtime), incumbentTag.getBoolean("building"),
                incumbentBody.getUniqueId());
    }

    private static boolean quarantineReleased(SmallPlanetSavedData saved, ServerSubLevel body) {
        CompoundTag userData = body.getUserDataTag();
        return saved.isQuarantineReleased(body.getUniqueId())
                || (userData != null && userData.getBoolean(QUARANTINE_RELEASED_TAG));
    }

    /** Removes this mod's tickets except the one key the body should own; leaves every foreign ticket untouched. */
    private static void reconcileOwnedTickets(ServerSubLevelContainer container, ServerSubLevel body, String keepKey) {
        for (String key : ownedTicketKeys(container, body))
            if (!key.equals(keepKey)) container.removeForceLoadTicket(body, TICKET, key);
    }

    /** Remove every Xeno-owned key for an operator-released body, including stale keys from older versions. */
    private static void purgeOwnedTickets(ServerSubLevelContainer container, ServerSubLevel body) {
        for (String key : ownedTicketKeys(container, body)) {
            if (!container.removeForceLoadTicket(body, TICKET, key))
                throw new IllegalStateException("Sable did not remove existing Xeno small-planet ticket key " + key);
        }
        Set<String> remaining = ownedTicketKeys(container, body);
        if (!remaining.isEmpty())
            throw new IllegalStateException("Xeno small-planet tickets remain after release cleanup: " + remaining);
    }

    private static Set<String> ownedTicketKeys(ServerSubLevelContainer container, ServerSubLevel body) {
        var info = container.getAllTickets().get(body.getUniqueId());
        if (info == null) return Set.of();
        Set<String> keys = new HashSet<>();
        for (SubLevelLoadingTicket<?> ticket : new ArrayList<>(info.tickets())) {
            if (!TICKET.equals(SableLoadingTickets.type(ticket))) continue;
            Object rawKey = SableLoadingTickets.key(ticket);
            if (!(rawKey instanceof String key))
                throw new IllegalStateException("Xeno small-planet ticket has a non-string key: " + rawKey);
            keys.add(key);
        }
        return Set.copyOf(keys);
    }

    private static boolean editedForAdoption(ServerSubLevel body, CompoundTag tag,
                                             SmallPlanetField.Candidate candidate, Runtime runtime) {
        if (hasTouchedFlag(body.getUserDataTag())) return true;
        if (runtime != null && (hasPendingPlacement(runtime, body.getUniqueId())
                || runtime.capturedMutations.values().stream().anyMatch(mutation -> mutation.body().equals(body.getUniqueId()))))
            return true;
        Active active = candidate == null || runtime == null ? null : runtime.active.get(candidate.id());
        if (active != null && active.body.equals(body.getUniqueId()) && active.editedPinned) return true;
        return candidate != null && !tag.getBoolean("building")
                && massChanged(tag.getDouble("mass"), body.getMassTracker().getMass());
    }

    private static Active active(ServerLevel level, SmallPlanetField.Candidate candidate, ServerSubLevel body, double mass) {
        return active(level, candidate, body, mass, true);
    }

    private static Active active(ServerLevel level, SmallPlanetField.Candidate candidate, ServerSubLevel body,
                                 double mass, boolean reportUnavailable) {
        long seed = net.bullettrain.xenopixelsmod.space.universe.UniverseService.fieldSeed(level.getServer());
        UniverseField.StarSystem system = FrontierBindings.get(level).systemAt(seed, candidate.id().systemCell());
        var chart = SpaceChartService.state(level.getServer()).runtime.chart;
        SpaceHomeOrbitalFrame.Focus homeFocus = SpaceHomeOrbitalFrame.resolve(level, chart);
        double homeX = homeFocus == null ? Double.NaN : homeFocus.x();
        double homeY = homeFocus == null ? Double.NaN : homeFocus.y();
        double homeZ = homeFocus == null ? Double.NaN : homeFocus.z();
        SmallPlanetOrbitAdmission.Result admission = SmallPlanetOrbitAdmission.classify(
                system != null, system != null && system.cell().home(), homeFocus != null);
        if (admission != SmallPlanetOrbitAdmission.Result.READY) {
            if (reportUnavailable) XenoPixelsMod.LOGGER.warn(
                    "Small planet {} adopted without orbital motion: prerequisite={} systemResolved={} homeFocusResolved={}",
                    candidate.id().bodyId(), admission, system != null, homeFocus != null);
            Active parked = new Active(candidate, body.getUniqueId(), mass, null, system, homeX, homeY, homeZ);
            parked.orbitRetryPending = true;
            return parked;
        }
        double starX = system.cell().home() ? homeX : system.x();
        double starY = system.cell().home() ? homeY : system.y();
        double starZ = system.cell().home() ? homeZ : system.z();
        SmallPlanetOrbit orbit = null;
        try { orbit = SmallPlanetOrbitRecovery.canonicalSeedOrbit(candidate, starX, starY, starZ); }
        catch (IllegalArgumentException invalid) {
            if (reportUnavailable) XenoPixelsMod.LOGGER.warn("Small planet {} has no valid orbit after adopt: {}",
                    candidate.id().bodyId(), invalid.getMessage());
        }
        return new Active(candidate, body.getUniqueId(), mass, orbit, system, starX, starY, starZ);
    }

    private static void retryOrbit(ServerLevel level, Active active, ServerSubLevel body, long tick) {
        if (!active.orbitRetryPending || !SmallPlanetOrbitAdmission.retryDue(tick, active.nextOrbitRetryTick)) return;
        active.nextOrbitRetryTick = tick + PASS_TICKS;
        try {
            Active resolved = active(level, active.candidate, body, body.getMassTracker().getMass(), false);
            if (resolved.orbit == null || resolved.system == null) {
                active.orbitRetryPending = resolved.orbitRetryPending;
                return;
            }
            active.orbitRetryPending = true;
            var position = body.logicalPose().position();
            double epochSeconds = SpaceOrbitClock.seconds(level.getServer());
            SmallPlanetOrbit resumed = SmallPlanetOrbitRecovery.canonicalSeedOrbit(active.candidate,
                    resolved.starX, resolved.starY, resolved.starZ);
            active.orbit = resumed;
            active.system = resolved.system;
            active.starX = resolved.starX; active.starY = resolved.starY; active.starZ = resolved.starZ;
            active.orbitRetryPending = false;
            active.track.reset();
            XenoPixelsMod.LOGGER.info("Small planet orbit prerequisites restored; body is rejoining its canonical seeded orbit: id={} parkedPosition={} orbitEpochSeconds={}",
                    active.candidate.id().bodyId(), position, epochSeconds);
        } catch (RuntimeException failure) {
            active.orbitRetryPending = true;
            if (!active.orbitRetryFailureReported) {
                active.orbitRetryFailureReported = true;
                XenoPixelsMod.LOGGER.warn("Small planet {} could not refresh its orbit prerequisites; retrying",
                        active.candidate.id().bodyId(), failure);
            }
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

    /** Body id this body's own persisted small-planet identity tag decodes to, or null if absent or malformed. */
    public static String taggedBodyId(ServerSubLevel body) {
        CompoundTag data = body == null ? null : body.getUserDataTag();
        if (data == null || !data.contains(TAG)) return null;
        SmallPlanetField.Candidate candidate = readCandidate(data.getCompound(TAG));
        return candidate == null ? null : candidate.id().bodyId();
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
        runtime.quarantined.entrySet().removeIf(entry -> !(container.getSubLevel(entry.getKey()) instanceof ServerSubLevel body)
                || body.isRemoved());
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        MinecraftServer server = event.getServer();
        Runtime runtime = RUNTIMES.get(server);
        ServerLevel level = host(server);
        if (runtime == null) return;
        runtime.stopping = true;
        // NeoForge 21.1.248 posts ServerStoppingEvent before MinecraftServer.stopServer() calls
        // saveAllChunks(). Drain accepted placements now so even zero-mass edits (for example,
        // flowers or torches) set the persistent edited marker before Sable serializes their plots.
        if (level != null) {
            try {
                flushPendingPlacements(level, runtime, Long.MAX_VALUE, true);
            } catch (RuntimeException error) {
                XenoPixelsMod.LOGGER.error("Unexpected small-planet placement flush failure during shutdown; continuing teardown",
                        error);
            }
            abandonBuild(level, runtime, "server stopping");
            for (var entry : new ArrayList<>(runtime.active.entrySet()))
                release(level, runtime, entry.getKey(), entry.getValue(), "server stopping");
        }
        flushPlacementTestDimension(server, runtime, level, true);
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        RUNTIMES.remove(event.getServer());
        RELEASE_WAKEUP_REGISTRATIONS.entrySet().removeIf(entry -> entry.getValue() == event.getServer());
        SpaceTickScheduler.unregister(ORBIT_PASS);
    }

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
        SmallPlanetActivationPolicy.Gate activationGate = activationGate();
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null) return "small planets: physical=" + activationGate.label()
                + " adoption=not-started"
                + " field=" + (XenoSpaceConfig.smallPlanetFieldEnabled() ? "on" : "off")
                + " radialGravity=" + XenoSpaceConfig.smallPlanetRadialGravity()
                + " caps=" + XenoSpaceConfig.smallPlanetMaxGlobal() + "/" + XenoSpaceConfig.smallPlanetMaxPerPlayer()
                + " editedCap=" + XenoSpaceConfig.smallPlanetMaxEditedGlobal();
        int activeVoxels = 0, unknownVoxelBodies = runtime.active.size();
        int editedBodies = editedActiveCount(runtime);
        int normalBodies = runtime.active.size() - editedActiveBodyCount(runtime);
        int parkedOrbits = 0;
        ServerLevel activeLevel = host(server);
        for (Active active : runtime.active.values()) {
            if (active.orbit == null || active.system == null) parkedOrbits++;
            if (activeLevel != null) {
                if (SubLevelContainer.getContainer(activeLevel).getSubLevel(active.body) instanceof ServerSubLevel body
                        && body.getUserDataTag() != null && body.getUserDataTag().contains(TAG)
                        && body.getUserDataTag().getCompound(TAG).contains("voxels")) {
                    activeVoxels += Math.max(0, body.getUserDataTag().getCompound(TAG).getInt("voxels"));
                    unknownVoxelBodies--;
                }
            }
        }
        return "small planets: physical=" + activationGate.label()
                + " field=" + (XenoSpaceConfig.smallPlanetFieldEnabled() ? "on" : "off")
                + " adoption=" + (runtime.adopted ? "ready" : "retrying")
                + " nextAdoptRetryInTicks=" + Math.max(0,
                        runtime.nextAdoptRetryTick - SpaceOrbitClock.gameTick(server))
                + " active=" + normalBodies + "/" + XenoSpaceConfig.smallPlanetMaxGlobal()
                + " editedPinned=" + editedBodies + "/" + XenoSpaceConfig.smallPlanetMaxEditedGlobal()
                + " activeVoxels=" + activeVoxels
                + " unknownVoxelBodies=" + unknownVoxelBodies
                + " parkedOrbits=" + parkedOrbits
                + " quarantined=" + runtime.quarantined.size()
                + " releaseAuditRetained=" + runtime.releasedQuarantineIds.size()
                + " releaseAuditQueued=" + runtime.releaseMarkerAuditQueue.fallbackSize()
                + " releaseAuditPriorityQueued=" + runtime.releaseMarkerAuditQueue.prioritySize()
                + " releaseAuditPointerlessLast=" + runtime.lastUnresolvedReleasePointerCount
                + " releaseAuditLastTick=" + (runtime.lastUnresolvedReleasePointerAuditTick == Long.MIN_VALUE
                        ? "never" : runtime.lastUnresolvedReleasePointerAuditTick)
                + " releaseAuditOldestSampleAgeTicks=" + unresolvedPointerSampleOldestAge(runtime,
                        SpaceOrbitClock.gameTick(server))
                + " releaseAuditPointerlessSample=" + runtime.unresolvedReleasePointerSample.keySet()
                + " radialGravity=" + XenoSpaceConfig.smallPlanetRadialGravity()
                + " wanted=" + runtime.wanted.size() + " building="
                + (runtime.building == null ? "-" : runtime.building.id().bodyId())
                + " built/removed/detached/failed=" + runtime.built + "/" + runtime.removed + "/"
                + runtime.detached + "/" + runtime.failed + " maxBatchMs="
                + String.format("%.2f", runtime.maxBatchNanos / 1_000_000.0);
    }

    private static long unresolvedPointerSampleOldestAge(Runtime runtime, long nowTick) {
        long oldestAge = 0L;
        for (long seenTick : runtime.unresolvedReleasePointerSample.values())
            oldestAge = Math.max(oldestAge, Math.max(0L, nowTick - seenTick));
        return oldestAge;
    }

    public static String quarantineList(MinecraftServer server) {
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null || runtime.quarantined.isEmpty()) return "Small-planet quarantine: no held bodies.";
        StringBuilder out = new StringBuilder("Small-planet quarantine: ")
                .append(runtime.quarantined.size()).append(" quarantined body/bodies");
        int shown = 0;
        for (Quarantined body : runtime.quarantined.values()) {
            if (shown++ >= 40) break;
            out.append('\n').append(body.body).append(" — ").append(body.reason)
                    .append(body.edited ? " (protected-cap counted)" : "")
                    .append(body.holdAvailable ? " (hold active)"
                            : body.holdAttempted ? " (hold unavailable: physics handle/mass not ready)"
                            : " (awaiting physics hold pass)");
        }
        if (runtime.quarantined.size() > 40) out.append("\n… ").append(runtime.quarantined.size() - 40).append(" more");
        return out.toString();
    }

    /** Releases the safety hold/ticket only; this does not delete or alter the body's logical identity. */
    public static String releaseQuarantine(MinecraftServer server, UUID bodyId) {
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null || !runtime.quarantined.containsKey(bodyId))
            return "No quarantined small-planet body is registered for UUID " + bodyId + ".";
        ServerLevel level = host(server);
        if (level == null) return "Cannot release quarantine: Voidsea is not loaded.";
        SmallPlanetSavedData saved = SmallPlanetSavedData.get(level);
        if (!saved.markQuarantineReleased(bodyId))
            return "Cannot release quarantine: the persistent release-decision limit is full; no changes made.";
        var container = SubLevelContainer.getContainer(level);
        runtime.releasedQuarantineIds.add(bodyId);
        runtime.releasePending.add(bodyId);
        runtime.adopted = false;
        if (!(container.getSubLevel(bodyId) instanceof ServerSubLevel body) || body.isRemoved()) {
            String retryCondition = activationGate().enabled()
                    ? "when the body loads" : "when the body loads while small-planet physicalization is enabled";
            return "Persistently recorded release for small-planet body " + bodyId
                    + "; Sable's UUID-keyed ticket will be cleaned " + retryCondition + ". No body data was changed.";
        }
        CompoundTag originalData = body.getUserDataTag();
        if (originalData != null && originalData.contains(TAG)) {
            CompoundTag releasedData = originalData.copy();
            releasedData.putBoolean(QUARANTINE_RELEASED_TAG, true);
            try { body.setUserDataTag(releasedData); }
            catch (RuntimeException failure) {
                XenoPixelsMod.LOGGER.error("Saved quarantine release for {}; body NBT fast-path marker could not be written",
                        bodyId, failure);
            }
        }
        final Set<String> ticketsBeforeRelease;
        try {
            ticketsBeforeRelease = ownedTicketKeys(container, body);
            purgeOwnedTickets(container, body);
        } catch (RuntimeException failure) {
            XenoPixelsMod.LOGGER.error("Persisted quarantine release for {}; ticket cleanup will retry on adoption",
                    bodyId, failure);
            return "Persistently recorded release for " + bodyId
                    + "; Xeno ticket cleanup is incomplete and will retry when the body is reconciled. Its quarantine hold remains active for now.";
        }
        runtime.quarantined.remove(bodyId);
        String ticket = ticketsBeforeRelease.isEmpty() ? "no Xeno small-planet tickets were present"
                : "removed " + ticketsBeforeRelease.size() + " Xeno small-planet ticket(s)";
        return "Persistently recorded release for " + bodyId + " (" + ticket
                + "). Release does not promote it over the normal deterministic winner. If it is canonical, normal planet management resumes; if it loses, it remains unheld and may drift.";
    }

    private static int editedActiveCount(Runtime runtime) {
        int count = editedActiveBodyCount(runtime);
        for (Quarantined body : runtime.quarantined.values()) if (body.edited) count++;
        return count;
    }

    private static int editedActiveBodyCount(Runtime runtime) {
        int count = 0;
        for (Active active : runtime.active.values()) if (active.editedPinned) count++;
        return count;
    }

    private static int normalActiveCount(Runtime runtime) {
        return Math.max(0, runtime.active.size() - editedActiveCount(runtime));
    }

    /** Keep an unidentifiable, force-ticketed body from coasting out of Sable's removal bounds. */
    private static boolean holdQuarantined(ServerLevel level, SubLevelPhysicsSystem system, Quarantined quarantined,
                                           ServerSubLevel body, long tick, double dt,
                                           double min, double max, boolean unbounded) {
        RigidBodyHandle handle = system.getPhysicsHandle(body);
        double mass = body.getMassTracker().getMass();
        if (handle == null || !handle.isValid() || !(mass > 0.0) || !Double.isFinite(mass)) return false;
        OrbitTrack track = quarantined.track;
        var pose = body.logicalPose();
        var position = pose.position();
        track.bodyPosition[0] = position.x(); track.bodyPosition[1] = position.y(); track.bodyPosition[2] = position.z();
        if (!track.stationaryTargetCaptured) {
            SmallPlanetOrbitAdmission.writeStationaryTarget(
                    track.bodyPosition, track.target, track.targetVelocity, track.feedForward);
            track.stationaryTargetCaptured = true;
        } else {
            java.util.Arrays.fill(track.targetVelocity, 0.0);
            java.util.Arrays.fill(track.feedForward, 0.0);
        }
        if (track.previousTick != Long.MIN_VALUE && tick > track.previousTick) {
            double elapsed = Math.min(20.0, tick - track.previousTick) / 20.0;
            for (int axis = 0; axis < 3; axis++)
                track.velocity[axis] = (track.bodyPosition[axis] - track.previous[axis]) / elapsed;
            track.hasVelocity = true;
        }
        System.arraycopy(track.bodyPosition, 0, track.previous, 0, 3);
        track.previousTick = tick;

        var bounds = body.boundingBox();
        double halfHeight = Math.max(1.0, (bounds.maxY() - bounds.minY()) * 0.5);
        boolean currentSafe = SmallPlanetOrbitSafety.currentBoundsFitRemovalBand(bounds.minY(), bounds.maxY(),
                min, max, level.getMinBuildHeight(), unbounded);
        double lower = unbounded ? min : Math.max(min, level.getMinBuildHeight() - 64.0);
        double safeMinY = lower + halfHeight + 2.0;
        double safeMaxY = max - halfHeight - 2.0;
        if (safeMinY > safeMaxY) safeMinY = safeMaxY = (lower + max) * 0.5;
        if (!currentSafe) {
            track.target[0] = track.bodyPosition[0];
            track.target[1] = SmallPlanetOrbitSafety.safetyTargetY(position.y(), track.velocity[1],
                    safeMinY, safeMaxY, SmallPlanetOrbitSafety.holdAcceleration(XenoSpaceConfig.orbitHoldMaxAccel()));
            track.target[2] = track.bodyPosition[2];
        }
        DimensionPhysicsData.getGravity(level, position, track.gravityVector);
        track.impulse.set(track.gravityVector).negate();
        PlanetOrbitHold.acceleration(track.bodyPosition, track.velocity, track.target,
                track.targetVelocity, track.feedForward,
                SmallPlanetOrbitSafety.holdStiffness(XenoSpaceConfig.orbitHoldStiffness()),
                SmallPlanetOrbitSafety.holdDamping(XenoSpaceConfig.orbitHoldDamping()),
                SmallPlanetOrbitSafety.holdAcceleration(XenoSpaceConfig.orbitHoldMaxAccel()), track.command);
        track.impulse.add(track.command[0], track.command[1], track.command[2]).mul(mass * dt);
        pose.orientation().transformInverse(track.impulse);
        if (track.impulse.isFinite() && track.impulse.lengthSquared() > 1.0e-12) handle.applyLinearImpulse(track.impulse);
        return true;
    }

    /** Shared by activation status and discovery notices so they cannot report different physicalization gates. */
    public static SmallPlanetActivationPolicy.Gate activationGate() {
        return SmallPlanetActivationPolicy.gate(XenoSpaceConfig.enabled(), XenoSpaceConfig.smallPlanetPhysical(),
                XenoSpaceConfig.smallPlanetBuildIncrementalSections(),
                XenoSpaceConfig.smallPlanetMaxGlobal(), XenoSpaceConfig.smallPlanetMaxPerPlayer());
    }

    private static ServerLevel host(MinecraftServer server) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
    }

    /** Moves only the already-capped live incarnations; far candidates remain pure client ephemerides. */
    private static final class SmallPlanetOrbitPass implements SpaceTickScheduler.Pass {
        @Override public String name() { return "small-planet-orbits"; }
        @Override public boolean appliesTo(SubLevelPhysicsSystem system) {
            ServerLevel level = system.getLevel();
            return level != null && PhysicalEarth.HOST.equals(level.dimension().location());
        }
        @Override public int run(SubLevelPhysicsSystem system, double seconds) {
            ServerLevel level = system.getLevel();
            if (level == null) return 0;
            Runtime runtime = RUNTIMES.get(level.getServer());
            if (runtime == null || (runtime.active.isEmpty() && runtime.quarantined.isEmpty())) return 0;
            boolean orbitManagementEnabled = XenoSpaceConfig.enabled() && XenoSpaceConfig.smallPlanetPhysical();
            long tick = level.getGameTime();
            double time = SpaceOrbitClock.seconds(level.getServer());
            double dt = Math.max(0.05, seconds);
            var container = SubLevelContainer.getContainer(level);
            var band = SableRemovalBand.forDimension(level.dimension().location());
            boolean unbounded = band.voidseaExtended();
            double min = band.minY();
            double max = band.maxY();
            int worked = 0;
            for (Active active : runtime.active.values()) {
                if (!SmallPlanetEditedBodyPolicy.shouldRunOrbitController(orbitManagementEnabled, active.editedPinned))
                    continue;
                if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) continue;
                retryOrbit(level, active, body, tick);
                RigidBodyHandle handle = system.getPhysicsHandle(body);
                double mass = body.getMassTracker().getMass();
                if (handle == null || !handle.isValid() || !(mass > 0.0) || !Double.isFinite(mass)) continue;

                OrbitTrack track = active.track;
                // The feature toggle stops unedited analytic orbiting. Edited bodies remain ticketed, but use the
                // same gravity-canceling stationary hold and safe-Y brake so disabling cannot leave them coasting.
                boolean analyticOrbit = orbitManagementEnabled && active.orbit != null && active.system != null;
                var pose = body.logicalPose();
                var p = pose.position();
                track.bodyPosition[0] = p.x(); track.bodyPosition[1] = p.y(); track.bodyPosition[2] = p.z();
                if (analyticOrbit && XenoSpaceConfig.realOrbits())
                    active.orbit.positionAndVelocityAt(time, track.target, track.targetVelocity);
                else if (analyticOrbit) {
                    active.orbit.positionAt(active.orbit.epochSeconds(), track.target);
                    java.util.Arrays.fill(track.targetVelocity, 0.0);
                } else {
                    if (!track.stationaryTargetCaptured) {
                        SmallPlanetOrbitAdmission.writeStationaryTarget(
                                track.bodyPosition, track.target, track.targetVelocity, track.feedForward);
                        track.stationaryTargetCaptured = true;
                    } else {
                        java.util.Arrays.fill(track.targetVelocity, 0.0);
                        java.util.Arrays.fill(track.feedForward, 0.0);
                    }
                }
                if (analyticOrbit && XenoSpaceConfig.realOrbits()) {
                    double angularSpeed = 2.0 * Math.PI / active.orbit.periodSeconds();
                    double centripetal = -angularSpeed * angularSpeed;
                    track.acceleration[0] = (track.target[0] - active.starX) * centripetal;
                    track.acceleration[1] = (track.target[1] - active.starY) * centripetal;
                    track.acceleration[2] = (track.target[2] - active.starZ) * centripetal;
                } else java.util.Arrays.fill(track.acceleration, 0.0);
                if (analyticOrbit) System.arraycopy(track.acceleration, 0, track.feedForward, 0, 3);
                /*
                 * Keep the tracked pose and measured velocity below this point for both orbiting and parked
                 * bodies so gravity cancellation and the removal-band safety brake apply to either case.
                 */
                if (track.previousTick != Long.MIN_VALUE && tick > track.previousTick) {
                    double elapsed = Math.min(20.0, tick - track.previousTick) / 20.0;
                    System.arraycopy(track.velocity, 0, track.previousVelocity, 0, 3);
                    for (int axis = 0; axis < 3; axis++) track.velocity[axis] = (track.bodyPosition[axis] - track.previous[axis]) / elapsed;
                    if (track.hasVelocity) {
                        for (int axis = 0; axis < 3; axis++) track.measured[axis] = (track.velocity[axis] - track.previousVelocity[axis]) / elapsed;
                        track.held = track.release.update(track.measured, track.command, track.previousCommand,
                                XenoSpaceConfig.orbitHeldThreshold(), XenoSpaceConfig.orbitReleaseTicks(), (int) Math.ceil(elapsed * 20.0));
                    }
                    track.hasVelocity = true;
                }
                System.arraycopy(track.bodyPosition, 0, track.previous, 0, 3);
                track.previousTick = tick;

                var box = body.boundingBox();
                double halfHeight = SmallPlanetOrbitSafety.rotatedVoxelHalfExtent(active.candidate.radius());
                boolean currentSafe = SmallPlanetOrbitSafety.currentBoundsFitRemovalBand(box.minY(), box.maxY(), min, max,
                        level.getMinBuildHeight(), unbounded);
                boolean orbitSafe = !analyticOrbit || SmallPlanetOrbitSafety.orbitFitsRemovalBand(active.orbit, halfHeight,
                        min, max, level.getMinBuildHeight(), unbounded);
                boolean safetyPause = !currentSafe || !orbitSafe;
                if (!analyticOrbit && !currentSafe) track.stationaryTargetCaptured = false;
                if (safetyPause) {
                    if (!active.unsafeOrbitReported) {
                        active.unsafeOrbitReported = true;
                        XenoPixelsMod.LOGGER.warn("Small planet orbit controller paused outside safe live Y band: id={} current=[{},{}] allowed=[{},{}] orbit=[{},{}]",
                                active.candidate.id().bodyId(), box.minY(), box.maxY(), min, max,
                                active.orbit == null ? "unavailable" : active.orbit.minimumY(),
                                active.orbit == null ? "unavailable" : active.orbit.maximumY());
                    }
                } else active.unsafeOrbitReported = false;
                DimensionPhysicsData.getGravity(level, p, track.gravityVector);
                track.impulse.set(track.gravityVector).negate();
                System.arraycopy(track.command, 0, track.previousCommand, 0, 3);
                java.util.Arrays.fill(track.command, 0.0);
                if (safetyPause) {
                    // Pausing the analytic target must still brake momentum and cancel field gravity. Otherwise
                    // the body coasts past the very removal boundary this guard is meant to protect.
                    double lower = unbounded ? min : Math.max(min, level.getMinBuildHeight() - 64.0);
                    double safeMinY = lower + halfHeight + 2.0;
                    double safeMaxY = max - halfHeight - 2.0;
                    if (safeMinY > safeMaxY) safeMinY = safeMaxY = (lower + max) * 0.5;
                    double brakeAccel = SmallPlanetOrbitSafety.holdAcceleration(XenoSpaceConfig.orbitHoldMaxAccel());
                    double brakeStiffness = SmallPlanetOrbitSafety.holdStiffness(XenoSpaceConfig.orbitHoldStiffness());
                    double brakeDamping = SmallPlanetOrbitSafety.holdDamping(XenoSpaceConfig.orbitHoldDamping());
                    track.target[0] = track.bodyPosition[0];
                    track.target[1] = SmallPlanetOrbitSafety.safetyTargetY(p.y(), track.velocity[1],
                            safeMinY, safeMaxY, brakeAccel);
                    track.target[2] = track.bodyPosition[2];
                    java.util.Arrays.fill(track.targetVelocity, 0.0);
                    java.util.Arrays.fill(track.feedForward, 0.0);
                    PlanetOrbitHold.acceleration(track.bodyPosition, track.velocity, track.target,
                            track.targetVelocity, track.feedForward, brakeStiffness,
                            brakeDamping, brakeAccel, track.command);
                    track.impulse.add(track.command[0], track.command[1], track.command[2]);
                } else if (!analyticOrbit || !track.held) {
                    if (analyticOrbit && XenoSpaceConfig.orbitReturnBoost())
                        PlanetOrbitHold.returnAcceleration(track.bodyPosition, track.velocity, track.target,
                                track.targetVelocity, track.feedForward, XenoSpaceConfig.orbitReturnStiffness(),
                                XenoSpaceConfig.orbitReturnDamping(), XenoSpaceConfig.orbitReturnMaxAccel(),
                                XenoSpaceConfig.orbitReturnMaxSpeed(), track.command);
                    else PlanetOrbitHold.acceleration(track.bodyPosition, track.velocity, track.target,
                            track.targetVelocity, track.feedForward,
                            SmallPlanetOrbitSafety.holdStiffness(XenoSpaceConfig.orbitHoldStiffness()),
                            SmallPlanetOrbitSafety.holdDamping(XenoSpaceConfig.orbitHoldDamping()),
                            SmallPlanetOrbitSafety.holdAcceleration(XenoSpaceConfig.orbitHoldMaxAccel()), track.command);
                    track.impulse.add(track.command[0], track.command[1], track.command[2]);
                }
                track.impulse.mul(mass * dt);
                pose.orientation().transformInverse(track.impulse);
                if (track.impulse.isFinite() && track.impulse.lengthSquared() > 1.0e-12) handle.applyLinearImpulse(track.impulse);
                worked++;
            }
            for (Quarantined quarantined : runtime.quarantined.values()) {
                if (!(container.getSubLevel(quarantined.body) instanceof ServerSubLevel body) || body.isRemoved()) continue;
                quarantined.holdAttempted = true;
                quarantined.holdAvailable = holdQuarantined(level, system, quarantined, body, tick, dt, min, max, unbounded);
                if (quarantined.holdAvailable) {
                    quarantined.holdFailureLogged = false;
                    worked++;
                } else if (!quarantined.holdFailureLogged) {
                    quarantined.holdFailureLogged = true;
                    XenoPixelsMod.LOGGER.error("Quarantined small-planet body {} could not be held this physics pass; its UUID ticket remains, but movement safety is not established (physics handle/mass unavailable)",
                            quarantined.body);
                }
            }
            return worked;
        }

    }
}
