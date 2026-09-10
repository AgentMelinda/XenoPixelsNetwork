package com.dragonminez.common.events;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.util.ColorUtils;
import com.dragonminez.common.alignment.NpcAlignmentRules;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.combat.logic.weapon.WeaponRegistry;
import com.dragonminez.common.combat.util.Player_DMZ;
import com.dragonminez.common.combat.weapon.WeaponAttributes;
import com.dragonminez.common.compat.WorldGuardCompat;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.dragonball.DragonDefinitionReloadListener;
import com.dragonminez.common.init.CapsuleCorpMapTrade;
import com.dragonminez.common.init.MainAttributes;
import com.dragonminez.common.init.MainEnchants;
import com.dragonminez.common.init.MainItems;
import com.dragonminez.common.init.MainParticles;
import com.dragonminez.common.init.MainSounds;
import com.dragonminez.common.init.MainVillagers;
import com.dragonminez.common.init.armor.DbzArmorTextured;
import com.dragonminez.common.init.entities.MastersEntity;
import com.dragonminez.common.init.entities.PunchMachineEntity;
import com.dragonminez.common.init.entities.ki.KiBarrierEntity;
import com.dragonminez.common.init.entities.sagas.DBSagasEntity;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.PacketRateLimiter;
import com.dragonminez.common.network.TrainingSessionTracker;
import com.dragonminez.common.network.S2C.AppearanceSyncS2C;
import com.dragonminez.common.network.S2C.SyncWeaponRegistryS2C;
import com.dragonminez.common.network.S2C.SyncWishesS2C;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.spacepod.SpacePodDestinationRegistry;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.AttributeMods;
import com.dragonminez.common.util.BetaWhitelist;
import com.dragonminez.common.wish.DragonWishRegistry;
import com.dragonminez.common.wish.WishManager;
import com.dragonminez.server.DMZServer;
import com.dragonminez.server.commands.DMZPermissions;
import com.dragonminez.server.commands.LocateCommand;
import com.dragonminez.server.events.DragonBallsHandler;
import com.dragonminez.server.storage.StorageManager;
import com.dragonminez.server.util.FusionLogic;
import com.dragonminez.server.world.data.DragonBallSavedData;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.dragonminez.server.world.dimension.OtherworldDimension;
import com.dragonminez.server.world.dimension.OtherworldRegionLoader;
import com.dragonminez.server.world.npc.NPCPlacementManager;
import com.dragonminez.server.world.structure.helper.DMZStructures;
import com.dragonminez.server.world.structure.helper.StructureLocator;
import com.dragonminez.server.world.structure.helper.VillagePoolInjector;
import com.dragonminez.server.world.structure.placement.StructureRepairManager;
import com.dragonminez.server.world.structure.placement.StructureSpawnPlanner;
import com.google.gson.Gson;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.ParsedCommandNode;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.PlayLevelSoundEvent.AtPosition;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Pre;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent.Hands;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent.PositionCheck.Result;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.NameFormat;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangeGameModeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.LevelEvent.Load;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

@EventBusSubscriber(
   modid = "dragonminez",
   bus = Bus.GAME
)
public class ForgeCommonEvents {
   private static final int MAP_XP = 40;
   private static final int MAP_MAX_USES = 8;
   private static final UUID WEAPON_CRIT_CHANCE_UUID = UUID.fromString("c37e2055-7783-4559-b18a-1b3e6f5a0410");
   private static final UUID WEAPON_CRIT_DAMAGE_UUID = UUID.fromString("496f2714-a61f-41e2-aed5-265e7fa0e0fa");

