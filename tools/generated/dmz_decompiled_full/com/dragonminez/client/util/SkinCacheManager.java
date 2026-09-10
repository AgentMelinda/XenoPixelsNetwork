package com.dragonminez.client.util;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.util.BetaWhitelist;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

public final class SkinCacheManager {
   private static final String CACHE_DIR_NAME = "dmz_skincache";
   private static final String INDEX_FILE = "index.json";
   private static final int CONNECT_TIMEOUT_MS = 6000;
   private static final int READ_TIMEOUT_MS = 6000;
   private static final long THROTTLE_DELAY_MS = 150L;
   private static final int MAX_CONCURRENT_DOWNLOADS = 2;
   private static final long GATE_POLL_MS = 500L;
   private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
   private static final Type INDEX_TYPE = (new TypeToken<HashMap<String, SkinCacheManager.CacheEntry>>() {
   }).getType();
   private static final Map<String, SkinCacheManager.CacheEntry> index = new ConcurrentHashMap<>();
   private static final Map<String, ResourceLocation> registered = new ConcurrentHashMap<>();
   private static final Set<String> inFlight = ConcurrentHashMap.newKeySet();
   private static final BlockingQueue<SkinCacheManager.Task> downloadQueue = new LinkedBlockingQueue<>();
   private static Path cacheDir;
   private static boolean initialized;

   private SkinCacheManager() {
   }

   public static synchronized void init() {
      if (!initialized) {
         initialized = true;
         startWorkers();

         try {
            cacheDir = Minecraft.getInstance().gameDirectory.toPath().resolve("dmz_skincache");
            Files.createDirectories(cacheDir);
            loadIndex();
            registerCachedFromDisk();
         } catch (Exception var1) {
            LogUtil.error(Env.CLIENT, "SkinCache: failed to init cache dir: " + var1.getMessage());
         }

         preloadAll();
      }
   }

   public static void preloadAll() {
      if (initialized) {
         for (String name : BetaWhitelist.getSkinCandidates()) {
            queueResolve(name, false);
         }
      }
   }

   public static void revalidate() {
      if (initialized) {
         registered.clear();
         registerCachedFromDisk();

         for (String name : BetaWhitelist.getSkinCandidates()) {
            queueResolve(name, true);
         }
      }
   }

   public static ResourceLocation resolveTexture(String username) {
      if (username != null && !username.isEmpty()) {
         String key = username.toLowerCase();
         ResourceLocation loc = registered.get(key);
         if (loc != null) {
            return loc;
         } else {
            if (initialized && !index.containsKey(key)) {
               queueResolve(username, false);
            }

            return DefaultPlayerSkin.getDefaultTexture();
         }
      } else {
         return DefaultPlayerSkin.getDefaultTexture();
      }
   }

   private static void queueResolve(String username, boolean forceRecheck) {
      if (username != null && !username.isEmpty()) {
         String key = username.toLowerCase();
         if (inFlight.add(key)) {
            downloadQueue.add(new SkinCacheManager.Task(username, forceRecheck));
         }
      }
   }

   private static void startWorkers() {
      for (int i = 0; i < 2; i++) {
         Thread t = new Thread(SkinCacheManager::workerLoop, "DMZ-SkinCache-" + (i + 1));
         t.setDaemon(true);
         t.start();
      }
   }

   private static void workerLoop() {
      while (true) {
         SkinCacheManager.Task task;
         try {
            task = downloadQueue.take();
         } catch (InterruptedException var8) {
            Thread.currentThread().interrupt();
            return;
         }

         String key = task.username().toLowerCase();

         try {
            awaitDownloadableState();
            resolveBlocking(task.username(), task.force());
            Thread.sleep(150L);
            continue;
         } catch (InterruptedException var9) {
            Thread.currentThread().interrupt();
         } catch (Exception var10) {
            LogUtil.error(Env.CLIENT, "SkinCache: error resolving {}: {}", task.username(), var10.getMessage());
            continue;
         } finally {
            inFlight.remove(key);
         }

         return;
      }
   }

   private static void awaitDownloadableState() throws InterruptedException {
      while (!canDownloadNow()) {
         Thread.sleep(500L);
      }
   }

