package net.bullettrain.xenopixelsmod.space.physics;

import com.mojang.serialization.Codec;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.SubLevelPlayerChunkSender;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import dev.ryanhcode.sable.platform.SableChunkEventPlatform;
import dev.ryanhcode.sable.platform.SablePlotPlatform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CancellationException;

/**
 * Builds a Sable body without touching the world: blocks are written into in-memory chunk sections and installed
 * with {@code ServerLevelPlot.load}, which also owns mass and physics setup. Compared with placing every voxel in
 * the world and assembling it, this needs no staging volume, so it is safe with players nearby.
 *
 * <p>The plot tag layout was read from Sable 2.0.3's {@code ServerLevelPlot.load} bytecode: {@code log_size},
 * {@code data_version}, {@code biome}, and {@code chunks} keyed by plot-local {@code ChunkPos.asLong}, each with
 * {@code sections} keyed by level section index holding a {@code block_states} palette. Sable accepts each chunk
 * key once; a second call containing an existing key throws. Distinct complete columns can be installed in separate
 * calls, each with per-block mass updates and a merged-mass refresh. The builder coalesces Sable's per-holder bounds
 * recomputations because {@code load} itself recomputes complete plot bounds before returning. The approach follows
 * the dmzcompanion asteroid builder the owner supplied.
 */
public final class PlotBodyBuilder {
    private static final Codec<PalettedContainer<BlockState>> BLOCK_STATES = PalettedContainer.codecRW(
            Block.BLOCK_STATE_REGISTRY, BlockState.CODEC, PalettedContainer.Strategy.SECTION_STATES,
            Blocks.AIR.defaultBlockState());
    static final double UNBOUNDED_MILLIS_BUDGET = Double.MAX_VALUE;
    private static final int OFFSET = 512, MASK = 1023;
    private static final ThreadLocal<Integer> DEFERRED_CHUNK_BOUNDS = ThreadLocal.withInitial(() -> 0);

    /** True only while a body builder is creating a chunk whose enclosing batch updates plot bounds. */
    public static boolean deferChunkHolderBounds() { return DEFERRED_CHUNK_BOUNDS.get() > 0; }

    /** Runs a chunk-creation operation with its per-holder bounds update deferred to the enclosing batch. */
    public static void withChunkHolderBoundsDeferred(Runnable operation) {
        if (!net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildCoalesceChunkBounds()) {
            operation.run();
            return;
        }
        int depth = DEFERRED_CHUNK_BOUNDS.get();
        DEFERRED_CHUNK_BOUNDS.set(depth + 1);
        try {
            operation.run();
        } finally {
            if (depth == 0) DEFERRED_CHUNK_BOUNDS.remove();
            else DEFERRED_CHUNK_BOUNDS.set(depth);
        }
    }

    /** A voxel source in body-local block offsets from the centre voxel; must be pure (it runs off-thread). */
    @FunctionalInterface public interface Voxels { BlockState at(int x, int y, int z); }

    public record Result(ServerSubLevel body, int blocks, double fillMillis, double installMillis) {}

    private record Allocation(ServerSubLevel body, int slotX, int slotZ) {}

    record VoxelRange(int fromInclusive, int toExclusive) {
        int size() { return toExclusive - fromInclusive; }
    }

    static VoxelRange nextVoxelRange(int cursor, int total, int budget) {
        if (total < 0 || cursor < 0 || cursor > total || budget <= 0)
            throw new IllegalArgumentException("invalid voxel cursor/range/budget");
        int end = (int) Math.min((long) total, (long) cursor + budget);
        return new VoxelRange(cursor, end);
    }

    /**
     * Whether an atomic plot build should use the server-tick job path. The local voxel budget is the
     * existing operator switch: zero preserves the synchronous compatibility path, while a positive
     * value applies to permanent anchors as well as proximity-activated bodies.
     */
    static boolean shouldBatchAssembly(boolean atomic, int voxelBudgetPerTick) {
        return atomic && voxelBudgetPerTick > 0;
    }

    static boolean shouldDeferFinalize(boolean enabled, int installedVoxels, boolean hasMoreColumns) {
        return enabled && installedVoxels > 0 && hasMoreColumns;
    }

    private PlotBodyBuilder() {}

    /** A hollow {@link PlanetShell} of {@code state}, the same voxels the block-placing path assembles. */
    public static Voxels shell(int radius, BlockState state) {
        return (x, y, z) -> PlanetShell.contains(x, y, z, radius) ? state : null;
    }

    /** Synchronous build: the whole body in one tick. Returns null when no plot slot is free. */
    public static Result build(ServerLevel level, Vector3dc centre, int extent, Voxels voxels) {
        Job job = start(level, centre, extent, voxels);
        while (!job.step(Integer.MAX_VALUE)) {
            if (job.failed()) return null;
            job.voxels.join();
        }
        return job.result();
    }

    /**
     * Starts a batched build. The voxel test runs on a background thread; {@link Job#step} does the rest on the
     * server thread and never mutates Sable state off-thread.
     */
    public static Job start(ServerLevel level, Vector3dc centre, int extent, Voxels voxels) {
        return start(level, centre, extent, voxels, false);
    }

    /** Chart planet builds may defer repeated Sable finalization; other callers retain the standard behavior. */
    public static Job start(ServerLevel level, Vector3dc centre, int extent, Voxels voxels,
                            boolean deferFinalizeEnabled) {
        if (extent < 1 || extent >= OFFSET) throw new IllegalArgumentException("extent outside 1.." + (OFFSET - 1));
        var fillCancelled = new java.util.concurrent.atomic.AtomicBoolean();
        CompletableFuture<Filled> fillFuture = CompletableFuture.supplyAsync(
                () -> fill(extent, voxels, fillCancelled::get));
        return new Job(level, new Vector3d(centre), extent, fillFuture, deferFinalizeEnabled, fillCancelled);
    }

    /** Packed voxel list: {@code (x+512) | (y+512)<<10 | (z+512)<<20}, with each voxel's state alongside. */
    record Filled(int[] packed, BlockState[] states, double millis, List<int[]> grouped) {}

    /**
     * Plot centre block offset inside its chunk, as observed on Sable 2.0.3 (log "centre local x&15=8 z&15=8"). The column
     * grouping depends only on this offset, so it is computed off-thread for it; a different offset falls back to
     * grouping on the server thread.
     */
    static final int ASSUMED_CENTRE_OFFSET = 8;

    static Filled fill(int extent, Voxels voxels) {
        return fill(extent, voxels, () -> false);
    }

    static Filled fill(int extent, Voxels voxels, java.util.function.BooleanSupplier cancelled) {
        long started = System.nanoTime();
        int[] packed = new int[1024];
        BlockState[] states = new BlockState[1024];
        int count = 0;
        for (int x = -extent; x <= extent; x++)
            for (int y = -extent; y <= extent; y++) {
                // Poll between rows: an abandoned large body stops after at most one X/Z row of voxel calls.
                if (cancelled.getAsBoolean()) throw new CancellationException("generated-body voxel fill cancelled");
                for (int z = -extent; z <= extent; z++) {
                    BlockState state = voxels.at(x, y, z);
                    if (state == null || state.isAir()) continue;
                    if (count == packed.length) {
                        packed = java.util.Arrays.copyOf(packed, count * 2);
                        states = java.util.Arrays.copyOf(states, count * 2);
                    }
                    packed[count] = (x + OFFSET) | (y + OFFSET) << 10 | (z + OFFSET) << 20;
                    states[count++] = state;
                }
            }
        int[] finalPacked = java.util.Arrays.copyOf(packed, count);
        return new Filled(finalPacked, java.util.Arrays.copyOf(states, count),
                (System.nanoTime() - started) / 1e6, group(finalPacked, ASSUMED_CENTRE_OFFSET, ASSUMED_CENTRE_OFFSET));
    }
    static int unpackX(int p) { return (p & MASK) - OFFSET; }
    static int unpackY(int p) { return (p >> 10 & MASK) - OFFSET; }
    static int unpackZ(int p) { return (p >> 20 & MASK) - OFFSET; }

    /**
     * One collision-section registration above this many milliseconds earns its own line. Matches the whole-step
     * threshold in {@link Job#stepIncrementalSections}, and sits far above the ~0.55 ms mean a section costs.
     */
    private static final double SLOW_COLLISION_SECTION_MILLIS = 10.0;
    private static final java.util.List<java.lang.management.GarbageCollectorMXBean> GC_BEANS =
            java.lang.management.ManagementFactory.getGarbageCollectorMXBeans();
    private static final int GC_BEAN_COUNT = GC_BEANS.size();

