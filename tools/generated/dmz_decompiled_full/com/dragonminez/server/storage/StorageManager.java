package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.common.events.DMZEvent;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.StatsSyncS2C;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.util.TransformationsHelper;
import com.dragonminez.server.events.players.StatsEvents;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class StorageManager {
   private static IDataStorage activeStorage;
   private static ScheduledExecutorService autoSaveScheduler;
   private static ExecutorService dbExecutor;
   private static final ConcurrentHashMap<UUID, CompletableFuture<Void>> saveChains = new ConcurrentHashMap<>();

   public static void init() {
      GeneralServerConfig.StorageConfig.StorageType type = ConfigManager.getServerConfig().getStorage().getStorageType();
      switch (type) {
         case DATABASE:
            activeStorage = new DatabaseManager();
            break;
         case JSON:
            activeStorage = new JsonStorage();
            break;
         case NBT:
            LogUtil.info(Env.SERVER, "Using default NBT storage (Vanilla).");
            activeStorage = null;
      }

      if (activeStorage != null) {
         activeStorage.init();
         int threads = ConfigManager.getServerConfig().getStorage().getThreadPoolSize();
         if (dbExecutor != null) {
            shutdownDbExecutor();
         }

         dbExecutor = Executors.newFixedThreadPool(threads);
         LogUtil.info(Env.SERVER, "Storage initialized with " + threads + " async threads.");
         startAutoSave();
      }
   }

   public static void reload() {
      LogUtil.info(Env.SERVER, "Reloading Storage Subsystem...");
      if (ServerLifecycleHooks.getCurrentServer() != null) {
         LogUtil.info(Env.SERVER, "Saving online players before storage switch...");

         for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            savePlayer(player);
         }
      }

      shutdown();
      init();
      LogUtil.info(Env.SERVER, "Storage Subsystem reloaded. Active: " + (activeStorage == null ? "NBT (Vanilla)" : activeStorage.getName()));
   }

   public static void shutdown() {
      if (autoSaveScheduler != null && !autoSaveScheduler.isShutdown()) {
         autoSaveScheduler.shutdown();
      }

      if (activeStorage != null) {
         activeStorage.shutdown();
      }

      if (dbExecutor != null) {
         shutdownDbExecutor();
         dbExecutor = null;
      }
   }

   private static void shutdownDbExecutor() {
      dbExecutor.shutdown();

      try {
         if (!dbExecutor.awaitTermination(5L, TimeUnit.SECONDS)) {
            dbExecutor.shutdownNow();
         }
      } catch (InterruptedException var1) {
         dbExecutor.shutdownNow();
         Thread.currentThread().interrupt();
      }
   }

   public static void loadPlayer(ServerPlayer player) {
      if (activeStorage != null) {
         UUID uuid = player.getUUID();
         CompletableFuture.<CompoundTag>supplyAsync(() -> activeStorage.loadData(uuid), dbExecutor)
            .thenAccept(loadedData -> ServerLifecycleHooks.getCurrentServer().execute(() -> {
                  if (loadedData != null && player.connection != null) {
                     applyLoadedData(player, loadedData);
                  }
               }))
            .exceptionally(ex -> {
               LogUtil.error(Env.SERVER, "Error loading data async for " + player.getName().getString(), ex);
               return null;
            });
      }
   }

   private static void applyLoadedData(ServerPlayer player, CompoundTag loadedData) {
      NeoForge.EVENT_BUS.post(new DMZEvent.PlayerDataLoadEvent(player, loadedData));
      StatsProvider.get(StatsCapability.INSTANCE, player)
         .ifPresent(
            stats -> {
               if (isEmptyOrZeroStatsBlob(loadedData) && stats.getStats().getTotalStats() > 0) {
                  LogUtil.info(
                     Env.SERVER,
                     "Skipping async overwrite for {}: secondary store has empty Stats while live totals are {}",
                     player.getName().getString(),
                     stats.getStats().getTotalStats()
                  );
               } else {
                  try {
                     stats.load(loadedData);
                  } catch (ClassNotFoundException var4) {
                     throw new RuntimeException(var4);
                  }

                  if (stats.getPlayerQuestData().isSagaLocked("saiyan_saga")) {
                     stats.getPlayerQuestData().setSagaUnlocked("saiyan_saga", true);
                  }

                  TransformationsHelper.ensureSelectedFormDefault(stats);
                  TransformationsHelper.ensureSelectedStackFormDefault(stats);
                  StatsEvents.restoreStatsPoolsOnJoin(player);
                  NetworkHandler.sendToTrackingEntityAndSelf(new StatsSyncS2C(player), player);
                  LogUtil.info(
                     Env.SERVER,
                     "Async data loaded for: "
                        + player.getName().getString()
                        + " VIT="
                        + stats.getStats().getVitality()
                        + " PWR="
                        + stats.getStats().getKiPower()
                        + " ENE="
                        + stats.getStats().getEnergy()
                  );
               }
            }
         );
   }

   private static boolean isEmptyOrZeroStatsBlob(CompoundTag loadedData) {
      if (loadedData != null && loadedData.contains("Stats")) {
         CompoundTag s = loadedData.getCompound("Stats");
         return s.getInt("STR") == 0 && s.getInt("SKP") == 0 && s.getInt("RES") == 0 && s.getInt("VIT") == 0 && s.getInt("PWR") == 0 && s.getInt("ENE") == 0;
      } else {
         return true;
      }
   }

   public static void savePlayer(ServerPlayer player) {
      if (activeStorage != null) {
         StatsProvider.get(StatsCapability.INSTANCE, player).ifPresent(stats -> {
            if (stats.isDataLoaded() || stats.getStatus().isHasCreatedCharacter()) {
               CompoundTag dataToSave = stats.save();
               NeoForge.EVENT_BUS.post(new DMZEvent.PlayerDataSaveEvent(player, dataToSave));
               String name = player.getScoreboardName();
               UUID uuid = player.getUUID();
               saveChains.compute(uuid, (id, previous) -> {
                  CompletableFuture<Void> previousStage = (CompletableFuture<Void>)(previous != null ? previous : CompletableFuture.completedFuture(null));
                  CompletableFuture<Void> chained = previousStage.thenRunAsync(() -> {
                     try {
                        activeStorage.saveData(uuid, name, dataToSave);
                     } catch (Exception var4x) {
                        LogUtil.error(Env.SERVER, "Failed to save data async for " + name, var4x);
                     }
                  }, dbExecutor);
                  chained.whenComplete((v, ex) -> saveChains.remove(uuid, chained));
                  return chained;
               });
            }
         });
      }
   }

   private static void startAutoSave() {
      autoSaveScheduler = Executors.newSingleThreadScheduledExecutor();
      autoSaveScheduler.scheduleAtFixedRate(StorageManager::performAutoSave, 5L, 5L, TimeUnit.MINUTES);
   }

   private static void performAutoSave() {
      if (ServerLifecycleHooks.getCurrentServer() != null && activeStorage != null) {
         LogUtil.info(Env.SERVER, "Auto-Saving data...");

         for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            savePlayer(player);
         }
      }
   }
}
