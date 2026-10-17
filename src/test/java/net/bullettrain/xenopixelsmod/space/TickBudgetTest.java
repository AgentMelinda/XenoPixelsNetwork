package net.bullettrain.xenopixelsmod.space;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * The scheduling policy behind the single budgeted physics pass.
 *
 * <p>Three failure modes are worth testing for explicitly, because all three are invisible in play and
 * all three have precedent in this repo:
 *
 * <ul>
 *   <li><b>A pass that silently never runs.</b> This mod shipped a mixin that quietly stopped applying
 *       for long enough that gravity ownership was not what anyone thought it was. A deferred pass that
 *       never reaches the front of the queue is the same bug wearing a different hat, so the
 *       round-robin guarantee gets its own test.</li>
 *   <li><b>An interval that elapses too often.</b> The clock is supplied rather than counted, because
 *       Sable invokes the callback ~18 times per game tick ({@code ai/evidence/space-perf-s5-2026-10-14})
 *       and a counter advanced per invocation turned {@code lodIntervalTicks=10} into "every half tick".
 *       The same-clock and declined-pass tests below are what pin that shut.</li>
 *   <li><b>A clock that saturates.</b> An {@code int} tick counter reaches 2^31, after which every pass
 *       looks perpetually due and the configured intervals stop meaning anything.</li>
 * </ul>
 */
class TickBudgetTest {

    private static int[] intervals(int... values) {
        return values;
    }

    @Test void everyPassIsDueOnTheFirstClockValue() {
        TickBudget budget = new TickBudget(3, intervals(20, 20, 20));
        int[] order = new int[3];
        assertEquals(3, budget.planDue(0L, order), "nothing has run yet, so all three are overdue");
        assertArrayEquals(new int[]{0, 1, 2}, order);
        assertEquals(TickBudget.NEVER_RUN, budget.lastRunTick(0), "and none of them has a run time yet");
    }

    @Test void anIntervalKeepsAPassFromRunningEveryTick() {
        TickBudget budget = new TickBudget(1, intervals(20));
        int[] order = new int[1];

        assertEquals(1, budget.planDue(100L, order));
        budget.commit(100L, order, 1);
        // Nineteen game ticks of nothing due.
        for (long tick = 101L; tick < 120L; tick++) {
            assertEquals(0, budget.planDue(tick, order), "must not be due again on game tick " + tick);
        }
        assertEquals(1, budget.planDue(120L, order), "due again exactly one interval later");
    }

    /**
     * The 18x bug, in one test. Sable calls every registered system many times per game tick; with a
     * supplied clock each pass executes once per interval and the other seventeen invocations cost
     * nothing. Before the fix these three counts were 720, 72 and 36.
     */
    @Test void repeatedInvocationsAtOneClockRunAPassOncePerIntervalNotPerInvocation() {
        TickBudget budget = new TickBudget(3, intervals(1, 10, 20));
        int[] order = new int[3];
        int[] executions = new int[3];
        for (long tick = 0L; tick < 40L; tick++) {
            for (int invocation = 0; invocation < 18; invocation++) {
                int due = budget.planDue(tick, order);
                for (int i = 0; i < due; i++) executions[order[i]]++;
                budget.commit(tick, order, due);
            }
        }
        assertEquals(40, executions[0], "an every-tick pass runs once per game tick");
        assertEquals(4, executions[1], "a 10-tick pass runs once per interval, not once per invocation");
        assertEquals(2, executions[2], "a 20-tick pass likewise");
    }

    /**
     * A pass that the current system declines is not an execution, so it must still be due at the same
     * clock for the system it does apply to. Getting this wrong is how an orbit-only pass would end up
     * running on whichever dimension happened to tick first.
     */
    @Test void aDeclinedPassStaysDueForTheSystemThatWantsIt() {
        TickBudget budget = new TickBudget(2, intervals(1, 1));
        int[] order = new int[2];
        assertEquals(2, budget.planDue(500L, order));

        // The scheduler partitions so the applicable pass leads, and counts executions only.
        order[0] = 1;
        order[1] = 0;
        budget.commit(500L, order, 1);

        assertEquals(500L, budget.lastRunTick(1), "the pass that ran is marked");
        assertEquals(TickBudget.NEVER_RUN, budget.lastRunTick(0), "the pass that declined is not");
        int[] again = new int[2];
        assertEquals(1, budget.planDue(500L, again), "one pass is still due at this same clock");
        assertEquals(0, again[0], "and it is the one that declined, for the system that applies to it");
    }

    /**
     * Each dimension loads its own saved game time, so two physics systems can report different clocks.
     * The newest execution has to win: a lagging dimension must never re-trigger work the leading one
     * already did, and it must never fall out of step permanently.
     */
    @Test void anOlderClockCannotMakeARecentExecutionDueAgain() {
        TickBudget budget = new TickBudget(1, intervals(1));
        int[] order = new int[1];
        assertEquals(1, budget.planDue(1_000L, order));
        budget.commit(1_000L, order, 1);

        assertEquals(0, budget.planDue(999L, order), "a lagging dimension must not re-run a pass");
        assertEquals(0, budget.planDue(1_000L, order), "nor may the leading one run it twice");
        assertEquals(1, budget.planDue(1_001L, order), "and the leading clock still moves one per tick");
    }

