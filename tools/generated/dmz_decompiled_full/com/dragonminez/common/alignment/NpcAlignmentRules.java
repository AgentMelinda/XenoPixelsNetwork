package com.dragonminez.common.alignment;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.combat.logic.player.TargetHelper;
import com.dragonminez.common.diagnostics.JsonKeys;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

public final class NpcAlignmentRules {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final String NPC_FOLDER = "dragonminez" + File.separator + "npcs";
   private static final String RULES_FILE = "alignment_rules.json";
   private static Map<String, NpcAlignmentRule> rules = defaultRules();
   private static Path loadedFrom = null;
   private static final String RULES_LABEL = "npcs/alignment_rules.json";
   private static final Set<String> ROOT_KEYS = Set.of("npcs");
   private static final Set<String> RULE_KEYS = Set.of("default_relation", "interaction", "hostile_below", "hostile_above", "min_alignment", "max_alignment");
   private static final Set<String> INTERACTION_KEYS = Set.of("min_alignment", "max_alignment");

   private NpcAlignmentRules() {
   }

   public static void load(MinecraftServer server) {
      if (server != null) {
         Path npcDir = server.getWorldPath(LevelResource.ROOT).resolve(NPC_FOLDER);
         Path file = npcDir.resolve("alignment_rules.json");

         try {
            Files.createDirectories(npcDir);
            if (!Files.exists(file)) {
               writeDefaults(file);
            }

            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
               JsonObject root = (JsonObject)GSON.fromJson(reader, JsonObject.class);
               rules = parseRules(root);
               loadedFrom = file;
               LogUtil.info(Env.SERVER, "NpcAlignmentRules: loaded {} rule(s)", rules.size());
            }
         } catch (Exception var8) {
            rules = defaultRules();
            loadedFrom = file;
            LogUtil.error(Env.SERVER, "NpcAlignmentRules: failed to load NPC alignment rules from {}", file, var8);
         }
      }
   }

   @Nullable
   public static NpcAlignmentRule get(String npcId) {
      return npcId != null && !npcId.isBlank() ? rules.get(normalize(npcId)) : null;
   }

   public static Map<String, NpcAlignmentRule> all() {
      return Map.copyOf(rules);
   }

   private static Map<String, NpcAlignmentRule> parseRules(@Nullable JsonObject root) {
      Map<String, NpcAlignmentRule> parsed = defaultRules();
      if (root != null && root.has("npcs") && root.get("npcs").isJsonObject()) {
         JsonLoadReport.clear("npc-alignment");
         JsonKeys.checkObject("npc-alignment", "npcs/alignment_rules.json", "", root, ROOT_KEYS);
         JsonObject npcs = root.getAsJsonObject("npcs");

         for (Entry<String, JsonElement> entry : npcs.entrySet()) {
            if (entry.getValue().isJsonObject()) {
               JsonObject ruleJson = entry.getValue().getAsJsonObject();
               JsonKeys.checkObject("npc-alignment", "npcs/alignment_rules.json", "npcs." + entry.getKey(), ruleJson, RULE_KEYS);
               if (ruleJson.has("interaction") && ruleJson.get("interaction").isJsonObject()) {
                  JsonKeys.checkObject(
                     "npc-alignment",
                     "npcs/alignment_rules.json",
                     "npcs." + entry.getKey() + ".interaction",
                     ruleJson.getAsJsonObject("interaction"),
                     INTERACTION_KEYS
                  );
               }

               NpcAlignmentRule rule = parseRule(entry.getValue().getAsJsonObject());
               if (rule != null) {
                  parsed.put(normalize(entry.getKey()), rule);
               }
            }
         }

         return parsed;
      } else {
         return parsed;
      }
   }

   @Nullable
   private static NpcAlignmentRule parseRule(JsonObject json) {
      TargetHelper.Relation defaultRelation = parseRelation(getString(json, "default_relation", "NEUTRAL"));
      JsonObject interaction = json.has("interaction") && json.get("interaction").isJsonObject() ? json.getAsJsonObject("interaction") : json;
      return new NpcAlignmentRule(
         defaultRelation,
         getInt(interaction, "min_alignment", null),
         getInt(interaction, "max_alignment", null),
         getInt(json, "hostile_below", null),
         getInt(json, "hostile_above", null)
      );
   }

   private static void writeDefaults(Path file) throws IOException {
      try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
         GSON.toJson(defaultRulesJson(), writer);
      }
   }

   private static JsonObject defaultRulesJson() {
      JsonObject root = new JsonObject();
      JsonObject npcs = new JsonObject();
      addRule(npcs, "goku", TargetHelper.Relation.FRIENDLY, 61, null, 25, null);
      addRule(npcs, "roshi", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "karin", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "guru", TargetHelper.Relation.FRIENDLY, 61, null, 25, null);
      addRule(npcs, "dende", TargetHelper.Relation.FRIENDLY, 61, null, 25, null);
      addRule(npcs, "popo", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "kingkai", TargetHelper.Relation.FRIENDLY, 61, null, 25, null);
      addRule(npcs, "gero", TargetHelper.Relation.NEUTRAL, null, 60, null, null);
      addRule(npcs, "enma", TargetHelper.Relation.NEUTRAL, null, null, null, null);
      addRule(npcs, "baba", TargetHelper.Relation.NEUTRAL, null, null, null, null);
      addRule(npcs, "toribot", TargetHelper.Relation.NEUTRAL, null, null, null, null);
      addRule(npcs, "bulma", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "krillin", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "yamcha", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "tien", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "chiaotzu", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "gohan", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "trunks", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "chi_chi", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "videl", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "namek_elder", TargetHelper.Relation.FRIENDLY, 61, null, 25, null);
      addRule(npcs, "shin", TargetHelper.Relation.FRIENDLY, 41, null, 25, null);
      addRule(npcs, "piccolo", TargetHelper.Relation.NEUTRAL, 41, null, 20, null);
      addRule(npcs, "vegeta", TargetHelper.Relation.NEUTRAL, null, null, null, null);
      root.add("npcs", npcs);
      return root;
   }

   private static Map<String, NpcAlignmentRule> defaultRules() {
      Map<String, NpcAlignmentRule> parsed = new LinkedHashMap<>();
      JsonObject npcs = defaultRulesJson().getAsJsonObject("npcs");

      for (Entry<String, JsonElement> entry : npcs.entrySet()) {
         if (entry.getValue().isJsonObject()) {
            NpcAlignmentRule rule = parseRule(entry.getValue().getAsJsonObject());
            if (rule != null) {
               parsed.put(normalize(entry.getKey()), rule);
            }
         }
      }

      return parsed;
   }

   private static void addRule(JsonObject npcs, String id, TargetHelper.Relation relation, Integer min, Integer max, Integer hostileBelow, Integer hostileAbove) {
      JsonObject rule = new JsonObject();
      rule.addProperty("default_relation", relation.name());
      JsonObject interaction = new JsonObject();
      if (min != null) {
         interaction.addProperty("min_alignment", min);
      }

      if (max != null) {
         interaction.addProperty("max_alignment", max);
      }

      if (!interaction.entrySet().isEmpty()) {
         rule.add("interaction", interaction);
      }

      if (hostileBelow != null) {
         rule.addProperty("hostile_below", hostileBelow);
      }

      if (hostileAbove != null) {
         rule.addProperty("hostile_above", hostileAbove);
      }

      npcs.add(id, rule);
   }

   private static String normalize(String id) {
      String normalized = id.trim().toLowerCase();
      if (normalized.contains(":")) {
         normalized = normalized.substring(normalized.indexOf(58) + 1);
      }

      return normalized;
   }

   private static String getString(JsonObject json, String key, String fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : fallback;
   }

   @Nullable
   private static Integer getInt(JsonObject json, String key, @Nullable Integer fallback) {
      return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsInt() : fallback;
   }

   private static TargetHelper.Relation parseRelation(String raw) {
      if (raw != null && !raw.isBlank()) {
         try {
            return TargetHelper.Relation.valueOf(raw.trim().toUpperCase());
         } catch (IllegalArgumentException var2) {
            return TargetHelper.Relation.NEUTRAL;
         }
      } else {
         return TargetHelper.Relation.NEUTRAL;
      }
   }
}
