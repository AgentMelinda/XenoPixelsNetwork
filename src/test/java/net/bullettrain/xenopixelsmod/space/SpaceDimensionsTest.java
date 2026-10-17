package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one place that answers "does §19.1 call this dimension space?", which used to be two different
 * questions asked inconsistently — the chart knew about a dimension the config list did not name, and
 * the spike orbit was enterable without ever appearing on the chart.
 */
class SpaceDimensionsTest {
    private static final ResourceLocation DEMO_ORBIT = ResourceLocation.parse("demo:orbit");
    private static final ResourceLocation OVERWORLD = ResourceLocation.parse("minecraft:overworld");

    @Test void aChartBoundDimensionIsSpaceWithoutBeingListed() {
        ChartRegistry chart = chartBoundToDemoOrbit();
        assertTrue(SpaceDimensions.isSpace(chart, DEMO_ORBIT, Set.of()));
        assertFalse(SpaceDimensions.isSpace(chart, OVERWORLD, Set.of()));
    }

    @Test void aListedDimensionIsSpaceWithoutAChartRow() {
        assertTrue(SpaceDimensions.isSpace(new ChartRegistry(), DEMO_ORBIT, Set.of(DEMO_ORBIT)));
    }

    /** A null chart or dimension is "not space", which keeps vanilla void damage where the chart is absent. */
    @Test void nullInputsFallBackToVanilla() {
        assertFalse(SpaceDimensions.isSpace(null, DEMO_ORBIT, Set.of()));
        assertFalse(SpaceDimensions.isSpace(new ChartRegistry(), (ResourceLocation) null, Set.of(DEMO_ORBIT)));
    }

    /**
     * The string overload is what the config and the gravity settings speak, so a malformed entry has to
     * read as "not space" rather than throw the config load down.
     */
    @Test void theStringOverloadToleratesUnparseableIds() {
        ChartRegistry chart = chartBoundToDemoOrbit();
        assertTrue(SpaceDimensions.isSpace(chart, "demo:orbit", Set.of()));
        assertFalse(SpaceDimensions.isSpace(chart, "not an id", Set.of()));
        assertFalse(SpaceDimensions.isSpace(chart, (String) null, Set.of()));
        assertFalse(SpaceDimensions.isSpace(chart, "demo/orbit", Set.of(DEMO_ORBIT)));
        assertTrue(SpaceDimensions.isSpace(new ChartRegistry(), "demo:orbit", Set.of(DEMO_ORBIT)));
    }

    private static ChartRegistry chartBoundToDemoOrbit() {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(new CelestialBody("demo:orbit", CelestialBody.BodyKind.PLANET, "Orbit", null,
                0, 0, 0, null, 100, 0.0, null, "demo:orbit", 200, 700, false, false, 0, null, 0)));
        return chart;
    }
}