   @SubscribeEvent
   public static void onVillagerTrades(VillagerTradesEvent event) {
      if (event.getType().equals(MainVillagers.CAPSULE_CORP_ASSISTANT.get())) {
         Int2ObjectMap<List<ItemListing>> trades = event.getTrades();
         ((List)trades.get(1)).add(mapTrade(DMZStructures.GOKU_HOUSE, "dragonminez.goku_house", new ItemStack((ItemLike)MainItems.DINO_MEAT_COOKED.get(), 10)));
         ((List)trades.get(1)).add(mapTrade(DMZStructures.ROSHI_HOUSE, "dragonminez.roshi_house", new ItemStack(Items.COD, 8)));
         ((List)trades.get(2)).add(mapTrade(DMZStructures.PICCOLO_HOUSE, "dragonminez.piccolo_house", new ItemStack(Items.WATER_BUCKET, 1)));
         ((List)trades.get(2)).add(mapTrade(DMZStructures.YAMCHA_HOUSE, "dragonminez.yamcha_house", new ItemStack(Items.BONE, 1)));
         ((List)trades.get(3)).add(mapTrade(DMZStructures.KAMILOOKOUT, "dragonminez.kamilookout", new ItemStack(Items.SPRUCE_SAPLING, 1)));
         ((List)trades.get(3)).add(mapTrade(DMZStructures.CELL_ARENA, "dragonminez.cell_arena", new ItemStack((ItemLike)MainItems.T1_RADAR_CHIP.get(), 1)));
         ((List)trades.get(4)).add(mapTrade(DMZStructures.GERO_LAB, "dragonminez.gero_lab", new ItemStack(Items.IRON_INGOT, 16)));
         ((List)trades.get(4)).add(mapTrade(DMZStructures.TRUNKS_SHIP, "dragonminez.trunks_ship", new ItemStack(Items.IRON_SWORD, 1)));
         ((List)trades.get(5)).add(mapTrade(DMZStructures.VEGETA_POD, "dragonminez.vegeta_pod", new ItemStack((ItemLike)MainItems.RED_SCOUTER.get(), 1)));
         ((List)trades.get(5)).add(mapTrade(DMZStructures.BABIDI, "dragonminez.babidi", new ItemStack(Items.ENDER_PEARL, 4)));
      }
   }

   @SubscribeEvent
   public static void onMerchantInteract(EntityInteract event) {
      if (!event.getLevel().isClientSide()) {
         if (event.getLevel() instanceof ServerLevel serverLevel) {
            if (event.getTarget() instanceof AbstractVillager merchant) {
               try {
                  CapsuleCorpMapTrade.refreshStaleMapOffers(serverLevel, merchant);
               } catch (Exception var4) {
               }
            }
         }
      }
   }

   private static ItemStack emptyMap() {
      return new ItemStack(Items.MAP, 1);
   }

