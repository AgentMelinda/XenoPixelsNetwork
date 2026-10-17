package net.bullettrain.xenopixelsmod.space.chart;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
class ChartAssemblyTest {
    @Test void defaultsAreOverriddenAndMissingDimensionsBecomeShowcase() {
        var defaults = JsonParser.parseString("{\"radius_blocks\":120}").getAsJsonObject();
        var definitions = Map.of("demo:body", JsonParser.parseString("{\"radius_blocks\":80,\"dimension\":\"demo:absent\"}").getAsJsonObject());
        var result = ChartAssembly.build(definitions, defaults, Set.of("minecraft:overworld"));
        assertEquals(80, result.chart().byId("demo:body").radiusBlocks());
        assertFalse(result.chart().byId("demo:body").enterable());
        assertEquals(1, result.errors().size());
    }
    @Test void cyclesAreRejectedAndResourceRemovalProducesEmptyCandidate() {
        var a = JsonParser.parseString("{\"parent\":\"demo:b\"}").getAsJsonObject();
        var b = JsonParser.parseString("{\"parent\":\"demo:a\"}").getAsJsonObject();
        assertEquals(0, ChartAssembly.build(Map.of("demo:a", a, "demo:b", b), new com.google.gson.JsonObject(), Set.of()).chart().size());
        assertEquals(0, ChartAssembly.build(Map.of(), new com.google.gson.JsonObject(), Set.of()).chart().size());
    }
    @Test void selfParentIsRejectedAsCycle() {
        var a = JsonParser.parseString("{\"parent\":\"demo:a\"}").getAsJsonObject();
        var result = ChartAssembly.build(Map.of("demo:a", a), new com.google.gson.JsonObject(), Set.of());
        assertEquals(0, result.chart().size());
        assertFalse(result.errors().isEmpty());
    }
    @Test void authoredHierarchyDepthIsBoundedBeforeRecursiveOrdering() {
        var rows = new java.util.TreeMap<String, com.google.gson.JsonObject>();
        for (int i = 0; i < 80; i++) {
            var row = new com.google.gson.JsonObject();
            if (i > 0) row.addProperty("parent", "demo:b" + (i - 1));
            rows.put("demo:b" + i, row);
        }
        var result = ChartAssembly.build(rows, new com.google.gson.JsonObject(), Set.of());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("depth")));
        assertEquals(65, result.chart().size());
    }
}
