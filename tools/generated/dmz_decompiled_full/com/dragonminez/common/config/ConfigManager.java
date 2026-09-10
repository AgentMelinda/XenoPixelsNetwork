package com.dragonminez.common.config;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.client.animation.AnimationCache;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.dragonminez.common.init.MainEntities;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.world.entity.EntityType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ConfigManager {
   public static final String CONFIG_VERSION = "2.1.3";
   public static final String CLIENT_ONLY_CONFIG = "general-user";
   private static final String PREVIOUS_CONFIGS_ROOT = "/data/dragonminez/previousConfigs/";
   private static final String OLD_BACKUP_DIR = "oldBackup";
   private static final double DEFENSE_SCALING_FOLD = 0.12;
   private static final double DEFENSE_SCALING_FOLD_VERSION = 21.2;
   private static final Gson GSON = new GsonBuilder()
      .setPrettyPrinting()
      .setLenient()
      .registerTypeAdapter(RaceCharacterConfig.FormSkillCost.class, new RaceCharacterConfig.FormSkillCost.Adapter())
      .create();
   private static final ConfigLoader LOADER = new ConfigLoader(GSON);
   private static final DefaultFormsFactory FORMS_FACTORY = new DefaultFormsFactory();
   private static final Path CONFIG_DIR = FMLPaths.CONFIGDIR.get().resolve("dragonminez");
   private static final Path STACK_FORMS_DIR = CONFIG_DIR.resolve("forms");
   private static final Path RACES_DIR = CONFIG_DIR.resolve("races");
   private static final String[] DEFAULT_RACES = new String[]{"human", "saiyan", "namekian", "frostdemon", "bioandroid", "majin"};
   private static final Set<String> RACES_WITH_GENDER = new HashSet<>(Arrays.asList("human", "saiyan", "majin"));
   private static final Map<String, RaceStatsConfig> RACE_STATS = new HashMap<>();
   private static final Map<String, RaceCharacterConfig> RACE_CHARACTER = new HashMap<>();
   private static final Map<String, Map<String, FormConfig>> RACE_FORMS = new HashMap<>();
   private static final List<String> LOADED_RACES = new ArrayList<>();
   private static final List<String> CACHED_CONFIG_FILES = new ArrayList<>();
   private static Map<String, FormConfig> STACK_FORMS = new HashMap<>();
   private static GeneralServerConfig SERVER_SYNCED_GENERAL_SERVER;
   private static SkillsConfig SERVER_SYNCED_SKILLS;
   private static TechniqueConfig SERVER_SYNCED_TECHNIQUES;
   private static CombatConfig SERVER_SYNCED_COMBAT;
   private static TrainingConfig SERVER_SYNCED_TRAINING;
   private static Map<String, Map<String, FormConfig>> SERVER_SYNCED_FORMS;
   private static Map<String, RaceStatsConfig> SERVER_SYNCED_STATS;
   private static Map<String, RaceCharacterConfig> SERVER_SYNCED_CHARACTER;
   private static Map<String, FormConfig> SERVER_SYNCED_STACK_FORMS;
   private static EntitiesConfig SERVER_SYNCED_ENTITIES;
   private static boolean serverSyncActive = false;
   private static GeneralUserConfig userConfig;
   private static GeneralServerConfig serverConfig;
   private static CombatConfig combatConfig;
   private static TrainingConfig trainingConfig;
   private static SkillsConfig skillsConfig;
   private static TechniqueConfig techniqueConfig;
   private static EntitiesConfig entitiesConfig;

   public static void initialize() {
      LogUtil.info(Env.COMMON, "Initializing DragonMineZ configuration system...");
      JsonLoadReport.clear("config");

      try {
         Files.createDirectories(CONFIG_DIR);
         Files.createDirectories(RACES_DIR);
         loadGeneralConfigs();
         loadAllRaces();
         createOrLoadStackForms(true);
         LogUtil.info(Env.COMMON, "Configuration system initialized successfully");
         LogUtil.info(Env.COMMON, "Loaded races: {}", LOADED_RACES);
      } catch (IOException var1) {
         LogUtil.error(Env.COMMON, "Error initializing configuration system: {}", var1.getMessage());
      }
   }

   public static void reload() {
      LogUtil.info(Env.COMMON, "Reloading DragonMineZ configuration system...");
      JsonLoadReport.clear("config");

      try {
         RACE_STATS.clear();
         RACE_CHARACTER.clear();
         RACE_FORMS.clear();
         LOADED_RACES.clear();
         STACK_FORMS.clear();
         CACHED_CONFIG_FILES.clear();
         AnimationCache.clear();
         loadGeneralConfigs();
         loadAllRaces();
         createOrLoadStackForms(true);
         LogUtil.info(Env.COMMON, "Configuration system reloaded successfully");
      } catch (IOException var1) {
         LogUtil.error(Env.COMMON, "Error reloading configuration system: {}", var1.getMessage());
      }
   }

   private static String peekConfigVersion(Path path) {
      if (!Files.exists(path)) {
         return null;
      } else {
         try {
            JsonElement parsed = JsonParser.parseString(Files.readString(path));
            if (parsed != null && parsed.isJsonObject()) {
               JsonObject obj = parsed.getAsJsonObject();
               return obj.has("configVersion") && obj.get("configVersion").isJsonPrimitive() ? obj.get("configVersion").getAsString() : "";
            } else {
               return "";
            }
         } catch (Exception var3) {
            return "";
         }
      }
   }

   private static Integer[] parseSemver(String version) {
      if (version != null && !version.isBlank()) {
         String v = version.trim();
         int suffixRank = 0;
         int end = v.length();

         while (end > 0 && Character.isLetter(v.charAt(end - 1))) {
            end--;
         }

         if (end < v.length()) {
            suffixRank = Character.toLowerCase(v.charAt(end)) - 'a' + 1;
            v = v.substring(0, end);
         }

         String[] parts = v.split("\\.");
         if (parts.length != 3) {
            return null;
         } else {
            Integer[] comps = new Integer[4];

            for (int i = 0; i < 3; i++) {
               try {
                  comps[i] = Integer.parseInt(parts[i].trim());
               } catch (NumberFormatException var8) {
                  return null;
               }
            }

            comps[3] = suffixRank;
            return comps;
         }
      } else {
         return null;
      }
   }

   private static int compareSemver(Integer[] a, Integer[] b) {
      int n = Math.min(a.length, b.length);

      for (int i = 0; i < n; i++) {
         int cmp = Integer.compare(a[i], b[i]);
         if (cmp != 0) {
            return cmp;
         }
      }

      return 0;
   }

   private static boolean isOutdated(String storedVersion) {
      Integer[] stored = parseSemver(storedVersion);
      if (stored == null) {
         return true;
      } else {
         Integer[] current = parseSemver("2.1.3");
         return compareSemver(stored, current) < 0;
      }
   }

   private static boolean isLegacyPreFoldVersion(String storedVersion) {
      if (storedVersion == null || storedVersion.isBlank()) {
         return false;
      } else if (parseSemver(storedVersion) != null) {
         return false;
      } else {
         try {
            double legacy = Double.parseDouble(storedVersion.trim());
            return legacy >= 0.0 && legacy < 21.2;
         } catch (NumberFormatException var3) {
            return false;
         }
      }
   }

   private static Double defaultDefenseScaling(RaceStatsConfig config, String className) {
      RaceStatsConfig.ClassStats classStats = config.getClasses().get(className);
      if (classStats == null) {
         return null;
      } else {
         RaceStatsConfig.StatScaling scaling = classStats.getStatScaling();
         return scaling != null ? scaling.getDefenseScaling() : null;
      }
   }

   private static String relativeName(Path path) {
      try {
         return CONFIG_DIR.relativize(path).toString().replace('\\', '/');
      } catch (Exception var2) {
         return path.getFileName().toString();
      }
   }

   private static void backupOldConfig(Path configPath) {
      if (Files.exists(configPath)) {
         try {
            Path relative = CONFIG_DIR.relativize(configPath);
            Path backupPath = CONFIG_DIR.resolve("oldBackup").resolve(relative);
            Files.createDirectories(backupPath.getParent());
            Files.move(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
            LogUtil.info(Env.COMMON, "Obsolete config backed up: {}", CONFIG_DIR.relativize(backupPath));
         } catch (Exception var3) {
            LogUtil.error(Env.COMMON, "Failed to backup old config '{}': {}", configPath.getFileName(), var3);
         }
      }
   }

   private static JsonObject loadBaselineObject(Path configPath) {
      Path relative;
      try {
         relative = CONFIG_DIR.relativize(configPath);
      } catch (Exception var7) {
         return null;
      }

      String resource = "/data/dragonminez/previousConfigs/" + relative.toString().replace('\\', '/');

      try {
         JsonObject var5;
         try (InputStream in = ConfigManager.class.getResourceAsStream(resource)) {
            if (in == null) {
               return null;
            }

            JsonElement parsed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            var5 = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
         }

         return var5;
      } catch (Exception var9) {
         return null;
      }
   }

   private static <T> T loadAndValidate(
      Path path,
      Class<T> clazz,
      Supplier<T> defaultProvider,
      Function<T, String> versionGetter,
      BiConsumer<T, String> versionSetter,
      String currentVersion,
      String templateName
   ) {
      boolean overwrite = false;
      String reason = "";
      T config = null;
      if (Files.exists(path)) {
         try {
            config = LOADER.loadConfig(path, clazz);
            String version = versionGetter.apply(config);
            if (isOutdated(version)) {
               reason = version != null && !version.isBlank() ? "Outdated version (" + version + " < " + currentVersion + ")" : "Missing config version";
               overwrite = true;
            }
         } catch (Exception var17) {
            reason = "Parsing error: " + var17.getMessage();
            overwrite = true;
            JsonLoadReport.error(
               "config",
               relativeName(path),
               "Malformed JSON: "
                  + JsonLoadReport.rootCause(var17)
                  + " — running on defaults for now; your file was left untouched, fix the syntax and /dmzreload to apply your edits"
            );
         }
      } else {
         reason = "File not found";
         overwrite = true;
         if (templateName != null) {
            try {
               LOADER.saveDefaultFromTemplate(path, templateName);
               config = LOADER.loadConfig(path, clazz);
               if (isOutdated(versionGetter.apply(config))) {
                  overwrite = true;
               }
            } catch (Exception var16) {
               reason = "Template loading failed: " + var16.getMessage();
            }
         }
      }

      if (overwrite) {
         boolean parsingError = reason.startsWith("Parsing error");
         if (parsingError) {
            LogUtil.warn(
               Env.COMMON,
               String.format("%s has malformed JSON; using defaults this session and leaving your file untouched. Reason: %s", path.getFileName(), reason)
            );
            return defaultProvider.get();
         }

         String oldRawJson = null;
         JsonObject baseline = loadBaselineObject(path);
         if (Files.exists(path)) {
            try {
               oldRawJson = Files.readString(path);
            } catch (IOException var15) {
               LogUtil.error(Env.COMMON, "Could not read old config '{}' for value preservation: {}", path.getFileName(), var15.getMessage());
            }

            backupOldConfig(path);
         }

         config = defaultProvider.get();

         try {
            versionSetter.accept(config, currentVersion);
            if (oldRawJson != null) {
               config = mergePreservedValues(oldRawJson, config, clazz, currentVersion, versionSetter, baseline);
            }

            LogUtil.warn(Env.COMMON, String.format("Regenerating %s. Reason: %s", path.getFileName(), reason));
            LOADER.saveConfig(path, config);
         } catch (Exception var14) {
            LogUtil.error(Env.COMMON, "Error saving regenerated config: " + var14.getMessage());
         }
      }

      return config != null ? config : defaultProvider.get();
   }

   private static <T> T mergePreservedValues(
      String oldRawJson, T defaultConfig, Class<T> clazz, String currentVersion, BiConsumer<T, String> versionSetter, JsonObject baseline
   ) {
      try {
         JsonElement oldParsed = JsonParser.parseString(oldRawJson);
         if (oldParsed != null && oldParsed.isJsonObject()) {
            JsonElement newTree = GSON.toJsonTree(defaultConfig);
            if (newTree != null && newTree.isJsonObject()) {
               JsonObject oldObj = oldParsed.getAsJsonObject();
               JsonObject newObj = newTree.getAsJsonObject();
               int preserved = mergeMatchingValues(oldObj, newObj, baseline, clazz);
               T merged = (T)GSON.fromJson(newObj, clazz);
               if (merged == null) {
                  return defaultConfig;
               } else {
                  versionSetter.accept(merged, currentVersion);
                  if (preserved > 0) {
                     LogUtil.info(Env.COMMON, "Preserved {} user-modified value(s) from the old config", preserved);
                  }

                  return merged;
               }
            } else {
               return defaultConfig;
            }
         } else {
            return defaultConfig;
         }
      } catch (Exception var12) {
         LogUtil.error(Env.COMMON, "Could not preserve old config values, falling back to defaults: {}", var12.getMessage());
         return defaultConfig;
      }
   }

   private static JsonElement baselineChild(JsonObject baseline, String key) {
      return baseline != null && baseline.has(key) && !baseline.get(key).isJsonNull() ? baseline.get(key) : null;
   }

   private static boolean shouldPreserve(JsonElement oldVal, JsonElement newVal, JsonElement baseVal) {
      return baseVal != null ? !valuesEqual(oldVal, baseVal) : !valuesEqual(oldVal, newVal);
   }

   private static int mergeMatchingValues(JsonObject oldObj, JsonObject newObj, JsonObject baseline, Class<?> type) {
      int count = 0;

      for (String key : new ArrayList(newObj.keySet())) {
         if (!key.equals("configVersion") && oldObj.has(key)) {
            JsonElement oldVal = oldObj.get(key);
            JsonElement newVal = newObj.get(key);
            if (!oldVal.isJsonNull()) {
               JsonElement baseVal = baselineChild(baseline, key);
               Field field = findField(type, key);
               Class<?> fieldType = field != null ? field.getType() : null;
               if (field == null || !field.isAnnotationPresent(ConfigNonPreservable.class)) {
                  if (fieldType != null && isMapType(fieldType) && oldVal.isJsonObject() && newVal.isJsonObject()) {
                     JsonObject baseMap = baseVal != null && baseVal.isJsonObject() ? baseVal.getAsJsonObject() : null;
                     count += mergeMapValues(oldVal.getAsJsonObject(), newVal.getAsJsonObject(), baseMap, mapValueClass(field));
                  } else if (fieldType != null && isCollectionOrArray(fieldType)) {
                     if (oldVal.isJsonArray() && shouldPreserve(oldVal, newVal, baseVal)) {
                        newObj.add(key, stripNullElements(oldVal.getAsJsonArray()));
                        count++;
                     }
                  } else if (oldVal.isJsonObject() && newVal.isJsonObject()) {
                     JsonObject baseObj = baseVal != null && baseVal.isJsonObject() ? baseVal.getAsJsonObject() : null;
                     count += mergeMatchingValues(oldVal.getAsJsonObject(), newVal.getAsJsonObject(), baseObj, fieldType);
                  } else if (isValueCompatible(oldVal, newVal, fieldType) && shouldPreserve(oldVal, newVal, baseVal)) {
                     newObj.add(key, oldVal);
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   private static int mergeMapValues(JsonObject oldMap, JsonObject newMap, JsonObject baseMap, Class<?> valueType) {
      int count = 0;

      for (String key : new ArrayList(newMap.keySet())) {
         if (!oldMap.has(key)) {
            if (baseMap != null && baseMap.has(key)) {
               newMap.remove(key);
               count++;
            }
         } else {
            JsonElement oldVal = oldMap.get(key);
            JsonElement newVal = newMap.get(key);
            if (!oldVal.isJsonNull()) {
               JsonElement baseVal = baselineChild(baseMap, key);
               if (oldVal.isJsonObject() && newVal.isJsonObject()) {
                  if (valueType != null && !isMapType(valueType) && !isCollectionOrArray(valueType)) {
                     JsonObject baseObj = baseVal != null && baseVal.isJsonObject() ? baseVal.getAsJsonObject() : null;
                     count += mergeMatchingValues(oldVal.getAsJsonObject(), newVal.getAsJsonObject(), baseObj, valueType);
                  } else if (shouldPreserve(oldVal, newVal, baseVal)) {
                     newMap.add(key, oldVal);
                     count++;
                  }
               } else if (oldVal.isJsonArray()) {
                  if (shouldPreserve(oldVal, newVal, baseVal)) {
                     newMap.add(key, stripNullElements(oldVal.getAsJsonArray()));
                     count++;
                  }
               } else if (isValueCompatible(oldVal, newVal, valueType) && shouldPreserve(oldVal, newVal, baseVal)) {
                  newMap.add(key, oldVal);
                  count++;
               }
            }
         }
      }

      for (String keyx : oldMap.keySet()) {
         if (!newMap.has(keyx)) {
            newMap.add(keyx, oldMap.get(keyx));
            count++;
         }
      }

      return count;
   }

   private static boolean isValueCompatible(JsonElement oldVal, JsonElement newVal, Class<?> type) {
      if (!oldVal.isJsonPrimitive()) {
         return false;
      } else {
         JsonPrimitive oldPrim = oldVal.getAsJsonPrimitive();
         if (type == null) {
            if (newVal != null && newVal.isJsonPrimitive()) {
               JsonPrimitive newPrim = newVal.getAsJsonPrimitive();
               if (oldPrim.isBoolean() || newPrim.isBoolean()) {
                  return oldPrim.isBoolean() && newPrim.isBoolean();
               } else if (oldPrim.isString() || newPrim.isString()) {
                  return oldPrim.isString() && newPrim.isString();
               } else {
                  return oldPrim.isNumber() && newPrim.isNumber() ? isIntegralLiteral(oldPrim) == isIntegralLiteral(newPrim) : false;
               }
            } else {
               return false;
            }
         } else if (type == boolean.class || type == Boolean.class) {
            return oldPrim.isBoolean();
         } else if (type == String.class || type == char.class || type == Character.class || type.isEnum()) {
            return oldPrim.isString();
         } else if (isIntegralType(type)) {
            return oldPrim.isNumber() && isIntegralLiteral(oldPrim);
         } else {
            return isDecimalType(type) ? oldPrim.isNumber() : false;
         }
      }
   }

   private static boolean valuesEqual(JsonElement oldVal, JsonElement newVal) {
      if (newVal == null) {
         return false;
      } else {
         if (oldVal.isJsonPrimitive() && newVal.isJsonPrimitive()) {
            JsonPrimitive op = oldVal.getAsJsonPrimitive();
            JsonPrimitive np = newVal.getAsJsonPrimitive();
            if (op.isNumber() && np.isNumber()) {
               try {
                  return op.getAsBigDecimal().compareTo(np.getAsBigDecimal()) == 0;
               } catch (NumberFormatException var5) {
                  return op.getAsString().equals(np.getAsString());
               }
            }
         }

         return oldVal.equals(newVal);
      }
   }

   private static boolean isIntegralLiteral(JsonPrimitive prim) {
      if (!prim.isNumber()) {
         return false;
      } else {
         String s = prim.getAsString();
         return s.indexOf(46) < 0 && s.indexOf(101) < 0 && s.indexOf(69) < 0;
      }
   }

   private static boolean isIntegralType(Class<?> type) {
      return type == int.class
         || type == Integer.class
         || type == long.class
         || type == Long.class
         || type == short.class
         || type == Short.class
         || type == byte.class
         || type == Byte.class
         || BigInteger.class.isAssignableFrom(type);
   }

   private static boolean isDecimalType(Class<?> type) {
      return type == double.class || type == Double.class || type == float.class || type == Float.class || BigDecimal.class.isAssignableFrom(type);
   }

   private static boolean isMapType(Class<?> type) {
      return Map.class.isAssignableFrom(type);
   }

   private static boolean isCollectionOrArray(Class<?> type) {
      return type.isArray() || Collection.class.isAssignableFrom(type);
   }

   private static JsonArray stripNullElements(JsonArray array) {
      JsonArray cleaned = new JsonArray();

      for (JsonElement element : array) {
         if (!element.isJsonNull()) {
            cleaned.add(element);
         }
      }

      return cleaned;
   }

   private static Field findField(Class<?> type, String name) {
      if (type == null) {
         return null;
      } else {
         for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
               if (!Modifier.isStatic(f.getModifiers()) && !f.isSynthetic() && f.getName().equals(name)) {
                  return f;
               }
            }
         }

         return null;
      }
   }

   private static Class<?> mapValueClass(Field field) {
      try {
         if (field.getGenericType() instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (args.length == 2) {
               Type var5 = args[1];
               if (var5 instanceof Class) {
                  return (Class<?>)var5;
               }
            }
         }
      } catch (Exception var6) {
      }

      return null;
   }

   private static FormConfig regenerateOutdatedForm(Path formFilePath, FormConfig defaultFormConfig, String label) {
      LogUtil.warn(Env.COMMON, "Regenerating form '{}'. Reason: Outdated version", label);
      String oldRaw = null;
      if (Files.exists(formFilePath)) {
         try {
            oldRaw = Files.readString(formFilePath);
         } catch (IOException var8) {
            LogUtil.error(Env.COMMON, "Could not read old form '{}' for value preservation: {}", formFilePath.getFileName(), var8.getMessage());
         }
      }

      JsonObject baseline = loadBaselineObject(formFilePath);
      backupOldConfig(formFilePath);
      defaultFormConfig.setConfigVersion("2.1.3");
      FormConfig result = oldRaw != null
         ? mergePreservedValues(oldRaw, defaultFormConfig, FormConfig.class, "2.1.3", FormConfig::setConfigVersion, baseline)
         : defaultFormConfig;

      try {
         LOADER.saveConfig(formFilePath, result);
      } catch (Exception var7) {
         LogUtil.error(Env.COMMON, "Failed to save regenerated form '{}': {}", formFilePath.getFileName(), var7.getMessage());
      }

      return result;
   }

   private static void upgradeUserFormFiles(Path formsDir, Map<String, FormConfig> defaultForms) {
      if (Files.exists(formsDir)) {
         Set<String> defaults = new HashSet<>();

         for (FormConfig form : defaultForms.values()) {
            if (form != null) {
               defaults.add(form.getGroupName().toLowerCase());
            }
         }

         try (Stream<Path> stream = Files.list(formsDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).filter(p -> !p.getFileName().toString().toLowerCase().startsWith("old_")).forEach(p -> {
               try {
                  FormConfig existing = LOADER.loadConfig(p, FormConfig.class);
                  if (existing == null || defaults.contains(existing.getGroupName().toLowerCase())) {
                     return;
                  }

                  if (isOutdated(existing.getConfigVersion())) {
                     LogUtil.warn(Env.COMMON, "Regenerating user form '{}'. Reason: Outdated version", p.getFileName());
                     backupOldConfig(p);
                     existing.setConfigVersion("2.1.3");
                     LOADER.saveConfig(p, existing);
                  }
               } catch (Exception var3) {
                  LogUtil.error(Env.COMMON, "Failed to upgrade user form '{}': {}", p.getFileName(), var3.getMessage());
                  JsonLoadReport.error("config", relativeName(p), "Form file failed to upgrade: " + JsonLoadReport.rootCause(var3));
               }
            });
         } catch (IOException var8) {
            LogUtil.error(Env.COMMON, "Failed to scan user forms in '{}': {}", formsDir, var8.getMessage());
         }
      }
   }

   private static void backupRenamedDefaultForms(Path formsDir, Map<String, FormConfig> defaultForms) {
      if (Files.exists(formsDir) && !defaultForms.isEmpty()) {
         Set<String> defaults = new HashSet<>();

         for (FormConfig form : defaultForms.values()) {
            if (form != null && form.getGroupName() != null) {
               defaults.add(form.getGroupName().toLowerCase());
            }
         }

         try (Stream<Path> stream = Files.list(formsDir)) {
            stream.filter(p -> p.toString().endsWith(".json")).filter(p -> !p.getFileName().toString().toLowerCase().startsWith("old_")).forEach(p -> {
               try {
                  FormConfig existing = LOADER.loadConfig(p, FormConfig.class);
                  if (existing == null || existing.getGroupName() == null) {
                     return;
                  }

                  String group = existing.getGroupName().toLowerCase();
                  if (defaults.contains(group) || !defaults.contains(group + "s")) {
                     return;
                  }

                  if (!isOutdated(existing.getConfigVersion())) {
                     return;
                  }

                  LogUtil.warn(Env.COMMON, "Backing up obsolete renamed form file '{}' (group '{}' renamed to '{}s')", p.getFileName(), group, group);
                  backupOldConfig(p);
               } catch (Exception var4x) {
                  LogUtil.error(Env.COMMON, "Failed to inspect form file '{}' for legacy rename: {}", p.getFileName(), var4x.getMessage());
               }
            });
         } catch (IOException var8) {
            LogUtil.error(Env.COMMON, "Failed to scan forms for legacy renames in '{}': {}", formsDir, var8.getMessage());
         }
      }
   }

   private static void loadGeneralConfigs() {
      userConfig = loadAndValidate(
         CONFIG_DIR.resolve("general-user.json"),
         GeneralUserConfig.class,
         GeneralUserConfig::new,
         GeneralUserConfig::getConfigVersion,
         GeneralUserConfig::setConfigVersion,
         "2.1.3",
         null
      );
      serverConfig = loadAndValidate(
         CONFIG_DIR.resolve("general-server.json"),
         GeneralServerConfig.class,
         GeneralServerConfig::new,
         GeneralServerConfig::getConfigVersion,
         GeneralServerConfig::setConfigVersion,
         "2.1.3",
         null
      );
      combatConfig = loadAndValidate(
         CONFIG_DIR.resolve("combat.json"),
         CombatConfig.class,
         CombatConfig::new,
         CombatConfig::getConfigVersion,
         CombatConfig::setConfigVersion,
         "2.1.3",
         null
      );
      trainingConfig = loadAndValidate(
         CONFIG_DIR.resolve("training.json"),
         TrainingConfig.class,
         TrainingConfig::new,
         TrainingConfig::getConfigVersion,
         TrainingConfig::setConfigVersion,
         "2.1.3",
         null
      );
      skillsConfig = loadAndValidate(
         CONFIG_DIR.resolve("skills.json"),
         SkillsConfig.class,
         SkillsConfig::new,
         SkillsConfig::getConfigVersion,
         SkillsConfig::setConfigVersion,
         "2.1.3",
         null
      );
      techniqueConfig = loadAndValidate(
         CONFIG_DIR.resolve("techniques.json"),
         TechniqueConfig.class,
         TechniqueConfig::new,
         TechniqueConfig::getConfigVersion,
         TechniqueConfig::setConfigVersion,
         "2.1.3",
         null
      );
      entitiesConfig = loadAndValidate(
         CONFIG_DIR.resolve("entities.json"),
         EntitiesConfig.class,
         ConfigManager::createDefaultEntitiesConfig,
         EntitiesConfig::getConfigVersion,
         EntitiesConfig::setConfigVersion,
         "2.1.3",
         null
      );
   }

   private static void createOrLoadRace(String raceName, boolean isDefault) throws IOException {
      Path racePath = RACES_DIR.resolve(raceName);
      Files.createDirectories(racePath);
      Path formsPath = racePath.resolve("forms");
      Files.createDirectories(formsPath);
      RaceCharacterConfig characterConfig = loadAndValidate(
         racePath.resolve("character.json"),
         RaceCharacterConfig.class,
         () -> createDefaultCharacterConfig(raceName, isDefault),
         RaceCharacterConfig::getConfigVersion,
         RaceCharacterConfig::setConfigVersion,
         "2.1.3",
         null
      );
      if (skillsConfig != null && characterConfig.normalizeFormSkillKeys(skillsConfig.getFormSkills())) {
         LogUtil.warn(Env.COMMON, "Normalized legacy form-skill keys in character.json for race '{}'", raceName);

         try {
            LOADER.saveConfig(racePath.resolve("character.json"), characterConfig);
         } catch (Exception var19) {
            LogUtil.error(Env.COMMON, "Failed to save normalized character.json for race '{}': {}", raceName, var19.getMessage());
         }
      }

      Path statsPath = racePath.resolve("stats.json");
      String previousStatsVersion = peekConfigVersion(statsPath);
      RaceStatsConfig statsConfig = loadAndValidate(
         statsPath,
         RaceStatsConfig.class,
         ConfigManager::createDefaultStatsConfig,
         RaceStatsConfig::getConfigVersion,
         RaceStatsConfig::setConfigVersion,
         "2.1.3",
         null
      );
      if (isLegacyPreFoldVersion(previousStatsVersion)) {
         RaceStatsConfig newDefaults = createDefaultStatsConfig();
         boolean folded = false;

         for (Entry<String, RaceStatsConfig.ClassStats> entry : statsConfig.getClasses().entrySet()) {
            RaceStatsConfig.StatScaling scaling = entry.getValue().getStatScaling();
            if (scaling != null && scaling.getDefenseScaling() != null) {
               Double newDefault = defaultDefenseScaling(newDefaults, entry.getKey());
               if (newDefault == null || !newDefault.equals(scaling.getDefenseScaling())) {
                  scaling.setDefenseScaling(scaling.getDefenseScaling() * 0.12);
                  folded = true;
               }
            }
         }

         if (folded) {
            try {
               LOADER.saveConfig(statsPath, statsConfig);
            } catch (Exception var18) {
               LogUtil.error(Env.COMMON, "Failed to save migrated stats.json for race '{}': {}", raceName, var18.getMessage());
            }

            LogUtil.warn(Env.COMMON, "Migrated user-modified DEF_scaling to flat-defense units for race '{}'", raceName);
         }
      }

      Map<String, FormConfig> raceForms = new HashMap<>();
      if (isDefault) {
         FORMS_FACTORY.createDefaultFormsForRace(raceName, formsPath, raceForms);
      }

      if (isDefault) {
         backupRenamedDefaultForms(formsPath, raceForms);
      }

      upgradeUserFormFiles(formsPath, raceForms);
      Map<String, FormConfig> userDiskForms = LOADER.loadRaceForms(raceName, formsPath);
      if (!isDefault) {
         raceForms.putAll(userDiskForms);
      } else {
         for (Entry<String, FormConfig> defaultEntry : raceForms.entrySet()) {
            String groupKey = defaultEntry.getKey().toLowerCase();
            FormConfig defaultFormConfig = defaultEntry.getValue();
            Path formFilePath = formsPath.resolve(defaultEntry.getKey() + ".json");
            if (userDiskForms.containsKey(groupKey)) {
               FormConfig userConfig = userDiskForms.get(groupKey);
               if (isOutdated(userConfig.getConfigVersion())) {
                  FormConfig regenerated = regenerateOutdatedForm(formFilePath, defaultFormConfig, defaultEntry.getKey() + "' for race '" + raceName);
                  raceForms.put(groupKey, regenerated);
               } else {
                  raceForms.put(groupKey, userConfig);
               }
            } else {
               defaultFormConfig.setConfigVersion("2.1.3");
               if (!Files.exists(formFilePath)) {
                  try {
                     LOADER.saveConfig(formFilePath, defaultFormConfig);
                  } catch (Exception var17) {
                  }
               }
            }
         }

         userDiskForms.forEach(raceForms::putIfAbsent);
      }

      RACE_FORMS.put(raceName.toLowerCase(), raceForms);
      RACE_CHARACTER.put(raceName.toLowerCase(), characterConfig);
      RACE_STATS.put(raceName.toLowerCase(), statsConfig);
      LOADED_RACES.add(raceName);
   }

   private static void createOrLoadStackForms(boolean isDefault) throws IOException {
      Files.createDirectories(STACK_FORMS_DIR);
      Map<String, FormConfig> finalStackForms = new HashMap<>();
      if (isDefault) {
         FORMS_FACTORY.createDefaultStackForms(STACK_FORMS_DIR, finalStackForms);
      }

      if (isDefault) {
         backupRenamedDefaultForms(STACK_FORMS_DIR, finalStackForms);
      }

      upgradeUserFormFiles(STACK_FORMS_DIR, finalStackForms);
      Map<String, FormConfig> userDiskForms = LOADER.loadStackForms(STACK_FORMS_DIR);
      if (isDefault) {
         for (Entry<String, FormConfig> defaultEntry : finalStackForms.entrySet()) {
            String groupKey = defaultEntry.getKey().toLowerCase();
            FormConfig defaultFormConfig = defaultEntry.getValue();
            Path formFilePath = STACK_FORMS_DIR.resolve(defaultEntry.getKey() + ".json");
            if (userDiskForms.containsKey(groupKey)) {
               FormConfig userConfig = userDiskForms.get(groupKey);
               if (isOutdated(userConfig.getConfigVersion())) {
                  FormConfig regenerated = regenerateOutdatedForm(formFilePath, defaultFormConfig, defaultEntry.getKey());
                  finalStackForms.put(groupKey, regenerated);
               } else {
                  finalStackForms.put(groupKey, userConfig);
               }
            } else {
               defaultFormConfig.setConfigVersion("2.1.3");
               if (!Files.exists(formFilePath)) {
                  try {
                     LOADER.saveConfig(formFilePath, defaultFormConfig);
                  } catch (Exception var10) {
                  }
               }
            }
         }

         userDiskForms.forEach(finalStackForms::putIfAbsent);
      } else {
         finalStackForms.putAll(userDiskForms);
      }

      STACK_FORMS = finalStackForms;
   }

   private static EntitiesConfig createDefaultEntitiesConfig() {
      EntitiesConfig config = new EntitiesConfig();
      Map<String, EntitiesConfig.EntityStats> statsMap = config.getDefaultEntityStats();
      addDefaultEntityStats(statsMap, MainEntities.DINO_KID, 30.0, 4.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.DINOSAUR1, 100.0, 8.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.DINOSAUR2, 150.0, 12.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.DINOSAUR3, 75.0, 10.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.SABERTOOTH, 30.0, 5.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.BANDIT, 75.0, 10.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.RED_RIBBON_SOLDIER, 40.0, 5.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.RED_RIBBON_ROBOT1, 120.0, 15.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.RED_RIBBON_ROBOT2, 120.0, 15.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.RED_RIBBON_ROBOT3, 120.0, 15.0, 0.0);
      addDefaultEntityStats(statsMap, MainEntities.MINI_BUU, 60.0, 8.0, 6.0);
      EntitiesConfig.TransformSettings transform = config.getTransformDefaults();
      transform.setHealthMultiplier(1.5);
      transform.setMeleeMultiplier(1.5);
      transform.setKiMultiplier(1.5);
      transform.setTriggerHealthPercent(0.5);
      return config;
   }

   private static void addDefaultEntityStats(
      Map<String, EntitiesConfig.EntityStats> map,
      DeferredHolder<EntityType<?>, ? extends EntityType<?>> entityType,
      double health,
      double meleeDamage,
      double kiDamage
   ) {
      EntitiesConfig.EntityStats stats = new EntitiesConfig.EntityStats();
      stats.setHealth(health);
      stats.setMeleeDamage(meleeDamage);
      stats.setKiDamage(kiDamage);
      map.put(entityType.getKey().location().toString(), stats);
   }

   private static void loadAllRaces() throws IOException {
      RACE_STATS.clear();
      RACE_CHARACTER.clear();
      RACE_FORMS.clear();
      LOADED_RACES.clear();

      for (String raceName : DEFAULT_RACES) {
         createOrLoadRace(raceName, true);
      }

      try (Stream<Path> stream = Files.list(RACES_DIR)) {
         stream.forEach(racePath -> {
            if (Files.isDirectory(racePath)) {
               String raceNamex = racePath.getFileName().toString();
               if (!isDefaultRace(raceNamex)) {
                  try {
                     createOrLoadRace(raceNamex, false);
                     LogUtil.info(Env.COMMON, "Custom race detected: {}", raceNamex);
                  } catch (IOException var3x) {
                     LogUtil.error(Env.COMMON, "Error loading custom race '{}': {}", raceNamex, var3x.getMessage());
                     JsonLoadReport.error("config", "races/" + raceNamex, "Custom race failed to load: " + JsonLoadReport.rootCause(var3x));
                  }
               }
            }
         });
      }
   }

   public static boolean isDefaultRace(String raceName) {
      for (String vanilla : DEFAULT_RACES) {
         if (vanilla.equalsIgnoreCase(raceName)) {
            return true;
         }
      }

      return false;
   }

   private static RaceCharacterConfig createDefaultCharacterConfig(String raceName, boolean isDefault) {
      RaceCharacterConfig config = new RaceCharacterConfig();
      config.setRaceName(raceName);
      config.setUseVanillaSkin(false);
      config.setCustomModel("");
      if (isDefault) {
         boolean hasGender = RACES_WITH_GENDER.contains(raceName.toLowerCase());
         config.setHasGender(hasGender);
         String var4 = raceName.toLowerCase();
         switch (var4) {
            case "human":
               setupHumanCharacter(config);
               break;
            case "saiyan":
               setupSaiyanCharacter(config);
               break;
            case "namekian":
               setupNamekianCharacter(config);
               break;
            case "frostdemon":
               setupFrostDemonCharacter(config);
               break;
            case "bioandroid":
               setupBioAndroidCharacter(config);
               break;
            case "majin":
               setupMajinCharacter(config);
               break;
            default:
               setupDefaultCharacter(config);
         }
      } else {
         config.setHasGender(true);
         setupDefaultCharacter(config);
      }

      return config;
   }

   private static void setupHumanCharacter(RaceCharacterConfig config) {
      config.setUseVanillaSkin(true);
      config.setIsLayered(true);
      config.setRacialSkill("human");
      config.setHeadBones(new String[]{"hair"});
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(1);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#FFD3C9");
      config.setDefaultBodyColor2("#FFD3C9");
      config.setDefaultBodyColor3("#FFD3C9");
      config.setDefaultHairColor("#222629");
      config.setDefaultEye1Color("#222629");
      config.setDefaultEye2Color("#222629");
      config.setDefaultAuraColor("#7FFFFF");
      config.setFormSkillTpCosts("superforms", new Integer[]{21000, 42000, 65000, 104000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
      config.setFormSkillTpCosts("androidforms", new Integer[]{42000, 104000});
   }

   private static void setupSaiyanCharacter(RaceCharacterConfig config) {
      config.setUseVanillaSkin(true);
      config.setIsLayered(true);
      config.setRacialSkill("saiyan");
      config.setHeadBones(new String[]{"hair"});
      config.setHasSaiyanTail(true);
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(1);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#FFD3C9");
      config.setDefaultBodyColor2("#572117");
      config.setDefaultBodyColor3("#FFD3C9");
      config.setDefaultHairColor("#222629");
      config.setDefaultEye1Color("#222629");
      config.setDefaultEye2Color("#222629");
      config.setDefaultAuraColor("#7FFFFF");
      config.setFormSkillTpCosts("superforms", new Integer[]{13000, 21000, 31000, 42000, 52000, 65000, 78000, 104000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
   }

   private static void setupNamekianCharacter(RaceCharacterConfig config) {
      config.setRacialSkill("namekian");
      config.setIsLayered(true);
      config.setHeadBones(new String[]{"ears1", "ears2", "ears3"});
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(0);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#1FAA24");
      config.setDefaultBodyColor2("#BB2024");
      config.setDefaultBodyColor3("#FF86A6");
      config.setDefaultHairColor("#80FF69");
      config.setDefaultEye1Color("#222629");
      config.setDefaultEye2Color("#222629");
      config.setDefaultAuraColor("#7FFF00");
      config.setFormSkillTpCosts("superforms", new Integer[]{23000, 47000, 78000, 117000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
   }

   private static void setupFrostDemonCharacter(RaceCharacterConfig config) {
      config.setRacialSkill("frostdemon");
      config.setIsLayered(true);
      config.setHeadBones(new String[]{"horns1", "horns2", "horns3", "horns4", "horns5"});
      config.setDefaultModelScaling(new Float[]{0.7375F, 0.7375F, 0.7375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(0);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#FFFFFF");
      config.setDefaultBodyColor2("#E8A2FF");
      config.setDefaultBodyColor3("#FF39A9");
      config.setDefaultHairColor("#8B1BCC");
      config.setDefaultEye1Color("#FF001D");
      config.setDefaultEye2Color("#FF001D");
      config.setDefaultAuraColor("#5F00FF");
      config.setFormSkillTpCosts("superforms", new Integer[]{18000, 31000, 52000, 83000, 117000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
   }

   private static void setupBioAndroidCharacter(RaceCharacterConfig config) {
      config.setRacialSkill("bioandroid");
      config.setIsLayered(true);
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(0);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#187600");
      config.setDefaultBodyColor2("#9FE321");
      config.setDefaultBodyColor3("#FF7600");
      config.setDefaultHairColor("#187600");
      config.setDefaultEye1Color("#2E2424");
      config.setDefaultEye2Color("#F06F6E");
      config.setDefaultAuraColor("#1AA700");
      config.setFormSkillTpCosts("superforms", new Integer[]{26000, 57000, 88000, 125000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
   }

   private static void setupMajinCharacter(RaceCharacterConfig config) {
      config.setRacialSkill("majin");
      config.setIsLayered(true);
      config.setHeadBones(new String[]{"majin1", "majin2", "majin3"});
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(0);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#FFA4FF");
      config.setDefaultBodyColor2("#FFA4FF");
      config.setDefaultBodyColor3("#FFA4FF");
      config.setDefaultHairColor("#FFA4FF");
      config.setDefaultEye1Color("#B40000");
      config.setDefaultEye2Color("#B40000");
      config.setDefaultAuraColor("#FF6DFF");
      config.setFormSkillTpCosts("superforms", new Integer[]{23000, 47000, 78000, 114000});
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[]{-1, -1, -1});
   }

   private static void setupDefaultCharacter(RaceCharacterConfig config) {
      config.setUseVanillaSkin(true);
      config.setIsLayered(true);
      config.setRacialSkill("human");
      config.setDefaultModelScaling(new Float[]{0.9375F, 0.9375F, 0.9375F});
      config.setDefaultBodyType(0);
      config.setDefaultHairType(1);
      config.setDefaultEyesType(0);
      config.setDefaultNoseType(0);
      config.setDefaultMouthType(0);
      config.setDefaultTattooType(0);
      config.setDefaultBodyColor("#FFD3C9");
      config.setDefaultBodyColor2("#FFD3C9");
      config.setDefaultBodyColor3("#FFD3C9");
      config.setDefaultHairColor("#222629");
      config.setDefaultEye1Color("#222629");
      config.setDefaultEye2Color("#222629");
      config.setDefaultAuraColor("#7FFFFF");
      config.setFormSkillTpCosts("superforms", new Integer[0]);
      config.setFormSkillTpCosts("godforms", new Integer[0]);
      config.setFormSkillTpCosts("legendaryforms", new Integer[0]);
   }

   private static RaceStatsConfig createDefaultStatsConfig() {
      RaceStatsConfig config = new RaceStatsConfig();
      setupInitialStats(config.getClassStats("warrior"), 10, 0, 5, 5, 0, 0, 1.75, 0.06, 4.0, 0.08, 12.0, 0.12);
      setupScalingStats(config.getClassStats("warrior"), 1.4, 1.0, 0.24, 1.6, 1.8, 0.5, 1.5);
      setupInitialStats(config.getClassStats("spiritualist"), 0, 0, 0, 0, 10, 10, 0.5, 0.015, 8.0, 0.2, 5.0, 0.05);
      setupScalingStats(config.getClassStats("spiritualist"), 0.3, 0.5, 0.156, 0.7, 1.4, 1.9, 3.7);
      setupInitialStats(config.getClassStats("martialartist"), 0, 10, 0, 10, 0, 0, 1.5, 0.0525, 4.0, 0.08, 9.0, 0.09);
      setupScalingStats(config.getClassStats("martialartist"), 0.8, 1.8, 0.18, 1.3, 2.2, 0.6, 1.6);
      setupInitialStats(config.getClassStats("berserker"), 10, 0, 0, 10, 0, 0, 1.0, 0.0375, 2.0, 0.04, 14.0, 0.13);
      setupScalingStats(config.getClassStats("berserker"), 1.7, 0.8, 0.18, 1.1, 3.0, 0.4, 1.3);
      setupInitialStats(config.getClassStats("paladin"), 0, 5, 10, 5, 0, 0, 2.0, 0.0675, 4.0, 0.08, 8.0, 0.08);
      setupScalingStats(config.getClassStats("paladin"), 0.8, 1.2, 0.336, 1.2, 2.0, 0.6, 1.2);
      setupInitialStats(config.getClassStats("tank"), 0, 0, 10, 10, 0, 0, 2.25, 0.075, 5.0, 0.1, 9.0, 0.09);
      setupScalingStats(config.getClassStats("tank"), 0.6, 0.7, 0.384, 1.5, 2.5, 0.5, 0.8);
      config.getClassStats("tank").setTpGainMultiplier(1.25);
      setupInitialStats(config.getClassStats("cleric"), 0, 0, 5, 0, 0, 15, 0.5, 0.015, 12.0, 0.24, 16.0, 0.12);
      setupScalingStats(config.getClassStats("cleric"), 0.5, 0.5, 0.168, 2.6, 1.2, 0.8, 3.0);
      config.getClassStats("cleric").setTpGainMultiplier(1.25);
      config.getClassStats("cleric").setTpCostMultiplier(0.9);
      setupDefaultPassives(config);
      return config;
   }

   private static void setupDefaultPassives(RaceStatsConfig config) {
      Map<String, Double> warrior = new HashMap<>();
      warrior.put("comboHits", 3.0);
      warrior.put("maxStacks", 5.0);
      warrior.put("stmRegenPerStack", 0.1);
      warrior.put("armorPenAtMax", 0.1);
      warrior.put("stackDurationTicks", 100.0);
      warrior.put("comboResetTicks", 60.0);
      setupPassive(config.getClassStats("warrior"), warrior);
      Map<String, Double> martial = new HashMap<>();
      martial.put("maxBonus", 0.25);
      martial.put("hpHigh", 0.75);
      martial.put("hpLow", 0.25);
      setupPassive(config.getClassStats("martialartist"), martial);
      Map<String, Double> spiritualist = new HashMap<>();
      spiritualist.put("cdPrimary", 0.2);
      spiritualist.put("cdSecondary", 0.15);
      spiritualist.put("durationBonus", 0.25);
      setupPassive(config.getClassStats("spiritualist"), spiritualist);
      Map<String, Double> berserker = new HashMap<>();
      berserker.put("hpThreshHigh", 0.66);
      berserker.put("hpThreshLow", 0.33);
      berserker.put("hpRegenHigh", 0.25);
      berserker.put("critHigh", 0.1);
      berserker.put("hpRegenLow", 0.75);
      berserker.put("critLow", 0.25);
      setupPassive(config.getClassStats("berserker"), berserker);
      Map<String, Double> paladin = new HashMap<>();
      paladin.put("redirectPct", 0.15);
      paladin.put("lifestealPct", 0.15);
      setupPassive(config.getClassStats("paladin"), paladin);
      Map<String, Double> tank = new HashMap<>();
      tank.put("stmToHpRegenRatio", 0.5);
      tank.put("healingBonus", 0.25);
      tank.put("lowHpThreshold", 0.3);
      tank.put("lowHpMultiplier", 2.0);
      setupPassive(config.getClassStats("tank"), tank);
      Map<String, Double> cleric = new HashMap<>();
      cleric.put("cdPrimary", 0.2);
      cleric.put("cdSecondary", 0.15);
      cleric.put("durationBonus", 0.25);
      setupPassive(config.getClassStats("cleric"), cleric);
   }

   private static void setupPassive(RaceStatsConfig.ClassStats classStats, Map<String, Double> values) {
      RaceStatsConfig.Passive passive = classStats.getPassive();
      passive.setEnabled(true);
      passive.setValues(values);
   }

   private static void setupInitialStats(
      RaceStatsConfig.ClassStats classStats,
      int str,
      int skp,
      int res,
      int vit,
      int pwr,
      int ene,
      double baseHp5,
      double hp5VitScaling,
      double baseEp5,
      double ep5EneScaling,
      double baseSp5,
      double sp5StmScaling
   ) {
      RaceStatsConfig.BaseStats base = classStats.getBaseStats();
      base.setStrength(str);
      base.setStrikePower(skp);
      base.setResistance(res);
      base.setVitality(vit);
      base.setKiPower(pwr);
      base.setEnergy(ene);
      classStats.setBaseHp5(baseHp5);
      classStats.setHp5VitScaling(hp5VitScaling);
      classStats.setBaseEp5(baseEp5);
      classStats.setEp5EneScaling(ep5EneScaling);
      classStats.setBaseSp5(baseSp5);
      classStats.setSp5StmScaling(sp5StmScaling);
   }

   private static void setupScalingStats(
      RaceStatsConfig.ClassStats classStats,
      double strScale,
      double skpScale,
      double defScale,
      double stmScale,
      double vitScale,
      double pwrScale,
      double eneScale
   ) {
      RaceStatsConfig.StatScaling scaling = classStats.getStatScaling();
      scaling.setStrengthScaling(strScale);
      scaling.setStrikePowerScaling(skpScale);
      scaling.setDefenseScaling(defScale);
      scaling.setStaminaScaling(stmScale);
      scaling.setVitalityScaling(vitScale);
      scaling.setKiPowerScaling(pwrScale);
      scaling.setEnergyScaling(eneScale);
   }

   public static RaceStatsConfig getRaceStats(String raceName) {
      String key = raceName != null ? raceName.toLowerCase() : "human";
      if (serverSyncActive) {
         Map<String, RaceStatsConfig> synced = SERVER_SYNCED_STATS != null ? SERVER_SYNCED_STATS : Collections.emptyMap();
         RaceStatsConfig config = synced.getOrDefault(key, synced.get("human"));
         return config != null ? config : createDefaultStatsConfig();
      } else {
         RaceStatsConfig config = RACE_STATS.getOrDefault(key, RACE_STATS.get("human"));
         return config != null ? config : createDefaultStatsConfig();
      }
   }

   public static RaceCharacterConfig getRaceCharacter(String raceName) {
      String key = raceName != null ? raceName.toLowerCase() : "human";
      if (serverSyncActive) {
         Map<String, RaceCharacterConfig> synced = SERVER_SYNCED_CHARACTER != null ? SERVER_SYNCED_CHARACTER : Collections.emptyMap();
         RaceCharacterConfig config = synced.getOrDefault(key, synced.get("human"));
         return config != null ? config : createDefaultCharacterConfig(key, false);
      } else {
         RaceCharacterConfig config = RACE_CHARACTER.getOrDefault(key, RACE_CHARACTER.get("human"));
         return config != null ? config : createDefaultCharacterConfig(key, false);
      }
   }

   public static List<String> getLoadedRaces() {
      List<String> races;
      if (serverSyncActive) {
         races = SERVER_SYNCED_CHARACTER != null ? new ArrayList<>(SERVER_SYNCED_CHARACTER.keySet()) : new ArrayList<>();
      } else {
         races = new ArrayList<>(LOADED_RACES);
      }

      races.sort((r1, r2) -> {
         int index1 = -1;
         int index2 = -1;

         for (int i = 0; i < DEFAULT_RACES.length; i++) {
            if (DEFAULT_RACES[i].equalsIgnoreCase(r1)) {
               index1 = i;
            }

            if (DEFAULT_RACES[i].equalsIgnoreCase(r2)) {
               index2 = i;
            }
         }

         if (index1 != -1 && index2 != -1) {
            return Integer.compare(index1, index2);
         } else if (index1 != -1) {
            return -1;
         } else {
            return index2 != -1 ? 1 : r1.compareToIgnoreCase(r2);
         }
      });
      return races;
   }

   public static List<String> getDefaultRaces() {
      return Arrays.asList(DEFAULT_RACES);
   }

   public static boolean isRaceLoaded(String raceName) {
      if (raceName == null) {
         return false;
      } else {
         return !serverSyncActive
            ? LOADED_RACES.stream().anyMatch(r -> r.equalsIgnoreCase(raceName))
            : SERVER_SYNCED_CHARACTER != null && SERVER_SYNCED_CHARACTER.containsKey(raceName.toLowerCase());
      }
   }

   public static GeneralUserConfig getUserConfig() {
      return userConfig != null ? userConfig : new GeneralUserConfig();
   }

   public static GeneralServerConfig getServerConfig() {
      if (serverSyncActive && SERVER_SYNCED_GENERAL_SERVER != null) {
         return SERVER_SYNCED_GENERAL_SERVER;
      } else {
         return serverConfig != null ? serverConfig : new GeneralServerConfig();
      }
   }

   public static CombatConfig getCombatConfig() {
      if (serverSyncActive && SERVER_SYNCED_COMBAT != null) {
         return SERVER_SYNCED_COMBAT;
      } else {
         return combatConfig != null ? combatConfig : new CombatConfig();
      }
   }

   public static TrainingConfig getTrainingConfig() {
      if (serverSyncActive && SERVER_SYNCED_TRAINING != null) {
         return SERVER_SYNCED_TRAINING;
      } else {
         return trainingConfig != null ? trainingConfig : new TrainingConfig();
      }
   }

   public static void saveGeneralUserConfig() {
      try {
         LOADER.saveConfig(CONFIG_DIR.resolve("general-user.json"), userConfig);
      } catch (IOException var1) {
         LogUtil.error(Env.COMMON, "Error saving user configuration: {}", var1.getMessage());
      }
   }

   public static boolean updateConfigValue(String configFileName, String optionalSubtype, String key, String value) {
      try {
         Path configPath = CONFIG_DIR.resolve(configFileName + ".json");
         if (!Files.exists(configPath)) {
            return false;
         } else {
            String content = Files.readString(configPath);
            JsonObject rootObj = JsonParser.parseString(content).getAsJsonObject();
            JsonObject targetObj = rootObj;
            if (optionalSubtype != null && !optionalSubtype.isEmpty()) {
               if (!rootObj.has(optionalSubtype) || !rootObj.get(optionalSubtype).isJsonObject()) {
                  return false;
               }

               targetObj = rootObj.getAsJsonObject(optionalSubtype);
            }

            if (!targetObj.has(key)) {
               return false;
            } else {
               JsonElement existing = targetObj.get(key);
               if (existing == null || !existing.isJsonObject() && !existing.isJsonArray()) {
                  JsonElement parsedValue;
                  if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                     try {
                        Double.parseDouble(value);
                        parsedValue = JsonParser.parseString(value);
                     } catch (NumberFormatException var11) {
                        parsedValue = GSON.toJsonTree(value);
                     }
                  } else {
                     parsedValue = JsonParser.parseString(value.toLowerCase(Locale.ROOT));
                  }

                  targetObj.add(key, parsedValue);
                  Files.writeString(configPath, GSON.toJson(rootObj));
                  return true;
               } else {
                  return false;
               }
            }
         }
      } catch (Exception var12) {
         LogUtil.error(Env.COMMON, "Error updating config value: " + var12.getMessage());
         return false;
      }
   }

   public static List<String> getAvailableConfigFiles() {
      if (!CACHED_CONFIG_FILES.isEmpty()) {
         return CACHED_CONFIG_FILES;
      } else {
         try (Stream<Path> stream = Files.walk(CONFIG_DIR)) {
            stream.filter(x$0 -> Files.isRegularFile(x$0))
               .filter(p -> p.toString().endsWith(".json"))
               .filter(p -> !p.getFileName().toString().toLowerCase().startsWith("old_"))
               .filter(p -> !CONFIG_DIR.relativize(p).toString().replace("\\", "/").startsWith("oldBackup/"))
               .forEach(p -> {
                  String relativePath = CONFIG_DIR.relativize(p).toString().replace("\\", "/");
                  CACHED_CONFIG_FILES.add(relativePath.substring(0, relativePath.length() - 5));
               });
         } catch (IOException var5) {
            LogUtil.error(Env.COMMON, "Error scanning config files: " + var5.getMessage());
         }

         return CACHED_CONFIG_FILES;
      }
   }

   public static boolean isSubtype(String configFileName, String name) {
      if (name != null && !name.isEmpty()) {
         try {
            Path configPath = CONFIG_DIR.resolve(configFileName + ".json");
            if (!Files.exists(configPath)) {
               return false;
            } else {
               JsonObject rootObj = JsonParser.parseString(Files.readString(configPath)).getAsJsonObject();
               return rootObj.has(name) && rootObj.get(name).isJsonObject();
            }
         } catch (Exception var4) {
            return false;
         }
      } else {
         return false;
      }
   }

   public static List<String> getKeysOrSubtypes(String configFileName, String optionalSubtype) {
      List<String> list = new ArrayList<>();

      try {
         Path configPath = CONFIG_DIR.resolve(configFileName + ".json");
         if (!Files.exists(configPath)) {
            return list;
         }

         String content = Files.readString(configPath);
         JsonObject rootObj = JsonParser.parseString(content).getAsJsonObject();
         JsonObject targetObj = rootObj;
         if (optionalSubtype != null && !optionalSubtype.isEmpty()) {
            if (!rootObj.has(optionalSubtype) || !rootObj.get(optionalSubtype).isJsonObject()) {
               return list;
            }

            targetObj = rootObj.getAsJsonObject(optionalSubtype);
         }

         for (Entry<String, JsonElement> entry : targetObj.entrySet()) {
            list.add(entry.getKey());
         }
      } catch (Exception var9) {
      }

      return list;
   }

   public static List<String> getValueSuggestions(String configFileName, String optionalSubtype, String key) {
      Set<String> list = new LinkedHashSet<>();

      try {
         Path configPath = CONFIG_DIR.resolve(configFileName + ".json");
         if (!Files.exists(configPath)) {
            return new ArrayList<>();
         }

         String content = Files.readString(configPath);
         JsonObject rootObj = JsonParser.parseString(content).getAsJsonObject();
         JsonObject targetObj = rootObj;
         if (optionalSubtype != null && !optionalSubtype.isEmpty()) {
            if (!rootObj.has(optionalSubtype) || !rootObj.get(optionalSubtype).isJsonObject()) {
               return new ArrayList<>();
            }

            targetObj = rootObj.getAsJsonObject(optionalSubtype);
         }

         if (targetObj.has(key)) {
            JsonElement element = targetObj.get(key);
            if (element.isJsonPrimitive()) {
               if (element.getAsJsonPrimitive().isBoolean()) {
                  list.add("true");
                  list.add("false");
               } else if (element.getAsJsonPrimitive().isNumber()) {
                  String currentNum = element.getAsString();
                  list.add(currentNum);
                  if (currentNum.contains(".")) {
                     list.addAll(Arrays.asList("0.0", "0.5", "1.0", "1.5", "2.0", "5.0", "10.0"));
                  } else {
                     list.addAll(Arrays.asList("0", "1", "2", "5", "10", "20", "50", "100"));
                  }
               } else if (element.getAsJsonPrimitive().isString()) {
                  list.add(element.getAsString());
               }
            }
         }
      } catch (Exception var10) {
      }

      return new ArrayList<>(list);
   }

   public static void reloadSpecificConfig(String configFilePath) throws IOException {
      Path path = CONFIG_DIR.resolve(configFilePath + ".json");
      if (configFilePath.equals("general-server")) {
         serverConfig = LOADER.loadConfig(path, GeneralServerConfig.class);
      } else if (configFilePath.equals("combat")) {
         combatConfig = LOADER.loadConfig(path, CombatConfig.class);
      } else if (configFilePath.equals("training")) {
         trainingConfig = LOADER.loadConfig(path, TrainingConfig.class);
      } else if (configFilePath.equals("skills")) {
         skillsConfig = LOADER.loadConfig(path, SkillsConfig.class);
      } else if (configFilePath.equals("techniques")) {
         techniqueConfig = LOADER.loadConfig(path, TechniqueConfig.class);
      } else if (configFilePath.equals("entities")) {
         entitiesConfig = LOADER.loadConfig(path, EntitiesConfig.class);
      } else if (configFilePath.startsWith("races/")) {
         String[] parts = configFilePath.split("/");
         String raceName = parts[1];
         if (parts[2].equals("stats")) {
            RACE_STATS.put(raceName.toLowerCase(), LOADER.loadConfig(path, RaceStatsConfig.class));
         } else if (parts[2].equals("character")) {
            RACE_CHARACTER.put(raceName.toLowerCase(), LOADER.loadConfig(path, RaceCharacterConfig.class));
         } else if (parts[2].equals("forms")) {
            FormConfig formConfig = LOADER.loadConfig(path, FormConfig.class);
            RACE_FORMS.computeIfAbsent(raceName.toLowerCase(), k -> new HashMap<>()).put(formGroupKey(formConfig, parts[3]), formConfig);
         }
      } else if (configFilePath.startsWith("forms/")) {
         FormConfig formConfig = LOADER.loadConfig(path, FormConfig.class);
         STACK_FORMS.put(formGroupKey(formConfig, configFilePath.split("/")[1]), formConfig);
      }
   }

   public static boolean saveRawConfig(String configFilePath, String json) {
      try {
         JsonElement parsed = JsonParser.parseString(json);
         Path path = CONFIG_DIR.resolve(configFilePath + ".json");
         if (!Files.exists(path)) {
            return false;
         } else {
            Files.writeString(path, GSON.toJson(parsed));
            return true;
         }
      } catch (Exception var4) {
         LogUtil.error(Env.COMMON, "Error saving raw config '{}': {}", configFilePath, var4.getMessage());
         return false;
      }
   }

   public static String getSpecificConfigJson(String configFilePath) {
      Path path = CONFIG_DIR.resolve(configFilePath + ".json");
      if (!Files.exists(path)) {
         return null;
      } else {
         try {
            String content = Files.readString(path);
            return content != null && !content.isBlank() ? content : null;
         } catch (IOException var3) {
            LogUtil.error(Env.COMMON, "Could not read config for sync: " + configFilePath);
            return null;
         }
      }
   }

   private static String formGroupKey(FormConfig config, String fileNameFallback) {
      return config != null && config.getGroupName() != null && !config.getGroupName().isEmpty()
         ? config.getGroupName().toLowerCase()
         : fileNameFallback.toLowerCase();
   }

   public static void applySpecificSyncedConfig(String configFilePath, String json) {
      try {
         serverSyncActive = true;
         if (configFilePath.equals("general-server")) {
            SERVER_SYNCED_GENERAL_SERVER = (GeneralServerConfig)GSON.fromJson(json, GeneralServerConfig.class);
         } else if (configFilePath.equals("combat")) {
            SERVER_SYNCED_COMBAT = (CombatConfig)GSON.fromJson(json, CombatConfig.class);
         } else if (configFilePath.equals("training")) {
            SERVER_SYNCED_TRAINING = (TrainingConfig)GSON.fromJson(json, TrainingConfig.class);
         } else if (configFilePath.equals("skills")) {
            SERVER_SYNCED_SKILLS = (SkillsConfig)GSON.fromJson(json, SkillsConfig.class);
         } else if (configFilePath.equals("techniques")) {
            SERVER_SYNCED_TECHNIQUES = (TechniqueConfig)GSON.fromJson(json, TechniqueConfig.class);
         } else if (configFilePath.equals("entities")) {
            SERVER_SYNCED_ENTITIES = (EntitiesConfig)GSON.fromJson(json, EntitiesConfig.class);
         } else if (configFilePath.startsWith("races/")) {
            String[] parts = configFilePath.split("/");
            String raceName = parts[1];
            if (parts[2].equals("stats")) {
               if (SERVER_SYNCED_STATS == null) {
                  SERVER_SYNCED_STATS = new HashMap<>();
               }

               SERVER_SYNCED_STATS.put(raceName.toLowerCase(), (RaceStatsConfig)GSON.fromJson(json, RaceStatsConfig.class));
            } else if (parts[2].equals("character")) {
               if (SERVER_SYNCED_CHARACTER == null) {
                  SERVER_SYNCED_CHARACTER = new HashMap<>();
               }

               SERVER_SYNCED_CHARACTER.put(raceName.toLowerCase(), (RaceCharacterConfig)GSON.fromJson(json, RaceCharacterConfig.class));
            } else if (parts[2].equals("forms")) {
               if (SERVER_SYNCED_FORMS == null) {
                  SERVER_SYNCED_FORMS = new HashMap<>();
               }

               FormConfig formConfig = (FormConfig)GSON.fromJson(json, FormConfig.class);
               SERVER_SYNCED_FORMS.computeIfAbsent(raceName.toLowerCase(), k -> new HashMap<>()).put(formGroupKey(formConfig, parts[3]), formConfig);
            }
         } else if (configFilePath.startsWith("forms/")) {
            if (SERVER_SYNCED_STACK_FORMS == null) {
               SERVER_SYNCED_STACK_FORMS = new HashMap<>();
            }

            FormConfig formConfig = (FormConfig)GSON.fromJson(json, FormConfig.class);
            SERVER_SYNCED_STACK_FORMS.put(formGroupKey(formConfig, configFilePath.split("/")[1]), formConfig);
         }
      } catch (Exception var5) {
         LogUtil.error(Env.CLIENT, "Error applying synced config: " + var5.getMessage());
      }
   }

   public static void applySyncedServerConfig(
      GeneralServerConfig syncedServerConfig,
      CombatConfig syncedCombatConfig,
      SkillsConfig syncedSkillsConfig,
      Map<String, Map<String, FormConfig>> syncedForms,
      Map<String, RaceStatsConfig> syncedStats,
      Map<String, RaceCharacterConfig> syncedCharacters,
      Map<String, FormConfig> syncedStackForms
   ) {
      SERVER_SYNCED_GENERAL_SERVER = syncedServerConfig;
      SERVER_SYNCED_COMBAT = syncedCombatConfig;
      SERVER_SYNCED_SKILLS = syncedSkillsConfig;
      SERVER_SYNCED_FORMS = syncedForms;
      SERVER_SYNCED_STATS = syncedStats;
      SERVER_SYNCED_CHARACTER = syncedCharacters;
      SERVER_SYNCED_STACK_FORMS = syncedStackForms;
      serverSyncActive = true;
   }

   private static void clearSyncedMaps() {
      SERVER_SYNCED_GENERAL_SERVER = null;
      SERVER_SYNCED_COMBAT = null;
      SERVER_SYNCED_TRAINING = null;
      SERVER_SYNCED_SKILLS = null;
      SERVER_SYNCED_TECHNIQUES = null;
      SERVER_SYNCED_ENTITIES = null;
      SERVER_SYNCED_FORMS = null;
      SERVER_SYNCED_STATS = null;
      SERVER_SYNCED_CHARACTER = null;
      SERVER_SYNCED_STACK_FORMS = null;
   }

   public static void beginServerSyncBatch() {
      clearSyncedMaps();
      serverSyncActive = true;
   }

   public static void clearServerSync() {
      clearSyncedMaps();
      serverSyncActive = false;
   }

   public static Map<String, RaceStatsConfig> getAllRaceStats() {
      if (serverSyncActive) {
         return (Map<String, RaceStatsConfig>)(SERVER_SYNCED_STATS != null ? SERVER_SYNCED_STATS : new HashMap<>());
      } else {
         return new HashMap<>(RACE_STATS);
      }
   }

   public static Map<String, RaceCharacterConfig> getAllRaceCharacters() {
      if (serverSyncActive) {
         return (Map<String, RaceCharacterConfig>)(SERVER_SYNCED_CHARACTER != null ? SERVER_SYNCED_CHARACTER : new HashMap<>());
      } else {
         return new HashMap<>(RACE_CHARACTER);
      }
   }

   public static Map<String, Map<String, FormConfig>> getAllForms() {
      if (serverSyncActive) {
         return (Map<String, Map<String, FormConfig>>)(SERVER_SYNCED_FORMS != null ? SERVER_SYNCED_FORMS : new HashMap<>());
      } else {
         return RACE_FORMS;
      }
   }

   public static Map<String, FormConfig> getAllFormsForRace(String raceName) {
      return getAllForms().getOrDefault(raceName.toLowerCase(), new HashMap<>());
   }

   public static FormConfig getFormGroup(String raceName, String groupName) {
      Map<String, FormConfig> raceForms = getAllFormsForRace(raceName);
      return raceForms != null ? raceForms.get(groupName.toLowerCase()) : null;
   }

   public static FormConfig.FormData getForm(String raceName, String groupName, String formName) {
      FormConfig group = getFormGroup(raceName, groupName);
      return group != null ? group.getForm(formName) : null;
   }

   public static Map<String, FormConfig> getAllStackForms() {
      if (serverSyncActive) {
         return (Map<String, FormConfig>)(SERVER_SYNCED_STACK_FORMS != null ? SERVER_SYNCED_STACK_FORMS : new HashMap<>());
      } else {
         return STACK_FORMS;
      }
   }

   public static FormConfig getStackFormGroup(String groupName) {
      Map<String, FormConfig> stackForms = getAllStackForms();
      return stackForms != null ? stackForms.get(groupName.toLowerCase()) : null;
   }

   public static FormConfig.FormData getStackForm(String groupName, String formName) {
      FormConfig group = getStackFormGroup(groupName);
      return group != null ? group.getForm(formName) : null;
   }

   public static SkillsConfig getSkillsConfig() {
      if (serverSyncActive && SERVER_SYNCED_SKILLS != null) {
         return SERVER_SYNCED_SKILLS;
      } else {
         return skillsConfig != null ? skillsConfig : new SkillsConfig();
      }
   }

   public static TechniqueConfig getTechniqueConfig() {
      if (serverSyncActive && SERVER_SYNCED_TECHNIQUES != null) {
         return SERVER_SYNCED_TECHNIQUES;
      } else {
         return techniqueConfig != null ? techniqueConfig : new TechniqueConfig();
      }
   }

   public static EntitiesConfig getEntitiesConfig() {
      return serverSyncActive ? SERVER_SYNCED_ENTITIES : entitiesConfig;
   }

   public static EntitiesConfig.EntityStats getEntityStats(String registryName) {
      EntitiesConfig config = getEntitiesConfig();
      return config != null && config.getDefaultEntityStats() != null ? config.getDefaultEntityStats().get(registryName) : null;
   }

   public static EntitiesConfig.TransformSettings getEntityTransformDefaults() {
      EntitiesConfig config = getEntitiesConfig();
      EntitiesConfig.TransformSettings transform = config != null ? config.getTransformDefaults() : null;
      return transform != null ? transform : new EntitiesConfig.TransformSettings();
   }
}
