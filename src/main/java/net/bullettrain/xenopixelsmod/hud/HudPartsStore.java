package net.bullettrain.xenopixelsmod.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side global HUD parts blob. Lives in {@code config/}, not ModNetwork. */
public final class HudPartsStore {
    private static final Path PATH =
            FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-hud-parts-global.json");

    private static String json = "";

    private HudPartsStore() {}

    public static synchronized void load() {
        if (!Files.exists(PATH)) {
            json = "";
            return;
        }
        try {
            String raw = Files.readString(PATH, StandardCharsets.UTF_8);
            json = HudPartsBundle.fromJson(raw) == null ? "" : raw;
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load global HUD parts", e);
            json = "";
        }
    }

    public static synchronized boolean save(String payload) {
        HudPartsBundle parsed = HudPartsBundle.fromJson(payload);
        if (parsed == null) return false;
        String compact = HudPartsBundle.toJson(parsed);
        if (compact.length() > HudPartsBundle.MAX_JSON) return false;
        try {
            Files.createDirectories(PATH.getParent());
            Files.writeString(PATH, compact, StandardCharsets.UTF_8);
            json = compact;
            return true;
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save global HUD parts", e);
            return false;
        }
    }

    public static synchronized void clear() {
        json = "";
        try {
            Files.deleteIfExists(PATH);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to delete global HUD parts", e);
        }
    }

    public static synchronized String json() {
        return json == null ? "" : json;
    }

    public static boolean present() {
        return !json().isBlank();
    }
}
