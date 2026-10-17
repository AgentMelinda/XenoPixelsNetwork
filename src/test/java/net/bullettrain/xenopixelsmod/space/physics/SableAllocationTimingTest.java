package net.bullettrain.xenopixelsmod.space.physics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SableAllocationTimingTest {
    @Test void recordsOnlyInsideTheAllocationScopeAndClearsItAtEnd() {
        assertFalse(SableAllocationTiming.isActive());
        var sample = SableAllocationTiming.begin();
        assertTrue(SableAllocationTiming.isActive());
        SableAllocationTiming.recordSubLevelCreate(1_250_000L);
        SableAllocationTiming.recordPlotConstruction(750_000L);
        SableAllocationTiming.recordMassTracker(2_000_000L);
        SableAllocationTiming.recordPipelineAdd(3_500_000L);
        assertEquals(1.25, sample.subLevelCreateMillis(), 0.0);
        assertEquals(0.75, sample.plotConstructionMillis(), 0.0);
        assertEquals(2.0, sample.massTrackerMillis(), 0.0);
        assertEquals(3.5, sample.pipelineAddMillis(), 0.0);
        SableAllocationTiming.end(sample);
        assertFalse(SableAllocationTiming.isActive());
        SableAllocationTiming.recordMassTracker(4_000_000L);
        SableAllocationTiming.recordPlotConstruction(4_000_000L);
        assertEquals(2.0, sample.massTrackerMillis(), 0.0);
        assertEquals(1.25, sample.subLevelCreateMillis(), 0.0);
        assertEquals(0.75, sample.plotConstructionMillis(), 0.0);
    }

    @Test void rejectsNestedOrMismatchedTimingScopes() {
        var sample = SableAllocationTiming.begin();
        assertThrows(IllegalStateException.class, SableAllocationTiming::begin);
        assertThrows(IllegalStateException.class, () -> SableAllocationTiming.end(new SableAllocationTiming.Sample()));
        SableAllocationTiming.end(sample);
        assertFalse(SableAllocationTiming.isActive());
    }
}