    /**
     * Cumulative GC milliseconds and collections. Read only around a section that is already slow:
     * {@code getCollectionTime()} advances when a collection completes, so a pause still in flight at the read
     * is not attributed, and the delta spans since the previous slow sample rather than only this one section.
     */
    private static long gcMillis() {
        long total = 0L;
        for (int i = 0; i < GC_BEAN_COUNT; i++) total += GC_BEANS.get(i).getCollectionTime();
        return total;
    }

    private static long gcCollections() {
        long total = 0L;
        for (int i = 0; i < GC_BEAN_COUNT; i++) total += GC_BEANS.get(i).getCollectionCount();
        return total;
    }

    /** One body being built. Not thread-safe: step it from the server thread only. */
    public static final class Job {
        private final ServerLevel level;
        private final Vector3d centre;
        private final boolean deferFinalizeEnabled;
        private CompletableFuture<Filled> voxels;
        private final java.util.concurrent.atomic.AtomicBoolean fillCancelled;
        private ServerSubLevel body;
        private int allocationSlotX = -1;
        private int allocationSlotZ = -1;
        private BlockPos allocatedPlotCentre;
        private List<int[]> columns;
        private int nextColumn, installed, total, frameColumns;
        private int centreLocalX, centreLocalZ, centreY, logPlotSize;
        private final Map<Long, ChunkPos> hostChunkCache = new HashMap<>();
        private final Map<Long, LevelChunk> incrementalChunks = new LinkedHashMap<>();
        private final Map<Long, ChunkPos> incrementalGlobalChunks = new LinkedHashMap<>();
        private final java.util.Set<IncrementalSectionKey> incrementalAirCheckedSections = new java.util.HashSet<>();
        private Consumer<ServerSubLevel> onBodyAllocated = ignored -> {};
        private Consumer<ServerSubLevel> onCancellation = ignored -> {};
        private boolean incrementalSections, initialStatsUploaded;
        private boolean frameFirstDisabled;
        private int incrementalCursor;
        private int incrementalFinishPhase, incrementalFinishChunkCursor, incrementalFinishSectionCursor,
                incrementalFinishNotifyCursor;
        private java.util.Iterator<Map.Entry<Long, LevelChunk>> incrementalFinishChunkIterator;
        private List<IncrementalFinishChunk> incrementalFinishChunks = List.of();
        private List<IncrementalFinishSection> incrementalFinishSections = List.of();
        private long incrementalFinishChunkNanos, incrementalFinishSectionNanos;
        private long incrementalFinishBoundsNanos, incrementalFinishMassNanos;
        private long incrementalFinishStatsNanos, incrementalFinishIndexNanos, incrementalFinishPoseNanos;
        /** Slowest single collision-section registration and ticket call of this job, each with its section and tick. */
        private double slowestCollisionSectionMillis;
        private SectionPos slowestCollisionSection;
        private long slowestCollisionSectionTick;
        private double slowestTicketSectionMillis;
        private SectionPos slowestTicketSection;
        private long slowestTicketSectionTick;
        private long gcMillisSample, gcCollectionsSample;
        private double installMillis, fillMillis, maxServerSliceMillis;
        private int batches;
        private boolean failed, done;
        private boolean evicts = true;

        /** For bodies that are not hollow planet shells (asteroids): never move other bodies before completion. */
        public Job withoutEviction() { evicts = false; return this; }

        /** Keep asynchronous work batches bounded to the regular voxel budget, without a multi-column first frame. */
        public Job withoutFrameFirst() { frameFirstDisabled = true; return this; }

        /**
         * Select Sable's exact voxel-count writer. It may be selected after preparation only while no voxel has been
         * installed, allowing budget enforcement to fall back when one plot.load group is too large.
         */
        public Job withIncrementalSections() {
            if (!incrementalSections && installed > 0)
                throw new IllegalStateException("cannot change the plot writer after block installation has started");
            incrementalSections = true;
            columns = null;
            frameColumns = 0;
            return this;
        }

        /** Attach stable build identity immediately after allocation, before any chunk or block is installed. */
        public Job onBodyAllocated(Consumer<ServerSubLevel> callback) {
            if (body != null) throw new IllegalStateException("body already allocated");
            onBodyAllocated = java.util.Objects.requireNonNull(callback);
            return this;
        }

        /** Runs before a failed/cancelled build removes its allocated body; owners may release build-scoped tickets. */
        public Job onCancellation(Consumer<ServerSubLevel> callback) {
            onCancellation = java.util.Objects.requireNonNull(callback);
            return this;
        }

        /** Server-thread milliseconds of the slowest preparation or install slice, and the batch count. */
        public double maxServerSliceMillis() { return maxServerSliceMillis; }
        public int batches() { return batches; }

        private final int extent;

        private Job(ServerLevel level, Vector3d centre, int extent, CompletableFuture<Filled> voxels,
                    boolean deferFinalizeEnabled, java.util.concurrent.atomic.AtomicBoolean fillCancelled) {
            this.level = level; this.centre = centre; this.extent = extent; this.voxels = voxels;
            this.deferFinalizeEnabled = deferFinalizeEnabled;
            this.fillCancelled = fillCancelled;
        }

        public ServerSubLevel body() { return body; }
        public boolean fillReady() { return voxels.isDone(); }
        boolean deferFinalizeEnabled() { return deferFinalizeEnabled; }
        public boolean failed() { return failed; }
        public boolean done() { return done; }
        public int installed() { return installed; }
        public int total() { return total; }
        /**
         * Largest indivisible {@code plot.load} unit after preparation. Sable 2.0.3 rejects loading an
         * already-loaded chunk key, so regular batches cannot split an X/Z chunk column across calls.
         * Returns zero until the server-thread preparation has allocated the plot and selected its exact grouping.
         */
        public int maxColumnVoxels() {
            if (incrementalSections) return 1;
            return maxGroupedVoxels(columns);
        }
        public boolean incrementalSections() { return incrementalSections; }
        public Result result() { return done ? new Result(body, installed, fillMillis, installMillis) : null; }