   private static boolean canDownloadNow() {
      Minecraft mc = Minecraft.getInstance();
      if (mc == null) {
         return false;
      } else if (mc.getConnection() != null) {
         return false;
      } else {
         Screen screen = mc.screen;
         return !(screen instanceof LevelLoadingScreen)
            && !(screen instanceof ReceivingLevelScreen)
            && !(screen instanceof ProgressScreen)
            && !(screen instanceof GenericMessageScreen);
      }
   }

   private static void resolveBlocking(String username, boolean forceRecheck) {
      String key = username.toLowerCase();
      SkinCacheManager.CacheEntry entry = index.get(key);
      if (entry == null || entry.state != SkinCacheManager.State.NO_PREMIUM) {
         if (forceRecheck || entry == null || entry.state != SkinCacheManager.State.OK || !registered.containsKey(key)) {
            String uuid = fetchUuid(username);
            if (uuid != null) {
               String skinUrl = fetchSkinUrl(uuid);
               if (skinUrl == null) {
                  putState(key, SkinCacheManager.State.NO_SKIN, null, null);
               } else {
                  String hash = hashFromUrl(skinUrl);
                  if (entry == null || entry.state != SkinCacheManager.State.OK || !hash.equals(entry.skinHash) || !registered.containsKey(key)) {
                     if (entry != null && entry.file != null) {
                        deleteFileQuietly(entry.file);
                     }

                     byte[] png = downloadBytes(skinUrl);
                     if (png == null) {
                        putState(key, SkinCacheManager.State.NO_SKIN, hash, null);
                     } else {
                        String fileName = key + "-" + hash + ".png";

                        try {
                           Files.write(cacheDir.resolve(fileName), png);
                        } catch (IOException var10) {
                           LogUtil.error(Env.CLIENT, "SkinCache: failed to write {}: {}", fileName, var10.getMessage());
                           putState(key, SkinCacheManager.State.NO_SKIN, hash, null);
                           return;
                        }

                        registerTexture(key, png);
                        putState(key, SkinCacheManager.State.OK, hash, fileName);
                        LogUtil.info(Env.CLIENT, "SkinCache: cached skin for {} ({})", username, hash);
                     }
                  }
               }
            }
         }
      }
   }

   private static String fetchUuid(String username) {
      try {
         String body = httpGet("https://api.mojang.com/users/profiles/minecraft/" + username);
         if (body != null && !body.isEmpty()) {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            if (json.has("id")) {
               return json.get("id").getAsString();
            } else {
               putState(username.toLowerCase(), SkinCacheManager.State.NO_PREMIUM, null, null);
               return null;
            }
         } else {
            putState(username.toLowerCase(), SkinCacheManager.State.NO_PREMIUM, null, null);
            return null;
         }
      } catch (IOException var3) {
         putState(username.toLowerCase(), SkinCacheManager.State.TIMEOUT, null, null);
         return null;
      } catch (Exception var4) {
         putState(username.toLowerCase(), SkinCacheManager.State.NO_PREMIUM, null, null);
         return null;
      }
   }

