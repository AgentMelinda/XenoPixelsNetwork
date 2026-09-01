package net.bullettrain.xenopixelsmod.combat.overcharge;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Optional technique-id → sound-id map for signature lines on overcharge release.
 *
 * <p>OST audio is not shipped (copyright, and the feature is still a work in progress).
 * A missing or unknown id is silence — never an error on the hot path.
 */
public final class OverchargeVoices {

    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();
    private static final String CLASSPATH = "/data/xenopixelsmod/overcharge_voices.json";

    private static Map<String, ResourceLocation> MAP = Map.of();

    private OverchargeVoices() {
    }

    public static void reload() {
        Map<String, ResourceLocation> next = new HashMap<>();
        loadFromClasspath(next);
        loadFromConfigOverride(next);
        MAP = next.isEmpty() ? Map.of() : Collections.unmodifiableMap(next);
    }

    public static void play(ServerLevel level, ServerPlayer player, String techniqueId) {
        if (level == null || player == null || techniqueId == null || techniqueId.isEmpty()) return;
        ResourceLocation id = MAP.get(techniqueId);
        if (id == null) return;
        if (!BuiltInRegistries.SOUND_EVENT.containsKey(id)) return;
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(id);
        if (sound == null) return;
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    private static void loadFromClasspath(Map<String, ResourceLocation> into) {
        try (InputStream in = OverchargeVoices.class.getResourceAsStream(CLASSPATH)) {
            if (in == null) return;
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                merge(into, reader);
            }
        } catch (Exception ignored) {
            // A missing or malformed bundled file must not take the server down.
        }
    }

    private static void loadFromConfigOverride(Map<String, ResourceLocation> into) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-overcharge-voices.json");
        if (!Files.isRegularFile(path)) return;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            merge(into, reader);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.debug("overcharge voices override skipped: {}", e.toString());
        }
    }

    private static void merge(Map<String, ResourceLocation> into, Reader reader) {
        JsonObject root = GSON.fromJson(reader, JsonObject.class);
        if (root == null) return;
        JsonObject voices = root.has("voices") && root.get("voices").isJsonObject()
                ? root.getAsJsonObject("voices")
                : root;
        Map<String, String> raw = GSON.fromJson(voices, MAP_TYPE);
        if (raw == null) return;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) continue;
            ResourceLocation id = ResourceLocation.tryParse(e.getValue().trim());
            if (id != null) into.put(e.getKey(), id);
        }
    }
}
