package com.dragonminez.common.quest;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import lombok.Generated;

public final class QuestUpgrader {
   public static final String DEFAULTS_VERSION = "2.1.2";
   static final String VERSION_KEY = "defaultsVersion";
   private static final String PREVIOUS_QUESTS_ROOT = "/data/dragonminez/previousQuests/";
   private static final String OLD_BACKUP_DIR = "oldBackup";
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static boolean autoUpdateEnabled = true;

   private QuestUpgrader() {
   }

   public static void upgradeOrWrite(Path dmzBase, Path file, JsonObject newDefault) {
      newDefault.addProperty("defaultsVersion", "2.1.2");

      try {
         if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            writeJson(file, newDefault);
            return;
         }

         if (!autoUpdateEnabled) {
            return;
         }

         JsonObject userObj = readJson(file);
         if (userObj == null) {
            return;
         }

         String userVersion = userObj.has("defaultsVersion") && userObj.get("defaultsVersion").isJsonPrimitive()
            ? userObj.get("defaultsVersion").getAsString()
            : null;
         if ("2.1.2".equals(userVersion)) {
            return;
         }

         String relative = relativeKey(dmzBase, file);
         JsonObject baseline = loadBaseline(relative);
         QuestUpdateReport.FileReport report = QuestUpdateReport.forFile(relative, userVersion == null ? "unversioned" : userVersion, "2.1.2");
         JsonObject merged = deepCopy(newDefault);
         mergeInto(userObj, baseline, merged, "", report);
         merged.addProperty("defaultsVersion", "2.1.2");
         if (report.hasChanges()) {
            backup(dmzBase, file);
            LogUtil.info(
               Env.COMMON,
               "Upgraded quest file '{}' ({} → {}): {} update(s), {} conflict(s)",
               relative,
               report.fromVersion,
               report.toVersion,
               report.appliedCount,
               report.conflicts.size()
            );
         }

         writeJson(file, merged);
      } catch (Exception var9) {
         LogUtil.error(Env.COMMON, "Failed to upgrade quest file '{}': {}", file.getFileName(), var9.getMessage());
      }
   }

   private static void mergeInto(JsonObject user, JsonObject baseline, JsonObject target, String path, QuestUpdateReport.FileReport report) {
      for (String key : new ArrayList(target.keySet())) {
         if (!"defaultsVersion".equals(key) && user.has(key)) {
            JsonElement userVal = user.get(key);
            JsonElement newVal = target.get(key);
            JsonElement baseVal = child(baseline, key);
            String childPath = path.isEmpty() ? key : path + "." + key;
            if (userVal.isJsonObject() && newVal.isJsonObject()) {
               JsonObject baseObj = baseVal != null && baseVal.isJsonObject() ? baseVal.getAsJsonObject() : null;
               mergeInto(userVal.getAsJsonObject(), baseObj, newVal.getAsJsonObject(), childPath, report);
            } else if (userVal.isJsonArray() && newVal.isJsonArray()) {
               JsonArray baseArr = baseVal != null && baseVal.isJsonArray() ? baseVal.getAsJsonArray() : null;
               target.add(key, mergeArray(userVal.getAsJsonArray(), baseArr, newVal.getAsJsonArray(), childPath, report));
            } else {
               resolveScalar(target, key, childPath, userVal, baseVal, newVal, report);
            }
         }
      }

      for (String keyx : user.keySet()) {
         if (!target.has(keyx) && !"defaultsVersion".equals(keyx)) {
            target.add(keyx, user.get(keyx));
         }
      }
   }

   private static void resolveScalar(
      JsonObject target, String key, String path, JsonElement userVal, JsonElement baseVal, JsonElement newVal, QuestUpdateReport.FileReport report
   ) {
      boolean userMatchesNew = equal(userVal, newVal);
      if (!userMatchesNew) {
         if (baseVal != null) {
            boolean userEdited = !equal(userVal, baseVal);
            boolean newChanged = !equal(newVal, baseVal);
            if (!userEdited) {
               report.appliedCount++;
            } else {
               target.add(key, userVal);
               if (newChanged) {
                  report.conflicts.add(new QuestUpdateReport.Conflict(path, asText(userVal), asText(baseVal), asText(newVal)));
               }
            }
         } else {
            target.add(key, userVal);
         }
      }
   }

   private static JsonArray mergeArray(JsonArray userArr, JsonArray baseArr, JsonArray newArr, String path, QuestUpdateReport.FileReport report) {
      if (baseArr != null && equal(userArr, baseArr)) {
         if (!equal(newArr, baseArr)) {
            report.appliedCount++;
         }

         return newArr;
      } else if (baseArr != null && equal(newArr, baseArr)) {
         return userArr;
      } else {
         if (baseArr != null) {
            report.conflicts.add(new QuestUpdateReport.Conflict(path + "[]", "user-edited list", "old default list", "new default list"));
         }

         return userArr;
      }
   }

   private static JsonElement child(JsonObject obj, String key) {
      return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key) : null;
   }

   private static boolean equal(JsonElement a, JsonElement b) {
      if (b == null) {
         return false;
      } else {
         if (a.isJsonPrimitive() && b.isJsonPrimitive()) {
            JsonPrimitive pa = a.getAsJsonPrimitive();
            JsonPrimitive pb = b.getAsJsonPrimitive();
            if (pa.isNumber() && pb.isNumber()) {
               try {
                  return pa.getAsBigDecimal().compareTo(pb.getAsBigDecimal()) == 0;
               } catch (NumberFormatException var5) {
                  return pa.getAsString().equals(pb.getAsString());
               }
            }
         }

         return a.equals(b);
      }
   }

   private static String asText(JsonElement e) {
      return e == null ? "(absent)" : e.toString();
   }

   private static JsonObject deepCopy(JsonObject obj) {
      return JsonParser.parseString(obj.toString()).getAsJsonObject();
   }

   private static String relativeKey(Path dmzBase, Path file) {
      return dmzBase.relativize(file).toString().replace('\\', '/');
   }

   static JsonObject loadBaseline(String relativeKey) {
      String resource = "/data/dragonminez/previousQuests/" + relativeKey;

      try {
         JsonObject var4;
         try (InputStream in = QuestUpgrader.class.getResourceAsStream(resource)) {
            if (in == null) {
               return null;
            }

            JsonElement parsed = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            var4 = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
         }

         return var4;
      } catch (Exception var7) {
         return null;
      }
   }

   private static void backup(Path dmzBase, Path file) {
      try {
         Path backup = dmzBase.resolve("oldBackup").resolve(dmzBase.relativize(file));
         Files.createDirectories(backup.getParent());
         Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING);
      } catch (Exception var3) {
         LogUtil.error(Env.COMMON, "Failed to back up quest file '{}': {}", file.getFileName(), var3.getMessage());
      }
   }

   private static JsonObject readJson(Path file) {
      try {
         JsonElement parsed = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
         return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
      } catch (Exception var2) {
         LogUtil.warn(Env.COMMON, "Could not read quest file '{}' for upgrade: {}", file.getFileName(), var2.getMessage());
         return null;
      }
   }

   private static void writeJson(Path file, JsonObject obj) throws IOException {
      try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
         GSON.toJson(obj, w);
      }
   }

   @Generated
   public static void setAutoUpdateEnabled(boolean autoUpdateEnabled) {
      QuestUpgrader.autoUpdateEnabled = autoUpdateEnabled;
   }
}
