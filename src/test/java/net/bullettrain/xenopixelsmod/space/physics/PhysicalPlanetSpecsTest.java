package net.bullettrain.xenopixelsmod.space.physics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.space.chart.CelestialBody;
import net.bullettrain.xenopixelsmod.space.chart.ChartAssembly;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalPlanetSpecsTest {
    private static final Path PROJECT = Path.of(System.getProperty("xenopixels.projectDir", "."));
    private static final List<String> SHIPPED = List.of("sol", "earth", "mercury", "venus", "mars", "jupiter",
            "saturn", "uranus", "neptune", "moon", "phobos", "deimos", "io", "europa", "ganymede",
            "callisto", "triton", "namek", "otherworld", "sacred_kai", "time_chamber");

    private static ChartRegistry chart() throws Exception { return assemble(rows()); }

    private static ChartRegistry assemble(LinkedHashMap<String, JsonObject> rows) {
        var result = ChartAssembly.build(rows, new JsonObject(), Set.of("minecraft:overworld", "dragonminez:namek",
                "dragonminez:otherworld", "dragonminez:sacredkaiplanet", "dragonminez:time_chamber",
                "xenopixelsmod:planet_surfaces", "xenospace_spike:moon_surface", "xenospace_spike:surface"));
        assertEquals(List.of(), result.errors());
        return result.chart();
    }

    private static LinkedHashMap<String, JsonObject> rows() throws Exception {
        var rows = new LinkedHashMap<String, JsonObject>();
        for (String id : SHIPPED) rows.put("xenopixelsmod:" + id, read("src/main/resources/data/xenopixelsmod/xenospace/planet/" + id + ".json"));
        rows.put(PhysicalEarth.LEGACY_MOON_ID, read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/moon_surface.json"));
        rows.put("xenospace_spike:surface", read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/surface.json"));
        return rows;
    }
    private static JsonObject read(String file) throws Exception {
        return JsonParser.parseString(Files.readString(PROJECT.resolve(file))).getAsJsonObject();
    }
    private static List<String> ids(List<PhysicalEarth.Spec> specs) {
        return specs.stream().map(PhysicalEarth.Spec::id).toList();
    }

    @Test void allModeMakesEveryBodyPhysicalIncludingTheSunEarthFirst() throws Exception {
        var ids = ids(PhysicalEarth.specsFor(chart(), "all"));
        assertEquals(PhysicalEarth.BODY_ID, ids.get(0));
        for (String id : SHIPPED) assertTrue(ids.contains("xenopixelsmod:" + id), id);
        assertTrue(ids.contains(PhysicalEarth.MOON_ID));
        assertTrue(!ids.contains("xenospace_spike:surface"), "the handoff test surface is excluded by default");
        assertEquals(ids.size(), Set.copyOf(ids).size(), "no duplicates");
    }

    @Test void planetsModeLeavesTheSunVisualOnly() throws Exception {
        var ids = ids(PhysicalEarth.specsFor(chart(), "planets"));
        assertTrue(!ids.contains("xenopixelsmod:sol"));
        assertTrue(ids.contains("xenopixelsmod:jupiter"));
    }

    @Test void naturalSatelliteRowsAreEligibleForProximityPhysicalization() throws Exception {
        var chart = chart();
        for (String id : List.of("phobos", "deimos", "io", "europa", "ganymede", "callisto", "triton")) {
            String bodyId = "xenopixelsmod:" + id;
            var row = row(chart, bodyId);
            var placement = PhysicalEarth.Placement.of(row, "proximity", 200, Set.of());
            assertEquals(bodyId, placement.spec() == null ? null : placement.spec().id(),
                    bodyId + " must be eligible for proximity Sable physicalization");
            assertTrue(row.radiusBlocks() >= 4 && row.radiusBlocks() <= 200,
                    bodyId + " must satisfy the physical shell radius band");
        }
    }

    @Test void earthMoonModeIsTheS9Set() throws Exception {
        assertEquals(List.of(PhysicalEarth.BODY_ID, PhysicalEarth.MOON_ID), ids(PhysicalEarth.specsFor(chart(), "earth_moon")));
    }

    @Test void childrenComeAfterTheirParents() throws Exception {
        var ids = ids(PhysicalEarth.specsFor(chart(), "all"));
        assertTrue(ids.indexOf(PhysicalEarth.MOON_ID) > ids.indexOf(PhysicalEarth.BODY_ID));
        assertTrue(ids.indexOf("xenopixelsmod:time_chamber") > ids.indexOf(PhysicalEarth.BODY_ID));
    }

    private static CelestialBody row(ChartRegistry chart, String id) {
        for (int slot = 0; slot < chart.size(); slot++) if (chart.body(slot).id().equals(id)) return chart.body(slot);
        throw new IllegalArgumentException(id + " is not charted");
    }

    @Test void everyDropIsNamedByTheSameRuleThatMadeTheManagedSet() throws Exception {
        var chart = chart();
        var excluded = Set.of("xenospace_spike:surface");
        for (String mode : List.of("all", "planets", "earth_moon", "proximity")) {
            var managed = Set.copyOf(ids(PhysicalEarth.specsFor(chart, mode, 200, excluded)));
            for (int slot = 0; slot < chart.size(); slot++) {
                var body = chart.body(slot);
                if (body.id().equals(PhysicalEarth.BODY_ID)) continue;
                var placed = PhysicalEarth.Placement.of(body, mode, 200, excluded);
                assertEquals(managed.contains(body.id()), placed.spec() != null, body.id() + " in " + mode);
                assertEquals(placed.spec() != null, placed.rejection() == null, body.id() + " reason in " + mode);
            }
        }
    }

    @Test void radiusOutsideTheBuildBandIsRefusedWithItsReason() throws Exception {
        var big = row(chart(), "xenopixelsmod:sol");
        assertEquals("radius 160 exceeds space.physicalPlanetMaxRadius 50",
                PhysicalEarth.Placement.of(big, "all", 50, Set.of()).rejection());

        var rows = rows();
        var pebble = read("src/main/resources/data/xenopixelsmod/xenospace/planet/mercury.json");
        pebble.addProperty("radius_blocks", 2);
        rows.put("xenopixelsmod:mercury", pebble);
        assertEquals("radius 2 is below the 4 block floor",
                PhysicalEarth.Placement.of(row(assemble(rows), "xenopixelsmod:mercury"), "all", 200, Set.of()).rejection());
        assertTrue(!ids(PhysicalEarth.specsFor(assemble(rows), "all", 200, Set.of())).contains("xenopixelsmod:mercury"));
    }

    @Test void aModeChangeDropsTheRetriesItNoLongerWants() throws Exception {
        var wanted = PhysicalEarth.specsFor(chart(), "all", 200, Set.of());
        var queued = new java.util.LinkedHashSet<>(wanted);
        var planetsOnly = PhysicalEarth.specsFor(chart(), "planets", 200, Set.of());

        assertEquals(1, PhysicalEarth.discardUnmanagedPending(queued, planetsOnly),
                "the sun is the only body planets mode stops building");
        assertEquals(Set.copyOf(planetsOnly), Set.copyOf(queued), "every body the new mode still wants stays queued");
    }

    @Test void starsAndExcludedBodiesAreRefusedForTheirOwnReasons() throws Exception {
        var sol = row(chart(), "xenopixelsmod:sol");
        assertEquals("stars are physical only in all/proximity mode",
                PhysicalEarth.Placement.of(sol, "planets", 200, Set.of()).rejection());
        assertNull(PhysicalEarth.Placement.of(sol, "all", 200, Set.of()).rejection());
        assertEquals("space.physicalPlanetExclude lists it",
                PhysicalEarth.Placement.of(sol, "all", 200, Set.of("xenopixelsmod:sol")).rejection());
        assertEquals("mode earth_moon keeps it visual-only",
                PhysicalEarth.Placement.of(sol, "earth_moon", 200, Set.of()).rejection());

        var moon = row(chart(), PhysicalEarth.MOON_ID);
        assertEquals(PhysicalEarth.MOON_ID, PhysicalEarth.Placement.of(moon, "earth_moon", 200, Set.of()).spec().id(),
                "the moon is physical in every mode including earth_moon");
        assertEquals("space.physicalPlanetExclude lists it",
                PhysicalEarth.Placement.of(moon, "all", 200, Set.of(PhysicalEarth.MOON_ID)).rejection(),
                "the exclude list outranks the always-physical moon");
    }

    @Test void canonicalChartMoonGetsThePhysicalMoonAndLegacySpikeEntryCannotDuplicateIt() throws Exception {
        var chart = chart();
        var canonical = row(chart, PhysicalEarth.MOON_ID);
        var legacy = row(chart, PhysicalEarth.LEGACY_MOON_ID);

        assertEquals(PhysicalEarth.MOON_ID,
                PhysicalEarth.Placement.of(canonical, "earth_moon", 200, Set.of()).spec().id());
        assertEquals("legacy spike Moon is superseded by " + PhysicalEarth.MOON_ID,
                PhysicalEarth.Placement.of(legacy, "all", 200, Set.of()).rejection());
        assertEquals(1, ids(PhysicalEarth.specsFor(chart, "all")).stream()
                .filter(PhysicalEarth.MOON_ID::equals).count(),
                "a chart containing both identities must manage exactly the canonical Moon");
    }
}
