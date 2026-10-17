package net.bullettrain.xenopixelsmod.space.worldgen;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Codec registration for the Moon and shared profile-based planet-surface generators. */
public final class SpaceWorldgen {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, "xenopixelsmod");
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOME_SOURCES =
            DeferredRegister.create(Registries.BIOME_SOURCE, "xenopixelsmod");
    static { TYPES.register("lunar_height", () -> LunarHeightDensity.MAP_CODEC); }
    static { TYPES.register("planet_surface_terrain", () -> PlanetSurfaceTerrainDensity.MAP_CODEC); }
    static { BIOME_SOURCES.register("planet_surface", () -> PlanetSurfaceBiomeSource.CODEC); }
    private SpaceWorldgen() {}
    public static void register(IEventBus bus) {
        TYPES.register(bus);
        BIOME_SOURCES.register(bus);
    }
}
