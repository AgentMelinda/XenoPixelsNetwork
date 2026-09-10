package com.dragonminez.server.events;

import com.dragonminez.Env;
import com.dragonminez.LogUtil;
import com.dragonminez.common.dragonball.DragonBallDefinitions;
import com.dragonminez.common.dragonball.DragonBallSetDefinition;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.network.S2C.RadarSyncS2C;
import com.dragonminez.server.world.data.DragonBallSavedData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerChangedDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.neoforged.neoforge.event.level.BlockEvent.BreakEvent;
import net.neoforged.neoforge.event.level.BlockEvent.EntityPlaceEvent;
import net.neoforged.neoforge.event.level.ChunkEvent.Load;
import net.neoforged.neoforge.event.tick.LevelTickEvent.Post;

@EventBusSubscriber(
   modid = "dragonminez"
)
public class DragonBallsHandler {
   private static final Queue<Runnable> generationQueue = new ConcurrentLinkedQueue<>();
   private static final int PENDING_RESCAN_INTERVAL = 100;
   private static int pendingRescanTimer = 0;
   private static final int RADAR_SYNC_INTERVAL = 100;
   private static int radarSyncTimer = 0;
   private static final double NEARBY_GEN_RANGE_SQR = 16384.0;

   public static void scatterDragonBalls(ServerLevel level, String setId) {
      DragonBallSetDefinition definition = DragonBallDefinitions.getBallSet(setId);
      if (definition != null && definition.supportsDimension(level.dimension())) {
         DragonBallSavedData data = DragonBallSavedData.get(level);
         Random random = new Random();
         int range = definition.getSpawnRange();
         BlockPos spawnPos = level.getSharedSpawnPos();
         Map<Integer, List<BlockPos>> active = data.getActiveBalls(setId);
         Map<Integer, List<BlockPos>> pending = data.getPendingBalls(setId);
         boolean isFirstSpawn = !data.isFirstSpawnComplete(setId);
         int maxSets = definition.getCopies();
         int setsToSpawn = isFirstSpawn ? maxSets : 1;

         for (int star : definition.getStars()) {
            int currentCount = active.get(star).size() + pending.get(star).size();
            int actualToSpawn = Math.min(setsToSpawn, maxSets - currentCount);

            for (int i = 0; i < actualToSpawn; i++) {
               int x = spawnPos.getX() + random.nextInt(range * 2) - range;
               int z = spawnPos.getZ() + random.nextInt(range * 2) - range;
               BlockPos targetPos = new BlockPos(x, 0, z);
               pending.get(star).add(targetPos);
               LogUtil.debug(Env.SERVER, "Dragon Ball (pending) [" + star + "] assigned to " + targetPos + " for set " + setId + " (Y is a dummy value)");
               if (level.isLoaded(targetPos)) {
                  generationQueue.add(() -> generateBallSafely(level, definition, star, targetPos));
               }
            }
         }

         if (isFirstSpawn) {
            data.setFirstSpawnComplete(setId, true);
         }

         data.setDirty();
         syncRadar(level);
      }
   }

   public static void unregisterConsumedDragonBalls(ServerLevel level, Collection<BlockPos> consumedPositions, String setId) {
      if (consumedPositions != null && !consumedPositions.isEmpty()) {
         DragonBallSavedData data = DragonBallSavedData.get(level);
         Map<Integer, List<BlockPos>> active = data.getActiveBalls(setId);
         Set<BlockPos> consumedSet = new HashSet<>(consumedPositions);
         boolean changed = false;

         for (int star : DragonBallDefinitions.getBallSet(setId).getStars()) {
            List<BlockPos> positions = active.get(star);
            if (positions != null && positions.removeIf(consumedSet::contains)) {
               changed = true;
            }
         }

         if (changed) {
            data.setDirty();
            syncRadar(level);
         }
      }
   }

