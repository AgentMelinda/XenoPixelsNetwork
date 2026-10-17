package net.bullettrain.xenopixelsmod.space.chart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class ChartResourcesTest {
    private static Resource resource(String json) { return new Resource(null, () -> new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))); }
    @Test void folderCollisionsAndMalformedJsonAreReported() {
        var planet = Map.of(ResourceLocation.parse("demo:xenospace/planet/a.json"), resource("{}"),
                ResourceLocation.parse("demo:xenospace/planet/bad.json"), resource("["));
        var entry = Map.of(ResourceLocation.parse("demo:xenospace/chart_entry/a.json"), resource("{\"dimension\":\"minecraft:overworld\"}"));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> args[0].equals("xenospace/planet") ? planet
                        : args[0].equals("xenospace/chart_entry") ? entry : Map.of());
        var result = ChartResources.scan(manager);
        assertTrue(result.definitions().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("body id occurs in both")));
        assertEquals(2, result.errors().stream().filter(error -> !error.contains("missing valid assignment")).count());
        assertEquals(7, result.errors().stream().filter(error -> error.contains("missing valid assignment")).count());
    }
    @Test void nullDimensionIsNotAChartEntryBinding() {
        var entries = Map.of(ResourceLocation.parse("demo:xenospace/chart_entry/a.json"), resource("{\"dimension\":null}"));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> args[0].equals("xenospace/planet") ? Map.of()
                        : args[0].equals("xenospace/chart_entry") ? entries : Map.of());
        var result = ChartResources.scan(manager);
        assertTrue(result.definitions().isEmpty());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("chart_entry requires dimension")));
        assertEquals(7, result.errors().stream().filter(error -> error.contains("missing valid assignment")).count());
    }

    @Test void loadsSevenNamespacedPlanetAssignmentsAndRejectsUnresolvedSchema() {
        var resources = new java.util.HashMap<ResourceLocation, Resource>();
        for (int slot = 1; slot <= 7; slot++)
            resources.put(ResourceLocation.parse("xenopixelsmod:xenospace/superdragonball/" + slot + ".json"),
                    resource("{\"slot\":" + slot + ",\"host_chart_id\":\"xenopixelsmod:earth\",\"orbit_radius_blocks\":120,\"orbit_phase_radians\":0.5}"));
        resources.put(ResourceLocation.parse("addon:xenospace/superdragonball/1.json"),
                resource("{\"slot\":1,\"host_chart_id\":\"addon:moon\",\"orbit_radius_blocks\":80}"));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> args[0].equals("xenospace/superdragonball") ? resources : Map.of());
        var result = ChartResources.scan(manager);
        assertEquals(6, result.dragonBallPlanets().size());
        assertEquals("xenopixelsmod:earth", result.dragonBallPlanets().get(2).hostChartId());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("multiple data packs assign Super Dragon Ball slot 1")));
        assertFalse(result.errors().stream().anyMatch(error -> error.contains("missing valid assignment for slot 2")));
    }

    @Test void planetAssignmentRequiresNamespacedHostAndPositiveOrbitRadius() {
        var unqualifiedHost = JsonParser.parseString("{\"slot\":1,\"host_chart_id\":\"earth\",\"orbit_radius_blocks\":80}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition.parse("pack:1", unqualifiedHost));
        var zeroOrbit = JsonParser.parseString("{\"slot\":1,\"host_chart_id\":\"pack:earth\",\"orbit_radius_blocks\":0}").getAsJsonObject();
        assertThrows(IllegalArgumentException.class,
                () -> net.bullettrain.xenopixelsmod.space.dragonball.DragonBallPlanetDefinition.parse("pack:1", zeroOrbit));
    }

    @Test void unrelatedFilesDoNotConsumeAssignmentBudgetAndNestedFilesAreRejectedClearly() {
        var resources = new java.util.HashMap<ResourceLocation, Resource>();
        for (int slot = 1; slot <= net.bullettrain.xenopixelsmod.space.dragonball.CosmicDragonBalls.COUNT; slot++)
            resources.put(ResourceLocation.parse("xenopixelsmod:xenospace/superdragonball/" + slot + ".json"),
                    resource("{\"slot\":" + slot + ",\"host_chart_id\":\"xenopixelsmod:earth\",\"orbit_radius_blocks\":120}"));
        for (int i = 0; i < 80; i++)
            resources.put(ResourceLocation.parse("addon:xenospace/superdragonball/readme" + i + ".json"), resource("{}"));
        resources.put(ResourceLocation.parse("addon:xenospace/superdragonball/sub/1.json"), resource("{}"));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> args[0].equals("xenospace/superdragonball") ? resources : Map.of());
        var result = ChartResources.scan(manager);
        assertEquals(7, result.dragonBallPlanets().size());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("expected a top-level slot file")));
        assertFalse(result.errors().stream().anyMatch(error -> error.contains("more than 64 candidate slot files")));
    }

    @Test void everyConflictingDuplicateIsReportedWithoutRedundantMissingSlotError() {
        var resources = new java.util.HashMap<ResourceLocation, Resource>();
        for (String namespace : java.util.List.of("xenopixelsmod", "addon_a", "addon_b"))
            resources.put(ResourceLocation.parse(namespace + ":xenospace/superdragonball/1.json"),
                    resource("{\"slot\":1,\"host_chart_id\":\"xenopixelsmod:earth\",\"orbit_radius_blocks\":120}"));
        ResourceManager manager = (ResourceManager) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ResourceManager.class},
                (proxy, method, args) -> args[0].equals("xenospace/superdragonball") ? resources : Map.of());
        var result = ChartResources.scan(manager);
        assertFalse(result.dragonBallPlanets().containsKey(1));
        assertEquals(1, result.errors().stream().filter(error -> error.contains("multiple data packs assign Super Dragon Ball slot 1")).count());
        assertEquals(1, result.errors().stream().filter(error -> error.contains("slot 1") && error.contains("ignored because")).count());
        assertFalse(result.errors().stream().anyMatch(error -> error.contains("missing valid assignment for slot 1")));
    }
}
