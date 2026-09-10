package com.dragonminez.server.world.structure.placement;

import com.dragonminez.common.config.ConfigManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.lang.reflect.Field;
import java.util.Optional;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.ExclusionZone;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.FrequencyReductionMethod;

public class BiomeAwareUniquePlacement extends StructurePlacement {
   public static final MapCodec<BiomeAwareUniquePlacement> CODEC = RecordCodecBuilder.mapCodec(
      instance -> placementCodec(instance)
            .and(
               instance.group(
                  RegistryCodecs.homogeneousList(Registries.BIOME).fieldOf("valid_biomes").forGetter(BiomeAwareUniquePlacement::getValidBiomes),
                  Rotation.CODEC.optionalFieldOf("rotation", Rotation.NONE).forGetter(BiomeAwareUniquePlacement::getRotation)
               )
            )
            .apply(instance, BiomeAwareUniquePlacement::new)
   );
   private final HolderSet<Biome> validBiomes;
   private final Rotation rotation;
   private static Field biomeSourceField = null;

   public BiomeAwareUniquePlacement(
      Vec3i locateOffset,
      FrequencyReductionMethod frequencyReductionMethod,
      float frequency,
      int salt,
      Optional<ExclusionZone> exclusionZone,
      HolderSet<Biome> validBiomes,
      Rotation rotation
   ) {
      super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
      this.validBiomes = validBiomes;
      this.rotation = rotation;
      StructureSpawnPlanner.register(this);
   }

   public int placementSalt() {
      return this.salt();
   }

   public BiomeAwareUniquePlacement(
      Vec3i locateOffset,
      FrequencyReductionMethod frequencyReductionMethod,
      float frequency,
      int salt,
      Optional<ExclusionZone> exclusionZone,
      HolderSet<Biome> validBiomes
   ) {
      this(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, validBiomes, Rotation.NONE);
   }

   private BiomeSource getBiomeSourceReflection(ChunkGeneratorStructureState state) {
      try {
         if (biomeSourceField == null) {
            for (Field f : ChunkGeneratorStructureState.class.getDeclaredFields()) {
               if (BiomeSource.class.isAssignableFrom(f.getType())) {
                  f.setAccessible(true);
                  biomeSourceField = f;
                  break;
               }
            }
         }

         if (biomeSourceField != null) {
            return (BiomeSource)biomeSourceField.get(state);
         }
      } catch (Exception var6) {
         System.err.println("[DMZ Debug] Error trying to get BiomeSource: " + var6.getMessage());
      }

      return null;
   }

   protected boolean isPlacementChunk(ChunkGeneratorStructureState structureState, int x, int z) {
      if (!ConfigManager.getServerConfig().getWorldGen().getGenerateCustomStructures()) {
         return false;
      } else {
         ChunkPos pos = this.getStructureChunk(
            structureState.getLevelSeed(), this.getBiomeSourceReflection(structureState), structureState.randomState(), structureState
         );
         return pos != null && pos.x == x && pos.z == z;
      }
   }

   public ChunkPos getStructureChunk(long worldSeed, BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState state) {
      return StructureSpawnPlanner.getPositionFor(this, worldSeed, biomeSource, randomState, state);
   }

   public StructurePlacementType<?> type() {
      return (StructurePlacementType<?>)MainStructurePlacements.BIOME_AWARE_PLACEMENT.get();
   }

   public HolderSet<Biome> getValidBiomes() {
      return this.validBiomes;
   }

   public Rotation getRotation() {
      return this.rotation;
   }
}
