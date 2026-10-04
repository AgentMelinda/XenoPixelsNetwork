package net.bullettrain.xenopixelsmod.aero;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persists {@code config/xenopixelsmod-guidance.json}. Default remains v1. */
public final class GuidanceConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Path path;
    private static GuidanceVersion version = GuidanceVersion.V1;

    private GuidanceConfig() {}

    static Path path() {
        if (path == null) {
            path = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-guidance.json");
        }
        return path;
    }

    /** Test hook so persist checks do not touch the live config directory. */
    public static void usePathForTest(Path next) {
        path = next;
    }

    /** Restore in-memory default after a persist test. Does not touch FML paths. */
    public static void resetForTest() {
        version = GuidanceVersion.V1;
        path = null;
    }

    public static GuidanceVersion version() {
        return version;
    }

    public static GuidanceVersion sanitize(GuidanceVersion next) {
        return next == null ? GuidanceVersion.V1 : next;
    }

    public static GuidanceVersion set(GuidanceVersion next) {
        version = sanitize(next);
        save();
        return version;
    }

    public static void load() {
        if (!Files.exists(path())) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(path())) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data != null) {
                version = sanitize(GuidanceVersion.byName(data.version));
            }
        } catch (Exception e) {
            XenoPixelsMod.LOGGER.warn("Failed to load guidance config; using v1", e);
            version = GuidanceVersion.V1;
        }
    }

    public static void save() {
        try {
            Files.createDirectories(path().getParent());
            try (Writer writer = Files.newBufferedWriter(path())) {
                Data data = new Data();
                data.version = version.name().toLowerCase();
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save guidance config", e);
        }
    }

    public static final class Data {
        public String version = "v1";
    }
}