   @SubscribeEvent
   public static void onChunkLoad(Load event) {
      if (event.getLevel() instanceof ServerLevel level) {
         DragonBallSavedData var7 = DragonBallSavedData.get(level);
         ChunkPos chunkPos = event.getChunk().getPos();

         for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSetsForDimension(level.dimension())) {
            Map<Integer, List<BlockPos>> pending = var7.getPendingBalls(definition.getId());
            pending.forEach((star, targets) -> {
               for (BlockPos target : new ArrayList<>(targets)) {
                  if (chunkPos.x == target.getX() >> 4 && chunkPos.z == target.getZ() >> 4) {
                     generationQueue.add(() -> generateBallSafely(level, definition, star, target));
                  }
               }
            });
         }
      }
   }

   @SubscribeEvent
   public static void onLevelTick(Post event) {
      if (!event.getLevel().isClientSide) {
         if (event.getLevel() instanceof ServerLevel level && ++pendingRescanTimer >= 100) {
            pendingRescanTimer = 0;
            rescanPendingBalls(level);
         }

         if (event.getLevel() instanceof ServerLevel level && level.dimension().equals(Level.OVERWORLD) && ++radarSyncTimer >= 100) {
            radarSyncTimer = 0;
            syncRadar(level);
         }

         if (event.getLevel() instanceof ServerLevel level) {
            generateNearbyPendingBalls(level);
         }

         while (!generationQueue.isEmpty()) {
            Runnable task = generationQueue.poll();
            if (task != null) {
               task.run();
            }
         }
      }
   }

   private static void generateNearbyPendingBalls(ServerLevel level) {
      List<ServerPlayer> players = level.players();
      if (!players.isEmpty()) {
         DragonBallSavedData data = DragonBallSavedData.get(level);

         for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSetsForDimension(level.dimension())) {
            Map<Integer, List<BlockPos>> pending = data.getPendingBalls(definition.getId());
            pending.forEach((star, targets) -> {
               for (BlockPos target : new ArrayList<>(targets)) {
                  if (level.isLoaded(target)) {
                     for (ServerPlayer player : players) {
                        double dx = player.getX() - ((double)target.getX() + 0.5);
                        double dz = player.getZ() - ((double)target.getZ() + 0.5);
                        if (dx * dx + dz * dz <= 16384.0) {
                           generationQueue.add(() -> generateBallSafely(level, definition, star, target));
                           break;
                        }
                     }
                  }
               }
            });
         }
      }
   }

   private static void rescanPendingBalls(ServerLevel level) {
      DragonBallSavedData data = DragonBallSavedData.get(level);

      for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSetsForDimension(level.dimension())) {
         Map<Integer, List<BlockPos>> pending = data.getPendingBalls(definition.getId());
         pending.forEach((star, targets) -> {
            for (BlockPos target : new ArrayList<>(targets)) {
               if (level.isLoaded(target)) {
                  generationQueue.add(() -> generateBallSafely(level, definition, star, target));
               }
            }
         });
      }
   }

   @SubscribeEvent
   public static void onPlayerLogin(PlayerLoggedInEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         syncRadarForPlayer(player);
         scheduleDelayedSync(player, 40);
      }
   }

   @SubscribeEvent
   public static void onPlayerRespawn(PlayerRespawnEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         syncRadarForPlayer(player);
      }
   }

   @SubscribeEvent
   public static void onPlayerChangedDimension(PlayerChangedDimensionEvent event) {
      if (event.getEntity() instanceof ServerPlayer player) {
         syncRadarForPlayer(player);
      }
   }

   private static void scheduleDelayedSync(ServerPlayer player, int delayTicks) {
      player.server.tell(new TickTask(player.server.getTickCount() + delayTicks, () -> {
         if (!player.hasDisconnected()) {
            syncRadarForPlayer(player);
         }
      }));
   }

   private static void generateBallSafely(ServerLevel level, DragonBallSetDefinition definition, int star, BlockPos targetXZ) {
      DragonBallSavedData data = DragonBallSavedData.get(level);
      List<BlockPos> pendingForStar = data.getPendingBalls(definition.getId()).get(star);
      if (pendingForStar != null && pendingForStar.contains(targetXZ)) {
         int x = targetXZ.getX();
         int z = targetXZ.getZ();
         int y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         BlockPos realPos = new BlockPos(x, y, z);
         if (level.isLoaded(realPos)) {
            MutableBlockPos mutable = realPos.mutable();

            while (mutable.getY() > level.getMinBuildHeight() && level.getBlockState(mutable.below()).canBeReplaced()) {
               mutable.move(0, -1, 0);
            }

            realPos = mutable.immutable();

            while (!level.getBlockState(realPos).canBeReplaced() && realPos.getY() < level.getMaxBuildHeight() - 1) {
               realPos = realPos.above();
            }

            BlockState below = level.getBlockState(realPos.below());
            if (below.isAir() || below.is(Blocks.WATER)) {
               level.setBlock(realPos.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
            }

            Block block = definition.getBlockForStar(star);
            if (block != null) {
               boolean success = level.setBlock(realPos, block.defaultBlockState(), 2);
               if (success && level.getBlockState(realPos).getBlock() == block) {
                  data.getPendingBalls(definition.getId()).get(star).remove(targetXZ);
                  if (!data.getActiveBalls(definition.getId()).get(star).contains(realPos)) {
                     data.getActiveBalls(definition.getId()).get(star).add(realPos);
                  }

                  data.setDirty();
                  LogUtil.info(Env.SERVER, "Dragon Ball [" + star + "] physically generated at " + realPos + " for set " + definition.getId());
                  syncRadar(level);
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onBlockPlace(EntityPlaceEvent event) {
      Block block = event.getPlacedBlock().getBlock();
      DragonBallSetDefinition definition = DragonBallDefinitions.getBallSetForBlock(block);
      if (definition != null && event.getLevel() instanceof ServerLevel level) {
         Integer star = definition.getStarForBlock(block);
         if (star != null) {
            DragonBallSavedData data = DragonBallSavedData.get(level);
            if (!data.getActiveBalls(definition.getId()).get(star).contains(event.getPos())) {
               data.getActiveBalls(definition.getId()).get(star).add(event.getPos());
            }

            data.setDirty();
            syncRadar(level);
         }
      }
   }

   @SubscribeEvent
   public static void onBlockBreak(BreakEvent event) {
      Block block = event.getState().getBlock();
      DragonBallSetDefinition definition = DragonBallDefinitions.getBallSetForBlock(block);
      if (definition != null && event.getLevel() instanceof ServerLevel level) {
         Integer star = definition.getStarForBlock(block);
         if (star != null) {
            DragonBallSavedData data = DragonBallSavedData.get(level);
            data.getActiveBalls(definition.getId()).get(star).remove(event.getPos());
            data.setDirty();
            syncRadar(level);
         }
      }
   }

   public static void syncRadar(ServerLevel level) {
      if (level != null) {
         RadarSyncS2C packet = buildRadarPacket(level.getServer());
         if (packet != null) {
            NetworkHandler.sendToAllPlayers(packet);
         }
      }
   }

   public static void syncRadarForPlayer(ServerPlayer player) {
      if (player != null) {
         RadarSyncS2C packet = buildRadarPacket(player.serverLevel().getServer());
         if (packet != null) {
            NetworkHandler.sendToPlayer(packet, player);
         }
      }
   }

   private static RadarSyncS2C buildRadarPacket(MinecraftServer server) {
      if (server == null) {
         return null;
      } else {
         Map<String, List<BlockPos>> positionsBySet = new HashMap<>();

         for (DragonBallSetDefinition definition : DragonBallDefinitions.getBallSets()) {
            List<BlockPos> positions = positionsBySet.computeIfAbsent(definition.getId(), ignored -> new ArrayList<>());

            for (ResourceLocation dimension : definition.getValidDimensions()) {
               ServerLevel setLevel = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
               if (setLevel != null) {
                  positions.addAll(DragonBallSavedData.get(setLevel).getAllKnownPositionsForRadar(definition.getId()));
               }
            }
         }

         List<BlockPos> earthPositions = new ArrayList<>(positionsBySet.getOrDefault("earth", List.of()));
         List<BlockPos> namekPositions = new ArrayList<>(positionsBySet.getOrDefault("namek", List.of()));
         return new RadarSyncS2C(earthPositions, namekPositions, positionsBySet);
      }
   }
}
