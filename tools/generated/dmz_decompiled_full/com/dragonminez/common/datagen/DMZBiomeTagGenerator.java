package com.dragonminez.common.datagen;

import com.dragonminez.common.init.MainTags;
import com.dragonminez.server.world.biome.HTCBiomes;
import com.dragonminez.server.world.biome.NamekBiomes;
import com.dragonminez.server.world.biome.OtherworldBiomes;
import com.dragonminez.server.world.biome.OverworldBiomes;
import com.dragonminez.server.world.biome.SacredKaiBiomes;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.BiomeTagsProvider;
import net.minecraft.tags.BiomeTags;
import net.neoforged.neoforge.common.Tags.Biomes;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

public class DMZBiomeTagGenerator extends BiomeTagsProvider {
   public DMZBiomeTagGenerator(PackOutput output, CompletableFuture<Provider> pProvider, @Nullable ExistingFileHelper existingFileHelper) {
      super(output, pProvider, "dragonminez", existingFileHelper);
   }

   protected void addTags(Provider provider) {
      this.tag(MainTags.Biomes.IS_NAMEK).replace(false).add(NamekBiomes.AJISSA_PLAINS).add(NamekBiomes.SACRED_LAND).add(NamekBiomes.NAMEKIAN_RIVERS);
      this.tag(MainTags.Biomes.IS_SACREDLAND).replace(false).add(NamekBiomes.SACRED_LAND);
      this.tag(MainTags.Biomes.IS_HTC).replace(false).add(HTCBiomes.TIME_CHAMBER);
      this.tag(MainTags.Biomes.IS_OTHERWORLD).replace(false).add(OtherworldBiomes.OTHERWORLD);
      this.tag(MainTags.Biomes.HAS_DINOSAURS)
         .replace(false)
         .addTag(BiomeTags.HAS_VILLAGE_SAVANNA)
         .addTag(BiomeTags.IS_BADLANDS)
         .addTag(BiomeTags.IS_MOUNTAIN)
         .addTag(BiomeTags.IS_HILL)
         .add(OverworldBiomes.ROCKY);
      this.tag(MainTags.Biomes.HAS_SABERTOOTH)
         .replace(false)
         .addTag(BiomeTags.HAS_VILLAGE_SAVANNA)
         .addTag(BiomeTags.IS_SAVANNA)
         .addTag(BiomeTags.HAS_VILLAGE_PLAINS)
         .addTag(BiomeTags.IS_FOREST)
         .addTag(BiomeTags.HAS_WOODLAND_MANSION)
         .addTag(BiomeTags.IS_JUNGLE);
      this.tag(MainTags.Biomes.HAS_ROBOTS)
         .replace(false)
         .addTag(BiomeTags.HAS_VILLAGE_SAVANNA)
         .addTag(BiomeTags.HAS_VILLAGE_PLAINS)
         .addTag(BiomeTags.HAS_VILLAGE_SNOWY);
      this.tag(MainTags.Biomes.IS_ROCKYBIOME).replace(false).add(OverworldBiomes.ROCKY);
      this.tag(MainTags.Biomes.HAS_SAIBAMANS).replace(false).add(OverworldBiomes.ROCKY);
      this.tag(MainTags.Biomes.IS_SACREDKAI)
         .replace(false)
         .add(SacredKaiBiomes.SACREDKAI_PLAINS)
         .add(SacredKaiBiomes.SACREDKAI_HILLS)
         .add(SacredKaiBiomes.SACREDKAI_RIVERS);
      this.tag(MainTags.Biomes.IS_LAND)
         .replace(false)
         .addTag(BiomeTags.HAS_VILLAGE_PLAINS)
         .addTag(BiomeTags.HAS_VILLAGE_DESERT)
         .addTag(BiomeTags.HAS_VILLAGE_SAVANNA)
         .addTag(BiomeTags.HAS_VILLAGE_SNOWY)
         .addTag(BiomeTags.HAS_VILLAGE_TAIGA)
         .addTag(BiomeTags.IS_FOREST)
         .addTag(BiomeTags.IS_JUNGLE)
         .addTag(BiomeTags.IS_BADLANDS)
         .addTag(BiomeTags.IS_TAIGA)
         .addTag(BiomeTags.IS_HILL)
         .addTag(BiomeTags.IS_MOUNTAIN)
         .add(OverworldBiomes.ROCKY)
         .addTag(Biomes.IS_PLAINS)
         .addTag(Biomes.IS_DESERT)
         .addTag(Biomes.IS_SANDY)
         .addTag(Biomes.IS_SNOWY)
         .addTag(Biomes.IS_SWAMP)
         .addTag(Biomes.IS_MOUNTAIN)
         .addTag(Biomes.IS_PLATEAU)
         .addTag(Biomes.IS_MOUNTAIN_SLOPE)
         .addTag(Biomes.IS_MOUNTAIN_PEAK)
         .addTag(Biomes.IS_LUSH)
         .addTag(Biomes.IS_CONIFEROUS_TREE);
      this.tag(MainTags.Biomes.IS_MOUNTAINLIKE).replace(false).addTag(BiomeTags.IS_MOUNTAIN).addTag(Biomes.IS_MOUNTAIN);
      this.tag(MainTags.Biomes.IS_PLAINSLIKE).replace(false).addTag(BiomeTags.HAS_VILLAGE_PLAINS).addTag(Biomes.IS_PLAINS);
      this.tag(MainTags.Biomes.IS_DESERTLIKE).replace(false).addTag(BiomeTags.HAS_VILLAGE_DESERT).addTag(Biomes.IS_DESERT);
   }
}
