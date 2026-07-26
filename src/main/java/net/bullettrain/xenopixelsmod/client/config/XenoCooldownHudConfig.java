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

/**
 * Layout and display options for the BT3 combat cooldown HUD.
 * Written to {@code config/xenopixelsmod-cooldown-hud.json}.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoCooldownHudConfig {
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-cooldown-hud.json");

    /** Master visibility (also gated by {@link XenoClientConfig#cooldownHudEnabled}). */
    public static boolean visible = true;

    /** Top-left anchor (screen pixels). */
    public static int x = 2;
    public static int y = 84;
    public static float scale = 0.87f;

    /** When true, hide the whole strip while nothing is on cooldown / charging. */
    public static boolean showOnlyWhenActive = false;

    /** Horizontal row (true) vs vertical column (false). */
    public static boolean horizontal = true;

    public static boolean showLabels = true;
    public static boolean showSeconds = true;
    public static boolean showIcons = true;

    // Which slots to draw
    public static boolean showVanish = true;
    public static boolean showChase = true;
    public static boolean showBackstep = true;
    public static boolean showCombo = true;
    public static boolean showCharge = true;

    /** Background panel alpha 0..1 */
    public static float panelOpacity = 0.55f;

    /**
     * When true, chips/panel draw as rectangles (no italic skew).
     * Default square; toggle via {@code /xenohud cd shape}.
     */
    public static boolean squareShape = true;

    private XenoCooldownHudConfig() {
    }

    public static void toggleSquareShape() {
        squareShape = !squareShape;
        save();
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data data = GSON.fromJson(reader, Data.class);
            if (data == null) return;
            apply(data);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load cooldown HUD config", e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(snapshot(), writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save cooldown HUD config", e);
        }
    }

    public static void reset() {
        apply(new Data());
        save();
    }

    public static void toggleVisible() {
        visible = !visible;
        save();
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static void clampToScreen(int screenWidth, int screenHeight) {
        x = Math.max(0, Math.min(Math.max(0, screenWidth - 40), x));
        y = Math.max(0, Math.min(Math.max(0, screenHeight - 40), y));
    }

    public static Data snapshot() {
        Data d = new Data();
        d.visible = visible;
        d.x = x;
        d.y = y;
        d.scale = scale;
        d.showOnlyWhenActive = showOnlyWhenActive;
        d.horizontal = horizontal;
        d.showLabels = showLabels;
        d.showSeconds = showSeconds;
        d.showIcons = showIcons;
        d.showVanish = showVanish;
        d.showChase = showChase;
        d.showBackstep = showBackstep;
        d.showCombo = showCombo;
        d.showCharge = showCharge;
        d.panelOpacity = panelOpacity;
        d.squareShape = squareShape;
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        visible = d.visible;
        x = d.x;
        y = d.y;
        scale = clampScale(d.scale <= 0f ? 1f : d.scale);
        showOnlyWhenActive = d.showOnlyWhenActive;
        horizontal = d.horizontal;
        showLabels = d.showLabels;
        showSeconds = d.showSeconds;
        showIcons = d.showIcons;
        showVanish = d.showVanish;
        showChase = d.showChase;
        showBackstep = d.showBackstep;
        showCombo = d.showCombo;
        showCharge = d.showCharge;
        panelOpacity = Math.max(0f, Math.min(1f, d.panelOpacity <= 0f ? 0.55f : d.panelOpacity));
        squareShape = d.squareShape;
    }

    public static class Data {
        public boolean visible = true;
        public int x = 0;
        public int y = 44;
        public float scale = 0.75f;
        public boolean showOnlyWhenActive = false;
        public boolean horizontal = true;
        public boolean showLabels = true;
        public boolean showSeconds = true;
        public boolean showIcons = true;
        public boolean showVanish = true;
        public boolean showChase = true;
        public boolean showBackstep = true;
        public boolean showCombo = true;
        public boolean showCharge = true;
        public float panelOpacity = 0.55f;
        public boolean squareShape = true;
    }
}
