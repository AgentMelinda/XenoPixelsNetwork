package com.dragonminez.common.stats;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.ResourceSyncS2C;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.network.S2C.SyncQuestRegistryS2C;
import com.dragonminez.common.network.S2C.SyncServerConfigS2C;
import com.dragonminez.common.quest.PlayerQuestData;
import com.dragonminez.common.quest.QuestRegistry;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.compat.capabilities.Capability;
import com.dragonminez.compat.capabilities.CapabilityManager;
import com.dragonminez.compat.capabilities.CapabilityToken;
import com.dragonminez.server.events.players.StatsEvents;
import com.dragonminez.server.events.players.TickHandler;
import com.dragonminez.server.world.structure.helper.QuestStructureHints;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.Clone;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent.Post;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries.Keys;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class StatsCapability {
   public static final Capability<StatsData> INSTANCE = CapabilityManager.get(new CapabilityToken<StatsData>() {
   });
   private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(Keys.ATTACHMENT_TYPES, "dragonminez");
   public static final DeferredHolder<AttachmentType<?>, AttachmentType<StatsProvider>> PLAYER_STATS = ATTACHMENTS.register(
      "player_stats", () -> AttachmentType.serializable(holder -> new StatsProvider((Player)holder)).copyOnDeath().build()
   );
   private static StatsData CLIENT_CACHE;

   public static void clearClientCache() {
      CLIENT_CACHE = null;
   }

   public static void register(IEventBus modEventBus) {
      ATTACHMENTS.register(modEventBus);
   }

   @SubscribeEvent
   public static void onPlayerClone(Clone event) {
      Player player = event.getEntity();
      Player original = event.getOriginal();
      TickHandler.registerForceKillGrace(player.getUUID());
      StatsProvider.get(INSTANCE, player).ifPresent(newData -> {
         StatsProvider.get(INSTANCE, original).ifPresent(oldData -> {
            newData.copyFrom(oldData);
            if (player.level().isClientSide) {
               if (oldData.getStatus().isHasCreatedCharacter()) {
                  CLIENT_CACHE = oldData;
               } else if (CLIENT_CACHE != null) {
                  newData.copyFrom(CLIENT_CACHE);
               }
            }
         });
         if (player instanceof ServerPlayer serverPlayer) {
            StatsEvents.restoreStatsPoolsOnJoin(serverPlayer);
         } else {
            newData.reapplyStatAttributes();
         }
      });
      StatsProvider.remove(original);
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         List<String> availableConfigs = ConfigManager.getAvailableConfigFiles();
         boolean resetBatch = true;

         for (String file : availableConfigs) {
            if (!file.equals("general-user")) {
               String jsonPayload = ConfigManager.getSpecificConfigJson(file);
               if (jsonPayload != null && !jsonPayload.isBlank()) {
                  NetworkHandler.sendToPlayer(new SyncServerConfigS2C(file, jsonPayload, resetBatch), serverPlayer);
                  resetBatch = false;
               }
            }
         }

         NetworkHandler.sendToPlayer(new SyncQuestRegistryS2C(QuestRegistry.getAllSagas(), QuestRegistry.getAllQuests()), serverPlayer);
         MinecraftServer server = serverPlayer.getServer();
         if (server != null && !QuestStructureHints.isResolved()) {
            UUID playerId = serverPlayer.getUUID();
            QuestStructureHints.ensureResolvedAsync(server).thenRun(() -> server.execute(() -> {
                  ServerPlayer online = server.getPlayerList().getPlayer(playerId);
                  if (online != null) {
                     NetworkHandler.sendToPlayer(new SyncQuestRegistryS2C(QuestRegistry.getAllSagas(), QuestRegistry.getAllQuests()), online);
                  }
               }));
         }

         StatsProvider.get(INSTANCE, serverPlayer)
            .ifPresent(
               data -> {
                  markCurrentDimensionVisited(serverPlayer, data);
                  PlayerQuestData questData = data.getPlayerQuestData();
                  if (questData.isSagaLocked("saiyan_saga")) {
                     questData.setSagaUnlocked("saiyan_saga", true);
                  }

                  TransformationsHelper.ensureSelectedFormDefault(data);
                  TransformationsHelper.ensureSelectedStackFormDefault(data);
                  data.getStatus().setStrikeLocked(false);
                  data.getStatus().setStunEffect(false);
                  data.getStatus().setKnockedDown(false);
                  data.getCooldowns().removeCooldown("KnockdownDuration");
                  Map<String, String> repairedSkills = data.getSkills().repairSkillNames();
                  if (!repairedSkills.isEmpty()) {
                     repairedSkills.forEach(
                        (oldName, newName) -> LogUtil.info(
                              Env.SERVER, "Repaired skill for {}: '{}' -> '{}'", serverPlayer.getGameProfile().getName(), oldName, newName
                           )
                     );
                  }

                  data.getSkills().setSkillActive("kisense", false);
                  StatsEvents.restoreStatsPoolsOnJoin(serverPlayer);
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
               }
            );
      }

      event.getEntity().refreshDimensions();
   }

   @SubscribeEvent
   public static void onPlayerTick(Post event) {
      if (!event.getEntity().level().isClientSide) {
         StatsProvider.get(INSTANCE, event.getEntity()).ifPresent(StatsData::tick);
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         StatsProvider.get(INSTANCE, serverPlayer).ifPresent(data -> {
            data.getResources().setCurrentEnergy(data.getMaxEnergy());
            data.getResources().setCurrentStamina(data.getMaxStamina());
            data.getStatus().setStrikeLocked(false);
            data.getStatus().setStunEffect(false);
            data.getStatus().setKnockedDown(false);
            data.getCooldowns().removeCooldown("KnockdownDuration");
            NetworkHandler.sendToTrackingEntityAndSelf(new ResourceSyncS2C(serverPlayer), serverPlayer);
         });
      }
   }

   @SubscribeEvent
   public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer serverPlayer) {
         StatsProvider.get(INSTANCE, serverPlayer).ifPresent(data -> {
            markCurrentDimensionVisited(serverPlayer, data);
            data.getSkills().setSkillActive("kisense", false);
            data.getStatus().setStrikeLocked(false);
            data.getStatus().setStunEffect(false);
            data.getStatus().setKnockedDown(false);
            data.getCooldowns().removeCooldown("KnockdownDuration");
            NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(serverPlayer), serverPlayer);
         });
      }
   }

   private static void markCurrentDimensionVisited(ServerPlayer player, StatsData data) {
      data.getStatus().markVisitedDimension(player.serverLevel().dimension().location().toString());
   }
}
