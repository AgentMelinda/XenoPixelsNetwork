package net.bullettrain.xenopixelsmod.space.physics;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** Shared per-server voxel and best-effort wall-time budgets, divided among generated physical-body owners. */
public final class SpaceBuildWorkBudget {
    public enum Owner { CHART_PLANET, ASTEROID, SMALL_PLANET, SUPER_DRAGON_BALL, SPACE_POD }
    private static final double FIRST_VOXEL_SLICE_MILLIS = 0.001;

    private static final Map<MinecraftServer, TickBudget> BUDGETS = new WeakHashMap<>();

    private SpaceBuildWorkBudget() {}

    /** Avoid allocating an empty Sable plot when this owner cannot install any voxels this tick. */
    public static synchronized boolean mayPrepare(MinecraftServer server, long tick, Owner owner, int localLimit) {
        if (localLimit <= 0) return false;
        double millisCap = XenoSpaceConfig.generatedBodyBuildMillisPerTick();
        if (!mayPrepareWithinMillis(millisCap, remainingMillis(server, tick, owner))) return false;
        int cap = XenoSpaceConfig.generatedBodyBuildVoxelsPerTick();
        if (cap <= 0) return true;
        TickBudget budget = budget(server, tick);
        return mayPrepareFor(cap, budget.used(owner), ownerShareFor(cap, tick, owner), localLimit);
    }

    public static synchronized int allowance(MinecraftServer server, long tick, Owner owner,
                                             PlotBodyBuilder.Job job, int localLimit) {
        int cap = XenoSpaceConfig.generatedBodyBuildVoxelsPerTick();
        if (cap <= 0) return Math.max(0, localLimit); // compatibility escape hatch: retain the local manager limit
        TickBudget budget = budget(server, tick);
        int ownerShare = ownerShareFor(cap, tick, owner);
        int remaining = Math.max(0, ownerShare - budget.used(owner));
        if (localLimit <= 0) return 0;
        if (remaining <= 0) {
            // Pairs with the current prepare() contract: prepare measures time but installs no voxels.
            // This keeps a newly allocated plot seedable if its time allowance expires during preparation.
            if (job.installed() != 0) return 0;
            remaining = 1;
        }

        int maxAtomicBatch = job.maxColumnVoxels();
        if (maxAtomicBatch <= 0) return 0; // caller must prepare the job before asking for work
        boolean exactWriter = job.incrementalSections();
        if (!exactWriter && requiresExactWriter(maxAtomicBatch, localLimit, remaining)) {
            // Sable's plot.load accepts each chunk key once. If one indivisible grouped column cannot fit,
            // switch this not-yet-started body to the verified exact voxel writer instead of overshooting or stalling.
            job.withIncrementalSections();
            exactWriter = true;
            XenoPixelsMod.LOGGER.debug(
                    "Generated-body build {} selected exact incremental batches: atomic plot.load group {} exceeds available tick budget {}",
                    job.body() == null ? "<unallocated>" : job.body().getUniqueId(), maxAtomicBatch, Math.min(localLimit, remaining));
        }
        return allowanceFor(localLimit, remaining, maxAtomicBatch, exactWriter);
    }

    /** Remaining owner wall-time allowance, bounded by its share and server remainder; indivisible work can overrun. */
    public static synchronized double remainingMillis(MinecraftServer server, long tick, Owner owner) {
        if (owner == null) throw new IllegalArgumentException("build-work owner is required");
        double cap = XenoSpaceConfig.generatedBodyBuildMillisPerTick();
        if (cap <= 0.0) return 0.0;
        TickBudget budget = budget(server, tick);
        return remainingMillisForOwner(cap, budget.usedMillis, budget.usedMillis(owner), Owner.values().length);
    }

    public static synchronized void record(MinecraftServer server, long tick, Owner owner, int installedVoxels) {
        if (installedVoxels <= 0 || XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() <= 0) return;
        budget(server, tick).record(owner, installedVoxels, XenoSpaceConfig.generatedBodyBuildVoxelsPerTick(), tick);
    }

    public static synchronized void recordMillis(MinecraftServer server, long tick, Owner owner, double elapsedMillis) {
        if (owner == null) throw new IllegalArgumentException("build-work owner is required");
        if (!Double.isFinite(elapsedMillis) || elapsedMillis <= 0.0) return;
        TickBudget budget = budget(server, tick);
        budget.usedMillis += elapsedMillis;
        budget.usedMillisByOwner[owner.ordinal()] += elapsedMillis;
    }

