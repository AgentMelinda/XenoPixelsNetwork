package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Position/scale settings for the technique ("KI") hotbar and its charge (ki
 * attack) meter — both are moveable/resizeable in {@code XenoHotbarEditScreen},
 * the same way {@link XenoHudConfig} drives the main HP/KI/STM HUD editor.
 *
 * <p>Unlike the main HUD (fixed base size), the hotbar's panel size depends on
 * live data (equipped technique name lengths), so instead of storing an
 * absolute x/y we store an <em>offset</em> from the overlay's own computed
 * default anchor, plus a scale multiplier applied via a pushed pose.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class XenoHotbarConfig {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-hotbar.json");

    /** Technique slot panel — offset (px) from its default bottom-left anchor, plus scale. */
    public static int hotbarOffsetX = 0;
    public static int hotbarOffsetY = 0;
    public static float hotbarScale = 1.0f;

    /** Ki attack charge meter bar — offset (px) from its default centered anchor, plus scale. */
    public static int chargeOffsetX = 0;
    public static int chargeOffsetY = 0;
    public static float chargeScale = 1.0f;

    private XenoHotbarConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            hotbarOffsetX = data.hotbarOffsetX;
            hotbarOffsetY = data.hotbarOffsetY;
            hotbarScale = clampScale(data.hotbarScale <= 0f ? 1.0f : data.hotbarScale);
            chargeOffsetX = data.chargeOffsetX;
            chargeOffsetY = data.chargeOffsetY;
            chargeScale = clampScale(data.chargeScale <= 0f ? 1.0f : data.chargeScale);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load hotbar config", e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.hotbarOffsetX = hotbarOffsetX;
        data.hotbarOffsetY = hotbarOffsetY;
        data.hotbarScale = hotbarScale;
        data.chargeOffsetX = chargeOffsetX;
        data.chargeOffsetY = chargeOffsetY;
        data.chargeScale = chargeScale;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save hotbar config", e);
        }
    }

    public static void reset() {
        hotbarOffsetX = 0;
        hotbarOffsetY = 0;
        hotbarScale = 1.0f;
        chargeOffsetX = 0;
        chargeOffsetY = 0;
        chargeScale = 1.0f;
        save();
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private static class Data {
        int hotbarOffsetX = 0;
        int hotbarOffsetY = 0;
        float hotbarScale = 1.0f;
        int chargeOffsetX = 0;
        int chargeOffsetY = 0;
        float chargeScale = 1.0f;
    }
}
