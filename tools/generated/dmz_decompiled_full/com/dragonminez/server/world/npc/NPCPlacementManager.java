package com.dragonminez.server.world.npc;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.diagnostics.JsonKeys;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.dragonminez.common.init.entities.questnpc.QuestNPCEntity;
import com.dragonminez.server.world.structure.helper.DMZStructures;
import com.dragonminez.server.world.structure.helper.StructureLocator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

public final class NPCPlacementManager {
   public static final String PLACEMENT_TAG = "dmz_npc_placement_id";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String NPC_FOLDER = "dragonminez" + File.separator + "npcs";
   private static final String PLACEMENTS_FILE = "placements.json";
   private static List<NPCPlacementManager.NPCPlacement> placements = List.of();
   private static Path loadedFrom = null;
   private static final String PLACEMENTS_LABEL = "npcs/placements.json";
   private static final Set<String> ROOT_KEYS = Set.of("placements", "schema");
   private static final Set<String> PLACEMENT_KEYS = Set.of(
      "id",
      "entity",
      "dimension",
      "npc_id",
      "model",
      "texture",
      "structure",
      "x",
      "y",
      "z",
      "yaw",
      "pitch",
      "surface",
      "relative_to_spawn",
      "enabled",
      "override",
      "alignment",
      "relation"
   );

   private NPCPlacementManager() {
   }

   public static void load(MinecraftServer server) {
      if (server != null) {
         Path worldFolder = server.getWorldPath(LevelResource.ROOT);
         Path npcDir = worldFolder.resolve(NPC_FOLDER);
         Path file = npcDir.resolve("placements.json");

         try {
            Files.createDirectories(npcDir);
            if (!Files.exists(file)) {
               writeDefaultPlacements(file);
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
               JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               placements = parsePlacements(root);
               loadedFrom = file;
               LogUtil.info(Env.SERVER, "NPCPlacementManager: loaded {} placement(s)", placements.size());
            }
         } catch (Exception var9) {
            placements = List.of();
            loadedFrom = file;
            LogUtil.error(Env.SERVER, "NPCPlacementManager: failed to load NPC placements from {}", file, var9);
         }
      }
   }

   public static void spawnForLoadedLevels(MinecraftServer server) {
      if (server != null) {
         ensureLoaded(server);

         for (ServerLevel level : server.getAllLevels()) {
            spawnForLevel(server, level);
         }
      }
   }

   public static void spawnForLevel(ServerLevel level) {
      if (level != null) {
         spawnForLevel(level.getServer(), level);
      }
   }

   public static void spawnForLevel(MinecraftServer server, ServerLevel level) {
      if (server != null && level != null) {
         ensureLoaded(server);
         if (!placements.isEmpty()) {
            for (NPCPlacementManager.NPCPlacement placement : placements) {
               if (placement.enabled() && placement.dimension().equals(level.dimension())) {
                  spawnOrUpdate(level, placement);
               }
            }
         }
      }
   }

   private static void ensureLoaded(MinecraftServer server) {
      Path file = server.getWorldPath(LevelResource.ROOT).resolve(NPC_FOLDER).resolve("placements.json");
      if (loadedFrom == null || !loadedFrom.equals(file)) {
         load(server);
      }
   }

   private static List<NPCPlacementManager.NPCPlacement> parsePlacements(@Nullable JsonObject root) {
      if (root != null && root.has("placements") && root.get("placements").isJsonArray()) {
         JsonLoadReport.clear("npcs");
         JsonKeys.checkObject("npcs", "npcs/placements.json", "", root, ROOT_KEYS);
         List<NPCPlacementManager.NPCPlacement> parsed = new ArrayList<>();
         int index = 0;

         for (JsonElement element : root.getAsJsonArray("placements")) {
            if (element.isJsonObject()) {
               JsonKeys.checkObject("npcs", "npcs/placements.json", "placements[" + index++ + "]", element.getAsJsonObject(), PLACEMENT_KEYS);
               NPCPlacementManager.NPCPlacement placement = parsePlacement(element.getAsJsonObject());
               if (placement != null) {
                  parsed.add(placement);
               }
            }
         }

         return List.copyOf(parsed);
      } else {
         return List.of();
      }
   }