    /** Prepare one generated-body job and charge its synchronous server-thread work to this tick's measured budget. */
    public static boolean prepare(MinecraftServer server, long tick, Owner owner, PlotBodyBuilder.Job job) {
        long started = System.nanoTime();
        try {
            return job.prepare();
        } finally {
            recordMillis(server, tick, owner, (System.nanoTime() - started) / 1.0e6);
        }
    }

    /** Run a generated-body slice and charge its elapsed time and installed-voxel delta even if it throws. */
    static <T> T measureStep(MinecraftServer server, long tick, Owner owner, IntSupplier installed,
                             Supplier<T> operation) {
        int before = installed.getAsInt();
        long started = System.nanoTime();
        try {
            return operation.get();
        } finally {
            record(server, tick, owner, Math.max(0, installed.getAsInt() - before));
            recordMillis(server, tick, owner, (System.nanoTime() - started) / 1.0e6);
        }
    }

    /** Common instrumented builder call so every owner counts failed as well as successful slices. */
    public static boolean step(MinecraftServer server, long tick, Owner owner, PlotBodyBuilder.Job job,
                               int voxelBudget, double millisBudget) {
        double cap = XenoSpaceConfig.generatedBodyBuildMillisPerTick();
        int installed = job.installed();
        double boundedMillis = stepMillisBudget(cap, millisBudget, installed);
        int boundedVoxels = stepVoxelBudget(voxelBudget, cap, millisBudget, installed);
        return measureStep(server, tick, owner, job::installed, () -> job.step(boundedVoxels, boundedMillis));
    }

    /** Elapsed preparation and build-step work this tick, recorded even when the optional limiter is disabled. */
    public static synchronized double usedMillis(MinecraftServer server, long tick) {
        return budget(server, tick).usedMillis;
    }

    /** Per-owner preparation and build-step time this tick, recorded even when the optional limiter is disabled. */
    public static synchronized double usedMillis(MinecraftServer server, long tick, Owner owner) {
        if (owner == null) throw new IllegalArgumentException("build-work owner is required");
        return budget(server, tick).usedMillis(owner);
    }

    /** Formats measured build work for the admin perf report, including owners when the cap is off. */
    public static String formatTimeReport(double totalMillis, double capMillis,
                                          double chartMillis, double asteroidMillis,
                                          double smallPlanetMillis, double dragonBallMillis) {
        String cap = Double.isFinite(capMillis) && capMillis > 0.0
                ? String.format(java.util.Locale.ROOT, "%.3f ms best-effort", capMillis) : "off";
        return String.format(java.util.Locale.ROOT,
                "generated-body prepare+step ms this tick=%.3f cap=%s; by owner chart=%.3f asteroid=%.3f small-planet=%.3f super-dragon-ball=%.3f",
                finiteNonNegative(totalMillis), cap, finiteNonNegative(chartMillis), finiteNonNegative(asteroidMillis),
                finiteNonNegative(smallPlanetMillis), finiteNonNegative(dragonBallMillis));
    }

    private static double finiteNonNegative(double value) {
        return Double.isFinite(value) && value > 0.0 ? value : 0.0;
    }

    public static synchronized int used(MinecraftServer server, long tick) {
        if (XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() <= 0) return 0;
        return budget(server, tick).used;
    }

    public static synchronized int used(MinecraftServer server, long tick, Owner owner) {
        if (XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() <= 0) return 0;
        return budget(server, tick).used(owner);
    }

    public static int share(long tick, Owner owner) {
        return ownerShareFor(XenoSpaceConfig.generatedBodyBuildVoxelsPerTick(), tick, owner);
    }

    public static synchronized int overrun(MinecraftServer server, long tick) {
        if (XenoSpaceConfig.generatedBodyBuildVoxelsPerTick() <= 0) return 0;
        return budget(server, tick).overrun;
    }

    public static synchronized void forget(MinecraftServer server) { BUDGETS.remove(server); }

    static int allowanceFor(int localLimit, int remaining, int maxAtomicBatchVoxels, boolean exactVoxelWriter) {
        if (localLimit <= 0 || remaining <= 0 || maxAtomicBatchVoxels <= 0) return 0;
        int hardCeiling = Math.min(localLimit, remaining);
        if (exactVoxelWriter) return hardCeiling;
        // A regular plot.load call consumes a whole grouped X/Z chunk. Leave room for its largest possible group;
        // at least one complete group fits because the caller selects the exact writer if it cannot.
        if (maxAtomicBatchVoxels > hardCeiling) return 0;
        return Math.max(1, hardCeiling - maxAtomicBatchVoxels);
    }

    static boolean mayPrepareFor(int cap, int ownerUsed, int ownerShare, int localLimit) {
        if (localLimit <= 0) return false;
        if (cap <= 0) return true;
        return ownerShare > 0 && ownerUsed < ownerShare;
    }

