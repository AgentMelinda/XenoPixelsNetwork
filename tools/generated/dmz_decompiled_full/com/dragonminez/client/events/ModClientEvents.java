package com.dragonminez.client.events;

import com.dragonminez.client.animation.CombatAnimationResolver;
import com.dragonminez.client.crowdin.CrowdinManager;
import com.dragonminez.client.crowdin.CrowdinPackResources;
import com.dragonminez.client.dragonball.DragonBallPackResources;
import com.dragonminez.client.gui.UtilityMenuScreen;
import com.dragonminez.client.gui.hud.AlternativeHUD;
import com.dragonminez.client.gui.hud.BeamClashOverlay;
import com.dragonminez.client.gui.hud.ScouterHUD;
import com.dragonminez.client.gui.hud.TechniqueChargeOverlay;
import com.dragonminez.client.gui.hud.TechniqueHotbarHUD;
import com.dragonminez.client.gui.hud.TrackedQuestHUD;
import com.dragonminez.client.gui.hud.XenoverseHUD;
import com.dragonminez.client.gui.tooltip.CustomTooltipNodes;
import com.dragonminez.client.gui.tooltip.CustomTooltipRenderers;
import com.dragonminez.client.init.blocks.renderer.DragonBallBlockRenderer;
import com.dragonminez.client.init.blocks.renderer.EnergyCableBlockRenderer;
import com.dragonminez.client.init.blocks.renderer.FuelGeneratorBlockRenderer;
import com.dragonminez.client.init.blocks.renderer.GravityDeviceBlockRenderer;
import com.dragonminez.client.init.blocks.renderer.KikonoStationBlockRenderer;
import com.dragonminez.client.init.entities.renderer.DinoFlyRenderer;
import com.dragonminez.client.init.entities.renderer.DinosRenderer;
import com.dragonminez.client.init.entities.renderer.DragonDBRenderer;
import com.dragonminez.client.init.entities.renderer.GranDinoRenderer;
import com.dragonminez.client.init.entities.renderer.MasterEntityRenderer;
import com.dragonminez.client.init.entities.renderer.PunchMachineRenderer;
import com.dragonminez.client.init.entities.renderer.QuestNPCRenderer;
import com.dragonminez.client.init.entities.renderer.SpacePodRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiBarrierRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiDiskRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiExplosionRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiExplosionVisualRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiLaserRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiProjectileRenderer;
import com.dragonminez.client.init.entities.renderer.ki.KiWaveRenderer;
import com.dragonminez.client.init.entities.renderer.ki.MajinSkillRenderer;
import com.dragonminez.client.init.entities.renderer.ki.SPBlueHurricaneRenderer;
import com.dragonminez.client.init.entities.renderer.ki.SPDragonFistRenderer;
import com.dragonminez.client.init.entities.renderer.ki.SPMajinCandyRenderer;
import com.dragonminez.client.init.entities.renderer.ki.SPOzaruFistRenderer;
import com.dragonminez.client.init.entities.renderer.rr.RedRibbonRenderer;
import com.dragonminez.client.init.entities.renderer.rr.RedRibbonSoldierRenderer;
import com.dragonminez.client.init.entities.renderer.rr.RobotRRRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.BlackNimbusRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.DBSagasRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.FlyingNimbusRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.NamekFrogRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.NamekianRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.NamekianWarriorRenderer;
import com.dragonminez.client.init.entities.renderer.sagas.SagaSaibamanRenderer;
import com.dragonminez.client.init.menu.screens.FuelGeneratorScreen;
import com.dragonminez.client.init.menu.screens.GravityDeviceScreen;
import com.dragonminez.client.init.menu.screens.KikonoStationScreen;
import com.dragonminez.client.util.ArmorTextureResolver;
import com.dragonminez.client.util.KeyBinds;
import com.dragonminez.client.util.SkinCacheManager;
import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.init.MainBlockEntities;
import com.dragonminez.common.init.MainBlocks;
import com.dragonminez.common.init.MainEntities;
import com.dragonminez.common.init.MainFluids;
import com.dragonminez.common.init.MainMenus;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.armor.client.model.ArmorBaseModel;
import com.dragonminez.common.init.entities.dragon.DragonWishEntity;
import com.dragonminez.common.init.particles.AuraParticle;
import com.dragonminez.common.init.particles.BlockParticle;
import com.dragonminez.common.init.particles.DivineParticle;
import com.dragonminez.common.init.particles.DustParticle;
import com.dragonminez.common.init.particles.GuardBlockParticle;
import com.dragonminez.common.init.particles.KiExplosionFlashParticle;
import com.dragonminez.common.init.particles.KiExplosionParticle;
import com.dragonminez.common.init.particles.KiExplosionSplashParticle;
import com.dragonminez.common.init.particles.KiFlashParticle;
import com.dragonminez.common.init.particles.KiLightningParticle;
import com.dragonminez.common.init.particles.KiSheddingParticle;
import com.dragonminez.common.init.particles.KiSplashParticle;
import com.dragonminez.common.init.particles.KiSplashWaveParticle;
import com.dragonminez.common.init.particles.KiTrailParticle;
import com.dragonminez.common.init.particles.KintonParticle;
import com.dragonminez.common.init.particles.PunchParticle;
import com.dragonminez.common.init.particles.RockParticle;
import com.dragonminez.common.util.BetaWhitelist;
import com.dragonminez.server.world.dimension.CustomSpecialEffects;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.Pack.Metadata;
import net.minecraft.server.packs.repository.Pack.Position;
import net.minecraft.server.packs.repository.Pack.ResourcesSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.GrassColor;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;
import org.lwjgl.glfw.GLFWImage.Buffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.MOD,
   value = {Dist.CLIENT}
)
public class ModClientEvents {
   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiLayersEvent e) {
      e.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "xenoversehud"), XenoverseHUD.HUD_XENOVERSE);
      e.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "alternativehud"), AlternativeHUD.HUD_ALTERNATIVE);
      e.registerAbove(
         VanillaGuiLayers.PLAYER_HEALTH,
         ResourceLocation.fromNamespaceAndPath("dragonminez", "technique_charge_hud"),
         TechniqueChargeOverlay.HUD_TECHNIQUE_CHARGE
      );
      e.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "scouterhud"), ScouterHUD.HUD_SCOUTER);
      e.registerAbove(
         VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "tracked_quest_hud"), TrackedQuestHUD.HUD_TRACKED_QUEST
      );
      e.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "techniquehud"), TechniqueHotbarHUD.HUD_TECHNIQUES);
      e.registerAbove(VanillaGuiLayers.PLAYER_HEALTH, ResourceLocation.fromNamespaceAndPath("dragonminez", "beam_clash_hud"), BeamClashOverlay.HUD_BEAM_CLASH);
      e.registerAbove(VanillaGuiLayers.CROSSHAIR, ResourceLocation.fromNamespaceAndPath("dragonminez", "lock_on_hud"), LockOnEvent.HUD_LOCK_ON);
   }

   @SubscribeEvent
   public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
      event.registerReloadListener(new SimplePreparableReloadListener<Void>() {
         protected Void prepare(ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            return null;
         }

         protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profilerFiller) {
            TextureCounter.clearCache();
            ArmorTextureResolver.clearCache();
            CombatAnimationResolver.reload(resourceManager);
            SkinCacheManager.revalidate();
         }
      });
   }

   @SubscribeEvent
   public static void onKeyRegister(RegisterKeyMappingsEvent event) {
      KeyBinds.registerAll(event);
   }

   @SubscribeEvent
   public static void onAddPackFinders(AddPackFindersEvent event) {
      if (event.getPackType() == PackType.CLIENT_RESOURCES) {
         if (CrowdinManager.isLiveTranslationsEnabled()) {
            String currentLang = Minecraft.getInstance().options.languageCode;
            CrowdinManager.fetchLanguage(currentLang);
         }

         event.addRepositorySource(
            packConsumer -> {
               PackLocationInfo crowdinInfo = new PackLocationInfo(
                  "dmz_crowdin_ota", Component.literal("DMZ Live Translations"), PackSource.BUILT_IN, Optional.empty()
               );
               ResourcesSupplier crowdinSupplier = new ResourcesSupplier() {
                  public PackResources openPrimary(PackLocationInfo location) {
                     return new CrowdinPackResources(location);
                  }

                  public PackResources openFull(PackLocationInfo location, Metadata metadata) {
                     return this.openPrimary(location);
                  }
               };
               Pack crowdinPack = Pack.readMetaAndCreate(
                  crowdinInfo, crowdinSupplier, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Position.TOP, false)
               );
               if (crowdinPack != null) {
                  packConsumer.accept(crowdinPack);
               }

               PackLocationInfo dragonballInfo = new PackLocationInfo(
                  "dmz_dragonballs_runtime", Component.literal("DMZ Dragonballs Runtime Resources"), PackSource.BUILT_IN, Optional.empty()
               );
               ResourcesSupplier dragonballSupplier = new ResourcesSupplier() {
                  public PackResources openPrimary(PackLocationInfo location) {
                     return new DragonBallPackResources(location);
                  }

                  public PackResources openFull(PackLocationInfo location, Metadata metadata) {
                     return this.openPrimary(location);
                  }
               };
               Pack dragonBallRuntimePack = Pack.readMetaAndCreate(
                  dragonballInfo, dragonballSupplier, PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Position.TOP, false)
               );
               if (dragonBallRuntimePack != null) {
                  packConsumer.accept(dragonBallRuntimePack);
               }
            }
         );
      }
   }

   @SubscribeEvent
   public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
      event.register((MenuType)MainMenus.KIKONO_STATION_MENU.get(), KikonoStationScreen::new);
      event.register((MenuType)MainMenus.FUEL_GENERATOR_MENU.get(), FuelGeneratorScreen::new);
      event.register((MenuType)MainMenus.GRAVITY_DEVICE_MENU.get(), GravityDeviceScreen::new);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(() -> {
         setCustomWindowIcon();
         BetaWhitelist.reload();
         SkinCacheManager.init();
         BlockEntityRenderers.register((BlockEntityType)MainBlockEntities.DRAGON_BALL_BLOCK_ENTITY.get(), DragonBallBlockRenderer::new);
         BlockEntityRenderers.register((BlockEntityType)MainBlockEntities.ENERGY_CABLE_BE.get(), EnergyCableBlockRenderer::new);
         BlockEntityRenderers.register((BlockEntityType)MainBlockEntities.KIKONO_STATION_BE.get(), KikonoStationBlockRenderer::new);
         BlockEntityRenderers.register((BlockEntityType)MainBlockEntities.FUEL_GENERATOR_BE.get(), FuelGeneratorBlockRenderer::new);
         BlockEntityRenderers.register((BlockEntityType)MainBlockEntities.GRAVITY_DEVICE_BE.get(), GravityDeviceBlockRenderer::new);
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_AJISSA_LOG.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_STRIPPED_AJISSA_LOG.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_SACRED_LOG.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_STRIPPED_SACRED_LOG.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.INVISIBLE_LADDER_BLOCK.get(), RenderType.translucent());
         ItemBlockRenderTypes.setRenderLayer((Fluid)MainFluids.SOURCE_NAMEK.get(), RenderType.translucent());
         ItemBlockRenderTypes.setRenderLayer((Fluid)MainFluids.FLOWING_NAMEK.get(), RenderType.translucent());
         ItemBlockRenderTypes.setRenderLayer((Fluid)MainFluids.FLOWING_HEALING.get(), RenderType.translucent());
         ItemBlockRenderTypes.setRenderLayer((Fluid)MainFluids.SOURCE_HEALING.get(), RenderType.translucent());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.CHRYSANTHEMUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.AMARYLLIS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.MARIGOLD_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.CATHARANTHUS_ROSEUS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.TRILLIUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.LOTUS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_FERN.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_CHRYSANTHEMUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_AMARYLLIS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_MARIGOLD_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_CATHARANTHUS_ROSEUS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_TRILLIUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.SACRED_FERN.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_AJISSA_SAPLING.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.NAMEK_SACRED_SAPLING.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_CHRYSANTHEMUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_AMARYLLIS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_MARIGOLD_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_CATHARANTHUS_ROSEUS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_TRILLIUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_NAMEK_FERN.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_CHRYSANTHEMUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_AMARYLLIS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_MARIGOLD_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_CATHARANTHUS_ROSEUS_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_TRILLIUM_FLOWER.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_FERN.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_AJISSA_SAPLING.get(), RenderType.cutout());
         ItemBlockRenderTypes.setRenderLayer((Block)MainBlocks.POTTED_SACRED_SAPLING.get(), RenderType.cutout());
         ItemProperties.registerGeneric(ResourceLocation.fromNamespaceAndPath("dragonminez", "loaded"), (stack, level, entity, seed) -> 1.0F);
      });
      UtilityMenuScreen.initMenuSlots();
      Minecraft.getInstance().getMainRenderTarget().enableStencil();
   }

   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      for (DeferredHolder<EntityType<?>, ? extends EntityType<?>> masterEntity : MainEntities.getMasterEntities()) {
         event.registerEntityRenderer((EntityType)masterEntity.get(), context -> new MasterEntityRenderer(context));
      }

      event.registerEntityRenderer((EntityType)MainEntities.QUEST_NPC.get(), QuestNPCRenderer::new);

      for (DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> sagaEntity : MainEntities.getSagaEntities()) {
         event.registerEntityRenderer((EntityType)sagaEntity.get(), context -> new DBSagasRenderer(context));
      }

      regRender(
         event,
         SagaSaibamanRenderer::new,
         MainEntities.SAGA_SAIBAMAN,
         MainEntities.SAGA_SAIBAMAN2,
         MainEntities.SAGA_SAIBAMAN3,
         MainEntities.SAGA_SAIBAMAN4,
         MainEntities.SAGA_SAIBAMAN5,
         MainEntities.SAGA_SAIBAMAN6
      );
      event.registerEntityRenderer((EntityType)MainEntities.DINOSAUR1.get(), DinosRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.DINOSAUR2.get(), GranDinoRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.DINOSAUR3.get(), DinoFlyRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.DINO_KID.get(), DinosRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.NAMEK_FROG.get(), NamekFrogRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.NAMEK_FROG_GINYU.get(), NamekFrogRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.NAMEK_TRADER.get(), NamekianRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.CC_NAMEKIAN.get(), NamekianRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.NAMEK_WARRIOR.get(), NamekianWarriorRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SABERTOOTH.get(), DinosRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.BANDIT.get(), RedRibbonRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.RED_RIBBON_ROBOT1.get(), RobotRRRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.RED_RIBBON_ROBOT2.get(), RobotRRRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.RED_RIBBON_ROBOT3.get(), RobotRRRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.RED_RIBBON_SOLDIER.get(), RedRibbonSoldierRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SPACE_POD.get(), SpacePodRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.FLYING_NIMBUS.get(), FlyingNimbusRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.BLACK_NIMBUS.get(), BlackNimbusRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.ROBOT_XENOVERSE.get(), RedRibbonRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.PUNCH_MACHINE.get(), PunchMachineRenderer::new);

      for (DeferredHolder<EntityType<?>, ? extends EntityType<DragonWishEntity>> entity : MainEntities.getDragonWishEntities().values()) {
         event.registerEntityRenderer((EntityType)entity.get(), DragonDBRenderer::new);
      }

      event.registerEntityRenderer((EntityType)MainEntities.KI_BLAST.get(), KiProjectileRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_EXPLOSION.get(), KiExplosionRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SP_BLUE_HURRICANE.get(), SPBlueHurricaneRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SP_DRAGON_FIST.get(), SPDragonFistRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SP_OZARU_FIST.get(), SPOzaruFistRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.SP_MAJIN_CANDY.get(), SPMajinCandyRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_LASER.get(), KiLaserRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_WAVE.get(), KiWaveRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.MAJIN_SKILL.get(), MajinSkillRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_DISC.get(), KiDiskRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_BARRIER.get(), KiBarrierRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_EXPLOSION_VISUAL.get(), KiExplosionVisualRenderer::new);
      event.registerEntityRenderer((EntityType)MainEntities.KI_AREA.get(), KiProjectileRenderer::new);
   }

   @SubscribeEvent
   public static void registerModelLayers(RegisterLayerDefinitions e) {
      e.registerLayerDefinition(ArmorBaseModel.LAYER_LOCATION, ArmorBaseModel::createBodyLayer);
   }

   @SubscribeEvent
   public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
      event.registerSpriteSet((ParticleType)MainParticles.KI_FLASH.get(), KiFlashParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_SPLASH.get(), KiSplashParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_SPLASH_WAVE.get(), KiSplashWaveParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_TRAIL.get(), KiTrailParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_SHEDDING.get(), KiSheddingParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_LIGHTNING.get(), KiLightningParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_EXPLOSION_FLASH.get(), KiExplosionFlashParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_EXPLOSION_SPLASH.get(), KiExplosionSplashParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KI_EXPLOSION.get(), KiExplosionParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.KINTON.get(), KintonParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.PUNCH_PARTICLE.get(), PunchParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.BLOCK_PARTICLE.get(), BlockParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.GUARD_BLOCK.get(), GuardBlockParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.SPARKS.get(), KiSplashParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.AURA.get(), AuraParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.DUST.get(), DustParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.ROCK.get(), RockParticle.Provider::new);
      event.registerSpriteSet((ParticleType)MainParticles.DIVINE.get(), DivineParticle.Provider::new);
   }

   @SafeVarargs
   private static void regRender(RegisterRenderers event, EntityRendererProvider provider, DeferredHolder... entities) {
      for (DeferredHolder reg : entities) {
         event.registerEntityRenderer((EntityType)reg.get(), provider);
      }
   }

   @SubscribeEvent
   public static void registerDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
      CustomSpecialEffects.registerSpecialEffects(event);
   }

   @SubscribeEvent
   public static void registerBlockColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Block event) {
      event.register(
         (state, level, pos, tintIndex) -> level != null && pos != null ? BiomeColors.getAverageGrassColor(level, pos) : GrassColor.getDefaultColor(),
         new Block[]{(Block)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get()}
      );
   }

   @SubscribeEvent
   public static void registerItemColors(Item event) {
      event.register((stack, tintIndex) -> GrassColor.get(0.5, 1.0), new ItemLike[]{(ItemLike)MainBlocks.SACRED_PLANET_GRASS_BLOCK.get()});
   }

   private static void setCustomWindowIcon() {
      Minecraft mc = Minecraft.getInstance();
      if (Minecraft.ON_OSX) {
         try {
            ResourceLocation macLoc = ResourceLocation.fromNamespaceAndPath("dragonminez", "icons/minecraft.icns");
            Optional<Resource> res = mc.getResourceManager().getResource(macLoc);
            if (res.isPresent()) {
               MacosUtil.loadIcon(res.get()::open);
            }
         } catch (Exception var23) {
         }
      } else {
         long windowId = mc.getWindow().getWindow();
         String[] iconNames = new String[]{"icon_16x16.png", "icon_32x32.png", "icon_48x48.png", "icon_128x128.png", "icon_256x256.png"};
         List<NativeImage> loadedImages = new ArrayList<>();

         for (String name : iconNames) {
            try {
               ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("dragonminez", "icons/" + name);
               Optional<Resource> resource = mc.getResourceManager().getResource(loc);
               if (resource.isPresent()) {
                  try (InputStream is = resource.get().open()) {
                     loadedImages.add(NativeImage.read(is));
                  }
               }
            } catch (Exception var28) {
            }
         }

         if (!loadedImages.isEmpty()) {
            List<ByteBuffer> buffersToFree = new ArrayList<>();

            try {
               MemoryStack stack = MemoryStack.stackPush();

               try {
                  Buffer glfwImages = GLFWImage.malloc(loadedImages.size(), stack);

                  for (int i = 0; i < loadedImages.size(); i++) {
                     NativeImage image = loadedImages.get(i);
                     ByteBuffer byteBuffer = MemoryUtil.memAlloc(image.getWidth() * image.getHeight() * 4);
                     buffersToFree.add(byteBuffer);
                     byteBuffer.asIntBuffer().put(image.getPixelsRGBA());
                     glfwImages.position(i);
                     glfwImages.width(image.getWidth());
                     glfwImages.height(image.getHeight());
                     glfwImages.pixels(byteBuffer);
                  }

                  GLFW.glfwSetWindowIcon(windowId, (Buffer)glfwImages.position(0));
               } catch (Throwable var25) {
                  if (stack != null) {
                     try {
                        stack.close();
                     } catch (Throwable var22) {
                        var25.addSuppressed(var22);
                     }
                  }

                  throw var25;
               }

               if (stack != null) {
                  stack.close();
               }
            } finally {
               buffersToFree.forEach(MemoryUtil::memFree);
               loadedImages.forEach(NativeImage::close);
            }
         }
      }
   }

   @SubscribeEvent
   public static void registerTooltipComponents(RegisterClientTooltipComponentFactoriesEvent event) {
      event.register(CustomTooltipNodes.HeaderNode.class, CustomTooltipRenderers.HeaderRenderer::new);
      event.register(CustomTooltipNodes.PaddingNode.class, CustomTooltipRenderers.PaddingRenderer::new);
      event.register(CustomTooltipNodes.SeparatorNode.class, CustomTooltipRenderers.SeparatorRenderer::new);
   }
}
