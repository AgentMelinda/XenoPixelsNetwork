package net.bullettrain.xenopixelsmod.space;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpacePerfProbeTest {
    @Test void percentilesUseBoundedRawSamplesAndReset() {
        var p = new SpacePerfProbe(1);
        for (int i = 1; i <= 100; i++) p.record(0, i * 1000L);
        assertEquals(50, p.percentileMicros(0, 0.50));
        assertEquals(95, p.percentileMicros(0, 0.95));
        p.reset();
        assertEquals(0, p.percentileMicros(0, 0.95));
    }

    @Test void timedPeakKeepsGameTickAndWallClockForProfilerCorrelation() {
        var p = new SpacePerfProbe(1);
        p.recordTimed(0, 10_000_000L, 100L, 1_800_000_000_000L);
        p.recordTimed(0, 20_000_000L, 101L, 1_800_000_000_050L);
        p.recordTimed(0, 5_000_000L, 102L, 1_800_000_000_100L);

        assertEquals(101L, p.peakSampleTick(0));
        assertEquals(1_800_000_000_050L, p.peakSampleEpochMillis(0));
        assertTrue(p.report().contains("peakTick=101 peakEpochMs=1800000000050"));

        p.reset();
        assertEquals(Long.MIN_VALUE, p.peakSampleTick(0));
        assertEquals(0L, p.peakSampleEpochMillis(0));
    }
}
