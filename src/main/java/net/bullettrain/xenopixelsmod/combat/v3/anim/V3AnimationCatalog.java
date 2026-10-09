package net.bullettrain.xenopixelsmod.combat.v3.anim;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Common metadata for the separately authored, occurrence-specific V3 animation file. */
public final class V3AnimationCatalog {
    public static final String PREFIX = "combat.xeno_bt3_v3_";
    public static final String CONTROL_RELEASE = PREFIX + "release_control";
    public static final String RESOURCE_PATH = "animations/entity/bt3_v3_techniques.animation.json";
    private static final String CLASSPATH_RESOURCE = "/assets/xenopixelsmod/" + RESOURCE_PATH;
    private static final Map<String, Integer> DURATIONS = loadBundled();

    private V3AnimationCatalog() {}

    public static boolean isPlayable(String id) {
        return id != null && DURATIONS.containsKey(id);
    }

    /** Returns the authored duration rounded up to server ticks, or -1 for an unknown clip. */
    public static int durationTicks(String id) {
        return id == null ? -1 : DURATIONS.getOrDefault(id, -1);
    }

    public static Set<String> allNames() {
        return DURATIONS.keySet();
    }

    private static Map<String, Integer> loadBundled() {
        try (var stream = V3AnimationCatalog.class.getResourceAsStream(CLASSPATH_RESOURCE)) {
            // Unauthored occurrences stay absent, never generic animation aliases.
            if (stream == null) return Map.of();
            return parse(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot read " + CLASSPATH_RESOURCE, failure);
        }
    }

    static Map<String, Integer> parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        JsonObject animations = root.getAsJsonObject("animations");
        if (animations == null) throw new IllegalArgumentException("V3 animation file needs animations");
        Map<String, Integer> durations = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : animations.entrySet()) {
            String name = entry.getKey();
            if (!name.startsWith(PREFIX) || name.length() == PREFIX.length() || name.length() > 64
                    || !name.matches("[a-z0-9_.]+")) {
                throw new IllegalArgumentException("Invalid V3 animation name: " + name);
            }
            JsonObject clip = entry.getValue().getAsJsonObject();
            JsonElement length = clip.get("animation_length");
            if (length == null || !length.isJsonPrimitive() || !length.getAsJsonPrimitive().isNumber()) {
                throw new IllegalArgumentException("Missing V3 animation length: " + name);
            }
            double seconds = length.getAsDouble();
            if (!Double.isFinite(seconds) || seconds <= 0 || seconds > 600) {
                throw new IllegalArgumentException("Invalid V3 animation length: " + name);
            }
            durations.put(name, Math.max(1, (int) Math.ceil(seconds * 20.0 - 1.0e-6)));
        }
        return Collections.unmodifiableMap(durations);
    }
}
