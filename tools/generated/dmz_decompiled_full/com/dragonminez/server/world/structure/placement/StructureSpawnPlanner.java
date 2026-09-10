package com.dragonminez.server.world.structure.placement;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.GeneralServerConfig;
import com.dragonminez.server.world.data.StructurePlanSavedData;
import com.dragonminez.server.world.structure.TallJigsawStructure;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

public final class StructureSpawnPlanner {
   private static final int EXCLUSION_CHUNK_RADIUS = 3;
   private static final int CENTER_CHUNK_X = 0;
   private static final int CENTER_CHUNK_Z = 0;
   private static final int RING_STEP = 64;
   private static final int TAIL_EXTRA_RINGS = 256;
   private static final int TAIL_CHUNK_STRIDE = 3;
   private static final long STARTUP_WAIT_SECONDS = 30L;
   private static final int FLATNESS_MAX_SPREAD = 20;
   private static final int FLATNESS_SCAN_EXTRA_RINGS = 5;
   private static final int ABSOLUTE_SCAN_CAP_RINGS = 96;
   private static final long SEARCH_SAMPLE_BUDGET = 120000L;
   private static final TreeMap<Integer, BiomeAwareUniquePlacement> REGISTERED = new TreeMap<>();
   private static final TreeMap<Integer, UniqueNearSpawnPlacement> NEAR_SPAWN_RESERVED = new TreeMap<>();
   private static final ConcurrentHashMap<StructureSpawnPlanner.PlanKey, StructureSpawnPlanner.PlanHolder> PLANS = new ConcurrentHashMap<>();
   private static volatile StructureSpawnPlanner.PlanHolder lastHolder = null;
   private static volatile int planEpoch = 0;
   private static Field biomeSourceField = null;

   private StructureSpawnPlanner() {
   }

   static synchronized void register(BiomeAwareUniquePlacement placement) {
      REGISTERED.put(placement.placementSalt(), placement);
      invalidate();
   }

   static synchronized void registerReservation(UniqueNearSpawnPlacement placement) {
      NEAR_SPAWN_RESERVED.put(placement.placementSalt(), placement);
      invalidate();
   }

   public static void reset() {
      invalidate();
   }

   private static synchronized void invalidate() {
      planEpoch++;
      PLANS.clear();
      lastHolder = null;
   }

   private static boolean isStale(int buildEpoch) {
      return buildEpoch != planEpoch;
   }

   public static void onLevelLoad(ServerLevel level) {
      if (level != null) {
         if (ConfigManager.getServerConfig().getWorldGen().getGenerateCustomStructures()) {
            ServerChunkCache chunkSource = level.getChunkSource();
            ChunkGeneratorStructureState state = chunkSource.getGeneratorState();
            RandomState randomState = chunkSource.randomState();
            if (state != null && randomState != null) {
               BiomeSource biomeSource = getBiomeSourceReflection(state);
               if (biomeSource != null) {
                  StructureSpawnPlanner.PlanHolder holder = obtainHolder(level.getSeed(), biomeSource, randomState, state);
                  StructurePlanSavedData saved = StructurePlanSavedData.get(level);
                  Map<Integer, ChunkPos> savedPositions = saved.getPositions();
                  if (!savedPositions.isEmpty()) {
                     holder.publish(savedPositions);
                  }

                  if (saved.isResolved() && savedPositions.keySet().containsAll(expectedSalts(state, biomeSource))) {
                     holder.started.set(true);
                  } else if (level.dimension().equals(Level.OVERWORLD)) {
                     if (holder.started.compareAndSet(false, true)) {
                        StructureAsyncResolver.buildPlanSync(holder);
                     }
                  } else {
                     ensureBuildStarted(holder);
                  }
               }
            }
         }
      }
   }

   private static Set<Integer> expectedSalts(ChunkGeneratorStructureState state, BiomeSource biomeSource) {
      Map<Integer, HolderSet<Biome>> structureBiomes = buildStructureBiomes(state);
      Set<Integer> expected = new HashSet<>();
      synchronized (StructureSpawnPlanner.class) {
         for (BiomeAwareUniquePlacement placement : REGISTERED.values()) {
            int salt = placement.placementSalt();
            if (structureBiomes.containsKey(salt) && biomeSourceHasAny(biomeSource, placement.getValidBiomes())) {
               expected.add(salt);
            }
         }

         return expected;
      }
   }

