package net.bullettrain.xenopixelsmod.space;

import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.system.SubLevelPhysicsSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.config.XenoSpaceConfig;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One Sable physics-tick callback for every space system, instead of one per system.
 *
 * <p>The mod already registers <b>seven</b> independent {@code onPhysicsTick} callbacks —
 * {@code ShipGravityControl}, {@code XenoThrusterControl}, {@code ShipBallisticController},
 * {@code AeroStabilizerSystem}, {@code AeroControlSurfaceTorque}, {@code AeroAeroForceSystem} and
 * {@code OrbitalGravitySystem}. Each one walks {@code SubLevelContainer.getAllSubLevels()} on its own,
 * keeps its own {@code ConcurrentHashMap} of per-ship state, and runs its own staleness sweep on its
 * own schedule — 30 s/300 s in one, 30 s/60 s in another, so a disassembled ship is forgotten at
 * different times by different systems. Adding the Voidsea's gravity field, LOD ladder, anchor
 * pre-warming and pod drag as four more of those would mean eleven full sub-level walks per physics
 * tick.
 *
 * <p>This scheduler is the alternative, and it is the single biggest performance lever in the space
 * groundwork: one walk, one budget, one set of timings. New space systems register a {@link Pass} here
 * and never touch {@code SableEventPlatform} themselves. §13's Q21 asks for exactly this — "per-tick
 * drag/downforce math on potentially hundreds of pods needs to fold into the existing Sable
 * physics-tick pass rather than spawning a new event listener".
 *
 * <p>The seven legacy callbacks are deliberately <b>left alone</b>. They work, ships fly with them
 * today, and rewriting all of them in the same pass as introducing the scheduler would make any
 * regression impossible to attribute. They migrate onto this later, one at a time.
 *
 * <p>State is published as one immutable {@link Snapshot} behind a single volatile, so a tick reads it
 * once and sees a consistent set of passes, budget and scratch regardless of concurrent registration.
 * Registration is rare (mod setup, config reload) and takes a lock; the tick path takes none and
 * allocates nothing.
 */
@EventBusSubscriber(modid = XenoPixelsMod.MOD_ID)
public final class SpaceTickScheduler {

    /**
     * One unit of per-physics-tick space work.
     *
     * <p>Implementations run inside Sable's physics tick, so they may apply impulses through
     * {@code system.getPhysicsHandle(subLevel)} and must follow the body-space convention the existing
     * systems use — {@code subLevel.logicalPose().orientation().transformInverse(impulse)} before
     * {@code applyLinearImpulse}, because Sable's impulse API takes body-space vectors.
     */
    public interface Pass {
        /** Short stable name, used in {@code /xenospace perf} output. */
        String name();

        /**
         * Physics ticks between runs. One means every tick. Gravity field summation wants twenty, the
         * same interval {@code OrbitalGravitySystem} already throttles itself to, because a field that
         * changes only when a ship changes body does not need re-summing 20 times a second.
         *
         * <p>The unit is a <b>game tick</b>, not an invocation of this scheduler: Sable calls the
         * callback once per physics system per step, so the alternative would make a configured interval
         * of ten fire every half tick.
         */
        default int intervalTicks() {
            return 1;
        }

        /**
         * Whether this system is the one this pass wants to run on.
         *
         * <p>The scheduler fans out to every {@link SubLevelPhysicsSystem} on the server — nine on the
         * capture world, each stepped twice a tick — while most space passes have exactly one host
         * dimension. Declining here rather than inside {@link #run} is what lets a pass be invoked 18
         * times a tick and still execute once, because a declined pass is never marked as having run and
         * stays due for the system it does apply to.
         */
        default boolean appliesTo(SubLevelPhysicsSystem system) {
            return true;
        }

        /**
         * Does the work.
         *
         * @return work units consumed — typically the number of sub-levels, ships or pods actually
         *         touched — so the scheduler can stop early and defer the rest. Returning zero is
         *         legal for a pass that is not worth budgeting.
         */
        int run(SubLevelPhysicsSystem system, double deltaSeconds);
    }

