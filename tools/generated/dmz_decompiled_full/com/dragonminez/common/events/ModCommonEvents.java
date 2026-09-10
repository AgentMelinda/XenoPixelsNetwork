package com.dragonminez.common.events;

import com.dragonminez.common.dragonball.DragonBallDataPackResources;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.entities.BlackNimbusEntity;
import com.dragonminez.common.init.entities.FlyingNimbusEntity;
import com.dragonminez.common.init.entities.MajinSkillEntity;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import com.dragonminez.common.init.entities.SpacePodEntity;
import com.dragonminez.common.init.entities.animal.Dino1Entity;
import com.dragonminez.common.init.entities.animal.Dino2Entity;
import com.dragonminez.common.init.entities.animal.DinoFlyEntity;
import com.dragonminez.common.init.entities.animal.DinoKidEntity;
import com.dragonminez.common.init.entities.animal.NamekFrogEntity;
import com.dragonminez.common.init.entities.animal.NamekFrogGinyuEntity;
import com.dragonminez.common.init.entities.animal.SabertoothEntity;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.init.entities.namek.NamekTraderEntity;
import com.dragonminez.common.init.entities.namek.NamekWarriorEntity;
import com.dragonminez.common.init.entities.redribbon.BanditEntity;
import com.dragonminez.common.init.entities.redribbon.RedRibbonSoldierEntity;
import com.dragonminez.common.init.entities.redribbon.RobotEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaSoldier01Entity;
import com.dragonminez.common.init.entities.sagas.SagaOzaruEntity;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import com.dragonminez.common.stats.techniques.PredefinedTechniques;
import com.dragonminez.server.world.gen.OverworldSurfaceRules;
import com.dragonminez.server.world.region.OverworldRegion;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack.Metadata;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.Pack.ResourcesSupplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage;
import net.neoforged.neoforge.capabilities.Capabilities.ItemHandler;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import terrablender.api.Regions;
import terrablender.api.SurfaceRuleManager;
import terrablender.api.SurfaceRuleManager.RuleCategory;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD
)
public class ModCommonEvents {
   @SubscribeEvent
   public static void onAddPackFinders(AddPackFindersEvent event) {
      if (event.getPackType() == PackType.SERVER_DATA) {
         event.addRepositorySource(
            packConsumer -> {
               Pack dragonballPack = Pack.readMetaAndCreate(
                  new PackLocationInfo("dmz_dragonballs_runtime_data", Component.literal("DMZ Dragonballs Runtime Data"), PackSource.BUILT_IN, Optional.empty()),
                  new ResourcesSupplier() {
                     public PackResources openPrimary(PackLocationInfo location) {
                        return new DragonBallDataPackResources(location);
                     }

                     public PackResources openFull(PackLocationInfo location, Metadata metadata) {
                        return this.openPrimary(location);
                     }
                  },
                  PackType.SERVER_DATA,
                  new PackSelectionConfig(true, Position.TOP, false)
               );
               if (dragonballPack != null) {
                  packConsumer.accept(dragonballPack);
               }
            }
         );
      }
   }

   @SubscribeEvent
   public static void registerAttributes(EntityAttributeCreationEvent event) {
      for (DeferredHolder<EntityType<?>, ? extends EntityType<?>> masterEntity : MainEntities.getMasterEntities()) {
         event.put((EntityType)masterEntity.get(), MastersEntity.createAttributes().build());
      }

      event.put((EntityType)MainEntities.QUEST_NPC.get(), MastersEntity.createAttributes().build());

      for (DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> entity : MainEntities.getDragonWishEntities().values()) {
         event.put((EntityType)entity.get(), DragonWishEntity.createAttributes().build());
      }

      List<DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>>> saibamans = List.of(
         MainEntities.SAGA_SAIBAMAN,
         MainEntities.SAGA_SAIBAMAN2,
         MainEntities.SAGA_SAIBAMAN3,
         MainEntities.SAGA_SAIBAMAN4,
         MainEntities.SAGA_SAIBAMAN5,
         MainEntities.SAGA_SAIBAMAN6
      );
      List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends DBSagasEntity>>> soldiers = List.of(
         MainEntities.SAGA_FRIEZA_SOLDIER, MainEntities.SAGA_FRIEZA_SOLDIER2, MainEntities.SAGA_FRIEZA_SOLDIER3, MainEntities.SAGA_MORO_SOLDIER
      );
      List<DeferredHolder<EntityType<?>, ? extends EntityType<SagaOzaruEntity>>> ozarus = List.of(MainEntities.SAGA_OZARU_VEGETA, MainEntities.SAGA_OZARU);

      for (DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> saibaman : saibamans) {
         event.put((EntityType)saibaman.get(), SagaSaibamanEntity.createAttributes().build());
      }

      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends DBSagasEntity>> soldier : soldiers) {
         event.put((EntityType)soldier.get(), SagaFriezaSoldier01Entity.createAttributes().build());
      }