   public static Map<Integer, ChunkPos> publishedPositions(ServerLevel level) {
      StructureSpawnPlanner.PlanHolder holder = findExistingHolder(level);
      if (holder == null) {
         return Collections.emptyMap();
      } else {
         Map<Integer, ChunkPos> positions = holder.positions;
         return positions == null ? Collections.emptyMap() : positions;
      }
   }

   public static void relocate(ServerLevel level, int salt) {
      StructureSpawnPlanner.PlanHolder holder = findExistingHolder(level);
      if (holder != null) {
         synchronized (holder.writeLock) {
            Map<Integer, ChunkPos> current = holder.positions;
            if (current == null || !current.containsKey(salt)) {
               return;
            }

            Map<Integer, ChunkPos> next = new HashMap<>(current);
            holder.excluded.add(next.remove(salt));
            holder.positions = Collections.unmodifiableMap(next);
         }

         StructurePlanSavedData.get(level).removePosition(salt);
         LogUtil.info(Env.SERVER, "[DMZ] Relocating structure with salt " + salt + " in " + level.dimension().location() + "; previous site was unusable.");
         StructureAsyncResolver.buildPlan(holder);
      }
   }

   private static StructureSpawnPlanner.PlanHolder findExistingHolder(ServerLevel level) {
      ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
      BiomeSource biomeSource = getBiomeSourceReflection(state);
      if (biomeSource == null) {
         return null;
      } else {
         StructureSpawnPlanner.PlanHolder cached = lastHolder;
         return cached != null && cached.seed == level.getSeed() && cached.biomeSource == biomeSource
            ? cached
            : PLANS.get(new StructureSpawnPlanner.PlanKey(level.getSeed(), biomeSource));
      }
   }

   public static void precomputeAndWait(MinecraftServer server) {
      if (server != null) {
         if (ConfigManager.getServerConfig().getWorldGen().getGenerateCustomStructures()) {
            StructureSpawnPlanner.PlanHolder overworldHolder = null;

            for (ServerLevel level : server.getAllLevels()) {
               try {
                  ServerChunkCache chunkSource = level.getChunkSource();
                  ChunkGeneratorStructureState state = chunkSource.getGeneratorState();
                  RandomState randomState = chunkSource.randomState();
                  if (state != null && randomState != null) {
                     BiomeSource biomeSource = getBiomeSourceReflection(state);
                     if (biomeSource != null) {
                        onLevelLoad(level);
                        if (level.dimension().equals(Level.OVERWORLD)) {
                           overworldHolder = obtainHolder(level.getSeed(), biomeSource, randomState, state);
                        }
                     }
                  }
               } catch (Throwable var8) {
                  LogUtil.error(Env.SERVER, "[DMZ] Structure precompute failed for a level: " + var8.getMessage());
               }
            }

            if (overworldHolder != null && overworldHolder.positions == null) {
               overworldHolder.awaitReady(30L);
            }
         }
      }
   }

   static ChunkPos getPositionFor(
      BiomeAwareUniquePlacement placement, long worldSeed, BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState state
   ) {
      if (biomeSource != null && randomState != null) {
         StructureSpawnPlanner.PlanHolder holder = obtainHolder(worldSeed, biomeSource, randomState, state);
         ensureBuildStarted(holder);
         Map<Integer, ChunkPos> positions = holder.positions;
         return positions == null ? null : positions.get(placement.placementSalt());
      } else {
         return null;
      }
   }

   private static StructureSpawnPlanner.PlanHolder obtainHolder(
      long worldSeed, BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState state
   ) {
      StructureSpawnPlanner.PlanHolder cached = lastHolder;
      if (cached != null && cached.seed == worldSeed && cached.biomeSource == biomeSource) {
         return cached;
      } else {
         StructureSpawnPlanner.PlanKey key = new StructureSpawnPlanner.PlanKey(worldSeed, biomeSource);
         StructureSpawnPlanner.PlanHolder holder = PLANS.computeIfAbsent(
            key, k -> new StructureSpawnPlanner.PlanHolder(worldSeed, biomeSource, randomState, state)
         );
         lastHolder = holder;
         return holder;
      }
   }

   private static void ensureBuildStarted(StructureSpawnPlanner.PlanHolder holder) {
      if (holder.started.compareAndSet(false, true)) {
         StructureAsyncResolver.buildPlan(holder);
      }
   }

