package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.block.custom.KikonoStationBlock;
import java.util.Set;
import net.minecraft.core.HolderLookup.Provider;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.LootTable.Builder;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DMZBlockLootTables extends BlockLootSubProvider {
   public DMZBlockLootTables(Provider registries) {
      super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
   }

   protected void generate() {
      for (DragonBallSetDefinition setDefinition : DragonBallDefinitions.getBallSets()) {
         MainBlocks.getDragonBallBlocks(setDefinition.getId()).values().forEach(block -> this.dropSelf((Block)block.get()));
      }

      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_LOG.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_WOOD.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get());
      this.add((Block)MainBlocks.NAMEK_AJISSA_DOOR.get(), block -> this.createDoorTable((Block)MainBlocks.NAMEK_AJISSA_DOOR.get()));
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_TRAPDOOR.get());
      this.add((Block)MainBlocks.NAMEK_AJISSA_SLAB.get(), block -> this.createSlabItemTable((Block)MainBlocks.NAMEK_AJISSA_SLAB.get()));
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_STAIRS.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_FENCE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_FENCE_GATE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_BUTTON.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_SAPLING.get());
      this.dropSelf((Block)MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_LOG.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_WOOD.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STRIPPED_SACRED_WOOD.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_PLANKS.get());
      this.add((Block)MainBlocks.NAMEK_SACRED_DOOR.get(), block -> this.createDoorTable((Block)MainBlocks.NAMEK_SACRED_DOOR.get()));
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_TRAPDOOR.get());
      this.add((Block)MainBlocks.NAMEK_SACRED_SLAB.get(), block -> this.createSlabItemTable((Block)MainBlocks.NAMEK_SACRED_SLAB.get()));
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_STAIRS.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_FENCE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_FENCE_GATE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_BUTTON.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_SAPLING.get());
      this.dropSelf((Block)MainBlocks.NAMEK_SACRED_PRESSURE_PLATE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_BLOCK.get());
      this.dropSelf((Block)MainBlocks.NAMEK_DIRT.get());
      this.dropSelf((Block)MainBlocks.GETE_BLOCK.get());
      this.dropSelf((Block)MainBlocks.KIKONO_BLOCK.get());
      this.dropSelf((Block)MainBlocks.NAMEK_COBBLESTONE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STONE_STAIRS.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STONE_SLAB.get());
      this.dropSelf((Block)MainBlocks.NAMEK_STONE_WALL.get());
      this.dropSelf((Block)MainBlocks.NAMEK_COBBLESTONE_STAIRS.get());
      this.dropSelf((Block)MainBlocks.NAMEK_COBBLESTONE_SLAB.get());
      this.dropSelf((Block)MainBlocks.NAMEK_COBBLESTONE_WALL.get());
      this.dropSelf((Block)MainBlocks.NAMEK_DEEPSLATE.get());
      this.dropSelf((Block)MainBlocks.NAMEK_DEEPSLATE_STAIRS.get());
      this.dropSelf((Block)MainBlocks.NAMEK_DEEPSLATE_SLAB.get());
      this.dropSelf((Block)MainBlocks.NAMEK_DEEPSLATE_WALL.get());
      this.dropSelf((Block)MainBlocks.ROCKY_DIRT.get());
      this.dropSelf((Block)MainBlocks.ROCKY_STONE_STAIRS.get());
      this.dropSelf((Block)MainBlocks.ROCKY_STONE_SLAB.get());
      this.dropSelf((Block)MainBlocks.ROCKY_STONE_WALL.get());
      this.dropSelf((Block)MainBlocks.ROCKY_COBBLESTONE.get());
      this.dropSelf((Block)MainBlocks.ROCKY_COBBLESTONE_STAIRS.get());
      this.dropSelf((Block)MainBlocks.ROCKY_COBBLESTONE_SLAB.get());
      this.dropSelf((Block)MainBlocks.ROCKY_COBBLESTONE_WALL.get());
      this.dropSelf((Block)MainBlocks.GETE_ORE.get());
      this.dropSelf((Block)MainBlocks.TIME_CHAMBER_BLOCK.get());
      this.add((Block)MainBlocks.KIKONO_STATION.get(), block -> this.createSinglePropConditionTable(block, KikonoStationBlock.HALF, DoubleBlockHalf.LOWER));
      this.dropSelf((Block)MainBlocks.ENERGY_CABLE.get());
      this.dropSelf((Block)MainBlocks.FUEL_GENERATOR.get());
      this.dropSelf((Block)MainBlocks.GRAVITY_DEVICE.get());
      this.add((Block)MainBlocks.NAMEK_DIAMOND_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DIAMOND_ORE.get(), Items.DIAMOND));
      this.add((Block)MainBlocks.NAMEK_GOLD_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_GOLD_ORE.get(), Items.RAW_GOLD));
      this.add((Block)MainBlocks.NAMEK_IRON_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_IRON_ORE.get(), Items.RAW_IRON));
      this.add((Block)MainBlocks.NAMEK_LAPIS_ORE.get(), block -> this.MultiOreDrop((Block)MainBlocks.NAMEK_LAPIS_ORE.get(), Items.LAPIS_LAZULI));
      this.add((Block)MainBlocks.NAMEK_REDSTONE_ORE.get(), block -> this.MultiOreDrop((Block)MainBlocks.NAMEK_REDSTONE_ORE.get(), Items.REDSTONE));
      this.add((Block)MainBlocks.NAMEK_COAL_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_COAL_ORE.get(), Items.COAL));
      this.add((Block)MainBlocks.NAMEK_EMERALD_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_EMERALD_ORE.get(), Items.EMERALD));
      this.add((Block)MainBlocks.NAMEK_COPPER_ORE.get(), block -> this.CopperOreDrop((Block)MainBlocks.NAMEK_COPPER_ORE.get(), Items.RAW_COPPER));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_DIAMOND.get(), Items.DIAMOND));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_GOLD.get(), Items.RAW_GOLD));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_IRON.get(), Items.RAW_IRON));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get(), block -> this.MultiOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_LAPIS.get(), Items.LAPIS_LAZULI));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get(), block -> this.MultiOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_REDSTONE.get(), Items.REDSTONE));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_COAL.get(), Items.COAL));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_EMERALD.get(), Items.EMERALD));
      this.add((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get(), block -> this.CopperOreDrop((Block)MainBlocks.NAMEK_DEEPSLATE_COPPER.get(), Items.RAW_COPPER));
      this.add(
         (Block)MainBlocks.NAMEK_KIKONO_ORE.get(), block -> this.SingleOreDrop((Block)MainBlocks.NAMEK_KIKONO_ORE.get(), (Item)MainItems.KIKONO_SHARD.get())
      );
      this.add(
         (Block)MainBlocks.NAMEK_STONE.get(), block -> this.SilkTouchBlockDrop((Block)MainBlocks.NAMEK_STONE.get(), (Block)MainBlocks.NAMEK_COBBLESTONE.get())
      );
      this.add(
         (Block)MainBlocks.ROCKY_STONE.get(), block -> this.SilkTouchBlockDrop((Block)MainBlocks.ROCKY_STONE.get(), (Block)MainBlocks.ROCKY_COBBLESTONE.get())
      );
      this.add(
         (Block)MainBlocks.NAMEK_AJISSA_LEAVES.get(),
         block -> this.createLeavesDrops(block, (Block)MainBlocks.NAMEK_AJISSA_SAPLING.get(), NORMAL_LEAVES_SAPLING_CHANCES)
      );
      this.add(
         (Block)MainBlocks.NAMEK_SACRED_LEAVES.get(),
         block -> this.createLeavesDrops(block, (Block)MainBlocks.NAMEK_SACRED_SAPLING.get(), NORMAL_LEAVES_SAPLING_CHANCES)
      );
      this.add(
         (Block)MainBlocks.NAMEK_GRASS_BLOCK.get(),
         block -> this.SilkTouchBlockDrop((Block)MainBlocks.NAMEK_GRASS_BLOCK.get(), (Block)MainBlocks.NAMEK_DIRT.get())
      );
      this.add(
         (Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get(),
         block -> this.SilkTouchBlockDrop((Block)MainBlocks.NAMEK_SACRED_GRASS_BLOCK.get(), (Block)MainBlocks.NAMEK_DIRT.get())
      );
      this.add(
         (Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get(),
         block -> this.SilkTouchBlockDrop((Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get(), (Block)MainBlocks.ROCKY_DIRT.get())
      );
      this.add((Block)MainBlocks.NAMEK_GRASS.get(), block -> this.ShearsOnlyDrop((Block)MainBlocks.NAMEK_GRASS.get()));
      this.add((Block)MainBlocks.NAMEK_SACRED_GRASS.get(), block -> this.ShearsOnlyDrop((Block)MainBlocks.NAMEK_SACRED_GRASS.get()));
      this.add((Block)MainBlocks.NAMEK_FERN.get(), block -> this.ShearsOnlyDrop((Block)MainBlocks.NAMEK_FERN.get()));
      this.add((Block)MainBlocks.POTTED_NAMEK_FERN.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.NAMEK_FERN.get()));
      this.add((Block)MainBlocks.SACRED_FERN.get(), block -> this.ShearsOnlyDrop((Block)MainBlocks.SACRED_FERN.get()));
      this.add((Block)MainBlocks.POTTED_SACRED_FERN.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_FERN.get()));
      this.dropSelf((Block)MainBlocks.CHRYSANTHEMUM_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_CHRYSANTHEMUM_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.CHRYSANTHEMUM_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.AMARYLLIS_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_AMARYLLIS_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.AMARYLLIS_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.MARIGOLD_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_MARIGOLD_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.MARIGOLD_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_CATHARANTHUS_ROSEUS_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.TRILLIUM_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_TRILLIUM_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.TRILLIUM_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.LOTUS_FLOWER.get());
      this.dropSelf((Block)MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get());
      this.add(
         (Block)MainBlocks.POTTED_SACRED_CHRYSANTHEMUM_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get())
      );
      this.dropSelf((Block)MainBlocks.SACRED_AMARYLLIS_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_SACRED_AMARYLLIS_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_AMARYLLIS_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.SACRED_MARIGOLD_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_SACRED_MARIGOLD_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_MARIGOLD_FLOWER.get()));
      this.dropSelf((Block)MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.get());
      this.add(
         (Block)MainBlocks.POTTED_SACRED_CATHARANTHUS_ROSEUS_FLOWER.get(),
         this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.get())
      );
      this.dropSelf((Block)MainBlocks.SACRED_TRILLIUM_FLOWER.get());
      this.add((Block)MainBlocks.POTTED_SACRED_TRILLIUM_FLOWER.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.SACRED_TRILLIUM_FLOWER.get()));
      this.add((Block)MainBlocks.POTTED_AJISSA_SAPLING.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.NAMEK_AJISSA_SAPLING.get()));
      this.add((Block)MainBlocks.POTTED_SACRED_SAPLING.get(), this.createPotFlowerItemTable((ItemLike)MainBlocks.NAMEK_SACRED_SAPLING.get()));
   }

   protected Builder MultiOreDrop(Block pBlock, Item item) {
      return this.createSilkTouchDispatchTable(
         pBlock,
         (net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.Builder)this.applyExplosionDecay(
            pBlock,
            LootItem.lootTableItem(item)
               .apply(SetItemCountFunction.setCount(UniformGenerator.between(1.0F, 5.0F)))
               .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
         )
      );
   }

   protected Builder CopperOreDrop(Block pBlock, Item item) {
      return this.createSilkTouchDispatchTable(
         pBlock,
         (net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.Builder)this.applyExplosionDecay(
            pBlock,
            LootItem.lootTableItem(item)
               .apply(SetItemCountFunction.setCount(UniformGenerator.between(2.0F, 6.0F)))
               .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
         )
      );
   }

   protected Builder SingleOreDrop(Block pBlock, Item item) {
      return this.createSilkTouchDispatchTable(
         pBlock,
         (net.minecraft.world.level.storage.loot.entries.LootPoolEntryContainer.Builder)this.applyExplosionDecay(
            pBlock,
            LootItem.lootTableItem(item)
               .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
               .apply(SetItemCountFunction.setCount(ConstantValue.exactly(1.0F)))
         )
      );
   }

   protected Builder SilkTouchBlockDrop(Block pBlock, Block pDrop) {
      return LootTable.lootTable()
         .withPool(
            LootPool.lootPool()
               .setRolls(ConstantValue.exactly(1.0F))
               .add(LootItem.lootTableItem(pBlock).when(this.hasSilkTouch()))
               .add(LootItem.lootTableItem(pDrop).when(this.doesNotHaveSilkTouch()))
         );
   }

   protected Builder ShearsOnlyDrop(Block pBlock) {
      return LootTable.lootTable()
         .withPool(
            LootPool.lootPool()
               .setRolls(ConstantValue.exactly(1.0F))
               .add(LootItem.lootTableItem(pBlock).when(HAS_SHEARS))
               .add(EmptyLootItem.emptyItem().when(HAS_SHEARS.invert()))
         );
   }

   protected Iterable<Block> getKnownBlocks() {
      return () -> MainBlocks.BLOCK_REGISTER.getEntries().stream().<Block>map(DeferredHolder::get).iterator();
   }
}