    /** The round-robin cursor is the only thing that decides who is tried first; it must lead with the deferred pass. */
    @Test void deferredPassesLeadTheNextTickInsteadOfStarving() {
        TickBudget budget = new TickBudget(3, intervals(1, 1, 1));
        int[] order = new int[3];
        int[] ranTotal = new int[3];

        for (long tick = 0L; tick < 9L; tick++) {
            assertEquals(3, budget.planDue(tick, order));
            // Work budget of exactly one pass this tick; the other two stay due.
            ranTotal[order[0]]++;
            budget.commit(tick, order, 1);
            if (tick == 0L) assertEquals(order[1], budget.cursor(),
                    "the cursor leads with what was deferred, not past what ran");
        }
        assertEquals(3, ranTotal[0], "pass 0 ran " + ranTotal[0] + " times");
        assertEquals(3, ranTotal[1], "pass 1 ran " + ranTotal[1] + " times");
        assertEquals(3, ranTotal[2], "pass 2 ran " + ranTotal[2] + " times");
    }

    /** Committing an empty block must not silently push a pass's run time forward, or its interval never elapses. */
    @Test void commitWithNoExecutionsChangesNothing() {
        TickBudget budget = new TickBudget(1, intervals(5));
        int[] order = new int[1];
        assertEquals(1, budget.planDue(100L, order));
        budget.commit(100L, order, 1);
        assertEquals(0, budget.planDue(101L, order));
        budget.commit(101L, order, 0);
        assertEquals(100L, budget.lastRunTick(0), "an empty commit must not re-stamp the run time");
        assertEquals(1, budget.planDue(105L, order), "the interval still elapses on schedule");
    }

    @Test void mixedIntervalsEachFireOnTheirOwnCadence() {
        TickBudget budget = new TickBudget(3, intervals(1, 5, 20));
        int[] order = new int[3];
        int[] ran = new int[3];
        for (long tick = 0L; tick < 20L; tick++) {
            int due = budget.planDue(tick, order);
            for (int i = 0; i < due; i++) ran[order[i]]++;
            budget.commit(tick, order, due);
        }
        assertEquals(20, ran[0], "an every-tick pass runs every tick");
        assertEquals(4, ran[1], "a 5-tick pass runs 4 times in 20 ticks");
        assertEquals(1, ran[2], "a 20-tick pass runs once, on the first tick");
    }

    /** The saturation case: a long-running server passes 2^31 and the intervals have to keep meaning ticks. */
    @Test void aClockPastTwoToTheThirtyOneStillHonoursItsInterval() {
        TickBudget budget = new TickBudget(1, intervals(20));
        int[] order = new int[1];
        long clock = 1L << 32;
        assertEquals(1, budget.planDue(clock, order), "overdue, however long the server has been up");
        budget.commit(clock, order, 1);
        for (long tick = clock + 1L; tick < clock + 20L; tick++) {
            assertEquals(0, budget.planDue(tick, order), "throttled on tick " + (tick - clock) + " after running");
        }
        assertEquals(1, budget.planDue(clock + 20L, order), "due again exactly one interval later");
    }

    @Test void anIntervalCanBeChangedForAConfigReload() {
        TickBudget budget = new TickBudget(1, intervals(20));
        assertEquals(20, budget.intervalOf(0));
        budget.setInterval(0, 1);
        assertEquals(1, budget.intervalOf(0));
        // Out-of-range indices are ignored rather than throwing on a bad config.
        budget.setInterval(5, 10);
        assertEquals(1, budget.intervalOf(5), "an unknown slot keeps the safe default");
        budget.setInterval(0, 0);
        assertEquals(1, budget.intervalOf(0), "an interval below one is clamped, never zero");

        // The reload takes effect on the next evaluation, not by resetting anything.
        int[] order = new int[1];
        budget.setInterval(0, 10);
        assertEquals(1, budget.planDue(1_000L, order));
        budget.commit(1_000L, order, 1);
        assertEquals(0, budget.planDue(1_005L, order), "the new interval is what the next clock is compared to");
        assertEquals(1, budget.planDue(1_010L, order));
    }

    @Test void ticksSinceRunIsReportedAgainstTheSuppliedClock() {
        TickBudget budget = new TickBudget(1, intervals(10));
        int[] order = new int[1];
        assertEquals(1, budget.planDue(1_000L, order));
        budget.commit(1_000L, order, 1);
        assertEquals(0, budget.ticksSinceRun(0, 1_000L), "it ran on this clock");
        assertEquals(5, budget.ticksSinceRun(0, 1_005L));
        assertEquals(0, budget.ticksSinceRun(99, 1_005L), "an unknown slot reports zero rather than throwing");

        // A pass that never ran sits at the sentinel, so its age against a real clock is astronomical;
        // the diagnostic has to saturate rather than wrap into a negative number.
        TickBudget neverRun = new TickBudget(1, intervals(10));
        assertEquals(Integer.MAX_VALUE, neverRun.ticksSinceRun(0, 1L << 33),
                "a pass that never ran reports a saturated age");
    }

    @Test void anEmptyPassSetPlansNothingAndNeverThrows() {
        TickBudget budget = new TickBudget(0, null);
        int[] order = new int[0];
        assertEquals(0, budget.planDue(1L, order));
        budget.commit(1L, order, 0);
        assertFalse(budget.isDue(0, 1L));
        assertEquals(0, budget.passCount());
        assertEquals(TickBudget.NEVER_RUN, budget.lastRunTick(0), "with no slots at all, nothing has a run time");
    }

    @Test void aShortIntervalArrayDefaultsTheMissingEntriesToEveryTick() {
        TickBudget budget = new TickBudget(3, intervals(10));
        assertEquals(10, budget.intervalOf(0));
        assertEquals(1, budget.intervalOf(1), "an unlisted pass must default to every tick");
        assertEquals(1, budget.intervalOf(2));
    }
}
