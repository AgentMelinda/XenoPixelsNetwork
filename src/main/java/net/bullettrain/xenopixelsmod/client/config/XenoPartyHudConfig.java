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

/** Independent top-left layout for the nearby party-card stack. */
@OnlyIn(Dist.CLIENT)
public final class XenoPartyHudConfig {
    public static final int CARD_W = 765;
    public static final int CARD_H = 295;
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-party-hud.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static int x = 6;
    public static int y = 6;
    public static float scale = 0.24f;
    public static boolean visible = true;

    private XenoPartyHudConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data d = GSON.fromJson(reader, Data.class);
            if (d == null) return;
            x = d.x;
            y = d.y;
            scale = clampScale(d.scale);
            visible = d.visible;
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load party HUD config", e);
        }
    }

    public static void save() {
        Data d = new Data(); d.x = x; d.y = y; d.scale = scale; d.visible = visible;
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) { GSON.toJson(d, writer); }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save party HUD config", e);
        }
    }

    public static void reset() { x = 6; y = 6; scale = 0.24f; visible = true; save(); }
    public static float clampScale(float value) { return Math.max(0.12f, Math.min(0.6f, value)); }
    public static int scaledWidth() { return Math.round(CARD_W * scale); }
    public static int scaledCardHeight() { return Math.round(CARD_H * scale); }
    public static int scaledHeight(int cards) {
        return cards <= 0 ? 0 : Math.round((cards * CARD_H + (cards - 1) * 18) * scale);
    }

    public static void clampToScreen(int screenW, int screenH, int cards) {
        x = Math.max(0, Math.min(Math.max(0, screenW - scaledWidth()), x));
        y = Math.max(0, Math.min(Math.max(0, screenH - scaledHeight(Math.max(1, cards))), y));
    }

    private static final class Data {
        int x = 6; int y = 6; float scale = 0.24f; boolean visible = true;
    }
}
