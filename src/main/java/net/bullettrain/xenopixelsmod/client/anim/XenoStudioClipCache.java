package net.bullettrain.xenopixelsmod.client.anim;

import com.google.gson.JsonObject;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.util.GsonHelper;
import software.bernie.geckolib.animation.Animation;
import software.bernie.geckolib.loading.json.typeadapter.KeyFramesAdapter;
import software.bernie.geckolib.loading.object.BakedAnimations;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bakes the studio's own clips into real GeckoLib animations so they can play as combat moves.
 *
 * <p>GeckoLib only bakes animation JSON it finds under {@code assets/<modid>/animations} at resource
 * reload. Studio clips live in {@code config/xenopixelsmod-anims/}, outside any pack, so GeckoLib
 * never sees them; that is the only reason a saved clip could previously be previewed through
 * {@link net.bullettrain.xenopixelsmod.client.anim.studio.StudioPoseBuffer} but never played as a
 * real animation.
 *
 * <p>The baking is done by GeckoLib's own loader rather than by hand:
 * {@code KeyFramesAdapter.GEO_GSON.fromJson(GsonHelper.getAsJsonObject(root, "animations"),
 * BakedAnimations.class)} is exactly what {@code FileLoader.loadAnimationsFile} does - verified with
 * {@code javap -c} against the pinned geckolib-neoforge-1.21.1-4.9.2.jar. So easing, {@code
 * lerp_mode}, molang keyframes and every other detail of the format behave identically to a shipped
 * file.
 *
 * <p>{@code GeckoLibCache.getBakedAnimations()} is never written to. That map belongs to GeckoLib
 * and mutating it is not a contract it offers; this cache is consulted alongside it instead.
 */
public final class XenoStudioClipCache {
    private static final Map<String, Animation> BAKED = new ConcurrentHashMap<>();
    private static final Map<String, String> FAILURES = new ConcurrentHashMap<>();
    private static volatile boolean loaded;

    private XenoStudioClipCache() {}

    /** Bakes on first use; call {@link #reload()} to pick up a file written since. */
    private static void ensureLoaded() {
        if (!loaded) reload();
    }

    /**
     * Re-reads every clip this client can play: the studio's own, then the server's.
     *
     * <p>Safe to call when neither directory exists.
     */
    public static synchronized void reload() {
        BAKED.clear();
        FAILURES.clear();
        loaded = true;
        Path dir = XenoAnimClip.dir();
        if (!Files.isDirectory(dir)) return;
        int files = 0;
        for (String file : XenoAnimClip.listSaved()) {
            if (bakeFile(dir.resolve(file), file)) files++;
        }
        // Server clips are baked last so they win a name clash: on a server everybody has to be
        // watching the same animation, and the local copy is the one that is out of step.
        Path serverDir = XenoClipLibraryClient.dir();
        if (Files.isDirectory(serverDir)) {
            for (String name : XenoClipLibraryClient.names()) {
                if (bakeFile(serverDir.resolve(name + ".animation.json"), "server/" + name)) files++;
            }
        }
        if (files > 0 || !FAILURES.isEmpty()) {
            XenoPixelsMod.LOGGER.info("Studio clip cache: {} animation(s) from {} file(s), {} failed",
                    BAKED.size(), files, FAILURES.size());
        }
    }

    private static boolean bakeFile(Path path, String label) {
        try {
            JsonObject root = GsonHelper.parse(Files.readString(path));
            BakedAnimations baked = KeyFramesAdapter.GEO_GSON.fromJson(
                    GsonHelper.getAsJsonObject(root, "animations"), BakedAnimations.class);
            if (baked == null || baked.animations() == null || baked.animations().isEmpty()) {
                FAILURES.put(label, "no animations in file");
                return false;
            }
            BAKED.putAll(baked.animations());
            return true;
        } catch (IOException | RuntimeException e) {
            // One malformed clip must not take the rest of the cache - or a punch - down with it.
            FAILURES.put(label, String.valueOf(e.getMessage()));
            XenoPixelsMod.LOGGER.warn("Studio clip {} could not be baked: {}", label, e.toString());
            return false;
        }
    }

    /** Drops one clip and re-bakes it, for the studio to call right after a save. */
    public static synchronized void refresh(String clipName) {
        ensureLoaded();
        String base = XenoAnimClip.sanitize(clipName);
        BAKED.keySet().removeIf(name -> name.equals(XenoAnimClip.ANIMATION_PREFIX + base));
        FAILURES.remove(base + ".animation.json");
        bakeFile(XenoAnimClip.dir().resolve(base + ".animation.json"), base + ".animation.json");
    }

    /** @param animationName the full GeckoLib name, e.g. {@code combat.xeno_my_clip} */
    public static Animation get(String animationName) {
        if (animationName == null) return null;
        ensureLoaded();
        return BAKED.get(animationName);
    }

    public static boolean has(String animationName) {
        return get(animationName) != null;
    }

    /**
     * Authored length in seconds, or null when the clip is not baked.
     *
     * <p>{@code Animation.length()} is in ticks: {@code BakedAnimationsAdapter} multiplies the
     * file's {@code animation_length} by 20 before constructing the record (javap on the pinned
     * jar), so the division back to seconds is not optional.
     */
    public static Float lengthSeconds(String animationName) {
        Animation animation = get(animationName);
        return animation == null ? null : (float) (animation.length() / 20.0);
    }

    /** Every baked studio animation name. */
    public static Set<String> names() {
        ensureLoaded();
        return Collections.unmodifiableSet(BAKED.keySet());
    }

    /** File name to error message for every clip that failed to bake. */
    public static Map<String, String> failures() {
        ensureLoaded();
        return Collections.unmodifiableMap(new LinkedHashMap<>(FAILURES));
    }
}