   static void injectResolved(StructureSpawnPlanner.PlanHolder holder, int salt, ChunkPos pos) {
      if (holder != null && pos != null) {
         synchronized (holder.writeLock) {
            Map<Integer, ChunkPos> current = holder.positions;
            Map<Integer, ChunkPos> next = current == null ? new HashMap<>() : new HashMap<>(current);
            next.put(salt, pos);
            holder.positions = Collections.unmodifiableMap(next);
         }
      }
   }

   static List<BiomeAwareUniquePlacement> runBuild(StructureSpawnPlanner.PlanHolder holder, ForkJoinPool searchPool) {
      long buildStartNanos = System.nanoTime();
      int epoch = planEpoch;
      long worldSeed = holder.seed;
      BiomeSource biomeSource = holder.biomeSource;
      RandomState randomState = holder.randomState;
      ChunkGeneratorStructureState state = holder.state;
      StructureSpawnPlanner.WorldGenSettings cfg = new StructureSpawnPlanner.WorldGenSettings();
      ChunkGenerator generator = resolveGeneratorFromServer(state);
      LevelHeightAccessor heightAccessor = generator != null ? LevelHeightAccessor.create(generator.getMinY(), generator.getGenDepth()) : null;
      StructureSpawnPlanner.SampleCache cache = new StructureSpawnPlanner.SampleCache(biomeSource, randomState, generator, heightAccessor);
      double minChunks = (double)cfg.minDistanceFromSpawn / 16.0;
      double maxChunks = Math.max(minChunks + 1.0, (double)cfg.maxDistanceFromSpawn / 16.0);
      double spacingChunks = (double)cfg.minDistanceBetween / 16.0;
      double spacingSqr = spacingChunks * spacingChunks;
      int minRing = (int)Math.floor(minChunks);
      int maxRing = (int)Math.ceil(maxChunks);
      Map<Integer, HolderSet<Biome>> structureBiomes = buildStructureBiomes(state);
      Map<Integer, Integer> structureMinHeights = buildStructureMinHeights(state);
      Map<Integer, String> structureNames = buildStructureNames(state);
      List<Holder<StructureSet>> avoid = collectAvoidableSets(state);
      List<ChunkPos> reservedBaseline = new ArrayList<>();

      for (UniqueNearSpawnPlacement reserved : NEAR_SPAWN_RESERVED.values()) {
         ChunkPos pos = reserved.getStructureChunk(worldSeed);
         if (pos != null) {
            reservedBaseline.add(pos);
         }
      }

      Map<Integer, ChunkPos> existing = holder.positions;
      if (existing != null) {
         reservedBaseline.addAll(existing.values());
      }

      synchronized (holder.excluded) {
         reservedBaseline.addAll(holder.excluded);
      }

      List<BiomeAwareUniquePlacement> targets = new ArrayList<>();

      for (BiomeAwareUniquePlacement placement : REGISTERED.values()) {
         if (structureBiomes.get(placement.placementSalt()) != null
            && biomeSourceHasAny(biomeSource, placement.getValidBiomes())
            && (existing == null || !existing.containsKey(placement.placementSalt()))) {
            targets.add(placement);
         }
      }

      Map<Integer, ChunkPos> independent = new ConcurrentHashMap<>();
      searchIndependent(
         targets, structureBiomes, structureMinHeights, cache, minRing, maxRing, reservedBaseline, spacingSqr, state, avoid, independent, searchPool, epoch
      );
      Map<Integer, ChunkPos> plan = new HashMap<>();
      List<ChunkPos> accepted = new ArrayList<>(reservedBaseline);
      List<BiomeAwareUniquePlacement> notFound = new ArrayList<>();

      for (BiomeAwareUniquePlacement placementx : targets) {
         if (isStale(epoch)) {
            break;
         }

         int salt = placementx.placementSalt();
         ChunkPos candidate = independent.get(salt);
         if (candidate != null && !tooClose(accepted, candidate.x, candidate.z, spacingSqr)) {
            plan.put(salt, candidate);
            accepted.add(candidate);
         } else {
            ChunkPos reconciled = searchNearest(
               placementx,
               structureBiomes.get(salt),
               cache,
               minRing,
               maxRing,
               accepted,
               spacingSqr,
               state,
               avoid,
               structureMinHeights.getOrDefault(salt, Integer.MIN_VALUE),
               epoch,
               1,
               new AtomicLong(120000L)
            );
            if (reconciled != null) {
               plan.put(salt, reconciled);
               accepted.add(reconciled);
            } else if (generator != null && heightAccessor != null && !isStale(epoch)) {
               notFound.add(placementx);
            }
         }
      }

      holder.publish(plan);
      long buildMs = (System.nanoTime() - buildStartNanos) / 1000000L;
      if (!targets.isEmpty()) {
         LogUtil.info(
            Env.SERVER,
            "[DMZ] Structure plan built in "
               + buildMs
               + "ms ("
               + targets.size()
               + " targets, "
               + plan.size()
               + " placed, "
               + notFound.size()
               + " deferred to tail)."
         );

         for (Entry<Integer, ChunkPos> entry : plan.entrySet()) {
            logPlacement(structureNames, entry.getKey(), entry.getValue());
         }
      }

      if (!notFound.isEmpty() && !isStale(epoch)) {
         resolveTail(holder, notFound, structureBiomes, structureMinHeights, structureNames, cache, maxRing, spacingSqr, accepted, state, avoid, epoch);
      }

      persistPlan(holder, epoch);
      return notFound;
   }

