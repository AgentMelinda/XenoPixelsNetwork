package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceBuildWorkBudgetTest {
    @Test void exactWriterGetsAllRemainingBudgetButPlotLoadWriterReservesItsLargestAtomicGroup() {
        assertEquals(1_200, SpaceBuildWorkBudget.allowanceFor(2_000, 1_200, 201, true));
        assertEquals(999, SpaceBuildWorkBudget.allowanceFor(2_000, 1_200, 201, false));
        assertEquals(1, SpaceBuildWorkBudget.allowanceFor(2_000, 201, 201, false));
    }

    @Test void localAndGlobalLimitsBothApplyAndInvalidBudgetsDoNoWork() {
        assertEquals(512, SpaceBuildWorkBudget.allowanceFor(512, 1_200, 97, true));
        assertEquals(415, SpaceBuildWorkBudget.allowanceFor(512, 1_200, 97, false));
        assertEquals(1_103, SpaceBuildWorkBudget.allowanceFor(2_000, 1_200, 97, false));
        assertEquals(0, SpaceBuildWorkBudget.allowanceFor(0, 1_200, 1, true));
        assertEquals(0, SpaceBuildWorkBudget.allowanceFor(2_000, 0, 1, true));
        assertEquals(0, SpaceBuildWorkBudget.allowanceFor(2_000, 1_200, 0, false));
    }

    @Test void mixedAtomicGroupAndExactJobsFitWithinTheSharedHardCap() {
        int cap = 2_000;
        int firstAllowance = SpaceBuildWorkBudget.allowanceFor(cap, cap, 401, false);
        int firstWorstCase = firstAllowance + 401;
        int remaining = cap - firstWorstCase;
        int exactJobAllowance = SpaceBuildWorkBudget.allowanceFor(512, remaining, 97, true);
        assertEquals(1_599, firstAllowance);
        assertEquals(2_000, firstWorstCase);
        assertEquals(0, exactJobAllowance);
        assertEquals(cap, firstWorstCase + exactJobAllowance);
    }

    @Test void measuredGroupsExcludeTheSortSentinelAndOversizedUnitsDoNotReceiveAnAllowance() {
        var groups = java.util.List.of(new int[]{77, 1, 2, 3}, new int[]{99, 4, 5, 6, 7, 8});
        int largestAtomicGroup = PlotBodyBuilder.maxGroupedVoxels(groups);
        assertEquals(5, largestAtomicGroup);
        assertEquals(0, SpaceBuildWorkBudget.allowanceFor(2_000, 2_000, 2_001, false));
        assertEquals(1_995, SpaceBuildWorkBudget.allowanceFor(2_000, 2_000, largestAtomicGroup, false));
        assertTrue(SpaceBuildWorkBudget.requiresExactWriter(2_001, 2_000, 2_000));
        assertTrue(SpaceBuildWorkBudget.requiresExactWriter(513, 512, 2_000));
        assertFalse(SpaceBuildWorkBudget.requiresExactWriter(512, 512, 2_000));
    }

    @Test void managerSharesIncludeEveryGeneratedBodyOwnerAndNeverExceedTheServerCap() {
        for (int tick = 0; tick < 9; tick++) {
            int chart = SpaceBuildWorkBudget.ownerShareFor(2_000, tick, SpaceBuildWorkBudget.Owner.CHART_PLANET);
            int asteroids = SpaceBuildWorkBudget.ownerShareFor(2_000, tick, SpaceBuildWorkBudget.Owner.ASTEROID);
            int small = SpaceBuildWorkBudget.ownerShareFor(2_000, tick, SpaceBuildWorkBudget.Owner.SMALL_PLANET);
            int dragonBalls = SpaceBuildWorkBudget.ownerShareFor(2_000, tick, SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL);
            int pods = SpaceBuildWorkBudget.ownerShareFor(2_000, tick, SpaceBuildWorkBudget.Owner.SPACE_POD);
            assertEquals(2_000, chart + asteroids + small + dragonBalls + pods);
            assertEquals(400, chart);
            assertEquals(400, asteroids);
            assertEquals(400, small);
            assertEquals(400, dragonBalls);
            assertEquals(400, pods);
        }
        for (int tick = 0; tick < SpaceBuildWorkBudget.Owner.values().length; tick++) {
            int total = 0;
            for (var owner : SpaceBuildWorkBudget.Owner.values()) {
                int share = SpaceBuildWorkBudget.ownerShareFor(1, tick, owner);
                total += share;
                assertEquals(owner.ordinal() == tick ? 1 : 0, share,
                        "a one-voxel remainder should rotate across all owners");
            }
            assertEquals(1, total);
        }
    }

    @Test void emptyPlotsAreNotPreparedWithoutWorkAvailableForTheirOwner() {
        assertFalse(SpaceBuildWorkBudget.mayPrepareFor(2_000, 667, 667, 512));
        assertFalse(SpaceBuildWorkBudget.mayPrepareFor(1, 0, 0, 512));
        assertTrue(SpaceBuildWorkBudget.mayPrepareFor(2_000, 666, 667, 512));
        assertTrue(SpaceBuildWorkBudget.mayPrepareFor(0, 0, 0, 512));
        assertFalse(SpaceBuildWorkBudget.mayPrepareFor(0, 0, 0, 0));
    }

    @Test void sharedInstallTimeBudgetNeverReturnsNegativeOrInventedTime() {
        assertEquals(2.0, SpaceBuildWorkBudget.remainingMillisFor(5.0, 3.0), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisFor(5.0, 7.0), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisFor(0.0, 0.0), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisFor(Double.NaN, 0.0), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisFor(5.0, Double.NaN), 0.0);
    }

    @Test void ownerTimeSharesAreBoundedByBothOwnerAndGlobalRemainders() {
        int ownerCount = SpaceBuildWorkBudget.Owner.values().length;
        assertEquals(0.4, SpaceBuildWorkBudget.remainingMillisForOwner(2.0, 0.0, 0.0, ownerCount), 0.0);
        assertEquals(0.25, SpaceBuildWorkBudget.remainingMillisForOwner(2.0, 1.75, 0.0, ownerCount), 0.0);
        assertEquals(0.125, SpaceBuildWorkBudget.remainingMillisForOwner(2.0, 0.0, 0.275, ownerCount), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisForOwner(2.0, 0.25, 0.5, ownerCount), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisForOwner(Double.NaN, 0.0, 0.0, ownerCount), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.remainingMillisForOwner(2.0, 0.0, 0.0, 0), 0.0);
    }

    @Test void anExhaustedEnabledTimeBudgetBlocksStartingAnotherPlotButDisabledCapDoesNot() {
        assertTrue(SpaceBuildWorkBudget.mayPrepareWithinMillis(8.0, 2.0));
        assertFalse(SpaceBuildWorkBudget.mayPrepareWithinMillis(8.0, 0.0));
        assertFalse(SpaceBuildWorkBudget.mayPrepareWithinMillis(8.0, Double.NaN));
        assertTrue(SpaceBuildWorkBudget.mayPrepareWithinMillis(0.0, 0.0));
        assertFalse(SpaceBuildWorkBudget.mayPrepareWithinMillis(Double.NaN, 1.0));
    }

    @Test void anAllocatedEmptyPlotGetsExactlyOneTinyTimeSlicedSeedVoxelAfterPrepareOverruns() {
        assertEquals(0.001, SpaceBuildWorkBudget.stepMillisBudget(8.0, 0.0, 0), 0.0);
        assertEquals(0.001, SpaceBuildWorkBudget.stepMillisBudget(8.0, 0.0001, 0), 0.0);
        assertEquals(0.25, SpaceBuildWorkBudget.stepMillisBudget(8.0, 0.25, 0), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.stepMillisBudget(8.0, 0.0, 1), 0.0);
        assertEquals(0.0, SpaceBuildWorkBudget.stepMillisBudget(0.0, 0.0, 0), 0.0);
        assertEquals(1, SpaceBuildWorkBudget.stepVoxelBudget(500, 8.0, 0.0, 0));
        assertEquals(1, SpaceBuildWorkBudget.stepVoxelBudget(500, 8.0, 0.0005, 0));
        assertEquals(500, SpaceBuildWorkBudget.stepVoxelBudget(500, 8.0, 0.25, 0));
        assertEquals(500, SpaceBuildWorkBudget.stepVoxelBudget(500, 0.0, 0.0, 0));
    }

    @Test void buildStepTimeRemainsObservableWhenTheOptionalLimiterIsOff() {
        long tick = Long.MIN_VALUE + 37;
        SpaceBuildWorkBudget.forget(null);
        SpaceBuildWorkBudget.recordMillis(null, tick, SpaceBuildWorkBudget.Owner.CHART_PLANET, 0.75);
        SpaceBuildWorkBudget.recordMillis(null, tick, SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL, 1.25);

        assertEquals(2.0, SpaceBuildWorkBudget.usedMillis(null, tick), 0.0);
        assertEquals(0.75, SpaceBuildWorkBudget.usedMillis(null, tick,
                SpaceBuildWorkBudget.Owner.CHART_PLANET), 0.0);
        assertEquals(1.25, SpaceBuildWorkBudget.usedMillis(null, tick,
                SpaceBuildWorkBudget.Owner.SUPER_DRAGON_BALL), 0.0);
        SpaceBuildWorkBudget.forget(null);
    }

    @Test void preparationWorkConsumesTheSameBestEffortTimeAllowanceAsInstallWork() {
        long tick = Long.MIN_VALUE + 38;
        SpaceBuildWorkBudget.forget(null);
        // Production records synchronous Job.prepare() duration before asking for a step allowance.
        SpaceBuildWorkBudget.recordMillis(null, tick, SpaceBuildWorkBudget.Owner.ASTEROID, 1.25);

        assertEquals(1.25, SpaceBuildWorkBudget.usedMillis(null, tick), 0.0);
        assertEquals(0.75, SpaceBuildWorkBudget.remainingMillisFor(2.0,
                SpaceBuildWorkBudget.usedMillis(null, tick)), 0.0);
        SpaceBuildWorkBudget.forget(null);
    }

    @Test void failedBuildStepsStillChargeElapsedTimeAndCompletedVoxelDelta() {
        long tick = Long.MIN_VALUE + 39;
        SpaceBuildWorkBudget.forget(null);
        int[] installed = {10};

        assertThrows(IllegalStateException.class, () -> SpaceBuildWorkBudget.measureStep(
                null, tick, SpaceBuildWorkBudget.Owner.CHART_PLANET, () -> installed[0], () -> {
                    installed[0] = 13;
                    throw new IllegalStateException("simulated plot callback failure");
                }));

        assertEquals(3, SpaceBuildWorkBudget.used(null, tick,
                SpaceBuildWorkBudget.Owner.CHART_PLANET));
        assertTrue(SpaceBuildWorkBudget.usedMillis(null, tick) > 0.0);
        SpaceBuildWorkBudget.forget(null);
    }

    @Test void timeReportShowsEveryOwnerWhenTheOptionalLimiterIsOff() {
        String report = SpaceBuildWorkBudget.formatTimeReport(2.5, 0.0, 0.25, 0.5, 0.75, 1.0);
        assertTrue(report.contains("prepare+step ms this tick=2.500 cap=off"));
        assertTrue(report.contains("chart=0.250 asteroid=0.500 small-planet=0.750 super-dragon-ball=1.000"));

        String capped = SpaceBuildWorkBudget.formatTimeReport(0.5, 2.0, 0.1, 0.2, 0.1, 0.1);
        assertTrue(capped.contains("cap=2.000 ms best-effort"));
    }
}
