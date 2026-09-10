package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetAssetDefinition;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.init.MainBlocks;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile.UncheckedModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DMZBlockStateProvider extends BlockStateProvider {
   public DMZBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
      super(output, "dragonminez", existingFileHelper);
   }

   public void registerStatesAndModels() {
      this.blockWithItem(MainBlocks.NAMEK_BLOCK);
      this.blockWithItem(MainBlocks.NAMEK_DIRT);
      this.grassBlock(MainBlocks.NAMEK_GRASS_BLOCK);
      this.grassBlock(MainBlocks.NAMEK_SACRED_GRASS_BLOCK);
      this.grassBlock(MainBlocks.SACRED_PLANET_GRASS_BLOCK);
      this.blockWithItem(MainBlocks.NAMEK_STONE);
      this.blockWithItem(MainBlocks.NAMEK_COBBLESTONE);
      this.blockWithItem(MainBlocks.ROCKY_DIRT);
      this.blockWithItem(MainBlocks.ROCKY_STONE);
      this.blockWithItem(MainBlocks.ROCKY_COBBLESTONE);
      this.blockWithItem(MainBlocks.TIME_CHAMBER_BLOCK);
      this.axisBlock(
         (RotatedPillarBlock)MainBlocks.NAMEK_DEEPSLATE.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get())
      );
      this.blockWithItem(MainBlocks.TIME_CHAMBER_PORTAL);
      this.blockWithItem(MainBlocks.OTHERWORLD_CLOUD);
      this.blockWithItem(MainBlocks.NAMEK_AJISSA_PLANKS);
      this.leavesBlock(MainBlocks.NAMEK_AJISSA_LEAVES);
      this.blockWithItem(MainBlocks.NAMEK_AJISSA_LOG);
      this.blockWithItem(MainBlocks.NAMEK_STRIPPED_AJISSA_LOG);
      this.blockWithItem(MainBlocks.NAMEK_AJISSA_WOOD);
      this.blockWithItem(MainBlocks.NAMEK_STRIPPED_AJISSA_WOOD);
      this.blockWithItem(MainBlocks.NAMEK_SACRED_PLANKS);
      this.leavesBlock(MainBlocks.NAMEK_SACRED_LEAVES);
      this.blockWithItem(MainBlocks.NAMEK_SACRED_LOG);
      this.blockWithItem(MainBlocks.NAMEK_STRIPPED_SACRED_LOG);
      this.blockWithItem(MainBlocks.NAMEK_SACRED_WOOD);
      this.blockWithItem(MainBlocks.NAMEK_STRIPPED_SACRED_WOOD);
      this.saplingBlock(MainBlocks.NAMEK_AJISSA_SAPLING);
      this.saplingBlock(MainBlocks.NAMEK_SACRED_SAPLING);
      this.blockWithItem(MainBlocks.GETE_BLOCK);
      this.blockWithItem(MainBlocks.NAMEK_KIKONO_ORE);
      this.blockWithItem(MainBlocks.KIKONO_BLOCK);
      this.blockWithItem(MainBlocks.NAMEK_DIAMOND_ORE);
      this.blockWithItem(MainBlocks.NAMEK_GOLD_ORE);
      this.blockWithItem(MainBlocks.NAMEK_IRON_ORE);
      this.blockWithItem(MainBlocks.NAMEK_LAPIS_ORE);
      this.blockWithItem(MainBlocks.NAMEK_REDSTONE_ORE);
      this.blockWithItem(MainBlocks.NAMEK_COAL_ORE);
      this.blockWithItem(MainBlocks.NAMEK_EMERALD_ORE);
      this.blockWithItem(MainBlocks.NAMEK_COPPER_ORE);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_DIAMOND);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_GOLD);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_IRON);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_LAPIS);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_REDSTONE);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_COAL);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_EMERALD);
      this.blockWithItem(MainBlocks.NAMEK_DEEPSLATE_COPPER);
      this.stairsBlock((StairBlock)MainBlocks.NAMEK_AJISSA_STAIRS.get(), this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.NAMEK_AJISSA_SLAB.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get())
      );
      this.buttonBlock((ButtonBlock)MainBlocks.NAMEK_AJISSA_BUTTON.get(), this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()));
      this.pressurePlateBlock((PressurePlateBlock)MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE.get(), this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()));
      this.fenceBlock((FenceBlock)MainBlocks.NAMEK_AJISSA_FENCE.get(), this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()));
      this.fenceGateBlock((FenceGateBlock)MainBlocks.NAMEK_AJISSA_FENCE_GATE.get(), this.blockTexture((Block)MainBlocks.NAMEK_AJISSA_PLANKS.get()));
      this.doorBlockWithRenderType(
         (DoorBlock)MainBlocks.NAMEK_AJISSA_DOOR.get(), this.modLoc("block/namek_ajissa_door_bottom"), this.modLoc("block/namek_ajissa_door_top"), "cutout"
      );
      this.trapdoorBlockWithRenderType((TrapDoorBlock)MainBlocks.NAMEK_AJISSA_TRAPDOOR.get(), this.modLoc("block/namek_ajissa_trapdoor"), true, "cutout");
      this.stairsBlock((StairBlock)MainBlocks.NAMEK_SACRED_STAIRS.get(), this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.NAMEK_SACRED_SLAB.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get())
      );
      this.buttonBlock((ButtonBlock)MainBlocks.NAMEK_SACRED_BUTTON.get(), this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()));
      this.pressurePlateBlock((PressurePlateBlock)MainBlocks.NAMEK_SACRED_PRESSURE_PLATE.get(), this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()));
      this.fenceBlock((FenceBlock)MainBlocks.NAMEK_SACRED_FENCE.get(), this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()));
      this.fenceGateBlock((FenceGateBlock)MainBlocks.NAMEK_SACRED_FENCE_GATE.get(), this.blockTexture((Block)MainBlocks.NAMEK_SACRED_PLANKS.get()));
      this.doorBlockWithRenderType(
         (DoorBlock)MainBlocks.NAMEK_SACRED_DOOR.get(), this.modLoc("block/namek_sacred_door_bottom"), this.modLoc("block/namek_sacred_door_top"), "cutout"
      );
      this.trapdoorBlockWithRenderType((TrapDoorBlock)MainBlocks.NAMEK_SACRED_TRAPDOOR.get(), this.modLoc("block/namek_sacred_trapdoor"), true, "cutout");
      this.stairsBlock((StairBlock)MainBlocks.NAMEK_STONE_STAIRS.get(), this.blockTexture((Block)MainBlocks.NAMEK_STONE.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.NAMEK_STONE_SLAB.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_STONE.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_STONE.get())
      );
      this.wallBlock((WallBlock)MainBlocks.NAMEK_STONE_WALL.get(), this.blockTexture((Block)MainBlocks.NAMEK_STONE.get()));
      this.stairsBlock((StairBlock)MainBlocks.NAMEK_COBBLESTONE_STAIRS.get(), this.blockTexture((Block)MainBlocks.NAMEK_COBBLESTONE.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.NAMEK_COBBLESTONE_SLAB.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_COBBLESTONE.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_COBBLESTONE.get())
      );
      this.wallBlock((WallBlock)MainBlocks.NAMEK_COBBLESTONE_WALL.get(), this.blockTexture((Block)MainBlocks.NAMEK_COBBLESTONE.get()));
      this.stairsBlock((StairBlock)MainBlocks.NAMEK_DEEPSLATE_STAIRS.get(), this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.NAMEK_DEEPSLATE_SLAB.get(),
         this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get()),
         this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get())
      );
      this.wallBlock((WallBlock)MainBlocks.NAMEK_DEEPSLATE_WALL.get(), this.blockTexture((Block)MainBlocks.NAMEK_DEEPSLATE.get()));
      this.stairsBlock((StairBlock)MainBlocks.ROCKY_STONE_STAIRS.get(), this.blockTexture((Block)MainBlocks.ROCKY_STONE.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.ROCKY_STONE_SLAB.get(),
         this.blockTexture((Block)MainBlocks.ROCKY_STONE.get()),
         this.blockTexture((Block)MainBlocks.ROCKY_STONE.get())
      );
      this.wallBlock((WallBlock)MainBlocks.ROCKY_STONE_WALL.get(), this.blockTexture((Block)MainBlocks.ROCKY_STONE.get()));
      this.stairsBlock((StairBlock)MainBlocks.ROCKY_COBBLESTONE_STAIRS.get(), this.blockTexture((Block)MainBlocks.ROCKY_COBBLESTONE.get()));
      this.slabBlock(
         (SlabBlock)MainBlocks.ROCKY_COBBLESTONE_SLAB.get(),
         this.blockTexture((Block)MainBlocks.ROCKY_COBBLESTONE.get()),
         this.blockTexture((Block)MainBlocks.ROCKY_COBBLESTONE.get())
      );
      this.wallBlock((WallBlock)MainBlocks.ROCKY_COBBLESTONE_WALL.get(), this.blockTexture((Block)MainBlocks.ROCKY_COBBLESTONE.get()));

      for (DragonBallSetDefinition setDefinition : DragonBallDefinitions.getBallSets()) {
         DragonBallSetAssetDefinition assets = setDefinition.resolveAssetDefinition();

         for (Entry<Integer, DeferredHolder<Block, ? extends Block>> entry : MainBlocks.getDragonBallBlocks(setDefinition.getId()).entrySet()) {
            int star = entry.getKey();
            DeferredHolder<Block, ? extends Block> block = entry.getValue();
            if (assets != null && assets.getFlatTexturePathForStar(star).isPresent()) {
               ResourceLocation texture = ResourceLocation.parse(assets.getFlatTexturePathForStar(star).get());
               this.simpleBlockWithItem((Block)block.get(), this.models().cubeAll(BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(), texture));
            } else {
               this.blockWithItem(block);
            }
         }
      }
   }

   private void blockWithItem(DeferredHolder<Block, ? extends Block> blockDeferredHolder) {
      this.simpleBlockWithItem((Block)blockDeferredHolder.get(), this.cubeAll((Block)blockDeferredHolder.get()));
   }

   private void grassBlock(DeferredHolder<Block, ? extends Block> blockDeferredHolder) {
      String path = BuiltInRegistries.BLOCK.getKey((Block)blockDeferredHolder.get()).getPath();
      ResourceLocation bottom = ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + path + "_down");
      ResourceLocation top = ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + path + "_top");
      ResourceLocation side = ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + path + "_side");
      this.simpleBlockWithItem((Block)blockDeferredHolder.get(), this.models().cubeBottomTop(path, side, bottom, top));
   }

   private void blockItem(DeferredHolder<Block, ? extends Block> blockDeferredHolder) {
      this.simpleBlockItem(
         (Block)blockDeferredHolder.get(),
         new UncheckedModelFile("dragonminez:block/" + BuiltInRegistries.BLOCK.getKey((Block)blockDeferredHolder.get()).getPath())
      );
   }

   private void leavesBlock(DeferredHolder<Block, ? extends Block> blockDeferredHolder) {
      this.simpleBlockWithItem(
         (Block)blockDeferredHolder.get(),
         ((BlockModelBuilder)this.models()
               .singleTexture(
                  BuiltInRegistries.BLOCK.getKey((Block)blockDeferredHolder.get()).getPath(),
                  ResourceLocation.parse("minecraft:block/leaves"),
                  "all",
                  this.blockTexture((Block)blockDeferredHolder.get())
               ))
            .renderType("cutout")
      );
   }

   private void saplingBlock(DeferredHolder<Block, ? extends Block> blockDeferredHolder) {
      this.simpleBlock(
         (Block)blockDeferredHolder.get(),
         ((BlockModelBuilder)this.models()
               .cross(BuiltInRegistries.BLOCK.getKey((Block)blockDeferredHolder.get()).getPath(), this.blockTexture((Block)blockDeferredHolder.get())))
            .renderType("cutout")
      );
   }
}
