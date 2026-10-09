package net.bullettrain.xenopixelsmod.combat.v3.ki;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.v3.technique.V3TechniqueDefinition;
import net.neoforged.fml.loading.FMLPaths;

/**
 * Loads / saves Xeno Ki presentation profiles from
 * {@code config/xenopixelsmod/ki_profiles.json}.
 *
 * <p>Resolve order: archetype default ({@code type:nativeId}) → exact technique id.
 * Maker / {@code /xenokiprofile} writes the config file; datapack seeds are optional later.
 */
public final class XenoKiProfileCatalog {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<String, XenoKiProfile> PROFILES = new ConcurrentHashMap<>();
    private static final Map<String, XenoKiProfile> ARCHETYPES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private XenoKiProfileCatalog() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod").resolve("ki_profiles.json");
    }

    public static synchronized void load() {
        try {
            load(path());
        } catch (IOException failure) {
            XenoPixelsMod.LOGGER.warn("Ki profiles load failed: {}", failure.toString());
            PROFILES.clear();
            ARCHETYPES.clear();
            loaded = true;
        }
    }

    static synchronized void load(Path file) throws IOException {
        PROFILES.clear();
        ARCHETYPES.clear();
        if (!Files.isRegularFile(file)) {
            seedDefaults();
            save(file);
            loaded = true;
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            int schema = root.has("schema") ? root.get("schema").getAsInt() : 0;
            if (schema != XenoKiProfile.SCHEMA) {
                XenoPixelsMod.LOGGER.warn("Ki profiles schema {} unsupported; expected {}", schema, XenoKiProfile.SCHEMA);
            }
            JsonObject profiles = root.has("profiles") && root.get("profiles").isJsonObject()
                    ? root.getAsJsonObject("profiles") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : profiles.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                PROFILES.put(entry.getKey(), XenoKiProfile.fromJson(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
            JsonObject arch = root.has("archetypeDefaults") && root.get("archetypeDefaults").isJsonObject()
                    ? root.getAsJsonObject("archetypeDefaults") : new JsonObject();
            for (Map.Entry<String, JsonElement> entry : arch.entrySet()) {
                if (!entry.getValue().isJsonObject()) continue;
                ARCHETYPES.put(entry.getKey(), XenoKiProfile.fromJson("", entry.getValue().getAsJsonObject()));
            }
        }
        if (PROFILES.isEmpty() && ARCHETYPES.isEmpty()) seedDefaults();
        loaded = true;
        XenoPixelsMod.LOGGER.info("Ki profiles loaded: {} techniques, {} archetypes from {}",
                PROFILES.size(), ARCHETYPES.size(), file.toAbsolutePath());
    }

    public static synchronized void save() {
        try {
            save(path());
        } catch (IOException failure) {
            XenoPixelsMod.LOGGER.warn("Ki profiles save failed: {}", failure.toString());
        }
    }

    static synchronized void save(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        JsonObject root = new JsonObject();
        root.addProperty("schema", XenoKiProfile.SCHEMA);
        JsonObject profiles = new JsonObject();
        for (Map.Entry<String, XenoKiProfile> entry : new LinkedHashMap<>(PROFILES).entrySet()) {
            profiles.add(entry.getKey(), entry.getValue().toJson());
        }
        root.add("profiles", profiles);
        JsonObject arch = new JsonObject();
        for (Map.Entry<String, XenoKiProfile> entry : new LinkedHashMap<>(ARCHETYPES).entrySet()) {
            arch.add(entry.getKey(), entry.getValue().toJson());
        }
        root.add("archetypeDefaults", arch);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, GSON.toJson(root) + "\n", StandardCharsets.UTF_8);
        Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
    }

    public static void ensureLoaded() {
        if (!loaded) load();
    }

    public static XenoKiProfile resolve(V3TechniqueDefinition technique) {
        ensureLoaded();
        if (technique == null) return XenoKiProfile.empty("", "");
        String type = technique.type() == null ? "" : technique.type();
        String nativeId = technique.kiTechnique() == null ? "" : technique.kiTechnique();
        XenoKiProfile profile = XenoKiProfile.empty(technique.id(), nativeId);
        String archKey = type + ":" + nativeId;
        if (ARCHETYPES.containsKey(archKey)) profile = profile.merge(ARCHETYPES.get(archKey));
        if (ARCHETYPES.containsKey(nativeId)) profile = profile.merge(ARCHETYPES.get(nativeId));
        if (PROFILES.containsKey(technique.id())) profile = profile.merge(PROFILES.get(technique.id()));
        return profile;
    }

    public static XenoKiProfile resolve(String techniqueId, String type, String nativeId) {
        ensureLoaded();
        XenoKiProfile profile = XenoKiProfile.empty(techniqueId == null ? "" : techniqueId, nativeId == null ? "" : nativeId);
        String archKey = (type == null ? "" : type) + ":" + (nativeId == null ? "" : nativeId);
        if (ARCHETYPES.containsKey(archKey)) profile = profile.merge(ARCHETYPES.get(archKey));
        if (nativeId != null && ARCHETYPES.containsKey(nativeId)) profile = profile.merge(ARCHETYPES.get(nativeId));
        if (techniqueId != null && PROFILES.containsKey(techniqueId)) profile = profile.merge(PROFILES.get(techniqueId));
        return profile;
    }

    public static synchronized void put(XenoKiProfile profile) {
        if (profile == null || profile.techniqueId().isBlank()) return;
        PROFILES.put(profile.techniqueId(), profile);
    }

    public static Map<String, XenoKiProfile> profilesView() {
        ensureLoaded();
        return Collections.unmodifiableMap(PROFILES);
    }

    public static Map<String, XenoKiProfile> archetypesView() {
        ensureLoaded();
        return Collections.unmodifiableMap(ARCHETYPES);
    }

    /** Built-in BT3-leaning archetype defaults (starting points, not video-measured). */
    private static void seedDefaults() {
        ARCHETYPES.put("beam:kamehameha", base("kamehameha", 5240831, 5240831, 1.15f, 1.2f, true));
        ARCHETYPES.put("beam:galick_gun", base("galick_gun", 13504739, 11407587, 1.15f, 1.15f, true));
        ARCHETYPES.put("beam:masenko", base("masenko", 16771584, 16771584, 1.1f, 1.15f, true));
        ARCHETYPES.put("beam:final_flash", base("final_flash", 16750848, 16750848, 1.35f, 1.1f, true));
        ARCHETYPES.put("beam:makkanko", base("makkanko", 16770363, 12860415, 1.05f, 1.4f, true));
        ARCHETYPES.put("beam:death_beam", base("death_beam", 13504739, 13504739, 0.85f, 1.8f, true));
        ARCHETYPES.put("beam:soul_punisher", base("soul_punisher", 10027263, 6684774, 1.2f, 1.1f, true));
        ARCHETYPES.put("volley:ki_barrage", base("ki_barrage", 16776960, 16776960, 0.6f, 1.3f, false));
        ARCHETYPES.put("giant_ball:spiritbomb", base("spiritbomb", 3211249, 63743, 2.6f, 0.85f, true));
        ARCHETYPES.put("giant_ball:supernova", base("supernova", 16750848, 16729088, 2.4f, 0.9f, true));
        ARCHETYPES.put("ball:big_bang", base("big_bang", 5240831, 5240831, 1.7f, 1.2f, true));
        ARCHETYPES.put("ball:sokidan", base("sokidan", 5220863, 5220863, 1.2f, 1.4f, false));
        ARCHETYPES.put("disc:kienzan", base("kienzan", 16771584, 16771584, 0.9f, 1.5f, true));
        ARCHETYPES.put("laser:death_beam", base("death_beam", 13504739, 13504739, 0.8f, 2.0f, true));
    }

    private static XenoKiProfile base(String nativeId, int interior, int exterior, float size, float speed,
                                      boolean center) {
        return new XenoKiProfile("", nativeId, interior, exterior, 16777215, size, speed, 1.0f, 0,
                center ? 0f : null, 0.2f, 0.5f, null, center, null, null, null);
    }
}
