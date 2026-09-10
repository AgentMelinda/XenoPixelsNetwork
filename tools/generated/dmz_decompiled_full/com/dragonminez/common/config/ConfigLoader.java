package com.dragonminez.common.config;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.diagnostics.JsonLoadReport;
import com.dragonminez.common.diagnostics.JsonSchema;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;
import lombok.Generated;

public class ConfigLoader {
   private final Gson gson;

   public <T> T loadConfig(Path path, Class<T> clazz) throws IOException {
      try {
         String content = Files.readString(path, StandardCharsets.UTF_8);
         JsonElement tree = (JsonElement)this.gson.fromJson(content, JsonElement.class);
         if (tree != null && tree.isJsonObject()) {
            JsonSchema.check("config", path.getFileName().toString(), "", tree.getAsJsonObject(), clazz);
         }

         return (T)this.gson.fromJson(tree, clazz);
      } catch (JsonSyntaxException var5) {
         throw new IOException("Invalid JSON syntax in file: " + path.getFileName(), var5);
      } catch (Exception var6) {
         throw new IOException("Failed to parse config file: " + path.getFileName(), var6);
      }
   }

   public void saveConfig(Path path, Object config) throws IOException {
      String jsonContent = this.gson.toJson(config);
      Files.writeString(path, jsonContent, StandardCharsets.UTF_8);
   }

   public Map<String, FormConfig> loadRaceForms(String raceName, Path formsPath) throws IOException {
      Map<String, FormConfig> forms = new HashMap<>();
      if (!Files.exists(formsPath)) {
         return forms;
      } else {
         try (Stream<Path> stream = Files.list(formsPath)) {
            stream.filter(path -> path.toString().endsWith(".json"))
               .filter(path -> !path.getFileName().toString().toLowerCase().startsWith("old_"))
               .forEach(
                  formFile -> {
                     try {
                        FormConfig formConfig = this.loadConfig(formFile, FormConfig.class);
                        if (formConfig == null) {
                           LogUtil.error(Env.COMMON, "Form file '{}' is empty or null.", formFile.getFileName());
                           return;
                        }

                        String groupName = formConfig.getGroupName();
                        if (groupName != null && !groupName.isEmpty()) {
                           forms.put(groupName.toLowerCase(), formConfig);
                           LogUtil.info(Env.COMMON, "Form group '{}' loaded for race '{}'", groupName, raceName);
                        }
                     } catch (IOException var6) {
                        LogUtil.error(Env.COMMON, "Error loading form file '{}' for race '{}': {}", formFile.getFileName(), raceName, var6.getMessage());
                        JsonLoadReport.error(
                           "config",
                           "races/" + raceName + "/forms/" + formFile.getFileName(),
                           "Malformed form JSON, file skipped: " + JsonLoadReport.rootCause(var6)
                        );
                     }
                  }
               );
         }

         return forms;
      }
   }

   public Map<String, FormConfig> loadStackForms(Path formsPath) throws IOException {
      Map<String, FormConfig> forms = new HashMap<>();
      if (!Files.exists(formsPath)) {
         return forms;
      } else {
         try (Stream<Path> stream = Files.list(formsPath)) {
            stream.filter(path -> path.toString().endsWith(".json"))
               .filter(path -> !path.getFileName().toString().toLowerCase().startsWith("old_"))
               .forEach(
                  formFile -> {
                     try {
                        FormConfig formConfig = this.loadConfig(formFile, FormConfig.class);
                        if (formConfig == null) {
                           return;
                        }

                        String groupName = formConfig.getGroupName();
                        if (groupName != null && !groupName.isEmpty()) {
                           forms.put(groupName.toLowerCase(), formConfig);
                           LogUtil.info(Env.COMMON, "Form group '{}' loaded for stack forms", groupName);
                        }
                     } catch (IOException var5) {
                        LogUtil.error(Env.COMMON, "Error loading stack form file '{}': {}", formFile.getFileName(), var5.getMessage());
                        JsonLoadReport.error(
                           "config", "forms/" + formFile.getFileName(), "Malformed stack-form JSON, file skipped: " + JsonLoadReport.rootCause(var5)
                        );
                     }
                  }
               );
         }

         return forms;
      }
   }

   public void saveDefaultFromTemplate(Path target, String templateName) {
      try (InputStream inputStream = this.getClass().getResourceAsStream("/assets/dragonminez/config_defaults/" + templateName)) {
         if (inputStream != null) {
            Files.copy(inputStream, target);
            LogUtil.info(Env.COMMON, "Created default config from template: {}", templateName);
         } else {
            LogUtil.error(Env.COMMON, "Template not found: {}", templateName);
         }
      } catch (IOException var8) {
         LogUtil.error(Env.COMMON, "Could not copy default config: {}", var8.getMessage());
      }
   }

   public boolean hasExistingFiles(Path directory) throws IOException {
      if (!Files.exists(directory)) {
         return false;
      } else {
         boolean var3;
         try (Stream<Path> stream = Files.list(directory)) {
            var3 = stream.anyMatch(path -> path.toString().endsWith(".json"));
         }

         return var3;
      }
   }

   @Generated
   public ConfigLoader(Gson gson) {
      this.gson = gson;
   }
}
