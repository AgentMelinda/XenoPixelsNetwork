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

@OnlyIn(Dist.CLIENT)
public final class XenoHudConfig {
    /** Floating XV2 strip: portrait + name + HP/KI/STM */
    public static final int BASE_WIDTH = 420;
    public static final int BASE_HEIGHT = 90;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-hud.json");
    private static final int CURRENT_CONFIG_VERSION = 1;

    public static int x = 3;
    public static int y = 3;
    public static float scale = 0.55f;
    public static boolean visible = true;
    /**
     * Phase 4 migration-testing toggle: true = legacy procedural renderer,
     * false = LDLib-backed {@code XenoHudView} (the default).
     * Temporary; remove once the LDLib renderer is signed off (repo plan.md Phase 4).
     */
    public static boolean legacyHudRenderer = false;
    /**
     * Phase 6 migration-testing toggle: true = legacy procedural technique
     * hotbar chrome (default, unchanged behavior), false = LDLib-tinted-texture
     * chrome for the same slots (all data/selection/cooldown logic unchanged).
     * Temporary; remove once signed off (repo plan.md Phase 6).
     */
    public static boolean legacyTechniqueRenderer = true;
    /**
     * Modern-unified renderer: the stat cluster and the BT3 combat cooldown strip drawn as one
     * panel at the main HUD's position and scale, instead of two independently placed ones.
     *
     * <p>Only meaningful while {@link #legacyHudRenderer} is false, which is why this remains a
     * second flag rather than replacing the legacy-renderer switch.
     */
    public static boolean unifiedHudRenderer = true;

    /** True when the unified renderer owns drawing the cooldown strip. */
    public static boolean unifiedActive() {
        return !legacyHudRenderer && unifiedHudRenderer;
    }

    private XenoHudConfig() {}

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            boolean migrated = data.configVersion < CURRENT_CONFIG_VERSION;
            if (migrated) {
                data.legacyHudRenderer = false;
                data.unifiedHudRenderer = true;
                data.configVersion = CURRENT_CONFIG_VERSION;
            }
            x = data.x;
            y = data.y;
            scale = clampScale(data.scale <= 0f ? 0.55f : data.scale);
            visible = data.visible;
            legacyHudRenderer = data.legacyHudRenderer;
            legacyTechniqueRenderer = data.legacyTechniqueRenderer;
            unifiedHudRenderer = data.unifiedHudRenderer;
            if (migrated) save();
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load HUD config", e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.configVersion = CURRENT_CONFIG_VERSION;
        data.x = x;
        data.y = y;
        data.scale = scale;
        data.visible = visible;
        data.legacyHudRenderer = legacyHudRenderer;
        data.legacyTechniqueRenderer = legacyTechniqueRenderer;
        data.unifiedHudRenderer = unifiedHudRenderer;
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
        x = 3;
        y = 3;
        scale = 0.55f;
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

    /**
     * Unscaled footprint of the last unified panel drawn.
     *
     * <p>The unified plate's height depends on how many combat chips wrap into it, so unlike the
     * other renderers it has no constant. Clamping it against {@link #BASE_HEIGHT} let the panel
     * hang off the bottom of the screen, and dragging it in {@code /xenohud edit} stopped short
     * of the real edge. Reported by the view each frame; the constants stand in until then.
     */
    private static volatile int unifiedWidth = BASE_WIDTH;
    private static volatile int unifiedHeight = BASE_HEIGHT;

    public static void reportUnifiedSize(int width, int height) {
        unifiedWidth = Math.max(1, width);
        unifiedHeight = Math.max(1, height);
    }

    public static int baseWidth() {
        return unifiedActive() ? unifiedWidth : BASE_WIDTH;
    }

    public static int baseHeight() {
        return unifiedActive() ? unifiedHeight : BASE_HEIGHT;
    }

    public static int scaledWidth() {
        return Math.round(baseWidth() * scale);
    }

    public static int scaledHeight() {
        return Math.round(baseHeight() * scale);
    }

    public static void clampToScreen(int screenWidth, int screenHeight) {
        int w = scaledWidth();
        int h = scaledHeight();
        x = Math.max(0, Math.min(Math.max(0, screenWidth - w), x));
        y = Math.max(0, Math.min(Math.max(0, screenHeight - h), y));
    }

    private static class Data {
        int configVersion;
        int x = 3;
        int y = 3;
        float scale = 0.55f;
        boolean visible = true;
        boolean legacyHudRenderer = false;
        boolean legacyTechniqueRenderer = true;
        boolean unifiedHudRenderer = true;
    }
}
