package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ChartRuntimeTest {
    @Test void disabledWorkHasNoSamplesAndEnabledWorkRunsOncePerInterval() {
        var runtime = new ChartRuntime();
        runtime.chart.rebuild(List.of(ChartDefinition.parse("demo:a", JsonParser.parseString("{}").getAsJsonObject())));
        runtime.tick(20, false, 20, true);
        assertEquals(0, runtime.probe.runs(0));
        runtime.tick(20, true, 20, true);
        runtime.tick(21, true, 20, true);
        assertEquals(1, runtime.probe.runs(0));
        runtime.tick(40, true, 20, false);
        assertEquals(1, runtime.probe.runs(0));
        assertFalse(runtime.probe.isEnabled());
    }
    @Test void separateServersHaveIndependentChartsAndSamples() {
        var a = new ChartRuntime(); var b = new ChartRuntime();
        a.tick(0, true, 1, true);
        assertEquals(1, a.probe.runs(0));
        assertEquals(0, b.probe.runs(0));
        assertEquals(0, b.chart.size());
    }
    @Test void serverTickSpanSamplesAreIndependentOfChartEvaluation() {
        var runtime = new ChartRuntime();
        runtime.recordServerTickSpan(1_000_000L, 20L);
        assertEquals(1, runtime.probe.runs(ChartRuntime.SLOT_SERVER_TICK_SPAN));
        assertEquals(0, runtime.probe.runs(ChartRuntime.SLOT_CHART_EVALUATION));
        runtime.probe.setEnabled(false);
        runtime.recordServerTickSpan(2_000_000L, 21L);
        assertEquals(1, runtime.probe.runs(ChartRuntime.SLOT_SERVER_TICK_SPAN));
    }

    @Test void proximityPassProbeUsesSharedOrbitClockAndSamplesEveryPass() {
        var runtime = new ChartRuntime();
        runtime.recordProximityPass(10_000_000L, 100L);
        runtime.recordProximityPass(20_000_000L, 120L);

        assertEquals(2, runtime.probe.runs(ChartRuntime.SLOT_PLANET_PROXIMITY));
        assertEquals(20_000.0, runtime.probe.peakMicros(ChartRuntime.SLOT_PLANET_PROXIMITY));
        assertEquals(120L, runtime.probe.peakSampleTick(ChartRuntime.SLOT_PLANET_PROXIMITY));
        assertTrue(runtime.probe.report().contains("planet proximity"));
    }
}
