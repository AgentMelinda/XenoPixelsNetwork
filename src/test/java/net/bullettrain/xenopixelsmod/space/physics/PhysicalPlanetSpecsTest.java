package net.bullettrain.xenopixelsmod.space.physics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.space.chart.ChartAssembly;
import net.bullettrain.xenopixelsmod.space.chart.ChartRegistry;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhysicalPlanetSpecsTest {
    private static final Path PROJECT = Path.of(System.getProperty("xenopixels.projectDir", "."));
    private static final List<String> SHIPPED = List.of("sol", "earth", "mercury", "venus", "mars", "jupiter",
            "saturn", "uranus", "neptune", "namek", "otherworld", "sacred_kai", "time_chamber");

    private static ChartRegistry chart() throws Exception {
        var rows = new LinkedHashMap<String, JsonObject>();
        for (String id : SHIPPED) rows.put("xenopixelsmod:" + id, read("src/main/resources/data/xenopixelsmod/xenospace/planet/" + id + ".json"));
        rows.put(PhysicalEarth.MOON_ID, read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/moon_surface.json"));
        rows.put("xenospace_spike:surface", read("examples/xenospace-handoff-spike/data/xenospace_spike/xenospace/chart_entry/surface.json"));
        var result = ChartAssembly.build(rows, new JsonObject(), Set.of("minecraft:overworld", "dragonminez:namek",
                "dragonminez:otherworld", "dragonminez:sacredkaiplanet", "dragonminez:time_chamber",
                "xenospace_spike:moon_surface", "xenospace_spike:surface"));
        assertEquals(List.of(), result.errors());
        return result.chart();
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

    @Test void earthMoonModeIsTheS9Set() throws Exception {
        assertEquals(List.of(PhysicalEarth.BODY_ID, PhysicalEarth.MOON_ID), ids(PhysicalEarth.specsFor(chart(), "earth_moon")));
    }

    @Test void childrenComeAfterTheirParents() throws Exception {
        var ids = ids(PhysicalEarth.specsFor(chart(), "all"));
        assertTrue(ids.indexOf(PhysicalEarth.MOON_ID) > ids.indexOf(PhysicalEarth.BODY_ID));
        assertTrue(ids.indexOf("xenopixelsmod:time_chamber") > ids.indexOf(PhysicalEarth.BODY_ID));
    }
}
