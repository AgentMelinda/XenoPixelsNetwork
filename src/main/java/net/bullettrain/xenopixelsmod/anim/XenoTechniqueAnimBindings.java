package net.bullettrain.xenopixelsmod.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.bullettrain.xenopixelsmod.combat.anim.TechniqueAnimSlot;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server map of live animation slot → studio clip name.
 *
 * <p>Slots are Hakai hold/fire plus every {@link Bt3AnimationIntent}. A published clip of the
 * same {@code combat.xeno_*} name also overrides the shipped file at lookup time; this map is
 * what lets a renamed clip take a slot. Joiners receive the map with the clip library.
 */
public final class XenoTechniqueAnimBindings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<String, String> BINDINGS = new LinkedHashMap<>();
    private static volatile boolean loaded;

    private XenoTechniqueAnimBindings() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-anim-technique-bindings.json");
    }

    public static synchronized void load() {
        loaded = true;
        BINDINGS.clear();
        Path path = file();
        if (!Files.isRegularFile(path)) return;
        try {
            BINDINGS.putAll(parse(Files.readString(path)));
        } catch (IOException | RuntimeException e) {
            XenoPixelsMod.LOGGER.warn("Could not read {}: {}", path.getFileName(), e.toString());
        }
    }

    public static Map<String, String> parse(String json) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException e) {
            return out;
        }
        if (root == null) return out;
        for (var entry : root.entrySet()) {
            String slot = normalizeSlot(entry.getKey());
            if (slot == null || !entry.getValue().isJsonPrimitive()) continue;
            String clip = sanitize(entry.getValue().getAsString());
            if (!clip.isBlank()) out.put(slot, clip);
        }
        return out;
    }

    public static String write(Map<String, String> bindings) {
        JsonObject root = new JsonObject();
        if (bindings != null) {
            bindings.forEach((slot, clip) -> {
                if (slot != null && clip != null && !clip.isBlank()) {
                    root.addProperty(slot, clip);
                }
            });
        }
        return GSON.toJson(root);
    }

    public static String writeCurrent() {
        ensureLoaded();
        return write(BINDINGS);
    }

    public static synchronized void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, write(BINDINGS));
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not write technique bindings: {}", e.toString());
        }
    }

    public static synchronized void bind(String slotName, String clipName) {
        String slot = normalizeSlot(slotName);
        if (slot == null) return;
        ensureLoaded();
        String clip = sanitize(clipName);
        if (clip.isBlank()) {
            BINDINGS.remove(slot);
        } else {
            BINDINGS.put(slot, clip);
        }
        save();
    }

    public static synchronized boolean unbind(String slotName) {
        String slot = normalizeSlot(slotName);
        if (slot == null) return false;
        ensureLoaded();
        boolean removed = BINDINGS.remove(slot) != null;
        if (removed) save();
        return removed;
    }

    public static String clipFor(String slotName) {
        String slot = normalizeSlot(slotName);
        if (slot == null) return null;
        ensureLoaded();
        return BINDINGS.get(slot);
    }

    public static String clipFor(TechniqueAnimSlot slot) {
        return slot == null ? null : clipFor(slot.name());
    }

    public static String clipFor(Bt3AnimationIntent intent) {
        return intent == null ? null : clipFor(intent.name());
    }

    public static String resolve(TechniqueAnimSlot slot) {
        if (slot == null) return "";
        String clip = clipFor(slot);
        if (clip == null || clip.isBlank()) return slot.defaultAnim();
        return "combat.xeno_" + clip;
    }

    /** Bound {@code combat.xeno_*} name, or null so the catalog generation still wins. */
    public static String resolve(Bt3AnimationIntent intent) {
        String clip = clipFor(intent);
        if (clip == null || clip.isBlank()) return null;
        return "combat.xeno_" + clip;
    }

    public static String defaultAnim(String slotName) {
        String slot = normalizeSlot(slotName);
        if (slot == null) return "";
        TechniqueAnimSlot tech = TechniqueAnimSlot.of(slot);
        if (tech != null) return tech.defaultAnim();
        Bt3AnimationIntent intent = intentOf(slot);
        if (intent == null) return "";
        Bt3AnimationCatalog.Clip clip = Bt3AnimationCatalog.clipFor(intent, Bt3AnimationCatalog.GEN_DEFAULT);
        return clip == null ? "" : clip.name();
    }

    public static Map<String, String> all() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(BINDINGS));
    }

    public static String normalizeSlot(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String trimmed = raw.trim();
        TechniqueAnimSlot tech = TechniqueAnimSlot.of(trimmed);
        if (tech != null) return tech.name();
        Bt3AnimationIntent intent = intentOf(trimmed);
        return intent == null ? null : intent.name();
    }

    public static Bt3AnimationIntent intentOf(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            if (intent.name().equalsIgnoreCase(trimmed)) return intent;
        }
        return null;
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }
}
