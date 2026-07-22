package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@OnlyIn(Dist.CLIENT)
public final class XenoHudConfig {
    /** Floating XV2 strip: portrait + name + HP/KI/STM */
    public static final int BASE_WIDTH = 420;
    public static final int BASE_HEIGHT = 90;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-hud.json");

    public static int x = 10;
    public static int y = 10;
    public static float scale = 0.5f;
    public static boolean visible = true;

    private XenoHudConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            x = data.x;
            y = data.y;
            scale = clampScale(data.scale <= 0f ? 0.5f : data.scale);
            visible = data.visible;
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load HUD config", e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.x = x;
        data.y = y;
        data.scale = scale;
        data.visible = visible;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save HUD config", e);
        }
    }

    public static void reset() {
        x = 10;
        y = 10;
        scale = 0.5f;
        visible = true;
        save();
    }

    public static void toggleVisible() {
        visible = !visible;
        save();
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static int scaledWidth() {
        return Math.round(BASE_WIDTH * scale);
    }

    public static int scaledHeight() {
        return Math.round(BASE_HEIGHT * scale);
    }

    public static void clampToScreen(int screenWidth, int screenHeight) {
        int w = scaledWidth();
        int h = scaledHeight();
        x = Math.max(0, Math.min(Math.max(0, screenWidth - w), x));
        y = Math.max(0, Math.min(Math.max(0, screenHeight - h), y));
    }

    private static class Data {
        int x = 10;
        int y = 10;
        float scale = 0.5f;
        boolean visible = true;
    }
}