    /**
     * Everything a tick needs, swapped atomically on registration. The scratch arrays live here so the
     * tick path never allocates and never sees a budget sized for a different set of passes.
     * {@code deferred} is the staging buffer the applicability partition in {@link #physicsTick} swaps
     * through; it is never read outside that method.
     */
    private record Snapshot(Pass[] passes, TickBudget budget, SpacePerfProbe probe, int[] order,
                            int[] deferred) {
        static final Snapshot EMPTY =
                new Snapshot(new Pass[0], new TickBudget(0, null), new SpacePerfProbe(0),
                        new int[0], new int[0]);
    }

    private static final AtomicBoolean REGISTERED = new AtomicBoolean();
    private static final Object LOCK = new Object();
    private static volatile Snapshot snapshot = Snapshot.EMPTY;

    /** Passes that threw, reported once each so a per-tick failure cannot spam the log. */
    private static final java.util.Set<String> REPORTED_FAILURES =
            java.util.concurrent.ConcurrentHashMap.newKeySet();

    private SpaceTickScheduler() {
    }

    /**
     * Installs the single physics-tick callback. Safe to call repeatedly and from any thread; the
     * first caller wins. Matches the {@code ensureRegistered()} convention every other Sable system in
     * this mod uses, and is wired from {@code XenoPixelsMod#commonSetup} alongside them.
     */
    public static void ensureRegistered() {
        if (!REGISTERED.compareAndSet(false, true)) return;
        SableEventPlatform.INSTANCE.onPhysicsTick(SpaceTickScheduler::physicsTick);
        XenoPixelsMod.LOGGER.info("Registered space tick scheduler (single budgeted physics pass)");
    }

    /** Adds a pass and re-registers the callback if this is the first one. */
    public static void register(Pass pass) {
        if (pass == null || pass.name() == null) return;
        synchronized (LOCK) {
            List<Pass> next = new ArrayList<>(List.of(snapshot.passes()));
            if (next.stream().anyMatch(existing -> existing == pass)) return;
            next.add(pass);
            publish(next);
        }
        ensureRegistered();
    }

    /** Removes a pass. Idempotent, and safe to call for a pass that was never registered. */
    public static void unregister(Pass pass) {
        if (pass == null) return;
        synchronized (LOCK) {
            List<Pass> next = new ArrayList<>(List.of(snapshot.passes()));
            if (!next.remove(pass)) return;
            publish(next);
        }
    }

    /** Drops every pass. Called on server stop so nothing retains sub-level state across a restart. */
    public static void clearAll() {
        synchronized (LOCK) {
            if (snapshot.passes().length == 0) return;
            publish(List.of());
        }
        REPORTED_FAILURES.clear();
    }