   private static ItemListing mapTrade(ResourceKey<Structure> destination, String displayName, ItemStack cost) {
      return new CapsuleCorpMapTrade(emptyMap(), cost, destination, displayName, MapDecorationTypes.RED_X, 8, 40);
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      String version = "2.1.3";
      if (version.contains("-beta") || version.contains("-alpha")) {
         Player player = event.getEntity();
         String username = player.getGameProfile().getName();
         if (!BetaWhitelist.isAllowed(username)) {
            LogUtil.error(Env.SERVER, "User {} tried to join but is not in the beta whitelist.", username);
            if (!(player instanceof ServerPlayer serverPlayer)) {
               throw new IllegalStateException("DMZ: User not allowed.");
            }

            serverPlayer.connection
               .disconnect(
                  Component.literal(
                     "§c[DragonMine Z]\n\n§7You are not allowed to play this Beta/Alpha version.\n§fAre you a §cPatreon§f? Use the §cVerify here§f button to connect Discord and request access automatically.\n\n§7If you have been recently whitelisted, restart Minecraft to apply the changes!\n§7Your Minecraft nickname is: §f"
                        + username
                  )
               );
         }
      }

      if (event.getEntity() instanceof ServerPlayer player) {
         NetworkHandler.sendToPlayer(new SyncWishesS2C(WishManager.getAllWishes()), player);
         DragonBallsHandler.syncRadar(player.serverLevel());
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (ConfigManager.getCombatConfig().getKillPlayersOnCombatLogout() && data.getCooldowns().hasCooldown("CombatTimer")) {
               player.kill();
            }

            endFusionIfNeeded(player);
         });
         Map<String, WeaponAttributes> stringMap = new HashMap<>();
         WeaponRegistry.registrations.forEach((k, v) -> stringMap.put(k.toString(), v));
         String jsonRegistry = new Gson().toJson(stringMap);
         NetworkHandler.sendToPlayer(new SyncWeaponRegistryS2C(jsonRegistry), player);
         SpacePodDestinationRegistry.syncToPlayer(player);
      }
   }

   @SubscribeEvent
   public static void onPlayerLogout(PlayerLoggedOutEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         TrainingSessionTracker.end(player.getUUID());
         PacketRateLimiter.clear(player.getUUID());
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (ConfigManager.getCombatConfig().getKillPlayersOnCombatLogout() && data.getCooldowns().hasCooldown("CombatTimer")) {
               player.kill();
            }

            endFusionIfNeeded(player);
         });
      }
   }

   @SubscribeEvent
   public static void onPlayerDeath(LivingDeathEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DragonBallsHandler.syncRadar(player.serverLevel());
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            endFusionIfNeeded(player);
            if (ConfigManager.getServerConfig().getWorldGen().getOtherworldActive()) {
               if (data.getStatus().isAlive()) {
                  data.getCooldowns().addCooldown("Revive", ConfigManager.getServerConfig().getGameplay().getReviveCooldownSeconds() * 20);
               }

               if (data.getStatus().isHasCreatedCharacter()) {
                  data.getStatus().setAlive(false);
               }

               if (!data.getStatus().isInKaioPlanet()) {
                  data.getStatus().setInKaioPlanet(true);
               }
            }

            if (data.getSkills().hasSkill("kaioken") && data.getSkills().isSkillActive("kaioken")) {
               data.getSkills().setSkillActive("kaioken", false);
            }

            data.getCooldowns().removeCooldown("CombatTimer");
            data.getCharacter().clearActiveForm(player);
            data.getCharacter().clearActiveStackForm(player);
            data.getEffects().removeAllEffects();
            data.getSecondaryStatEffects().clear();
            player.refreshDimensions();
         });
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         player.refreshDimensions();
      }
   }

   @SubscribeEvent
   public static void onGamemodeChange(PlayerChangeGameModeEvent event) {
      if (event.getEntity() instanceof ServerPlayer player && event.getNewGameMode() != GameType.SPECTATOR) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> endFusionIfNeeded(player));
      }
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      if (event.getPlayer() instanceof ServerPlayer player) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (data.getStatus().isBlocking()) {
               event.setCanceled(true);
            }
         });
      }
   }

   public static double getCriticalChance(Player player) {
      double chance = player.getAttributeValue(MainAttributes.CRIT_CHANCE);
      int chanceLevel = MainEnchants.level(player.getMainHandItem(), player.level(), MainEnchants.CRIT_CHANCE);
      if (chanceLevel > 0) {
         chance += (double)chanceLevel * 0.05;
      }

      DMZEvent.CritChanceEvent event = new DMZEvent.CritChanceEvent(player, chance);
      NeoForge.EVENT_BUS.post(event);
      return event.getChance();
   }

   public static double getCriticalDamage(Player player) {
      double multiplier = player.getAttributeValue(MainAttributes.CRIT_DAMAGE);
      int damageLevel = MainEnchants.level(player.getMainHandItem(), player.level(), MainEnchants.CRIT_DAMAGE);
      if (damageLevel > 0) {
         multiplier += (double)damageLevel * 0.05;
      }

      return multiplier;
   }

   @SubscribeEvent
   public static void onPlayerAttack(AttackEntityEvent event) {
      Entity target = event.getTarget();
      Player attacker = event.getEntity();
      Level level = attacker.level();
      if (!level.isClientSide() && level instanceof ServerLevel serverLevel) {
         TargetHelper.Relation relation = TargetHelper.getRelation(attacker, target);
         if (!TargetHelper.canAttack(attacker, target, (double)attacker.distanceTo(target) + 1.0)) {
            event.setCanceled(true);
            return;
         }

         TargetHelper.onSuccessfulAttack(attacker, target, relation);
         double chance = getCriticalChance(attacker);
         boolean isCrit = ((Player_DMZ)attacker).rollAndGetCriticalStatus(chance);
         boolean isValidTarget = false;
         if (target instanceof ServerPlayer targetPlayer) {
            isValidTarget = StatsProvider.get(StatsCapability.INSTANCE, targetPlayer)
               .map(targetData -> !targetData.getStatus().isBlocking() && !targetPlayer.isCreative())
               .orElse(false);
         } else if (!(target instanceof MastersEntity) && !target.isInvulnerable() && !(target instanceof PunchMachineEntity)) {
            isValidTarget = true;
         }

         if (isValidTarget) {
            double x = target.getX();
            double y = target.getY() + (double)target.getBbHeight() * 0.65;
            double z = target.getZ();
            float[] rgb = ColorUtils.rgbIntToFloat(16777215);
            if (!isCrit) {
               serverLevel.sendParticles(
                  (SimpleParticleType)MainParticles.PUNCH_PARTICLE.get(), x, y, z, 0, (double)rgb[0], (double)rgb[1], (double)rgb[2], 1.0
               );
            }
         }

         if (isCrit) {
            DeferredHolder<SoundEvent, ? extends SoundEvent>[] sonidosCritico = new DeferredHolder[]{MainSounds.CRITICO1, MainSounds.CRITICO2};
            int indiceRandom = level.random.nextInt(sonidosCritico.length);
            SoundEvent sonidoCritico = (SoundEvent)sonidosCritico[indiceRandom].get();
            attacker.playNotifySound(sonidoCritico, SoundSource.PLAYERS, 1.0F, 1.0F);
         } else if (attacker.getMainHandItem() == ItemStack.EMPTY || attacker.getMainHandItem().is(Items.AIR)) {
            DeferredHolder<SoundEvent, ? extends SoundEvent>[] sonidosGolpe = new DeferredHolder[]{
               MainSounds.GOLPE1, MainSounds.GOLPE2, MainSounds.GOLPE3, MainSounds.GOLPE4, MainSounds.GOLPE5, MainSounds.GOLPE6
            };
            int indiceRandom = level.random.nextInt(sonidosGolpe.length);
            SoundEvent sonidoElegido = (SoundEvent)sonidosGolpe[indiceRandom].get();
            attacker.playNotifySound(sonidoElegido, SoundSource.PLAYERS, 1.0F, 1.0F);
         }
      }
   }

   @SubscribeEvent
   public static void onMeleeCriticalHit(CriticalHitEvent event) {
      Player player = event.getEntity();
      if (player != null && !player.level().isClientSide) {
         double chance = getCriticalChance(player);
         boolean isCrit = ((Player_DMZ)player).rollAndGetCriticalStatus(chance);
         if (isCrit) {
            event.setDamageMultiplier((float)getCriticalDamage(player));
            event.setCriticalHit(true);
         } else {
            event.setCriticalHit(false);
         }
      }
   }

   @SubscribeEvent
   public static void onRangedCriticalHit(Pre event) {
      if (event.getSource().getDirectEntity() instanceof AbstractArrow && event.getSource().getEntity() instanceof Player player) {
         if (player.level().isClientSide) {
            return;
         }

         double chance = getCriticalChance(player);
         boolean isCrit = ((Player_DMZ)player).rollAndGetCriticalStatus(chance);
         if (isCrit) {
            event.setNewDamage((float)((double)event.getNewDamage() * getCriticalDamage(player)));
         }
      }
   }

   @SubscribeEvent
   public static void suppressVanillaCritSounds(AtPosition event) {
      if (event.getSound() == SoundEvents.PLAYER_ATTACK_CRIT) {
         event.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void attachDynamicWeaponAttributes(ItemAttributeModifierEvent event) {
      ItemStack stack = event.getItemStack();
      WeaponAttributes attributes = WeaponRegistry.getAttributes(stack);
      if (attributes != null) {
         double weaponCritChance = attributes.getSafeCritChance();
         double weaponCritDamage = attributes.getSafeCritDamage();
         if (weaponCritChance > 0.0) {
            event.addModifier(
               MainAttributes.CRIT_CHANCE,
               AttributeMods.of(WEAPON_CRIT_CHANCE_UUID, "Weapon innate crit chance", weaponCritChance, Operation.ADD_VALUE),
               EquipmentSlotGroup.MAINHAND
            );
         }

         if (weaponCritDamage > 0.0) {
            event.addModifier(
               MainAttributes.CRIT_DAMAGE,
               AttributeMods.of(WEAPON_CRIT_DAMAGE_UUID, "Weapon innate crit damage", weaponCritDamage, Operation.ADD_VALUE),
               EquipmentSlotGroup.MAINHAND
            );
         }
      }
   }

   private static String fusionDisplayName(Player player) {
      return StatsProvider.get(StatsCapability.INSTANCE, player)
         .map(data -> data.getStatus().isFused() ? data.getStatus().getFusionName() : "")
         .filter(name -> name != null && !name.isEmpty())
         .orElse(null);
   }

   @SubscribeEvent
   public static void onPlayerNameFormat(NameFormat event) {
      String fusionName = fusionDisplayName(event.getEntity());
      if (fusionName != null) {
         event.setDisplayname(Component.literal(fusionName));
      }
   }

   @SubscribeEvent
   public static void onPlayerTabListNameFormat(TabListNameFormat event) {
      String fusionName = fusionDisplayName(event.getEntity());
      if (fusionName != null) {
         event.setDisplayName(Component.literal(fusionName));
      }
   }

   @SubscribeEvent
   public static void onPlayerChangeDimension(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         DragonBallsHandler.syncRadarForPlayer(player);
      }
   }

   @SubscribeEvent
   public static void onServerAboutToStart(ServerAboutToStartEvent event) {
      if (ConfigManager.getServerConfig().getWorldGen().getOtherworldActive()) {
         OtherworldRegionLoader.loadPreGeneratedRegions(event.getServer());
      }

      VillagePoolInjector.injectAll(event.getServer());
   }

   @SubscribeEvent
   public static void onServerStarting(ServerStartingEvent event) {
      StorageManager.init();
      BetaWhitelist.reload();
      WishManager.loadWishes(event.getServer());
      DMZPermissions.init();
      QuestRegistry.loadAll(event.getServer());
      NpcAlignmentRules.load(event.getServer());
      NPCPlacementManager.load(event.getServer());
      WorldGuardCompat.init();
      if (ConfigManager.getServerConfig().getWorldGen().getGenerateDragonBalls()) {
         for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSets()) {
            ServerLevel targetLevel = event.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, definition.getValidDimensions().iterator().next()));
            if (targetLevel != null) {
               DragonBallSavedData data = DragonBallSavedData.get(targetLevel);
               if (!data.isFirstSpawnComplete(definition.getId())) {
                  DragonBallsHandler.scatterDragonBalls(targetLevel, definition.getId());
                  LogUtil.info(Env.COMMON, "First DragonBalls Spawn setup for set: " + definition.getId());
               }
            }
         }
      } else {
         LogUtil.info(Env.COMMON, "DragonBalls generation is disabled in the config.");
      }

      JsonLoadReport.logConsoleReport();
   }

   @SubscribeEvent
   public static void onServerStarted(ServerStartedEvent event) {
      StructureSpawnPlanner.precomputeAndWait(event.getServer());
      ServerLevel otherworld = event.getServer().getLevel(OtherworldDimension.OTHERWORLD_KEY);
      if (otherworld != null) {
         LogUtil.info(Env.SERVER, "ServerStartedEvent: Attempting to load Otherworld regions, double-checking dimension presence.");
         OtherworldRegionLoader.loadPreGeneratedRegions(event.getServer());
      }

      NPCPlacementManager.spawnForLoadedLevels(event.getServer());
   }

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
         try {
            StructureRepairManager.tick(serverLevel);
         } catch (Throwable var3) {
         }
      }
   }

   @SubscribeEvent
   public static void onLevelLoad(Load event) {
      if (event.getLevel() instanceof ServerLevel serverLevel) {
         try {
            StructureSpawnPlanner.onLevelLoad(serverLevel);
         } catch (Throwable var3) {
         }

         if (serverLevel.dimension().equals(OtherworldDimension.OTHERWORLD_KEY)) {
            LogUtil.info(Env.SERVER, "LevelEvent.Load: Detected Otherworld dimension load, attempting to load regions.");
            OtherworldRegionLoader.loadPreGeneratedRegions(serverLevel.getServer());
         }
      }
   }

   @SubscribeEvent
   public static void onServerStopping(ServerStoppingEvent event) {
      StorageManager.shutdown();
      StructureSpawnPlanner.reset();
      StructureRepairManager.reset();
   }

   @SubscribeEvent
   public static void onRegisterCommands(RegisterCommandsEvent event) {
      DMZServer.registerCommands(event.getDispatcher());
   }

   @SubscribeEvent
   public static void onLocateCommand(CommandEvent event) {
      try {
         ParseResults<CommandSourceStack> parse = event.getParseResults();
         List<ParsedCommandNode<CommandSourceStack>> nodes = parse.getContext().getNodes();
         if (nodes.size() < 3) {
            return;
         }

         if (!nodes.get(0).getNode().getName().equals("locate")) {
            return;
         }

         if (!nodes.get(1).getNode().getName().equals("structure")) {
            return;
         }

         String argument = nodes.get(2).getRange().get(parse.getReader().getString());
         if (argument.isEmpty() || argument.charAt(0) == '#') {
            return;
         }

         ResourceLocation id = ResourceLocation.tryParse(argument);
         if (id == null || !id.getNamespace().equals("dragonminez")) {
            return;
         }

         CommandSourceStack source = (CommandSourceStack)parse.getContext().getSource();
         ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
         if (!StructureLocator.usesCustomPlacement(source.getLevel(), key)) {
            return;
         }

         event.setCanceled(true);
         LocateCommand.locate(source, key);
      } catch (Exception var7) {
      }
   }

   @SubscribeEvent
   public static void onMobSpawn(PositionCheck event) {
      Mob mob = event.getEntity();
      if (mob.getType().getCategory() == MobCategory.MONSTER) {
         if (!mob.level().dimension().equals(HTCDimension.HTC_KEY)) {
            List<MastersEntity> masters = mob.level().getEntitiesOfClass(MastersEntity.class, new AABB(mob.blockPosition()).inflate(80.0));
            if (!masters.isEmpty()) {
               event.setResult(Result.FAIL);
            }
         }
      }
   }

   @SubscribeEvent
   public static void onEquipmentChange(LivingEquipmentChangeEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         if (event.getSlot() == EquipmentSlot.CHEST) {
            ItemStack newStack = event.getTo();
            StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
               boolean shouldBeArmored = false;
               if (!newStack.isEmpty() && newStack.getItem() instanceof ArmorItem) {
                  boolean isVanilla = BuiltInRegistries.ITEM.getKey(newStack.getItem()).getNamespace().equals("minecraft");
                  boolean isDbzArmor = newStack.getItem() instanceof DbzArmorTextured;
                  if (!isVanilla && !isDbzArmor) {
                     shouldBeArmored = true;
                  }
               }

               if (stats.getCharacter().getArmored() != shouldBeArmored) {
                  stats.getCharacter().setArmored(shouldBeArmored);
                  NetworkHandler.sendToTrackingEntityAndSelf(new AppearanceSyncS2C(player), player);
               }
            });
         }
      }
   }

   @SubscribeEvent
   public static void onLivingAttack(LivingIncomingDamageEvent event) {
      LivingEntity victim = event.getEntity();
      if (isCharacterCreationProtected(victim)) {
         event.setCanceled(true);
      } else if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
         if (victim instanceof Player || victim instanceof DBSagasEntity) {
            AABB searchArea = victim.getBoundingBox().inflate(3.0);

            for (KiBarrierEntity barrier : victim.level().getEntitiesOfClass(KiBarrierEntity.class, searchArea)) {
               if (barrier.isActive() && barrier.protects(victim)) {
                  event.setCanceled(true);
                  barrier.absorbDamage(event.getAmount(), event.getSource().getEntity());
                  return;
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLivingDamage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event) {
   }

   private static boolean isCharacterCreationProtected(LivingEntity entity) {
      if (!ConfigManager.getServerConfig().getGameplay().getForceCharacterCreation()) {
         return false;
      } else if (entity instanceof ServerPlayer player) {
         boolean[] protectedPlayer = new boolean[]{false};
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
            if (!data.getStatus().isHasCreatedCharacter()) {
               protectedPlayer[0] = true;
            }
         });
         return protectedPlayer[0];
      } else {
         return false;
      }
   }

   public static void endFusionIfNeeded(ServerPlayer player) {
      StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(data -> {
         if (data.getStatus().isFused() || data.getStatus().getFusionPartnerUUID() != null) {
            UUID partnerUUID = data.getStatus().getFusionPartnerUUID();
            if (partnerUUID != null) {
               ServerPlayer partner = player.getServer().getPlayerList().getPlayer(partnerUUID);
               if (partner != null) {
                  if (!partner.isDeadOrDying()) {
                     partner.kill();
                  }

                  StatsProvider.get(StatsCapability.INSTANCE, partner).ifPresent(partnerData -> FusionLogic.endFusion(partner, partnerData, true));
               }
            }

            FusionLogic.endFusion(player, data, true);
         }
      });
   }

   @SubscribeEvent
   public static void onAddReloadListeners(AddReloadListenerEvent event) {
      event.addListener(SpacePodDestinationRegistry.INSTANCE);
      event.addListener(DragonDefinitionReloadListener.INSTANCE);
      event.addListener(DragonWishRegistry.INSTANCE);
      event.addListener(new SimplePreparableReloadListener<Void>() {
         protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
            return null;
         }

         protected void apply(Void dummy, ResourceManager resourceManager, ProfilerFiller profiler) {
            WeaponRegistry.loadAttributes(resourceManager);
         }
      });
   }

   @SubscribeEvent
   public static void onHandSwap(Hands event) {
      if (event.getEntity() instanceof Player player) {
         ItemStack offHandStack = player.getOffhandItem();
         event.setItemSwappedToOffHand(player.getMainHandItem());
         event.setItemSwappedToMainHand(offHandStack);
      }
   }
}
