package com.dragonminez.server.storage;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public class JsonStorage implements IDataStorage {
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private Path storageDir;

   @Override
   public void init() {
      MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
      if (server != null) {
         this.storageDir = server.getWorldPath(LevelResource.ROOT).resolve("dragonminez").resolve("playerdata_json");

         try {
            Files.createDirectories(this.storageDir);
            LogUtil.info(Env.SERVER, "JSON Storage initialized at: " + this.storageDir);
         } catch (IOException var3) {
            LogUtil.error(Env.SERVER, "Failed to create JSON storage directory", var3);
         }
      }
   }

   @Override
   public void shutdown() {
   }

   @Override
   public CompoundTag loadData(UUID playerUUID) {
      if (this.storageDir == null) {
         return null;
      } else {
         Path file = this.storageDir.resolve(playerUUID.toString() + ".json");
         if (!Files.exists(file)) {
            return null;
         } else {
            try {
               CompoundTag var10;
               try (Reader reader = Files.newBufferedReader(file)) {
                  JsonElement json = (JsonElement)GSON.fromJson(reader, JsonElement.class);
                  if (json == null) {
                     return null;
                  }

                  if (json.isJsonObject()) {
                     JsonObject obj = json.getAsJsonObject();
                     if (obj.has("data") && obj.has("format") && "snbt".equals(obj.get("format").getAsString())) {
                        return TagParser.parseTag(obj.get("data").getAsString());
                     }
                  }

                  var10 = (CompoundTag)JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json);
               }

               return var10;
            } catch (Exception var9) {
               LogUtil.error(Env.SERVER, "Failed to load JSON data for " + playerUUID, var9);
               return null;
            }
         }
      }
   }

   @Override
   public boolean saveData(UUID playerUUID, String playerName, CompoundTag data) {
      if (this.storageDir == null) {
         return false;
      } else {
         Path file = this.storageDir.resolve(playerUUID.toString() + ".json");
         Path tempFile = this.storageDir.resolve(playerUUID.toString() + ".json.tmp");

         try {
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
               JsonObject wrapper = new JsonObject();
               wrapper.addProperty("format", "snbt");
               wrapper.addProperty("data", NbtUtils.structureToSnbt(data));
               GSON.toJson(wrapper, writer);
            }

            try {
               Files.move(tempFile, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException var10) {
               Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
         } catch (IOException var12) {
            LogUtil.error(Env.SERVER, "Failed to save JSON data for " + playerName, var12);
            return false;
         }
      }
   }

   @Override
   public String getName() {
      return "JSON";
   }
}
