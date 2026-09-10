package com.dragonminez.server.world.dimension;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.storage.RegionFile;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.storage.LevelResource;

public class OtherworldRegionLoader {
   private static final String RESOURCE_PATH = "/data/dragonminez/regions/otherworld/";
   private static final long MIN_VALID_REGION_BYTES = 1048576L;
   private static final Set<String> GENERATED_TERRAIN_BLOCKS = Set.of("minecraft:air", "minecraft:bedrock", "dragonminez:otherworld_cloud");

   public static void loadPreGeneratedRegions(MinecraftServer server) {
      try {
         Path worldPath = server.getWorldPath(LevelResource.ROOT);
         Path regionDestPath = worldPath.resolve("dimensions").resolve("dragonminez").resolve("otherworld").resolve("region");
         if (!Files.exists(regionDestPath)) {
            LogUtil.info(Env.SERVER, "Creating region directory at: {}", regionDestPath);
            Files.createDirectories(regionDestPath);
         }

         String[] regionFiles = new String[]{"r.0.0.mca", "r.0.1.mca", "r.0.2.mca", "r.0.-1.mca", "r.-1.0.mca", "r.-1.1.mca", "r.-1.2.mca", "r.-1.-1.mca"};
         int copiedFiles = 0;
         int repairedFiles = 0;

         for (String fileName : regionFiles) {
            Path destFile = regionDestPath.resolve(fileName);
            OtherworldRegionLoader.RegionAction action = inspectExistingRegion(fileName, destFile);
            if (action == OtherworldRegionLoader.RegionAction.COPY_MISSING && copyRegionFile(fileName, destFile)) {
               copiedFiles++;
            } else if (action.shouldRepair()) {
               backupRegionFile(destFile, action);
               if (copyRegionFile(fileName, destFile)) {
                  repairedFiles++;
               }
            }
         }

         LogUtil.info(Env.SERVER, "Region loader finished. New: {}, Repaired: {}", copiedFiles, repairedFiles);
      } catch (IOException var12) {
         LogUtil.error(Env.SERVER, "Fatal IO error loading regions: {}", var12.getMessage());
         var12.printStackTrace();
      }
   }

   static OtherworldRegionLoader.RegionAction inspectExistingRegion(String fileName, Path destFile) throws IOException {
      if (!Files.exists(destFile)) {
         return OtherworldRegionLoader.RegionAction.COPY_MISSING;
      } else {
         long fileSize = Files.size(destFile);
         if (fileSize < 1048576L) {
            return OtherworldRegionLoader.RegionAction.REPAIR_EMPTY;
         } else {
            Set<String> requiredStructureBlocks = readBundledStructureBlocks(fileName);
            if (requiredStructureBlocks.isEmpty()) {
               return OtherworldRegionLoader.RegionAction.KEEP;
            } else {
               Set<String> existingBlocks = readRegionPaletteBlocks(destFile, fileName);
               return existingBlocks.containsAll(requiredStructureBlocks)
                  ? OtherworldRegionLoader.RegionAction.KEEP
                  : OtherworldRegionLoader.RegionAction.REPAIR_STALE;
            }
         }
      }
   }

   private static Set<String> readBundledStructureBlocks(String fileName) throws IOException {
      Path tempFile = Files.createTempFile("dmz-otherworld-" + fileName, ".mca");

      Set var3;
      try {
         if (!copyRegionFile(fileName, tempFile)) {
            throw new IOException("Could not load bundled Otherworld region " + fileName);
         }

         Set<String> blocks = readRegionPaletteBlocks(tempFile, fileName);
         blocks.removeAll(GENERATED_TERRAIN_BLOCKS);
         var3 = blocks;
      } finally {
         Files.deleteIfExists(tempFile);
      }

      return var3;
   }

   private static Set<String> readRegionPaletteBlocks(Path regionFilePath, String fileName) throws IOException {
      Set<String> blockNames = new HashSet<>();
      OtherworldRegionLoader.RegionCoordinates coordinates = OtherworldRegionLoader.RegionCoordinates.parse(fileName);
      RegionFile regionFile = new RegionFile(
         new RegionStorageInfo("dragonminez", Level.OVERWORLD, "otherworld_scan"), regionFilePath, regionFilePath.getParent(), false
      );

      try {
         for (int localX = 0; localX < 32; localX++) {
            for (int localZ = 0; localZ < 32; localZ++) {
               ChunkPos chunkPos = new ChunkPos(coordinates.regionX() * 32 + localX, coordinates.regionZ() * 32 + localZ);

               try (DataInputStream stream = regionFile.getChunkDataInputStream(chunkPos)) {
                  if (stream != null) {
                     collectChunkPaletteBlocks(NbtIo.read(stream), blockNames);
                  }
               }
            }
         }
      } catch (Throwable var14) {
         try {
            regionFile.close();
         } catch (Throwable var11) {
            var14.addSuppressed(var11);
         }

         throw var14;
      }

      regionFile.close();
      return blockNames;
   }

