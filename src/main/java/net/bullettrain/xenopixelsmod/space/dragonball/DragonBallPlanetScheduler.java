package net.bullettrain.xenopixelsmod.space.dragonball;

import net.bullettrain.xenopixelsmod.space.physics.SpaceActivationBudget;

import java.util.Arrays;

/**
 * Owns when the seven planet-form Super Dragon Balls may attempt a build, and when the manager is allowed
 * to wake up again.
 *
 * <p>It deliberately holds no Sable, Minecraft or ledger reference: it consumes a {@link DragonBallBodyPlan.Plan}
 * and reports outcomes, so the whole cadence policy is testable without a running server. The manager keeps
 * authority over scanning, building and binding; this class only decides ordering and delay.
 *
 * <p>Two rules here exist because of measurements, not taste:
 * <ul>
 *     <li>a denied or failed build parks at {@link SpaceActivationBudget#denialRetryTick(long)} and NOT at the
 *     scan interval. Measured on the current gate (channel SB-1.6): with a 10-tick chart/asteroid poller, an
 *     owner that re-asks only every 40 ticks received zero activations in 2,000 ticks. Parking at the denial
 *     cadence is what keeps SDB in the rotation at all.</li>
 *     <li>a {@code WAIT_FOR_SABLE} slot never escalates into a create on a timer. The saved incarnation may
 *     simply not be streamed yet, and building a second body for that slot would strand the first, so waiting
 *     is unbounded by design and only a plan that actually says {@code CREATE} can queue work.</li>
 * </ul>
 */
public final class DragonBallPlanetScheduler {
    /** What one slot is waiting for. */
    public enum Phase {
        /** No work outstanding: the body exists, was adopted, or the slot conflicts. */
        IDLE,
        /** The ledger binds a body that Sable has not loaded yet; rescan and keep waiting. */
        WAITING_FOR_LOAD,
        /** A create is authorized and queued for the shared activation gate. */
        QUEUED,
        /** This slot holds the single in-flight build attempt. */
        BUILDING
    }

    private final int slots;
    private final int scanIntervalTicks;
    private final Phase[] phase;
    private final long[] nextActionTick;
    private final int[] buildFailures;
    /** Slot that last received an attempt; zero means none yet, so the first scan begins at slot 1. */
    private int lastTaken;
    private int inFlight = -1;

    /**
     * @param slots             slot count, which the caller takes from its ledger size
     * @param scanIntervalTicks how often the manager rescans Sable and the ledger when nothing is urgent
     * @throws IllegalArgumentException when either value is outside its usable range
     */
    public DragonBallPlanetScheduler(int slots, int scanIntervalTicks) {
        if (slots < 1) throw new IllegalArgumentException("SDB slot count must be >= 1, got " + slots);
        if (scanIntervalTicks < 1)
            throw new IllegalArgumentException("SDB scan interval must be >= 1 tick, got " + scanIntervalTicks);
        this.slots = slots;
        this.scanIntervalTicks = scanIntervalTicks;
        this.phase = new Phase[slots + 1];
        this.nextActionTick = new long[slots + 1];
        this.buildFailures = new int[slots + 1];
        Arrays.fill(phase, Phase.IDLE);
        Arrays.fill(nextActionTick, Long.MIN_VALUE);
    }

    /**
     * Folds one reconciliation pass into the schedule. Idempotent for repeated passes: an already queued slot
     * keeps its backoff, so a rescanning manager cannot speed up a failing build.
     *
     * <p>A slot whose build is in flight is never re-tagged here. The manager owns that job; letting a stale
     * plan cancel it would leave a finished body with no owner.
     *
     * @throws IllegalArgumentException when the plan names a slot outside this scheduler's range
     */
    public void reconcile(DragonBallBodyPlan.Plan plan, long gameTime) {
        if (plan == null) throw new IllegalArgumentException("SDB plan is required");
        if (plan.overall() != DragonBallBodyPlan.Overall.PROCEED) {
            // Fail closed: a blocked or unscanned cycle authorizes nothing, but the work is not abandoned,
            // so pending intents keep their slot and simply come back at the scan cadence.
            for (int slot = 1; slot <= slots; slot++)
                if (phase[slot] == Phase.WAITING_FOR_LOAD || phase[slot] == Phase.QUEUED)
                    nextActionTick[slot] = SpaceActivationBudget.tickAfter(gameTime, scanIntervalTicks);
            return;
        }
        for (DragonBallBodyPlan.Entry entry : plan.entries()) {
            int slot = entry.slot();
            if (slot < 1 || slot > slots)
                throw new IllegalArgumentException("SDB plan slot " + slot + " is outside 1.." + slots);
            if (phase[slot] == Phase.BUILDING) continue;
            switch (entry.action()) {
                case CREATE -> {
                    if (phase[slot] != Phase.QUEUED) {
                        phase[slot] = Phase.QUEUED;
                        nextActionTick[slot] = gameTime;
                    }
                }
                case WAIT_FOR_SABLE -> {
                    phase[slot] = Phase.WAITING_FOR_LOAD;
                    nextActionTick[slot] = SpaceActivationBudget.tickAfter(gameTime, scanIntervalTicks);
                }
                case EXISTING, ADOPT -> {
                    phase[slot] = Phase.IDLE;
                    nextActionTick[slot] = gameTime;
                    buildFailures[slot] = 0;
                }
                case DEFERRED_NO_ASSIGNMENT -> {
                    // An absent/invalid chart assignment is not a build error. Leave the slot idle; the
                    // manager's bounded rescan will see a later datapack publication without queue churn.
                    phase[slot] = Phase.IDLE;
                    nextActionTick[slot] = gameTime;
                    buildFailures[slot] = 0;
                }
                case CONFLICT -> {
                    // No create is authorized against a disagreement. The deadline still has to move: this slot
                    // can stay WAITING_FOR_LOAD, and pinning it to "now" would make every pass due immediately.
                    if (phase[slot] == Phase.QUEUED) phase[slot] = Phase.IDLE;
                    nextActionTick[slot] = SpaceActivationBudget.tickAfter(gameTime, scanIntervalTicks);
                }
            }
        }
    }