        /** Allocate and group without installing blocks so the shared budget can reserve the true next-call bound. */
        public boolean prepare() {
            if (done || failed) return done;
            if (!voxels.isDone()) return false;
            Filled filled;
            try { filled = voxels.join(); }
            catch (RuntimeException error) { failed = true; throw error; }
            if (body != null) return true;
            long preparationStart = System.nanoTime();
            fillMillis = filled.millis();
            if (filled.packed().length == 0) { failed = true; throw new IllegalStateException("empty body"); }
            long started = System.nanoTime();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            long allocateStart = System.nanoTime();
            boolean traceAllocation = net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.sableAllocationPhaseProbe();
            SableAllocationTiming.Sample allocationSample = traceAllocation ? SableAllocationTiming.begin() : null;
            try {
                Allocation allocation = allocate(container,
                        new Pose3d(new Vector3d(centre), new Quaterniond(), new Vector3d(), new Vector3d(1, 1, 1)));
                body = allocation == null ? null : allocation.body();
                if (allocation != null) {
                    allocationSlotX = allocation.slotX();
                    allocationSlotZ = allocation.slotZ();
                }
            } finally {
                if (allocationSample != null) SableAllocationTiming.end(allocationSample);
            }
            double allocateMillis = (System.nanoTime() - allocateStart) / 1e6;
            if (body == null) { failed = true; return false; }
            try {
            if (allocationSample == null) net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                    "Incremental plot allocation: {} ms", String.format("%.2f", allocateMillis));
            else {
                double unclassifiedMillis = allocateMillis - allocationSample.subLevelCreateMillis()
                        - allocationSample.massTrackerMillis() - allocationSample.pipelineAddMillis();
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                        "Incremental plot allocation: total={} ms createSubLevel={} ms plotConstructorInclusive={} ms observerMass={} ms observerPipelineAdd={} ms other={} ms",
                        String.format("%.2f", allocateMillis), String.format("%.2f", allocationSample.subLevelCreateMillis()),
                        String.format("%.2f", allocationSample.plotConstructionMillis()),
                        String.format("%.2f", allocationSample.massTrackerMillis()),
                        String.format("%.2f", allocationSample.pipelineAddMillis()), String.format("%.2f", unclassifiedMillis));
            }
            onBodyAllocated.accept(body);
            var plot = body.getPlot();
            var centreBlock = plot.getCenterBlock();
            allocatedPlotCentre = centreBlock.immutable();
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                    "Incremental plot allocated: bodyUuid={} slot=({}, {}) requestedCentre={} plotCentre={}",
                    body.getUniqueId(), allocationSlotX, allocationSlotZ, centre, centreBlock);
            logPlotSize = container.getLogPlotSize();
            var centreChunk = plot.toLocal(new ChunkPos(centreBlock));
            centreLocalX = centreChunk.x * 16 + (centreBlock.getX() & 15);
            centreLocalZ = centreChunk.z * 16 + (centreBlock.getZ() & 15);
            centreY = centreBlock.getY();
            if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildPoseFix())
                body.logicalPose().rotationPoint().set(centreBlock.getX() + 0.5, centreBlock.getY() + 0.5, centreBlock.getZ() + 0.5);
            total = filled.packed().length;
            // Time slicing requires the exact voxel writer; plot.load is indivisible at chunk-column granularity.
            if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.generatedBodyBuildMillisPerTick() > 0.0)
                withIncrementalSections();
            if (!incrementalSections) {
                long groupStart = System.nanoTime();
                boolean preGrouped = (centreLocalX & 15) == ASSUMED_CENTRE_OFFSET && (centreLocalZ & 15) == ASSUMED_CENTRE_OFFSET;
                columns = preGrouped ? new ArrayList<>(filled.grouped()) : group(filled.packed(), centreLocalX, centreLocalZ);
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                        "Batched build prepared: allocate {} ms, group {} ms, max atomic voxels={}, centre local x&15={} z&15={}",
                        String.format("%.2f", allocateMillis), String.format("%.2f", (System.nanoTime() - groupStart) / 1e6),
                        maxColumnVoxels(), centreLocalX & 15, centreLocalZ & 15);
                if (!frameFirstDisabled && net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildFrameFirst())
                    frameColumns = frameFirst(columns);
                else if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildDeferFinalize()) smallestFirst(columns);
            }
            double preparationMillis = (System.nanoTime() - preparationStart) / 1e6;
            installMillis += preparationMillis;
            maxServerSliceMillis = Math.max(maxServerSliceMillis, preparationMillis);
            // A periodic body census must not treat this allocated, not-yet-tagged Sable body as unreadable.
            // Preparation runs on the server thread, so registering here cannot expose a half-prepared job.
            PlotBodyBuildRegistry.register(body.getUniqueId(), level);
            return true;
            } catch (RuntimeException | Error failure) {
                cancel(failure);
                throw failure;
            }
        }

        /** Number of deterministic solid voxels from the completed fill, for budgeted post-build passes. */
        public int sourceVoxelCount() {
            if (!done) throw new IllegalStateException("body build is not complete");
            return voxels.join().packed().length;
        }

        /** Host-level position corresponding to one solid voxel in the completed plot-local fill. */
        public BlockPos hostBlockAt(int index) {
            if (!done || body == null) throw new IllegalStateException("body build is not complete");
            Filled filled = voxels.join();
            int packed = filled.packed()[index];
            int localX = centreLocalX + unpackX(packed);
            int localY = centreY + unpackY(packed);
            int localZ = centreLocalZ + unpackZ(packed);
            int localChunkX = Math.floorDiv(localX, 16), localChunkZ = Math.floorDiv(localZ, 16);
            long localKey = ChunkPos.asLong(localChunkX, localChunkZ);
            ChunkPos globalChunk = hostChunkCache.computeIfAbsent(localKey,
                    ignored -> body.getPlot().toGlobal(new ChunkPos(localChunkX, localChunkZ)));
            return new BlockPos(globalChunk.getMinBlockX() + Math.floorMod(localX, 16),
                    localY, globalChunk.getMinBlockZ() + Math.floorMod(localZ, 16));
        }

        /**
         * Installs whole chunk columns, nearest the centre first, until at least {@code voxelBudget} voxels went in
         * this call (one column minimum, so it always progresses). The first call allocates the plot and installs
         * in the same tick, so the body is never massless. Returns true when the body is complete. This legacy
         * overload has no shared wall-time allocator; managed build owners must use the two-argument overload.
         */
        public boolean step(int voxelBudget) {
            // Zero means "no time budget" for this legacy entry point. The managed overload uses zero to mean
            // "the shared allowance is exhausted", so pass an explicit unbounded sentinel instead; otherwise an
            // incremental writer selected by a positive global cap can install all voxels and defer finish forever.
            return step(voxelBudget, UNBOUNDED_MILLIS_BUDGET);
        }

        /**
         * Install one build slice. A positive milliseconds allowance applies only to the incremental writer and
         * is checked between voxel callbacks; allocation, chunk creation, and each Sable callback remain indivisible.
         */
        public boolean step(int voxelBudget, double millisBudget) {
            if (done || failed) return done;
            if (body != null && body.isRemoved()) {
                failed = true;
                try {
                    onCancellation.accept(body);
                } catch (RuntimeException cleanupFailure) {
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.error(
                            "Could not release build-owned ticket for already-removed Sable body {}",
                            body.getUniqueId(), cleanupFailure);
                }
                PlotBodyBuildRegistry.unregister(body.getUniqueId(), level);
                throw removedDuringBuild("before build slice", null, incrementalCursor);
            }
            if (!prepare()) return false;
            Filled filled = voxels.join();
            if (!incrementalSections && voxelBudget > 0
                    && !atomicBatchesFit(columns, nextColumn, frameColumns, voxelBudget)) {
                if (installed == 0) {
                    // Some direct callers use Job.step(localLimit) without the shared allocator. Make the
                    // positive per-call voxel limit exact there too; otherwise one grouped plot.load call (or
                    // the frame-first prefix) can exceed it by an entire chunk column.
                    withIncrementalSections();
                } else {
                    // A live config/budget reduction cannot safely change writers after plot.load has installed
                    // chunks: the exact writer would try to create those chunk keys again. Fail closed instead
                    // of silently breaking the newly supplied cap.
                    cancel();
                    throw new IllegalStateException("voxel budget fell below remaining atomic plot batches after installation started");
                }
            }
            long started = System.nanoTime();
            ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
            try {
                if (incrementalSections) return stepIncrementalSections(filled, container, started, voxelBudget, millisBudget);
                Map<Long, Map<Integer, PalettedContainer<BlockState>>> chunks = new HashMap<>();
                int batch = 0;
                while (nextColumn < columns.size() && (batch == 0 || batch < voxelBudget || nextColumn < frameColumns)) {
                    int[] column = columns.get(nextColumn++);
                    for (int i = 1; i < column.length; i++) {
                        int index = column[i], p = filled.packed()[index];
                        int px = centreLocalX + unpackX(p), py = centreY + unpackY(p), pz = centreLocalZ + unpackZ(p);
                        int section = level.getSectionIndex(py);
                        if (section < 0 || section >= level.getSectionsCount())
                            throw new IllegalStateException("body does not fit the level's section range at y=" + py);
                        chunks.computeIfAbsent(ChunkPos.asLong(Math.floorDiv(px, 16), Math.floorDiv(pz, 16)), k -> new HashMap<>())
                                .computeIfAbsent(section, k -> new PalettedContainer<>(Block.BLOCK_STATE_REGISTRY,
                                        Blocks.AIR.defaultBlockState(), PalettedContainer.Strategy.SECTION_STATES))
                                .getAndSetUnchecked(Math.floorMod(px, 16), Math.floorMod(py, 16), Math.floorMod(pz, 16),
                                        filled.states()[index]);
                        batch++;
                    }
                }
                // A body that entered the shell band while the planet was building gets no contact until completion
                // and is then embedded in the shell (S11 build-contact arms CL-058/059): move it out first.
                if (evicts && nextColumn >= columns.size() && net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildEvictIntruders())
                    evictIntruders(container);
                // Defer Sable's per-load finalize on every batch but the first (the body needs mass at once) and the last.
                boolean defer = shouldDeferFinalize(deferFinalizeEnabled, installed, nextColumn < columns.size());
                DEFERRING.set(defer);
                long loadStart = System.nanoTime();
                try {
                    withChunkHolderBoundsDeferred(() -> body.getPlot().load(tag(chunks, logPlotSize)));
                }
                finally { DEFERRING.set(false); }
                installed += batch;
                if (nextColumn >= columns.size()) {
                    body.getPlot().updateBoundingBox();
                    body.updateBoundingBox();
                    snapshot("post-load", body, centre, total, deferFinalizeEnabled);
                    if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildFinalize()) {
                        finalizeLikeReference(body);
                        snapshot("E5 after finalize", body, centre, total, deferFinalizeEnabled);
                    }
                    // Whatever the centre of mass did while the shell grew, the finished body sits exactly where
                    // it was asked to be, at rest.
                    var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
                    if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildPoseFix() && handle.isValid()) {
                        handle.teleport(centre, new Quaterniond());
                        handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                                handle.getAngularVelocity(new Vector3d()).negate());
                    }
                    done = true;
                    PlotBodyBuildRegistry.unregister(body.getUniqueId(), level);
                }
                double millis = (System.nanoTime() - started) / 1e6;
                // S11 20 ms survey: per-batch cost against body size (is the per-batch native mass upload size-bound?).
                if (millis > 10 || done)
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("Batched build batch {} load part {} ms", batches,
                            String.format("%.2f", (System.nanoTime() - loadStart) / 1e6));
                if (millis > 10 || done)
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info("Batched build batch {}: {} voxels this batch, {} installed, {} ms",
                            batches, batch, installed, String.format("%.2f", millis));
                installMillis += millis;
                maxServerSliceMillis = Math.max(maxServerSliceMillis, millis);
                batches++;
                return done;
            } catch (RuntimeException | Error failure) {
                cancel(failure);
                throw failure;
            }
        }

        private boolean stepIncrementalSections(Filled filled, ServerSubLevelContainer container, long started,
                                                int voxelBudget, double millisBudget) {
            int from = incrementalCursor;
            if (from >= filled.packed().length) {
                if (shouldDeferFinish(net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.generatedBodyBuildMillisPerTick(),
                        millisBudget, 0.0, 0)) return false;
                long finishStart = System.nanoTime();
                done = stepFinishIncrementalSections(container, container.physicsSystem());
                double millis = (System.nanoTime() - finishStart) / 1e6;
                installMillis += millis;
                maxServerSliceMillis = Math.max(maxServerSliceMillis, millis);
                batches++;
                return done;
            }
            if (voxelBudget <= 0) return false;
            int budget = voxelBudget;
            int to = nextVoxelRange(from, filled.packed().length, budget).toExclusive();
            var plot = body.getPlot();
            var access = plot instanceof SableIncrementalPlotAccess sableAccess ? sableAccess : null;
            if (access == null) throw new IllegalStateException("Sable incremental plot mixin is not applied");
            SubLevelPhysicsSystem physics = container.physicsSystem();
            long writeStart = System.nanoTime();
            long preReadNanos = 0, sectionWriteNanos = 0, holderChangeNanos = 0, heatMapNanos = 0, floatingNanos = 0,
                    massChangeNanos = 0, plotChangeNanos = 0, chunkCreateNanos = 0;
            int chunksCreatedThisBatch = 0;
            int index = from;
            for (; index < to; index++) {
                // Always finish the first voxel so a nonzero allowance makes progress. The callback sequence itself
                // cannot be interrupted safely; measured runtimes must account for this indivisible overrun.
                if (shouldStopTimeSlicedStep(millisBudget, (System.nanoTime() - writeStart) / 1e6, index - from)) break;
                int packed = filled.packed()[index];
                int px = centreLocalX + unpackX(packed), py = centreY + unpackY(packed), pz = centreLocalZ + unpackZ(packed);
                int sectionIndex = level.getSectionIndex(py);
                if (sectionIndex < 0 || sectionIndex >= level.getSectionsCount())
                    throw new IllegalStateException("body does not fit the level's section range at y=" + py);
                int chunkX = Math.floorDiv(px, 16), chunkZ = Math.floorDiv(pz, 16);
                long key = ChunkPos.asLong(chunkX, chunkZ);
                ChunkPos localChunk = new ChunkPos(chunkX, chunkZ);
                LevelChunk chunk = incrementalChunks.get(key);
                ChunkPos globalChunk = incrementalGlobalChunks.get(key);
                boolean createdChunkHere = false;
                boolean holderBeforeCreate = false;
                boolean holderImmediatelyAfterCreate = false;
                if (chunk == null) {
                    globalChunk = plot.toGlobal(localChunk);
                    chunk = plot.getChunk(localChunk);
                    // Sable may already hold an empty chunk for this plot-local position (for example,
                    // after plot lifecycle work has materialized its holder). Its newNonLitChunk path
                    // throws whenever a holder exists, even if this Job's private cache has not seen it.
                    // Adopt the holder's chunk and check the target voxel before writing; Sable may
                    // have materialized a chunk with existing plot data.
                    if (chunk == null) {
                        holderBeforeCreate = container.getChunkHolder(globalChunk) != null;
                        ChunkPos createAt = globalChunk;
                        long chunkCreateStart = System.nanoTime();
                        withChunkHolderBoundsDeferred(() -> access.xenopixels$createNonLitChunk(createAt));
                        chunkCreateNanos += System.nanoTime() - chunkCreateStart;
                        chunksCreatedThisBatch++;
                        createdChunkHere = true;
                        holderImmediatelyAfterCreate = container.getChunkHolder(globalChunk) != null;
                        chunk = plot.getChunk(localChunk);
                        if (chunk == null) {
                            boolean holderOnImmediateReread = container.getChunkHolder(globalChunk) != null;
                            throw missingChunkFailure("plot chunk absent after createNonLitChunk", globalChunk,
                                    index, createdChunkHere, holderBeforeCreate, holderImmediatelyAfterCreate,
                                    holderOnImmediateReread);
                        }
                    }
                    incrementalChunks.put(key, chunk);
                    incrementalGlobalChunks.put(key, globalChunk);
                }
                BlockState state = filled.states()[index];
                if (state.hasBlockEntity() || !state.getFluidState().isEmpty())
                    throw new IllegalStateException("incremental asteroid writer does not support block entities or fluids: " + state);
                LevelChunkSection section = chunk.getSections()[sectionIndex];
                int localX = Math.floorMod(px, 16), localY = Math.floorMod(py, 16), localZ = Math.floorMod(pz, 16);
                long preReadStart = System.nanoTime();
                if (incrementalAirCheckedSections.add(new IncrementalSectionKey(key, sectionIndex))
                        && !section.hasOnlyAir())
                    throw new IllegalStateException("incremental writer encountered a pre-existing non-air section: chunk="
                            + globalChunk + " section=" + sectionIndex + " voxel=" + px + "," + py + "," + pz);
                BlockState existingState = section.getBlockState(localX, localY, localZ);
                preReadNanos += System.nanoTime() - preReadStart;
                if (!existingState.isAir())
                    throw new IllegalStateException("incremental writer encountered an occupied voxel before write: chunk="
                            + globalChunk + " voxel=" + px + "," + py + "," + pz + " state=" + existingState);
                long hookStart = System.nanoTime();
                BlockState oldState = section.setBlockState(localX, localY, localZ, state);
                sectionWriteNanos += System.nanoTime() - hookStart;
                // Keep the postcondition too: if a callback or unexpected mutation changes the section
                // between the pre-read and write, fail the build instead of hiding the collision.
                if (!oldState.isAir())
                    throw new IllegalStateException("incremental writer encountered an occupied voxel during write: chunk="
                            + globalChunk + " voxel=" + px + "," + py + "," + pz + " previous=" + oldState);
                if (body.isRemoved())
                    throw removedDuringBuild("after section write", globalChunk, index);
                var holder = container.getChunkHolder(globalChunk);
                if (holder == null) {
                    boolean holderOnImmediateReread = container.getChunkHolder(globalChunk) != null;
                    throw missingChunkFailure("holder absent after block write", globalChunk, index,
                            createdChunkHere, holderBeforeCreate, holderImmediatelyAfterCreate,
                            holderOnImmediateReread);
                }
                hookStart = System.nanoTime();
                holder.handleBlockChange(Math.floorMod(px, 16), py, Math.floorMod(pz, 16), oldState, state);
                holderChangeNanos += System.nanoTime() - hookStart;
                BlockPos hostPosition = new BlockPos(globalChunk.getMinBlockX() + Math.floorMod(px, 16), py,
                        globalChunk.getMinBlockZ() + Math.floorMod(pz, 16));
                hookStart = System.nanoTime();
                body.getHeatMapManager().onSolidAdded(hostPosition);
                heatMapNanos += System.nanoTime() - hookStart;
                hookStart = System.nanoTime();
                body.getFloatingBlockController().queueAddFloatingBlock(state, hostPosition);
                floatingNanos += System.nanoTime() - hookStart;
                hookStart = System.nanoTime();
                physics.updateMassDataFromBlockChange(body, hostPosition, oldState, state, false);
                massChangeNanos += System.nanoTime() - hookStart;
                hookStart = System.nanoTime();
                plot.onBlockChange(hostPosition, state);
                plotChangeNanos += System.nanoTime() - hookStart;
            }
            int completedTo = index;
            double writeMillis = (System.nanoTime() - writeStart) / 1e6;
            // addChunkHolder's O(current chunk count) bounds scan is coalesced above; publish the completed
            // batch bounds once before the body can be observed on the next server tick.
            long boundsStart = System.nanoTime();
            plot.updateBoundingBox();
            body.updateBoundingBox();
            double boundsMillis = (System.nanoTime() - boundsStart) / 1e6;
            incrementalCursor = completedTo;
            installed += completedTo - from;
            double initialStatsMillis = 0.0;
            if (!initialStatsUploaded && installed > 0) {
                long statsStart = System.nanoTime();
                plot.updateBoundingBox();
                body.updateBoundingBox();
                body.updateMergedMassData(1.0F);
                physics.getPipeline().onStatsChanged(body);
                initialStatsMillis = (System.nanoTime() - statsStart) / 1e6;
                initialStatsUploaded = true;
            }
            double finishMillis = 0.0;
            if (incrementalCursor >= filled.packed().length) {
                double elapsedMillis = (System.nanoTime() - started) / 1e6;
                if (!shouldDeferFinish(net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.generatedBodyBuildMillisPerTick(),
                        millisBudget, elapsedMillis, completedTo - from)) {
                    long finishStart = System.nanoTime();
                    done = stepFinishIncrementalSections(container, physics);
                    finishMillis = (System.nanoTime() - finishStart) / 1e6;
                }
            }
            double millis = (System.nanoTime() - started) / 1e6;
            if (millis > 10 || done || chunksCreatedThisBatch > 0)
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                        "Incremental plot build batch {}: {} voxels this batch (cap {}), {} installed, {} chunks (+{} this batch), {} ms [write={} ms (chunkCreate={} ms, preRead={} ms, section={} holder={} heat={} floating={} mass={} plot={}), bounds={} ms, initialStats={} ms, finish={} ms]",
                        batches, completedTo - from, budget, installed, incrementalChunks.size(), chunksCreatedThisBatch, String.format("%.2f", millis),
                        String.format("%.2f", writeMillis), String.format("%.2f", chunkCreateNanos / 1e6),
                        String.format("%.2f", preReadNanos / 1e6),
                        String.format("%.2f", sectionWriteNanos / 1e6),
                        String.format("%.2f", holderChangeNanos / 1e6), String.format("%.2f", heatMapNanos / 1e6),
                        String.format("%.2f", floatingNanos / 1e6), String.format("%.2f", massChangeNanos / 1e6),
                        String.format("%.2f", plotChangeNanos / 1e6), String.format("%.2f", boundsMillis),
                        String.format("%.2f", initialStatsMillis), String.format("%.2f", finishMillis));
            installMillis += millis;
            maxServerSliceMillis = Math.max(maxServerSliceMillis, millis);
            batches++;
            return done;
        }

        private IllegalStateException removedDuringBuild(String reason, ChunkPos globalChunk, int voxelIndex) {
            String chunk = globalChunk == null ? "<not reached>" : globalChunk.toString();
            String centerBlock = allocatedPlotCentre == null ? "<unallocated>" : allocatedPlotCentre.toString();
            return new IllegalStateException("Sable incremental plot build failed: reason=" + reason
                    + ", bodyUuid=" + (body == null ? "<unallocated>" : body.getUniqueId())
                    + ", removed=" + (body != null && body.isRemoved())
                    + ", slot=(" + allocationSlotX + "," + allocationSlotZ + ")"
                    + ", plotCenter=" + centerBlock + ", chunk=" + chunk
                    + ", voxelIndex=" + voxelIndex + "/" + total + ", installed=" + installed);
        }

        private IllegalStateException missingChunkFailure(String reason, ChunkPos globalChunk, int voxelIndex,
                                                          boolean createdChunkHere, boolean holderBeforeCreate,
                                                          boolean holderImmediatelyAfterCreate,
                                                          boolean holderOnImmediateReread) {
            String centerBlock = allocatedPlotCentre == null ? "<unallocated>" : allocatedPlotCentre.toString();
            return new IllegalStateException("Sable incremental chunk state lost: reason=" + reason
                    + ", bodyUuid=" + (body == null ? "<unallocated>" : body.getUniqueId())
                    + ", removed=" + (body != null && body.isRemoved())
                    + ", slot=(" + allocationSlotX + "," + allocationSlotZ + ")"
                    + ", plotCenter=" + centerBlock + ", chunk=" + globalChunk
                    + ", tick=" + level.getGameTime() + ", voxelIndex=" + voxelIndex + "/" + total
                    + ", installed=" + installed + ", createdChunkHere=" + createdChunkHere
                    + ", holderBeforeCreate=" + holderBeforeCreate
                    + ", holderImmediatelyAfterCreate=" + holderImmediatelyAfterCreate
                    + ", holderOnImmediateReread=" + holderOnImmediateReread);
        }

        private boolean stepFinishIncrementalSections(ServerSubLevelContainer container, SubLevelPhysicsSystem physics) {
            var plot = body.getPlot();
            var hostLevel = (net.minecraft.server.level.ServerLevel) container.getLevel();
            if (incrementalFinishPhase == 0) {
                // Let the light engine make progress over multiple server ticks if it has queued work.
                if (plot.getLightEngine().hasLightWork()) {
                    plot.getLightEngine().runLightUpdates();
                    return false;
                }
                long phaseStarted = System.nanoTime();
                long operationStarted = System.nanoTime();
                plot.updateBoundingBox();
                body.updateBoundingBox();
                incrementalFinishBoundsNanos += System.nanoTime() - operationStarted;
                operationStarted = System.nanoTime();
                body.updateMergedMassData(1.0F);
                incrementalFinishMassNanos += System.nanoTime() - operationStarted;
                operationStarted = System.nanoTime();
                physics.getPipeline().onStatsChanged(body);
                incrementalFinishStatsNanos += System.nanoTime() - operationStarted;
                // Keep the complete chunk/section inventory pass out of this single server-thread call.
                // Phase 1 discovers at most one chunk per step, alongside the existing one-chunk lifecycle work.
                incrementalFinishChunks = new ArrayList<>(incrementalChunks.size());
                incrementalFinishSections = new ArrayList<>();
                incrementalFinishChunkIterator = incrementalChunks.entrySet().iterator();
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                        "Incremental plot finish setup: {} ms [bounds={} ms mass={} ms stats={} ms chunkSectionIndex={} ms]",
                        String.format("%.2f", (System.nanoTime() - phaseStarted) / 1e6),
                        String.format("%.2f", incrementalFinishBoundsNanos / 1e6),
                        String.format("%.2f", incrementalFinishMassNanos / 1e6),
                        String.format("%.2f", incrementalFinishStatsNanos / 1e6),
                        String.format("%.2f", incrementalFinishIndexNanos / 1e6));
                incrementalFinishPhase = 1;
                return false;
            }
            if (incrementalFinishPhase == 1) {
                if (incrementalFinishChunkIterator != null && incrementalFinishChunkIterator.hasNext()) {
                    long started = System.nanoTime();
                    var entry = incrementalFinishChunkIterator.next();
                    ChunkPos globalChunk = incrementalGlobalChunks.get(entry.getKey());
                    LevelChunk chunk = entry.getValue();
                    IncrementalFinishChunk finishChunk = new IncrementalFinishChunk(globalChunk, chunk);
                    incrementalFinishChunks.add(finishChunk);
                    long indexStarted = System.nanoTime();
                    LevelChunkSection[] chunkSections = chunk.getSections();
                    for (int sectionIndex = 0; sectionIndex < chunkSections.length; sectionIndex++) {
                        LevelChunkSection section = chunkSections[sectionIndex];
                        if (!section.hasOnlyAir()) incrementalFinishSections.add(new IncrementalFinishSection(globalChunk,
                                section, chunk.getSectionYFromSectionIndex(sectionIndex)));
                    }
                    incrementalFinishIndexNanos += System.nanoTime() - indexStarted;
                    incrementalFinishChunkCursor++;
                    var access = plot instanceof SableIncrementalPlotAccess sableAccess ? sableAccess : null;
                    if (access == null) throw new IllegalStateException("Sable incremental plot lifecycle mixin is not applied");
                    Heightmap.primeHeightmaps(chunk, chunk.getPersistedStatus().heightmapsAfter());
                    CompoundTag emptyChunkData = new CompoundTag();
                    var registryAccess = hostLevel.registryAccess();
                    SablePlotPlatform.INSTANCE.readLightData(emptyChunkData, registryAccess, chunk);
                    chunk.setLightCorrect(false);
                    access.xenopixels$lightChunk(chunk);
                    SablePlotPlatform.INSTANCE.readChunkAttachments(emptyChunkData, registryAccess, chunk);
                    chunk.registerAllBlockEntitiesAfterLevelLoad();
                    hostLevel.startTickingChunk(chunk);
                    SablePlotPlatform.INSTANCE.postLoad(emptyChunkData, chunk);
                    long elapsed = System.nanoTime() - started;
                    incrementalFinishChunkNanos += elapsed;
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.debug(
                            "Incremental plot finish chunk {}/{}: {} ms (indexed collision sections={})",
                            incrementalFinishChunkCursor, incrementalChunks.size(), String.format("%.2f", elapsed / 1e6),
                            incrementalFinishSections.size());
                    return false;
                }
                incrementalFinishChunkIterator = null;
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                        "Incremental plot finish indexing complete: chunks={}, collisionSections={}, cumulativeIndex={} ms",
                        incrementalFinishChunks.size(), incrementalFinishSections.size(),
                        String.format("%.2f", incrementalFinishIndexNanos / 1e6));
                incrementalFinishPhase = 2;
                gcMillisSample = gcMillis();
                gcCollectionsSample = gcCollections();
                return false;
            }
            if (incrementalFinishPhase == 2) {
                if (incrementalFinishSectionCursor < incrementalFinishSections.size()) {
                    long started = System.nanoTime();
                    IncrementalFinishSection finishSection = incrementalFinishSections.get(incrementalFinishSectionCursor++);
                    ChunkPos globalChunk = finishSection.globalChunk();
                    SectionPos sectionPos = SectionPos.of(globalChunk, finishSection.sectionY());
                    long ticketStarted = System.nanoTime();
                    physics.getTicketManager().addTicketForSection(hostLevel, sectionPos);
                    double ticketMillis = (System.nanoTime() - ticketStarted) / 1e6;
                    long collisionStarted = System.nanoTime();
                    try (var sparseAirScope = SableSparseAirCollisionEncoding.enter(
                            net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildSparseAirCollisionEncoding())) {
                        physics.getPipeline().handleChunkSectionAddition(finishSection.section(), globalChunk.x,
                                finishSection.sectionY(), globalChunk.z, true);
                    }
                    double collisionMillis = (System.nanoTime() - collisionStarted) / 1e6;
                    long elapsed = System.nanoTime() - started;
                    incrementalFinishSectionNanos += elapsed;
                    double sectionMillis = elapsed / 1e6;
                    net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.debug(
                            "Incremental plot finish collision section {}/{} at {}: {} ms",
                            incrementalFinishSectionCursor, incrementalFinishSections.size(), sectionPos,
                            String.format("%.2f", sectionMillis));
                    long sectionTick = hostLevel.getGameTime();
                    if (collisionMillis > slowestCollisionSectionMillis) {
                        slowestCollisionSectionMillis = collisionMillis;
                        slowestCollisionSection = sectionPos;
                        slowestCollisionSectionTick = sectionTick;
                    }
                    if (ticketMillis > slowestTicketSectionMillis) {
                        slowestTicketSectionMillis = ticketMillis;
                        slowestTicketSection = sectionPos;
                        slowestTicketSectionTick = sectionTick;
                    }
                    if (sectionMillis > SLOW_COLLISION_SECTION_MILLIS) {
                        long gc = gcMillis(), collections = gcCollections();
                        net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                                "Slow collision section {}/{}: {} ms at {} on tick {} (ticket={} ms, registration={} ms, GC {} ms / {} collections since the previous slow sample)",
                                incrementalFinishSectionCursor, incrementalFinishSections.size(),
                                String.format("%.2f", sectionMillis), sectionPos, sectionTick,
                                String.format("%.2f", ticketMillis), String.format("%.2f", collisionMillis),
                                gc - gcMillisSample, collections - gcCollectionsSample);
                        gcMillisSample = gc;
                        gcCollectionsSample = collections;
                    }
                    return false;
                }
                incrementalFinishPhase = 3;
                return false;
            }
            if (incrementalFinishPhase == 3) {
                if (incrementalFinishNotifyCursor < incrementalFinishChunks.size()) {
                    IncrementalFinishChunk finishChunk = incrementalFinishChunks.get(incrementalFinishNotifyCursor++);
                    SableChunkEventPlatform.INSTANCE.onPlotChunkLoaded(finishChunk.chunk());
                    for (ServerPlayer player : container.getPlayersTracking(finishChunk.globalChunk())) {
                        SubLevelPlayerChunkSender.sendChunk(packet -> player.connection.send(packet), plot.getLightEngine(), finishChunk.chunk());
                        SubLevelPlayerChunkSender.sendChunkPoiData(hostLevel, finishChunk.chunk());
                    }
                    return false;
                }
                incrementalFinishPhase = 4;
                return false;
            }
            if (plot.getLightEngine().hasLightWork()) {
                plot.getLightEngine().runLightUpdates();
                return false;
            }
            long finalStarted = System.nanoTime();
            long operationStarted = System.nanoTime();
            body.updateMergedMassData(1.0F);
            incrementalFinishMassNanos += System.nanoTime() - operationStarted;
            operationStarted = System.nanoTime();
            physics.getPipeline().onStatsChanged(body);
            incrementalFinishStatsNanos += System.nanoTime() - operationStarted;
            var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(body);
            if (net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildPoseFix() && handle.isValid()) {
                operationStarted = System.nanoTime();
                handle.teleport(centre, new Quaterniond());
                handle.addLinearAndAngularVelocity(handle.getLinearVelocity(new Vector3d()).negate(),
                        handle.getAngularVelocity(new Vector3d()).negate());
                incrementalFinishPoseNanos += System.nanoTime() - operationStarted;
            }
            done = true;
            PlotBodyBuildRegistry.unregister(body.getUniqueId(), level);
            net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                    "Incremental plot finish complete: chunks={}, collisionSections={}, chunkLifecycle={} ms, collisionRegistration={} ms, bounds={} ms, mass={} ms, stats={} ms, chunkSectionIndex={} ms, poseFix={} ms, finalPhase={} ms, slowestCollisionSection={} ms at {} on tick {}, slowestTicket={} ms at {} on tick {}",
                    incrementalFinishChunks.size(), incrementalFinishSections.size(),
                    String.format("%.2f", incrementalFinishChunkNanos / 1e6),
                    String.format("%.2f", incrementalFinishSectionNanos / 1e6),
                    String.format("%.2f", incrementalFinishBoundsNanos / 1e6),
                    String.format("%.2f", incrementalFinishMassNanos / 1e6),
                    String.format("%.2f", incrementalFinishStatsNanos / 1e6),
                    String.format("%.2f", incrementalFinishIndexNanos / 1e6),
                    String.format("%.2f", incrementalFinishPoseNanos / 1e6),
                    String.format("%.2f", (System.nanoTime() - finalStarted) / 1e6),
                    String.format("%.2f", slowestCollisionSectionMillis),
                    slowestCollisionSection == null ? "none" : slowestCollisionSection, slowestCollisionSectionTick,
                    String.format("%.2f", slowestTicketSectionMillis),
                    slowestTicketSection == null ? "none" : slowestTicketSection, slowestTicketSectionTick);
            return true;
        }

        private record IncrementalFinishChunk(ChunkPos globalChunk, LevelChunk chunk) { }
        private record IncrementalFinishSection(ChunkPos globalChunk, LevelChunkSection section, int sectionY) { }
        private record IncrementalSectionKey(long chunkKey, int sectionIndex) { }

        /**
         * Moves every other sub-level whose world box reaches into this body's shell band (from {@code extent - 3} to
         * {@code extent + 2} blocks from the build centre) radially outward until its box clears {@code extent + 2}.
         * Only meaningful for hollow shells built around {@link #centre}; bodies fully inside the hollow are left alone.
         */
        private void evictIntruders(ServerSubLevelContainer container) {
            double inner = extent - 3, outer = extent + 2;
            for (var other : new ArrayList<>(container.getAllSubLevels())) {
                if (other == body || other.isRemoved() || PhysicalEarth.isManaged(other)) continue;
                var box = other.boundingBox();
                double nx = Math.max(box.minX(), Math.min(centre.x, box.maxX())), ny = Math.max(box.minY(), Math.min(centre.y, box.maxY())),
                        nz = Math.max(box.minZ(), Math.min(centre.z, box.maxZ()));
                double near = centre.distance(nx, ny, nz);
                double fx = Math.max(Math.abs(box.minX() - centre.x), Math.abs(box.maxX() - centre.x)),
                        fy = Math.max(Math.abs(box.minY() - centre.y), Math.abs(box.maxY() - centre.y)),
                        fz = Math.max(Math.abs(box.minZ() - centre.z), Math.abs(box.maxZ() - centre.z));
                double far = Math.sqrt(fx * fx + fy * fy + fz * fz);
                if (near > outer || far < inner) continue;
                var handle = dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle.of(other);
                if (!handle.isValid()) continue;
                Vector3d position = new Vector3d(other.logicalPose().position());
                Vector3d direction = new Vector3d(position).sub(centre);
                if (direction.lengthSquared() < 1e-6) direction.set(1, 0, 0);
                direction.normalize();
                double halfDiagonal = 0.5 * Math.sqrt(Math.pow(box.maxX() - box.minX(), 2) + Math.pow(box.maxY() - box.minY(), 2)
                        + Math.pow(box.maxZ() - box.minZ(), 2));
                Vector3d target = new Vector3d(direction).mul(outer + halfDiagonal + 1).add(centre);
                handle.teleport(target, new Quaterniond(other.logicalPose().orientation()));
                net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.warn("Batched build: moved sub-level {} out of the shell band before completion: {} -> {}",
                        other.getUniqueId(), position, target);
            }
        }

        /** Removes a half-built body (a player left before it finished, or a failure). */
        public void cancel() {
            cancel(null);
        }

        private void cancel(Throwable primaryFailure) {
            failed = true;
            ServerSubLevel cancellingBody = body;
            RuntimeException cleanupFailure = null;
            try {
                if (cancellingBody != null) onCancellation.accept(cancellingBody);
            } catch (RuntimeException ticketFailure) {
                cleanupFailure = ticketFailure;
            }
            try {
                if (cancellingBody != null && !cancellingBody.isRemoved()) {
                    ServerSubLevelContainer container = SubLevelContainer.getContainer(level);
                    if (container == null) throw new IllegalStateException("Sable container is unavailable during build cancellation");
                    container.removeSubLevel(cancellingBody, SubLevelRemovalReason.REMOVED);
                }
            } catch (RuntimeException removalFailure) {
                if (cleanupFailure == null) cleanupFailure = removalFailure;
                else cleanupFailure.addSuppressed(removalFailure);
            }
            try {
                if (cleanupFailure != null) {
                    if (primaryFailure != null) primaryFailure.addSuppressed(cleanupFailure);
                    else net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.error(
                            "Could not fully clean up partially built Sable body during cancellation{}",
                            cancellingBody == null ? "" : " " + cancellingBody.getUniqueId(), cleanupFailure);
                }
            } finally {
                if (cancellingBody != null) PlotBodyBuildRegistry.unregister(cancellingBody.getUniqueId(), level);
                // Sable detaches/unloads its chunk holders on removal, but these job-owned maps otherwise
                // keep the old LevelChunk objects strongly reachable if a caller retains the cancelled job.
                hostChunkCache.clear();
                incrementalChunks.clear();
                incrementalGlobalChunks.clear();
                incrementalAirCheckedSections.clear();
                incrementalFinishChunkIterator = null;
                incrementalFinishChunks = List.of();
                incrementalFinishSections = List.of();
                columns = null;
                // Sable LevelPlot retains holders in its final chunk array after onRemove clears loadedChunks.
                // Drop the Job's remaining root to that plot/LevelChunks once cancellation is complete.
                body = null;
                releaseVoxelFuture();
            }
        }

        private void releaseVoxelFuture() {
            fillCancelled.set(true);
            CompletableFuture<Filled> pending = voxels;
            voxels = null;
            // The supplier polls fillCancelled between rows. cancel(false) abandons its result future without
            // relying on thread interruption, which CompletableFuture does not guarantee for supplyAsync.
            if (pending != null && !pending.isDone()) pending.cancel(false);
        }
    }

    /**
     * S11 experiment E5 (switch {@code planetBuildFinalize}, off by default): the post-load sequence of the
     * reference mod's active path ({@code GiantAsteroidSectorService.verifyMassAfterPlotLoad}, dmzcompanion
     * src.zip lines 479-510) followed by {@code updateLastPose}, which every Sable 2.0.3 creation path calls.
     * Tested as one group; it is not a diagnosed fix. All calls javap-verified on Sable 2.0.3.
     */
    static void finalizeLikeReference(ServerSubLevel body) {
        body.getPlot().updateBoundingBox();
        var self = body.getSelfMassTracker();
        if (self == null || self.isInvalid() || self.getCenterOfMass() == null || !Double.isFinite(self.getMass()) || self.getMass() <= 0) {
            body.buildMassTracker();
            self = body.getSelfMassTracker();
        }
        if (self == null || self.isInvalid() || self.getCenterOfMass() == null || !Double.isFinite(self.getMass()) || self.getMass() <= 0)
            throw new IllegalStateException("self mass invalid after plot load");
        body.updateMergedMassData(1.0F);
        var merged = body.getMassTracker();
        if (merged == null || merged.isInvalid() || merged.getCenterOfMass() == null || !Double.isFinite(merged.getMass()) || merged.getMass() <= 0)
            throw new IllegalStateException("merged mass invalid after plot load");
        body.logicalPose().rotationPoint().set(merged.getCenterOfMass());
        var container = SubLevelContainer.getContainer(body.getLevel());
        if (container != null && container.physicsSystem() != null) container.physicsSystem().getPipeline().onStatsChanged(body);
        body.updateBoundingBox();
        body.forceUpdateGlobalBounds();
        body.updateLastPose();
    }

    /**
     * Bounded diagnostic record of one body (no per-voxel data). Centres of mass are plot-space coordinates, not
     * world coordinates; the requested world centre and voxel count are included for comparison.
     */
    public static void snapshot(String label, ServerSubLevel body, Vector3dc requestedCentre, int voxels,
                                boolean deferFinalizeEnabled) {
        var self = body.getSelfMassTracker();
        var merged = body.getMassTracker();
        net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.info(
                "S11 SNAPSHOT {}: uuid={} requestedCentre={} voxels={} flags[referenceFinalize={}, deferPerBatchFinalize={}, poseFix={}, "
                        + "coalesceChunkBounds={}, voxelsPerTick={}] "
                        + "self[mass={} com(plot)={} inertia={} invInertia={} invalid={}] merged[mass={} com(plot)={} inertia={} invInertia={} invalid={}] "
                        + "pose={} lastPose={} boundingBox={}",
                label, body.getUniqueId(), requestedCentre, voxels,
                net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildFinalize(),
                deferFinalizeEnabled,
                net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildPoseFix(),
                net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildCoalesceChunkBounds(),
                net.bullettrain.xenopixelsmod.config.XenoSpaceConfig.planetBuildVoxelsPerTick(),
                self == null ? "null" : self.getMass(), self == null ? "null" : plain(self.getCenterOfMass()),
                self == null ? "null" : self.getInertiaTensor(), self == null ? "null" : self.getInverseInertiaTensor(),
                self == null ? "null" : self.isInvalid(),
                merged == null ? "null" : merged.getMass(), merged == null ? "null" : plain(merged.getCenterOfMass()),
                merged == null ? "null" : merged.getInertiaTensor(), merged == null ? "null" : merged.getInverseInertiaTensor(),
                merged == null ? "null" : merged.isInvalid(),
                body.logicalPose(), body.lastPose(), bounds(body.boundingBox()));
    }

    /** Full-precision vector (Vector3d.toString prints four significant digits, i.e. +/-5000 blocks at plot coordinates). */
    private static String plain(org.joml.Vector3dc v) { return v == null ? "null" : "(" + v.x() + ", " + v.y() + ", " + v.z() + ")"; }

    /** BoundingBox3d has no coordinate toString; print its six public accessors (verified on sable-companion 1.6.0). */
    private static String bounds(dev.ryanhcode.sable.companion.math.BoundingBox3dc box) {
        return box == null ? "null" : String.format("[%.1f %.1f %.1f .. %.1f %.1f %.1f]", box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }

    /**
     * Voxel indices grouped by plot chunk column, in a planar sweep (column x, then z). {@code load} replaces a
     * whole column, so batches are column-granular; a sweep keeps the installed part of a hollow shell one
     * connected cap at every step, where a centre-out ring order leaves the top and bottom caps unconnected.
     * Element 0 of each array is unused (it carries the sweep key for sorting).
     */
    static List<int[]> group(int[] packed, int centreLocalX, int centreLocalZ) {
        // fastutil map: ChunkPos.asLong keys under Long.hashCode collide as x ^ z, which treeified java.util.HashMap and
        // cost ~80 ms of one server tick for the Sun (S11 c3/batch4 slow-tick profiles).
        var byColumn = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<it.unimi.dsi.fastutil.ints.IntArrayList>();
        int ccx = Math.floorDiv(centreLocalX, 16), ccz = Math.floorDiv(centreLocalZ, 16);
        for (int i = 0; i < packed.length; i++) {
            int cx = Math.floorDiv(centreLocalX + unpackX(packed[i]), 16), cz = Math.floorDiv(centreLocalZ + unpackZ(packed[i]), 16);
            long key = ChunkPos.asLong(cx, cz);
            var list = byColumn.get(key);
            if (list == null) {
                list = new it.unimi.dsi.fastutil.ints.IntArrayList();
                list.add((cx - ccx + 1024) * 4096 + (cz - ccz + 1024));
                byColumn.put(key, list);
            }
            list.add(i);
        }
        List<int[]> columns = new ArrayList<>(byColumn.size());
        for (var list : byColumn.values()) columns.add(list.toIntArray());
        columns.sort(java.util.Comparator.comparingInt(column -> column[0]));
        return columns;
    }

    static int maxGroupedVoxels(List<int[]> columns) {
        if (columns == null || columns.isEmpty()) return 0;
        int max = 0;
        for (int[] column : columns) max = Math.max(max, column.length - 1); // element 0 is the sort key
        return max;
    }

    /** True when the current greedy whole-column schedule can stay within a positive voxel allowance. */
    static boolean atomicBatchesFit(List<int[]> columns, int startColumn, int forcedPrefix, int voxelBudget) {
        if (voxelBudget <= 0) return true;
        if (columns == null || startColumn < 0 || startColumn > columns.size()) return false;
        int cursor = startColumn;
        while (cursor < columns.size()) {
            long batch = 0;
            while (cursor < columns.size() && (batch == 0 || batch < voxelBudget || cursor < forcedPrefix)) {
                batch += Math.max(0, columns.get(cursor).length - 1);
                cursor++;
            }
            if (batch > voxelBudget) return false;
        }
        return true;
    }

    static boolean shouldStopTimeSlicedStep(double millisBudget, double elapsedMillis, int completedVoxels) {
        return millisBudget > 0.0 && completedVoxels > 0 && elapsedMillis >= millisBudget;
    }

    static boolean shouldDeferFinish(double millisCap, double millisBudget, double elapsedMillis, int completedVoxels) {
        if (!Double.isFinite(millisCap) || millisCap <= 0.0) return false;
        if (!Double.isFinite(millisBudget) || millisBudget <= 0.0) return true;
        return shouldStopTimeSlicedStep(millisBudget, elapsedMillis, completedVoxels);
    }

    /**
     * Moves the centre column (it holds the y poles) and one column at each x and z extreme to the front, so the first
     * batch already spans the body's final bounding box. S11 per-batch timing showed the expensive batches are the
     * periodic ones in the x-then-z sweep, with a cost growing with body size, consistent with a native rebuild
     * whenever the bounds grow. Returns how many frame columns lead the list (all installed in the first batch).
     */
    /**
     * With the per-batch finalize deferred, the first batch is the only one that pays the body's first full Sable
     * finalize, so it should carry as few voxels as possible: the smallest column goes first (alone, as it is batch 0).
     */
    static void smallestFirst(List<int[]> columns) {
        int smallest = 0;
        for (int i = 1; i < columns.size(); i++) if (columns.get(i).length < columns.get(smallest).length) smallest = i;
        if (smallest > 0) columns.add(0, columns.remove(smallest));
    }

    static int frameFirst(List<int[]> columns) {
        if (columns.size() < 2) return columns.size();
        int centre = 0, minX = 0, maxX = 0, minZ = 0, maxZ = 0;
        for (int i = 0; i < columns.size(); i++) {
            int key = columns.get(i)[0], x = key / 4096 - 1024, z = key % 4096 - 1024;
            int cKey = columns.get(centre)[0], cx = cKey / 4096 - 1024, cz = cKey % 4096 - 1024;
            if (Math.abs(x) + Math.abs(z) < Math.abs(cx) + Math.abs(cz)) centre = i;
            if (x < columns.get(minX)[0] / 4096 - 1024) minX = i;
            if (x > columns.get(maxX)[0] / 4096 - 1024) maxX = i;
            if (z < columns.get(minZ)[0] % 4096 - 1024) minZ = i;
            if (z > columns.get(maxZ)[0] % 4096 - 1024) maxZ = i;
        }
        var picked = new java.util.LinkedHashSet<Integer>(List.of(centre, minX, maxX, minZ, maxZ));
        List<int[]> frame = new ArrayList<>(), rest = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) (picked.contains(i) ? frame : rest).add(columns.get(i));
        // Each remaining column is followed by its mirror (-x, -z) so the centre of mass stays near the middle through
        // the build (test: are the remaining expensive batches the native centre-of-mass update?).
        var byKey = new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<int[]>();
        for (int[] column : rest) byKey.put(column[0], column);
        List<int[]> paired = new ArrayList<>(rest.size());
        for (int[] column : rest) {
            if (!byKey.containsKey(column[0])) continue;
            byKey.remove(column[0]);
            paired.add(column);
            int x = column[0] / 4096 - 1024, z = column[0] % 4096 - 1024;
            int[] mirror = byKey.remove((-x + 1024) * 4096 + (-z + 1024));
            if (mirror != null) paired.add(mirror);
        }
        columns.clear();
        columns.addAll(frame);
        columns.addAll(paired);
        return frame.size();
    }

    private static final ThreadLocal<Boolean> DEFERRING = ThreadLocal.withInitial(() -> false);

    /** For SablePlotLoadFinalizeDeferMixin: true only inside a non-final batch's plot.load of a batched build. */
    public static boolean deferringFinalize() { return DEFERRING.get(); }

    private static CompoundTag tag(Map<Long, Map<Integer, PalettedContainer<BlockState>>> chunks, int logPlotSize) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("log_size", logPlotSize);
        tag.putInt("data_version", 1);
        tag.putString("biome", "minecraft:the_void");
        CompoundTag chunksTag = new CompoundTag();
        for (var chunk : chunks.entrySet()) {
            CompoundTag sections = new CompoundTag();
            for (var section : chunk.getValue().entrySet()) {
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.put("block_states", BLOCK_STATES.encodeStart(NbtOps.INSTANCE, section.getValue()).getOrThrow());
                sections.put(Integer.toString(section.getKey()), sectionTag);
            }
            CompoundTag chunkTag = new CompoundTag();
            chunkTag.put("sections", sections);
            chunksTag.put(Long.toString(chunk.getKey()), chunkTag);
        }
        tag.put("chunks", chunksTag);
        return tag;
    }

    /**
     * A slot that both the occupancy bitset and the slot table agree is free; the dmzcompanion authors saw
     * Sable 2.0.x's own allocator reuse a slot whose bit was clear while a plot still held it.
     */
    private static Allocation allocate(ServerSubLevelContainer container, Pose3d pose) {
        int side = 1 << container.getLogSideLength();
        var occupancy = container.getOccupancy();
        for (int z = 0; z < side; z++)
            for (int x = 0; x < side; x++) {
                if (occupancy.get(container.getIndex(x, z)) || container.getSubLevel(x, z) != null) continue;
                try {
                    return new Allocation((ServerSubLevel) container.allocateSubLevel(UUID.randomUUID(), x, z, pose), x, z);
                } catch (IllegalArgumentException taken) {
                    // Claimed between the checks; keep scanning.
                }
            }
        return null;
    }
}