    static boolean requiresExactWriter(int maxAtomicBatchVoxels, int localLimit, int remaining) {
        return maxAtomicBatchVoxels > 0 && maxAtomicBatchVoxels > Math.min(localLimit, remaining);
    }

    static double remainingMillisFor(double cap, double usedMillis) {
        if (!Double.isFinite(cap) || !Double.isFinite(usedMillis) || cap <= 0.0) return 0.0;
        return Math.max(0.0, cap - Math.max(0.0, usedMillis));
    }

    /** A positive cap must have time remaining before starting synchronous allocation/preparation work. */
    static boolean mayPrepareWithinMillis(double cap, double remainingMillis) {
        if (!Double.isFinite(cap)) return false;
        return cap <= 0.0 || (Double.isFinite(remainingMillis) && remainingMillis > 0.0);
    }

    /**
     * If preparation consumed the remaining allowance, still install one voxel into a newly
     * allocated plot. Sable removes empty plots at tick boundaries for extreme Y bounds. A tiny
     * positive allowance lets the incremental writer complete its mandatory first callback and
     * stop before the second; that callback is indivisible and may still overrun the time cap.
     */
    static double stepMillisBudget(double cap, double remainingMillis, int installedVoxels) {
        if (!Double.isFinite(cap) || cap <= 0.0) return 0.0;
        double remaining = Double.isFinite(remainingMillis) ? Math.max(0.0, remainingMillis) : 0.0;
        return installedVoxels <= 0 ? Math.max(FIRST_VOXEL_SLICE_MILLIS, remaining) : remaining;
    }

    /** A first voxel always seeds the allocated plot, but never uses a full local batch after the time slice expired. */
    static int stepVoxelBudget(int requested, double cap, double remainingMillis, int installedVoxels) {
        if (requested <= 0) return 0;
        if (!Double.isFinite(cap) || cap <= 0.0 || installedVoxels > 0) return requested;
        return !Double.isFinite(remainingMillis) || remainingMillis < FIRST_VOXEL_SLICE_MILLIS
                ? 1 : requested;
    }

    static double remainingMillisForOwner(double cap, double totalUsedMillis, double ownerUsedMillis, int ownerCount) {
        if (!Double.isFinite(cap) || !Double.isFinite(totalUsedMillis) || !Double.isFinite(ownerUsedMillis)
                || cap <= 0.0 || ownerCount <= 0) return 0.0;
        double globalRemaining = remainingMillisFor(cap, totalUsedMillis);
        double ownerRemaining = remainingMillisFor(cap / ownerCount, ownerUsedMillis);
        return Math.min(globalRemaining, ownerRemaining);
    }

    static int ownerShareFor(int cap, long tick, Owner owner) {
        if (cap <= 0) return 0;
        int ownerCount = Owner.values().length;
        int base = cap / ownerCount;
        int remainder = cap % ownerCount;
        int firstRemainderOwner = Math.floorMod((int) Math.floorMod(tick, ownerCount), ownerCount);
        int rank = Math.floorMod(owner.ordinal() - firstRemainderOwner, ownerCount);
        return base + (rank < remainder ? 1 : 0);
    }

    private static TickBudget budget(MinecraftServer server, long tick) {
        TickBudget budget = BUDGETS.computeIfAbsent(server, ignored -> new TickBudget());
        budget.begin(tick);
        return budget;
    }

    private static final class TickBudget {
        long tick = Long.MIN_VALUE;
        int used;
        int overrun;
        double usedMillis;
        final int[] usedByOwner = new int[Owner.values().length];
        final double[] usedMillisByOwner = new double[Owner.values().length];

        void begin(long currentTick) {
            if (tick == currentTick) return;
            tick = currentTick;
            used = 0;
            overrun = 0;
            usedMillis = 0.0;
            java.util.Arrays.fill(usedByOwner, 0);
            java.util.Arrays.fill(usedMillisByOwner, 0.0);
        }

        int used(Owner owner) { return usedByOwner[owner.ordinal()]; }
        double usedMillis(Owner owner) { return usedMillisByOwner[owner.ordinal()]; }

        void record(Owner owner, int count, int cap, long currentTick) {
            int share = ownerShareFor(cap, currentTick, owner);
            long ownerNext = (long) usedByOwner[owner.ordinal()] + count;
            long totalNext = (long) used + count;
            int excess = (int) Math.min(Integer.MAX_VALUE, Math.max(ownerNext - share, totalNext - cap));
            if (excess > overrun) overrun = excess;
            usedByOwner[owner.ordinal()] = (int) Math.min(Integer.MAX_VALUE, ownerNext);
            used = (int) Math.min(Integer.MAX_VALUE, totalNext);
        }
    }
}
