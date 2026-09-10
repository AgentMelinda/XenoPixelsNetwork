package com.dragonminez.common.init;

import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonDefinition;
import com.dragonminez.common.init.entities.AllMastersEntity;
import com.dragonminez.common.init.entities.BlackNimbusEntity;
import com.dragonminez.common.init.entities.FlyingNimbusEntity;
import com.dragonminez.common.init.entities.MajinSkillEntity;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import com.dragonminez.common.init.entities.ShadowDummyEntity;
import com.dragonminez.common.init.entities.SpacePodEntity;
import com.dragonminez.common.init.entities.animal.Dino1Entity;
import com.dragonminez.common.init.entities.animal.Dino2Entity;
import com.dragonminez.common.init.entities.animal.DinoFlyEntity;
import com.dragonminez.common.init.entities.animal.DinoGlobalEntity;
import com.dragonminez.common.init.entities.animal.NamekFrogEntity;
import com.dragonminez.common.init.entities.animal.NamekFrogGinyuEntity;
import com.dragonminez.common.init.entities.animal.SabertoothEntity;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.init.entities.ki.KiAreaEntity;
import com.dragonminez.common.init.entities.ki.KiBarrierEntity;
import com.dragonminez.common.init.entities.ki.KiBlastEntity;
import com.dragonminez.common.init.entities.ki.KiDiskEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionEntity;
import com.dragonminez.common.init.entities.ki.KiExplosionVisualEntity;
import com.dragonminez.common.init.entities.ki.KiLaserEntity;
import com.dragonminez.common.init.entities.ki.KiWaveEntity;
import com.dragonminez.common.init.entities.ki.OzaruFistEntity;
import com.dragonminez.common.init.entities.ki.SPBlueHurricaneEntity;
import com.dragonminez.common.init.entities.ki.SPDragonFistEntity;
import com.dragonminez.common.init.entities.ki.SPMajinCandyEntity;
import com.dragonminez.common.init.entities.namek.CCNamekianEntity;
import com.dragonminez.common.init.entities.namek.NamekTraderEntity;
import com.dragonminez.common.init.entities.namek.NamekWarriorEntity;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.common.init.entities.redribbon.BanditEntity;
import com.dragonminez.common.init.entities.redribbon.RedRibbonEntity;
import com.dragonminez.common.init.entities.redribbon.RedRibbonSoldierEntity;
import com.dragonminez.common.init.entities.redribbon.RobotEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.init.entities.sagas.SagaAndroidsEntity;
import com.dragonminez.common.init.entities.sagas.SagaBabidiSoldiersEntity;
import com.dragonminez.common.init.entities.sagas.SagaBuuEntity;
import com.dragonminez.common.init.entities.sagas.SagaCellEntity;
import com.dragonminez.common.init.entities.sagas.SagaCuiEntity;
import com.dragonminez.common.init.entities.sagas.SagaDodoriaEntity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaEntity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaSoldier01Entity;
import com.dragonminez.common.init.entities.sagas.SagaFriezaSoldier02Entity;
import com.dragonminez.common.init.entities.sagas.SagaGinyuForcesEntity;
import com.dragonminez.common.init.entities.sagas.SagaGohanEntity;
import com.dragonminez.common.init.entities.sagas.SagaGokuEntity;
import com.dragonminez.common.init.entities.sagas.SagaGotenEntity;
import com.dragonminez.common.init.entities.sagas.SagaMoviesEntity;
import com.dragonminez.common.init.entities.sagas.SagaNappaEntity;
import com.dragonminez.common.init.entities.sagas.SagaOzaruEntity;
import com.dragonminez.common.init.entities.sagas.SagaPiccoloEntity;
import com.dragonminez.common.init.entities.sagas.SagaRaditzEntity;
import com.dragonminez.common.init.entities.sagas.SagaSaibamanEntity;
import com.dragonminez.common.init.entities.sagas.SagaTrunksEntity;
import com.dragonminez.common.init.entities.sagas.SagaVegetaEntity;
import com.dragonminez.common.init.entities.sagas.SagaZFightersEntity;
import com.dragonminez.common.init.entities.sagas.SagaZarbonEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent.Operation;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD
)
public class MainEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, "dragonminez");
   private static final Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>>> DRAGON_WISH_ENTITIES = registerDragonWishEntities();
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> SHENRON = getDragonWishEntityOrThrow("shenron");
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> PORUNGA = getDragonWishEntityOrThrow("porunga");
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterKarinEntity>> MASTER_KARIN = ENTITY_TYPES.register(
      "master_karin",
      () -> Builder.of(AllMastersEntity.MasterKarinEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 0.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_karin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterGokuEntity>> MASTER_GOKU = ENTITY_TYPES.register(
      "master_goku",
      () -> Builder.of(AllMastersEntity.MasterGokuEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_goku").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterKaiosamaEntity>> MASTER_KAIOSAMA = ENTITY_TYPES.register(
      "master_kaiosama",
      () -> Builder.of(AllMastersEntity.MasterKaiosamaEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_kaiosama").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterRoshiEntity>> MASTER_ROSHI = ENTITY_TYPES.register(
      "master_roshi",
      () -> Builder.of(AllMastersEntity.MasterRoshiEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_roshi").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterUranaiEntity>> MASTER_URANAI = ENTITY_TYPES.register(
      "master_uranai",
      () -> Builder.of(AllMastersEntity.MasterUranaiEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_uranai").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterEnmaEntity>> MASTER_ENMA = ENTITY_TYPES.register(
      "master_enma",
      () -> Builder.of(AllMastersEntity.MasterEnmaEntity::new, MobCategory.CREATURE)
            .sized(5.5F, 7.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_enma").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterDendeEntity>> MASTER_DENDE = ENTITY_TYPES.register(
      "master_dende",
      () -> Builder.of(AllMastersEntity.MasterDendeEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_dende").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterGeroEntity>> MASTER_GERO = ENTITY_TYPES.register(
      "master_gero",
      () -> Builder.of(AllMastersEntity.MasterGeroEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_gero").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterPopoEntity>> MASTER_POPO = ENTITY_TYPES.register(
      "master_popo",
      () -> Builder.of(AllMastersEntity.MasterPopoEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_popo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterGuruEntity>> MASTER_GURU = ENTITY_TYPES.register(
      "master_guru",
      () -> Builder.of(AllMastersEntity.MasterGuruEntity::new, MobCategory.CREATURE)
            .sized(1.3F, 3.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_guru").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterToribotEntity>> MASTER_TORIBOT = ENTITY_TYPES.register(
      "master_toribot",
      () -> Builder.of(AllMastersEntity.MasterToribotEntity::new, MobCategory.CREATURE)
            .sized(0.6F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_toribot").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterPiccolo>> MASTER_PICCOLO = ENTITY_TYPES.register(
      "master_piccolo",
      () -> Builder.of(AllMastersEntity.MasterPiccolo::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_piccolo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterGohan>> MASTER_GOHAN = ENTITY_TYPES.register(
      "master_gohan",
      () -> Builder.of(AllMastersEntity.MasterGohan::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_gohan").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterBabidi>> MASTER_BABIDI = ENTITY_TYPES.register(
      "master_babidi",
      () -> Builder.of(AllMastersEntity.MasterBabidi::new, MobCategory.CREATURE)
            .sized(0.6F, 1.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_babidi").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterOldKai>> MASTER_OLDKAI = ENTITY_TYPES.register(
      "master_oldkai",
      () -> Builder.of(AllMastersEntity.MasterOldKai::new, MobCategory.CREATURE)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_oldkai").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterCell>> MASTER_CELL = ENTITY_TYPES.register(
      "master_cell",
      () -> Builder.of(AllMastersEntity.MasterCell::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_cell").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterVegeta>> MASTER_VEGETA = ENTITY_TYPES.register(
      "master_vegeta",
      () -> Builder.of(AllMastersEntity.MasterVegeta::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_vegeta").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterFrieza>> MASTER_FRIEZA = ENTITY_TYPES.register(
      "master_frieza",
      () -> Builder.of(AllMastersEntity.MasterFrieza::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_frieza").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterTrunks>> MASTER_TRUNKS = ENTITY_TYPES.register(
      "master_trunks",
      () -> Builder.of(AllMastersEntity.MasterTrunks::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_trunks").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterYamcha>> MASTER_YAMCHA = ENTITY_TYPES.register(
      "master_yamcha",
      () -> Builder.of(AllMastersEntity.MasterYamcha::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_yamcha").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterKrillin>> MASTER_KRILLIN = ENTITY_TYPES.register(
      "master_krillin",
      () -> Builder.of(AllMastersEntity.MasterKrillin::new, MobCategory.CREATURE)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_krillin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterBeerus>> MASTER_BEERUS = ENTITY_TYPES.register(
      "master_beerus",
      () -> Builder.of(AllMastersEntity.MasterBeerus::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_beerus").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<AllMastersEntity.MasterWhis>> MASTER_WHIS = ENTITY_TYPES.register(
      "master_whis",
      () -> Builder.of(AllMastersEntity.MasterWhis::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "master_whis").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<Dino1Entity>> DINOSAUR1 = ENTITY_TYPES.register(
      "dino1",
      () -> Builder.of(Dino1Entity::new, MobCategory.MONSTER).sized(2.2F, 5.1F).build(ResourceLocation.fromNamespaceAndPath("dragonminez", "dino1").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<Dino2Entity>> DINOSAUR2 = ENTITY_TYPES.register(
      "dino2",
      () -> Builder.of(Dino2Entity::new, MobCategory.MONSTER).sized(3.3F, 5.2F).build(ResourceLocation.fromNamespaceAndPath("dragonminez", "dino2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<DinoFlyEntity>> DINOSAUR3 = ENTITY_TYPES.register(
      "dino3",
      () -> Builder.of(DinoFlyEntity::new, MobCategory.MONSTER)
            .sized(1.8F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "dino3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<Dino1Entity>> DINO_KID = ENTITY_TYPES.register(
      "dinokid",
      () -> Builder.of(Dino1Entity::new, MobCategory.MONSTER)
            .sized(1.0F, 1.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "dinokid").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SabertoothEntity>> SABERTOOTH = ENTITY_TYPES.register(
      "sabertooth",
      () -> Builder.of(SabertoothEntity::new, MobCategory.MONSTER)
            .sized(1.8F, 1.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "sabertooth").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<NamekFrogEntity>> NAMEK_FROG = ENTITY_TYPES.register(
      "namek_frog",
      () -> Builder.of(NamekFrogEntity::new, MobCategory.AMBIENT)
            .sized(0.4F, 0.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_frog").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<NamekFrogGinyuEntity>> NAMEK_FROG_GINYU = ENTITY_TYPES.register(
      "namek_frog_ginyu",
      () -> Builder.of(NamekFrogGinyuEntity::new, MobCategory.AMBIENT)
            .sized(0.4F, 0.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_frog_ginyu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<BanditEntity>> BANDIT = ENTITY_TYPES.register(
      "bandit",
      () -> Builder.of(BanditEntity::new, MobCategory.MONSTER)
            .sized(1.4F, 3.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "bandit").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<RobotEntity>> RED_RIBBON_ROBOT1 = ENTITY_TYPES.register(
      "robot1",
      () -> Builder.of(RobotEntity::new, MobCategory.MONSTER)
            .sized(1.7F, 4.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "robot1").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<RobotEntity>> RED_RIBBON_ROBOT2 = ENTITY_TYPES.register(
      "robot2",
      () -> Builder.of(RobotEntity::new, MobCategory.MONSTER)
            .sized(1.7F, 4.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "robot2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<RobotEntity>> RED_RIBBON_ROBOT3 = ENTITY_TYPES.register(
      "robot3",
      () -> Builder.of(RobotEntity::new, MobCategory.MONSTER)
            .sized(1.7F, 4.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "robot3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<RedRibbonSoldierEntity>> RED_RIBBON_SOLDIER = ENTITY_TYPES.register(
      "red_ribbon_soldier",
      () -> Builder.of(RedRibbonSoldierEntity::new, MobCategory.MONSTER)
            .sized(1.0F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "red_ribbon_soldier").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<NamekTraderEntity>> NAMEK_TRADER = ENTITY_TYPES.register(
      "namek_trader",
      () -> Builder.of(NamekTraderEntity::new, MobCategory.CREATURE)
            .sized(1.0F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_trader").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<CCNamekianEntity>> CC_NAMEKIAN = ENTITY_TYPES.register(
      "cc_namekian",
      () -> Builder.of(CCNamekianEntity::new, MobCategory.CREATURE)
            .sized(1.0F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "cc_namekian").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<NamekWarriorEntity>> NAMEK_WARRIOR = ENTITY_TYPES.register(
      "namek_warrior",
      () -> Builder.of(NamekWarriorEntity::new, MobCategory.CREATURE)
            .sized(1.0F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "namek_warrior").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SpacePodEntity>> SPACE_POD = ENTITY_TYPES.register(
      "spacepod",
      () -> Builder.of(SpacePodEntity::new, MobCategory.CREATURE)
            .sized(2.0F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "spacepod").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<FlyingNimbusEntity>> FLYING_NIMBUS = ENTITY_TYPES.register(
      "flying_nimbus",
      () -> Builder.of(FlyingNimbusEntity::new, MobCategory.CREATURE)
            .sized(2.0F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "flying_nimbus").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<BlackNimbusEntity>> BLACK_NIMBUS = ENTITY_TYPES.register(
      "black_nimbus",
      () -> Builder.of(BlackNimbusEntity::new, MobCategory.CREATURE)
            .sized(2.0F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "black_nimbus").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<RobotEntity>> ROBOT_XENOVERSE = ENTITY_TYPES.register(
      "robotxv",
      () -> Builder.of(RobotEntity::new, MobCategory.CREATURE)
            .sized(1.5F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "robotxv").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<PunchMachineEntity>> PUNCH_MACHINE = ENTITY_TYPES.register(
      "punch_machine",
      () -> Builder.of(PunchMachineEntity::new, MobCategory.CREATURE)
            .sized(1.5F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "punch_machine").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<MajinSkillEntity>> MAJIN_SKILL = ENTITY_TYPES.register(
      "majin_skill",
      () -> Builder.of(MajinSkillEntity::new, MobCategory.CREATURE)
            .sized(0.5F, 0.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "majin_skill").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEarlyEntity>> SAGA_GOKU_EARLY = ENTITY_TYPES.register(
      "saga_goku_early",
      () -> Builder.of(SagaGokuEntity.SagaGokuEarlyEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_early").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEarlyEntity>> SAGA_GOKU_EARLY_NOWEIGHTS = ENTITY_TYPES.register(
      "saga_goku_early_noweights",
      () -> Builder.of(SagaGokuEntity.SagaGokuEarlyEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_early_noweights").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaPiccoloEntity.SagaPiccoloEarlyEntity>> SAGA_PICCOLO_EARLY = ENTITY_TYPES.register(
      "saga_piccolo",
      () -> Builder.of(SagaPiccoloEntity.SagaPiccoloEarlyEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_piccolo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.ChaozEntity>> SAGA_CHAOZ = ENTITY_TYPES.register(
      "saga_chaoz",
      () -> Builder.of(SagaZFightersEntity.ChaozEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_chaoz").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN = ENTITY_TYPES.register(
      "saga_saibaman1",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman1").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN2 = ENTITY_TYPES.register(
      "saga_saibaman2",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN3 = ENTITY_TYPES.register(
      "saga_saibaman3",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN4 = ENTITY_TYPES.register(
      "saga_saibaman4",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman4").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN5 = ENTITY_TYPES.register(
      "saga_saibaman5",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman5").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaSaibamanEntity>> SAGA_SAIBAMAN6 = ENTITY_TYPES.register(
      "saga_saibaman6",
      () -> Builder.of(SagaSaibamanEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_saibaman6").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaRaditzEntity>> SAGA_RADITZ = ENTITY_TYPES.register(
      "saga_raditz",
      () -> Builder.of(SagaRaditzEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_raditz").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaNappaEntity>> SAGA_NAPPA = ENTITY_TYPES.register(
      "saga_nappa",
      () -> Builder.of(SagaNappaEntity::new, MobCategory.MONSTER)
            .sized(1.3F, 2.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_nappa").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaExplorerEntity>> SAGA_VEGETA = ENTITY_TYPES.register(
      "saga_vegeta",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaExplorerEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaOzaruEntity>> SAGA_OZARU_VEGETA = ENTITY_TYPES.register(
      "saga_ozaruvegeta",
      () -> Builder.of(SagaOzaruEntity::new, MobCategory.MONSTER)
            .sized(6.5F, 10.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ozaruvegeta").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaOzaruEntity>> SAGA_OZARU = ENTITY_TYPES.register(
      "saga_ozaru",
      () -> Builder.of(SagaOzaruEntity::new, MobCategory.MONSTER)
            .sized(6.5F, 10.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ozaru").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaSoldier01Entity>> SAGA_FRIEZA_SOLDIER = ENTITY_TYPES.register(
      "saga_friezasoldier01",
      () -> Builder.of(SagaFriezaSoldier01Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_friezasoldier01").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaSoldier02Entity>> SAGA_FRIEZA_SOLDIER2 = ENTITY_TYPES.register(
      "saga_friezasoldier02",
      () -> Builder.of(SagaFriezaSoldier02Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_friezasoldier02").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaSoldier02Entity>> SAGA_FRIEZA_SOLDIER3 = ENTITY_TYPES.register(
      "saga_friezasoldier03",
      () -> Builder.of(SagaFriezaSoldier02Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_friezasoldier03").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaSoldier01Entity>> SAGA_MORO_SOLDIER = ENTITY_TYPES.register(
      "saga_morosoldier",
      () -> Builder.of(SagaFriezaSoldier01Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_morosoldier").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCuiEntity>> SAGA_CUI = ENTITY_TYPES.register(
      "saga_cui",
      () -> Builder.of(SagaCuiEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cui").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaDodoriaEntity>> SAGA_DODORIA = ENTITY_TYPES.register(
      "saga_dodoria",
      () -> Builder.of(SagaDodoriaEntity::new, MobCategory.MONSTER)
            .sized(1.0F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_dodoria").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaNamekEntity>> SAGA_VEGETA_NAMEK = ENTITY_TYPES.register(
      "saga_vegeta_namek",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaNamekEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_namek").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZarbonEntity>> SAGA_ZARBON = ENTITY_TYPES.register(
      "saga_zarbon",
      () -> Builder.of(SagaZarbonEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_zarbon").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZarbonEntity.SagaZarbonT1Entity>> SAGA_ZARBON_TRANSF = ENTITY_TYPES.register(
      "saga_zarbont1",
      () -> Builder.of(SagaZarbonEntity.SagaZarbonT1Entity::new, MobCategory.MONSTER)
            .sized(1.3F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_zarbont1").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaGuldoEntity>> SAGA_GULDO = ENTITY_TYPES.register(
      "saga_guldo",
      () -> Builder.of(SagaGinyuForcesEntity.SagaGuldoEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_guldo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaRecoomeEntity>> SAGA_RECOOME = ENTITY_TYPES.register(
      "saga_recoome",
      () -> Builder.of(SagaGinyuForcesEntity.SagaRecoomeEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_recoome").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaBurterEntity>> SAGA_BURTER = ENTITY_TYPES.register(
      "saga_burter",
      () -> Builder.of(SagaGinyuForcesEntity.SagaBurterEntity::new, MobCategory.MONSTER)
            .sized(1.2F, 2.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_burter").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaJeiceEntity>> SAGA_JEICE = ENTITY_TYPES.register(
      "saga_jeice",
      () -> Builder.of(SagaGinyuForcesEntity.SagaJeiceEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_jeice").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaGinyuEntity>> SAGA_GINYU = ENTITY_TYPES.register(
      "saga_ginyu",
      () -> Builder.of(SagaGinyuForcesEntity.SagaGinyuEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ginyu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGinyuForcesEntity.SagaGinyuGokuEntity>> SAGA_GINYU_GOKU = ENTITY_TYPES.register(
      "saga_ginyu_goku",
      () -> Builder.of(SagaGinyuForcesEntity.SagaGinyuGokuEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ginyu_goku").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaPiccoloEntity.SagaNailEntity>> SAGA_NAIL = ENTITY_TYPES.register(
      "saga_nail",
      () -> Builder.of(SagaPiccoloEntity.SagaNailEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_nail").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaFriezaFirstForm>> SAGA_FREEZER_FIRST = ENTITY_TYPES.register(
      "saga_frieza_first",
      () -> Builder.of(SagaFriezaEntity.SagaFriezaFirstForm::new, MobCategory.MONSTER)
            .sized(0.5F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_frieza_first").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaFriezaSecondForm>> SAGA_FREEZER_SECOND = ENTITY_TYPES.register(
      "saga_frieza_second",
      () -> Builder.of(SagaFriezaEntity.SagaFriezaSecondForm::new, MobCategory.MONSTER)
            .sized(0.8F, 2.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_frieza_second").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaFriezaThirdForm>> SAGA_FREEZER_THIRD = ENTITY_TYPES.register(
      "saga_frieza_third",
      () -> Builder.of(SagaFriezaEntity.SagaFriezaThirdForm::new, MobCategory.MONSTER)
            .sized(1.0F, 2.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_frieza_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaFriezaFinalForm>> SAGA_FREEZER_BASE = ENTITY_TYPES.register(
      "saga_frieza_base",
      () -> Builder.of(SagaFriezaEntity.SagaFriezaFinalForm::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_frieza_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaFriezaFPForm>> SAGA_FREEZER_FP = ENTITY_TYPES.register(
      "saga_frieza_fp",
      () -> Builder.of(SagaFriezaEntity.SagaFriezaFPForm::new, MobCategory.MONSTER)
            .sized(0.8F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_frieza_fp").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaKidGohanEntity>> SAGA_KID_GOHAN = ENTITY_TYPES.register(
      "saga_kid_gohan",
      () -> Builder.of(SagaGohanEntity.SagaKidGohanEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_kid_gohan").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaKrillinEntity>> SAGA_KRILLIN = ENTITY_TYPES.register(
      "saga_krillin",
      () -> Builder.of(SagaZFightersEntity.SagaKrillinEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_krillin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaTienShinhanEntity>> SAGA_TIEN_EARLY = ENTITY_TYPES.register(
      "saga_tien_early",
      () -> Builder.of(SagaZFightersEntity.SagaTienShinhanEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_tien_early").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaYamchaEntity>> SAGA_YAMCHA = ENTITY_TYPES.register(
      "saga_yamcha",
      () -> Builder.of(SagaZFightersEntity.SagaYamchaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_yamcha").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuMidBaseEntity>> SAGA_GOKU_MID_BASE = ENTITY_TYPES.register(
      "saga_goku_mid_base",
      () -> Builder.of(SagaGokuEntity.SagaGokuMidBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_mid_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuMidSSJEntity>> SAGA_GOKU_MID_SSJ = ENTITY_TYPES.register(
      "saga_goku_mid_ssj",
      () -> Builder.of(SagaGokuEntity.SagaGokuMidSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_mid_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaMechaFrieza>> SAGA_MECHA_FRIEZA = ENTITY_TYPES.register(
      "saga_mecha_frieza",
      () -> Builder.of(SagaFriezaEntity.SagaMechaFrieza::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_mecha_frieza").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaFriezaEntity.SagaKingCold>> SAGA_KING_COLD = ENTITY_TYPES.register(
      "saga_king_cold",
      () -> Builder.of(SagaFriezaEntity.SagaKingCold::new, MobCategory.MONSTER)
            .sized(0.9F, 2.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_king_cold").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaAndroidsEntity.SagaDrGeroEntity>> SAGA_DRGERO = ENTITY_TYPES.register(
      "saga_drgero",
      () -> Builder.of(SagaAndroidsEntity.SagaDrGeroEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_drgero").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaAndroidsEntity.SagaA19Entity>> SAGA_A19 = ENTITY_TYPES.register(
      "saga_a19",
      () -> Builder.of(SagaAndroidsEntity.SagaA19Entity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a19").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaAndroidsEntity.SagaA18Entity>> SAGA_A18 = ENTITY_TYPES.register(
      "saga_a18",
      () -> Builder.of(SagaAndroidsEntity.SagaA18Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a18").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaAndroidsEntity.SagaA17Entity>> SAGA_A17 = ENTITY_TYPES.register(
      "saga_a17",
      () -> Builder.of(SagaAndroidsEntity.SagaA17Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a17").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaAndroidsEntity.SagaA16Entity>> SAGA_A16 = ENTITY_TYPES.register(
      "saga_a16",
      () -> Builder.of(SagaAndroidsEntity.SagaA16Entity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a16").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCellEntity.SagaImperfectCellEntity>> SAGA_CELL_IMPERFECT = ENTITY_TYPES.register(
      "saga_cell_imperfect",
      () -> Builder.of(SagaCellEntity.SagaImperfectCellEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cell_imperfect").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaPiccoloEntity.SagaPiccoloKamiEntity>> SAGA_PICCOLO_KAMI = ENTITY_TYPES.register(
      "saga_piccolo_kami",
      () -> Builder.of(SagaPiccoloEntity.SagaPiccoloKamiEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_piccolo_kami").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCellEntity.SagaSemiPerfectCellEntity>> SAGA_CELL_SEMIPERFECT = ENTITY_TYPES.register(
      "saga_cell_semiperfect",
      () -> Builder.of(SagaCellEntity.SagaSemiPerfectCellEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cell_semiperfect").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaMidBaseEntity>> SAGA_VEGETA_MID = ENTITY_TYPES.register(
      "saga_vegeta_mid_base",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaMidBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_mid_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaMidSSJEntity>> SAGA_VEGETA_MID_SSJ = ENTITY_TYPES.register(
      "saga_vegeta_mid_ssj",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaMidSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_mid_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaMidSSG2Entity>> SAGA_VEGETA_MID_SSG2 = ENTITY_TYPES.register(
      "saga_vegeta_mid_ssg2",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaMidSSG2Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_mid_ssg2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaFutureTrunksKidBaseEntity>> SAGA_FUTURE_TRUNKS_KID_BASE = ENTITY_TYPES.register(
      "saga_ftrunks_kid_base",
      () -> Builder.of(SagaTrunksEntity.SagaFutureTrunksKidBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ftrunks_kid_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaFutureTrunksKidSSJEntity>> SAGA_FUTURE_TRUNKS_KID_SSJ = ENTITY_TYPES.register(
      "saga_ftrunks_kid_ssj",
      () -> Builder.of(SagaTrunksEntity.SagaFutureTrunksKidSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ftrunks_kid_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaFutureTrunksBaseEntity>> SAGA_FUTURE_TRUNKS_BASE = ENTITY_TYPES.register(
      "saga_ftrunks_base",
      () -> Builder.of(SagaTrunksEntity.SagaFutureTrunksBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ftrunks_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaFutureTrunksSSJEntity>> SAGA_FUTURE_TRUNKS_SSJ = ENTITY_TYPES.register(
      "saga_ftrunks_ssj",
      () -> Builder.of(SagaTrunksEntity.SagaFutureTrunksSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ftrunks_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaFutureTrunksSSG3Entity>> SAGA_FUTURE_TRUNKS_SSG3 = ENTITY_TYPES.register(
      "saga_ftrunks_ssg3",
      () -> Builder.of(SagaTrunksEntity.SagaFutureTrunksSSG3Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_ftrunks_ssg3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCellEntity.SagaPerfectCellEntity>> SAGA_CELL_PERFECT = ENTITY_TYPES.register(
      "saga_cell_perfect",
      () -> Builder.of(SagaCellEntity.SagaPerfectCellEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cell_perfect").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanMidBaseEntity>> SAGA_GOHAN_MID_BASE = ENTITY_TYPES.register(
      "saga_gohan_mid_base",
      () -> Builder.of(SagaGohanEntity.SagaGohanMidBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_mid_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanMidSSJEntity>> SAGA_GOHAN_MID_SSJ = ENTITY_TYPES.register(
      "saga_gohan_mid_ssj",
      () -> Builder.of(SagaGohanEntity.SagaGohanMidSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_mid_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanMidSSJ2Entity>> SAGA_GOHAN_MID_SSJ2 = ENTITY_TYPES.register(
      "saga_gohan_mid_ssj2",
      () -> Builder.of(SagaGohanEntity.SagaGohanMidSSJ2Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_mid_ssj2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaFutureGohanBaseEntity>> SAGA_FUTURE_GOHAN_BASE = ENTITY_TYPES.register(
      "saga_fgohan_base",
      () -> Builder.of(SagaGohanEntity.SagaFutureGohanBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_fgohan_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaFutureGohanSSJEntity>> SAGA_FUTURE_GOHAN_SSJ = ENTITY_TYPES.register(
      "saga_fgohan_ssj",
      () -> Builder.of(SagaGohanEntity.SagaFutureGohanSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_fgohan_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCellEntity.SagaSuperPerfectCellEntity>> SAGA_CELL_SUPERPERFECT = ENTITY_TYPES.register(
      "saga_cell_superperfect",
      () -> Builder.of(SagaCellEntity.SagaSuperPerfectCellEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cell_superperfect").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaCellEntity.SagaCellJREntity>> SAGA_CELL_JR = ENTITY_TYPES.register(
      "saga_cell_jr",
      () -> Builder.of(SagaCellEntity.SagaCellJREntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cell_jr").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEndBaseEntity>> SAGA_GOKU_END_BASE = ENTITY_TYPES.register(
      "saga_goku_end_base",
      () -> Builder.of(SagaGokuEntity.SagaGokuEndBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_end_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEndSSJEntity>> SAGA_GOKU_END_SSJ = ENTITY_TYPES.register(
      "saga_goku_end_ssj",
      () -> Builder.of(SagaGokuEntity.SagaGokuEndSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_end_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEndSSJ2Entity>> SAGA_GOKU_END_SSJ2 = ENTITY_TYPES.register(
      "saga_goku_end_ssj2",
      () -> Builder.of(SagaGokuEntity.SagaGokuEndSSJ2Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_end_ssj2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGokuEntity.SagaGokuEndSSJ3Entity>> SAGA_GOKU_END_SSJ3 = ENTITY_TYPES.register(
      "saga_goku_end_ssj3",
      () -> Builder.of(SagaGokuEntity.SagaGokuEndSSJ3Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goku_end_ssj3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaEndBaseEntity>> SAGA_VEGETA_END_BASE = ENTITY_TYPES.register(
      "saga_vegeta_end_base",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaEndBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_end_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaEndSSJEntity>> SAGA_VEGETA_END_SSJ = ENTITY_TYPES.register(
      "saga_vegeta_end_ssj",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaEndSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_end_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegetaEndSSJ2Entity>> SAGA_VEGETA_END_SSJ2 = ENTITY_TYPES.register(
      "saga_vegeta_end_ssj2",
      () -> Builder.of(SagaVegetaEntity.SagaVegetaEndSSJ2Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_end_ssj2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaMajinVegetaEntity>> SAGA_VEGETA_MAJIN = ENTITY_TYPES.register(
      "saga_vegeta_majin",
      () -> Builder.of(SagaVegetaEntity.SagaMajinVegetaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegeta_majin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanEndBaseEntity>> SAGA_GOHAN_END_BASE = ENTITY_TYPES.register(
      "saga_gohan_end_base",
      () -> Builder.of(SagaGohanEntity.SagaGohanEndBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_end_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanEndSSJEntity>> SAGA_GOHAN_END_SSJ = ENTITY_TYPES.register(
      "saga_gohan_end_ssj",
      () -> Builder.of(SagaGohanEntity.SagaGohanEndSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_end_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanEndSSJ2Entity>> SAGA_GOHAN_END_SSJ2 = ENTITY_TYPES.register(
      "saga_gohan_end_ssj2",
      () -> Builder.of(SagaGohanEntity.SagaGohanEndSSJ2Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_end_ssj2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGohanEntity.SagaGohanEndUltimateEntity>> SAGA_GOHAN_END_ULTIMATE = ENTITY_TYPES.register(
      "saga_gohan_end_ultimate",
      () -> Builder.of(SagaGohanEntity.SagaGohanEndUltimateEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gohan_end_ultimate").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGotenEntity.SagaGotenKidEntity>> SAGA_GOTEN = ENTITY_TYPES.register(
      "saga_goten",
      () -> Builder.of(SagaGotenEntity.SagaGotenKidEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goten").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGotenEntity.SagaGotenKidSSJEntity>> SAGA_GOTEN_SSJ = ENTITY_TYPES.register(
      "saga_goten_ssj",
      () -> Builder.of(SagaGotenEntity.SagaGotenKidSSJEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_goten_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaKidTrunksBaseEntity>> SAGA_KID_TRUNKS = ENTITY_TYPES.register(
      "saga_kid_trunks",
      () -> Builder.of(SagaTrunksEntity.SagaKidTrunksBaseEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_kid_trunks").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaTrunksEntity.SagaKidTrunksSSJEntity>> SAGA_KID_TRUNKS_SSJ = ENTITY_TYPES.register(
      "saga_kid_trunks_ssj",
      () -> Builder.of(SagaTrunksEntity.SagaKidTrunksSSJEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_kid_trunks_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGotenEntity.SagaGotenksBaseEntity>> SAGA_GOTENKS = ENTITY_TYPES.register(
      "saga_gotenks",
      () -> Builder.of(SagaGotenEntity.SagaGotenksBaseEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gotenks").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGotenEntity.SagaGotenksSSJEntity>> SAGA_GOTENKS_SSJ = ENTITY_TYPES.register(
      "saga_gotenks_ssj",
      () -> Builder.of(SagaGotenEntity.SagaGotenksSSJEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gotenks_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaGotenEntity.SagaGotenksSSJ3Entity>> SAGA_GOTENKS_SSJ3 = ENTITY_TYPES.register(
      "saga_gotenks_ssj3",
      () -> Builder.of(SagaGotenEntity.SagaGotenksSSJ3Entity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gotenks_ssj3").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaShinEntity>> SAGA_SHIN = ENTITY_TYPES.register(
      "saga_shin",
      () -> Builder.of(SagaZFightersEntity.SagaShinEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_shin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaShinEntity>> SAGA_VIDEL = ENTITY_TYPES.register(
      "saga_videl",
      () -> Builder.of(SagaZFightersEntity.SagaShinEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_videl").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.BasicNPCEntity>> SAGA_BULMA = ENTITY_TYPES.register(
      "saga_bulma",
      () -> Builder.of(SagaZFightersEntity.BasicNPCEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bulma").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaZFightersEntity.SagaKibitoEntity>> SAGA_KIBITO = ENTITY_TYPES.register(
      "saga_kibito",
      () -> Builder.of(SagaZFightersEntity.SagaKibitoEntity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_kibito").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBabidiSoldiersEntity.SagaSpopovitchEntity>> SAGA_SPOPOVITCH = ENTITY_TYPES.register(
      "saga_spopovitch",
      () -> Builder.of(SagaBabidiSoldiersEntity.SagaSpopovitchEntity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_spopovitch").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBabidiSoldiersEntity.SagaPuiPuiEntity>> SAGA_PUIPUI = ENTITY_TYPES.register(
      "saga_puipui",
      () -> Builder.of(SagaBabidiSoldiersEntity.SagaPuiPuiEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_puipui").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBabidiSoldiersEntity.SagaYakonEntity>> SAGA_YAKON = ENTITY_TYPES.register(
      "saga_yakon",
      () -> Builder.of(SagaBabidiSoldiersEntity.SagaYakonEntity::new, MobCategory.MONSTER)
            .sized(1.1F, 2.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_yakon").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBabidiSoldiersEntity.DaburaEntity>> SAGA_DABURA = ENTITY_TYPES.register(
      "saga_dabura",
      () -> Builder.of(SagaBabidiSoldiersEntity.DaburaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_dabura").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBabidiSoldiersEntity.BabidiEntity>> SAGA_BABIDI = ENTITY_TYPES.register(
      "saga_babidi",
      () -> Builder.of(SagaBabidiSoldiersEntity.BabidiEntity::new, MobCategory.MONSTER)
            .sized(0.4F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_babidi").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.BuuFatEntity>> SAGA_BUU_FAT = ENTITY_TYPES.register(
      "saga_buufat",
      () -> Builder.of(SagaBuuEntity.BuuFatEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 1.9F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_buufat").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.EvilBuuEntity>> SAGA_EVILBUU = ENTITY_TYPES.register(
      "saga_evilbuu",
      () -> Builder.of(SagaBuuEntity.EvilBuuEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_evilbuu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.MiniBuuEntity>> MINI_BUU = ENTITY_TYPES.register(
      "mini_buu",
      () -> Builder.of(SagaBuuEntity.MiniBuuEntity::new, MobCategory.MONSTER)
            .sized(0.4F, 0.9F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "mini_buu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.SuperBuuEntity>> SAGA_SUPERBUU = ENTITY_TYPES.register(
      "saga_superbuu",
      () -> Builder.of(SagaBuuEntity.SuperBuuEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_superbuu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.SuperBuuPiccoloEntity>> SAGA_SUPERBUU_PICCOLO = ENTITY_TYPES.register(
      "saga_superbuu_piccolo",
      () -> Builder.of(SagaBuuEntity.SuperBuuPiccoloEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_superbuu_piccolo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.SuperBuuGotenksEntity>> SAGA_SUPERBUU_GOTENKS = ENTITY_TYPES.register(
      "saga_superbuu_gotenks",
      () -> Builder.of(SagaBuuEntity.SuperBuuGotenksEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_superbuu_gotenks").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.SuperBuuGohanEntity>> SAGA_SUPERBUU_GOHAN = ENTITY_TYPES.register(
      "saga_superbuu_gohan",
      () -> Builder.of(SagaBuuEntity.SuperBuuGohanEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_superbuu_gohan").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaBuuEntity.KidBuuEntity>> SAGA_KIDBUU = ENTITY_TYPES.register(
      "saga_kidbuu",
      () -> Builder.of(SagaBuuEntity.KidBuuEntity::new, MobCategory.MONSTER)
            .sized(0.4F, 1.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_kidbuu").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegettoBaseEntity>> SAGA_VEGETTO_BASE = ENTITY_TYPES.register(
      "saga_vegetto_base",
      () -> Builder.of(SagaVegetaEntity.SagaVegettoBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegetto_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaVegetaEntity.SagaVegettoSSJEntity>> SAGA_VEGETTO_SSJ = ENTITY_TYPES.register(
      "saga_vegetto_ssj",
      () -> Builder.of(SagaVegetaEntity.SagaVegettoSSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_vegetto_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.GarlickJrEntity>> SAGA_GARLICK_JR = ENTITY_TYPES.register(
      "saga_garlick_jr",
      () -> Builder.of(SagaMoviesEntity.GarlickJrEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_garlick_jr").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.GarlickJrTransformedEntity>> SAGA_GARLICK_JR_TRANSFORMED = ENTITY_TYPES.register(
      "saga_garlick_jr_transformed",
      () -> Builder.of(SagaMoviesEntity.GarlickJrTransformedEntity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_garlick_jr_transformed").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.DrWheeloEntity>> SAGA_DR_WHEELO = ENTITY_TYPES.register(
      "saga_dr_wheelo",
      () -> Builder.of(SagaMoviesEntity.DrWheeloEntity::new, MobCategory.MONSTER)
            .sized(3.0F, 4.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_dr_wheelo").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.TurlesEntity>> SAGA_TURLES = ENTITY_TYPES.register(
      "saga_turles",
      () -> Builder.of(SagaMoviesEntity.TurlesEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_turles").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SlugSoldierEntity>> SAGA_SLUG_SOLDIER = ENTITY_TYPES.register(
      "saga_slug_soldier",
      () -> Builder.of(SagaMoviesEntity.SlugSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_slug_soldier").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SlugEntity>> SAGA_SLUG = ENTITY_TYPES.register(
      "saga_slug",
      () -> Builder.of(SagaMoviesEntity.SlugEntity::new, MobCategory.MONSTER)
            .sized(0.7F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_slug").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SlugGiantEntity>> SAGA_SLUG_GIANT = ENTITY_TYPES.register(
      "saga_slug_giant",
      () -> Builder.of(SagaMoviesEntity.SlugGiantEntity::new, MobCategory.MONSTER)
            .sized(4.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_slug_giant").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.CoolerSoldierEntity>> SAGA_SALZA = ENTITY_TYPES.register(
      "saga_salza",
      () -> Builder.of(SagaMoviesEntity.CoolerSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_salza").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.CoolerSoldierEntity>> SAGA_DORE = ENTITY_TYPES.register(
      "saga_dore",
      () -> Builder.of(SagaMoviesEntity.CoolerSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_dore").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.CoolerSoldierEntity>> SAGA_NEIZ = ENTITY_TYPES.register(
      "saga_neiz",
      () -> Builder.of(SagaMoviesEntity.CoolerSoldierEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_neiz").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.CoolerEntity>> SAGA_COOLER = ENTITY_TYPES.register(
      "saga_cooler",
      () -> Builder.of(SagaMoviesEntity.CoolerEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cooler").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.Cooler5TAEntity>> SAGA_COOLER_5TA = ENTITY_TYPES.register(
      "saga_cooler_5ta",
      () -> Builder.of(SagaMoviesEntity.Cooler5TAEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_cooler_5ta").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.GeteRobotEntity>> SAGA_GETE_ROBOT = ENTITY_TYPES.register(
      "saga_gete_robot",
      () -> Builder.of(SagaMoviesEntity.GeteRobotEntity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.4F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gete_robot").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.MetalCoolerEntity>> SAGA_METAL_COOLER = ENTITY_TYPES.register(
      "saga_metal_cooler",
      () -> Builder.of(SagaMoviesEntity.MetalCoolerEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_metal_cooler").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.MetalCoolerCoreEntity>> SAGA_METAL_COOLER_CORE = ENTITY_TYPES.register(
      "saga_metal_cooler_core",
      () -> Builder.of(SagaMoviesEntity.MetalCoolerCoreEntity::new, MobCategory.MONSTER)
            .sized(4.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_metal_cooler_core").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.A14Entity>> SAGA_A14 = ENTITY_TYPES.register(
      "saga_a14",
      () -> Builder.of(SagaMoviesEntity.A14Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a14").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.A15Entity>> SAGA_A15 = ENTITY_TYPES.register(
      "saga_a15",
      () -> Builder.of(SagaMoviesEntity.A15Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a15").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.A13Entity>> SAGA_A13 = ENTITY_TYPES.register(
      "saga_a13",
      () -> Builder.of(SagaMoviesEntity.A13Entity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_a13").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SuperA13Entity>> SAGA_SUPER_A13 = ENTITY_TYPES.register(
      "saga_super_a13",
      () -> Builder.of(SagaMoviesEntity.SuperA13Entity::new, MobCategory.MONSTER)
            .sized(1.0F, 2.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_super_a13").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.ParagusEntity>> SAGA_PARAGUS = ENTITY_TYPES.register(
      "saga_paragus",
      () -> Builder.of(SagaMoviesEntity.ParagusEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_paragus").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BrolyBaseEntity>> SAGA_BROLY_BASE = ENTITY_TYPES.register(
      "saga_broly_base",
      () -> Builder.of(SagaMoviesEntity.BrolyBaseEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_broly_base").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BrolySSJRestringidoEntity>> SAGA_BROLY_SSJ_RESTRICTED = ENTITY_TYPES.register(
      "saga_broly_ssj_restricted",
      () -> Builder.of(SagaMoviesEntity.BrolySSJRestringidoEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_broly_ssj_restricted").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BrolySSJEntity>> SAGA_BROLY_SSJ = ENTITY_TYPES.register(
      "saga_broly_ssj",
      () -> Builder.of(SagaMoviesEntity.BrolySSJEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.1F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_broly_ssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BrolySSJLegendarioEntity>> SAGA_BROLY_LSSJ = ENTITY_TYPES.register(
      "saga_broly_lssj",
      () -> Builder.of(SagaMoviesEntity.BrolySSJLegendarioEntity::new, MobCategory.MONSTER)
            .sized(1.0F, 2.7F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_broly_lssj").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.ZangyaEntity>> SAGA_ZANGYA = ENTITY_TYPES.register(
      "saga_zangya",
      () -> Builder.of(SagaMoviesEntity.ZangyaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_zangya").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.GokuaEntity>> SAGA_GOKUA = ENTITY_TYPES.register(
      "saga_gokua",
      () -> Builder.of(SagaMoviesEntity.GokuaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_gokua").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BidoEntity>> SAGA_BIDO = ENTITY_TYPES.register(
      "saga_bido",
      () -> Builder.of(SagaMoviesEntity.BidoEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bido").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BujinEntity>> SAGA_BUJIN = ENTITY_TYPES.register(
      "saga_bujin",
      () -> Builder.of(SagaMoviesEntity.BujinEntity::new, MobCategory.MONSTER)
            .sized(0.5F, 1.6F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bujin").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BojackEntity>> SAGA_BOJACK = ENTITY_TYPES.register(
      "saga_bojack",
      () -> Builder.of(SagaMoviesEntity.BojackEntity::new, MobCategory.MONSTER)
            .sized(0.7F, 2.2F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bojack").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BojackFullPowerEntity>> SAGA_BOJACK_FP = ENTITY_TYPES.register(
      "saga_bojack_fp",
      () -> Builder.of(SagaMoviesEntity.BojackFullPowerEntity::new, MobCategory.MONSTER)
            .sized(0.9F, 2.3F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bojack_fp").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BioBrolyEntity>> SAGA_BIO_BROLY = ENTITY_TYPES.register(
      "saga_bio_broly",
      () -> Builder.of(SagaMoviesEntity.BioBrolyEntity::new, MobCategory.MONSTER)
            .sized(0.8F, 2.5F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bio_broly").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.BioBrolyGiganteEntity>> SAGA_BIO_BROLY_GIANT = ENTITY_TYPES.register(
      "saga_bio_broly_giant",
      () -> Builder.of(SagaMoviesEntity.BioBrolyGiganteEntity::new, MobCategory.MONSTER)
            .sized(4.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_bio_broly_giant").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.PaikuhanEntity>> SAGA_PAIKUHAN = ENTITY_TYPES.register(
      "saga_paikuhan",
      () -> Builder.of(SagaMoviesEntity.PaikuhanEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_paikuhan").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.JanembaGordoEntity>> SAGA_JANEMBA_FAT = ENTITY_TYPES.register(
      "saga_janemba_fat",
      () -> Builder.of(SagaMoviesEntity.JanembaGordoEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_janemba_fat").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SuperJanembaEntity>> SAGA_SUPER_JANEMBA = ENTITY_TYPES.register(
      "saga_super_janemba",
      () -> Builder.of(SagaMoviesEntity.SuperJanembaEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_super_janemba").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.HirudegarnEntity>> SAGA_HIRUDEGARN = ENTITY_TYPES.register(
      "saga_hirudegarn",
      () -> Builder.of(SagaMoviesEntity.HirudegarnEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_hirudegarn").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.HirudegarnEntity>> SAGA_HIRUDEGARN_INCOMPLETE_1 = ENTITY_TYPES.register(
      "saga_hirudegarn_incomplete1",
      () -> Builder.of(SagaMoviesEntity.HirudegarnEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_hirudegarn_incomplete1").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.HirudegarnEntity>> SAGA_HIRUDEGARN_INCOMPLETE_2 = ENTITY_TYPES.register(
      "saga_hirudegarn_incomplete2",
      () -> Builder.of(SagaMoviesEntity.HirudegarnEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_hirudegarn_incomplete2").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SagaMoviesEntity.SuperHirudegarnEntity>> SAGA_SUPER_HIRUDEGARN = ENTITY_TYPES.register(
      "saga_super_hirudegarn",
      () -> Builder.of(SagaMoviesEntity.SuperHirudegarnEntity::new, MobCategory.MONSTER)
            .sized(7.5F, 12.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "saga_super_hirudegarn").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<ShadowDummyEntity>> SHADOW_DUMMY = ENTITY_TYPES.register(
      "shadow_dummy",
      () -> Builder.of(ShadowDummyEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.8F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "shadow_dummy").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiBlastEntity>> KI_BLAST = ENTITY_TYPES.register(
      "ki_blast",
      () -> Builder.of(KiBlastEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(4).updateInterval(10).fireImmune().build("ki_blast")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SPBlueHurricaneEntity>> SP_BLUE_HURRICANE = ENTITY_TYPES.register(
      "sp_blue_hurricane",
      () -> Builder.of(SPBlueHurricaneEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .clientTrackingRange(4)
            .updateInterval(10)
            .fireImmune()
            .build("sp_blue_hurricane")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiLaserEntity>> KI_LASER = ENTITY_TYPES.register(
      "ki_laser",
      () -> Builder.of(KiLaserEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(128).updateInterval(1).fireImmune().build("ki_laser")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiWaveEntity>> KI_WAVE = ENTITY_TYPES.register(
      "ki_wave",
      () -> Builder.of(KiWaveEntity::new, MobCategory.MISC)
            .sized(2.5F, 2.5F)
            .clientTrackingRange(64)
            .updateInterval(1)
            .fireImmune()
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "ki_wave").toString())
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiDiskEntity>> KI_DISC = ENTITY_TYPES.register(
      "ki_disc",
      () -> Builder.of(KiDiskEntity::new, MobCategory.MISC).sized(1.0F, 0.1F).clientTrackingRange(64).updateInterval(1).fireImmune().build("ki_disc")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiBarrierEntity>> KI_BARRIER = ENTITY_TYPES.register(
      "ki_barrier",
      () -> Builder.of(KiBarrierEntity::new, MobCategory.MISC).sized(0.8F, 0.8F).clientTrackingRange(64).updateInterval(10).fireImmune().build("ki_barrier")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiExplosionEntity>> KI_EXPLOSION = ENTITY_TYPES.register(
      "ki_explosion",
      () -> Builder.of(KiExplosionEntity::new, MobCategory.MISC)
            .sized(0.8F, 0.8F)
            .clientTrackingRange(64)
            .updateInterval(10)
            .fireImmune()
            .build("ki_explosion")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiAreaEntity>> KI_AREA = ENTITY_TYPES.register(
      "ki_area", () -> Builder.of(KiAreaEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(3).noSave().build("ki_area")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<KiExplosionVisualEntity>> KI_EXPLOSION_VISUAL = ENTITY_TYPES.register(
      "ki_explosion_visual",
      () -> Builder.of(KiExplosionVisualEntity::new, MobCategory.MISC)
            .sized(1.0F, 1.0F)
            .clientTrackingRange(10)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .build("ki_explosion_visual")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SPDragonFistEntity>> SP_DRAGON_FIST = ENTITY_TYPES.register(
      "sp_dragon_fist",
      () -> Builder.of(SPDragonFistEntity::new, MobCategory.MISC).sized(2.0F, 2.0F).clientTrackingRange(10).updateInterval(1).build("sp_dragon_fist")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<OzaruFistEntity>> SP_OZARU_FIST = ENTITY_TYPES.register(
      "sp_ozaru_fist",
      () -> Builder.of(OzaruFistEntity::new, MobCategory.MISC).sized(2.0F, 2.0F).clientTrackingRange(10).updateInterval(1).build("sp_ozaru_fist")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<SPMajinCandyEntity>> SP_MAJIN_CANDY = ENTITY_TYPES.register(
      "sp_majin_candy",
      () -> Builder.of(SPMajinCandyEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).clientTrackingRange(10).updateInterval(1).build("sp_majin_candy")
   );
   public static final DeferredHolder<EntityType<?>, ? extends EntityType<QuestNPCEntity>> QUEST_NPC = ENTITY_TYPES.register(
      "quest_npc",
      () -> Builder.of(QuestNPCEntity::new, MobCategory.CREATURE)
            .sized(0.8F, 2.0F)
            .build(ResourceLocation.fromNamespaceAndPath("dragonminez", "quest_npc").toString())
   );

   public static List<DeferredHolder<EntityType<?>, ? extends EntityType<?>>> getMasterEntities() {
      return List.of(
         MASTER_KARIN,
         MASTER_GOKU,
         MASTER_KAIOSAMA,
         MASTER_ROSHI,
         MASTER_URANAI,
         MASTER_ENMA,
         MASTER_DENDE,
         MASTER_GERO,
         MASTER_POPO,
         MASTER_GURU,
         MASTER_TORIBOT,
         MASTER_PICCOLO,
         MASTER_GOHAN,
         MASTER_BABIDI,
         MASTER_OLDKAI,
         MASTER_CELL,
         MASTER_VEGETA,
         MASTER_FRIEZA,
         MASTER_TRUNKS,
         MASTER_YAMCHA,
         MASTER_KRILLIN,
         MASTER_BEERUS,
         MASTER_WHIS
      );
   }

   public static List<DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>>> getSagaEntities() {
      return List.of(
         SAGA_GOKU_EARLY,
         SAGA_GOKU_EARLY_NOWEIGHTS,
         SAGA_PICCOLO_EARLY,
         SAGA_CHAOZ,
         SAGA_SAIBAMAN,
         SAGA_SAIBAMAN2,
         SAGA_SAIBAMAN3,
         SAGA_SAIBAMAN4,
         SAGA_SAIBAMAN5,
         SAGA_SAIBAMAN6,
         SAGA_RADITZ,
         SAGA_NAPPA,
         SAGA_VEGETA,
         SAGA_OZARU_VEGETA,
         SAGA_OZARU,
         SAGA_FRIEZA_SOLDIER,
         SAGA_FRIEZA_SOLDIER2,
         SAGA_FRIEZA_SOLDIER3,
         SAGA_MORO_SOLDIER,
         SAGA_CUI,
         SAGA_DODORIA,
         SAGA_VEGETA_NAMEK,
         SAGA_ZARBON,
         SAGA_ZARBON_TRANSF,
         SAGA_GULDO,
         SAGA_RECOOME,
         SAGA_BURTER,
         SAGA_JEICE,
         SAGA_GINYU,
         SAGA_GINYU_GOKU,
         SAGA_NAIL,
         SAGA_FREEZER_FIRST,
         SAGA_FREEZER_SECOND,
         SAGA_FREEZER_THIRD,
         SAGA_FREEZER_BASE,
         SAGA_FREEZER_FP,
         SAGA_KID_GOHAN,
         SAGA_KRILLIN,
         SAGA_TIEN_EARLY,
         SAGA_YAMCHA,
         SAGA_GOKU_MID_BASE,
         SAGA_GOKU_MID_SSJ,
         SAGA_MECHA_FRIEZA,
         SAGA_KING_COLD,
         SAGA_DRGERO,
         SAGA_A19,
         SAGA_A18,
         SAGA_A17,
         SAGA_A16,
         SAGA_CELL_IMPERFECT,
         SAGA_PICCOLO_KAMI,
         SAGA_CELL_SEMIPERFECT,
         SAGA_VEGETA_MID,
         SAGA_VEGETA_MID_SSJ,
         SAGA_VEGETA_MID_SSG2,
         SAGA_FUTURE_TRUNKS_KID_BASE,
         SAGA_FUTURE_TRUNKS_KID_SSJ,
         SAGA_FUTURE_TRUNKS_BASE,
         SAGA_FUTURE_TRUNKS_SSJ,
         SAGA_FUTURE_TRUNKS_SSG3,
         SAGA_CELL_PERFECT,
         SAGA_GOHAN_MID_BASE,
         SAGA_GOHAN_MID_SSJ,
         SAGA_GOHAN_MID_SSJ2,
         SAGA_FUTURE_GOHAN_BASE,
         SAGA_FUTURE_GOHAN_SSJ,
         SAGA_CELL_SUPERPERFECT,
         SAGA_CELL_JR,
         SAGA_GOKU_END_BASE,
         SAGA_GOKU_END_SSJ,
         SAGA_GOKU_END_SSJ2,
         SAGA_GOKU_END_SSJ3,
         SAGA_VEGETA_END_BASE,
         SAGA_VEGETA_END_SSJ,
         SAGA_VEGETA_END_SSJ2,
         SAGA_VEGETA_MAJIN,
         SAGA_GOHAN_END_BASE,
         SAGA_GOHAN_END_SSJ,
         SAGA_GOHAN_END_SSJ2,
         SAGA_GOHAN_END_ULTIMATE,
         SAGA_GOTEN,
         SAGA_GOTEN_SSJ,
         SAGA_KID_TRUNKS,
         SAGA_KID_TRUNKS_SSJ,
         SAGA_GOTENKS,
         SAGA_GOTENKS_SSJ,
         SAGA_GOTENKS_SSJ3,
         SAGA_SHIN,
         SAGA_VIDEL,
         SAGA_BULMA,
         SAGA_KIBITO,
         SAGA_SPOPOVITCH,
         SAGA_PUIPUI,
         SAGA_YAKON,
         SAGA_DABURA,
         SAGA_BABIDI,
         SAGA_BUU_FAT,
         SAGA_EVILBUU,
         SAGA_SUPERBUU,
         SAGA_SUPERBUU_PICCOLO,
         SAGA_SUPERBUU_GOTENKS,
         SAGA_SUPERBUU_GOHAN,
         SAGA_KIDBUU,
         SAGA_VEGETTO_BASE,
         SAGA_VEGETTO_SSJ,
         SAGA_GARLICK_JR,
         SAGA_GARLICK_JR_TRANSFORMED,
         SAGA_DR_WHEELO,
         SAGA_TURLES,
         SAGA_SLUG_SOLDIER,
         SAGA_SLUG,
         SAGA_SLUG_GIANT,
         SAGA_DORE,
         SAGA_SALZA,
         SAGA_NEIZ,
         SAGA_COOLER,
         SAGA_COOLER_5TA,
         SAGA_GETE_ROBOT,
         SAGA_METAL_COOLER,
         SAGA_METAL_COOLER_CORE,
         SAGA_A14,
         SAGA_A15,
         SAGA_A13,
         SAGA_SUPER_A13,
         SAGA_PARAGUS,
         SAGA_BROLY_BASE,
         SAGA_BROLY_SSJ_RESTRICTED,
         SAGA_BROLY_SSJ,
         SAGA_BROLY_LSSJ,
         SAGA_ZANGYA,
         SAGA_GOKUA,
         SAGA_BIDO,
         SAGA_BUJIN,
         SAGA_BOJACK,
         SAGA_BOJACK_FP,
         SAGA_BIO_BROLY,
         SAGA_BIO_BROLY_GIANT,
         SAGA_PAIKUHAN,
         SAGA_JANEMBA_FAT,
         SAGA_SUPER_JANEMBA,
         SAGA_HIRUDEGARN,
         SAGA_HIRUDEGARN_INCOMPLETE_1,
         SAGA_HIRUDEGARN_INCOMPLETE_2,
         SAGA_SUPER_HIRUDEGARN,
         SHADOW_DUMMY,
         MINI_BUU
      );
   }

   private static Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>>> registerDragonWishEntities() {
      Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>>> registered = new LinkedHashMap<>();

      for (DragonDefinition definition : DragonBallDefinitions.getBootstrapDragons()) {
         DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> entity = ENTITY_TYPES.register(
            definition.getEntityRegistryName(),
            () -> Builder.of((type, level) -> new DragonWishEntity(type, level, definition.getId()), MobCategory.CREATURE)
                  .sized(definition.getEntityWidth(), definition.getEntityHeight())
                  .build(ResourceLocation.fromNamespaceAndPath("dragonminez", definition.getEntityRegistryName()).toString())
         );
         registered.put(definition.getId(), entity);
      }

      return Map.copyOf(registered);
   }

   public static DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> getDragonWishEntityOrThrow(String dragonId) {
      DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> entity = DRAGON_WISH_ENTITIES.get(dragonId);
      if (entity == null) {
         throw new IllegalArgumentException("No dragon wish entity registered for dragon '" + dragonId + "'");
      } else {
         return entity;
      }
   }

   public static Map<String, DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>>> getDragonWishEntities() {
      return DRAGON_WISH_ENTITIES;
   }

   public static void register(IEventBus eventBus) {
      ENTITY_TYPES.register(eventBus);
   }

   @SubscribeEvent
   public static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> sE : getSagaEntities()) {
         registerSagaSpawn(event, (EntityType)sE.get());
      }

      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> dE : List.of(DINOSAUR1, DINOSAUR2, DINOSAUR3, DINO_KID, SABERTOOTH)) {
         registerDinoSpawn(event, (EntityType)dE.get());
      }

      registerUnrestrictedSpawn(event, (EntityType)NAMEK_FROG.get());
      registerUnrestrictedSpawn(event, (EntityType)NAMEK_FROG_GINYU.get());

      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> rrE : List.of(
         BANDIT, RED_RIBBON_ROBOT1, RED_RIBBON_ROBOT2, RED_RIBBON_ROBOT3, RED_RIBBON_SOLDIER
      )) {
         registerRedRibbonSpawn(event, (EntityType)rrE.get());
      }
   }

   private static <T extends Mob> void registerSagaSpawn(RegisterSpawnPlacementsEvent event, EntityType<T> entityType) {
      event.register(
         entityType,
         SpawnPlacementTypes.ON_GROUND,
         Types.MOTION_BLOCKING,
         (e, w, r, p, rand) -> DBSagasEntity.canSpawnHere(e, w, r, p, rand),
         Operation.REPLACE
      );
   }

   private static <T extends Mob> void registerDinoSpawn(RegisterSpawnPlacementsEvent event, EntityType<T> entityType) {
      event.register(
         entityType,
         SpawnPlacementTypes.ON_GROUND,
         Types.MOTION_BLOCKING,
         (e, w, r, p, rand) -> DinoGlobalEntity.canSpawnHere(e, w, r, p, rand),
         Operation.REPLACE
      );
   }

   private static <T extends Mob> void registerRedRibbonSpawn(RegisterSpawnPlacementsEvent event, EntityType<T> entityType) {
      event.register(
         entityType,
         SpawnPlacementTypes.ON_GROUND,
         Types.MOTION_BLOCKING,
         (e, w, r, p, rand) -> RedRibbonEntity.canSpawnHere(e, w, r, p, rand),
         Operation.REPLACE
      );
   }

   private static <T extends Mob> void registerUnrestrictedSpawn(RegisterSpawnPlacementsEvent event, EntityType<T> entityType) {
      event.register(entityType, SpawnPlacementTypes.NO_RESTRICTIONS, Types.MOTION_BLOCKING, (e, w, r, p, rand) -> true, Operation.REPLACE);
   }
}
