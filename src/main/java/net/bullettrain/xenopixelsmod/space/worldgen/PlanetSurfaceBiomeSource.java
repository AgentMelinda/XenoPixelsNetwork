package net.bullettrain.xenopixelsmod.space.worldgen;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.bullettrain.xenopixelsmod.space.smallplanet.PlanetSurfaceCells;
import net.bullettrain.xenopixelsmod.space.smallplanet.SmallPlanetField;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.EnumMap;
import java.util.Map;
import java.util.stream.Stream;

/** Resolves each 8,192-block planet cell to the biome selected for its deterministic small-planet profile. */
public final class PlanetSurfaceBiomeSource extends BiomeSource {
    public static final MapCodec<PlanetSurfaceBiomeSource> CODEC =
            RecordCodecBuilder.<PlanetSurfaceBiomeSource>mapCodec(instance -> instance.group(
            Biome.CODEC.fieldOf("rock").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.ROCK)),
            Biome.CODEC.fieldOf("ice").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.ICE)),
            Biome.CODEC.fieldOf("desert").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.DESERT)),
            Biome.CODEC.fieldOf("jungle").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.JUNGLE)),
            Biome.CODEC.fieldOf("lava").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.LAVA)),
            Biome.CODEC.fieldOf("moon").forGetter((PlanetSurfaceBiomeSource source) -> source.biomes.get(SmallPlanetField.Profile.LUNAR))
            ).apply(instance, PlanetSurfaceBiomeSource::new)).stable();

    private final Map<SmallPlanetField.Profile, Holder<Biome>> biomes;

    public PlanetSurfaceBiomeSource(Holder<Biome> rock, Holder<Biome> ice, Holder<Biome> desert,
                                    Holder<Biome> jungle, Holder<Biome> lava, Holder<Biome> moon) {
        var byProfile = new EnumMap<SmallPlanetField.Profile, Holder<Biome>>(SmallPlanetField.Profile.class);
        byProfile.put(SmallPlanetField.Profile.ROCK, rock);
        byProfile.put(SmallPlanetField.Profile.ICE, ice);
        byProfile.put(SmallPlanetField.Profile.DESERT, desert);
        byProfile.put(SmallPlanetField.Profile.JUNGLE, jungle);
        byProfile.put(SmallPlanetField.Profile.LAVA, lava);
        byProfile.put(SmallPlanetField.Profile.LUNAR, moon);
        this.biomes = Map.copyOf(byProfile);
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(SmallPlanetField.Profile.values()).map(biomes::get);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
        var address = PlanetSurfaceCells.addressAtBlock(QuartPos.toBlock(quartX), QuartPos.toBlock(quartZ));
        SmallPlanetField.Profile profile = address.map(PlanetSurfaceCells.Address::profile)
                .orElse(SmallPlanetField.Profile.ROCK);
        return biomes.get(profile);
    }
}
