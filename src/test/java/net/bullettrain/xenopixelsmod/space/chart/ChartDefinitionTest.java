package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChartDefinitionTest {
    @Test void resourceIdAndSurfaceGravityAreResolved() {
        var body = ChartDefinition.parse("demo:earth", JsonParser.parseString("""
                {"schema_version":1,"radius_blocks":100,"position":{"x":1000},
                 "gravity":{"surface_ms2":9.81},"dimension":"minecraft:overworld"}
                """).getAsJsonObject());
        assertEquals("demo:earth", body.id());
        assertEquals(98100, body.mu(), 0.001);
        assertEquals(1000, body.pinnedX());
        assertEquals("minecraft:overworld", body.dimensionId());
        assertEquals(400, body.warmUpAltitude());
    }
    @Test void warmUpAltitudeIsPerBodyAndMustPrecedeExit() {
        var body = ChartDefinition.parse("demo:earth", JsonParser.parseString("""
                {"exit_altitude":2032,"warm_up_altitude":1700}
                """).getAsJsonObject());
        assertEquals(1700, body.warmUpAltitude());
        assertEquals(1732, ChartDefinition.parse("demo:earth",
                JsonParser.parseString("{\"exit_altitude\":2032}").getAsJsonObject()).warmUpAltitude());
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:earth",
                JsonParser.parseString("{\"exit_altitude\":700,\"warm_up_altitude\":700}").getAsJsonObject()));
    }
    @Test void spatialAnchorDefaultsToOriginAndAcceptsFiniteHorizontalCoordinates() {
        var defaults = ChartDefinition.parse("demo:earth", JsonParser.parseString("{}").getAsJsonObject());
        assertEquals(0.0, defaults.spatialAnchorX());
        assertEquals(0.0, defaults.spatialAnchorZ());
        var anchored = ChartDefinition.parse("demo:earth", JsonParser.parseString(
                "{\"spatial_anchor\":{\"x\":120.5,\"z\":-35}}").getAsJsonObject());
        assertEquals(120.5, anchored.spatialAnchorX());
        assertEquals(-35.0, anchored.spatialAnchorZ());
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:earth",
                JsonParser.parseString("{\"spatial_anchor\":{\"y\":4}}").getAsJsonObject()));
    }
    @Test void breathableFlagIsAcceptedAndTypeChecked() {
        assertDoesNotThrow(() -> ChartDefinition.parse("demo:mars",
                JsonParser.parseString("{\"breathable\":false}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:mars",
                JsonParser.parseString("{\"breathable\":\"no\"}").getAsJsonObject()));
    }
    @Test void blackHoleBlockIsValidated() {
        assertDoesNotThrow(() -> ChartDefinition.parse("demo:maw", JsonParser.parseString("""
                {"kind":"black_hole","black_hole":{"horizon_radius":40,"tidal_radius":400,"lens_radius":4000,
                 "exit":{"dimension":"minecraft:overworld"}}}
                """).getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:maw",
                JsonParser.parseString("{\"black_hole\":{\"horizon_radius\":400,\"tidal_radius\":40,\"lens_radius\":4000}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:maw",
                JsonParser.parseString("{\"black_hole\":{\"horizon_radius\":40,\"tidal_radius\":400,\"lens_radius\":4000,\"warp\":1}}").getAsJsonObject()));
    }
    @Test void invalidNumbersIdsAndUnknownFieldsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("bad id", JsonParser.parseString("{}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a", JsonParser.parseString("{\"radius_blocks\":-1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a", JsonParser.parseString("{\"oxygen\":true}").getAsJsonObject()));
    }
    @Test void voidDamageOverrideIsTriState() {
        assertNull(ChartDefinition.parse("demo:a", JsonParser.parseString("{}").getAsJsonObject()).voidDamage());
        assertNull(ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"void_damage\":null}").getAsJsonObject()).voidDamage());
        assertEquals(Boolean.TRUE, ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"void_damage\":true}").getAsJsonObject()).voidDamage());
        assertEquals(Boolean.FALSE, ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"void_damage\":false}").getAsJsonObject()).voidDamage());
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"void_damage\":\"yes\"}").getAsJsonObject()));
    }
    @Test void skyStyleIsAnAtlasCellIndex() {
        assertEquals(0, ChartDefinition.parse("demo:a", JsonParser.parseString("{}").getAsJsonObject()).skyStyle());
        assertEquals(2, ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"sky_style\":2}").getAsJsonObject()).skyStyle());
        // Past the last shipped cell would be an out-of-range UV on the client, not a style choice.
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"sky_style\":3}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"sky_style\":-1}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> ChartDefinition.parse("demo:a",
                JsonParser.parseString("{\"sky_style\":1.5}").getAsJsonObject()));
    }
    @Test void partialOverridesPreserveUnspecifiedFields() {
        var base = JsonParser.parseString("{\"position\":{\"x\":4,\"z\":8},\"radius_blocks\":90}").getAsJsonObject();
        var patch = JsonParser.parseString("{\"position\":{\"x\":7}}").getAsJsonObject();
        var merged = ChartDefinition.merge(base, patch);
        assertEquals(7, merged.getAsJsonObject("position").get("x").getAsInt());
        assertEquals(8, merged.getAsJsonObject("position").get("z").getAsInt());
        assertEquals(4, base.getAsJsonObject("position").get("x").getAsInt());
    }
}
