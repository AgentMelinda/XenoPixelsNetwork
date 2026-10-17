package net.bullettrain.xenopixelsmod.space;

/**
 * Which passes run this tick, and in what order, under a work budget.
 *
 * <p>Pure planning with no Sable and no Minecraft types, so the scheduling policy is unit-testable on
 * its own — the same split {@code V3Motion} uses between its {@code Lease} and the entity-touching
 * statics, and {@code FreezeOwnership} between {@code Hold} and its callers.
 *
 * <p><b>The clock is supplied, never internal.</b> Sable invokes its physics-tick listeners once per
 * {@code SubLevelPhysicsSystem} per step — measured at 18 invocations per game tick on a nine-dimension
 * server ({@code ai/evidence/space-perf-s5-2026-10-14}) — so a counter that advances per invocation
 * makes every configured interval fire ~18x too often and turns "every 10 ticks" into "every half
 * tick". Taking {@code getGameTime()} as an argument instead makes an interval mean game ticks, and
 * makes the repeated invocations within one tick free: a pass that just ran is simply not due again.
 * It also means a pass that declined to run for lack of an applicable system stays due for the one it
 * does apply to, because only executions are recorded.
 *
 * <p>Two policies matter and both are easy to get subtly wrong:
 *
 * <p><b>Interval throttling.</b> A pass declares how many ticks it wants between runs. Gravity
 * field summation does not need to run every tick — the existing {@code OrbitalGravitySystem} already
 * throttles itself to one pass in twenty for exactly this reason — so the scheduler holds that
 * bookkeeping centrally instead of every pass keeping its own private counter.
 *
 * <p><b>Round-robin deferral without starvation.</b> When the budget runs out mid-tick, the passes
 * that did not get to run must go to the <i>front</i> of the next tick's order, not the back. Otherwise
 * a cheap pass registered early runs every tick and an expensive one registered late never runs at all,
 * which would look exactly like a silently disabled system — the failure mode this repo has already
 * been bitten by twice, with a mixin that quietly stopped applying and a gravity handler that quietly
 * stopped being ours.
 *
 * <p>Allocation-free: callers pass in the {@code int[]} to fill.
 */
public final class TickBudget {

    /** Sentinel meaning "never ran". A long, because so is the clock. */
    static final long NEVER_RUN = Long.MIN_VALUE / 4;

    private final int passCount;
    private final int[] interval;
    private final long[] lastRunTick;
    private int cursor;

    /**
     * @param passCount number of registered passes; must be &gt;= 0
     * @param intervals ticks between runs per pass. An entry &lt;= 1 means "every tick". The array is
     *                  copied, so the caller may reuse or mutate its own afterwards.
     */
    public TickBudget(int passCount, int[] intervals) {
        this.passCount = Math.max(0, passCount);
        this.interval = new int[this.passCount];
        this.lastRunTick = new long[this.passCount];
        if (intervals != null) {
            for (int i = 0; i < this.passCount; i++) {
                this.interval[i] = i < intervals.length ? Math.max(1, intervals[i]) : 1;
            }
        } else {
            java.util.Arrays.fill(this.interval, 1);
        }
        // Start far enough in the past that every pass is due on the very first clock value. An int
        // here saturates at 2^31, after which every pass looks perpetually due and the configured
        // intervals silently stop meaning anything.
        java.util.Arrays.fill(this.lastRunTick, NEVER_RUN);
    }

    /**
     * Fills {@code orderOut} with the indices of passes due at {@code clock}, most-overdue first,
     * starting from the round-robin cursor so a deferred pass leads the next tick.
     *
     * @param orderOut caller-owned buffer, at least {@link #passCount()} long
     * @return how many entries were written
     */
    public int planDue(long clock, int[] orderOut) {
        if (orderOut == null) return 0;
        int written = 0;
        for (int step = 0; step < passCount; step++) {
            int index = (cursor + step) % passCount;
            if (isDue(index, clock)) {
                if (written < orderOut.length) orderOut[written++] = index;
            }
        }
        return written;
    }

    /** True when {@code index}'s interval has elapsed as of {@code clock}. */
    public boolean isDue(int index, long clock) {
        if (index < 0 || index >= passCount) return false;
        // The NEVER_RUN sentinel keeps this arithmetic overflow-safe on the first tick.
        return clock - lastRunTick[index] >= interval[index];
    }

    /** When {@code index} last executed, or {@link #NEVER_RUN} if it never has. */
    public long lastRunTick(int index) {
        return index >= 0 && index < passCount ? lastRunTick[index] : NEVER_RUN;
    }

    /**
     * Records that the first {@code ranCount} entries of {@code order} actually executed at
     * {@code clock}, and moves the cursor so whatever was deferred leads next time.
     *
     * <p>Passes that were due but did not execute — deferred by the work budget, or declined because no
     * system applies to them — must not be in the leading block, because they are not marked as run and
     * stay due. Calling this repeatedly with the same clock is what the ~18 invocations per tick do, and
     * it is idempotent for those: nothing becomes due a second time within one tick.
     *
     * @param clock     the same game time {@link #planDue} was asked about
     * @param order     the same buffer {@link #planDue} filled
     * @param ranCount  how many leading entries completed; entries at or beyond this were deferred
     */
    public void commit(long clock, int[] order, int ranCount) {
        int ran = Math.max(0, Math.min(ranCount, passCount));
        if (order != null) {
            for (int i = 0; i < ran; i++) {
                int index = order[i];
                if (index >= 0 && index < passCount) lastRunTick[index] = clock;
            }
            if (ran < passCount && order.length > ran) {
                // Point the cursor at the first deferred pass rather than past the last one that ran.
                int next = order[ran];
                if (next >= 0 && next < passCount) cursor = next;
            }
        }
    }

    /** Changes a pass's interval, for a config reload. Takes effect on the next due evaluation. */
    public void setInterval(int index, int ticks) {
        if (index >= 0 && index < passCount) interval[index] = Math.max(1, ticks);
    }

    public int intervalOf(int index) {
        return index >= 0 && index < passCount ? interval[index] : 1;
    }

    public int passCount() {
        return passCount;
    }

    /** Cursor position, for tests that assert deferral rather than starvation. */
    int cursor() {
        return cursor;
    }

    /** Ticks since a pass last ran; exposed for {@code /xenospace perf}. */
    public int ticksSinceRun(int index, long clock) {
        if (index < 0 || index >= passCount) return 0;
        long delta = clock - lastRunTick[index];
        return delta > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) delta;
    }
}
