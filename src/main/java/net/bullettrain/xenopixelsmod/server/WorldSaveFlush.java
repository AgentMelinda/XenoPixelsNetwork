package net.bullettrain.xenopixelsmod.server;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.server.MinecraftServer;
import net.bullettrain.xenopixelsmod.config.XenoServerConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Blocking world flush that finishes LinearReader dirty regions instead of only
 * queuing them for the async per-tick drain.
 *
 * <p>LinearReader 1.3.0 ({@code linearreader}) verified with {@code javap}:
 * {@code LinearRuntime.flushRegionsBlocking(List)}, {@code LinearRegionFile.ALL_OPEN},
 * {@code isDirty()}. Vanilla {@code MinecraftServer.saveEverything(boolean, boolean, boolean)}
 * and {@code ChunkStorage.flushWorker()} verified on NeoForge 21.1.248.
 */
public final class WorldSaveFlush {
    public static final String LINEAR_MOD_ID = "linearreader";

    /**
     * @param regionFiles  region files found on disk after the flush
     * @param newestMillis newest region-file modification time, or 0 when nothing was found
     * @param synced       region directories fsynced, so the bytes are past the OS page cache
     */
    public record Result(boolean vanillaSaved, int linearRegions, String error,
                         int regionFiles, long newestMillis, int synced) {

        public Result(boolean vanillaSaved, int linearRegions, String error) {
            this(vanillaSaved, linearRegions, error, 0, 0L, 0);
        }

        public boolean ok() {
            return error == null || error.isBlank();
        }

        /**
         * What actually reached the disk, for the command to print.
         *
         * <p>The point of reporting this at all: a flush that does nothing and a flush that writes
         * the whole world both used to print "World saved". After a rollback, the newest timestamp
         * here is what separates "the bytes never landed" from "something replaced them afterwards",
         * and only the second of those is outside this mod.
         */
        public String diskSummary() {
            if (regionFiles <= 0) {
                return "no region files found on disk";
            }
            String newest = newestMillis <= 0L ? "unknown"
                    : java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.ofEpochMilli(newestMillis));
            return regionFiles + " region file(s), newest written " + newest
                    + (synced > 0 ? ", " + synced + " dir(s) fsynced" : "");
        }
    }

    private WorldSaveFlush() {}

    public static Result flush(MinecraftServer server) {
        if (server == null) {
            return new Result(false, 0, "no server");
        }
        boolean vanilla;
        try {
            vanilla = server.saveEverything(false, true, true);
            for (ServerLevel level : server.getAllLevels()) {
                level.getChunkSource().save(true);
                level.getChunkSource().chunkMap.flushWorker();
                flushRegionStorages(level);
            }
        } catch (RuntimeException e) {
            XenoPixelsMod.LOGGER.error("Vanilla world flush failed: {}", e.toString());
            return new Result(false, 0, e.getMessage());
        }
        LinearFlush linear = flushLinearReader();
        Disk disk = surveyAndSync(server);
        return new Result(vanilla, linear.count, linear.error,
                disk.files, disk.newestMillis, disk.synced);
    }

    /** What the region directories look like once the flush has returned. */
    private record Disk(int files, long newestMillis, int synced) {}

    /**
     * Stat the region directories, and push them past the OS page cache.
     *
     * <p>Two jobs, one directory walk. The survey is evidence: a save that reports a newest-write
     * time can be checked against the files after a rollback, which is the only way to tell a flush
     * that failed from a world that was replaced underneath the server.
     *
     * <p>The fsync is the one candidate cause that is both real and ours. A write that has returned
     * still lives in the page cache until the OS decides otherwise, so a hard kill loses it —
     * and this server is killed as often as it is stopped. Vanilla does not fsync either, so this is
     * an addition rather than a repair.
     */
    private static Disk surveyAndSync(MinecraftServer server) {
        int files = 0;
        long newest = 0L;
        int synced = 0;
        boolean fsync = XenoServerConfig.saveFsync;
        for (ServerLevel level : server.getAllLevels()) {
            java.nio.file.Path region;
            try {
                region = net.minecraft.world.level.dimension.DimensionType
                        .getStorageFolder(level.dimension(), server.getWorldPath(LevelResource.ROOT))
                        .resolve("region");
            } catch (RuntimeException e) {
                XenoPixelsMod.LOGGER.warn("Could not resolve region folder for {}: {}",
                        level.dimension().location(), e.toString());
                continue;
            }
            if (!java.nio.file.Files.isDirectory(region)) {
                continue;
            }
            try (var stream = java.nio.file.Files.list(region)) {
                for (java.nio.file.Path file : stream.toList()) {
                    if (!java.nio.file.Files.isRegularFile(file)) {
                        continue;
                    }
                    files++;
                    long modified = java.nio.file.Files.getLastModifiedTime(file).toMillis();
                    if (modified > newest) {
                        newest = modified;
                    }
                }
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not survey {}: {}", region, e.toString());
                continue;
            }
            if (fsync && fsyncDirectory(region)) {
                synced++;
            }
        }
        return new Disk(files, newest, synced);
    }

    /**
     * Force a directory's entries to stable storage.
     *
     * <p>Opening a directory channel for read and calling {@code force} is the portable way to ask
     * for this; it is not supported everywhere, and a failure here is worth a line in the log but
     * must never turn a good save into a reported failure.
     */
    private static boolean fsyncDirectory(java.nio.file.Path dir) {
        try (java.nio.channels.FileChannel channel =
                     java.nio.channels.FileChannel.open(dir, java.nio.file.StandardOpenOption.READ)) {
            channel.force(true);
            return true;
        } catch (IOException | RuntimeException e) {
            XenoPixelsMod.LOGGER.warn("fsync of {} not supported here: {}", dir, e.toString());
            return false;
        }
    }

    private record LinearFlush(int count, String error) {}

    /**
     * Optional. No compile dependency on LinearReader. Missing classes are logged,
     * not treated as a vanilla-save failure.
     */
    private static LinearFlush flushLinearReader() {
        if (!ModList.get().isLoaded(LINEAR_MOD_ID)) {
            return new LinearFlush(0, null);
        }
        try {
            Class<?> runtime = Class.forName("com.bugfunbug.linearreader.LinearRuntime");
            Class<?> region = Class.forName("com.bugfunbug.linearreader.linear.LinearRegionFile");
            @SuppressWarnings("unchecked")
            Set<Object> open = (Set<Object>) region.getField("ALL_OPEN").get(null);
            if (open == null || open.isEmpty()) {
                return new LinearFlush(0, null);
            }
            Method dirty = region.getMethod("isDirty");
            List<Object> toFlush = new ArrayList<>();
            for (Object file : Set.copyOf(open)) {
                if (Boolean.TRUE.equals(dirty.invoke(file))) {
                    toFlush.add(file);
                }
            }
            if (toFlush.isEmpty()) {
                return new LinearFlush(0, null);
            }
            Method flush = runtime.getMethod("flushRegionsBlocking", List.class);
            reportResolvedOnce(runtime, flush);
            flush.invoke(null, toFlush);
            return new LinearFlush(toFlush.size(), null);
        } catch (ClassNotFoundException | NoSuchFieldException | NoSuchMethodException e) {
            // An error, not a warning. This used to return a null error, so Result.ok() stayed true
            // and the command printed "World saved" having flushed nothing -- a total no-op and a
            // full success were indistinguishable to whoever typed the command.
            XenoPixelsMod.LOGGER.error(
                    "LinearReader {} is loaded but its flush symbols did not match: {}",
                    linearVersion(), e.toString());
            return new LinearFlush(0,
                    "LinearReader is installed but its flush API did not match; regions were NOT flushed");
        } catch (ReflectiveOperationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            XenoPixelsMod.LOGGER.error("LinearReader blocking flush failed: {}", cause.toString());
            return new LinearFlush(0, cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }
    }

    private static volatile boolean reportedResolved;

    /**
     * Log which LinearReader actually answered, once per run.
     *
     * <p>This is reflected against a jar that is not part of this project, so "did the symbols
     * match" was only answerable by reading a warning that appears when they do not. Naming the
     * version and the resolved method on the success path makes it answerable from the log either
     * way, without asking anyone to reproduce anything.
     */
    private static void reportResolvedOnce(Class<?> runtime, Method flush) {
        if (reportedResolved) {
            return;
        }
        reportedResolved = true;
        XenoPixelsMod.LOGGER.info("LinearReader {} flush resolved: {}#{}",
                linearVersion(), runtime.getName(), flush.getName());
    }

    /** The installed LinearReader version, or {@code unknown} when it cannot be read. */
    private static String linearVersion() {
        try {
            return ModList.get().getModContainerById(LINEAR_MOD_ID)
                    .map(c -> c.getModInfo().getVersion().toString())
                    .orElse("unknown");
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    /**
     * LinearReader overwrites {@link RegionFileStorage#flush()} to block on its dirty cache.
     * Field names ({@code worker}, {@code storage}, {@code simpleRegionStorage},
     * {@code entityManager}, {@code permanentStorage}) verified with {@code javap} on
     * NeoForge 21.1.248. Missing names are logged; vanilla save still stands.
     */
    private static void flushRegionStorages(ServerLevel level) {
        flushIoWorkerStorage(readField(level.getChunkSource().chunkMap, "worker"));
        Object poiSimple = readField(level.getPoiManager(), "simpleRegionStorage");
        joinSynchronize(poiSimple);
        flushIoWorkerStorage(readField(poiSimple, "worker"));
        Object entityManager = readField(level, "entityManager");
        Object entityStorage = readField(entityManager, "permanentStorage");
        if (entityStorage != null) {
            try {
                entityStorage.getClass().getMethod("flush", boolean.class).invoke(entityStorage, true);
            } catch (ReflectiveOperationException e) {
                XenoPixelsMod.LOGGER.warn("Entity region flush(boolean) missing: {}", e.toString());
                Object simple = readField(entityStorage, "simpleRegionStorage");
                joinSynchronize(simple);
                flushIoWorkerStorage(readField(simple, "worker"));
            }
        }
    }

    private static void joinSynchronize(Object simpleRegionStorage) {
        if (simpleRegionStorage == null) {
            return;
        }
        try {
            Object future = simpleRegionStorage.getClass()
                    .getMethod("synchronize", boolean.class)
                    .invoke(simpleRegionStorage, true);
            if (future instanceof CompletableFuture<?> completable) {
                completable.join();
            }
        } catch (ReflectiveOperationException e) {
            XenoPixelsMod.LOGGER.warn("Region synchronize(true) missing: {}", e.toString());
        }
    }

    private static void flushIoWorkerStorage(Object ioWorker) {
        Object storage = readField(ioWorker, "storage");
        if (storage instanceof RegionFileStorage region) {
            try {
                region.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static Object readField(Object owner, String name) {
        if (owner == null || name == null) {
            return null;
        }
        Class<?> type = owner.getClass();
        while (type != null && type != Object.class) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(owner);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (ReflectiveOperationException e) {
                XenoPixelsMod.LOGGER.warn("Could not read {}.{}: {}",
                        owner.getClass().getName(), name, e.toString());
                return null;
            }
        }
        XenoPixelsMod.LOGGER.warn("Field {} missing on {}", name, owner.getClass().getName());
        return null;
    }
}
