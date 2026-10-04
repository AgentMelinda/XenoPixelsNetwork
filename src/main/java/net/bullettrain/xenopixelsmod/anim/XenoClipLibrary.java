package net.bullettrain.xenopixelsmod.anim;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.combat.anim.Bt3AnimationCatalog;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The server's copy of the studio clips every player should be able to see.
 *
 * <p>A studio clip is a file in one player's config directory. That is fine while they are the only
 * one watching, but an NPC playing a clip has to animate for everyone in range, and the other
 * clients have never seen the file. So an operator pushes their clips here, the server keeps them,
 * and every client is handed the set when it joins - the same shape
 * {@code HudPartsStore} / {@code HudPartsNetwork} already use for the global HUD layout, over its
 * own channel so {@code ModNetwork} stays frozen.
 *
 * <p>The caps exist because this content is authored by a player and then sent to every other
 * player. A clip that will not parse, is too large, or would push the set over the total budget is
 * refused at the door rather than shipped.
 */
public final class XenoClipLibrary {
    private static final Gson GSON = new GsonBuilder().create();

    /** At most this many clips in the library. */
    public static final int MAX_CLIPS = 64;

    /** At most this much JSON for one clip. */
    public static final int MAX_CLIP_BYTES = 256 * 1024;

    /** At most this much JSON across the whole library. */
    public static final int MAX_TOTAL_BYTES = 2 * 1024 * 1024;

    private static final Map<String, String> CLIPS = new TreeMap<>();

