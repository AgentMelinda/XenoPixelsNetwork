package net.bullettrain.xenopixelsmod.space.worldgen;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.bullettrain.xenopixelsmod.space.chart.ChartDefinition;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PlanetSurfaceFeatureResourceTest {
    private static final Path ROOT = Path.of(System.getProperty("xenopixels.projectDir", "."))
            .resolve("src/main/resources/data/xenopixelsmod/worldgen");
    private static final Path DATA = Path.of(System.getProperty("xenopixels.projectDir", "."))
            .resolve("src/main/resources/data/xenopixelsmod");

    private static JsonObject read(String path) throws Exception {
        return JsonParser.parseString(Files.readString(ROOT.resolve(path))).getAsJsonObject();
    }

    @Test void modPackMetadataSupportsBothMinecraft1211ResourceAndDataFormats() throws Exception {
        var pack = JsonParser.parseString(Files.readString(Path.of(System.getProperty("xenopixels.projectDir", "."))
                .resolve("src/main/resources/pack.mcmeta"))).getAsJsonObject().getAsJsonObject("pack");
        assertEquals(34, pack.get("pack_format").getAsInt());
        var supported = pack.getAsJsonObject("supported_formats");
        assertNotNull(supported, "mod resource pack must declare the separate 1.21.1 data-pack format too");
        assertEquals(34, supported.get("min_inclusive").getAsInt());
        assertEquals(48, supported.get("max_inclusive").getAsInt());
    }

    @Test void onlyTheRareJungleProfileAddsSparseVegetation() throws Exception {
        var jungle = read("biome/planet_jungle.json").getAsJsonArray("features");
        assertEquals(11, jungle.size());
        assertEquals("xenopixelsmod:small_planet_jungle_trees",
                jungle.get(9).getAsJsonArray().get(0).getAsString());

        for (String profile : new String[]{"planet_rock", "planet_ice", "planet_desert", "planet_lava", "moon_surface"}) {
            var features = read("biome/" + profile + ".json").getAsJsonArray("features");
            assertEquals(11, features.size());
            assertTrue(features.get(9).getAsJsonArray().isEmpty(), profile + " should stay vegetation-free");
        }

        var trees = read("placed_feature/small_planet_jungle_trees.json");
        assertEquals("minecraft:jungle_tree", trees.get("feature").getAsString());
        assertEquals(2, trees.getAsJsonArray("placement").get(0).getAsJsonObject().get("count").getAsInt());
        assertEquals("minecraft:biome", trees.getAsJsonArray("placement").get(4).getAsJsonObject().get("type").getAsString());
    }

    @Test void sharedPlanetDimensionUsesRegisteredProfileTerrainAndAllNoiseInputs() throws Exception {
        var dimension = JsonParser.parseString(Files.readString(DATA.resolve("dimension/planet_surfaces.json")))
                .getAsJsonObject();
        assertEquals("xenopixelsmod:planet_surfaces", dimension.get("type").getAsString());
        var generator = dimension.getAsJsonObject("generator");
        assertEquals("xenopixelsmod:planet_surface", generator.getAsJsonObject("biome_source")
                .get("type").getAsString());
        assertEquals("xenopixelsmod:planet_surfaces", generator.get("settings").getAsString());
        assertEquals("xenopixelsmod:moon_surface", generator.getAsJsonObject("biome_source").get("moon").getAsString());

        var settings = read("noise_settings/planet_surfaces.json").getAsJsonObject("noise_router");
        for (String densityKey : new String[]{"initial_density_without_jaggedness", "final_density"}) {
            var interpolated = settings.getAsJsonObject(densityKey);
            assertEquals("minecraft:interpolated", interpolated.get("type").getAsString());
            var terrain = interpolated.getAsJsonObject("argument");
            assertEquals("xenopixelsmod:planet_surface_terrain", terrain.get("type").getAsString());
            for (String input : new String[]{"seed_noise", "landform_noise", "detail_noise", "ridge_noise", "cave_noise"}) {
                String id = terrain.get(input).getAsString();
                assertTrue(id.startsWith("xenopixelsmod:"), input + " must reference a mod noise resource");
                String file = id.substring("xenopixelsmod:".length()) + ".json";
                assertDoesNotThrow(() -> JsonParser.parseString(Files.readString(ROOT.resolve("noise").resolve(file))),
                        "invalid or missing noise resource " + id);
            }
        }
        var surfaceRules = read("noise_settings/planet_surfaces.json").getAsJsonObject("surface_rule")
                .getAsJsonArray("sequence").get(1).getAsJsonObject().getAsJsonObject("then_run")
                .getAsJsonArray("sequence").get(0).getAsJsonObject().getAsJsonObject("if_true")
                .getAsJsonArray("biome_is");
        assertTrue(surfaceRules.asList().stream().anyMatch(id -> id.getAsString().equals("xenopixelsmod:moon_surface")));
    }

    @Test void shippedMoonChartSelectsTheReservedCellAndFittingHeightRange() throws Exception {
        var chartJson = JsonParser.parseString(Files.readString(DATA.resolve("xenospace/planet/moon.json")))
                .getAsJsonObject();
        var moon = ChartDefinition.parse("xenopixelsmod:moon", chartJson);
        var cell = PlanetSurfaceCells.moonSurfaceCell();
        assertEquals("xenopixelsmod:planet_surfaces", moon.dimensionId());
        assertEquals(cell.centerBlockX() + 0.5, moon.spatialAnchorX());
        assertEquals(cell.centerBlockZ() + 0.5, moon.spatialAnchorZ());
        assertEquals("xenopixelsmod:earth", moon.parentId());

        var type = JsonParser.parseString(Files.readString(DATA.resolve("dimension_type/planet_surfaces.json")))
                .getAsJsonObject();
        int minY = type.get("min_y").getAsInt();
        int maxY = minY + type.get("height").getAsInt();
        assertTrue(LunarTerrain.MIN_HEIGHT >= minY);
        assertTrue(LunarTerrain.MAX_HEIGHT < maxY);
    }
}