   @Nullable
   private static NPCPlacementManager.NPCPlacement parsePlacement(JsonObject json) {
      String id = getString(json, "id", null);
      String entity = getString(json, "entity", null);
      String dimension = getString(json, "dimension", Level.OVERWORLD.location().toString());
      if (id != null && !id.isBlank() && entity != null && !entity.isBlank()) {
         ResourceKey<Level> dimensionKey;
         try {
            dimensionKey = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
         } catch (Exception var6) {
            return null;
         }

         return new NPCPlacementManager.NPCPlacement(
            id,
            entity,
            dimensionKey,
            getString(json, "npc_id", null),
            getString(json, "model", ""),
            getString(json, "texture", ""),
            getString(json, "structure", null),
            getDouble(json, "x", 0.5),
            getDouble(json, "y", 64.0),
            getDouble(json, "z", 0.5),
            getFloat(json, "yaw", 0.0F),
            getFloat(json, "pitch", 0.0F),
            getBoolean(json, "surface", false),
            getBoolean(json, "relative_to_spawn", false),
            getBoolean(json, "enabled", true),
            getBoolean(json, "override", false),
            getOptionalInt(json, "alignment"),
            getString(json, "relation", null)
         );
      } else {
         return null;
      }
   }

   private static void spawnOrUpdate(ServerLevel level, NPCPlacementManager.NPCPlacement placement) {
      NPCPlacementSavedData placementData = NPCPlacementSavedData.get(level);
      boolean overridePlacement = placement.override() || shouldForceManualSpawn(placement);

      ResourceLocation entityId;
      try {
         entityId = ResourceLocation.parse(placement.entity());
      } catch (Exception var11) {
         LogUtil.warn(Env.SERVER, "NPCPlacementManager: invalid entity id '{}' for placement '{}'", placement.entity(), placement.id());
         return;
      }

      EntityType<?> entityType = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.get(entityId);
      if (entityType == null) {
         LogUtil.warn(Env.SERVER, "NPCPlacementManager: unknown entity '{}' for placement '{}'", placement.entity(), placement.id());
      } else {
         Entity existing = findPlacedEntity(level, placement.id());
         if (existing == null) {
            Optional<UUID> placedUuid = placementData.getEntityUuid(placement.id());
            if (placedUuid.isPresent()) {
               Entity trackedEntity = level.getEntity(placedUuid.get());
               if (trackedEntity == null) {
                  NPCPlacementManager.ResolvedPosition pos = resolvePosition(level, placement);
                  NPCPlacementManager.MissingTrackedEntityAction action = resolveMissingTrackedEntity(isPlacementChunkLoaded(level, pos), overridePlacement);
                  if (action != NPCPlacementManager.MissingTrackedEntityAction.RESPAWN) {
                     return;
                  }

                  placementData.clear(placement.id());
               } else {
                  existing = trackedEntity;
                  applyPlacementMetadata(trackedEntity, placement);
               }
            }
         }

         if (existing != null && existing.getType() != entityType) {
            if (!overridePlacement) {
               LogUtil.warn(
                  Env.SERVER,
                  "NPCPlacementManager: placement '{}' is tagged on entity type '{}' but expects '{}'",
                  placement.id(),
                  existing.getType(),
                  entityType
               );
               return;
            }

            existing.discard();
            placementData.clear(placement.id());
            existing = null;
         }

         if (existing != null) {
            placementData.markSpawned(placement.id(), existing.getUUID());
            if (!overridePlacement) {
               applyPlacementMetadata(existing, placement);
               return;
            }
         }

         if (existing == null) {
            if (overridePlacement) {
               NPCPlacementManager.ResolvedPosition pos = resolvePosition(level, placement);
               existing = entityType.create(level);
               if (existing == null) {
                  LogUtil.warn(Env.SERVER, "NPCPlacementManager: failed to create entity '{}' for placement '{}'", placement.entity(), placement.id());
               } else {
                  applyPlacementData(existing, placement, pos);
                  boolean added = level.addFreshEntity(existing);
                  if (!added) {
                     LogUtil.warn(Env.SERVER, "NPCPlacementManager: failed to add spawned entity for placement '{}'", placement.id());
                  } else {
                     placementData.markSpawned(placement.id(), existing.getUUID());
                     LogUtil.info(Env.SERVER, "NPCPlacementManager: spawned '{}' at {}, {}, {}", placement.id(), pos.x(), pos.y(), pos.z());
                  }
               }
            }
         } else {
            applyPlacementData(existing, placement, resolvePosition(level, placement));
         }
      }
   }