    /**
     * Guarantees the invariant the passes rely on: after a stop there is no registration, no cached
     * failure and no budget state left over from the old server. Each pass also unregisters on its own,
     * so this is the backstop that keeps {@code passCount() == 0} true even for a pass added later
     * without a stop handler of its own.
     */
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        clearAll();
    }

    private static void publish(List<Pass> passes) {
        int count = passes.size();
        Pass[] array = passes.toArray(new Pass[0]);
        int[] intervals = new int[count];
        SpacePerfProbe probe = new SpacePerfProbe(count);
        for (int i = 0; i < count; i++) {
            intervals[i] = Math.max(1, array[i].intervalTicks());
            probe.name(i, array[i].name());
        }
        snapshot = new Snapshot(array, new TickBudget(count, intervals), probe, new int[count],
                new int[count]);
        // A pass set that changed invalidates any "this one already broke" memory.
        REPORTED_FAILURES.clear();
    }

    /**
     * Re-reads each pass's {@link Pass#intervalTicks()} into the live budget, for a config reload that
     * changed throttling.
     *
     * <p>Deliberately does not go through {@link #publish}: a fresh {@link TickBudget} or
     * {@link SpacePerfProbe} would zero every counter, and an operator reloading mid-session would read
     * {@code runs=0} and conclude the passes had died. The new interval is compared on the next due
     * evaluation, so a pass whose interval grew keeps the run time it already has rather than waiting an
     * entire interval again.
     */
    public static void applyIntervals() {
        Snapshot state = snapshot;
        Pass[] passes = state.passes();
        TickBudget budget = state.budget();
        for (int i = 0; i < passes.length; i++) budget.setInterval(i, Math.max(1, passes[i].intervalTicks()));
    }

    /**
     * The one callback Sable sees. Invoked once per {@link SubLevelPhysicsSystem} per physics step —
     * measured at eighteen invocations per game tick on the nine-dimension capture world
     * ({@code ai/evidence/space-perf-s5-2026-10-14}) — so due-ness is keyed on the level's game time,
     * which is identical across those invocations. A pass that executed at this clock is simply not due
     * again until the clock moves, which is what makes {@link Pass#intervalTicks()} mean game ticks.
     */
    private static void physicsTick(SubLevelPhysicsSystem system, double deltaSeconds) {
        Snapshot state = snapshot;
        Pass[] passes = state.passes();
        if (passes.length == 0) return;
        ServerLevel level = system.getLevel();
        // Every pass reads its level and the clock comes from it; with no level this invocation has no
        // clock to be due against, so it is not the tick's.
        if (level == null) return;
        long clock = level.getGameTime();

        TickBudget budget = state.budget();
        int[] order = state.order();
        int due = budget.planDue(clock, order);
        if (due == 0) return;

        // Partition in place, applicable passes to the front: commit() marks only its leading block as
        // executed, and a pass that declined for this system must stay due for the one it applies to.
        // declined <= due <= passCount, so the staging buffer is always large enough.
        int[] deferred = state.deferred();
        int applicable = 0;
        int declined = 0;
        for (int i = 0; i < due; i++) {
            int slot = order[i];
            if (slot < 0 || slot >= passes.length) continue;
            if (passes[slot].appliesTo(system)) order[applicable++] = slot;
            else deferred[declined++] = slot;
        }
        for (int i = 0; i < declined; i++) order[applicable + i] = deferred[i];
        if (declined == 0 && applicable < order.length) {
            // Nothing was left behind, so mark the slot after the block as empty rather than letting
            // commit() read a stale index and jump the round-robin cursor somewhere arbitrary.
            order[applicable] = -1;
        }

        int workBudget = XenoSpaceConfig.workPerPhysicsTick();
        SpacePerfProbe probe = state.probe();
        int ran = 0;
        int consumed = 0;
        for (int i = 0; i < applicable; i++) {
            if (workBudget > 0 && consumed >= workBudget) break;
            int slot = order[i];
            Pass pass = passes[slot];
            long start = System.nanoTime();
            try {
                consumed += Math.max(0, pass.run(system, deltaSeconds));
            } catch (Throwable t) {
                // A space pass must never take down the physics tick. That is not a hypothetical: the
                // AeroStar gravity handler this mod replaced threw NoClassDefFoundError inside this
                // exact callback and killed the server thread every tick a sub-level existed.
                if (REPORTED_FAILURES.add(pass.name())) {
                    XenoPixelsMod.LOGGER.error("Space pass '{}' failed; further errors from it are suppressed",
                            pass.name(), t);
                }
            } finally {
                probe.record(slot, System.nanoTime() - start);
            }
            // Counted as run even on failure, so a broken pass cannot permanently occupy the front of
            // the deferred queue and starve healthy ones.
            ran++;
        }
        budget.commit(clock, order, ran);
    }

    public static int passCount() {
        return snapshot.passes().length;
    }

    /** The live probe, for {@code /xenospace perf}. Never null. */
    public static SpacePerfProbe probe() {
        return snapshot.probe();
    }

    /** Pass names in registration order, for command output. Allocates, so commands only. */
    public static List<String> passNames() {
        Pass[] passes = snapshot.passes();
        List<String> names = new ArrayList<>(passes.length);
        for (Pass pass : passes) names.add(pass.name());
        return names;
    }
}
