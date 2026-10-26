package net.bullettrain.xenopixelsmod.space;

import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The void-rule precedence and the rescue bound, with no server, level, or event in sight. */
class SpaceVoidSafetyTest {
    private static final ResourceLocation DEMO_ORBIT = ResourceLocation.fromNamespaceAndPath("demo", "orbit");
    private static final ResourceLocation SPIKE_ORBIT =
            ResourceLocation.fromNamespaceAndPath("xenospace_spike", "orbit");
    private static final ResourceLocation OVERWORLD =
            ResourceLocation.fromNamespaceAndPath("minecraft", "overworld");

    @Test void unchartedDimensionKeepsVanillaVoidDamage() {
        assertEquals(SpaceVoidSafety.Verdict.VANILLA, SpaceVoidSafety.verdict(null, false, false));
        assertSame(SpaceVoidSafety.Verdict.VANILLA,
                SpaceVoidSafety.ruleFor(new ChartRegistry(), OVERWORLD, false, Set.of()).verdict());
    }

    @Test void chartedDimensionInheritsTheGlobalSwitch() {
        ChartRegistry chart = chartWith(null);
        assertEquals(SpaceVoidSafety.Verdict.PROTECTED,
                SpaceVoidSafety.ruleFor(chart, DEMO_ORBIT, false, Set.of()).verdict());
        assertEquals(SpaceVoidSafety.Verdict.DAMAGE,
                SpaceVoidSafety.ruleFor(chart, DEMO_ORBIT, true, Set.of()).verdict());
    }

    @Test void authoredOverrideBeatsTheGlobalSwitchInBothDirections() {
        assertEquals(SpaceVoidSafety.Verdict.DAMAGE,
                SpaceVoidSafety.ruleFor(chartWith(Boolean.TRUE), DEMO_ORBIT, false, Set.of()).verdict());
        assertEquals(SpaceVoidSafety.Verdict.PROTECTED,
                SpaceVoidSafety.ruleFor(chartWith(Boolean.FALSE), DEMO_ORBIT, true, Set.of()).verdict());
    }

    @Test void listedDimensionIsSpaceWithoutAChartRow() {
        var rule = SpaceVoidSafety.ruleFor(new ChartRegistry(), SPIKE_ORBIT, false, Set.of(SPIKE_ORBIT));
        assertEquals(SpaceVoidSafety.Verdict.PROTECTED, rule.verdict());
        // A dimension with no chart row has no anchor, so the rescue has to fall back on the bounds.
        assertTrue(Double.isNaN(rule.anchorY()));
        // The same dimension unlisted is not space at all: the list is the only thing protecting it.
        assertSame(SpaceVoidSafety.Verdict.VANILLA,
                SpaceVoidSafety.ruleFor(new ChartRegistry(), SPIKE_ORBIT, false, Set.of()).verdict());
    }

    @Test void rescuedFallerLandsInsideTheDimension() {
        assertEquals(64.0, SpaceVoidSafety.returnY(64.0, 0, 384), 0.0);
        assertEquals(376.0, SpaceVoidSafety.returnY(Double.NaN, 0, 384), 0.0);
        // A hand-edited pack anchor outside the build volume must not teleport the faller back into it.
        assertEquals(376.0, SpaceVoidSafety.returnY(5_000.0, 0, 384), 0.0);
        assertEquals(312.0, SpaceVoidSafety.returnY(-9_999.0, -64, 384), 0.0);
        assertEquals(312.0, SpaceVoidSafety.returnY(Double.NaN, -64, 384), 0.0);
        // A volume shorter than the headroom still cannot hand back a Y below its own floor.
        assertEquals(0.0, SpaceVoidSafety.returnY(Double.NaN, 0, 0), 0.0);
    }

    @Test void anchorFromAChartedBodyIsCarriedThroughTheRule() {
        assertEquals(200.0, SpaceVoidSafety.ruleFor(chartWith(null), DEMO_ORBIT, false, Set.of()).anchorY());
    }

    @Test void sharedSurfaceVoidRuleUsesThePlayersCellIdentity() {
        var chart = new ChartRegistry();
        chart.rebuild(List.of(
                sharedSurfaceBody("xenopixelsmod:moon", 200),
                sharedSurfaceBody("xenopixelsmod:phobos", 700)));
        var moon = net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells
                .forChartBody("xenopixelsmod:moon");
        var phobos = net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells
                .forChartBody("xenopixelsmod:phobos");
        var dimension = ResourceLocation.parse("xenopixelsmod:planet_surfaces");
        assertEquals(200.0, SpaceVoidSafety.ruleFor(chart, dimension, false, Set.of(),
                (int) moon.centerBlockX(), (int) moon.centerBlockZ()).anchorY());
        assertEquals(700.0, SpaceVoidSafety.ruleFor(chart, dimension, false, Set.of(),
                (int) phobos.centerBlockX(), (int) phobos.centerBlockZ()).anchorY());
        assertTrue(Double.isNaN(SpaceVoidSafety.ruleFor(chart, dimension, false, Set.of(), 0, 0).anchorY()));
    }

    private static CelestialBody sharedSurfaceBody(String id, double anchorY) {
        return new CelestialBody(id, CelestialBody.BodyKind.MOON, id, null,
                0, 0, 0, null, 8, 0.0, null, "xenopixelsmod:planet_surfaces",
                anchorY, 700, false, false, 0, null, 0);
    }

    private static ChartRegistry chartWith(Boolean voidDamage) {
        ChartRegistry chart = new ChartRegistry();
        chart.rebuild(List.of(new CelestialBody("demo:orbit", CelestialBody.BodyKind.PLANET, "Orbit", null,
                0, 0, 0, null, 100, 0.0, null, "demo:orbit", 200, 700, false, false, 0, voidDamage, 0)));
        return chart;
    }
}