   @Nullable
   private static Entity findPlacedEntity(ServerLevel level, String placementId) {
      List<Entity> matches = new ArrayList<>();

      for (Entity entity : level.getAllEntities()) {
         if (entity != null && placementId.equals(entity.getPersistentData().getString("dmz_npc_placement_id"))) {
            matches.add(entity);
         }
      }

      if (matches.isEmpty()) {
         return null;
      } else {
         Entity first = matches.get(0);

         for (int i = 1; i < matches.size(); i++) {
            matches.get(i).discard();
            LogUtil.warn(Env.SERVER, "NPCPlacementManager: removed duplicate entity for placement '{}'", placementId);
         }

         return first;
      }
   }

   private static void applyPlacementData(Entity entity, NPCPlacementManager.NPCPlacement placement, NPCPlacementManager.ResolvedPosition pos) {
      entity.moveTo(pos.x(), pos.y(), pos.z(), placement.yaw(), placement.pitch());
      entity.setYHeadRot(placement.yaw());
      entity.setYBodyRot(placement.yaw());
      applyPlacementMetadata(entity, placement);
   }

   private static void applyPlacementMetadata(Entity entity, NPCPlacementManager.NPCPlacement placement) {
      if (entity instanceof Mob mob) {
         mob.setPersistenceRequired();
      }

      entity.getPersistentData().putString("dmz_npc_placement_id", placement.id());
      if (placement.alignment() != null) {
         entity.getPersistentData().putInt("DmzNpcAlignment", Math.max(0, Math.min(100, placement.alignment())));
      }

      if (placement.relationOverride() != null && !placement.relationOverride().isBlank()) {
         entity.getPersistentData().putString("DmzNpcRelationOverride", placement.relationOverride());
      }

      if (entity instanceof QuestNPCEntity questNPC) {
         if (placement.npcId() != null && !placement.npcId().isBlank()) {
            questNPC.setNpcId(placement.npcId());
         }

         questNPC.setNpcModel(placement.model());
         questNPC.setNpcTexture(placement.texture());
      }
   }

   private static NPCPlacementManager.ResolvedPosition resolvePosition(ServerLevel level, NPCPlacementManager.NPCPlacement placement) {
      double x = placement.x();
      double y = placement.y();
      double z = placement.z();
      if (placement.structureId() != null && !placement.structureId().isBlank()) {
         ResourceKey<Structure> structureKey = structureKeyFromId(placement.structureId());
         if (structureKey != null) {
            BlockPos searchFrom = level.getSharedSpawnPos();
            BlockPos structureOrigin = StructureLocator.locateStructure(level, structureKey, searchFrom);
            if (structureOrigin != null) {
               x += (double)structureOrigin.getX();
               y += (double)structureOrigin.getY();
               z += (double)structureOrigin.getZ();
            } else {
               LogUtil.warn(
                  Env.SERVER,
                  "NPCPlacementManager: structure '{}' not found for placement '{}', using raw coordinates",
                  placement.structureId(),
                  placement.id()
               );
            }
         } else {
            LogUtil.warn(Env.SERVER, "NPCPlacementManager: unknown structure '{}' for placement '{}'", placement.structureId(), placement.id());
         }
      }

      if (placement.relativeToSpawn()) {
         BlockPos spawn = level.getSharedSpawnPos();
         x += (double)spawn.getX() + 0.5;
         z += (double)spawn.getZ() + 0.5;
      }

      if (placement.surface()) {
         BlockPos column = BlockPos.containing(x, 0.0, z);
         level.getChunk(column);
         y = (double)level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
      }

      return new NPCPlacementManager.ResolvedPosition(x, y, z);
   }

   private static boolean isPlacementChunkLoaded(ServerLevel level, NPCPlacementManager.ResolvedPosition pos) {
      return level.isLoaded(BlockPos.containing(pos.x(), pos.y(), pos.z()));
   }

