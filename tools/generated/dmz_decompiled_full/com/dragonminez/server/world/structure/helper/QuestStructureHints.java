package com.dragonminez.server.world.structure.helper;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.quest.QuestPrerequisites;
import com.dragonminez.server.world.dimension.HTCDimension;
import com.dragonminez.server.world.dimension.NamekDimension;
import com.mojang.datafixers.util.Pair;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

public final class QuestStructureHints {
   private static final Map<String, Pair<ResourceKey<Structure>, ResourceKey<Level>>> DMZ_STRUCTURE_INFO = Map.of(
      "dragonminez:goku_house",
      Pair.of(DMZStructures.GOKU_HOUSE, Level.OVERWORLD),
      "dragonminez:roshi_house",
      Pair.of(DMZStructures.ROSHI_HOUSE, Level.OVERWORLD),
      "dragonminez:elder_guru",
      Pair.of(DMZStructures.ELDER_GURU, NamekDimension.NAMEK_KEY),
      "dragonminez:timechamber",
      Pair.of(DMZStructures.TIMECHAMBER, HTCDimension.HTC_KEY),
      "dragonminez:kamilookout",
      Pair.of(DMZStructures.KAMILOOKOUT, Level.OVERWORLD),
      "dragonminez:gero_lab",
      Pair.of(DMZStructures.GERO_LAB, Level.OVERWORLD),
      "dragonminez:babidi",
      Pair.of(DMZStructures.BABIDI, Level.OVERWORLD)
   );
   private static final Map<String, QuestPrerequisites.StructureHint> CACHE = new ConcurrentHashMap<>();
   private static volatile CompletableFuture<Void> resolveFuture;

   private QuestStructureHints() {
   }

   public static QuestPrerequisites.StructureHint getCached(String structureId) {
      String normalized = normalize(structureId);
      return normalized == null ? null : CACHE.get(normalized);
   }

   public static boolean isResolved() {
      CompletableFuture<Void> f = resolveFuture;
      return f != null && f.isDone();
   }

   public static CompletableFuture<Void> ensureResolvedAsync(MinecraftServer server) {
      CompletableFuture<Void> f = resolveFuture;
      if (f != null) {
         return f;
      } else {
         synchronized (QuestStructureHints.class) {
            if (resolveFuture == null) {
               resolveFuture = CompletableFuture.runAsync(() -> resolveAll(server), Util.backgroundExecutor());
            }

            return resolveFuture;
         }
      }
   }

   private static void resolveAll(MinecraftServer server) {
      for (Entry<String, Pair<ResourceKey<Structure>, ResourceKey<Level>>> entry : DMZ_STRUCTURE_INFO.entrySet()) {
         String id = entry.getKey();
         if (!CACHE.containsKey(id)) {
            try {
               QuestPrerequisites.StructureHint hint = resolve(server, id, entry.getValue());
               if (hint != null) {
                  CACHE.put(id, hint);
               }
            } catch (Exception var5) {
               LogUtil.error(Env.COMMON, "QuestStructureHints: failed to resolve hint for '" + id + "': " + var5.getMessage());
            }
         }
      }
   }

   private static QuestPrerequisites.StructureHint resolve(MinecraftServer server, String structureId, Pair<ResourceKey<Structure>, ResourceKey<Level>> info) {
      String dimensionId = ((ResourceKey)info.getSecond()).location().toString();
      if (server == null) {
         return new QuestPrerequisites.StructureHint(dimensionId, null, null, null);
      } else {
         ServerLevel targetLevel = server.getLevel((ResourceKey)info.getSecond());
         if (targetLevel == null) {
            return new QuestPrerequisites.StructureHint(dimensionId, null, null, null);
         } else {
            BlockPos structurePos = StructureLocator.locateStructure(targetLevel, (ResourceKey<Structure>)info.getFirst(), targetLevel.getSharedSpawnPos());
            return structurePos == null
               ? new QuestPrerequisites.StructureHint(dimensionId, null, null, null)
               : new QuestPrerequisites.StructureHint(dimensionId, structurePos.getX(), structurePos.getY(), structurePos.getZ());
         }
      }
   }

   private static String normalize(String structureId) {
      if (structureId != null && !structureId.isBlank() && structureId.contains(":")) {
         try {
            return ResourceLocation.parse(structureId).toString();
         } catch (Exception var2) {
            return null;
         }
      } else {
         return null;
      }
   }
}