   private static void collectChunkPaletteBlocks(CompoundTag chunkTag, Set<String> blockNames) {
      ListTag sections = chunkTag.getList("sections", 10);

      for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
         CompoundTag section = sections.getCompound(sectionIndex);
         CompoundTag blockStates = section.getCompound("block_states");
         ListTag palette = blockStates.getList("palette", 10);

         for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
            String blockName = palette.getCompound(paletteIndex).getString("Name");
            if (!blockName.isBlank()) {
               blockNames.add(blockName);
            }
         }
      }
   }

   private static void backupRegionFile(Path destFile, OtherworldRegionLoader.RegionAction action) throws IOException {
      Path backupFile = nextBackupPath(destFile);
      Files.copy(destFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
      LogUtil.warn(
         Env.SERVER, "Repairing Otherworld region file {} ({}). Backup saved as {}", destFile.getFileName(), action.logReason(), backupFile.getFileName()
      );
   }

   private static Path nextBackupPath(Path destFile) {
      Path parent = destFile.getParent();
      String fileName = destFile.getFileName().toString();

      for (int index = 0; index < 1000; index++) {
         String suffix = index == 0 ? ".dmzbak" : ".dmzbak." + index;
         Path backupFile = parent.resolve(fileName + suffix);
         if (!Files.exists(backupFile)) {
            return backupFile;
         }
      }

      return parent.resolve(fileName + ".dmzbak.latest");
   }

   private static boolean copyRegionFile(String fileName, Path destFile) {
      String resourcePath = "/data/dragonminez/regions/otherworld/" + fileName;
      InputStream inputStream = OtherworldRegionLoader.class.getResourceAsStream(resourcePath);
      if (inputStream == null) {
         inputStream = OtherworldRegionLoader.class.getResourceAsStream(resourcePath.substring(1));
      }

      if (inputStream == null) {
         inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath.substring(1));
      }

      if (inputStream == null) {
         LogUtil.error(Env.SERVER, "FATAL: Could not find {} in JAR resources at {}", fileName, resourcePath);
         return false;
      } else {
         try {
            InputStream stream = inputStream;

            boolean var5;
            try {
               Files.copy(stream, destFile, StandardCopyOption.REPLACE_EXISTING);
               var5 = true;
            } catch (Throwable var8) {
               if (inputStream != null) {
                  try {
                     stream.close();
                  } catch (Throwable var7) {
                     var8.addSuppressed(var7);
                  }
               }

               throw var8;
            }

            if (inputStream != null) {
               inputStream.close();
            }

            return var5;
         } catch (IOException var9) {
            LogUtil.error(Env.SERVER, "Failed to copy {}: {}", fileName, var9.getMessage());
            return false;
         }
      }
   }

   static enum RegionAction {
      COPY_MISSING(null),
      REPAIR_EMPTY("empty or header-only"),
      REPAIR_STALE("missing bundled structure blocks"),
      KEEP(null);

      @Nullable
      private final String logReason;

      private RegionAction(@Nullable String logReason) {
         this.logReason = logReason;
      }

      boolean shouldRepair() {
         return this == REPAIR_EMPTY || this == REPAIR_STALE;
      }

      String logReason() {
         return this.logReason == null ? this.name().toLowerCase(Locale.ROOT) : this.logReason;
      }
   }

   private static record RegionCoordinates(int regionX, int regionZ) {
      private static OtherworldRegionLoader.RegionCoordinates parse(String fileName) throws IOException {
         if (fileName.startsWith("r.") && fileName.endsWith(".mca")) {
            String[] parts = fileName.substring(2, fileName.length() - 4).split("\\.");
            if (parts.length != 2) {
               throw new IOException("Invalid region file name " + fileName);
            } else {
               try {
                  return new OtherworldRegionLoader.RegionCoordinates(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
               } catch (NumberFormatException var3) {
                  throw new IOException("Invalid region file name " + fileName, var3);
               }
            }
         } else {
            throw new IOException("Invalid region file name " + fileName);
         }
      }
   }
}
