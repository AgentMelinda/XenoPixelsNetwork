package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainTags;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags.Blocks;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;

public class DMZBlockTagGenerator extends BlockTagsProvider {
   public DMZBlockTagGenerator(PackOutput output, CompletableFuture<Provider> lookupProvider, @Nullable ExistingFileHelper existingFileHelper) {
      super(output, lookupProvider, "dragonminez", existingFileHelper);
   }

   protected void addTags(@NotNull Provider pProvider) {
      this.tag(BlockTags.FROG_PREFER_JUMP_TO).add((Block)MainBlocks.LOTUS_FLOWER.get());
      this.tag(BlockTags.INSIDE_STEP_SOUND_BLOCKS).add((Block)MainBlocks.LOTUS_FLOWER.get());
      this.tag(BlockTags.CLIMBABLE).add((Block)MainBlocks.INVISIBLE_LADDER_BLOCK.get());
      this.tag(BlockTags.NEEDS_STONE_TOOL)
         .add((Block)MainBlocks.NAMEK_IRON_ORE.get())
         .add((Block)MainBlocks.NAMEK_COPPER_ORE.get())
         .add((Block)MainBlocks.NAMEK_LAPIS_ORE.get())
         .add((Block)MainBlocks.NAMEK_COAL_ORE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get());
      this.tag(BlockTags.NEEDS_IRON_TOOL)
         .add((Block)MainBlocks.NAMEK_DIAMOND_ORE.get())
         .add((Block)MainBlocks.NAMEK_GOLD_ORE.get())
         .add((Block)MainBlocks.NAMEK_REDSTONE_ORE.get())
         .add((Block)MainBlocks.NAMEK_EMERALD_ORE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get())
         .add((Block)MainBlocks.FUEL_GENERATOR.get());
      this.tag(BlockTags.NEEDS_DIAMOND_TOOL)
         .add((Block)MainBlocks.NAMEK_KIKONO_ORE.get())
         .add((Block)MainBlocks.KIKONO_BLOCK.get())
         .add((Block)MainBlocks.KIKONO_STATION.get())
         .add((Block)MainBlocks.GRAVITY_DEVICE.get());
      this.tag(Blocks.NEEDS_NETHERITE_TOOL).add((Block)MainBlocks.GETE_ORE.get()).add((Block)MainBlocks.GETE_BLOCK.get());
      this.tag(BlockTags.MINEABLE_WITH_AXE)
         .add((Block)MainBlocks.NAMEK_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_WOOD.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_DOOR.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_TRAPDOOR.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_SLAB.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_FENCE.get())
         .add((Block)MainBlocks.NAMEK_AJISSA_FENCE_GATE.get())
         .add((Block)MainBlocks.NAMEK_SACRED_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get())
         .add((Block)MainBlocks.NAMEK_SACRED_WOOD.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_SACRED_WOOD.get())
         .add((Block)MainBlocks.NAMEK_SACRED_PLANKS.get())
         .add((Block)MainBlocks.NAMEK_SACRED_DOOR.get())
         .add((Block)MainBlocks.NAMEK_SACRED_TRAPDOOR.get())
         .add((Block)MainBlocks.NAMEK_SACRED_SLAB.get())
         .add((Block)MainBlocks.NAMEK_SACRED_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_SACRED_FENCE.get())
         .add((Block)MainBlocks.NAMEK_SACRED_FENCE_GATE.get());
      this.tag(BlockTags.MINEABLE_WITH_HOE).add((Block)MainBlocks.NAMEK_AJISSA_LEAVES.get()).add((Block)MainBlocks.NAMEK_SACRED_LEAVES.get());
      this.tag(BlockTags.MINEABLE_WITH_PICKAXE)
         .add((Block)MainBlocks.NAMEK_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_STONE.get())
         .add((Block)MainBlocks.NAMEK_STONE_SLAB.get())
         .add((Block)MainBlocks.NAMEK_STONE_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_STONE_WALL.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE_SLAB.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_SLAB.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_WALL.get())
         .add((Block)MainBlocks.ROCKY_STONE.get())
         .add((Block)MainBlocks.ROCKY_STONE_SLAB.get())
         .add((Block)MainBlocks.ROCKY_STONE_STAIRS.get())
         .add((Block)MainBlocks.ROCKY_STONE_WALL.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_SLAB.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_STAIRS.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_WALL.get())
         .add((Block)MainBlocks.NAMEK_COAL_ORE.get())
         .add((Block)MainBlocks.NAMEK_IRON_ORE.get())
         .add((Block)MainBlocks.NAMEK_COPPER_ORE.get())
         .add((Block)MainBlocks.NAMEK_LAPIS_ORE.get())
         .add((Block)MainBlocks.NAMEK_GOLD_ORE.get())
         .add((Block)MainBlocks.NAMEK_DIAMOND_ORE.get())
         .add((Block)MainBlocks.NAMEK_REDSTONE_ORE.get())
         .add((Block)MainBlocks.NAMEK_EMERALD_ORE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get())
         .add((Block)MainBlocks.GETE_ORE.get())
         .add((Block)MainBlocks.GETE_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_KIKONO_ORE.get())
         .add((Block)MainBlocks.KIKONO_BLOCK.get())
         .add((Block)MainBlocks.KIKONO_STATION.get())
         .add((Block)MainBlocks.FUEL_GENERATOR.get())
         .add((Block)MainBlocks.GRAVITY_DEVICE.get());

      for (DragonBallSetDefinition setDefinition : DragonBallDefinitions.getBallSets()) {
         for (DeferredHolder<Block, ? extends Block> block : MainBlocks.getDragonBallBlocks(setDefinition.getId()).values()) {
            this.tag(BlockTags.MINEABLE_WITH_PICKAXE).add((Block)block.get());
         }
      }

      this.tag(BlockTags.MINEABLE_WITH_SHOVEL)
         .add((Block)MainBlocks.NAMEK_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_DIRT.get())
         .add((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get())
         .add((Block)MainBlocks.ROCKY_DIRT.get());
      this.tag(BlockTags.LOGS)
         .add((Block)MainBlocks.NAMEK_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_SACRED_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get());
      this.tag(BlockTags.LOGS_THAT_BURN)
         .add((Block)MainBlocks.NAMEK_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get())
         .add((Block)MainBlocks.NAMEK_SACRED_LOG.get())
         .add((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get());
      this.tag(BlockTags.PLANKS).add((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()).add((Block)MainBlocks.NAMEK_SACRED_PLANKS.get());
      this.tag(BlockTags.WOODEN_BUTTONS).add((Block)MainBlocks.NAMEK_AJISSA_BUTTON.get()).add((Block)MainBlocks.NAMEK_SACRED_BUTTON.get());
      this.tag(BlockTags.BUTTONS).add((Block)MainBlocks.NAMEK_AJISSA_BUTTON.get()).add((Block)MainBlocks.NAMEK_SACRED_BUTTON.get());
      this.tag(BlockTags.LEAVES).add((Block)MainBlocks.NAMEK_AJISSA_LEAVES.get()).add((Block)MainBlocks.NAMEK_SACRED_LEAVES.get());
      this.tag(BlockTags.WOODEN_DOORS).add((Block)MainBlocks.NAMEK_AJISSA_DOOR.get()).add((Block)MainBlocks.NAMEK_SACRED_DOOR.get());
      this.tag(BlockTags.DOORS).add((Block)MainBlocks.NAMEK_AJISSA_DOOR.get()).add((Block)MainBlocks.NAMEK_SACRED_DOOR.get());
      this.tag(BlockTags.WOODEN_SLABS).add((Block)MainBlocks.NAMEK_AJISSA_SLAB.get()).add((Block)MainBlocks.NAMEK_SACRED_SLAB.get());
      this.tag(BlockTags.SLABS)
         .add((Block)MainBlocks.NAMEK_AJISSA_SLAB.get())
         .add((Block)MainBlocks.NAMEK_SACRED_SLAB.get())
         .add((Block)MainBlocks.NAMEK_STONE_SLAB.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE_SLAB.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_SLAB.get())
         .add((Block)MainBlocks.ROCKY_STONE_SLAB.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_SLAB.get());
      this.tag(BlockTags.WOODEN_STAIRS).add((Block)MainBlocks.NAMEK_AJISSA_STAIRS.get()).add((Block)MainBlocks.NAMEK_SACRED_STAIRS.get());
      this.tag(BlockTags.STAIRS)
         .add((Block)MainBlocks.NAMEK_AJISSA_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_SACRED_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_STONE_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE_STAIRS.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_STAIRS.get())
         .add((Block)MainBlocks.ROCKY_STONE_STAIRS.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_STAIRS.get());
      this.tag(BlockTags.WOODEN_TRAPDOORS).add((Block)MainBlocks.NAMEK_AJISSA_TRAPDOOR.get()).add((Block)MainBlocks.NAMEK_SACRED_TRAPDOOR.get());
      this.tag(BlockTags.TRAPDOORS).add((Block)MainBlocks.NAMEK_AJISSA_TRAPDOOR.get()).add((Block)MainBlocks.NAMEK_SACRED_TRAPDOOR.get());
      this.tag(BlockTags.WOODEN_FENCES).add((Block)MainBlocks.NAMEK_AJISSA_FENCE.get()).add((Block)MainBlocks.NAMEK_SACRED_FENCE.get());
      this.tag(BlockTags.FENCES).add((Block)MainBlocks.NAMEK_AJISSA_FENCE.get()).add((Block)MainBlocks.NAMEK_SACRED_FENCE.get());
      this.tag(BlockTags.WOODEN_PRESSURE_PLATES)
         .add((Block)MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE.get())
         .add((Block)MainBlocks.NAMEK_SACRED_PRESSURE_PLATE.get());
      this.tag(BlockTags.PRESSURE_PLATES).add((Block)MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE.get()).add((Block)MainBlocks.NAMEK_SACRED_PRESSURE_PLATE.get());
      this.tag(BlockTags.WALLS)
         .add((Block)MainBlocks.NAMEK_STONE_WALL.get())
         .add((Block)MainBlocks.NAMEK_COBBLESTONE_WALL.get())
         .add((Block)MainBlocks.NAMEK_DEEPSLATE_WALL.get())
         .add((Block)MainBlocks.ROCKY_STONE_WALL.get())
         .add((Block)MainBlocks.ROCKY_COBBLESTONE_WALL.get());
      this.tag(BlockTags.ANIMALS_SPAWNABLE_ON)
         .add((Block)MainBlocks.NAMEK_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_DIRT.get())
         .add((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get())
         .add((Block)MainBlocks.ROCKY_DIRT.get());
      this.tag(BlockTags.FROGS_SPAWNABLE_ON)
         .add((Block)MainBlocks.NAMEK_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get())
         .add((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get());
      this.tag(BlockTags.BEACON_BASE_BLOCKS).add((Block)MainBlocks.GETE_BLOCK.get()).add((Block)MainBlocks.KIKONO_BLOCK.get());
      this.tag(BlockTags.DIRT)
         .add((Block)MainBlocks.NAMEK_DIRT.get())
         .add((Block)MainBlocks.NAMEK_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get())
         .add((Block)MainBlocks.ROCKY_DIRT.get())
         .add((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get());
      this.tag(BlockTags.COAL_ORES).add((Block)MainBlocks.NAMEK_COAL_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get());
      this.tag(BlockTags.IRON_ORES).add((Block)MainBlocks.NAMEK_IRON_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get());
      this.tag(BlockTags.COPPER_ORES).add((Block)MainBlocks.NAMEK_COPPER_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get());
      this.tag(BlockTags.LAPIS_ORES).add((Block)MainBlocks.NAMEK_LAPIS_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get());
      this.tag(BlockTags.GOLD_ORES).add((Block)MainBlocks.NAMEK_GOLD_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get());
      this.tag(BlockTags.DIAMOND_ORES).add((Block)MainBlocks.NAMEK_DIAMOND_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get());
      this.tag(BlockTags.REDSTONE_ORES).add((Block)MainBlocks.NAMEK_REDSTONE_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get());
      this.tag(BlockTags.EMERALD_ORES).add((Block)MainBlocks.NAMEK_EMERALD_ORE.get()).add((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get());
      this.tag(BlockTags.FLOWERS)
         .add((Block)MainBlocks.CHRYSANTHEMUM_FLOWER.get())
         .add((Block)MainBlocks.MARIGOLD_FLOWER.get())
         .add((Block)MainBlocks.AMARYLLIS_FLOWER.get())
         .add((Block)MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.get())
         .add((Block)MainBlocks.TRILLIUM_FLOWER.get())
         .add((Block)MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get())
         .add((Block)MainBlocks.SACRED_MARIGOLD_FLOWER.get())
         .add((Block)MainBlocks.SACRED_AMARYLLIS_FLOWER.get())
         .add((Block)MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.get())
         .add((Block)MainBlocks.SACRED_TRILLIUM_FLOWER.get());
      this.tag(BlockTags.FLOWER_POTS)
         .add((Block)MainBlocks.POTTED_CHRYSANTHEMUM_FLOWER.get())
         .add((Block)MainBlocks.POTTED_MARIGOLD_FLOWER.get())
         .add((Block)MainBlocks.POTTED_AMARYLLIS_FLOWER.get())
         .add((Block)MainBlocks.POTTED_CATHARANTHUS_ROSEUS_FLOWER.get())
         .add((Block)MainBlocks.POTTED_TRILLIUM_FLOWER.get())
         .add((Block)MainBlocks.POTTED_SACRED_CHRYSANTHEMUM_FLOWER.get())
         .add((Block)MainBlocks.POTTED_SACRED_MARIGOLD_FLOWER.get())
         .add((Block)MainBlocks.POTTED_AMARYLLIS_FLOWER.get())
         .add((Block)MainBlocks.POTTED_CATHARANTHUS_ROSEUS_FLOWER.get())
         .add((Block)MainBlocks.POTTED_TRILLIUM_FLOWER.get());
      this.tag(BlockTags.SAPLINGS).add((Block)MainBlocks.NAMEK_SACRED_SAPLING.get()).add((Block)MainBlocks.NAMEK_AJISSA_SAPLING.get());
      this.tag(BlockTags.VALID_SPAWN)
         .add((Block)MainBlocks.NAMEK_DIRT.get())
         .add((Block)MainBlocks.NAMEK_GRASS_BLOCK.get())
         .add((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get())
         .add((Block)MainBlocks.ROCKY_DIRT.get())
         .add((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get())
         .add((Block)MainBlocks.ROCKY_STONE.get());
      this.tag(MainTags.Blocks.NAMEK_ALOG).add((Block)MainBlocks.NAMEK_AJISSA_LOG.get()).add((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get());
      this.tag(MainTags.Blocks.NAMEK_SLOG).add((Block)MainBlocks.NAMEK_SACRED_LOG.get()).add((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get());
      this.tag(MainTags.Blocks.NAMEKSTONE_REPLACEABLES).replace(false).add((Block)MainBlocks.NAMEK_STONE.get());
      this.tag(MainTags.Blocks.NAMEKDEEPSLATE_REPLACEABLES).replace(false).add((Block)MainBlocks.NAMEK_DEEPSLATE.get());
   }
}
