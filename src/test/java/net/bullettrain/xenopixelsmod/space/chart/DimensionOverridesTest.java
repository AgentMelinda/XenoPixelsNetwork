package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DimensionOverridesTest {
    @TempDir Path directory;
    @Test void overridesBeatDatapacksAndMalformedRowsAreReported() throws Exception {
        Files.writeString(directory.resolve("earth.yml"), """
                dimension: minecraft:overworld
                charted_as: demo:earth
                overrides:
                  radius_blocks: 120
                  exit_altitude: 800
                """);
        Files.writeString(directory.resolve("bad.yml"), "dimension: [broken");
        var base = Map.of("demo:earth", JsonParser.parseString("{\"radius_blocks\":90,\"dimension\":\"minecraft:overworld\"}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertEquals(120, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).radiusBlocks());
        assertEquals(800, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).exitAltitude());
        assertEquals(1, result.errors().size());
        assertEquals(90, base.get("demo:earth").get("radius_blocks").getAsInt());
    }
    @Test void voidDamageOverrideRoundTripsThroughASidecar() throws Exception {
        Files.writeString(directory.resolve("lethal.yml"), """
                dimension: demo:hellmouth
                charted_as: demo:earth
                overrides:
                  void_damage: true
                  sky_style: 2
                """);
        var base = Map.of("demo:earth", JsonParser.parseString("{\"radius_blocks\":90,\"dimension\":\"demo:hellmouth\"}").getAsJsonObject());
        var result = DimensionOverrides.load(directory, base);
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals(Boolean.TRUE, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).voidDamage());
        // A server pack may recolor an existing body's art without touching the datapack that authored it.
        assertEquals(2, ChartDefinition.parse("demo:earth", result.definitions().get("demo:earth")).skyStyle());
    }
    @Test void duplicateYamlKeysAndObjectTagsAreRejected() throws Exception {
        Files.writeString(directory.resolve("a.yml"), "dimension: minecraft:overworld\ndimension: minecraft:the_end\n");
        Files.writeString(directory.resolve("b.yml"), "!!java/object:java.lang.String {}");
        assertEquals(2, DimensionOverrides.load(directory, Map.of()).errors().size());
    }
    @Test void implicitBodyUsesExplicitIdAndDimension() throws Exception {
        Files.writeString(directory.resolve("pocket.yml"), """
                dimension: demo:pocket
                implicit_body:
                  id: demo:pocket_body
                  radius_blocks: 32
                  kind: pocket
                """);
        var result = DimensionOverrides.load(directory, Map.of());
        assertTrue(result.errors().isEmpty(), result.errors().toString());
        assertEquals("demo:pocket", ChartDefinition.parse("demo:pocket_body", result.definitions().get("demo:pocket_body")).dimensionId());
    }
    @Test void implicitBodyInheritsGlobalDefaults() throws Exception {
        Files.writeString(directory.resolve("pocket.yml"), "dimension: demo:pocket\nimplicit_body:\n  id: demo:body\n");
        var defaults = JsonParser.parseString("{\"radius_blocks\":120,\"gravity\":{\"surface_ms2\":8}}").getAsJsonObject();
        var result = DimensionOverrides.load(directory, Map.of(), defaults);
        var body = ChartDefinition.parse("demo:body", result.definitions().get("demo:body"));
        assertEquals(120, body.radiusBlocks());
        assertEquals(115200, body.mu());
    }
}
