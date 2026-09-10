package com.dragonminez.server.world.structure.helper;

import com.dragonminez.common.init.MainTags;
import com.dragonminez.server.world.biome.NamekBiomes;
import com.dragonminez.server.world.biome.SacredKaiBiomes;
import com.dragonminez.server.world.structure.placement.BiomeAwareUniquePlacement;
import com.dragonminez.server.world.structure.placement.FixedStructurePlacement;
import com.dragonminez.server.world.structure.placement.UniqueNearSpawnPlacement;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.FrequencyReductionMethod;

public class DMZStructureSets {
   public static final ResourceKey<StructureSet> GOKU_HOUSE = createKey("goku_house");
   public static final ResourceKey<StructureSet> ROSHI_HOUSE = createKey("roshi_house");
   public static final ResourceKey<StructureSet> TIMECHAMBER = createKey("timechamber");
   public static final ResourceKey<StructureSet> ELDER_GURU = createKey("elder_guru");
   public static final ResourceKey<StructureSet> KAMILOOKOUT = createKey("kamilookout");
   public static final ResourceKey<StructureSet> GERO_LAB = createKey("gero_lab");
   public static final ResourceKey<StructureSet> BABIDI = createKey("babidi");
   public static final ResourceKey<StructureSet> CELL_ARENA = createKey("cell_arena");
   public static final ResourceKey<StructureSet> FRIEZA_SHIP = createKey("frieza_ship");
   public static final ResourceKey<StructureSet> PICCOLO_HOUSE = createKey("piccolo_house");
   public static final ResourceKey<StructureSet> OLDKAI_PILLAR = createKey("oldkai_pillar");
   public static final ResourceKey<StructureSet> YAMCHA_HOUSE = createKey("yamcha_house");
   public static final ResourceKey<StructureSet> TRUNKS_SHIP = createKey("trunks_ship");
   public static final ResourceKey<StructureSet> VEGETA_POD = createKey("vegeta_pod");

   public static void bootstrap(BootstrapContext<StructureSet> context) {
      HolderGetter<Structure> structures = context.lookup(Registries.STRUCTURE);
      HolderGetter<Biome> biomes = context.lookup(Registries.BIOME);
      unique(context, GOKU_HOUSE, structures.getOrThrow(DMZStructures.GOKU_HOUSE), 12345678, biomes.getOrThrow(MainTags.Biomes.IS_PLAINSLIKE));
      unique(context, ROSHI_HOUSE, structures.getOrThrow(DMZStructures.ROSHI_HOUSE), 87654321, biomes.getOrThrow(BiomeTags.IS_OCEAN));
      context.register(
         TIMECHAMBER,
         new StructureSet(
            structures.getOrThrow(DMZStructures.TIMECHAMBER),
            new FixedStructurePlacement(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 11223344, Optional.empty(), 0, 0)
         )
      );
      unique(context, ELDER_GURU, structures.getOrThrow(DMZStructures.ELDER_GURU), 44332211, biomes.getOrThrow(MainTags.Biomes.IS_SACREDLAND));
      context.register(
         KAMILOOKOUT,
         new StructureSet(
            structures.getOrThrow(DMZStructures.KAMILOOKOUT),
            new UniqueNearSpawnPlacement(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 55667788, Optional.empty())
         )
      );
      unique(context, GERO_LAB, structures.getOrThrow(DMZStructures.GERO_LAB), 99887766, biomes.getOrThrow(MainTags.Biomes.IS_ROCKYBIOME));
      unique(context, BABIDI, structures.getOrThrow(DMZStructures.BABIDI), 18273645, biomes.getOrThrow(MainTags.Biomes.IS_MOUNTAINLIKE));
      unique(context, CELL_ARENA, structures.getOrThrow(DMZStructures.CELL_ARENA), 13572468, biomes.getOrThrow(MainTags.Biomes.IS_PLAINSLIKE));
      unique(
         context,
         FRIEZA_SHIP,
         structures.getOrThrow(DMZStructures.FRIEZA_SHIP),
         24681357,
         HolderSet.direct(new Holder[]{biomes.getOrThrow(NamekBiomes.AJISSA_PLAINS)})
      );
      unique(context, PICCOLO_HOUSE, structures.getOrThrow(DMZStructures.PICCOLO_HOUSE), 36925814, biomes.getOrThrow(MainTags.Biomes.IS_LAND));
      unique(
         context,
         OLDKAI_PILLAR,
         structures.getOrThrow(DMZStructures.OLDKAI_PILLAR),
         41258963,
         HolderSet.direct(new Holder[]{biomes.getOrThrow(SacredKaiBiomes.SACREDKAI_PLAINS)})
      );
      unique(context, YAMCHA_HOUSE, structures.getOrThrow(DMZStructures.YAMCHA_HOUSE), 55114477, biomes.getOrThrow(MainTags.Biomes.IS_DESERTLIKE));
      unique(context, TRUNKS_SHIP, structures.getOrThrow(DMZStructures.TRUNKS_SHIP), 66332211, biomes.getOrThrow(MainTags.Biomes.IS_LAND));
      unique(context, VEGETA_POD, structures.getOrThrow(DMZStructures.VEGETA_POD), 77889900, biomes.getOrThrow(MainTags.Biomes.IS_ROCKYBIOME));
   }

   private static void unique(
      BootstrapContext<StructureSet> context, ResourceKey<StructureSet> key, Holder<Structure> structure, int salt, HolderSet<Biome> validBiomes
   ) {
      context.register(
         key,
         new StructureSet(structure, new BiomeAwareUniquePlacement(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, salt, Optional.empty(), validBiomes))
      );
   }

   private static ResourceKey<StructureSet> createKey(String name) {
      return ResourceKey.create(Registries.STRUCTURE_SET, ResourceLocation.fromNamespaceAndPath("dragonminez", name));
   }
}
