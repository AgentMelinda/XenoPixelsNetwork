package net.bullettrain.xenopixelsmod.space.asteroid;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ticket.SubLevelLoadingTicketType;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.companion.math.BoundingBox3i;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.api.network.AddonNetwork;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.bullettrain.xenopixelsmod.space.SpaceChartService;
import net.bullettrain.xenopixelsmod.space.physics.PhysicalEarth;
import net.bullettrain.xenopixelsmod.space.physics.PlotBodyBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Near physicalization of belt asteroids (S11 B3). Switch {@link XenoSpaceConfig#asteroidPhysical()} (default off; also
 * needs {@code asteroidBelts}). One shared incarnation per asteroid id, never one per player:
 * <ul>
 * <li>Every 20 ticks, for each Voidsea player, the field candidates within {@code asteroidActivateRange} are scanned
 *     under {@code asteroidScanCellsPerPass} cell/belt probes (nearest candidates among the cells visited so far are
 *     queued). Per-area/belt cursors resume on later passes, so a large range cannot monopolize one server pass.</li>
 * <li>At most one asteroid is built at a time, at most one new build per {@code asteroidSpawnIntervalTicks}, through the
 *     batched {@link PlotBodyBuilder.Job} with {@code asteroidBuildVoxelsPerTick} voxels per tick and no eviction.</li>
 * <li>A built asteroid holds a force-load ticket (players are usually farther than view distance). When no player is
 *     within {@code asteroidReleaseRange} it is released: an untouched asteroid is removed (the seed rebuilds it), a
 *     touched one (mass changed) is detached: ticket and tag removed, id recorded in {@link AsteroidSavedData}, and from
 *     then on Sable stores it like any other sub-level.</li>
 * <li>Clean stop releases everything the same way, so untouched asteroids are never stored.</li>
 * </ul>
 * Asteroids are static (no drift or spin) so the solver can sleep them. B4 impact-cut groundwork is experimental,
 * opt-in and not runtime-verified; child caps/lifecycle remain incomplete.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class AsteroidPhysicalizer {
    public static final String TAG = "xenopixelsmod_asteroid_v1";
    public static final String FRAGMENT_TAG = "xenopixelsmod_asteroid_fragment_v1";
    private static final String TOUCHED_TAG = "touched";
    static final SubLevelLoadingTicketType<String> TICKET = SubLevelLoadingTicketType.create(
            ResourceLocation.fromNamespaceAndPath("xenopixelsmod", "asteroid"), Codec.STRING);
    private static final int PASS_TICKS = 20;
    /** Fixed memory bound for deterministic field-cell results reused by nearby players and later proximity passes. */
    private static final int MAX_CACHED_FIELD_CELLS = 4_096;
    /** Fixed memory bound for resumable per-viewer/belt scans when players move between search boxes. */
    private static final int MAX_SCAN_CURSORS = 4_096;
    /** Safety circuit breaker: at most two full rescans after the initial build if edits keep invalidating it. */
    private static final int MAX_DIRTY_HEAT_MAP_RESTARTS = 2;

    private record FieldCellKey(long seed, AsteroidField.Belt belt, AsteroidField.Cell cell) {}
    private record ScanCursorKey(long seed, AsteroidField.Belt belt, AsteroidCellScanCursor.Bounds bounds) {}
    private record OtherLiveBodies(List<double[]> positions, int unlocatedReservations) {
        long count() { return (long) positions.size() + unlocatedReservations; }
    }
    private static final class ScanTask {
        final AsteroidField.Belt belt;
        final AsteroidCellScanCursor cursor;
        final List<Integer> viewers;
        boolean complete;
        ScanTask(AsteroidField.Belt belt, AsteroidCellScanCursor cursor, List<Integer> viewers) {
            this.belt = belt; this.cursor = cursor; this.viewers = viewers;
        }
    }

    private static final class Active {
        final AsteroidField.Candidate candidate;
        final UUID body;
        final FractureVelocityTracker fractureVelocity = new FractureVelocityTracker();
        double originalMass = Double.NaN;
        long fractureReadyAt;
        int fractures;
        boolean detachBlockedLogged;
        Active(AsteroidField.Candidate candidate, UUID body) { this.candidate = candidate; this.body = body; }
    }

    private static final class Runtime {
        final Map<AsteroidField.ObjectId, Active> active = new HashMap<>();
        final List<AsteroidField.Candidate> wanted = new ArrayList<>();
        /** Main-thread-only LRU; including the belt record makes chart edits invalidate old geometry naturally. */
        final LinkedHashMap<FieldCellKey, List<AsteroidField.Candidate>> fieldCellCache = new LinkedHashMap<>(256, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<FieldCellKey, List<AsteroidField.Candidate>> eldest) {
                return size() > MAX_CACHED_FIELD_CELLS;
            }
        };
        /** Main-thread-only LRU; scan work resumes across passes and is bounded independently of field-result caching. */
        final LinkedHashMap<ScanCursorKey, AsteroidCellScanCursor> scanCursors = new LinkedHashMap<>(128, 0.75f, true) {
            @Override protected boolean removeEldestEntry(Map.Entry<ScanCursorKey, AsteroidCellScanCursor> eldest) {
                return size() > MAX_SCAN_CURSORS;
            }
        };
        final ArrayDeque<HeatMapBootstrap> heatMapBootstraps = new ArrayDeque<>();
        final AsteroidFractureQueue<FractureCut> fractureCuts = new AsteroidFractureQueue<>();
        final LinkedHashMap<UUID, FragmentExpiryScan> fragmentExpiryScans = new LinkedHashMap<>();
        AsteroidField.Candidate building;
        PlotBodyBuilder.Job job;
        /** The building body already holds its force-load ticket (added right after the first batch). */
        boolean buildTicketed;
        long nextPass, nextSpawn;
        long nextFragmentDiscovery;
        long fractureWorkLastNanos, fractureWorkMaxNanos;
        long fragmentPositionsScannedLast, fragmentPositionsScannedMax;
        long fieldCellCacheHits, fieldCellCacheMisses;
        long scanWorkLastNanos, scanWorkMaxNanos;
        long scanCellsLast;
        OtherLiveBodies capacityBodiesSnapshot;
        long capacityBodiesSnapshotTick = Long.MIN_VALUE;
        int scanTaskStart;
        int fragmentsExpired;
        boolean adopted, presenceDirty;
        int built, removed, detached;
    }

    private interface HeatMapVoxelSource {
        int count();
        BlockPos hostPosition(int index);
        boolean sourceSolid(int index);
    }

    private static final class HeatMapBootstrap {
        final ServerSubLevel body;
        HeatMapVoxelSource source;
        final long startedNanos = System.nanoTime();
        int phase, index, registered;
        int dirtyRestarts;
        boolean dirty;
        HeatMapBootstrap(ServerSubLevel body, HeatMapVoxelSource source) { this.body = body; this.source = source; }
    }

    private static final class FractureCut {
        final AsteroidField.ObjectId id;
        final ServerSubLevel body;
        final AsteroidVoxels voxels;
        final AsteroidFractureMath.Plane plane;
        final double slabHalfWidth;
        final int extent, side, centerX, centerY, centerZ;
        final Map<Long, ChunkPos> hostChunks = new HashMap<>();
        int index, removed;

        FractureCut(AsteroidField.ObjectId id, ServerSubLevel body, AsteroidField.Candidate candidate,
                    AsteroidFractureMath.Plane plane, double slabHalfWidth) {
            this.id = id;
            this.body = body;
            this.voxels = new AsteroidVoxels(candidate);
            this.plane = plane;
            this.slabHalfWidth = slabHalfWidth;
            this.extent = voxels.extent();
            this.side = extent * 2 + 1;
            var plot = body.getPlot();
            var center = plot.getCenterBlock();
            var localChunk = plot.toLocal(new ChunkPos(center));
            this.centerX = localChunk.x * 16 + (center.getX() & 15);
            this.centerY = center.getY();
            this.centerZ = localChunk.z * 16 + (center.getZ() & 15);
        }

        int voxelCount() { return side * side * side; }
        int x() { return index / (side * side) - extent; }
        int y() { return index / side % side - extent; }
        int z() { return index % side - extent; }

        BlockPos hostPosition(int x, int y, int z) {
            int localX = centerX + x, localY = centerY + y, localZ = centerZ + z;
            int cx = Math.floorDiv(localX, 16), cz = Math.floorDiv(localZ, 16);
            long key = ChunkPos.asLong(cx, cz);
            ChunkPos global = hostChunks.computeIfAbsent(key, ignored -> body.getPlot().toGlobal(new ChunkPos(cx, cz)));
            return new BlockPos(global.getMinBlockX() + Math.floorMod(localX, 16), localY,
                    global.getMinBlockZ() + Math.floorMod(localZ, 16));
        }
    }

    /** Bounded host-block scan for an aged child; never uses Sable's unbounded destroyAllBlocks box sweep. */
    private static final class FragmentExpiryScan {
        final UUID bodyId;
        BoundingBox3i bounds;
        long volume, cursor;
        final ArrayList<BlockPos> solids = new ArrayList<>();

        FragmentExpiryScan(UUID bodyId, BoundingBox3i bounds) {
            this.bodyId = bodyId;
            reset(bounds);
        }

        void reset(BoundingBox3i nextBounds) {
            bounds = new BoundingBox3i(nextBounds);
            volume = AsteroidFragmentScanMath.volume(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ());
            cursor = 0;
            solids.clear();
        }

        BlockPos positionAt(long index) {
            int[] pos = AsteroidFragmentScanMath.positionAt(bounds.minX(), bounds.minY(), bounds.minZ(),
                    bounds.maxX(), bounds.maxY(), bounds.maxZ(), index);
            return new BlockPos(pos[0], pos[1], pos[2]);
        }
    }

    private static final Map<MinecraftServer, Runtime> RUNTIMES = new IdentityHashMap<>();
    private static final Map<MinecraftServer, Long> NEXT_FRAGMENT_RUNTIME_PROBE = new IdentityHashMap<>();
    /** Proof-server probe only: extra viewpoints treated like Voidsea players (the proof world has none). */
    private static final List<double[]> DEBUG_VIEWERS = new CopyOnWriteArrayList<>();

    public static String debugViewer(double x, double y, double z, boolean add) {
        if (add) DEBUG_VIEWERS.add(new double[]{x, y, z}); else DEBUG_VIEWERS.clear();
        String report = "asteroid debug viewers=" + DEBUG_VIEWERS.size() + (add ? " added (" + x + ", " + y + ", " + z + ")" : " cleared");
        XenoPixelsMod.LOGGER.warn("{}", report);
        return report;
    }

    private static List<double[]> viewers(ServerLevel level) {
        var out = new ArrayList<double[]>(level.players().size() + DEBUG_VIEWERS.size());
        for (ServerPlayer player : level.players()) out.add(new double[]{player.getX(), player.getY(), player.getZ()});
        out.addAll(DEBUG_VIEWERS);
        return out;
    }

    private AsteroidPhysicalizer() {}

    public static boolean isAsteroid(ServerSubLevel body) {
        return body != null && body.getUserDataTag() != null && body.getUserDataTag().contains(TAG);
    }

    private static boolean isAsteroidFragment(ServerSubLevel body) {
        CompoundTag data = body == null ? null : body.getUserDataTag();
        return data != null && data.contains(FRAGMENT_TAG)
                && fragmentId(data.getCompound(FRAGMENT_TAG)) != null;
    }

    /**
     * A managed asteroid or the asteroid being built. Such bodies skip Sable's world-chunk sweep (they float in empty
     * space; the sweep loaded ~2,900 extra Voidsea chunks for three asteroids, ~15 ms/tick, S11 B3 run 2) and its
     * split heat map (a half-built body split into ~35 fragments in one 3.9 s tick, the planet failure again). The
     * heat-map skip latches per body (SablePlanetHeatMapSkipMixin). With the default-off fracture switch enabled, a
     * bounded rebuild re-enables Sable connectivity splitting after installation; impact cuts and child lifecycle
     * remain experimental and require runtime proof.
     */
    public static boolean manages(ServerSubLevel body) {
        if (body == null) return false;
        if (isAsteroid(body)) return true;
        Runtime runtime = RUNTIMES.get(body.getLevel().getServer());
        return runtime != null && runtime.job != null && runtime.job.body() == body;
    }

    /** True for a chart asteroid root or any Sable child whose ancestry reaches that root. */
    public static boolean isAsteroidFamilyBody(ServerSubLevel body) {
        return asteroidFamilyId(body, body == null ? null : SubLevelContainer.getContainer(body.getLevel())) != null;
    }

    /** Defers live Sable heat-map notifications until a bounded bootstrap can replay the current block state. */
    public static void markFractureHeatMapDirty(ServerSubLevel body) {
        if (body == null || body.getLevel().getServer() == null) return;
        Runtime runtime = RUNTIMES.get(body.getLevel().getServer());
        if (runtime == null) return;
        for (HeatMapBootstrap bootstrap : runtime.heatMapBootstraps) {
            if (bootstrap.body == body) {
                bootstrap.dirty = true;
                return;
            }
        }
    }

    /**
     * Called immediately before Sable's split invocation. When the whole split would exceed either cap, the mixin
     * leaves Sable's component map untouched and retries on a later tick after a fragment slot becomes available.
     */
    public static boolean maySplitAsteroid(ServerSubLevel body, int pendingChildren) {
        if (body == null) return true;
        var container = SubLevelContainer.getContainer(body.getLevel());
        AsteroidField.ObjectId rootId = asteroidFamilyId(body, container);
        if (rootId == null) return true;
        if (pendingChildren < 0) return false;
        if (pendingChildren == 0) return true;

        // The fracture toggle prevents new impact cuts. A Sable connectivity split already in progress must still
        // finish when it fits the hard caps; freezing its manager here could strand a fragment after the toggle flips.
        int perAsteroid = 0;
        int global = 0;
        for (SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel candidate) || candidate.isRemoved() || isAsteroid(candidate)) continue;
            AsteroidField.ObjectId candidateRoot = asteroidFamilyId(candidate, container);
            if (candidateRoot == null) continue;
            global++;
            if (candidateRoot.equals(rootId)) perAsteroid++;
        }
        if (!AsteroidFragmentBudget.fits(perAsteroid, global, pendingChildren,
                XenoSpaceConfig.asteroidMaxFragmentsPerAsteroid(), XenoSpaceConfig.asteroidMaxFragmentsGlobal())) return false;

        Runtime runtime = RUNTIMES.get(body.getLevel().getServer());
        OtherLiveBodies other = otherLiveBodies((ServerLevel) body.getLevel(), runtime);
        int managedRoots = runtime == null ? 0 : runtime.active.size();
        int pendingBuild = runtime != null && runtime.job != null ? 1 : 0;
        if (!AsteroidSplitCapacity.fitsGlobal(managedRoots, pendingBuild, other.count(), pendingChildren,
                XenoSpaceConfig.asteroidMaxGlobal())) return false;

        List<double[]> nearbyPlayers = viewers((ServerLevel) body.getLevel());
        double release = XenoSpaceConfig.asteroidReleaseRange();
        double releaseSquared = release * release;
        var position = body.logicalPose().position();
        for (double[] viewer : nearbyPlayers) {
            if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2], position.x(), position.y(), position.z()) > releaseSquared)
                continue;
            long used = other.unlocatedReservations();
            for (double[] otherPosition : other.positions())
                if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                        otherPosition[0], otherPosition[1], otherPosition[2]) <= releaseSquared) used++;
            if (runtime != null) {
                for (Active active : runtime.active.values())
                    if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                            active.candidate.x(), active.candidate.y(), active.candidate.z()) <= releaseSquared) used++;
                if (runtime.building != null && AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                        runtime.building.x(), runtime.building.y(), runtime.building.z()) <= releaseSquared) used++;
            }
            if (!AsteroidSplitCapacity.fitsNearbyViewer(used, pendingChildren,
                    XenoSpaceConfig.asteroidMaxPerPlayer())) return false;
        }
        if (runtime != null) {
            // Sable is about to add split children. Any same-tick census must be refreshed before another
            // activation decision so the child bodies consume the hard global/per-player caps.
            runtime.capacityBodiesSnapshot = null;
            runtime.capacityBodiesSnapshotTick = Long.MIN_VALUE;
        }
        return true;
    }

    /** Tags newly allocated children linked to this parent by Sable's split assembly path. */
    public static void tagNewAsteroidFragments(ServerSubLevel parent, Set<UUID> subLevelsBeforeSplit) {
        if (parent == null || subLevelsBeforeSplit == null) return;
        Runtime runtime = RUNTIMES.get(parent.getLevel().getServer());
        if (runtime != null) {
            runtime.capacityBodiesSnapshot = null;
            runtime.capacityBodiesSnapshotTick = Long.MIN_VALUE;
        }
        var container = SubLevelContainer.getContainer(parent.getLevel());
        AsteroidField.ObjectId rootId = asteroidFamilyId(parent, container);
        if (rootId == null) return;
        long[] encodedId = {rootId.cellX(), rootId.cellY(), rootId.cellZ(), rootId.slot()};
        for (SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel child) || child == parent || child.isRemoved()
                    || subLevelsBeforeSplit.contains(child.getUniqueId()) || isAsteroid(child)) continue;
            // A third-party SplitListener can allocate an unrelated sublevel during the same synchronous split call.
            // Sable's assembleBlocks path sets this exact parent UUID on real split children, so require that link
            // before assigning fragment expiry/cap ownership metadata.
            if (!parent.getUniqueId().equals(child.getSplitFromSubLevel())) continue;
            CompoundTag userData = child.getUserDataTag();
            if (userData == null) {
                userData = new CompoundTag();
                child.setUserDataTag(userData);
            }
            CompoundTag fragment = new CompoundTag();
            fragment.putLongArray("rootId", encodedId);
            fragment.putUUID("parent", parent.getUniqueId());
            fragment.putLong("createdTick", parent.getLevel().getGameTime());
            userData.put(FRAGMENT_TAG, fragment);
            XenoPixelsMod.LOGGER.info("Asteroid {} split child tagged: parent={} child={}",
                    rootId, parent.getUniqueId(), child.getUniqueId());
        }
    }

    private static AsteroidField.ObjectId asteroidFamilyId(ServerSubLevel body,
                                                            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container) {
        if (body == null || container == null) return null;
        if (isAsteroid(body)) return idOf(body.getUserDataTag().getCompound(TAG));
        CompoundTag userData = body.getUserDataTag();
        if (userData != null && userData.contains(FRAGMENT_TAG)) {
            AsteroidField.ObjectId stored = fragmentId(userData.getCompound(FRAGMENT_TAG));
            if (stored != null) return stored;
        }
        UUID parentId = body.getSplitFromSubLevel();
        int remaining = Math.max(1, container.getAllSubLevels().size());
        while (parentId != null && remaining-- > 0) {
            SubLevel parent = container.getSubLevel(parentId);
            if (!(parent instanceof ServerSubLevel serverParent)) return null;
            if (isAsteroid(serverParent)) return idOf(serverParent.getUserDataTag().getCompound(TAG));
            CompoundTag parentData = serverParent.getUserDataTag();
            if (parentData != null && parentData.contains(FRAGMENT_TAG)) {
                AsteroidField.ObjectId stored = fragmentId(parentData.getCompound(FRAGMENT_TAG));
                if (stored != null) return stored;
            }
            parentId = serverParent.getSplitFromSubLevel();
        }
        return null;
    }

    private static AsteroidField.ObjectId fragmentId(CompoundTag tag) {
        long[] rootId = tag.getLongArray("rootId");
        if (rootId.length != 4 || rootId[3] < 0 || rootId[3] >= AsteroidField.CANDIDATES_PER_CELL) return null;
        return new AsteroidField.ObjectId(rootId[0], rootId[1], rootId[2], (int) rootId[3]);
    }

    @SubscribeEvent public static void blockBroken(BlockEvent.BreakEvent event) {
        if (markTouched(event.getLevel(), event.getPos())) event.setCanceled(true);
    }
    @SubscribeEvent public static void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (markTouched(event.getLevel(), event.getPos())) event.setCanceled(true);
    }

    /**
     * Sable keeps a body's blocks in the host ServerLevel at plot coordinates, so the event's level is the Voidsea, never
     * the sub-level (ServerSubLevel is not a LevelAccessor; javap, Sable 2.0.3). The body is found through the plot that
     * owns the position: SubLevelContainer.getPlot(ChunkPos) -> LevelPlot.getSubLevel().
     */
    private static boolean markTouched(LevelAccessor level, net.minecraft.core.BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        var plot = SubLevelContainer.getContainer(serverLevel).getPlot(new net.minecraft.world.level.ChunkPos(pos));
        if (plot == null || !(plot.getSubLevel() instanceof ServerSubLevel body)) return false;
        if (isAsteroidFragment(body)) {
            body.getUserDataTag().getCompound(FRAGMENT_TAG).putLong("cleanupCheckAfter", 0L);
            Runtime runtime = RUNTIMES.get(serverLevel.getServer());
            if (runtime != null) runtime.fragmentExpiryScans.remove(body.getUniqueId());
            return false;
        }
        if (!isAsteroid(body)) return false;
        if (body.getUserDataTag().getCompound(TAG).getBoolean("fractureHeatMapBootstrapping")) return true;
        AsteroidField.ObjectId id = idOf(body.getUserDataTag().getCompound(TAG));
        // Reserve the deterministic identity before accepting the first edit. If the bounded
        // ledger is saturated, cancel the edit instead of accepting a change release cannot keep.
        if (id == null || !AsteroidSavedData.get(serverLevel).detach(id, body.getUniqueId())) return true;
        body.getUserDataTag().getCompound(TAG).putBoolean(TOUCHED_TAG, true);
        return false;
    }

    static boolean isTouched(CompoundTag userData, double originalMass, double currentMass) {
        boolean explicitlyTouched = userData != null && userData.contains(TAG)
                && userData.getCompound(TAG).getBoolean(TOUCHED_TAG);
        return explicitlyTouched || (!Double.isNaN(originalMass)
                && Math.abs(currentMass - originalMass) > Math.max(1.0, originalMass * 1e-3));
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level == null) return;
        boolean enabled = XenoSpaceConfig.asteroidPhysical() && XenoSpaceConfig.asteroidBelts();
        long now = server.overworld().getGameTime();
        Runtime runtime = RUNTIMES.get(server);
        if (runtime == null) {
            if (!enabled && !XenoSpaceConfig.asteroidFracture()) {
                long nextProbe = NEXT_FRAGMENT_RUNTIME_PROBE.getOrDefault(server, Long.MIN_VALUE);
                if (now < nextProbe) return;
                NEXT_FRAGMENT_RUNTIME_PROBE.put(server, now + PASS_TICKS * 10L);
                if (!hasLoadedAsteroidFragments(level)) return;
            }
            runtime = new Runtime();
            RUNTIMES.put(server, runtime);
        }
        if (enabled && !runtime.adopted) adopt(level, runtime);
        stepBuild(level, runtime);
        stepExpiredFragments(level, runtime, now);
        long fractureStarted = System.nanoTime();
        stepFractureHeatMap(runtime);
        stepImpactSampling(level, runtime, now);
        stepFractureCuts(level, runtime);
        if (XenoSpaceConfig.asteroidFracture()) {
            runtime.fractureWorkLastNanos = System.nanoTime() - fractureStarted;
            runtime.fractureWorkMaxNanos = Math.max(runtime.fractureWorkMaxNanos, runtime.fractureWorkLastNanos);
        }
        if (now >= runtime.nextPass) {
            runtime.nextPass = now + PASS_TICKS;
            pass(server, level, runtime, enabled);
        }
        if (enabled && runtime.job == null && now >= runtime.nextSpawn && !runtime.wanted.isEmpty()
                && runtime.active.size() < XenoSpaceConfig.asteroidMaxGlobal() && runtime.heatMapBootstraps.isEmpty()) {
            StartNextResult result = startNext(level, runtime);
            runtime.nextSpawn = net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.nextAttemptTick(
                    now, XenoSpaceConfig.asteroidSpawnIntervalTicks(), result == StartNextResult.BUDGET_DENIED);
        }
        if (runtime.presenceDirty) {
            runtime.presenceDirty = false;
            var packet = presence(runtime);
            for (ServerPlayer player : level.players()) AddonNetwork.sendToPlayer(player, packet);
        }
    }

    /** Re-registers tagged asteroids that are already loaded (a crash left them stored); drops extra copies of one id. */
    private static void adopt(ServerLevel level, Runtime runtime) {
        runtime.adopted = true;
        var container = SubLevelContainer.getContainer(level);
        long fieldSeed = AsteroidFieldSnapshot.fieldSeed(level.getServer().overworld().getSeed());
        for (var sub : new ArrayList<>(container.getAllSubLevels())) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || !isAsteroid(body)) continue;
            CompoundTag asteroidTag = body.getUserDataTag().getCompound(TAG);
            if (asteroidTag.getBoolean("building")) {
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                XenoPixelsMod.LOGGER.warn("Discarded incomplete asteroid build {} left by an unclean shutdown", body.getUniqueId());
                continue;
            }
            var id = idOf(asteroidTag);
            if (id == null) continue;
            if (runtime.active.containsKey(id)) {
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                XenoPixelsMod.LOGGER.warn("Asteroid {}: removed a second loaded copy {}", id, body.getUniqueId());
                continue;
            }
            var candidate = candidateFor(level, fieldSeed, id);
            if (candidate == null) continue;
            var active = new Active(candidate, body.getUniqueId());
            active.originalMass = asteroidTag.getDouble("mass");
            active.fractures = Math.max(0, asteroidTag.getInt("fractureCutCount"));
            runtime.active.put(id, active);
            if (XenoSpaceConfig.asteroidFracture()) queueFractureHeatMap(runtime, body, candidate);
            runtime.presenceDirty = true;
            XenoPixelsMod.LOGGER.info("Asteroid {} adopted from storage: {}", id, body.getUniqueId());
        }
    }

    private static void stepBuild(ServerLevel level, Runtime runtime) {
        if (runtime.job == null) return;
        var candidate = runtime.building;
        var container = SubLevelContainer.getContainer(level);
        try {
            long tick = level.getServer().overworld().getGameTime();
            if (runtime.job.body() == null && !net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.mayPrepare(
                    level.getServer(), tick, net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID,
                    XenoSpaceConfig.asteroidBuildVoxelsPerTick())) return;
            if (!runtime.job.prepare()) {
                if (runtime.job.failed()) {
                    XenoPixelsMod.LOGGER.warn("Asteroid {} build failed: no free plot", candidate.id());
                    abandonBuild(container, runtime);
                }
                return;
            }
            // Keep an allocated, not-yet-installed body alive if another manager spent this tick's shared budget.
            if (!runtime.buildTicketed && runtime.job.body() != null && !runtime.job.body().isRemoved()) {
                container.addForceLoadTicket(runtime.job.body(), TICKET, candidate.id().toString());
                runtime.buildTicketed = true;
            }
            int stepBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.allowance(
                    level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID,
                    runtime.job, XenoSpaceConfig.asteroidBuildVoxelsPerTick());
            if (stepBudget <= 0) return;
            double millisBudget = net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.remainingMillis(
                    level.getServer(), tick, net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID);
            if (XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0 && millisBudget <= 0.0) return;
            int before = runtime.job.installed();
            long sliceStarted = System.nanoTime();
            boolean complete = runtime.job.step(stepBudget, millisBudget);
            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.record(level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID,
                    runtime.job.installed() - before);
            net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.recordMillis(level.getServer(), tick,
                    net.bullettrain.xenopixelsmod.space.physics.SpaceBuildWorkBudget.Owner.ASTEROID,
                    (System.nanoTime() - sliceStarted) / 1e6);
            if (!complete) {
                if (runtime.job.failed()) {
                    XenoPixelsMod.LOGGER.warn("Asteroid {} build failed (no free plot)", candidate.id());
                    abandonBuild(container, runtime);
                }
                return;
            }
        } catch (RuntimeException error) {
            XenoPixelsMod.LOGGER.error("Asteroid {} build failed", candidate.id(), error);
            abandonBuild(container, runtime);
            return;
        }
        PlotBodyBuilder.Job completedJob = runtime.job;
        ServerSubLevel body = completedJob.body();
        int voxels = completedJob.installed();
        runtime.job = null; runtime.building = null;
        boolean ticketed = runtime.buildTicketed;
        runtime.buildTicketed = false;
        CompoundTag tag = new CompoundTag();
        tag.putLongArray("id", new long[]{candidate.id().cellX(), candidate.id().cellY(), candidate.id().cellZ(), candidate.id().slot()});
        tag.putBoolean("building", false);
        tag.putInt("voxels", voxels);
        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
        body.getUserDataTag().put(TAG, tag);
        if (XenoSpaceConfig.asteroidFracture()) queueFractureHeatMap(runtime, body, completedJob);
        if (!ticketed) container.addForceLoadTicket(body, TICKET, candidate.id().toString());
        runtime.active.put(candidate.id(), new Active(candidate, body.getUniqueId()));
        var buildResult = completedJob.result();
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordBuild(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.ASTEROID, System.nanoTime(), voxels,
                buildResult.fillMillis(), buildResult.installMillis(), completedJob.maxServerSliceMillis());
        runtime.built++;
        runtime.presenceDirty = true;
        XenoPixelsMod.LOGGER.info("Asteroid {} built: r={} icy={} voxels={} at ({}, {}, {}) uuid={}", candidate.id(), candidate.radius(),
                candidate.icy(), voxels, Math.round(candidate.x()),
                Math.round(candidate.y()), Math.round(candidate.z()), body.getUniqueId());
    }

    /** Queues Sable connectivity work in bounded per-tick batches; fracture remains disabled by default. */
    private static void queueFractureHeatMap(Runtime runtime, ServerSubLevel body, PlotBodyBuilder.Job job) {
        int count = job.sourceVoxelCount();
        queueFractureHeatMap(runtime, body, new HeatMapVoxelSource() {
            @Override public int count() { return count; }
            @Override public BlockPos hostPosition(int index) { return job.hostBlockAt(index); }
            @Override public boolean sourceSolid(int index) { return true; }
        });
    }

    private static void queueFractureHeatMap(Runtime runtime, ServerSubLevel body, AsteroidField.Candidate candidate) {
        var voxels = new AsteroidVoxels(candidate);
        var plot = body.getPlot();
        var centerBlock = plot.getCenterBlock();
        var localCenterChunk = plot.toLocal(new net.minecraft.world.level.ChunkPos(centerBlock));
        int centerX = localCenterChunk.x * 16 + (centerBlock.getX() & 15);
        int centerY = centerBlock.getY();
        int centerZ = localCenterChunk.z * 16 + (centerBlock.getZ() & 15);
        int extent = voxels.extent(), side = extent * 2 + 1;
        int count = side * side * side;
        var hostChunks = new HashMap<Long, net.minecraft.world.level.ChunkPos>();
        queueFractureHeatMap(runtime, body, new HeatMapVoxelSource() {
            @Override public int count() { return count; }
            @Override public boolean sourceSolid(int index) {
                int x = index / (side * side) - extent;
                int y = index / side % side - extent;
                int z = index % side - extent;
                return voxels.at(x, y, z) != null;
            }
            @Override public BlockPos hostPosition(int index) {
                int x = index / (side * side) - extent;
                int y = index / side % side - extent;
                int z = index % side - extent;
                int localX = centerX + x, localY = centerY + y, localZ = centerZ + z;
                int cx = Math.floorDiv(localX, 16), cz = Math.floorDiv(localZ, 16);
                long key = net.minecraft.world.level.ChunkPos.asLong(cx, cz);
                var global = hostChunks.computeIfAbsent(key, ignored -> plot.toGlobal(new net.minecraft.world.level.ChunkPos(cx, cz)));
                return new BlockPos(global.getMinBlockX() + Math.floorMod(localX, 16), localY,
                        global.getMinBlockZ() + Math.floorMod(localZ, 16));
            }
        });
    }

    private static void queueFractureHeatMap(Runtime runtime, ServerSubLevel body, HeatMapVoxelSource source) {
        if (body.getUserDataTag() == null) return;
        CompoundTag asteroidTag = body.getUserDataTag().getCompound(TAG);
        if (asteroidTag.getBoolean("fractureHeatMapReady") || asteroidTag.getBoolean("fractureHeatMapAttempted")) return;
        if (!fractureHeatMapManaged(body)) {
            asteroidTag.putBoolean("fractureHeatMapAttempted", true);
            XenoPixelsMod.LOGGER.error("Asteroid {} fracture heat map bootstrap refused: body management and Sable skip predicates disagree",
                    body.getUniqueId());
            return;
        }
        if (!(body.getHeatMapManager() instanceof SableHeatMapFractureAccess access)) {
            asteroidTag.putBoolean("fractureHeatMapAttempted", true);
            XenoPixelsMod.LOGGER.error("Asteroid {} fracture heat map unavailable: Sable mixin access missing", body.getUniqueId());
            return;
        }
        try {
            access.xenopixels$beginFractureHeatMapRebuild();
            asteroidTag.putBoolean("fractureHeatMapAttempted", true);
            asteroidTag.putBoolean("fractureHeatMapBootstrapping", true);
            runtime.heatMapBootstraps.addLast(new HeatMapBootstrap(body, source));
        } catch (RuntimeException | LinkageError failure) {
            access.xenopixels$abortFractureHeatMapRebuild();
            asteroidTag.putBoolean("fractureHeatMapAttempted", true);
            XenoPixelsMod.LOGGER.error("Asteroid {} fracture heat map bootstrap could not start", body.getUniqueId(), failure);
        }
    }

    private static void stepFractureHeatMap(Runtime runtime) {
        if (runtime.heatMapBootstraps.isEmpty()) return;
        HeatMapBootstrap bootstrap = runtime.heatMapBootstraps.peekFirst();
        var body = bootstrap.body;
        var tag = body.getUserDataTag() == null ? null : body.getUserDataTag().getCompound(TAG);
        if (!XenoSpaceConfig.asteroidFracture()) {
            ((SableHeatMapFractureAccess) body.getHeatMapManager()).xenopixels$abortFractureHeatMapRebuild();
            if (tag != null) {
                tag.remove("fractureHeatMapBootstrapping");
                tag.remove("fractureHeatMapAttempted");
            }
            runtime.heatMapBootstraps.removeFirst();
            return;
        }
        if (body.isRemoved() || tag == null) {
            ((SableHeatMapFractureAccess) body.getHeatMapManager()).xenopixels$abortFractureHeatMapRebuild();
            runtime.heatMapBootstraps.removeFirst();
            return;
        }
        if (!fractureHeatMapManaged(body)) {
            ((SableHeatMapFractureAccess) body.getHeatMapManager()).xenopixels$abortFractureHeatMapRebuild();
            tag.remove("fractureHeatMapBootstrapping");
            tag.putBoolean("fractureHeatMapAttempted", true);
            runtime.heatMapBootstraps.removeFirst();
            XenoPixelsMod.LOGGER.error("Asteroid {} fracture heat map rebuild aborted: body management and Sable skip predicates disagree",
                    body.getUniqueId());
            return;
        }
        var access = (SableHeatMapFractureAccess) body.getHeatMapManager();
        if (bootstrap.phase < 2) {
            int budget = Math.max(1, Math.min(2_000, XenoSpaceConfig.asteroidBuildVoxelsPerTick()));
            int completedThisTick = 0;
            while (bootstrap.index < bootstrap.source.count() && completedThisTick < budget) {
                int index = bootstrap.index++;
                completedThisTick++;
                boolean sourceSolid = bootstrap.source.sourceSolid(index);
                BlockPos pos = bootstrap.source.hostPosition(index);
                boolean clearingPhase = bootstrap.phase == 0;
                boolean currentSolid = !clearingPhase && !body.getLevel().getBlockState(pos).isAir();
                if (!shouldVisitHeatMapCell(clearingPhase, sourceSolid, currentSolid)) continue;
                if (clearingPhase) access.xenopixels$removeFractureHeatMapBlock(pos);
                else if (currentSolid) {
                    access.xenopixels$addFractureHeatMapBlock(pos);
                    bootstrap.registered++;
                }
            }
            if (bootstrap.index >= bootstrap.source.count()) {
                bootstrap.phase++;
                bootstrap.index = 0;
            }
            return;
        }
        if (bootstrap.phase == 2) {
            if (bootstrap.dirty) {
                if (shouldAbortDirtyHeatMapRestart(bootstrap.dirtyRestarts, MAX_DIRTY_HEAT_MAP_RESTARTS)) {
                    access.xenopixels$abortFractureHeatMapRebuild();
                    tag.remove("fractureHeatMapBootstrapping");
                    tag.putBoolean("fractureHeatMapAttempted", true);
                    runtime.heatMapBootstraps.removeFirst();
                    XenoPixelsMod.LOGGER.warn("Asteroid {} fracture heat map rebuild aborted after {} dirty restarts; toggle fracture off/on to retry",
                            body.getUniqueId(), bootstrap.dirtyRestarts);
                    return;
                }
                // A real block edit during replay was held out of Sable's partial map. Restart from live host
                // blocks so it is incorporated exactly once; work remains bounded by the per-tick scan budget.
                HeatMapVoxelSource currentPlot = currentPlotHeatMapSource(body);
                if (currentPlot == null) {
                    access.xenopixels$abortFractureHeatMapRebuild();
                    tag.remove("fractureHeatMapBootstrapping");
                    tag.putBoolean("fractureHeatMapAttempted", true);
                    runtime.heatMapBootstraps.removeFirst();
                    XenoPixelsMod.LOGGER.error("Asteroid {} fracture heat map rebuild aborted after edit: plot bounds are invalid or too large",
                            body.getUniqueId());
                    return;
                }
                access.xenopixels$beginFractureHeatMapRebuild();
                bootstrap.source = currentPlot;
                bootstrap.phase = 0;
                bootstrap.index = 0;
                bootstrap.registered = 0;
                bootstrap.dirty = false;
                bootstrap.dirtyRestarts++;
                return;
            }
            if (bootstrap.registered == 0) {
                access.xenopixels$abortFractureHeatMapRebuild();
                tag.remove("fractureHeatMapBootstrapping");
                runtime.heatMapBootstraps.removeFirst();
                XenoPixelsMod.LOGGER.warn("Asteroid {} fracture heat map bootstrap skipped: no current solid blocks", body.getUniqueId());
                return;
            }
            access.xenopixels$finishFractureHeatMapRebuild();
            bootstrap.phase = 3;
            tag.remove("fractureHeatMapBootstrapping");
            XenoPixelsMod.LOGGER.info("Asteroid {} fracture heat map blocks registered={} elapsed ms={} (awaiting propagation)",
                    body.getUniqueId(), bootstrap.registered, String.format("%.2f", (System.nanoTime() - bootstrap.startedNanos) / 1e6));
            return;
        }
        if (access.xenopixels$fractureHeatMapReady(bootstrap.registered)) {
            tag.putBoolean("fractureHeatMapReady", true);
            runtime.heatMapBootstraps.removeFirst();
            XenoPixelsMod.LOGGER.info("Asteroid {} fracture heat map propagation complete", body.getUniqueId());
        }
    }

    static boolean shouldVisitHeatMapCell(boolean clearingPhase, boolean sourceSolid) {
        return shouldVisitHeatMapCell(clearingPhase, sourceSolid, false);
    }

    static boolean shouldAbortDirtyHeatMapRestart(int completedRestarts, int maxRestarts) {
        return completedRestarts >= maxRestarts;
    }

    static boolean shouldVisitHeatMapCell(boolean clearingPhase, boolean sourceSolid, boolean currentSolid) {
        return clearingPhase || sourceSolid || currentSolid;
    }

    /** Full current plot AABB source used after an edit invalidates the initial deterministic/job voxel list. */
    private static HeatMapVoxelSource currentPlotHeatMapSource(ServerSubLevel body) {
        var raw = body.getPlot().getBoundingBox();
        if (raw == null) return null;
        BoundingBox3i bounds = new BoundingBox3i(raw);
        long volume = AsteroidFragmentScanMath.volume(bounds.minX(), bounds.minY(), bounds.minZ(),
                bounds.maxX(), bounds.maxY(), bounds.maxZ());
        if (volume <= 0 || volume > Integer.MAX_VALUE) return null;
        int count = (int) volume;
        int sizeX = bounds.maxX() - bounds.minX() + 1;
        int sizeY = bounds.maxY() - bounds.minY() + 1;
        int sizeZ = bounds.maxZ() - bounds.minZ() + 1;
        return new HeatMapVoxelSource() {
            @Override public int count() { return count; }
            @Override public boolean sourceSolid(int index) { return true; }
            @Override public BlockPos hostPosition(int index) {
                int x = index / (sizeY * sizeZ);
                int remainder = index % (sizeY * sizeZ);
                int y = remainder / sizeZ;
                int z = remainder % sizeZ;
                return new BlockPos(bounds.minX() + x, bounds.minY() + y, bounds.minZ() + z);
            }
        };
    }

    private static boolean fractureHeatMapManaged(ServerSubLevel body) {
        boolean managed = manages(body);
        boolean skipPredicate = PhysicalEarth.skipsSplitHeatMap(body);
        return managed && skipPredicate;
    }

    /** Samples body pose once per tick; this avoids depending on when Sable writes latestLinearVelocity. */
    private static void stepImpactSampling(ServerLevel level, Runtime runtime, long now) {
        if (!XenoSpaceConfig.asteroidFracture()) return;
        var container = SubLevelContainer.getContainer(level);
        for (var entry : runtime.active.entrySet()) {
            Active active = entry.getValue();
            if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) continue;
            var position = body.logicalPose().position();
            FractureVelocityTracker.Sample sample = active.fractureVelocity.sample(position.x, position.y, position.z, now);
            if (sample == null || now < active.fractureReadyAt || !sample.exceeds(XenoSpaceConfig.asteroidFractureDeltaV())) continue;
            CompoundTag root = body.getUserDataTag() == null ? null : body.getUserDataTag().getCompound(TAG);
            if (root == null || !root.getBoolean("fractureHeatMapReady")
                    || root.getBoolean("fractureHeatMapBootstrapping") || root.getBoolean("fractureCutActive")) continue;

            // The strict per-root/global caps are checked at Sable's actual split() call, where its exact pending
            // component map is available. fractureCutCount is historical and must not be used as live occupancy:
            // doing so would permanently stop a root after expired fragments freed their slots.

            Vector3d localDelta = new Vector3d(sample.deltaVX(), sample.deltaVY(), sample.deltaVZ());
            body.logicalPose().orientation().transformInverse(localDelta);
            AsteroidFractureMath.Plane plane = AsteroidFractureMath.seededPlane(active.candidate.seed(),
                    0, 0, 0, active.candidate.radius(), localDelta.x, localDelta.y, localDelta.z);
            if (plane == null) continue;
            double topologyMinimum = AsteroidFractureMath.minimumHalfWidthFor18NeighborSeparation(
                    plane.normalX(), plane.normalY(), plane.normalZ());
            if (!Double.isFinite(topologyMinimum)) continue;
            double slabHalfWidth = Math.max(XenoSpaceConfig.asteroidFractureSlabHalfWidth(), topologyMinimum);
            if (runtime.fractureCuts.hasBody(body.getUniqueId())) continue;
            // Reserve the field identity before the first slab block can be removed. If the bounded
            // persistence ledger is full, skip fracture work rather than mutate a body we cannot save.
            if (!AsteroidSavedData.get(level).detach(entry.getKey(), body.getUniqueId())) continue;
            root.putBoolean(TOUCHED_TAG, true);
            if (!runtime.fractureCuts.enqueue(body.getUniqueId(),
                    new FractureCut(entry.getKey(), body, active.candidate, plane, slabHalfWidth))) continue;
            active.fractureReadyAt = now + XenoSpaceConfig.asteroidFractureCooldownTicks();
            root.putBoolean("fractureCutActive", true);
            XenoPixelsMod.LOGGER.info("Asteroid {} impact candidate: deltaV={} blocks/s; queued bounded seeded slab cut",
                    entry.getKey(), String.format("%.2f", sample.deltaV()));
        }
    }

    /** Removes only slab voxels, at a bounded number of inspected source positions each server tick. Queued work drains
     * even if the toggle is turned off; the toggle prevents new cuts, not completion of an already-started cut. */
    private static void stepFractureCuts(ServerLevel level, Runtime runtime) {
        if (runtime.fractureCuts.isEmpty()) return;
        var work = runtime.fractureCuts.first();
        FractureCut cut = work.payload();
        if (cut.body.isRemoved()) {
            clearFractureCutMarker(cut.body);
            runtime.fractureCuts.removeFirst();
            return;
        }
        int budget = Math.max(1, Math.min(2_000, XenoSpaceConfig.asteroidBuildVoxelsPerTick()));
        int inspected = 0;
        while (cut.index < cut.voxelCount() && inspected < budget) {
            int x = cut.x(), y = cut.y(), z = cut.z();
            cut.index++;
            inspected++;
            if (cut.voxels.at(x, y, z) == null || !cut.plane.withinSlab(x, y, z, cut.slabHalfWidth)) continue;
            BlockPos hostPos = cut.hostPosition(x, y, z);
            if (level.getBlockState(hostPos).isAir()) continue;
            if (level.setBlock(hostPos, Blocks.AIR.defaultBlockState(), 3)) {
                cut.removed++;
                CompoundTag userData = cut.body.getUserDataTag();
                if (userData != null && userData.contains(TAG))
                    userData.getCompound(TAG).putBoolean(TOUCHED_TAG, true);
            }
        }
        if (cut.index < cut.voxelCount()) return;

        var body = cut.body;
        CompoundTag root = body.getUserDataTag() == null ? null : body.getUserDataTag().getCompound(TAG);
        if (root != null) {
            root.remove("fractureCutActive");
            root.putInt("fractureCutCount", root.getInt("fractureCutCount") + (cut.removed > 0 ? 1 : 0));
            if (cut.removed > 0) root.putBoolean(TOUCHED_TAG, true);
        }
        if (cut.removed > 0) {
            Active active = runtime.active.get(cut.id);
            if (active != null) active.fractures++;
        }
        XenoPixelsMod.LOGGER.info("Asteroid {} seeded slab cut finished: removed={} inspected={} (Sable split/persistence not yet verified)",
                cut.id, cut.removed, cut.index);
        runtime.fractureCuts.removeFirst();
    }

    private static void clearFractureCutMarker(ServerSubLevel body) {
        CompoundTag userData = body.getUserDataTag();
        if (userData != null && userData.contains(TAG)) userData.getCompound(TAG).remove("fractureCutActive");
    }

    /** Stops a partial cut before releasing its root to reconcile the configured active-body caps. */
    private static void cancelFractureCut(ServerLevel level, Runtime runtime, UUID bodyId) {
        if (runtime.fractureCuts.cancelBody(bodyId) == 0) return;
        if (SubLevelContainer.getContainer(level).getSubLevel(bodyId) instanceof ServerSubLevel body)
            clearFractureCutMarker(body);
        XenoPixelsMod.LOGGER.warn("Canceled partial asteroid cut for {} to enforce current active-body caps; removed voxels remain saved",
                bodyId);
    }

    /**
     * Finds expired tiny fragments and inspects at most 2,000 host positions per server tick. If the current solid
     * count reaches the configured minimum, the fragment is retained. Otherwise only the inspected solid positions
     * (strictly fewer than the minimum) are cleared before REMOVED releases the sub-level slot.
     */
    private static void stepExpiredFragments(ServerLevel level, Runtime runtime, long now) {
        var container = SubLevelContainer.getContainer(level);
        if (now >= runtime.nextFragmentDiscovery) {
            runtime.nextFragmentDiscovery = now + PASS_TICKS;
            int lifetime = XenoSpaceConfig.asteroidFragmentLifetimeTicks();
            for (SubLevel sub : container.getAllSubLevels()) {
                if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || !isAsteroidFragment(body)
                        || !body.getTrackingPlayers().isEmpty() || runtime.fragmentExpiryScans.containsKey(body.getUniqueId())) continue;
                CompoundTag fragment = body.getUserDataTag().getCompound(FRAGMENT_TAG);
                if (!fragment.contains("createdTick")) continue;
                long created = fragment.getLong("createdTick");
                if (now < created || now - created < lifetime || now < fragment.getLong("cleanupCheckAfter")) continue;
                var localBounds = body.getPlot().getBoundingBox();
                if (localBounds == null) continue;
                FragmentExpiryScan scan = new FragmentExpiryScan(body.getUniqueId(), new BoundingBox3i(localBounds));
                if (scan.volume == 0) {
                    // A shutdown during a prior tiny-fragment clear can leave an already-empty child registered.
                    if (container.getSubLevel(body.getUniqueId()) == body) {
                        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                        runtime.fragmentsExpired++;
                        XenoPixelsMod.LOGGER.info("Removed empty expired asteroid fragment: uuid={}", body.getUniqueId());
                    }
                    continue;
                }
                if (scan.volume == Long.MAX_VALUE) {
                    fragment.putLong("cleanupCheckAfter", now + PASS_TICKS * 10L);
                    continue; // box cannot be linearly indexed safely; retain it rather than wrap the scan cursor
                }
                if (scan.volume > 0) runtime.fragmentExpiryScans.put(body.getUniqueId(), scan);
            }
        }

        runtime.fragmentPositionsScannedLast = 0;
        if (runtime.fragmentExpiryScans.isEmpty()) return;
        FragmentExpiryScan scan = runtime.fragmentExpiryScans.values().iterator().next(); // insertion order gives deterministic FIFO work
        SubLevel current = container.getSubLevel(scan.bodyId);
        if (!(current instanceof ServerSubLevel body) || body.isRemoved() || !isAsteroidFragment(body)) {
            runtime.fragmentExpiryScans.remove(scan.bodyId);
            return;
        }
        if (!body.getTrackingPlayers().isEmpty()) {
            runtime.fragmentExpiryScans.remove(scan.bodyId); // rediscovery resumes after it is no longer tracked
            return;
        }
        var currentBounds = body.getPlot().getBoundingBox();
        if (currentBounds == null) {
            container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
            runtime.fragmentsExpired++;
            runtime.fragmentExpiryScans.remove(scan.bodyId);
            return;
        }
        if (!scan.bounds.equals(currentBounds)) scan.reset(new BoundingBox3i(currentBounds));
        if (scan.volume <= 0) {
            if (container.getSubLevel(scan.bodyId) == body) {
                container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                runtime.fragmentsExpired++;
            }
            runtime.fragmentExpiryScans.remove(scan.bodyId);
            return;
        }

        int minimum = XenoSpaceConfig.asteroidFragmentMinBlocks();
        int budget = Math.max(1, Math.min(2_000, XenoSpaceConfig.asteroidBuildVoxelsPerTick()));
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        while (scan.cursor < scan.volume && runtime.fragmentPositionsScannedLast < budget) {
            BlockPos pos = scan.positionAt(scan.cursor++);
            runtime.fragmentPositionsScannedLast++;
            if (!level.getBlockState(mutable.set(pos)).isAir()) {
                scan.solids.add(pos);
                if (scan.solids.size() >= minimum) {
                    body.getUserDataTag().getCompound(FRAGMENT_TAG).putLong("cleanupCheckAfter", now + PASS_TICKS * 10L);
                    runtime.fragmentExpiryScans.remove(scan.bodyId);
                    break;
                }
            }
        }
        runtime.fragmentPositionsScannedMax = Math.max(runtime.fragmentPositionsScannedMax,
                runtime.fragmentPositionsScannedLast);
        if (scan.cursor >= scan.volume) {
            // The current AABB was scanned completely and contains fewer than the configured threshold.
            if (body.getTrackingPlayers().isEmpty() && scan.bounds.equals(body.getPlot().getBoundingBox())) {
                for (BlockPos pos : scan.solids) {
                    if (!level.getBlockState(pos).isAir()) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
                if (!body.isRemoved() && container.getSubLevel(scan.bodyId) == body) {
                    container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
                    runtime.fragmentsExpired++;
                    XenoPixelsMod.LOGGER.info("Expired tiny asteroid fragment: uuid={}, blocks={}, inspected={}, lifetimeTicks={}",
                            scan.bodyId, scan.solids.size(), scan.volume, XenoSpaceConfig.asteroidFragmentLifetimeTicks());
                }
            }
            runtime.fragmentExpiryScans.remove(scan.bodyId);
        }
    }

    private static boolean hasLoadedAsteroidFragments(ServerLevel level) {
        for (SubLevel sub : SubLevelContainer.getContainer(level).getAllSubLevels())
            if (sub instanceof ServerSubLevel body && !body.isRemoved() && isAsteroidFragment(body)) return true;
        return false;
    }

    /** Loaded detached bodies and fragments consume the same active-body budget as deterministic field roots. */
    private static OtherLiveBodies otherLiveBodies(ServerLevel level, Runtime runtime) {
        var container = SubLevelContainer.getContainer(level);
        var savedData = AsteroidSavedData.get(level);
        Set<UUID> managed = new HashSet<>();
        if (runtime != null) {
            for (Active active : runtime.active.values()) managed.add(active.body);
            if (runtime.job != null && runtime.job.body() != null) managed.add(runtime.job.body().getUniqueId());
        }
        Set<UUID> included = new HashSet<>();
        var positions = new ArrayList<double[]>();
        for (SubLevel sub : container.getAllSubLevels()) {
            if (!(sub instanceof ServerSubLevel body) || body.isRemoved()) continue;
            UUID bodyId = body.getUniqueId();
            if (managed.contains(bodyId) || !included.add(bodyId)) continue;
            if (!isAsteroid(body) && !isAsteroidFragment(body) && !savedData.isDetachedBody(bodyId)) continue;
            var p = body.logicalPose().position();
            positions.add(new double[]{p.x(), p.y(), p.z()});
        }
        // Only live sublevels consume activation capacity. UUID-mapped bodies absent from the
        // container are dormant Sable storage and must not permanently consume live slots; loaded
        // mapped bodies were counted above. UUID-less legacy tombstones cannot be matched to a body,
        // so retain a conservative unlocated reservation for those records only.
        return new OtherLiveBodies(List.copyOf(positions), savedData.unmappedDetachedCount());
    }

    /** Drops the build in progress: its ticket first, then the half-built body. */
    private static void abandonBuild(dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container, Runtime runtime) {
        var body = runtime.job.body();
        if (runtime.buildTicketed && body != null && !body.isRemoved())
            container.removeForceLoadTicket(body, TICKET, runtime.building.id().toString());
        runtime.job.cancel();
        runtime.job = null; runtime.building = null; runtime.buildTicketed = false;
    }

    private static void pass(MinecraftServer server, ServerLevel level, Runtime runtime, boolean enabled) {
        var container = SubLevelContainer.getContainer(level);
        OtherLiveBodies otherBodies = otherLiveBodies(level, runtime);
        runtime.capacityBodiesSnapshot = otherBodies;
        runtime.capacityBodiesSnapshotTick = level.getGameTime();
        // Record the settled mass once, after the body's first physics tick, as the "untouched" reference.
        for (var entry : runtime.active.entrySet()) {
            var active = entry.getValue();
            if (Double.isNaN(active.originalMass) && container.getSubLevel(active.body) instanceof ServerSubLevel body) {
                active.originalMass = body.getMassTracker().getMass();
                body.getUserDataTag().getCompound(TAG).putDouble("mass", active.originalMass);
            }
            if (container.getSubLevel(active.body) instanceof ServerSubLevel body && body.getUserDataTag() != null) {
                CompoundTag asteroidTag = body.getUserDataTag().getCompound(TAG);
                if (XenoSpaceConfig.asteroidFracture()) queueFractureHeatMap(runtime, body, active.candidate);
                else asteroidTag.remove("fractureHeatMapAttempted");
            }
        }
        List<double[]> players = viewers(level);
        double release = XenoSpaceConfig.asteroidReleaseRange();
        int maxPerPlayer = XenoSpaceConfig.asteroidMaxPerPlayer();
        int maxGlobal = XenoSpaceConfig.asteroidMaxGlobal();
        if (runtime.job != null) {
            var building = runtime.building;
            boolean keepBuild = enabled && building != null && maxPerPlayer > 0 && maxGlobal > 0
                    && nearestDistance(players, building.x(), building.y(), building.z()) <= release;
            if (!keepBuild) abandonBuild(container, runtime);
        }
        var activeCandidates = new ArrayList<AsteroidField.Candidate>(runtime.active.size());
        var fractureProtected = new HashSet<AsteroidField.ObjectId>();
        for (var entry : runtime.active.entrySet()) {
            activeCandidates.add(entry.getValue().candidate);
            if (runtime.fractureCuts.hasBody(entry.getValue().body)) fractureProtected.add(entry.getKey());
        }
        AsteroidActiveSelector.Selection selection = enabled
                ? AsteroidActiveSelector.select(activeCandidates, players, release, maxPerPlayer, maxGlobal,
                runtime.job == null ? null : runtime.building, fractureProtected,
                otherBodies.positions(), otherBodies.unlocatedReservations())
                : new AsteroidActiveSelector.Selection(Set.of(), false);
        if (runtime.job != null && !selection.keepBuilding()) {
            abandonBuild(container, runtime);
            // Recompute without the canceled reservation so an ordinary active body can use that slot.
            selection = enabled
                    ? AsteroidActiveSelector.select(activeCandidates, players, release, maxPerPlayer, maxGlobal,
                    null, fractureProtected, otherBodies.positions(), otherBodies.unlocatedReservations())
                    : new AsteroidActiveSelector.Selection(Set.of(), false);
        }
        Set<AsteroidField.ObjectId> retained = selection.retained();
        for (var iterator = runtime.active.entrySet().iterator(); iterator.hasNext(); ) {
            var entry = iterator.next();
            if (retained.contains(entry.getKey())) continue;
            if (fractureProtected.contains(entry.getKey()))
                cancelFractureCut(level, runtime, entry.getValue().body);
            if (release(level, runtime, entry.getKey(), entry.getValue(), enabled ? "outside proximity/active caps" : "switched off"))
                iterator.remove();
        }
        if (!enabled) {
            runtime.wanted.clear();
            if (!XenoSpaceConfig.asteroidFracture() && runtime.active.isEmpty() && runtime.heatMapBootstraps.isEmpty()
                    && runtime.fragmentExpiryScans.isEmpty() && !hasLoadedAsteroidFragments(level)) RUNTIMES.remove(server);
            return;
        }
        computeWanted(server, level, runtime, players, otherLiveBodies(level, runtime));
    }

    private static void computeWanted(MinecraftServer server, ServerLevel level, Runtime runtime, List<double[]> players,
                                      OtherLiveBodies otherBodies) {
        long scanStarted = System.nanoTime();
        runtime.wanted.clear();
        runtime.fieldCellCacheHits = 0;
        runtime.fieldCellCacheMisses = 0;
        runtime.scanCellsLast = 0;
        long remainingCapacity = (long) XenoSpaceConfig.asteroidMaxGlobal() - runtime.active.size()
                - (runtime.job == null ? 0 : 1) - otherBodies.count();
        int remainingGlobal = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, remainingCapacity));
        int perPlayerLimit = XenoSpaceConfig.asteroidMaxPerPlayer();
        if (players.isEmpty() || remainingGlobal == 0 || perPlayerLimit == 0) {
            recordScanWork(runtime, scanStarted);
            return;
        }
        var belts = AsteroidFieldSync.belts(SpaceChartService.state(server));
        if (belts.isEmpty()) {
            recordScanWork(runtime, scanStarted);
            return;
        }
        long seed = AsteroidFieldSnapshot.fieldSeed(server.overworld().getSeed());
        var detached = AsteroidSavedData.get(level);
        double range = XenoSpaceConfig.asteroidActivateRange();
        double rangeSquared = range * range;
        var globalNearest = new AsteroidCandidateSelector(remainingGlobal);
        var perPlayerNearest = new ArrayList<AsteroidCandidateSelector>(players.size());
        double releaseRangeSquared = XenoSpaceConfig.asteroidReleaseRange() * XenoSpaceConfig.asteroidReleaseRange();
        for (double[] player : players) {
            int used = otherBodies.unlocatedReservations();
            for (double[] body : otherBodies.positions())
                if (AsteroidCandidateSelector.distanceSquared(player[0], player[1], player[2],
                        body[0], body[1], body[2]) <= releaseRangeSquared) used++;
            for (Active active : runtime.active.values())
                if (AsteroidCandidateSelector.distanceSquared(player[0], player[1], player[2],
                        active.candidate.x(), active.candidate.y(), active.candidate.z()) <= releaseRangeSquared) used++;
            perPlayerNearest.add(new AsteroidCandidateSelector(Math.max(0, perPlayerLimit - used)));
        }

        // Merge viewers with the same cell bounds; this avoids repeating identical field probes for players
        // standing in the same search volume while preserving each player's own distance/cap ranking.
        var areaViewers = new LinkedHashMap<AsteroidCellScanCursor.Bounds, List<Integer>>();
        for (int i = 0; i < players.size(); i++) {
            double[] player = players.get(i);
            long minX = Math.floorDiv((long) Math.floor(player[0] - range), AsteroidField.CELL_SIZE);
            long maxX = Math.floorDiv((long) Math.floor(player[0] + range), AsteroidField.CELL_SIZE);
            long minY = Math.floorDiv((long) Math.floor(player[1] - range), AsteroidField.CELL_SIZE);
            long maxY = Math.floorDiv((long) Math.floor(player[1] + range), AsteroidField.CELL_SIZE);
            long minZ = Math.floorDiv((long) Math.floor(player[2] - range), AsteroidField.CELL_SIZE);
            long maxZ = Math.floorDiv((long) Math.floor(player[2] + range), AsteroidField.CELL_SIZE);
            var bounds = new AsteroidCellScanCursor.Bounds(minX, maxX, minY, maxY, minZ, maxZ);
            areaViewers.computeIfAbsent(bounds, ignored -> new ArrayList<>()).add(i);
        }
        var tasks = new ArrayList<ScanTask>(areaViewers.size() * belts.size());
        for (var area : areaViewers.entrySet()) for (var belt : belts) {
            var key = new ScanCursorKey(seed, belt, area.getKey());
            var cursor = runtime.scanCursors.computeIfAbsent(key, ignored -> new AsteroidCellScanCursor(area.getKey()));
            tasks.add(new ScanTask(belt, cursor, area.getValue()));
        }

        // This bounds cell/belt probes per 20-tick pass even when an operator raises the activation radius or
        // a datapack adds belts. Each task resumes where it stopped; the starting task rotates for fairness.
        int budget = XenoSpaceConfig.asteroidScanCellsPerPass();
        int work = 0;
        if (!tasks.isEmpty()) {
            int taskIndex = Math.floorMod(runtime.scanTaskStart, tasks.size());
            int idle = 0;
            while (work < budget && idle < tasks.size()) {
                ScanTask task = tasks.get(taskIndex);
                if (task.complete) {
                    idle++;
                } else {
                    task.cursor.visit(1, cell -> {
                        if (!cellMayTouch(task.belt, cell.x(), cell.y(), cell.z())) return;
                        for (var candidate : candidatesForCell(runtime, seed, cell, task.belt)) {
                            if (detached.isDetached(candidate.id())) continue;
                            for (int viewerIndex : task.viewers) {
                                double[] viewer = players.get(viewerIndex);
                                double distanceSquared = AsteroidCandidateSelector.distanceSquared(
                                        viewer[0], viewer[1], viewer[2], candidate.x(), candidate.y(), candidate.z());
                                if (distanceSquared <= rangeSquared)
                                    perPlayerNearest.get(viewerIndex).offer(candidate, distanceSquared);
                            }
                        }
                    });
                    work++;
                    if (task.cursor.nextIndex() == 0) {
                        // A cursor wraps only after one complete sweep; don't spend this pass rescanning it.
                        task.complete = true;
                        idle++;
                    } else idle = 0;
                }
                taskIndex = (taskIndex + 1) % tasks.size();
            }
            runtime.scanTaskStart = taskIndex;
        }
        runtime.scanCellsLast = work;
        for (var nearest : perPlayerNearest)
            nearest.offerAllTo(globalNearest, ranked -> !runtime.active.containsKey(ranked.candidate().id())
                    && (runtime.building == null || !runtime.building.id().equals(ranked.candidate().id())));
        for (var ranked : globalNearest.ranked()) runtime.wanted.add(ranked.candidate());
        recordScanWork(runtime, scanStarted);
    }

    private static void recordScanWork(Runtime runtime, long started) {
        runtime.scanWorkLastNanos = System.nanoTime() - started;
        runtime.scanWorkMaxNanos = Math.max(runtime.scanWorkMaxNanos, runtime.scanWorkLastNanos);
    }

    private static List<AsteroidField.Candidate> candidatesForCell(Runtime runtime, long seed,
                                                                     AsteroidField.Cell cell,
                                                                     AsteroidField.Belt belt) {
        FieldCellKey key = new FieldCellKey(seed, belt, cell);
        List<AsteroidField.Candidate> cached = runtime.fieldCellCache.get(key);
        if (cached != null) {
            runtime.fieldCellCacheHits++;
            return cached;
        }
        runtime.fieldCellCacheMisses++;
        List<AsteroidField.Candidate> generated = AsteroidField.inCell(seed, cell, belt);
        runtime.fieldCellCache.put(key, generated);
        return generated;
    }

    private enum StartNextResult { NORMAL_INTERVAL, BUDGET_DENIED }

    private static StartNextResult startNext(ServerLevel level, Runtime runtime) {
        var container = SubLevelContainer.getContainer(level);
        List<double[]> currentViewers = viewers(level);
        double activateRange = XenoSpaceConfig.asteroidActivateRange();
        long now = level.getGameTime();
        OtherLiveBodies otherBodies = runtime.capacityBodiesSnapshotTick == now
                && runtime.capacityBodiesSnapshot != null
                ? runtime.capacityBodiesSnapshot
                : otherLiveBodies(level, runtime);
        for (var candidate : runtime.wanted) {
            if (runtime.active.containsKey(candidate.id())) continue;
            if (nearestDistance(currentViewers, candidate.x(), candidate.y(), candidate.z()) > activateRange) continue;
            if (!fitsCurrentActivationCaps(runtime, candidate, currentViewers, otherBodies)) continue;
            var voxels = new AsteroidVoxels(candidate);
            int extent = voxels.extent();
            boolean blocked = false;
            for (var other : container.getAllSubLevels()) {
                var box = other.boundingBox();
                if (box.maxX() >= candidate.x() - extent && box.minX() <= candidate.x() + extent
                        && box.maxY() >= candidate.y() - extent && box.minY() <= candidate.y() + extent
                        && box.maxZ() >= candidate.z() - extent && box.minZ() <= candidate.z() + extent) { blocked = true; break; }
            }
            if (blocked) continue;
            if (!net.bullettrain.xenopixelsmod.space.physics.SpaceBuildActivationGate.tryAcquire(
                    level.getServer(), net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget.Owner.ASTEROID,
                    level.getServer().overworld().getGameTime())) return StartNextResult.BUDGET_DENIED;
            runtime.building = candidate;
            CompoundTag buildTag = new CompoundTag();
            buildTag.putLongArray("id", new long[]{candidate.id().cellX(), candidate.id().cellY(), candidate.id().cellZ(), candidate.id().slot()});
            buildTag.putBoolean("building", true);
            runtime.job = PlotBodyBuilder.start(level, new Vector3d(candidate.x(), candidate.y(), candidate.z()), extent, voxels.voxels())
                    .withoutEviction()
                    .withoutFrameFirst()
                    .onBodyAllocated(body -> {
                        if (body.getUserDataTag() == null) body.setUserDataTag(new CompoundTag());
                        body.getUserDataTag().put(TAG, buildTag.copy());
                    });
            if (XenoSpaceConfig.asteroidBuildIncrementalSections()) runtime.job.withIncrementalSections();
            runtime.wanted.remove(candidate);
            return StartNextResult.NORMAL_INTERVAL;
        }
        return StartNextResult.NORMAL_INTERVAL;
    }

    private static boolean fitsCurrentActivationCaps(Runtime runtime, AsteroidField.Candidate candidate,
                                                      List<double[]> currentViewers, OtherLiveBodies otherBodies) {
        int maxPerPlayer = XenoSpaceConfig.asteroidMaxPerPlayer();
        int maxGlobal = XenoSpaceConfig.asteroidMaxGlobal();
        if (maxPerPlayer <= 0 || maxGlobal <= 0
                || runtime.active.size() + otherBodies.count() >= maxGlobal) return false;
        double releaseRange = XenoSpaceConfig.asteroidReleaseRange();
        double releaseRangeSquared = releaseRange * releaseRange;
        double activateRange = XenoSpaceConfig.asteroidActivateRange();
        double activateRangeSquared = activateRange * activateRange;
        boolean nearAnyViewer = false;
        for (double[] viewer : currentViewers) {
            double candidateDistance = AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                    candidate.x(), candidate.y(), candidate.z());
            if (candidateDistance > activateRangeSquared) continue;
            nearAnyViewer = true;
            int used = otherBodies.unlocatedReservations();
            for (double[] body : otherBodies.positions())
                if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                        body[0], body[1], body[2]) <= releaseRangeSquared) used++;
            for (Active active : runtime.active.values())
                if (AsteroidCandidateSelector.distanceSquared(viewer[0], viewer[1], viewer[2],
                        active.candidate.x(), active.candidate.y(), active.candidate.z()) <= releaseRangeSquared) used++;
            if (used >= maxPerPlayer) return false;
        }
        return nearAnyViewer;
    }

    /** @return true when the body was released; false when persistence is saturated and it must remain managed. */
    private static boolean release(ServerLevel level, Runtime runtime, AsteroidField.ObjectId id, Active active, String why) {
        var container = SubLevelContainer.getContainer(level);
        if (!(container.getSubLevel(active.body) instanceof ServerSubLevel body) || body.isRemoved()) {
            runtime.presenceDirty = true;
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.ASTEROID, System.nanoTime());
            return true;
        }
        abortFractureBootstrap(runtime, body);
        double mass = body.getMassTracker().getMass();
        boolean touched = isTouched(body.getUserDataTag(), active.originalMass, mass);
        if (touched) {
            if (!AsteroidSavedData.get(level).detach(id, body.getUniqueId())) {
                // A modified body has to remain alive and ticketed when its stable field id cannot
                // be recorded. Removing it here would silently delete player edits and allow the
                // deterministic field to spawn a duplicate. Keeping it in runtime.active also
                // continues to consume the ordinary activation cap.
                if (!active.detachBlockedLogged) {
                    XenoPixelsMod.LOGGER.error("Asteroid {} could not be released ({}): detached-id ledger is full; retaining managed body {}",
                            id, why, body.getUniqueId());
                    active.detachBlockedLogged = true;
                }
                return false;
            }
            runtime.presenceDirty = true;
            container.removeForceLoadTicket(body, TICKET, id.toString());
            if (body.getUserDataTag() != null) body.getUserDataTag().remove(TAG);
            runtime.detached++;
            net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                    net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.ASTEROID, System.nanoTime());
            XenoPixelsMod.LOGGER.info("Asteroid {} detached ({}): mass {} -> {}, Sable keeps {}", id, why, active.originalMass, mass, body.getUniqueId());
            return true;
        }
        runtime.presenceDirty = true;
        container.removeForceLoadTicket(body, TICKET, id.toString());
        container.removeSubLevel(body, SubLevelRemovalReason.REMOVED);
        runtime.removed++;
        net.bullettrain.xenopixelsmod.space.SpaceChartService.state(level.getServer()).runtime.activationMetrics.recordRelease(
                net.bullettrain.xenopixelsmod.space.SpaceActivationMetrics.Owner.ASTEROID, System.nanoTime());
        XenoPixelsMod.LOGGER.info("Asteroid {} released ({})", id, why);
        return true;
    }

    private static void abortFractureBootstrap(Runtime runtime, ServerSubLevel body) {
        for (var iterator = runtime.heatMapBootstraps.iterator(); iterator.hasNext();) {
            var bootstrap = iterator.next();
            if (bootstrap.body != body) continue;
            if (body.getHeatMapManager() instanceof SableHeatMapFractureAccess access)
                access.xenopixels$abortFractureHeatMapRebuild();
            if (body.getUserDataTag() != null && body.getUserDataTag().contains(TAG)) {
                body.getUserDataTag().getCompound(TAG).remove("fractureHeatMapBootstrapping");
                body.getUserDataTag().getCompound(TAG).remove("fractureHeatMapAttempted");
            }
            iterator.remove();
            return;
        }
    }

    @SubscribeEvent public static void stopping(ServerStoppingEvent event) {
        Runtime runtime = RUNTIMES.get(event.getServer());
        ServerLevel level = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (runtime == null || level == null) return;
        if (runtime.job != null) abandonBuild(SubLevelContainer.getContainer(level), runtime);
        for (var bootstrap : new ArrayList<>(runtime.heatMapBootstraps))
            abortFractureBootstrap(runtime, bootstrap.body);
        while (!runtime.fractureCuts.isEmpty()) {
            var cut = runtime.fractureCuts.removeFirst().payload();
            clearFractureCutMarker(cut.body);
        }
        for (var entry : runtime.active.entrySet()) release(level, runtime, entry.getKey(), entry.getValue(), "server stopping");
        runtime.active.clear();
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        RUNTIMES.remove(event.getServer());
        NEXT_FRAGMENT_RUNTIME_PROBE.remove(event.getServer());
        DEBUG_VIEWERS.clear();
    }

    /** One line for logs and commands. */
    public static String status(MinecraftServer server) {
        Runtime r = RUNTIMES.get(server);
        int liveFragments = 0, maxFragmentsForRoot = 0;
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, PhysicalEarth.HOST));
        if (level != null) {
            var perRoot = new HashMap<AsteroidField.ObjectId, Integer>();
            for (SubLevel sub : SubLevelContainer.getContainer(level).getAllSubLevels()) {
                if (!(sub instanceof ServerSubLevel body) || body.isRemoved() || !isAsteroidFragment(body)) continue;
                AsteroidField.ObjectId rootId = fragmentId(body.getUserDataTag().getCompound(FRAGMENT_TAG));
                if (rootId == null) continue;
                liveFragments++;
                maxFragmentsForRoot = Math.max(maxFragmentsForRoot, perRoot.merge(rootId, 1, Integer::sum));
            }
        }
        OtherLiveBodies otherBodies = level == null ? new OtherLiveBodies(List.of(), 0) : otherLiveBodies(level, r);
        int managed = r == null ? 0 : r.active.size();
        int activeVoxels = 0, unknownVoxelBodies = r == null ? 0 : r.active.size();
        if (level != null && r != null) for (Active active : r.active.values()) {
            if (SubLevelContainer.getContainer(level).getSubLevel(active.body) instanceof ServerSubLevel body
                    && body.getUserDataTag() != null && body.getUserDataTag().contains(TAG)
                    && body.getUserDataTag().getCompound(TAG).contains("voxels")) {
                activeVoxels += Math.max(0, body.getUserDataTag().getCompound(TAG).getInt("voxels"));
                unknownVoxelBodies--;
            }
        }
        long liveBodies = (long) managed + otherBodies.count();
        if (r == null) return "asteroids: inactive liveBodies=" + liveBodies + "/" + XenoSpaceConfig.asteroidMaxGlobal()
                + " retainedLoaded=" + otherBodies.positions().size()
                + " legacyUnlocatedReservations=" + otherBodies.unlocatedReservations()
                + " liveFragments=" + liveFragments + "/" + XenoSpaceConfig.asteroidMaxFragmentsGlobal();
        return "asteroids: active=" + r.active.size() + " activeVoxels=" + activeVoxels
                + " unknownVoxelBodies=" + unknownVoxelBodies + " wanted=" + r.wanted.size() + " building=" + (r.building == null ? "-" : r.building.id())
                + " liveBodies=" + liveBodies + "/" + XenoSpaceConfig.asteroidMaxGlobal()
                + " retainedLoaded=" + otherBodies.positions().size()
                + " legacyUnlocatedReservations=" + otherBodies.unlocatedReservations()
                + " liveFragments=" + liveFragments + "/" + XenoSpaceConfig.asteroidMaxFragmentsGlobal()
                + " maxFragmentsPerRoot=" + maxFragmentsForRoot + "/" + XenoSpaceConfig.asteroidMaxFragmentsPerAsteroid()
                + " built=" + r.built + " removed=" + r.removed + " detached=" + r.detached
                + " fracture=" + (XenoSpaceConfig.asteroidFracture() ? "on" : "off")
                + " heatMapPending=" + r.heatMapBootstraps.size() + " cutsPending=" + r.fractureCuts.size()
                + " fractureCutsReserved=" + r.fractureCuts.size()
                + " fragmentScans=" + r.fragmentExpiryScans.size()
                + " fragmentScanPositionsLast/Max=" + r.fragmentPositionsScannedLast + "/" + r.fragmentPositionsScannedMax
                + " fragmentsExpired=" + r.fragmentsExpired
                + " fractureWorkLastUs=" + r.fractureWorkLastNanos / 1_000.0
                 + " fractureWorkMaxUs=" + r.fractureWorkMaxNanos / 1_000.0
                 + " fieldCellCache=" + r.fieldCellCache.size() + "/" + MAX_CACHED_FIELD_CELLS
                 + " fieldCellCacheHit/Miss=" + r.fieldCellCacheHits + "/" + r.fieldCellCacheMisses
                 + " scanCellsLast/Budget=" + r.scanCellsLast + "/" + XenoSpaceConfig.asteroidScanCellsPerPass()
                 + " scanWorkLast/MaxUs=" + r.scanWorkLastNanos / 1_000.0 + "/" + r.scanWorkMaxNanos / 1_000.0
                 + " scanCursors=" + r.scanCursors.size() + "/" + MAX_SCAN_CURSORS;
    }

    /** Sends the current full presence set to a player entering the Voidsea; never sends a stale delta. */
    public static void syncPresence(ServerPlayer player) {
        var server = player.getServer();
        Runtime runtime = server == null ? null : RUNTIMES.get(server);
        AddonNetwork.sendToPlayer(player, runtime == null ? new AsteroidPresence(List.of()) : presence(runtime));
    }

    private static AsteroidPresence presence(Runtime runtime) {
        var entries = new ArrayList<AsteroidPresence.Entry>();
        for (var entry : runtime.active.entrySet()) {
            if (entries.size() >= AsteroidPresence.MAX_ENTRIES) break;
            entries.add(new AsteroidPresence.Entry(entry.getKey(), entry.getValue().body));
        }
        return new AsteroidPresence(entries);
    }

    /** True unless the cell's box is entirely outside the belt's slab or annulus (16 blocks slack for radius). */
    static boolean cellMayTouch(AsteroidField.Belt belt, long cx, long cy, long cz) {
        double size = AsteroidField.CELL_SIZE, slack = AsteroidField.MAX_RADIUS;
        double x0 = cx * size, y0 = cy * size, z0 = cz * size;
        if (y0 > belt.centerY() + belt.halfThickness() + slack || y0 + size < belt.centerY() - belt.halfThickness() - slack) return false;
        double nx = Math.max(x0, Math.min(belt.centerX(), x0 + size)) - belt.centerX();
        double nz = Math.max(z0, Math.min(belt.centerZ(), z0 + size)) - belt.centerZ();
        if (Math.hypot(nx, nz) > belt.outerRadius() + slack) return false;
        double fx = Math.max(Math.abs(x0 - belt.centerX()), Math.abs(x0 + size - belt.centerX()));
        double fz = Math.max(Math.abs(z0 - belt.centerZ()), Math.abs(z0 + size - belt.centerZ()));
        return Math.hypot(fx, fz) >= belt.innerRadius() - slack;
    }

    private static AsteroidField.Candidate candidateFor(ServerLevel level, long seed, AsteroidField.ObjectId id) {
        for (var belt : AsteroidFieldSync.belts(SpaceChartService.state(level.getServer())))
            for (var candidate : AsteroidField.inCell(seed, new AsteroidField.Cell(id.cellX(), id.cellY(), id.cellZ()), belt))
                if (candidate.id().equals(id)) return candidate;
        return null;
    }

    private static AsteroidField.ObjectId idOf(CompoundTag tag) {
        long[] id = tag.getLongArray("id");
        if (id.length != 4 || id[3] < 0 || id[3] >= AsteroidField.CANDIDATES_PER_CELL) return null;
        return new AsteroidField.ObjectId(id[0], id[1], id[2], (int) id[3]);
    }

    private static double nearestDistance(List<double[]> players, double x, double y, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (double[] player : players) best = Math.min(best, distance(player[0], player[1], player[2], x, y, z));
        return best;
    }

    private static double distance(double ax, double ay, double az, double bx, double by, double bz) {
        double dx = ax - bx, dy = ay - by, dz = az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