      for (DeferredHolder<EntityType<?>, ? extends EntityType<SagaOzaruEntity>> ozaru : ozarus) {
         event.put((EntityType)ozaru.get(), SagaOzaruEntity.createAttributes().build());
      }

      AttributeSupplier defaultSagaAttributes = DBSagasEntity.createAttributes().build();

      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> sagaEntity : MainEntities.getSagaEntities()) {
         if (!saibamans.contains(sagaEntity) && !soldiers.contains(sagaEntity) && !ozarus.contains(sagaEntity)) {
            event.put((EntityType)sagaEntity.get(), defaultSagaAttributes);
         }
      }

      event.put((EntityType)MainEntities.DINOSAUR1.get(), Dino1Entity.createAttributes().build());
      event.put((EntityType)MainEntities.DINOSAUR2.get(), Dino2Entity.createAttributes().build());
      event.put((EntityType)MainEntities.DINOSAUR3.get(), DinoFlyEntity.createAttributes().build());
      event.put((EntityType)MainEntities.DINO_KID.get(), DinoKidEntity.createAttributes().build());
      event.put((EntityType)MainEntities.NAMEK_FROG.get(), NamekFrogEntity.createAttributes());
      event.put((EntityType)MainEntities.NAMEK_FROG_GINYU.get(), NamekFrogGinyuEntity.createAttributes());
      event.put((EntityType)MainEntities.NAMEK_TRADER.get(), NamekTraderEntity.createAttributes().build());
      event.put((EntityType)MainEntities.CC_NAMEKIAN.get(), NamekTraderEntity.createAttributes().build());
      event.put((EntityType)MainEntities.NAMEK_WARRIOR.get(), NamekWarriorEntity.createAttributes().build());
      event.put((EntityType)MainEntities.SABERTOOTH.get(), SabertoothEntity.createAttributes().build());
      event.put((EntityType)MainEntities.BANDIT.get(), BanditEntity.createAttributes().build());
      event.put((EntityType)MainEntities.RED_RIBBON_ROBOT1.get(), RobotEntity.createAttributes().build());
      event.put((EntityType)MainEntities.RED_RIBBON_ROBOT2.get(), RobotEntity.createAttributes().build());
      event.put((EntityType)MainEntities.RED_RIBBON_ROBOT3.get(), RobotEntity.createAttributes().build());
      event.put((EntityType)MainEntities.RED_RIBBON_SOLDIER.get(), RedRibbonSoldierEntity.createAttributes().build());
      event.put((EntityType)MainEntities.SPACE_POD.get(), SpacePodEntity.createAttributes());
      event.put((EntityType)MainEntities.FLYING_NIMBUS.get(), FlyingNimbusEntity.createAttributes());
      event.put((EntityType)MainEntities.BLACK_NIMBUS.get(), BlackNimbusEntity.createAttributes());
      event.put((EntityType)MainEntities.ROBOT_XENOVERSE.get(), RobotEntity.createAttributes().build());
      event.put((EntityType)MainEntities.PUNCH_MACHINE.get(), PunchMachineEntity.createAttributes().build());
      event.put((EntityType)MainEntities.MAJIN_SKILL.get(), MajinSkillEntity.createAttributes().build());
   }

   public static void commonSetup(FMLCommonSetupEvent event) {
      new PredefinedTechniques().init();
      event.enqueueWork(
         () -> {
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.CHRYSANTHEMUM_FLOWER.getId(), MainBlocks.POTTED_CHRYSANTHEMUM_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.AMARYLLIS_FLOWER.getId(), MainBlocks.POTTED_AMARYLLIS_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.MARIGOLD_FLOWER.getId(), MainBlocks.POTTED_MARIGOLD_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.getId(), MainBlocks.POTTED_CATHARANTHUS_ROSEUS_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.TRILLIUM_FLOWER.getId(), MainBlocks.POTTED_TRILLIUM_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.NAMEK_FERN.getId(), MainBlocks.POTTED_NAMEK_FERN);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.getId(), MainBlocks.POTTED_SACRED_CHRYSANTHEMUM_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.SACRED_AMARYLLIS_FLOWER.getId(), MainBlocks.POTTED_SACRED_AMARYLLIS_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.SACRED_MARIGOLD_FLOWER.getId(), MainBlocks.POTTED_SACRED_MARIGOLD_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT)
               .addPlant(MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.getId(), MainBlocks.POTTED_SACRED_CATHARANTHUS_ROSEUS_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.SACRED_TRILLIUM_FLOWER.getId(), MainBlocks.POTTED_SACRED_TRILLIUM_FLOWER);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.SACRED_FERN.getId(), MainBlocks.POTTED_SACRED_FERN);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.NAMEK_AJISSA_SAPLING.getId(), MainBlocks.POTTED_AJISSA_SAPLING);
            ((FlowerPotBlock)Blocks.FLOWER_POT).addPlant(MainBlocks.NAMEK_SACRED_SAPLING.getId(), MainBlocks.POTTED_SACRED_SAPLING);
            Regions.register(new OverworldRegion(40));
            SurfaceRuleManager.addSurfaceRules(RuleCategory.OVERWORLD, "dragonminez", OverworldSurfaceRules.makeRules());
         }
      );
   }

   @SubscribeEvent
   public static void onEntityAttributeModification(EntityAttributeModificationEvent event) {
      event.add(EntityType.PLAYER, MainAttributes.STRENGTH);
      event.add(EntityType.PLAYER, MainAttributes.STRIKE_POWER);
      event.add(EntityType.PLAYER, MainAttributes.RESISTANCE);
      event.add(EntityType.PLAYER, MainAttributes.VITALITY);
      event.add(EntityType.PLAYER, MainAttributes.KI_POWER);
      event.add(EntityType.PLAYER, MainAttributes.ENERGY);
      event.add(EntityType.PLAYER, MainAttributes.MAX_ENERGY);
      event.add(EntityType.PLAYER, MainAttributes.MAX_STAMINA);
      event.add(EntityType.PLAYER, MainAttributes.MAX_POISE);
      event.add(EntityType.PLAYER, MainAttributes.MELEE_DAMAGE);
      event.add(EntityType.PLAYER, MainAttributes.STRIKE_DAMAGE);
      event.add(EntityType.PLAYER, MainAttributes.DEFENSE);
      event.add(EntityType.PLAYER, MainAttributes.CRIT_CHANCE);
      event.add(EntityType.PLAYER, MainAttributes.CRIT_DAMAGE);
   }

   @SubscribeEvent
   public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
      event.registerBlockEntity(ItemHandler.BLOCK, (BlockEntityType)MainBlockEntities.KIKONO_STATION_BE.get(), (be, side) -> be.getItemHandler());
      event.registerBlockEntity(EnergyStorage.BLOCK, (BlockEntityType)MainBlockEntities.KIKONO_STATION_BE.get(), (be, side) -> be.getEnergyStorage());
      event.registerBlockEntity(ItemHandler.BLOCK, (BlockEntityType)MainBlockEntities.FUEL_GENERATOR_BE.get(), (be, side) -> be.getItemHandler());
      event.registerBlockEntity(EnergyStorage.BLOCK, (BlockEntityType)MainBlockEntities.FUEL_GENERATOR_BE.get(), (be, side) -> be.getEnergyStorage());
      event.registerBlockEntity(EnergyStorage.BLOCK, (BlockEntityType)MainBlockEntities.ENERGY_CABLE_BE.get(), (be, side) -> be.getEnergyStorage());
      event.registerBlockEntity(EnergyStorage.BLOCK, (BlockEntityType)MainBlockEntities.GRAVITY_DEVICE_BE.get(), (be, side) -> be.getEnergyStorage());
   }

   @SafeVarargs
   private static void regAttr(EntityAttributeCreationEvent event, AttributeSupplier attributes, DeferredHolder... entities) {
      for (DeferredHolder reg : entities) {
         event.put((EntityType)reg.get(), attributes);
      }
   }
}