   private static String fetchSkinUrl(String uuid) {
      try {
         String body = httpGet("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid);
         if (body == null) {
            return null;
         } else {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            JsonArray properties = json.getAsJsonArray("properties");
            if (properties == null) {
               return null;
            } else {
               for (JsonElement el : properties) {
                  JsonObject prop = el.getAsJsonObject();
                  if ("textures".equals(prop.get("name").getAsString())) {
                     String decoded = new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
                     JsonObject tex = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
                     if (tex != null && tex.has("SKIN")) {
                        return tex.getAsJsonObject("SKIN").get("url").getAsString();
                     }
                  }
               }

               return null;
            }
         }
      } catch (Exception var9) {
         return null;
      }
   }

   private static String httpGet(String urlStr) throws IOException {
      HttpURLConnection conn = (HttpURLConnection)new URL(urlStr).openConnection();
      conn.setRequestMethod("GET");
      conn.setConnectTimeout(6000);
      conn.setReadTimeout(6000);
      conn.setRequestProperty("User-Agent", "DragonMineZ/dragonminez");

      String var4;
      try {
         int code = conn.getResponseCode();
         if (code == 204 || code == 404) {
            return null;
         }

         if (code != 200) {
            throw new IOException("HTTP " + code);
         }

         try (InputStream is = conn.getInputStream()) {
            var4 = new String(is.readAllBytes(), StandardCharsets.UTF_8);
         }
      } finally {
         conn.disconnect();
      }

      return var4;
   }

   private static byte[] downloadBytes(String urlStr) {
      try {
         HttpURLConnection conn = (HttpURLConnection)new URL(urlStr).openConnection();
         conn.setConnectTimeout(6000);
         conn.setReadTimeout(6000);

         Object is;
         try {
            if (conn.getResponseCode() == 200) {
               try (InputStream isx = conn.getInputStream()) {
                  return isx.readAllBytes();
               }
            }

            is = null;
         } finally {
            conn.disconnect();
         }

         return (byte[])is;
      } catch (Exception var13) {
         return null;
      }
   }

   private static String hashFromUrl(String url) {
      int slash = url.lastIndexOf(47);
      return slash >= 0 ? url.substring(slash + 1) : url;
   }

   private static void registerCachedFromDisk() {
      for (Entry<String, SkinCacheManager.CacheEntry> e : index.entrySet()) {
         SkinCacheManager.CacheEntry entry = e.getValue();
         if (entry.state == SkinCacheManager.State.OK && entry.file != null) {
            Path file = cacheDir.resolve(entry.file);
            if (!Files.exists(file)) {
               entry.state = SkinCacheManager.State.NO_SKIN;
            } else {
               try {
                  registerTexture(e.getKey(), Files.readAllBytes(file));
               } catch (IOException var5) {
                  LogUtil.error(Env.CLIENT, "SkinCache: failed to load cached {}: {}", entry.file, var5.getMessage());
               }
            }
         }
      }
   }

   private static void registerTexture(String key, byte[] png) {
      Minecraft.getInstance().execute(() -> {
         NativeImage image;
         try {
            image = NativeImage.read(new ByteArrayInputStream(png));
         } catch (Exception var4) {
            LogUtil.error(Env.CLIENT, "SkinCache: failed to decode texture for {}: {}", key, var4.getMessage());
            return;
         }

         if (image.getHeight() < 64) {
            image.close();
         } else {
            ResourceLocation loc = ResourceLocation.fromNamespaceAndPath("dragonminez", "skins/" + key);
            Minecraft.getInstance().getTextureManager().register(loc, new DynamicTexture(image));
            registered.put(key, loc);
         }
      });
   }

   private static void putState(String key, SkinCacheManager.State state, String hash, String file) {
      index.put(key, new SkinCacheManager.CacheEntry(state, hash, file));
      saveIndex();
   }

   private static synchronized void loadIndex() {
      Path file = cacheDir.resolve("index.json");
      if (Files.exists(file)) {
         try {
            String json = Files.readString(file);
            Map<String, SkinCacheManager.CacheEntry> loaded = (Map<String, SkinCacheManager.CacheEntry>)GSON.fromJson(json, INDEX_TYPE);
            if (loaded != null) {
               index.putAll(loaded);
            }
         } catch (Exception var3) {
            LogUtil.error(Env.CLIENT, "SkinCache: failed to read index: " + var3.getMessage());
         }
      }
   }

   private static synchronized void saveIndex() {
      if (cacheDir != null) {
         try {
            Files.writeString(cacheDir.resolve("index.json"), GSON.toJson(index, INDEX_TYPE));
         } catch (IOException var1) {
            LogUtil.error(Env.CLIENT, "SkinCache: failed to write index: " + var1.getMessage());
         }
      }
   }

   private static void deleteFileQuietly(String fileName) {
      try {
         Files.deleteIfExists(cacheDir.resolve(fileName));
      } catch (IOException var2) {
      }
   }

   public static final class CacheEntry {
      SkinCacheManager.State state;
      String skinHash;
      String file;

      CacheEntry() {
      }

      CacheEntry(SkinCacheManager.State state, String skinHash, String file) {
         this.state = state;
         this.skinHash = skinHash;
         this.file = file;
      }
   }

   public static enum State {
      OK,
      NO_PREMIUM,
      NO_SKIN,
      TIMEOUT;
   }

   private static record Task(String username, boolean force) {
   }
}