    /**
     * Hands out the single permitted build attempt, or {@code null} when nothing may start. Only one body is
     * under construction at a time so the shared voxel budget is not multiplied by seven.
     *
     * <p>Attempts resume after the slot that was last handed out instead of always scanning from slot 1. A
     * failed build parks for only the gate cadence, so a lowest-slot-first scan would hand every attempt back
     * to the same broken slot and starve the other six indefinitely. Rotation keeps each queued slot reachable
     * while staying deterministic: a fresh scheduler starts from slot 1 like before.
     */
    public Integer takeCreateAttempt(long gameTime) {
        if (inFlight >= 0) return null;
        for (int offset = 1; offset <= slots; offset++) {
            int slot = wrap(lastTaken + offset);
            if (phase[slot] == Phase.QUEUED && gameTime >= nextActionTick[slot]) {
                lastTaken = slot;
                phase[slot] = Phase.BUILDING;
                inFlight = slot;
                return slot;
            }
        }
        return null;
    }

    /** The slot currently holding the in-flight attempt, or {@code null}. */
    public Integer inFlightSlot() { return inFlight < 0 ? null : inFlight; }

    /** The activation gate refused this attempt: stay queued, but re-ask at the gate cadence. */
    public void onGateDenied(long gameTime) {
        requireInFlight();
        phase[inFlight] = Phase.QUEUED;
        nextActionTick[inFlight] = SpaceActivationBudget.denialRetryTick(gameTime);
        inFlight = -1;
    }

    /** The build errored or found no free plot: retry at the same cadence and keep counting for diagnostics. */
    public void onBuildFailed(long gameTime) {
        requireInFlight();
        buildFailures[inFlight]++;
        phase[inFlight] = Phase.QUEUED;
        nextActionTick[inFlight] = SpaceActivationBudget.denialRetryTick(gameTime);
        inFlight = -1;
    }

    /** The body exists: the next rescan should report {@code EXISTING} and nothing further is queued. */
    public void onBuildSucceeded(long gameTime) {
        requireInFlight();
        phase[inFlight] = Phase.IDLE;
        buildFailures[inFlight] = 0;
        nextActionTick[inFlight] = gameTime;
        inFlight = -1;
    }

    /**
     * The earliest tick the manager has to be awake for anything: either a queued slot becoming due for a build
     * attempt, or the tag and ledger rescan. This is a wake deadline, not a rescan instruction. A due queued
     * slot goes through the cheap {@link #takeCreateAttempt(long)} and activation-gate path, and the Sable scan
     * keeps its own {@code scanIntervalTicks} cadence, so an overdue queue must never turn the scan into a
     * per-tick pass.
     *
     * <p>Always capped by the scan cadence, because a new Sable body can stream in, or a datapack assignment
     * change, while this scheduler holds no pending work.
     *
     * <p>An in-flight slot is skipped: a multi-batch build is advanced by the manager stepping its own job, so
     * reporting it here would mean "wake now" every tick for the whole duration of one build.
     */
    public long nextWakeTick(long gameTime) {
        long wake = SpaceActivationBudget.tickAfter(gameTime, scanIntervalTicks);
        for (int slot = 1; slot <= slots; slot++) {
            if (phase[slot] == Phase.IDLE || phase[slot] == Phase.BUILDING) continue;
            long due = Math.max(gameTime, nextActionTick[slot]);
            if (due < wake) wake = due;
        }
        return wake;
    }

    public Phase phase(int slot) { return phase[requireRange(slot, "phase")]; }

    public long nextActionTick(int slot) { return nextActionTick[requireRange(slot, "deadline")]; }

    /** Consecutive failed attempts for one slot, reset by a success or by the slot becoming {@code EXISTING}. */
    public int buildFailures(int slot) { return buildFailures[requireRange(slot, "failure count")]; }

    /** Slots waiting for the gate, including any parked after a denial. */
    public int queuedCreates() {
        int count = 0;
        for (int slot = 1; slot <= slots; slot++) if (phase[slot] == Phase.QUEUED) count++;
        return count;
    }

    /** Slots waiting on Sable to load a body the ledger already binds. */
    public int waitingForLoad() {
        int count = 0;
        for (int slot = 1; slot <= slots; slot++) if (phase[slot] == Phase.WAITING_FOR_LOAD) count++;
        return count;
    }

    private void requireInFlight() {
        if (inFlight < 0) throw new IllegalStateException("SDB scheduler has no in-flight build attempt");
    }

    /** Wraps a one-based slot back into {@code 1..slots}. */
    private int wrap(int slot) {
        int wrapped = slot % slots;
        return wrapped == 0 ? slots : wrapped;
    }

    private int requireRange(int slot, String what) {
        if (slot < 1 || slot > slots)
            throw new IllegalArgumentException("SDB slot " + slot + " is outside 1.." + slots + " for " + what);
        return slot;
    }
}
