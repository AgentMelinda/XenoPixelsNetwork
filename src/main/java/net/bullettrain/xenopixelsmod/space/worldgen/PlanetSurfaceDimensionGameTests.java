package net.bullettrain.xenopixelsmod.space.worldgen;

import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
import net.bullettrain.xenopixelsmod.space.universe.UniverseField;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Headless runtime checks for the shared planet-surface biome source. */
@GameTestHolder("xenopixels_space_worldgen")
@PrefixGameTestTemplate(false)
public final class PlanetSurfaceDimensionGameTests {
    private PlanetSurfaceDimensionGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 100, batch = "planet_surface_biome_source")
    public static void profileCellsResolveToTheirRegisteredBiomes(GameTestHelper helper) {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        var source = new PlanetSurfaceBiomeSource(
                biome(biomes, "planet_rock"), biome(biomes, "planet_ice"), biome(biomes, "planet_desert"),
                biome(biomes, "planet_jungle"), biome(biomes, "planet_lava"), biome(biomes, "moon_surface"));
        var sampler = helper.getLevel().getChunkSource().randomState().sampler();
        var system = new UniverseField.Cell(0, 0);
        for (SmallPlanetField.Profile profile : SmallPlanetField.Profile.values()) {
            var cell = profile == SmallPlanetField.Profile.LUNAR
                    ? PlanetSurfaceCells.moonSurfaceCell()
                    : PlanetSurfaceCells.forPlanet(system, 0, profile);
            int x = Math.toIntExact(cell.centerBlockX());
            int z = Math.toIntExact(cell.centerBlockZ());
            var biome = source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(0),
                    QuartPos.fromBlock(z), sampler);
            String actual = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
            helper.assertTrue(expectedBiome(profile).equals(actual),
                    "cell profile " + profile + " at " + cell + " should resolve to "
                            + expectedBiome(profile) + ", got " + actual);
        }
        helper.succeed();
    }

    private static Holder<Biome> biome(Registry<Biome> registry, String path) {
        ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath("xenopixelsmod", path));
        return registry.getHolderOrThrow(key);
    }

    private static String expectedBiome(SmallPlanetField.Profile profile) {
        return switch (profile) {
            case ROCK -> "xenopixelsmod:planet_rock";
            case ICE -> "xenopixelsmod:planet_ice";
            case DESERT -> "xenopixelsmod:planet_desert";
            case JUNGLE -> "xenopixelsmod:planet_jungle";
            case LAVA -> "xenopixelsmod:planet_lava";
            case LUNAR -> "xenopixelsmod:moon_surface";
        };
    }
}
