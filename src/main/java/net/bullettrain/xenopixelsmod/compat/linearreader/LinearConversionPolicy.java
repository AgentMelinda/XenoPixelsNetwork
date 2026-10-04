package net.bullettrain.xenopixelsmod.compat.linearreader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/** Persistent policy for new conversions; existing linear data is never exported or removed. */
public final class LinearConversionPolicy {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Settings current = new Settings();
    private static volatile Path worldRoot;
    private static Path configFile;
    // Once opened as Anvil, do not convert a region underneath its live file handle.
    private static final java.util.Set<Path> ANVIL_REGIONS = ConcurrentHashMap.newKeySet();
    private static final Map<Path, java.util.concurrent.locks.ReentrantLock> REGION_LOCKS = new ConcurrentHashMap<>();

    private LinearConversionPolicy() {}

    public static final class Settings {
        public boolean enabled = true;
        public boolean defaultConversionAllowed = true;
        public Map<String, Boolean> dimensions = new TreeMap<>();
        // Safe default, including older configs without this field: unclaimed files stay MCA.
        public boolean allowOutsideYawpClaims = false;
        public Map<String, Boolean> outsideYawpClaimsDimensions = new TreeMap<>();

        public Settings copy() {
            Settings copy = new Settings();
            copy.enabled = enabled;
            copy.defaultConversionAllowed = defaultConversionAllowed;
            copy.dimensions.putAll(dimensions);
            copy.allowOutsideYawpClaims = allowOutsideYawpClaims;
            copy.outsideYawpClaimsDimensions.putAll(outsideYawpClaimsDimensions);
            return copy;
        }

        public boolean allows(String dimension) {
            return !enabled || dimensions.getOrDefault(dimension, defaultConversionAllowed);
        }

        public boolean allowsOutsideClaims(String dimension) {
            return !enabled || (dimension == null ? allowOutsideYawpClaims
                    : outsideYawpClaimsDimensions.getOrDefault(dimension, allowOutsideYawpClaims));
        }
    }

    public static synchronized void start(Path config, Path root) throws IOException {
        configFile = config;
        worldRoot = root.toAbsolutePath().normalize();
        ANVIL_REGIONS.clear();
        REGION_LOCKS.clear();
        LinearClaimCoverage.invalidate();
        reload();
    }

    public static synchronized void stop() {
        worldRoot = null;
        ANVIL_REGIONS.clear();
        REGION_LOCKS.clear();
        LinearClaimCoverage.invalidate();
    }

    public static Settings settings() { return current.copy(); }

    public static synchronized void reload() throws IOException {
        reload(settings -> {});
    }

    public static synchronized void reload(java.util.function.Consumer<Settings> validate) throws IOException {
        if (!Files.exists(configFile)) {
            Settings defaults = new Settings();
            validate.accept(defaults);
            save(defaults);
            return;
        }
        Settings loaded = read(configFile);
        validate.accept(loaded);
        current = loaded; // Parse and validate fully before replacing the live policy.
    }

    static Settings read(Path file) throws IOException {
        try {
            Settings settings = GSON.fromJson(Files.readString(file), Settings.class);
            if (settings == null || settings.dimensions == null || settings.outsideYawpClaimsDimensions == null) {
                throw new IllegalArgumentException("Expected a settings object and dimensions map");
            }
            validateOverrides(settings);
            return settings;
        } catch (RuntimeException exception) {
            throw new IOException("Invalid LinearReader policy in " + file, exception);
        }
    }

    public static void validateDimension(String dimension) {
        if (dimension == null || !dimension.contains(":")) {
            throw new IllegalArgumentException("Use a dimension ID such as dragonminez:otherworld");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(dimension);
        if (parsed == null || !parsed.toString().equals(dimension)) {
            throw new IllegalArgumentException("Invalid dimension ID: " + dimension);
        }
    }

    public static synchronized void save(Settings settings) throws IOException {
        validateOverrides(settings);
        Files.createDirectories(configFile.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(configFile.toAbsolutePath().getParent(), "xenolinear-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(settings) + "\n");
            try {
                Files.move(temporary, configFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            current = settings.copy();
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void validateOverrides(Settings settings) {
        // Validate separately: an override in one map must not hide an invalid value in the other.
        for (var overrides : java.util.List.of(settings.dimensions, settings.outsideYawpClaimsDimensions)) {
            for (var entry : overrides.entrySet()) {
                validateDimension(entry.getKey());
                if (entry.getValue() == null) throw new IllegalArgumentException("Null dimension toggle");
            }
        }
    }

    public static Path regionPath(Path folder, int regionX, int regionZ) {
        return folder.resolve("r." + regionX + "." + regionZ + ".mca").toAbsolutePath().normalize();
    }

    public static boolean retainAnvil(Path region) {
        return ANVIL_REGIONS.contains(region.toAbsolutePath().normalize());
    }

    public static void openedAnvil(Path region) {
        var lock = regionLock(region);
        lock.lock();
        try { ANVIL_REGIONS.add(region.toAbsolutePath().normalize()); }
        finally { lock.unlock(); }
    }

    public static java.util.concurrent.locks.ReentrantLock regionLock(Path region) {
        return REGION_LOCKS.computeIfAbsent(region.toAbsolutePath().normalize(), ignored ->
                new java.util.concurrent.locks.ReentrantLock());
    }

    public static boolean allowsConversion(Path path) {
        if (retainAnvil(path)) return false;
        String dimension = dimensionAt(worldRoot, path);
        Settings policy = current;
        if (!policy.enabled) return true;
        if (dimension != null && !policy.allows(dimension)) return false;
        return policy.allowsOutsideClaims(dimension) || LinearClaimCoverage.covers(dimension, path);
    }

    /** Handles vanilla storage layout, including dimension IDs with a slash in their path. */
    static String dimensionAt(Path root, Path storage) {
        if (root == null || storage == null) return null;
        Path normalized = storage.toAbsolutePath().normalize();
        if (!normalized.startsWith(root.toAbsolutePath().normalize())) return null;
        Path relative = root.toAbsolutePath().normalize().relativize(normalized);
        int count = relative.getNameCount();
        if (count > 0 && relative.getName(count - 1).toString().endsWith(".mca")) count--;
        if (count == 0) return null;
        String kind = relative.getName(count - 1).toString();
        if (!java.util.Set.of("region", "entities", "poi").contains(kind)) return null;
        if (count == 1) return "minecraft:overworld";
        if (count == 2 && relative.getName(0).toString().equals("DIM-1")) return "minecraft:the_nether";
        if (count == 2 && relative.getName(0).toString().equals("DIM1")) return "minecraft:the_end";
        if (count >= 4 && relative.getName(0).toString().equals("dimensions")) {
            String dimension = relative.getName(1) + ":" + relative.subpath(2, count - 1).toString().replace('\\', '/');
            try { validateDimension(dimension); return dimension; }
            catch (IllegalArgumentException ignored) { return null; }
        }
        return null;
    }
}
