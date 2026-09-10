package com.dragonminez.common.quest;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

final class SagaDefaults {
   private SagaDefaults() {
   }

   static void createDefaultSagaFiles(Path sagaDir) {
      if (ConfigManager.getServerConfig().getGameplay().getStoryModeEnabled()) {
         if (ConfigManager.getServerConfig().getGameplay().getCreateDefaultSagas()) {
            Path dmzBase = sagaDir.getParent();
            writeSagaManifest(dmzBase, sagaDir, "saiyan_saga.json", "saiyan_saga", "dmz.saga.saiyan_saga", "", "saga_saiyan");
            writeSagaManifest(dmzBase, sagaDir, "frieza_saga.json", "frieza_saga", "dmz.saga.frieza_saga", "saiyan_saga", "saga_frieza");
            writeSagaManifest(dmzBase, sagaDir, "android_saga.json", "android_saga", "dmz.saga.android_saga", "frieza_saga", "saga_android");
            writeSagaManifest(dmzBase, sagaDir, "future_saga.json", "future_saga", "dmz.saga.future_saga", "android_saga", "saga_future");
            writeSagaManifest(dmzBase, sagaDir, "buu_saga.json", "buu_saga", "dmz.saga.buu_saga", "android_saga", "saga_buu");
            writeSagaManifest(dmzBase, sagaDir, "movies_saga.json", "movies_saga", "dmz.saga.movies_saga", "", "saga_movies");
         }
      }
   }

   private static void writeSagaManifest(Path dmzBase, Path sagaDir, String filename, String sagaId, String name, String previousSaga, String questFolder) {
      try {
         Files.createDirectories(sagaDir);
         JsonObject root = new JsonObject();
         root.addProperty("id", sagaId);
         root.addProperty("name", name);
         JsonObject req = new JsonObject();
         req.addProperty("previousSaga", previousSaga);
         root.add("requirements", req);
         root.addProperty("questFolder", questFolder);
         QuestUpgrader.upgradeOrWrite(dmzBase, sagaDir.resolve(filename), root);
      } catch (IOException var9) {
         LogUtil.error(Env.COMMON, "Failed to create saga manifest: {}", filename, var9);
      }
   }
}
