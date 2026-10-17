package net.bullettrain.xenopixelsmod.space;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpaceActivationMetricsTest {
    private static final long SECOND = 1_000_000_000L;

    @Test void recordsWallClockWindowByOwnerAndExcludesEventsOlderThanOneMinute() {
        var metrics = new SpaceActivationMetrics();
        metrics.recordBuild(SpaceActivationMetrics.Owner.ASTEROID, 10 * SECOND, 125, 2.5, 3.0, 1.25);
        metrics.recordRelease(SpaceActivationMetrics.Owner.ASTEROID, 40 * SECOND);
        metrics.recordBuild(SpaceActivationMetrics.Owner.CHART_PLANET, 39 * SECOND, 900, 1.0, 7.0, 2.0);

        var asteroid = metrics.snapshot(SpaceActivationMetrics.Owner.ASTEROID, 70 * SECOND);
        assertEquals(1, asteroid.activationsLastMinute());
        assertEquals(1, asteroid.releasesLastMinute());
        assertEquals(125, asteroid.lastBuildVoxels());
        assertEquals(2.5, asteroid.lastFillMillis());
        assertEquals(3.0, asteroid.lastInstallMillis());
        assertEquals(1.25, asteroid.lastMaxServerSliceMillis());
        assertTrue(asteroid.minuteWindowComplete());

        var chart = metrics.snapshot(SpaceActivationMetrics.Owner.CHART_PLANET, 70 * SECOND);
        assertEquals(1, chart.activationsLastMinute());
        assertEquals(0, chart.releasesLastMinute());
        assertEquals(900, chart.lastBuildVoxels());
    }

    @Test void handlesNegativeNanoTimeOriginsAndReportsTruncatedWindowAfterCapacityOverflow() {
        var metrics = new SpaceActivationMetrics();
        metrics.recordBuild(SpaceActivationMetrics.Owner.SMALL_PLANET, -30 * SECOND, 64, 1, 2, 0.5);
        var beforeOverflow = metrics.snapshot(SpaceActivationMetrics.Owner.SMALL_PLANET, -20 * SECOND);
        assertEquals(1, beforeOverflow.activationsLastMinute());
        assertTrue(beforeOverflow.minuteWindowComplete());

        for (int i = 0; i <= 8192; i++)
            metrics.recordRelease(SpaceActivationMetrics.Owner.SMALL_PLANET, -SECOND);

        var afterOverflow = metrics.snapshot(SpaceActivationMetrics.Owner.SMALL_PLANET, 0);
        assertEquals(8192, afterOverflow.releasesLastMinute());
        assertFalse(afterOverflow.minuteWindowComplete());
    }

    @Test void tracksSuperDragonBallBodiesAsTheirOwnOwner() {
        var metrics = new SpaceActivationMetrics();
        metrics.recordBuild(SpaceActivationMetrics.Owner.SUPER_DRAGON_BALL,
                5 * SECOND, 123_456, 1.5, 12.0, 4.0);

        var snapshot = metrics.snapshot(SpaceActivationMetrics.Owner.SUPER_DRAGON_BALL, 6 * SECOND);
        assertEquals(1, snapshot.activationsLastMinute());
        assertEquals(123_456, snapshot.lastBuildVoxels());
        assertTrue(metrics.report(6 * SECOND).contains("super_dragon_ball activations=1"));
    }
}