   private static void persistPlan(StructureSpawnPlanner.PlanHolder holder, int epoch) {
      if (!isStale(epoch)) {
         Map<Integer, ChunkPos> positions = holder.positions;
         if (positions != null) {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
               ServerLevel level = null;

               for (ServerLevel candidate : server.getAllLevels()) {
                  if (candidate.getChunkSource().getGeneratorState() == holder.state) {
                     level = candidate;
                     break;
                  }
               }

               if (level != null) {
                  boolean complete = positions.keySet().containsAll(expectedSalts(holder.state, holder.biomeSource));
                  ServerLevel targetLevel = level;
                  server.execute(() -> StructurePlanSavedData.get(targetLevel).setPositions(positions, complete));
               }
            }
         }
      }
   }

   private static void searchIndependent(
      List<BiomeAwareUniquePlacement> targets,
      Map<Integer, HolderSet<Biome>> structureBiomes,
      Map<Integer, Integer> structureMinHeights,
      StructureSpawnPlanner.SampleCache cache,
      int minRing,
      int maxRing,
      List<ChunkPos> reservedBaseline,
      double spacingSqr,
      ChunkGeneratorStructureState state,
      List<Holder<StructureSet>> avoid,
      Map<Integer, ChunkPos> out,
      ForkJoinPool searchPool,
      int epoch
   ) {
      Runnable work = () -> targets.parallelStream()
            .forEach(
               placementx -> {
                  if (!isStale(epoch)) {
                     int saltx = placementx.placementSalt();
                     ChunkPos foundx = searchNearest(
                        placementx,
                        structureBiomes.get(saltx),
                        cache,
                        minRing,
                        maxRing,
                        reservedBaseline,
                        spacingSqr,
                        state,
                        avoid,
                        structureMinHeights.getOrDefault(saltx, Integer.MIN_VALUE),
                        epoch,
                        1,
                        new AtomicLong(120000L)
                     );
                     if (foundx != null) {
                        out.put(saltx, foundx);
                     }
                  }
               }
            );

      try {
         searchPool.submit(work).get();
      } catch (Exception var20) {
         out.clear();

         for (BiomeAwareUniquePlacement placement : targets) {
            if (isStale(epoch)) {
               return;
            }

            int salt = placement.placementSalt();
            ChunkPos found = searchNearest(
               placement,
               structureBiomes.get(salt),
               cache,
               minRing,
               maxRing,
               reservedBaseline,
               spacingSqr,
               state,
               avoid,
               structureMinHeights.getOrDefault(salt, Integer.MIN_VALUE),
               epoch,
               1,
               new AtomicLong(120000L)
            );
            if (found != null) {
               out.put(salt, found);
            }
         }
      }
   }

   private static void resolveTail(
      StructureSpawnPlanner.PlanHolder holder,
      List<BiomeAwareUniquePlacement> notFound,
      Map<Integer, HolderSet<Biome>> structureBiomes,
      Map<Integer, Integer> structureMinHeights,
      Map<Integer, String> structureNames,
      StructureSpawnPlanner.SampleCache cache,
      int maxRing,
      double spacingSqr,
      List<ChunkPos> accepted,
      ChunkGeneratorStructureState state,
      List<Holder<StructureSet>> avoid,
      int epoch
   ) {
      int absoluteCap = maxRing + 256;

      for (BiomeAwareUniquePlacement placement : notFound) {
         if (isStale(epoch)) {
            return;
         }

         int salt = placement.placementSalt();
         HolderSet<Biome> structBiomes = structureBiomes.get(salt);
         if (structBiomes != null) {
            int minHeight = structureMinHeights.getOrDefault(salt, Integer.MIN_VALUE);
            AtomicLong budget = new AtomicLong(120000L);
            ChunkPos found = null;
            int from = maxRing + 1;

            while (found == null && from <= absoluteCap && budget.get() > 0L) {
               if (isStale(epoch)) {
                  return;
               }

               int to = Math.min(from + 64 - 1, absoluteCap);
               found = searchNearest(placement, structBiomes, cache, from, to, accepted, spacingSqr, state, avoid, minHeight, epoch, 3, budget);
               from = to + 1;
            }

            if (found == null) {
               System.err
                  .println(
                     "[DMZ] StructureSpawnPlanner: no valid placement found for salt "
                        + placement.placementSalt()
                        + " within "
                        + absoluteCap
                        + " chunks of spawn."
                  );
            } else {
               accepted.add(found);
               injectResolved(holder, placement.placementSalt(), found);
               logPlacement(structureNames, salt, found);
            }
         }
      }
   }

   private static void logPlacement(Map<Integer, String> structureNames, int salt, ChunkPos pos) {
      String name = structureNames.getOrDefault(salt, "salt:" + salt);
      LogUtil.info(Env.SERVER, "[DMZ]   placed " + name + " at x=" + ((pos.x << 4) + 8) + ", z=" + ((pos.z << 4) + 8) + " (chunk " + pos.x + ", " + pos.z + ")");
   }

   private static Map<Integer, String> buildStructureNames(ChunkGeneratorStructureState state) {
      Map<Integer, String> result = new HashMap<>();
      if (state == null) {
         return result;
      } else {
         for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            StructureSet set = (StructureSet)holder.value();
            StructurePlacement name = set.placement();
            if (name instanceof BiomeAwareUniquePlacement) {
               BiomeAwareUniquePlacement placement = (BiomeAwareUniquePlacement)name;
               if (!set.structures().isEmpty()) {
                  String namex = ((StructureSelectionEntry)set.structures().get(0))
                     .structure()
                     .unwrapKey()
                     .map(key -> key.location().toString())
                     .orElse("salt:" + placement.placementSalt());
                  result.put(placement.placementSalt(), namex);
               }
            }
         }

         return result;
      }
   }

   static ChunkPos searchNearest(
      BiomeAwareUniquePlacement placement,
      HolderSet<Biome> structureBiomes,
      StructureSpawnPlanner.SampleCache cache,
      int minRing,
      int maxRing,
      List<ChunkPos> accepted,
      double spacingSqr,
      ChunkGeneratorStructureState state,
      List<Holder<StructureSet>> avoid,
      int minHeight,
      int buildEpoch,
      int chunkStride,
      AtomicLong budget
   ) {
      if (structureBiomes == null) {
         return null;
      } else {
         ChunkPos bestNonOverlap = null;
         int bestNonOverlapSpread = Integer.MAX_VALUE;
         ChunkPos overlapFallback = null;
         int overlapFallbackSpread = Integer.MAX_VALUE;
         int firstValidRing = -1;

         for (int ring = minRing;
            ring <= maxRing
               && !isStale(buildEpoch)
               && ring - minRing <= 96
               && (firstValidRing < 0 || ring - firstValidRing <= 5)
               && (budget == null || budget.get() > 0L);
            ring++
         ) {
            List<ChunkPos> candidates = ringChunks(ring);

            for (int i = 0; i < candidates.size(); i++) {
               if (chunkStride <= 1 || i % chunkStride == 0) {
                  ChunkPos candidate = candidates.get(i);
                  if (!tooClose(accepted, candidate.x, candidate.z, spacingSqr)) {
                     if (budget != null && budget.decrementAndGet() < 0L) {
                        return bestNonOverlap != null ? bestNonOverlap : overlapFallback;
                     }

                     if (biomePrefilter(placement, cache, candidate.x, candidate.z)) {
                        int spread = evaluateCandidate(structureBiomes, cache, candidate.x, candidate.z, minHeight);
                        if (spread >= 0) {
                           if (firstValidRing < 0) {
                              firstValidRing = ring;
                           }

                           if (overlapsOtherStructures(state, avoid, candidate.x, candidate.z)) {
                              if (spread < overlapFallbackSpread) {
                                 overlapFallbackSpread = spread;
                                 overlapFallback = candidate;
                              }
                           } else {
                              if (spread <= 20) {
                                 return candidate;
                              }

                              if (spread < bestNonOverlapSpread) {
                                 bestNonOverlapSpread = spread;
                                 bestNonOverlap = candidate;
                              }
                           }
                        }
                     }
                  }
               }
            }
         }

         return bestNonOverlap != null ? bestNonOverlap : overlapFallback;
      }
   }

   static List<ChunkPos> ringChunks(int ring) {
      List<ChunkPos> out = new ArrayList<>();
      if (ring <= 0) {
         out.add(new ChunkPos(0, 0));
         return out;
      } else {
         for (int dx = -ring; dx <= ring; dx++) {
            out.add(new ChunkPos(0 + dx, 0 - ring));
            out.add(new ChunkPos(0 + dx, 0 + ring));
         }

         for (int dz = -ring + 1; dz <= ring - 1; dz++) {
            out.add(new ChunkPos(0 - ring, 0 + dz));
            out.add(new ChunkPos(0 + ring, 0 + dz));
         }

         out.sort((a, b) -> Long.compare(distSqrToCenter(a), distSqrToCenter(b)));
         return out;
      }
   }

   private static long distSqrToCenter(ChunkPos pos) {
      long dx = (long)pos.x - 0L;
      long dz = (long)pos.z - 0L;
      return dx * dx + dz * dz;
   }

   private static boolean biomeSourceHasAny(BiomeSource biomeSource, HolderSet<Biome> validBiomes) {
      if (biomeSource != null && validBiomes != null) {
         for (Holder<Biome> biome : biomeSource.possibleBiomes()) {
            if (validBiomes.contains(biome)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   private static boolean biomePrefilter(BiomeAwareUniquePlacement placement, StructureSpawnPlanner.SampleCache cache, int chunkX, int chunkZ) {
      for (Holder<Biome> biome : cache.columnBiomes(chunkX, chunkZ)) {
         if (placement.getValidBiomes().contains(biome)) {
            return true;
         }
      }

      return false;
   }

   static int evaluateCandidate(HolderSet<Biome> structureBiomes, StructureSpawnPlanner.SampleCache cache, int chunkX, int chunkZ, int minHeight) {
      StructureSpawnPlanner.ChunkTerrain terrain = cache.terrain(chunkX, chunkZ);
      if (terrain == null) {
         return 0;
      } else if (!structureBiomes.contains(terrain.cornerBiome())) {
         return -1;
      } else {
         if (minHeight > Integer.MIN_VALUE) {
            if (terrain.maxSurface() < minHeight) {
               return -1;
            }

            if (cache.interiorMaxSurface(chunkX, chunkZ) < minHeight) {
               return -1;
            }
         }

         return !terrain.cornerBiome().is(BiomeTags.IS_OCEAN) && terrain.cornerSurface() > terrain.floor() ? -1 : terrain.maxSurface() - terrain.minSurface();
      }
   }

   private static Map<Integer, HolderSet<Biome>> buildStructureBiomes(ChunkGeneratorStructureState state) {
      Map<Integer, HolderSet<Biome>> result = new HashMap<>();
      if (state == null) {
         return result;
      } else {
         for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            StructureSet set = (StructureSet)holder.value();
            StructurePlacement structure = set.placement();
            if (structure instanceof BiomeAwareUniquePlacement) {
               BiomeAwareUniquePlacement placement = (BiomeAwareUniquePlacement)structure;
               if (!set.structures().isEmpty()) {
                  Structure structurex = (Structure)((StructureSelectionEntry)set.structures().get(0)).structure().value();
                  result.put(placement.placementSalt(), structurex.biomes());
               }
            }
         }

         return result;
      }
   }

   private static Map<Integer, Integer> buildStructureMinHeights(ChunkGeneratorStructureState state) {
      Map<Integer, Integer> result = new HashMap<>();
      if (state == null) {
         return result;
      } else {
         for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            StructureSet set = (StructureSet)holder.value();
            StructurePlacement structure = set.placement();
            if (structure instanceof BiomeAwareUniquePlacement) {
               BiomeAwareUniquePlacement placement = (BiomeAwareUniquePlacement)structure;
               if (!set.structures().isEmpty()) {
                  Structure structurex = (Structure)((StructureSelectionEntry)set.structures().get(0)).structure().value();
                  if (structurex instanceof TallJigsawStructure tall) {
                     result.put(placement.placementSalt(), tall.getMinStartY());
                  }
               }
            }
         }

         return result;
      }
   }

   private static List<Holder<StructureSet>> collectAvoidableSets(ChunkGeneratorStructureState state) {
      if (state == null) {
         return Collections.emptyList();
      } else {
         List<Holder<StructureSet>> result = new ArrayList<>();

         for (Holder<StructureSet> holder : state.possibleStructureSets()) {
            StructurePlacement placement = ((StructureSet)holder.value()).placement();
            if (!(placement instanceof BiomeAwareUniquePlacement)
               && !(placement instanceof UniqueNearSpawnPlacement)
               && !(placement instanceof FixedStructurePlacement)
               && !(placement instanceof ConcentricRingsStructurePlacement)) {
               result.add(holder);
            }
         }

         return result;
      }
   }

   static boolean overlapsOtherStructures(ChunkGeneratorStructureState state, List<Holder<StructureSet>> avoid, int chunkX, int chunkZ) {
      if (state != null && !avoid.isEmpty()) {
         for (Holder<StructureSet> holder : avoid) {
            if (state.hasStructureChunkInRange(holder, chunkX, chunkZ, 3)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static boolean tooClose(List<ChunkPos> accepted, int chunkX, int chunkZ, double spacingSqr) {
      if (spacingSqr <= 0.0) {
         return false;
      } else {
         for (ChunkPos other : accepted) {
            double dx = (double)(other.x - chunkX);
            double dz = (double)(other.z - chunkZ);
            if (dx * dx + dz * dz < spacingSqr) {
               return true;
            }
         }

         return false;
      }
   }

   private static ChunkGenerator resolveGeneratorFromServer(ChunkGeneratorStructureState state) {
      if (state == null) {
         return null;
      } else {
         MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
         if (server == null) {
            return null;
         } else {
            for (ServerLevel level : server.getAllLevels()) {
               ServerChunkCache chunkSource = level.getChunkSource();
               if (chunkSource.getGeneratorState() == state) {
                  return chunkSource.getGenerator();
               }
            }

            return null;
         }
      }
   }

   static BiomeSource getBiomeSourceReflection(ChunkGeneratorStructureState state) {
      if (state == null) {
         return null;
      } else {
         try {
            if (biomeSourceField == null) {
               for (Field f : ChunkGeneratorStructureState.class.getDeclaredFields()) {
                  if (BiomeSource.class.isAssignableFrom(f.getType())) {
                     f.setAccessible(true);
                     biomeSourceField = f;
                     break;
                  }
               }
            }

            if (biomeSourceField != null) {
               return (BiomeSource)biomeSourceField.get(state);
            }
         } catch (Exception var5) {
            System.err.println("[DMZ] StructureSpawnPlanner could not reflect BiomeSource: " + var5.getMessage());
         }

         return null;
      }
   }

   private static record ChunkTerrain(Holder<Biome> cornerBiome, int minSurface, int maxSurface, int cornerSurface, int floor) {
   }

   static final class PlanHolder {
      final long seed;
      final BiomeSource biomeSource;
      final RandomState randomState;
      final ChunkGeneratorStructureState state;
      final AtomicBoolean started = new AtomicBoolean(false);
      final CountDownLatch ready = new CountDownLatch(1);
      final Object writeLock = new Object();
      final List<ChunkPos> excluded = Collections.synchronizedList(new ArrayList<>());
      volatile Map<Integer, ChunkPos> positions = null;

      PlanHolder(long seed, BiomeSource biomeSource, RandomState randomState, ChunkGeneratorStructureState state) {
         this.seed = seed;
         this.biomeSource = biomeSource;
         this.randomState = randomState;
         this.state = state;
      }

      void publish(Map<Integer, ChunkPos> plan) {
         synchronized (this.writeLock) {
            if (this.positions == null) {
               this.positions = Collections.unmodifiableMap(new HashMap<>(plan));
            } else {
               Map<Integer, ChunkPos> merged = new HashMap<>(plan);
               merged.putAll(this.positions);
               this.positions = Collections.unmodifiableMap(merged);
            }
         }

         this.ready.countDown();
      }

      boolean awaitReady(long seconds) {
         try {
            return this.ready.await(seconds, TimeUnit.SECONDS);
         } catch (InterruptedException var4) {
            Thread.currentThread().interrupt();
            return false;
         }
      }
   }

   private static record PlanKey(long seed, BiomeSource src) {
      @Override
      public boolean equals(Object o) {
         if (this == o) {
            return true;
         } else {
            return !(o instanceof StructureSpawnPlanner.PlanKey other) ? false : this.seed == other.seed && this.src == other.src;
         }
      }

      @Override
      public int hashCode() {
         return Long.hashCode(this.seed) * 31 + System.identityHashCode(this.src);
      }
   }

   private static final class SampleCache {
      final BiomeSource biomeSource;
      final RandomState randomState;
      final ChunkGenerator generator;
      final LevelHeightAccessor heightAccessor;
      private final ConcurrentHashMap<Long, List<Holder<Biome>>> columnBiomeCache = new ConcurrentHashMap<>();
      private final ConcurrentHashMap<Long, StructureSpawnPlanner.ChunkTerrain> terrainCache = new ConcurrentHashMap<>();
      private final ConcurrentHashMap<Long, Integer> interiorMaxCache = new ConcurrentHashMap<>();

      SampleCache(BiomeSource biomeSource, RandomState randomState, ChunkGenerator generator, LevelHeightAccessor heightAccessor) {
         this.biomeSource = biomeSource;
         this.randomState = randomState;
         this.generator = generator;
         this.heightAccessor = heightAccessor;
      }

      List<Holder<Biome>> columnBiomes(int chunkX, int chunkZ) {
         return this.columnBiomeCache.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), key -> {
            int quartX = QuartPos.fromBlock((chunkX << 4) + 8);
            int quartZ = QuartPos.fromBlock((chunkZ << 4) + 8);
            List<Holder<Biome>> column = new ArrayList<>(3);

            for (int y = 160; y >= 64; y -= 48) {
               column.add(this.biomeSource.getNoiseBiome(quartX, QuartPos.fromBlock(y), quartZ, this.randomState.sampler()));
            }

            return column;
         });
      }

      int interiorMaxSurface(int chunkX, int chunkZ) {
         return this.interiorMaxCache.computeIfAbsent(ChunkPos.asLong(chunkX, chunkZ), key -> {
            int startX = chunkX << 4;
            int startZ = chunkZ << 4;
            int best = Integer.MIN_VALUE;

            for (int dx = 0; dx < 16; dx += 4) {
               for (int dz = 0; dz < 16; dz += 4) {
                  int h = this.generator.getFirstFreeHeight(startX + dx, startZ + dz, Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
                  if (h > best) {
                     best = h;
                  }
               }
            }

            return best;
         });
      }

      StructureSpawnPlanner.ChunkTerrain terrain(int chunkX, int chunkZ) {
         return this.generator != null && this.heightAccessor != null
            ? this.terrainCache
               .computeIfAbsent(
                  ChunkPos.asLong(chunkX, chunkZ),
                  key -> {
                     int startX = chunkX << 4;
                     int startZ = chunkZ << 4;
                     int minSurface = Integer.MAX_VALUE;
                     int maxSurface = Integer.MIN_VALUE;

                     for (int dx = 0; dx <= 16; dx += 16) {
                        for (int dz = 0; dz <= 16; dz += 16) {
                           int h = this.generator.getFirstFreeHeight(startX + dx, startZ + dz, Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
                           if (h < minSurface) {
                              minSurface = h;
                           }

                           if (h > maxSurface) {
                              maxSurface = h;
                           }
                        }
                     }

                     int midX = startX + 8;
                     int midZ = startZ + 8;
                     int midSurface = this.generator.getFirstFreeHeight(midX, midZ, Types.WORLD_SURFACE_WG, this.heightAccessor, this.randomState);
                     Holder<Biome> biome = this.biomeSource
                        .getNoiseBiome(QuartPos.fromBlock(midX), QuartPos.fromBlock(midSurface), QuartPos.fromBlock(midZ), this.randomState.sampler());
                     int floor = this.generator.getFirstFreeHeight(midX, midZ, Types.OCEAN_FLOOR_WG, this.heightAccessor, this.randomState);
                     return new StructureSpawnPlanner.ChunkTerrain(biome, minSurface, maxSurface, midSurface, floor);
                  }
               )
            : null;
      }
   }

   private static final class WorldGenSettings {
      final int minDistanceFromSpawn;
      final int maxDistanceFromSpawn;
      final int minDistanceBetween;

      WorldGenSettings() {
         GeneralServerConfig.WorldGenConfig worldGen = ConfigManager.getServerConfig().getWorldGen();
         this.minDistanceFromSpawn = worldGen.getStructureMinDistanceFromSpawn();
         this.maxDistanceFromSpawn = worldGen.getStructureMaxDistanceFromSpawn();
         this.minDistanceBetween = worldGen.getStructureMinDistanceBetween();
      }
   }
}
