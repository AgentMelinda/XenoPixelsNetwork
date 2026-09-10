package com.dragonminez.common.datagen;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetAssetDefinition;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonRadarAssetDefinition;
import com.dragonminez.common.dragonball.DragonRadarDefinition;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainItems;
import java.util.Map;
import java.util.Map.Entry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ArmorItem.Type;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

public class DMZItemModelProvider extends ItemModelProvider {
   public DMZItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
      super(output, "dragonminez", existingFileHelper);
   }

   protected void registerModels() {
      for (DragonRadarDefinition radarDefinition : DragonBallDefinitions.getRadars()) {
         DragonRadarAssetDefinition assets = radarDefinition.resolveAssetDefinition();
         DeferredHolder<Item, ? extends Item> item = MainItems.getDragonRadarItemOrThrow(radarDefinition.getId());
         if (assets != null && assets.getItemTexturePath().isPresent()) {
            ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), this.mcLoc("item/generated")))
               .texture("layer0", ResourceLocation.parse(assets.getItemTexturePath().get()));
         } else {
            this.simpleItem(item);
         }
      }

      this.simpleItem(MainItems.MIGHT_TREE_FRUIT);
      this.simpleItem(MainItems.NUBE_ITEM);
      this.simpleItem(MainItems.NUBE_NEGRA_ITEM);
      this.simpleItem(MainItems.NAVE_SAIYAN_ITEM);
      this.simpleItem(MainItems.SENZU_BEAN);
      this.simpleItem(MainItems.RED_CAPSULE);
      this.simpleItem(MainItems.YELLOW_CAPSULE);
      this.simpleItem(MainItems.PURPLE_CAPSULE);
      this.simpleItem(MainItems.GREEN_CAPSULE);
      this.simpleItem(MainItems.BLUE_CAPSULE);
      this.simpleItem(MainItems.ORANGE_CAPSULE);
      this.simpleItem(MainItems.POTHALA_LEFT);
      this.simpleItem(MainItems.POTHALA_RIGHT);
      this.simpleItem(MainItems.GREEN_POTHALA_LEFT);
      this.simpleItem(MainItems.GREEN_POTHALA_RIGHT);
      this.simpleItem(MainItems.POTHALA_PAIR);
      this.simpleItem(MainItems.GREEN_POTHALA_PAIR);
      this.simpleItem(MainItems.HEART_MEDICINE);
      this.simpleItem(MainItems.NAMEK_WATER_BUCKET);
      this.simpleItem(MainItems.HEALING_BUCKET);

      for (DragonBallSetDefinition setDefinition : DragonBallDefinitions.getBallSets()) {
         DragonBallSetAssetDefinition assets = setDefinition.resolveAssetDefinition();

         for (Entry<Integer, DeferredHolder<Item, ? extends Item>> entry : MainItems.getDragonBallBlockItems(setDefinition.getId()).entrySet()) {
            int star = entry.getKey();
            DeferredHolder<Item, ? extends Item> item = entry.getValue();
            if (assets != null && assets.getInventoryTexturePathForStar(star).isPresent()) {
               ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), this.mcLoc("item/generated")))
                  .texture("layer0", ResourceLocation.parse(assets.getInventoryTexturePathForStar(star).get()));
            } else {
               this.simpleItem(item);
            }
         }
      }

      this.simpleItem(MainItems.RADAR_PIECE);
      this.simpleItem(MainItems.T1_RADAR_CHIP);
      this.simpleItem(MainItems.T2_RADAR_CHIP);
      this.simpleItem(MainItems.T1_RADAR_CPU);
      this.simpleItem(MainItems.T2_RADAR_CPU);
      this.simpleItem(MainItems.GREEN_SCOUTER);
      this.simpleItem(MainItems.RED_SCOUTER);
      this.simpleItem(MainItems.BLUE_SCOUTER);
      this.simpleItem(MainItems.PURPLE_SCOUTER);
      this.withExistingParent(MainItems.DINO_1.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.DINO_2.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.DINO_3.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.DINO_KID.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.NAMEK_FROG_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.GINYU_FROG_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.SOLDIER01_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.SOLDIER02_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.SOLDIER03_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.NWARRIOR_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.SAIBAMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.KAIWAREMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.KYUKONMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.COPYMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.TENNENMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.JINKOUMAN_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.REDRIBBONSOLDIER_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.REDRIBBONROBOT1_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.REDRIBBONROBOT2_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.REDRIBBONROBOT3_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.withExistingParent(MainItems.BANDIT_SE.getId().getPath(), this.mcLoc("item/template_spawn_egg"));
      this.simpleItem(MainItems.DINO_MEAT_RAW);
      this.simpleItem(MainItems.DINO_MEAT_COOKED);
      this.simpleItem(MainItems.BABY_DINO_MEAT_RAW);
      this.simpleItem(MainItems.BABY_DINO_MEAT_COOKED);
      this.simpleItem(MainItems.DINO_TAIL_RAW);
      this.simpleItem(MainItems.DINO_TAIL_COOKED);
      this.simpleItem(MainItems.FROG_LEGS_RAW);
      this.simpleItem(MainItems.FROG_LEGS_COOKED);
      this.generateArmorSetModels(MainItems.A13_ARMOR);
      this.generateArmorSetModels(MainItems.A14_ARMOR);
      this.generateArmorSetModels(MainItems.A16_ARMOR);
      this.generateArmorSetModels(MainItems.A17_ARMOR);
      this.generateArmorSetModels(MainItems.A17_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.A18_ARMOR);
      this.generateArmorSetModels(MainItems.A18_CELL_ARMOR);
      this.generateArmorSetModels(MainItems.A18_KAME_ARMOR);
      this.generateArmorSetModels(MainItems.A18_TOURNAMENT_ARMOR);
      this.generateArmorSetModels(MainItems.A20_ARMOR);
      this.generateArmorSetModels(MainItems.AGE1000_ARMOR);
      this.generateArmorSetModels(MainItems.BARDOCK_DBZ_ARMOR);
      this.generateArmorSetModels(MainItems.BARDOCK_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.BEERUS_ARMOR);
      this.generateArmorSetModels(MainItems.BLACKGOKU_ARMOR);
      this.generateArmorSetModels(MainItems.BROLY_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.BROLY_Z_ARMOR);
      this.generateArmorSetModels(MainItems.CAULIFLA_ARMOR);
      this.generateArmorSetModels(MainItems.CHAOZ_ARMOR);
      this.generateArmorSetModels(MainItems.DEMON_GI_BLUE_ARMOR);
      this.generateArmorSetModels(MainItems.DRAGON_CLAN_ARMOR);
      this.generateArmorSetModels(MainItems.EVIL_BUU_ARMOR);
      this.generateArmorSetModels(MainItems.FIGHTER_ARMOR);
      this.generateArmorSetModels(MainItems.FUSION_ZAMASU_ARMOR);
      this.generateArmorSetModels(MainItems.FUTURE_GOHAN_ARMOR);
      this.generateArmorSetModels(MainItems.GAMMA1_ARMOR);
      this.generateArmorSetModels(MainItems.GAMMA2_ARMOR);
      this.generateArmorSetModels(MainItems.GAS_ARMOR);
      this.generateArmorSetModels(MainItems.GINE_ARMOR);
      this.generateArmorSetModels(MainItems.GOGETA_ARMOR);
      this.generateArmorSetModels(MainItems.GOHAN_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.GOKU_ARMOR);
      this.generateArmorSetModels(MainItems.GOKU_GT_ARMOR);
      this.generateArmorSetModels(MainItems.GOKU_KID_ARMOR);
      this.generateArmorSetModels(MainItems.GOKU_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.GOKU_WHIS_ARMOR);
      this.generateArmorSetModels(MainItems.GOTEN_ARMOR);
      this.generateArmorSetModels(MainItems.GOTEN_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.GRANOLA_ARMOR);
      this.generateArmorSetModels(MainItems.GREAT_SAIYAMAN_ARMOR);
      this.generateArmorSetModels(MainItems.HIT_ARMOR);
      this.generateArmorSetModels(MainItems.INVENCIBLE_ARMOR);
      this.generateArmorSetModels(MainItems.INVENCIBLE_BLUE_ARMOR);
      this.generateArmorSetModels(MainItems.KALE_ARMOR);
      this.generateArmorSetModels(MainItems.KEFLA_ARMOR);
      this.generateArmorSetModels(MainItems.KIBITO_ARMOR);
      this.generateArmorSetModels(MainItems.KING_VEGETA_ARMOR);
      this.generateArmorSetModels(MainItems.MAJIN21_ARMOR);
      this.generateArmorSetModels(MainItems.MAJIN_BUU_ARMOR);
      this.generateArmorSetModels(MainItems.MIGHTY_MAJIN_ARMOR);
      this.generateArmorSetModels(MainItems.MYSTIC_ARMOR);
      this.generateArmorSetModels(MainItems.NARUKE_ARMOR);
      this.generateArmorSetModels(MainItems.ORANGE_HIGH_ARMOR);
      this.generateArmorSetModels(MainItems.PICCOLO_ARMOR);
      this.generateArmorSetModels(MainItems.PRIDE_TROOPS_ARMOR);
      this.generateArmorSetModels(MainItems.SHIN_ARMOR);
      this.generateArmorSetModels(MainItems.SLUG_ARMOR);
      this.generateArmorSetModels(MainItems.STRONGEST_ARMOR);
      this.generateArmorSetModels(MainItems.SUPER_BUU_ARMOR);
      this.generateArmorSetModels(MainItems.TIEN_ARMOR);
      this.generateArmorSetModels(MainItems.TRUNKS_KID_ARMOR);
      this.generateArmorSetModels(MainItems.TRUNKS_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.TRUNKS_Z_ARMOR);
      this.generateArmorSetModels(MainItems.TURLES_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_BUU_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_GT_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_NAMEK_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_SAIYAN_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_SUPER_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_WHIS_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETA_Z_ARMOR);
      this.generateArmorSetModels(MainItems.VEGETTO_ARMOR);
      this.generateArmorSetModels(MainItems.VIDEL_ARMOR);
      this.generateArmorSetModels(MainItems.WARRIOR_CLAN_ARMOR);
      this.generateArmorSetModels(MainItems.WHIS_ARMOR);
      this.generateArmorSetModels(MainItems.WONDER_MAJIN_ARMOR);
      this.generateArmorSetModels(MainItems.XENO_GOKU_ARMOR);
      this.generateArmorSetModels(MainItems.YARDRAT_ARMOR);
      this.generateArmorSetModels(MainItems.ZAMASU_ARMOR);
      this.generateArmorSetModels(MainItems.THRAGG_ARMOR);
      this.generateArmorSetModels(MainItems.GILGAMESH_ARMOR);
      this.generateArmorSetModels(MainItems.SUBARU_NATSUKI_ARMOR);
      this.generateArmorSetModels(MainItems.SUBARU_NATSUKI_ARC6_ARMOR);
      this.generateArmorSetModels(MainItems.RADITZ_ARMOR);
      this.generateArmorSetModels(MainItems.GERO_ARMOR);
      this.generateArmorSetModels(MainItems.COOLER_SOLDIER_ARMOR);
      this.generateArmorSetModels(MainItems.CAPSULE_CORP_ARMOR);
      this.generateArmorSetModels(MainItems.XENO_GOKU_PATREON_ARMOR);
      this.generateArmorSetModels(MainItems.VERGIL_ARMOR);
      this.generateArmorSetModels(MainItems.GREAT_SAIYAMAN_2_ARMOR);
      this.simpleItem(MainItems.KIKONO_STRING);
      this.simpleItem(MainItems.KIKONO_CLOTH);
      this.simpleItem(MainItems.KIKONO_STICK);
      this.simpleItem(MainItems.ARMOR_CRAFTING_KIT);
      this.simpleItem(MainItems.BLANK_PATTERN_Z);
      this.simpleItem(MainItems.BLANK_PATTERN_SUPER);
      this.patternItem(MainItems.PATTERN_A13);
      this.patternItem(MainItems.PATTERN_A16);
      this.patternItem(MainItems.PATTERN_A17);
      this.patternItem(MainItems.PATTERN_A17_SUPER);
      this.patternItem(MainItems.PATTERN_A18);
      this.patternItem(MainItems.PATTERN_A18_CELL);
      this.patternItem(MainItems.PATTERN_A18_KAME);
      this.patternItem(MainItems.PATTERN_A18_TOURNAMENT);
      this.patternItem(MainItems.PATTERN_AGE1000);
      this.patternItem(MainItems.PATTERN_BARDOCK1);
      this.patternItem(MainItems.PATTERN_BARDOCK2);
      this.patternItem(MainItems.PATTERN_BEERUS);
      this.patternItem(MainItems.PATTERN_BLACK);
      this.patternItem(MainItems.PATTERN_BROLY_SUPER);
      this.patternItem(MainItems.PATTERN_BROLY_Z);
      this.patternItem(MainItems.PATTERN_CAULIFLA);
      this.patternItem(MainItems.PATTERN_CHAOZ);
      this.patternItem(MainItems.PATTERN_DRAGON_CLAN);
      this.patternItem(MainItems.PATTERN_EVIL_BUU);
      this.patternItem(MainItems.PATTERN_FIGHTER);
      this.patternItem(MainItems.PATTERN_FUSION_ZAMASU);
      this.patternItem(MainItems.PATTERN_FUTURE_GOHAN);
      this.patternItem(MainItems.PATTERN_GAMMA1);
      this.patternItem(MainItems.PATTERN_GAMMA2);
      this.patternItem(MainItems.PATTERN_GAS);
      this.patternItem(MainItems.PATTERN_GETE);
      this.patternItem(MainItems.PATTERN_GINE);
      this.patternItem(MainItems.PATTERN_GOGETA);
      this.patternItem(MainItems.PATTERN_GOHAN1);
      this.patternItem(MainItems.PATTERN_GOHAN_SUPER);
      this.patternItem(MainItems.PATTERN_GOKU1);
      this.patternItem(MainItems.PATTERN_GOKU_GT);
      this.patternItem(MainItems.PATTERN_GOKU_KID);
      this.patternItem(MainItems.PATTERN_GOKU_SUPER);
      this.patternItem(MainItems.PATTERN_GOKU_WHIS);
      this.patternItem(MainItems.PATTERN_GOTEN);
      this.patternItem(MainItems.PATTERN_GOTEN_SUPER);
      this.patternItem(MainItems.PATTERN_GRANOLA);
      this.patternItem(MainItems.PATTERN_GREAT_SAIYAMAN);
      this.patternItem(MainItems.PATTERN_HIT);
      this.patternItem(MainItems.PATTERN_KALE);
      this.patternItem(MainItems.PATTERN_KEFLA);
      this.patternItem(MainItems.PATTERN_KIBITO);
      this.patternItem(MainItems.PATTERN_MAJIN21);
      this.patternItem(MainItems.PATTERN_MAJIN_BUU);
      this.patternItem(MainItems.PATTERN_MIGHTY_MAJIN);
      this.patternItem(MainItems.PATTERN_MYSTIC);
      this.patternItem(MainItems.PATTERN_ORANGE_HIGH);
      this.patternItem(MainItems.PATTERN_PICCOLO);
      this.patternItem(MainItems.PATTERN_PRIDE_TROOPS);
      this.patternItem(MainItems.PATTERN_SHIN);
      this.patternItem(MainItems.PATTERN_SLUG);
      this.patternItem(MainItems.PATTERN_SUPER_BUU);
      this.patternItem(MainItems.PATTERN_TIEN);
      this.patternItem(MainItems.PATTERN_TRUNKS_KID);
      this.patternItem(MainItems.PATTERN_TRUNKS_SUPER);
      this.patternItem(MainItems.PATTERN_TRUNKS_Z);
      this.patternItem(MainItems.PATTERN_TURLES);
      this.patternItem(MainItems.PATTERN_VEGETA1);
      this.patternItem(MainItems.PATTERN_VEGETA2);
      this.patternItem(MainItems.PATTERN_VEGETA_BUU);
      this.patternItem(MainItems.PATTERN_VEGETA_GT);
      this.patternItem(MainItems.PATTERN_VEGETA_SUPER);
      this.patternItem(MainItems.PATTERN_VEGETA_WHIS);
      this.patternItem(MainItems.PATTERN_VEGETA_Z);
      this.patternItem(MainItems.PATTERN_VEGETTO);
      this.patternItem(MainItems.PATTERN_VIDEL);
      this.patternItem(MainItems.PATTERN_WARRIOR_CLAN);
      this.patternItem(MainItems.PATTERN_WHIS);
      this.patternItem(MainItems.PATTERN_WONDER_MAJIN);
      this.patternItem(MainItems.PATTERN_XENO_GOKU);
      this.patternItem(MainItems.PATTERN_YARDRAT);
      this.patternItem(MainItems.PATTERN_ZAMASU);
      this.patternItem(MainItems.PATTERN_VERGIL);
      this.patternItem(MainItems.PATTERN_XENO_GOKU_PATREON);
      this.patternItem(MainItems.PATTERN_COOLER_SOLDIER);
      this.patternItem(MainItems.PATTERN_CAPSULE_CORP);
      this.patternItem(MainItems.PATTERN_A20);
      this.patternItem(MainItems.PATTERN_GERO);
      this.patternItem(MainItems.PATTERN_KING_VEGETA);
      this.patternItem(MainItems.PATTERN_GREAT_SAIYAMAN_2);
      this.patternItem(MainItems.PATTERN_RADITZ);
      this.patternItem(MainItems.PATTERN_A14);
      this.simpleItem(MainItems.GETE_SCRAP);
      this.simpleItem(MainItems.GETE_INGOT);
      this.simpleItem(MainItems.KIKONO_SHARD);
      this.simpleItem(MainItems.GETE_RED_CAPSULE);
      this.simpleItem(MainItems.GETE_PURPLE_CAPSULE);
      this.simpleItem(MainItems.GETE_YELLOW_CAPSULE);
      this.simpleItem(MainItems.GETE_GREEN_CAPSULE);
      this.simpleItem(MainItems.GETE_ORANGE_CAPSULE);
      this.simpleItem(MainItems.GETE_BLUE_CAPSULE);
      this.simpleItem(MainItems.KI_BATTERY);
      this.simpleItem(MainItems.ANTI_KI_CLOAK);
      this.simpleItem(MainItems.WEIGHT_TURTLE_SHELL);
      this.simpleItem(MainItems.WORKOUT_WEIGHTS);
      this.simpleItem(MainItems.WEIGHT_PICCOLO_CAPE);
      this.simpleBlockItem(MainBlocks.NAMEK_BLOCK);
      this.simpleBlockItem(MainBlocks.NAMEK_DIRT);
      this.simpleBlockItem(MainBlocks.NAMEK_STONE);
      this.simpleBlockItem(MainBlocks.NAMEK_COBBLESTONE);
      this.simpleBlockItem(MainBlocks.ROCKY_STONE);
      this.simpleBlockItem(MainBlocks.ROCKY_COBBLESTONE);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_PLANKS);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_LEAVES);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_PLANKS);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_LEAVES);
      this.simpleBlockItem(MainBlocks.GETE_BLOCK);
      this.simpleBlockItem(MainBlocks.NAMEK_KIKONO_ORE);
      this.simpleBlockItem(MainBlocks.KIKONO_BLOCK);
      this.simpleBlockItem(MainBlocks.NAMEK_DIAMOND_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_GOLD_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_IRON_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_LAPIS_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_REDSTONE_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_COAL_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_EMERALD_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_COPPER_ORE);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_DIAMOND);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_GOLD);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_IRON);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_LAPIS);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_REDSTONE);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_COAL);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_EMERALD);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_COPPER);
      this.simpleBlockItem(MainBlocks.TIME_CHAMBER_PORTAL);
      this.simpleBlockItem(MainBlocks.OTHERWORLD_CLOUD);
      this.simpleBlockItem(MainBlocks.GETE_ORE);
      this.blockAsItem(MainBlocks.NAMEK_AJISSA_DOOR);
      this.blockAsItem(MainBlocks.NAMEK_SACRED_DOOR);
      this.fenceItem(MainBlocks.NAMEK_AJISSA_FENCE, MainBlocks.NAMEK_AJISSA_PLANKS);
      this.fenceItem(MainBlocks.NAMEK_SACRED_FENCE, MainBlocks.NAMEK_SACRED_PLANKS);
      this.buttonItem(MainBlocks.NAMEK_AJISSA_BUTTON, MainBlocks.NAMEK_AJISSA_PLANKS);
      this.buttonItem(MainBlocks.NAMEK_SACRED_BUTTON, MainBlocks.NAMEK_SACRED_PLANKS);
      this.simpleBlockItem(MainBlocks.NAMEK_STONE_STAIRS);
      this.simpleBlockItem(MainBlocks.NAMEK_COBBLESTONE_STAIRS);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_STAIRS);
      this.simpleBlockItem(MainBlocks.ROCKY_STONE_STAIRS);
      this.simpleBlockItem(MainBlocks.ROCKY_COBBLESTONE_STAIRS);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_STAIRS);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_STAIRS);
      this.simpleBlockItem(MainBlocks.NAMEK_STONE_SLAB);
      this.simpleBlockItem(MainBlocks.NAMEK_COBBLESTONE_SLAB);
      this.simpleBlockItem(MainBlocks.NAMEK_DEEPSLATE_SLAB);
      this.simpleBlockItem(MainBlocks.ROCKY_STONE_SLAB);
      this.simpleBlockItem(MainBlocks.ROCKY_COBBLESTONE_SLAB);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_SLAB);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_SLAB);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_PRESSURE_PLATE);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_PRESSURE_PLATE);
      this.simpleBlockItem(MainBlocks.NAMEK_AJISSA_FENCE_GATE);
      this.simpleBlockItem(MainBlocks.NAMEK_SACRED_FENCE_GATE);
      this.trapdoorItem(MainBlocks.NAMEK_AJISSA_TRAPDOOR);
      this.trapdoorItem(MainBlocks.NAMEK_SACRED_TRAPDOOR);
      this.wallItem(MainBlocks.NAMEK_STONE_WALL, MainBlocks.NAMEK_STONE);
      this.wallItem(MainBlocks.NAMEK_COBBLESTONE_WALL, MainBlocks.NAMEK_COBBLESTONE);
      this.wallItem(MainBlocks.NAMEK_DEEPSLATE_WALL, MainBlocks.NAMEK_DEEPSLATE);
      this.wallItem(MainBlocks.ROCKY_STONE_WALL, MainBlocks.ROCKY_STONE);
      this.wallItem(MainBlocks.ROCKY_COBBLESTONE_WALL, MainBlocks.ROCKY_COBBLESTONE);
      this.blockAsItem(MainBlocks.CHRYSANTHEMUM_FLOWER);
      this.blockAsItem(MainBlocks.AMARYLLIS_FLOWER);
      this.blockAsItem(MainBlocks.MARIGOLD_FLOWER);
      this.blockAsItem(MainBlocks.CATHARANTHUS_ROSEUS_FLOWER);
      this.blockAsItem(MainBlocks.TRILLIUM_FLOWER);
      this.blockItem(MainBlocks.NAMEK_FERN);
      this.saplingItem(MainBlocks.NAMEK_SACRED_SAPLING);
      this.blockAsItem(MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER);
      this.blockAsItem(MainBlocks.SACRED_AMARYLLIS_FLOWER);
      this.blockAsItem(MainBlocks.SACRED_MARIGOLD_FLOWER);
      this.blockAsItem(MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER);
      this.blockAsItem(MainBlocks.SACRED_TRILLIUM_FLOWER);
      this.blockItem(MainBlocks.SACRED_FERN);
      this.saplingItem(MainBlocks.NAMEK_AJISSA_SAPLING);
      this.handheldItem(MainItems.GETE_PICKAXE);
      this.handheldItem(MainItems.GETE_AXE);
      this.handheldItem(MainItems.GETE_SHOVEL);
      this.handheldItem(MainItems.GETE_HOE);
   }

   private void simpleItem(DeferredHolder<Item, ? extends Item> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "item/" + item.getId().getPath()));
   }

   private void armorItem(DeferredHolder<Item, ? extends Item> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "item/armors/" + item.getId().getPath()));
   }

   private void patternItem(DeferredHolder<Item, ? extends Item> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "item/patterns/" + item.getId().getPath()));
   }

   private void blockItem(DeferredHolder<Block, ? extends Block> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + item.getId().getPath()));
   }

   private void blockAsItem(DeferredHolder<Block, ? extends Block> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "item/" + item.getId().getPath()));
   }

   public void simpleBlockItem(DeferredHolder<Block, ? extends Block> block) {
      this.withExistingParent(
         "dragonminez:" + BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(),
         this.modLoc("block/" + BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath())
      );
   }

   public void trapdoorItem(DeferredHolder<Block, ? extends Block> block) {
      this.withExistingParent(
         BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(),
         this.modLoc("block/" + BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath() + "_bottom")
      );
   }

   public void fenceItem(DeferredHolder<Block, ? extends Block> block, DeferredHolder<Block, ? extends Block> baseBlock) {
      ((ItemModelBuilder)this.withExistingParent(BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(), this.mcLoc("block/fence_inventory")))
         .texture("texture", ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + BuiltInRegistries.BLOCK.getKey((Block)baseBlock.get()).getPath()));
   }

   public void buttonItem(DeferredHolder<Block, ? extends Block> block, DeferredHolder<Block, ? extends Block> baseBlock) {
      ((ItemModelBuilder)this.withExistingParent(BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(), this.mcLoc("block/button_inventory")))
         .texture("texture", ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + BuiltInRegistries.BLOCK.getKey((Block)baseBlock.get()).getPath()));
   }

   public void wallItem(DeferredHolder<Block, ? extends Block> block, DeferredHolder<Block, ? extends Block> baseBlock) {
      ((ItemModelBuilder)this.withExistingParent(BuiltInRegistries.BLOCK.getKey((Block)block.get()).getPath(), this.mcLoc("block/wall_inventory")))
         .texture("wall", ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + BuiltInRegistries.BLOCK.getKey((Block)baseBlock.get()).getPath()));
   }

   private void saplingItem(DeferredHolder<Block, ? extends Block> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/generated")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "block/" + item.getId().getPath()));
   }

   private void generateArmorSetModels(Map<Type, DeferredHolder<Item, ? extends Item>> armorSet) {
      for (DeferredHolder<Item, ? extends Item> piece : armorSet.values()) {
         this.armorItem(piece);
      }
   }

   private void handheldItem(DeferredHolder<Item, ? extends Item> item) {
      ((ItemModelBuilder)this.withExistingParent(item.getId().getPath(), ResourceLocation.parse("item/handheld")))
         .texture("layer0", ResourceLocation.fromNamespaceAndPath("dragonminez", "item/" + item.getId().getPath()));
   }
}
