package com.dragonminez.common.datagen;

import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainTags;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.ItemTagsProvider;
import net.minecraft.data.tags.TagsProvider.TagLookup;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.Tags.Items;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DMZItemTagGenerator extends ItemTagsProvider {
   public DMZItemTagGenerator(
      PackOutput pOutput,
      CompletableFuture<Provider> pLookupProvider,
      CompletableFuture<TagLookup<Block>> pBlockTags,
      @Nullable ExistingFileHelper existingFileHelper
   ) {
      super(pOutput, pLookupProvider, pBlockTags, "dragonminez", existingFileHelper);
   }

   protected void addTags(@NotNull Provider pProvider) {
      this.tag(ItemTags.LOGS)
         .add(((Block)MainBlocks.NAMEK_AJISSA_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_AJISSA_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_SACRED_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_SACRED_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_SACRED_WOOD.get()).asItem());
      this.tag(ItemTags.LOGS_THAT_BURN)
         .add(((Block)MainBlocks.NAMEK_AJISSA_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_AJISSA_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_SACRED_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_SACRED_WOOD.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_SACRED_WOOD.get()).asItem());
      this.tag(Items.TOOLS).add((Item)MainItems.ARMOR_CRAFTING_KIT.get());
      this.tag(Items.INGOTS)
         .add((Item)MainItems.GETE_SCRAP.get())
         .add((Item)MainItems.GETE_INGOT.get())
         .add((Item)MainItems.KIKONO_SHARD.get())
         .add((Item)MainItems.KIKONO_STRING.get())
         .add((Item)MainItems.KIKONO_CLOTH.get());
      this.tag(ItemTags.PLANKS).add(((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()).asItem()).add(((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()).asItem());
      this.tag(ItemTags.LEAVES).add(((Block)MainBlocks.NAMEK_AJISSA_LEAVES.get()).asItem()).add(((Block)MainBlocks.NAMEK_SACRED_LEAVES.get()).asItem());
      this.tag(ItemTags.STONE_CRAFTING_MATERIALS)
         .add(((Block)MainBlocks.NAMEK_STONE.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_COBBLESTONE.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_DEEPSLATE.get()).asItem());
      this.tag(ItemTags.STONE_TOOL_MATERIALS)
         .add(((Block)MainBlocks.NAMEK_STONE.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_COBBLESTONE.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_DEEPSLATE.get()).asItem());
      this.tag(ItemTags.COAL_ORES).add(((Block)MainBlocks.NAMEK_COAL_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get()).asItem());
      this.tag(ItemTags.IRON_ORES).add(((Block)MainBlocks.NAMEK_IRON_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get()).asItem());
      this.tag(ItemTags.GOLD_ORES).add(((Block)MainBlocks.NAMEK_GOLD_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get()).asItem());
      this.tag(ItemTags.REDSTONE_ORES)
         .add(((Block)MainBlocks.NAMEK_REDSTONE_ORE.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get()).asItem());
      this.tag(ItemTags.LAPIS_ORES).add(((Block)MainBlocks.NAMEK_LAPIS_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get()).asItem());
      this.tag(ItemTags.DIAMOND_ORES).add(((Block)MainBlocks.NAMEK_DIAMOND_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get()).asItem());
      this.tag(ItemTags.EMERALD_ORES).add(((Block)MainBlocks.NAMEK_EMERALD_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get()).asItem());
      this.tag(ItemTags.COPPER_ORES).add(((Block)MainBlocks.NAMEK_COPPER_ORE.get()).asItem()).add(((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get()).asItem());
      this.tag(ItemTags.FLOWERS)
         .add(((Block)MainBlocks.CHRYSANTHEMUM_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.MARIGOLD_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.AMARYLLIS_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.TRILLIUM_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.SACRED_MARIGOLD_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.SACRED_AMARYLLIS_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.get()).asItem())
         .add(((Block)MainBlocks.SACRED_TRILLIUM_FLOWER.get()).asItem());
      this.tag(MainTags.Items.NAMEK_ALOG)
         .add(((Block)MainBlocks.NAMEK_AJISSA_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get()).asItem());
      this.tag(MainTags.Items.NAMEK_SLOG)
         .add(((Block)MainBlocks.NAMEK_SACRED_LOG.get()).asItem())
         .add(((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get()).asItem());
      this.tag(MainTags.Items.WEIGHTED_ITEMS)
         .add((Item)MainItems.WEIGHT_TURTLE_SHELL.get())
         .add((Item)MainItems.WORKOUT_WEIGHTS.get())
         .add((Item)MainItems.WEIGHT_PICCOLO_CAPE.get());
   }
}
