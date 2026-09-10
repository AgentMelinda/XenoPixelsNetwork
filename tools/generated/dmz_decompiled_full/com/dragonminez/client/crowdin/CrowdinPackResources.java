package com.dragonminez.client.crowdin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PackResources.ResourceOutput;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

public class CrowdinPackResources implements PackResources {
   private final PackLocationInfo location;
   private final byte[] packMcmetaBytes;

   public CrowdinPackResources(PackLocationInfo location) {
      this.location = location;
      JsonObject packInfo = new JsonObject();
      packInfo.addProperty("description", "DMZ Live Translations");
      packInfo.addProperty("pack_format", 34);
      JsonObject root = new JsonObject();
      root.add("pack", packInfo);
      this.packMcmetaBytes = root.toString().getBytes(StandardCharsets.UTF_8);
   }

   @Nullable
   public IoSupplier<InputStream> getRootResource(String... elements) {
      return elements.length > 0 && "pack.mcmeta".equals(elements[0]) ? () -> new ByteArrayInputStream(this.packMcmetaBytes) : null;
   }

   @Nullable
   public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
      if (!CrowdinManager.isLiveTranslationsEnabled()) {
         return null;
      } else {
         if (type == PackType.CLIENT_RESOURCES && location.getNamespace().equals("dragonminez") && location.getPath().startsWith("lang/")) {
            String expectedPath = "lang/" + CrowdinManager.getCachedLangCode() + ".json";
            if (location.getPath().equals(expectedPath) && CrowdinManager.hasData()) {
               return CrowdinManager::getStream;
            }
         }

         return null;
      }
   }

   public void listResources(PackType type, String namespace, String path, ResourceOutput resourceOutput) {
   }

   public Set<String> getNamespaces(PackType type) {
      return type == PackType.CLIENT_RESOURCES ? Set.of("dragonminez") : Collections.emptySet();
   }

   @Nullable
   public <T> T getMetadataSection(MetadataSectionSerializer<T> deserializer) {
      try {
         Object var5;
         try (InputStreamReader reader = new InputStreamReader(new ByteArrayInputStream(this.packMcmetaBytes), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            String sectionName = deserializer.getMetadataSectionName();
            if (json.has(sectionName)) {
               return (T)deserializer.fromJson(json.getAsJsonObject(sectionName));
            }

            var5 = null;
         }

         return (T)var5;
      } catch (Exception var8) {
         return null;
      }
   }

   public PackLocationInfo location() {
      return this.location;
   }

   public void close() {
   }

   public boolean isHidden() {
      return false;
   }
}
