package net.bullettrain.xenopixelsmod.client.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationIntent;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Which studio clip stands in for which BT3 combat intent on this client.
 *
 * <p>Deliberately client-side and deliberately not part of {@code Bt3AnimationCatalog}: that catalog
 * is common code the server reads when it names a clip for an NPC to play, and a clip that only
 * exists in one player's config directory has no business in a decision the server makes. A binding
 * therefore changes what <em>this</em> client renders for its own moves and nothing else.
 *
 * <p>Stored as {@code config/xenopixelsmod-anim-bindings.json}, a flat intent-name to clip-name map.
 */
public final class StudioClipBindings {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Map<Bt3AnimationIntent, String> BINDINGS =
            new EnumMap<>(Bt3AnimationIntent.class);
    private static volatile boolean loaded;

    private StudioClipBindings() {}

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-anim-bindings.json");
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

    /**
     * Pure parse half, so the file format can be tested without a game directory.
     *
     * <p>A corrupt file yields no bindings rather than an exception: losing a binding is a nuisance,
     * failing to start a move is a bug.
     */
    public static Map<Bt3AnimationIntent, String> parse(String json) {
        Map<Bt3AnimationIntent, String> out = new EnumMap<>(Bt3AnimationIntent.class);
        JsonObject root;
        try {
            root = GSON.fromJson(json, JsonObject.class);
        } catch (RuntimeException e) {
            return out;
        }
        if (root == null) return out;
        for (var entry : root.entrySet()) {
            Bt3AnimationIntent intent = intentOf(entry.getKey());
            if (intent == null || !entry.getValue().isJsonPrimitive()) continue;
            String clip = XenoAnimClip.sanitize(entry.getValue().getAsString());
            if (!clip.isBlank()) out.put(intent, clip);
        }
        return out;
    }

    /** Pure serialise half. */
    public static String write(Map<Bt3AnimationIntent, String> bindings) {
        JsonObject root = new JsonObject();
        if (bindings != null) {
            bindings.forEach((intent, clip) -> {
                if (intent != null && clip != null && !clip.isBlank()) {
                    root.addProperty(intent.name(), clip);
                }
            });
        }
        return GSON.toJson(root);
    }

    /** Case-insensitive lookup; unknown names are ignored rather than throwing. */
    public static Bt3AnimationIntent intentOf(String name) {
        if (name == null) return null;
        for (Bt3AnimationIntent intent : Bt3AnimationIntent.values()) {
            if (intent.name().equalsIgnoreCase(name.trim())) return intent;
        }
        return null;
    }

    private static void ensureLoaded() {
        if (!loaded) load();
    }

    public static synchronized void save() {
        try {
            Path path = file();
            Files.createDirectories(path.getParent());
            Files.writeString(path, write(BINDINGS));
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not write animation bindings: {}", e.toString());
        }
    }

    /** @return the clip name bound to {@code intent}, or null */
    public static String clipFor(Bt3AnimationIntent intent) {
        if (intent == null) return null;
        ensureLoaded();
        return BINDINGS.get(intent);
    }

    public static synchronized void bind(Bt3AnimationIntent intent, String clipName) {
        if (intent == null) return;
        ensureLoaded();
        BINDINGS.put(intent, XenoAnimClip.sanitize(clipName));
        save();
    }

    public static synchronized boolean unbind(Bt3AnimationIntent intent) {
        if (intent == null) return false;
        ensureLoaded();
        boolean removed = BINDINGS.remove(intent) != null;
        if (removed) save();
        return removed;
    }

    public static Map<Bt3AnimationIntent, String> all() {
        ensureLoaded();
        return Collections.unmodifiableMap(new EnumMap<>(BINDINGS));
    }

    /** The GeckoLib animation names every binding points at. */
    public static java.util.Set<String> boundAnimationNames() {
        ensureLoaded();
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (String clip : BINDINGS.values()) names.add(XenoAnimClip.ANIMATION_PREFIX + clip);
        return names;
    }
}
