package com.dragonminez.common.quest;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Stream;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class QuestRegistry extends SimplePreparableReloadListener<Map<String, Quest>> {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String SAGA_FOLDER = "dragonminez" + File.separator + "sagas";
   private static final String SIDEQUEST_FOLDER = "dragonminez" + File.separator + "sidequests";
   private static final String QUESTS_FOLDER = "dragonminez" + File.separator + "quests";
   private static final Map<String, Quest> LOADED_QUESTS = new LinkedHashMap<>();
   private static final Map<String, Saga> LOADED_SAGAS = new LinkedHashMap<>();
   private static final Map<String, Quest> CLIENT_QUESTS = new LinkedHashMap<>();
   private static final Map<String, Saga> CLIENT_SAGAS = new LinkedHashMap<>();
   private static final Map<QuestObjective.ObjectiveType, List<String>> OBJECTIVE_INDEX = new EnumMap<>(QuestObjective.ObjectiveType.class);
   private static final Map<String, List<String>> QUEST_GIVER_INDEX = new HashMap<>();
   private static final Map<String, List<String>> TURN_IN_INDEX = new HashMap<>();
   private static Path cachedWorldFolder = null;

   public static void init() {
   }

   public static void loadAll(@Nullable MinecraftServer server) {
      LOADED_QUESTS.clear();
      LOADED_SAGAS.clear();
      OBJECTIVE_INDEX.clear();
      QUEST_GIVER_INDEX.clear();
      TURN_IN_INDEX.clear();
      if (server == null) {
         LogUtil.error(Env.COMMON, "QuestRegistry: cannot load — server is null");
      } else {
         ServerLevel overworld = server.getLevel(Level.OVERWORLD);
         if (overworld == null) {
            LogUtil.error(Env.COMMON, "QuestRegistry: cannot load — overworld is null");
         } else {
            Path worldFolder = overworld.getServer().getWorldPath(LevelResource.ROOT);
            cachedWorldFolder = worldFolder;
            QuestUpdateReport.clear();
            JsonLoadReport.clear("quests");
            QuestUpgrader.setAutoUpdateEnabled(ConfigManager.getServerConfig().getGameplay().getAutoUpdateQuests());
            Path questsDir = worldFolder.resolve(QUESTS_FOLDER);

            try {
               if (!Files.exists(questsDir)) {
                  Files.createDirectories(questsDir);
               }

               QuestDefaults.createDefaultQuestFiles(questsDir);
            } catch (IOException var7) {
               LogUtil.error(Env.COMMON, "Failed to create quests directory", var7);
            }

            if (ConfigManager.getServerConfig().getGameplay().getStoryModeEnabled()) {
               loadSagaFiles(worldFolder.resolve(SAGA_FOLDER));
            }

            if (ConfigManager.getServerConfig().getGameplay().getSideQuestsEnabled()) {
               loadSideQuestFiles(worldFolder.resolve(SIDEQUEST_FOLDER));
            }

            buildIndexes();
            int sagaQuestCount = 0;

            for (Saga saga : LOADED_SAGAS.values()) {
               sagaQuestCount += saga.getQuests().size();
            }

            int sideCount = (int)LOADED_QUESTS.values().stream().filter(Quest::isSideQuest).count();
            LogUtil.info(
               Env.COMMON,
               "QuestRegistry: loaded {} quest(s) ({} saga quests across {} sagas, {} sidequests)",
               LOADED_QUESTS.size(),
               sagaQuestCount,
               LOADED_SAGAS.size(),
               sideCount
            );
            emitUpgradeReport(worldFolder);
         }
      }
   }

   private static void emitUpgradeReport(Path worldFolder) {
      if (!QuestUpdateReport.isEmpty()) {
         int applied = QuestUpdateReport.totalApplied();
         int conflicts = QuestUpdateReport.totalConflicts();
         LogUtil.info(
            Env.COMMON,
            "QuestRegistry: quest defaults upgraded — {} value(s) auto-updated, {} conflict(s) kept as your edits across {} file(s)",
            applied,
            conflicts,
            QuestUpdateReport.changedFiles().size()
         );
         JsonLoadReport.update("quests", "quest defaults", updateReportSummary());
         StringBuilder sb = new StringBuilder();
         sb.append("DragonMineZ quest update report\n");
         sb.append("Target defaults version: ").append("2.1.2").append('\n');
         sb.append(applied).append(" value(s) auto-updated to the new defaults; ").append(conflicts).append(" conflict(s) left as your edits.\n");
         sb.append("Originals were backed up under dragonminez/oldBackup/.\n\n");

         for (QuestUpdateReport.FileReport file : QuestUpdateReport.changedFiles()) {
            sb.append(file.relativePath).append(" (").append(file.fromVersion).append(" -> ").append(file.toVersion).append(")\n");
            if (file.appliedCount > 0) {
               sb.append("  auto-updated ").append(file.appliedCount).append(" field(s) you had left at the old default\n");
            }

            for (QuestUpdateReport.Conflict c : file.conflicts) {
               sb.append("  CONFLICT at '")
                  .append(c.path())
                  .append("': kept your value ")
                  .append(c.userValue())
                  .append(" (old default ")
                  .append(c.oldDefault())
                  .append(", new default ")
                  .append(c.newDefault())
                  .append(")\n");
            }

            sb.append('\n');
         }

         try {
            Path reportFile = worldFolder.resolve("dragonminez").resolve("quest_update_report.txt");
            Files.createDirectories(reportFile.getParent());
            Files.writeString(reportFile, sb.toString(), StandardCharsets.UTF_8);
         } catch (IOException var8) {
            LogUtil.error(Env.COMMON, "Failed to write quest update report: {}", var8.getMessage());
         }
      }
   }

   public static boolean hasPendingUpdateReport() {
      return !QuestUpdateReport.isEmpty();
   }

   public static String updateReportSummary() {
      return String.format(
         "DragonMineZ upgraded %d quest file(s): %d value(s) auto-updated, %d conflict(s) kept as your edits. See dragonminez/quest_update_report.txt.",
         QuestUpdateReport.changedFiles().size(),
         QuestUpdateReport.totalApplied(),
         QuestUpdateReport.totalConflicts()
      );
   }

   private static void loadSagaFiles(Path sagaDir) {
      try {
         if (!Files.exists(sagaDir)) {
            Files.createDirectories(sagaDir);
         }

         SagaDefaults.createDefaultSagaFiles(sagaDir);

         try (Stream<Path> stream = Files.walk(sagaDir)) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(QuestRegistry::loadSingleSagaFile);
         }
      } catch (IOException var6) {
         LogUtil.error(Env.COMMON, "Failed to load saga files from {}", sagaDir, var6);
      }
   }

   private static void loadSingleSagaFile(Path file) {
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         QuestParser.validateSaga("quests", "sagas/" + file.getFileName(), root);
         Saga saga = parseSagaFromJson(root, cachedWorldFolder);
         LOADED_SAGAS.put(saga.getId(), saga);

         for (Quest quest : saga.getQuests()) {
            String effectiveId = saga.getId() + ":" + quest.getId();
            LOADED_QUESTS.put(effectiveId, quest);
         }

         LogUtil.info(Env.COMMON, "Loaded saga: {} ({} quests)", saga.getName(), saga.getQuests().size());
      } catch (Exception var9) {
         LogUtil.error(Env.COMMON, "Failed to load saga file: {}", file.getFileName(), var9);
         JsonLoadReport.error("quests", "sagas/" + file.getFileName(), "Malformed saga JSON, file skipped: " + JsonLoadReport.rootCause(var9));
      }
   }

   private static Saga parseSagaFromJson(JsonObject json, @Nullable Path worldFolder) {
      String id = json.get("id").getAsString();
      String name = json.get("name").getAsString();
      Saga.SagaRequirements requirements = null;
      if (json.has("requirements")) {
         JsonObject reqJson = json.getAsJsonObject("requirements");
         String prevSaga = reqJson.has("previousSaga") ? reqJson.get("previousSaga").getAsString() : "";
         requirements = new Saga.SagaRequirements(prevSaga);
      }

      List<Quest> quests = new ArrayList<>();
      if (!json.has("questFolder")) {
         LogUtil.warn(Env.COMMON, "Saga '{}' is missing required field 'questFolder'", id);
         return new Saga(id, name, quests, requirements);
      } else {
         String folderName = json.get("questFolder").getAsString();
         if (worldFolder == null) {
            LogUtil.warn(Env.COMMON, "Saga '{}' cannot resolve questFolder '{}' because world folder context is unavailable", id, folderName);
            return new Saga(id, name, quests, requirements);
         } else {
            Path questsBase = worldFolder.resolve(QUESTS_FOLDER).normalize();
            Path questFolder = questsBase.resolve(folderName).normalize();
            if (!questFolder.startsWith(questsBase)) {
               LogUtil.warn(Env.COMMON, "Saga '{}' has a questFolder '{}' that escapes the quests directory; skipping", id, folderName);
               return new Saga(id, name, quests, requirements);
            } else {
               if (Files.exists(questFolder)) {
                  quests = loadQuestsFromFolder(questFolder);
               } else {
                  LogUtil.warn(Env.COMMON, "Saga '{}' references questFolder '{}' but it doesn't exist", id, folderName);
               }

               return new Saga(id, name, quests, requirements);
            }
         }
      }
   }

   private static List<Quest> loadQuestsFromFolder(Path folder) {
      List<Quest> quests = new ArrayList<>();

      try (Stream<Path> stream = Files.list(folder)) {
         for (Path file : stream.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
               JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               QuestParser.validate("quests", "quests/" + folder.getFileName() + "/" + file.getFileName(), root);
               Quest quest = QuestParser.parseQuest(root);
               if (quest != null) {
                  quests.add(quest);
               }
            } catch (Exception var12) {
               LogUtil.error(Env.COMMON, "Failed to load quest file: {}", file.getFileName(), var12);
               JsonLoadReport.error(
                  "quests",
                  "quests/" + folder.getFileName() + "/" + file.getFileName(),
                  "Malformed quest JSON, file skipped: " + JsonLoadReport.rootCause(var12)
               );
            }
         }
      } catch (IOException var14) {
         LogUtil.error(Env.COMMON, "Failed to list quest files in folder: {}", folder, var14);
      }

      return quests;
   }

   private static void loadSideQuestFiles(Path sideQuestDir) {
      try {
         if (!Files.exists(sideQuestDir)) {
            Files.createDirectories(sideQuestDir);
         }

         SideQuestDefaults.createDefaultSideQuestFiles(sideQuestDir);

         try (Stream<Path> stream = Files.walk(sideQuestDir)) {
            stream.filter(path -> path.toString().endsWith(".json")).forEach(QuestRegistry::loadSingleSideQuestFile);
         }
      } catch (IOException var6) {
         LogUtil.error(Env.COMMON, "Failed to load side-quest files from {}", sideQuestDir, var6);
      }
   }

   private static void loadSingleSideQuestFile(Path file) {
      try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
         JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
         QuestParser.validate("quests", "sidequests/" + file.getFileName(), root);
         Quest quest = QuestParser.parseQuest(root);
         if (quest != null) {
            String effectiveId = quest.getStringId() != null ? quest.getStringId() : quest.getEffectiveId();
            LOADED_QUESTS.put(effectiveId, quest);
         }
      } catch (Exception var7) {
         LogUtil.error(Env.COMMON, "Failed to load side-quest file: {}", file.getFileName(), var7);
         JsonLoadReport.error("quests", "sidequests/" + file.getFileName(), "Malformed side-quest JSON, file skipped: " + JsonLoadReport.rootCause(var7));
      }
   }

   private static void buildIndexes() {
      for (Entry<String, Quest> entry : LOADED_QUESTS.entrySet()) {
         String questId = entry.getKey();
         Quest quest = entry.getValue();

         for (QuestObjective objective : quest.getObjectives()) {
            OBJECTIVE_INDEX.computeIfAbsent(objective.getType(), k -> new ArrayList<>()).add(questId);
         }

         if (quest.getQuestGiver() != null && !quest.getQuestGiver().isEmpty()) {
            QUEST_GIVER_INDEX.computeIfAbsent(quest.getQuestGiver(), k -> new ArrayList<>()).add(questId);
         }

         if (quest.getTurnIn() != null && !quest.getTurnIn().isEmpty()) {
            TURN_IN_INDEX.computeIfAbsent(quest.getTurnIn(), k -> new ArrayList<>()).add(questId);
         }
      }
   }

   @Nullable
   public static Quest getQuest(String questId) {
      return LOADED_QUESTS.get(questId);
   }

   public static Map<String, Quest> getAllQuests() {
      return Collections.unmodifiableMap(LOADED_QUESTS);
   }

   @Nullable
   public static Saga getSaga(String sagaId) {
      return LOADED_SAGAS.get(sagaId);
   }

   public static Map<String, Saga> getAllSagas() {
      return Collections.unmodifiableMap(LOADED_SAGAS);
   }

   public static List<String> getQuestIdsByObjectiveType(QuestObjective.ObjectiveType type) {
      return OBJECTIVE_INDEX.getOrDefault(type, Collections.emptyList());
   }

   public static List<String> getQuestIdsByGiver(String npcId) {
      return QUEST_GIVER_INDEX.getOrDefault(npcId, Collections.emptyList());
   }

   public static List<String> getQuestIdsByTurnIn(String npcId) {
      return TURN_IN_INDEX.getOrDefault(npcId, Collections.emptyList());
   }

   public static List<Quest> getQuestsByType(Quest.QuestType type) {
      List<Quest> result = new ArrayList<>();

      for (Quest quest : LOADED_QUESTS.values()) {
         if (quest.getType() == type) {
            result.add(quest);
         }
      }

      return result;
   }

   public static List<Quest> getQuestsByCategory(String category) {
      List<Quest> result = new ArrayList<>();

      for (Quest quest : LOADED_QUESTS.values()) {
         if (category.equalsIgnoreCase(quest.getCategory())) {
            result.add(quest);
         }
      }

      return result;
   }

   @Nullable
   public static Quest getClientQuest(String questId) {
      return CLIENT_QUESTS.get(questId);
   }

   public static Map<String, Quest> getClientQuests() {
      return Collections.unmodifiableMap(CLIENT_QUESTS);
   }

   @Nullable
   public static Saga getClientSaga(String sagaId) {
      return CLIENT_SAGAS.get(sagaId);
   }

   public static Map<String, Saga> getClientSagas() {
      return Collections.unmodifiableMap(CLIENT_SAGAS);
   }

   public static void applySyncedQuests(Map<String, Quest> quests) {
      CLIENT_QUESTS.clear();
      CLIENT_QUESTS.putAll(quests);
      LogUtil.info(Env.CLIENT, "QuestRegistry: synced {} quest(s) from server", quests.size());
   }

   public static void applySyncedSagas(Map<String, Saga> sagas) {
      CLIENT_SAGAS.clear();
      CLIENT_SAGAS.putAll(sagas);
      LogUtil.info(Env.CLIENT, "QuestRegistry: synced {} saga(s) from server", sagas.size());
   }

   @NotNull
   protected Map<String, Quest> prepare(@NotNull ResourceManager pResourceManager, @NotNull ProfilerFiller pProfiler) {
      return new HashMap<>(LOADED_QUESTS);
   }

   protected void apply(@NotNull Map<String, Quest> pObject, @NotNull ResourceManager pResourceManager, @NotNull ProfilerFiller pProfiler) {
      LOADED_QUESTS.clear();
      LOADED_QUESTS.putAll(pObject);
      buildIndexes();
   }
}