    private XenoClipLibrary() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-anim-library");
    }

    /**
     * The clips this mod ships, seeded into the library when they are not already there.
     *
     * <p>They are social gestures rather than combat moves - a wave, a greeting, a nod, a turn on
     * the spot, and a small idle - and NPCs use them to read as alive. Shipped as resources rather
     * than left for an operator to push, because a greeting nobody installed is a greeting that
     * never happens.
     */
    private static final String[] BUILT_IN = {
            "wave", "hi_wave", "nod", "spin", "idle_shift"
    };

    private static final String BUILT_IN_PATH = "/assets/xenopixelsmod/animations/social/";

    /**
     * Exact hashes of every earlier shipped wave file, so an install still holding one is upgraded
     * to the current clip. User-edited clips never match these and are left alone.
     *
     * <p>The 2026-09-26 pair is the wave that keyed the whole arm as one stiff limb and swung it
     * up-and-down twice; it is replaced by the forward-then-three-side-to-side wave, so installs
     * seeded from that build have to be listed here too.
     */
    private static final Map<String, java.util.Set<String>> OLD_WAVE_HASHES = Map.of(
            "wave", java.util.Set.of(
                    "93966144c3aeac6e677cbcf56abca14b20b4c3763c121b30bc10f6e59a94615e",
                    "58ec38c638d08b9fee7f77e8ff1518bfe2e4adee9affff4d504a5c7a3862823c",
                    "ab23a030c6ec315f650502d443f53192cc31af574d12ba8ec5cbb3646699626f"),
            "hi_wave", java.util.Set.of(
                    "ef555823111d9678f55bd15e9a3711f169f1f063fdd9fdf79985c48f54569172",
                    "2998b8aa24187160f25ab6c23b41224995e03295a2eb7da3f8fae0d498982f24",
                    "58b60296dcf22c0ede829257fff8c0d1ae2a4b16539f1e6e9172ef5a0fc4764d",
                    // The shipped 2026-09-26 greeting, before the run-client edit was promoted.
                    "65279581e7769dc312a67bbdee17b6bb2bf0e787d99dbe6e94bd80758a14ded"));

    /**
     * Writes any missing built-in clip into the library directory.
     *
     * <p>Missing clips are seeded. The two earlier shipped waves are also upgraded by exact
     * content hash; operator edits under the same names are preserved.
     */
    private static void seedBuiltIns() {
        for (String name : BUILT_IN) {
            Path file = dir().resolve(name + ".animation.json");
            boolean upgrade;
            try {
                upgrade = Files.exists(file) && isOldWave(file, name);
                if (Files.exists(file) && !upgrade) continue;
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not inspect built-in clip {}: {}", name, e.toString());
                continue;
            }
            try (var in = XenoClipLibrary.class.getResourceAsStream(
                    BUILT_IN_PATH + name + ".animation.json")) {
                if (in == null) {
                    XenoPixelsMod.LOGGER.warn("Built-in clip {} is missing from the jar", name);
                    continue;
                }
                String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                String problem = validate(name, json);
                if (problem != null) {
                    XenoPixelsMod.LOGGER.warn("Built-in clip {} is not usable: {}", name, problem);
                    continue;
                }
                Files.createDirectories(dir());
                Files.writeString(file, json, StandardCharsets.UTF_8);
                if (upgrade) XenoPixelsMod.LOGGER.info("Upgraded shipped animation clip {}", name);
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not seed built-in clip {}: {}", name, e.toString());
            }
        }
    }

    private static boolean isOldWave(Path file, String name) throws IOException {
        java.util.Set<String> expected = OLD_WAVE_HASHES.get(name);
        if (expected == null) return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
            return expected.contains(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException missingSha256) {
            return false;
        }
    }

    /** Re-reads the library from disk and tells the catalog which names are now playable. */
    public static synchronized void load() {
        CLIPS.clear();
        seedBuiltIns();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(p -> p.getFileName().toString().endsWith(".animation.json"))
                        .sorted()
                        .forEach(XenoClipLibrary::readOne);
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not read the animation library: {}", e.toString());
            }
        }
        publishNames();
        if (!CLIPS.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Animation library: {} clip(s)", CLIPS.size());
        }
    }

    private static void readOne(Path file) {
        String name = sanitize(file.getFileName().toString().replace(".animation.json", ""));
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            if (validate(name, json) == null) CLIPS.put(name, json);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Could not read library clip {}: {}", name, e.toString());
        }
    }

    /**
     * Adds or replaces one clip.
     *
     * @return null on success, or why it was refused
     */
    public static synchronized String put(String rawName, String json) {
        String name = sanitize(rawName);
        String problem = validate(name, json);
        if (problem != null) return problem;
        if (!CLIPS.containsKey(name) && CLIPS.size() >= MAX_CLIPS) {
            return "the library already holds " + MAX_CLIPS + " clips";
        }
        int total = totalBytes() - byteLength(CLIPS.get(name)) + byteLength(json);
        if (total > MAX_TOTAL_BYTES) {
            return "the library would exceed " + (MAX_TOTAL_BYTES / 1024) + " KB";
        }
        CLIPS.put(name, json);
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(name + ".animation.json"), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            CLIPS.remove(name);
            return "could not write it: " + e.getMessage();
        }
        publishNames();
        return null;
    }

    public static synchronized void clear() {
        CLIPS.clear();
        Path dir = dir();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path file : stream.toList()) {
                    if (file.getFileName().toString().endsWith(".animation.json")) {
                        Files.deleteIfExists(file);
                    }
                }
            } catch (IOException e) {
                XenoPixelsMod.LOGGER.warn("Could not clear the animation library: {}", e.toString());
            }
        }
        publishNames();
    }

    public static synchronized boolean isEmpty() {
        return CLIPS.isEmpty();
    }

    public static synchronized Set<String> names() {
        return Collections.unmodifiableSet(new java.util.LinkedHashSet<>(CLIPS.keySet()));
    }

    /**
     * {@code animation_length} of a published clip, in ticks. {@code name} is the bare clip name
     * or a {@code combat.xeno_*} animation name. Returns {@code 0} when the library has no file
     * or the JSON has no length.
     */
    public static synchronized int durationTicks(String name) {
        String bare = sanitize(name == null ? "" : name);
        if (bare.startsWith("combat_xeno_")) {
            bare = bare.substring("combat_xeno_".length());
        } else if (name != null && name.startsWith("combat.xeno_")) {
            bare = sanitize(name.substring("combat.xeno_".length()));
        }
        String json = CLIPS.get(bare);
        if (json == null) {
            return 0;
        }
        JsonObject root = parseObject(json);
        if (root == null) {
            return 0;
        }
        JsonElement animations = root.get("animations");
        if (animations == null || !animations.isJsonObject()) {
            return 0;
        }
        for (var entry : animations.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) {
                continue;
            }
            JsonElement length = entry.getValue().getAsJsonObject().get("animation_length");
            if (length == null || !length.isJsonPrimitive()) {
                continue;
            }
            try {
                return Math.max(0, Math.round(length.getAsFloat() * 20.0f));
            } catch (RuntimeException ignored) {
                return 0;
            }
        }
        return 0;
    }

    /** The whole library as one object of clip name to file contents, for the join packet. */
    public static synchronized String bundle() {
        JsonObject root = new JsonObject();
        CLIPS.forEach((name, json) -> {
            JsonElement parsed = parse(json);
            if (parsed != null) root.add(name, parsed);
        });
        return GSON.toJson(root);
    }

    /** Splits a bundle back into clip name to file contents. Never throws on bad input. */
    public static Map<String, String> unbundle(String bundle) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonObject root = parseObject(bundle);
        if (root == null) return out;
        for (var entry : root.entrySet()) {
            String name = sanitize(entry.getKey());
            if (!entry.getValue().isJsonObject()) continue;
            String json = GSON.toJson(entry.getValue());
            if (validate(name, json) == null) out.put(name, json);
        }
        return out;
    }

    /** @return null when the clip is acceptable, otherwise the reason it is not */
    public static String validate(String name, String json) {
        if (name == null || name.isBlank() || !name.equals(sanitize(name))) {
            return "the name is not a plain clip name";
        }
        if (json == null || json.isBlank()) return "it is empty";
        if (byteLength(json) > MAX_CLIP_BYTES) {
            return "it is larger than " + (MAX_CLIP_BYTES / 1024) + " KB";
        }
        JsonObject root = parseObject(json);
        if (root == null) return "it is not valid JSON";
        JsonElement animations = root.get("animations");
        if (animations == null || !animations.isJsonObject()
                || animations.getAsJsonObject().entrySet().isEmpty()) {
            return "it holds no animations";
        }
        return null;
    }

    public static String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "";
        return raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static void publishNames() {
        Bt3AnimationCatalog.clearDynamicAnimations();
        for (String name : CLIPS.keySet()) {
            Bt3AnimationCatalog.registerDynamicAnimation("combat.xeno_" + name);
        }
    }

    private static int totalBytes() {
        int total = 0;
        for (String json : CLIPS.values()) total += byteLength(json);
        return total;
    }

    private static int byteLength(String json) {
        return json == null ? 0 : json.getBytes(StandardCharsets.UTF_8).length;
    }

    private static JsonObject parseObject(String json) {
        JsonElement parsed = parse(json);
        return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
    }

    private static JsonElement parse(String json) {
        try {
            return GSON.fromJson(json, JsonElement.class);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
