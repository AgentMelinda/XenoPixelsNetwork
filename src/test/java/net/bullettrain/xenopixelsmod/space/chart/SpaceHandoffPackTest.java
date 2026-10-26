package net.bullettrain.xenopixelsmod.space.chart;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class SpaceHandoffPackTest {
    private static final Path ROOT = Path.of(System.getProperty("xenopixels.projectDir", "."))
            .resolve("examples/xenospace-handoff-spike");
    private static final String DATA = "data/xenospace_spike/";
    private static JsonObject json(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }

    @Test void fixtureWorldsUseLocalReferencesAndNoIncidentalGeneration() throws Exception {
        assertEquals(48, json("pack.mcmeta").getAsJsonObject("pack").get("pack_format").getAsInt());
        for (String world : new String[]{"surface", "orbit"}) {
            var dimension = json(DATA + "dimension/" + world + ".json");
            assertEquals("xenospace_spike:test_space", dimension.get("type").getAsString());
            var generator = dimension.getAsJsonObject("generator");
            assertEquals("minecraft:flat", generator.get("type").getAsString());
            var settings = generator.getAsJsonObject("settings");
            assertEquals("xenospace_spike:test_void", settings.get("biome").getAsString());
            assertFalse(settings.get("features").getAsBoolean());
            assertFalse(settings.get("lakes").getAsBoolean());
            assertTrue(settings.getAsJsonArray("structure_overrides").isEmpty());
            int height = 0;
            for (var layer : settings.getAsJsonArray("layers")) height += layer.getAsJsonObject().get("height").getAsInt();
            assertEquals(world.equals("surface") ? 64 : 0, height);
        }
        var type = json(DATA + "dimension_type/test_space.json");
        assertEquals(0, type.get("min_y").getAsInt());
        assertEquals(384, type.get("height").getAsInt());
        assertFalse(type.get("natural").getAsBoolean());
        assertFalse(type.get("bed_works").getAsBoolean());
        assertFalse(type.get("respawn_anchor_works").getAsBoolean());
        var biome = json(DATA + "worldgen/biome/test_void.json");
        assertTrue(biome.getAsJsonObject("spawners").entrySet().isEmpty());
        assertTrue(biome.getAsJsonObject("carvers").entrySet().isEmpty());
        assertEquals(11, biome.getAsJsonArray("features").size());
        for (var features : biome.getAsJsonArray("features")) assertTrue(features.getAsJsonArray().isEmpty());
    }

    @Test void onlySurfaceIsChartedAndItUsesTheExistingSchema() throws Exception {
        var body = ChartDefinition.parse("xenospace_spike:surface", json(DATA + "xenospace/chart_entry/surface.json"));
        assertEquals("xenospace_spike:surface", body.dimensionId());
        assertTrue(body.enterable());
        assertNotNull(body.orbit(), "chart_entry resources require a parent-relative or root orbit");
        assertEquals("xenopixelsmod:sol", body.parentId());
        assertFalse(body.orbitalMotion());
        assertFalse(body.erasable());
        assertFalse(Files.exists(ROOT.resolve(DATA + "xenospace/chart_entry/orbit.json")));
    }

    /**
     * The planet-to-planet demo body. It exists so {@code /xenospace travel} and the altitude handoff have
     * a second enterable, dimension-bound column to hop between — the shipped Moon is a showcase row with
     * no dimension, so it cannot be travelled to and should not be faked into the mod jar. This asserts the
     * fixture parses and is genuinely handoff-ready, which is a data contract, not a runtime proof.
     */
    @Test void moonSurfaceIsASecondEnterableHandoffBodyOnItsOwnColumn() throws Exception {
        var moon = ChartDefinition.parse("xenospace_spike:moon_surface",
                json(DATA + "xenospace/chart_entry/moon_surface.json"));
        assertEquals("xenospace_spike:moon_surface", moon.dimensionId());
        assertTrue(moon.enterable(), "a moon with no dimension cannot be travelled to");
        // Noise terrain replaces the old flat Moon floor; the type still matches the other fixtures.
        var dim = json(DATA + "dimension/moon_surface.json");
        assertEquals("xenospace_spike:moon_type", dim.get("type").getAsString());
        assertEquals("minecraft:noise", dim.getAsJsonObject("generator").get("type").getAsString());
        assertEquals("xenospace_spike:moon", dim.getAsJsonObject("generator").get("settings").getAsString());
        assertEquals(600, moon.handoffAnchorY());
        assertEquals(700, moon.exitAltitude());
        // A surface with a floor keeps vanilla void damage, and its arrival sits below its own exit trigger.
        assertEquals(Boolean.TRUE, moon.voidDamage());
        // Distinct from the surface body's column so the two are separate descent targets, not the same one.
        assertNotEquals(20000, (int) moon.pinnedX());
    }

    @Test void manualFunctionsCannotTargetBystandersOrStartAutomaticWork() throws Exception {
        assertFalse(Files.exists(ROOT.resolve(DATA + "tags/function/load.json")));
        assertFalse(Files.exists(ROOT.resolve(DATA + "tags/function/tick.json")));
        assertFalse(Files.exists(ROOT.resolve("data/minecraft/tags/function/load.json")));
        assertFalse(Files.exists(ROOT.resolve("data/minecraft/tags/function/tick.json")));
        for (String function : new String[]{"to_surface", "to_orbit", "diagnostics"}) {
            String text = Files.readString(ROOT.resolve(DATA + "function/" + function + ".mcfunction"));
            assertFalse(text.matches("(?s).*@(a|p|r|e|n)\\b.*"));
            for (String line : text.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList()) {
                assertFalse(line.matches("(?:.*\\brun )?(gamemode|kill|summon|give|schedule)\\b.*"), line);
                assertFalse(line.matches(".*\\bforceload (add|remove)\\b.*"), line);
                if (line.contains(" run tp ")) {
                    assertTrue(line.contains("if entity @s[type=minecraft:player"), line);
                    assertTrue(line.endsWith("run tp @s 0 65 0") || line.endsWith("run tp @s 0 128 0"), line);
                    if (function.equals("to_orbit")) {
                        assertTrue(line.contains("gamemode=creative") || line.contains("gamemode=spectator"), line);
                    }
                }
            }
        }
    }
}