   private static void writeDefaultPlacements(Path file) throws IOException {
      JsonObject root = new JsonObject();
      root.addProperty("schema", 1);
      root.add("placements", defaultPlacements());

      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
         GSON.toJson(root, writer);
      }
   }

   private static JsonArray defaultPlacements() {
      JsonArray placements = new JsonArray();
      addMasterInStructure(placements, "master_roshi", "dragonminez:master_roshi", "minecraft:overworld", "roshi_house", 0.0, 0.0, 0.0, true, 225.0F);
      addMasterInStructure(placements, "master_goku", "dragonminez:master_goku", "minecraft:overworld", "goku_house", 0.0, 0.0, 0.0, true, 225.0F);
      addMasterInStructure(placements, "master_karin", "dragonminez:master_karin", "minecraft:overworld", "kamilookout", 0.0, 0.0, 0.0, true, 135.0F);
      addMasterInStructure(placements, "master_dende", "dragonminez:master_dende", "minecraft:overworld", "kamilookout", 0.0, 0.0, 0.0, true, 135.0F);
      addMasterInStructure(placements, "master_popo", "dragonminez:master_popo", "minecraft:overworld", "kamilookout", 0.0, 0.0, 0.0, true, 135.0F);
      addMasterInStructure(placements, "master_gero", "dragonminez:master_gero", "minecraft:overworld", "gero_lab", 0.0, 0.0, 0.0, true, 315.0F);
      addMasterInStructure(placements, "master_guru", "dragonminez:master_guru", "dragonminez:namek", "elder_guru", 0.0, 0.0, 0.0, true, 180.0F);
      addManualMaster(placements, "master_kaiosama", "dragonminez:master_kaiosama", "dragonminez:otherworld", false, 54.5, 190.0, 1082.5, false, 180.0F);
      addManualMaster(placements, "master_enma", "dragonminez:master_enma", "dragonminez:otherworld", false, 0.5, 41.0, 66.5, false, 180.0F);
      addManualMaster(placements, "master_baba", "dragonminez:master_uranai", "dragonminez:otherworld", false, 6.5, 41.0, 53.5, false, 180.0F);
      addManualMaster(placements, "master_toribot", "dragonminez:master_toribot", "dragonminez:otherworld", false, 50.5, 190.0, 1079.5, false, 180.0F);
      addQuestNPC(placements, "npc_bulma", "bulma", "minecraft:overworld", 0.0, 0.0, 0.0, 210.0F, "saga_bulma", "saga_bulma");
      addQuestNPC(placements, "npc_krillin", "krillin", "minecraft:overworld", 0.0, 0.0, 0.0, 210.0F, "saga_vegeta", "saga_krillin");
      addQuestNPC(placements, "npc_yamcha", "yamcha", "minecraft:overworld", 0.0, 0.0, 0.0, 210.0F, "saga_yamcha", "saga_yamcha");
      addQuestNPC(placements, "npc_tien", "tien", "minecraft:overworld", 0.0, 0.0, 0.0, 210.0F, "saga_goku", "saga_tien_early");
      addQuestNPC(placements, "npc_piccolo", "piccolo", "minecraft:overworld", 0.0, 0.0, 0.0, 150.0F, "saga_piccolo", "saga_piccolo");
      addQuestNPC(placements, "npc_gohan", "gohan", "minecraft:overworld", 0.0, 0.0, 0.0, 150.0F, "saga_gohan_mid", "saga_gohan_mid_base");
      addQuestNPC(placements, "npc_vegeta", "vegeta", "minecraft:overworld", 0.0, 0.0, 0.0, 330.0F, "saga_vegeta", "saga_vegeta");
      addQuestNPC(placements, "npc_trunks", "trunks", "minecraft:overworld", 0.0, 0.0, 0.0, 330.0F, "saga_trunks", "saga_ftrunks_base");
      addQuestNPC(placements, "npc_chi_chi", "chi_chi", "minecraft:overworld", 0.0, 0.0, 0.0, 150.0F, "", "");
      addQuestNPC(placements, "npc_videl", "videl", "minecraft:overworld", 0.0, 0.0, 0.0, 150.0F, "saga_videl", "saga_videl");
      addQuestNPC(placements, "npc_shin", "shin", "minecraft:overworld", 0.0, 0.0, 0.0, 180.0F, "saga_shin", "saga_shin");
      addQuestNPC(placements, "npc_namek_elder", "namek_elder", "dragonminez:namek", 0.0, 0.0, 0.0, 180.0F, "", "");
      return placements;
   }

   private static void addManualMaster(
      JsonArray placements, String id, String entity, String dimension, boolean relativeToSpawn, double x, double y, double z, boolean surface, float yaw
   ) {
      JsonObject placement = basePlacement(id, entity, dimension, relativeToSpawn, x, y, z, surface, yaw);
      placement.addProperty("override", true);
      placements.add(placement);
   }

   private static void addMasterInStructure(
      JsonArray placements,
      String id,
      String entity,
      String dimension,
      String structureId,
      double offsetX,
      double offsetY,
      double offsetZ,
      boolean surface,
      float yaw
   ) {
      JsonObject placement = basePlacement(id, entity, dimension, false, offsetX, offsetY, offsetZ, surface, yaw);
      placement.addProperty("structure", structureId);
      placements.add(placement);
   }

   private static void addQuestNPC(
      JsonArray placements, String id, String npcId, String dimension, double x, double y, double z, float yaw, String model, String texture
   ) {
      JsonObject placement = basePlacement(id, "dragonminez:quest_npc", dimension, true, x, y, z, true, yaw);
      placement.addProperty("npc_id", npcId);
      placement.addProperty("model", model);
      placement.addProperty("texture", texture);
      placement.addProperty("enabled", false);
      placements.add(placement);
   }

   private static JsonObject basePlacement(
      String id, String entity, String dimension, boolean relativeToSpawn, double x, double y, double z, boolean surface, float yaw
   ) {
      JsonObject placement = new JsonObject();
      placement.addProperty("id", id);
      placement.addProperty("entity", entity);
      placement.addProperty("dimension", dimension);
      placement.addProperty("x", x);
      placement.addProperty("y", y);
      placement.addProperty("z", z);
      placement.addProperty("yaw", yaw);
      placement.addProperty("pitch", 0.0F);
      placement.addProperty("surface", surface);
      placement.addProperty("relative_to_spawn", relativeToSpawn);
      placement.addProperty("enabled", true);
      placement.addProperty("override", false);
      return placement;
   }

   @Nullable
   private static String getString(JsonObject json, String key, @Nullable String fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
   }

   private static double getDouble(JsonObject json, String key, double fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsDouble() : fallback;
   }

   private static float getFloat(JsonObject json, String key, float fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsFloat() : fallback;
   }

   private static boolean getBoolean(JsonObject json, String key, boolean fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
   }

   @Nullable
   private static Integer getOptionalInt(JsonObject json, String key) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : null;
   }

   private static boolean shouldForceManualSpawn(NPCPlacementManager.NPCPlacement placement) {
      if (!"dragonminez:otherworld".equals(placement.dimension().location().toString())) {
         return false;
      } else {
         String var1 = placement.id();

         return switch (var1) {
            case "master_kaiosama", "master_enma", "master_baba", "master_toribot" -> true;
            default -> false;
         };
      }
   }

   static NPCPlacementManager.MissingTrackedEntityAction resolveMissingTrackedEntity(boolean placementChunkLoaded, boolean overridePlacement) {
      if (!placementChunkLoaded) {
         return NPCPlacementManager.MissingTrackedEntityAction.WAIT_FOR_CHUNK;
      } else {
         return overridePlacement ? NPCPlacementManager.MissingTrackedEntityAction.RESPAWN : NPCPlacementManager.MissingTrackedEntityAction.SKIP;
      }
   }

   @Nullable
   private static ResourceKey<Structure> structureKeyFromId(String structureId) {
      return switch (structureId) {
         case "goku_house" -> DMZStructures.GOKU_HOUSE;
         case "roshi_house" -> DMZStructures.ROSHI_HOUSE;
         case "elder_guru" -> DMZStructures.ELDER_GURU;
         case "timechamber" -> DMZStructures.TIMECHAMBER;
         case "kamilookout" -> DMZStructures.KAMILOOKOUT;
         case "gero_lab" -> DMZStructures.GERO_LAB;
         default -> null;
      };
   }

   static enum MissingTrackedEntityAction {
      WAIT_FOR_CHUNK,
      RESPAWN,
      SKIP;
   }

   private static record NPCPlacement(
      String id,
      String entity,
      ResourceKey<Level> dimension,
      @Nullable String npcId,
      String model,
      String texture,
      @Nullable String structureId,
      double x,
      double y,
      double z,
      float yaw,
      float pitch,
      boolean surface,
      boolean relativeToSpawn,
      boolean enabled,
      boolean override,
      @Nullable Integer alignment,
      @Nullable String relationOverride
   ) {
   }

   private static record ResolvedPosition(double x, double y, double z) {
   }
}
