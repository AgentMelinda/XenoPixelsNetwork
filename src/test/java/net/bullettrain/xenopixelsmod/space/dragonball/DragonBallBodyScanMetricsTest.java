package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DragonBallBodyScanMetricsTest {
    @Test void retainsTheLatestScanAndPeakCostWithItsCensusSize() {
        var metrics = new DragonBallBodyScanMetrics();

        metrics.record(120, 120, 0, 4_000_000L, true);
        metrics.record(150, 77, 1, 12_000_000L, false);

        var snapshot = metrics.snapshot();
        assertEquals(2, snapshot.scans());
        assertEquals(150, snapshot.listedBodies());
        assertEquals(77, snapshot.visitedBodies());
        assertEquals(1, snapshot.skippedInFlight());
        assertEquals(12_000_000L, snapshot.lastNanos());
        assertEquals(12_000_000L, snapshot.peakNanos());
        assertFalse(snapshot.complete());
    }

    @Test void rejectsImpossibleCensusAndTimeValues() {
        var metrics = new DragonBallBodyScanMetrics();
        assertThrows(IllegalArgumentException.class, () -> metrics.record(2, 3, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> metrics.record(-2, 0, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> metrics.record(1, 0, 0, -1, false));
        assertThrows(IllegalArgumentException.class, () -> metrics.record(-1, 1, 0, 1, false));
        assertThrows(IllegalArgumentException.class, () -> metrics.record(1, 1, 2, 1, true));
    }

    @Test void reportDistinguishesAnUnrunScanFromACompletedCensus() {
        var metrics = new DragonBallBodyScanMetrics();
        assertTrue(metrics.report().contains("not-run"));

        metrics.record(7, 7, 0, 1_250_000L, true);
        assertTrue(metrics.report().contains("bodies=7/7"));
        assertTrue(metrics.report().contains("skipped-in-flight=0"));
        assertTrue(metrics.report().contains("complete"));
    }

    @Test void completedCensusShowsWhichRowsWereTemporarilySkipped() {
        var metrics = new DragonBallBodyScanMetrics();
        metrics.record(7, 7, 2, 1_250_000L, true);
        assertTrue(metrics.report().contains("bodies=7/7"));
        assertTrue(metrics.report().contains("skipped-in-flight=2"));
        assertTrue(metrics.report().contains("status=complete"));
    }
}
