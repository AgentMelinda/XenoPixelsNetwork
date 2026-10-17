package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceActivationBudgetTest {
    private static final SpaceActivationBudget.Owner CHART = SpaceActivationBudget.Owner.CHART_PLANET;
    private static final SpaceActivationBudget.Owner ASTEROID = SpaceActivationBudget.Owner.ASTEROID;
    private static final SpaceActivationBudget.Owner SMALL_PLANET = SpaceActivationBudget.Owner.SMALL_PLANET;
    private static final SpaceActivationBudget.Owner SUPER_DRAGON_BALL = SpaceActivationBudget.Owner.SUPER_DRAGON_BALL;

    @Test void firstRequestFromAnyOwnerCanStartWithoutWaitingForAnIdleOwner() {
        for (var owner : SpaceActivationBudget.Owner.values()) {
            var budget = new SpaceActivationBudget();
            assertTrue(budget.tryAcquire(owner, 0), owner + " should receive the initial activation");
            assertEquals(10, budget.nextAllowedTick());
        }
    }

    @Test void eligibleOwnersRotateEvenWhenPassesCallInFixedOrder() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 100));
        assertFalse(budget.tryAcquire(CHART, 110));
        assertTrue(budget.tryAcquire(ASTEROID, 110));
        assertFalse(budget.tryAcquire(CHART, 120));
        assertFalse(budget.tryAcquire(ASTEROID, 120));
        assertTrue(budget.tryAcquire(SMALL_PLANET, 120));
        assertTrue(budget.tryAcquire(SUPER_DRAGON_BALL, 130));
        assertTrue(budget.tryAcquire(CHART, 140));
    }

    @Test void waitingNonPreferredOwnersTakeTheIdleOwnersTurnInRotatingOrder() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(CHART, 10));
        assertFalse(budget.tryAcquire(SMALL_PLANET, 10));
        assertFalse(budget.tryAcquire(CHART, 11));
        assertTrue(budget.tryAcquire(SMALL_PLANET, 11));
        assertFalse(budget.tryAcquire(CHART, 21));
        assertTrue(budget.tryAcquire(CHART, 22));
    }

    @Test void oneActiveNonPreferredOwnerUsesAnIdleTurnOnTheFollowingTick() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(SMALL_PLANET, 10));
        assertTrue(budget.tryAcquire(SMALL_PLANET, 11));
        assertEquals(21, budget.nextAllowedTick());
    }

    @Test void denialCountersIdentifyWhichOwnerWasRateLimited() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(ASTEROID, 9));
        assertEquals(1, budget.deniedRequests(ASTEROID));
        assertEquals(9, budget.lastDeniedTick(ASTEROID));
        assertTrue(budget.tryAcquire(ASTEROID, 10));
        assertFalse(budget.tryAcquire(CHART, 20));
        assertEquals(1, budget.deniedRequests(CHART));
        assertEquals(20, budget.lastDeniedTick(CHART));
        assertEquals(0, budget.deniedRequests(SMALL_PLANET));
        assertEquals(Long.MIN_VALUE, budget.lastDeniedTick(SMALL_PLANET));
    }

    @Test void aStaleDeferredOwnerCannotBlockTheCurrentActiveOwner() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(SMALL_PLANET, 10));
        assertFalse(budget.tryAcquire(CHART, 10));
        // The small-planet manager stopped asking after tick 10. Its stale waiter record must not
        // reserve the fallback turn indefinitely. Its one-interval grace preserves pass-order fairness.
        assertFalse(budget.tryAcquire(CHART, 11));
        assertFalse(budget.tryAcquire(CHART, 20));
        assertTrue(budget.tryAcquire(CHART, 21));
        assertEquals(31, budget.nextAllowedTick());
    }

    @Test void expiredDeferredOwnerMustReestablishItsWaitBeforeGettingTheNextTurn() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(SMALL_PLANET, 10));
        // The returning owner is itself the caller at 41: pruning must reset its old deferred timestamp,
        // so it cannot inherit the earlier wait and take the turn on that same poll.
        assertFalse(budget.tryAcquire(SMALL_PLANET, 41));
        assertTrue(budget.tryAcquire(SMALL_PLANET, 42));
        assertEquals(52, budget.nextAllowedTick());
    }

    @Test void chartBudgetDenialRetriesBeforeTheHundredTickPollErasesItsWaitRecord() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(ASTEROID, 90));
        assertFalse(budget.tryAcquire(CHART, 100));

        long retryTick = PhysicalEarth.nextPendingBudgetRetry(100);
        assertFalse(PhysicalEarth.pendingAttemptDue(109, 100, retryTick));
        assertTrue(PhysicalEarth.pendingAttemptDue(110, 100, retryTick));
        assertTrue(budget.tryAcquire(CHART, 110),
                "the chart caller must return while its deferred request is still fresh");
    }

    @Test void chartBudgetRetrySaturatesAtTheMaximumGameTime() {
        assertEquals(Long.MAX_VALUE, PhysicalEarth.nextPendingBudgetRetry(Long.MAX_VALUE));
    }

    @Test void sharedDenialRetryIsOneActivationIntervalAndSaturates() {
        assertEquals(110, SpaceActivationBudget.denialRetryTick(100));
        assertEquals(110, SpaceActivationBudget.nextAttemptTick(100, 1_200, true));
        assertEquals(1_300, SpaceActivationBudget.nextAttemptTick(100, 1_200, false));
        assertEquals(Long.MAX_VALUE, SpaceActivationBudget.tickAfter(Long.MAX_VALUE - 2, 10));
        assertEquals(Long.MAX_VALUE, SpaceActivationBudget.denialRetryTick(Long.MAX_VALUE - 9));
        assertEquals(Long.MAX_VALUE, SpaceActivationBudget.tickAfter(Long.MAX_VALUE, 0));
    }

    @Test void allOwnersProgressUnderUnevenPollingWhenDenialsUseTheGateRetryCadence() {
        var budget = new SpaceActivationBudget();
        var owners = SpaceActivationBudget.Owner.values();
        int[] scanIntervals = {20, 1_200, 40, 40};
        long[] nextRequest = new long[owners.length];
        int[] grants = new int[owners.length];
        long previousGrant = Long.MIN_VALUE;

        for (long tick = 0; tick <= 20_000; tick++) {
            for (var owner : owners) {
                int index = owner.ordinal();
                if (tick < nextRequest[index]) continue;
                boolean granted = budget.tryAcquire(owner, tick);
                nextRequest[index] = SpaceActivationBudget.nextAttemptTick(tick, scanIntervals[index], !granted);
                if (granted) {
                    if (previousGrant != Long.MIN_VALUE)
                        assertTrue(tick - previousGrant >= SpaceActivationBudget.MIN_INTERVAL_TICKS,
                                "global activation interval must hold for " + owner);
                    previousGrant = tick;
                    grants[index]++;
                }
            }
        }

        for (var owner : owners)
            assertTrue(grants[owner.ordinal()] > 0, owner + " must not starve under the mixed polling schedule");
        assertTrue(grants[SpaceActivationBudget.Owner.ASTEROID.ordinal()] <= 20,
                "the configured 1,200-tick ordinary cadence still applies after successful activations");
    }

    @Test void rejectsANullOwner() {
        var budget = new SpaceActivationBudget();
        assertThrows(IllegalArgumentException.class, () -> budget.tryAcquire(null, 0));
    }

    @Test void acceptsFirstActivationAtZeroAndNeverWrapsNearLongMax() {
        var budget = new SpaceActivationBudget();
        assertTrue(budget.tryAcquire(CHART, 0));
        assertFalse(budget.tryAcquire(ASTEROID, 9));
        assertTrue(budget.tryAcquire(ASTEROID, 10));

        var nearOverflow = new SpaceActivationBudget();
        assertTrue(nearOverflow.tryAcquire(ASTEROID, Long.MAX_VALUE - 20));
        assertEquals(Long.MAX_VALUE - 10, nearOverflow.nextAllowedTick());
        assertFalse(nearOverflow.tryAcquire(SMALL_PLANET, Long.MAX_VALUE - 11));
        assertTrue(nearOverflow.tryAcquire(SMALL_PLANET, Long.MAX_VALUE - 10));
        assertTrue(nearOverflow.tryAcquire(CHART, Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, nearOverflow.nextAllowedTick());
        assertFalse(nearOverflow.tryAcquire(SMALL_PLANET, Long.MAX_VALUE));
    }
}
