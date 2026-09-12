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
 * Layout and display options for the BT3 combat cooldown HUD.
 * Written to {@code config/xenopixelsmod-cooldown-hud.json}.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoCooldownHudConfig {
    public static final int DEFAULT_X = 0;
    public static final int DEFAULT_Y = 42;
    public static final float DEFAULT_SCALE = 0.475f;
    public static final boolean DEFAULT_SHOW_ONLY_WHEN_ACTIVE = false;

    public static final float MIN_SCALE = 0.25f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-cooldown-hud.json");
    private static final int CURRENT_CONFIG_VERSION = 2;

    /** Master visibility (also gated by {@link XenoClientConfig#cooldownHudEnabled}). */
    public static boolean visible = true;

    /** Top-left anchor (screen pixels). */
    public static int x = DEFAULT_X;
    public static int y = DEFAULT_Y;
    public static float scale = DEFAULT_SCALE;

    /** When true, hide the whole strip while nothing is on cooldown / charging. */
    public static boolean showOnlyWhenActive = DEFAULT_SHOW_ONLY_WHEN_ACTIVE;

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
    /** Draw chips on the Xenoverse blank action shell instead of the procedural plates. */
    public static boolean xenoverseShell = true;
    /** Chip caption size relative to the font's native height. */
    public static float textScale = 0.6f;
    /** Opt-in override of the computed caption layout. Off means the centred default is used. */
    public static boolean textCustomLayout = false;
    /**
     * Per-element nudges in chip space, applied to every chip alike. Ignored unless custom is on.
     *
     * <p>Each part of a chip moves independently so a layout can be dialled in against the artwork
     * rather than fought as one block: the key sits in the pod, the label and counter share the
     * centre panel, and the meter has its own band.
     */
    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];

    /** The independently positionable pieces of a chip. */
    public static final class Part {
        public static final int KEY = 0;
        public static final int LABEL = 1;
        public static final int COUNTER = 2;
        public static final int METER = 3;
        public static final int COUNT = 4;
        public static final String[] NAMES = {"key", "label", "counter", "meter"};

        private Part() {}

        public static int byName(String name) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equalsIgnoreCase(name)) return i;
            }
            return -1;
        }
    }

    /** Per-part scale multiplier. The global {@link #textScale} still applies on top of these. */
    public static final float[] partScale = new float[Part.COUNT];
    /** Per-part colour, ARGB. Defaults are the colours the strip shipped with. */
    public static final int[] partColor = new int[Part.COUNT];
    /** Per-part bold. */
    public static final boolean[] partBold = new boolean[Part.COUNT];
    /** Font per element, so a counter can use a fixed-width face while its label stays default. */
    public static final String[] partFont = new String[Part.COUNT];
    public static final boolean[] partHidden = new boolean[Part.COUNT];

    private static final int[] DEFAULT_PART_COLOR = {
            0xFFB6DDF1, // key
            0xFFF4F8FF, // label
            0xFFFFE0B2, // counter
            0xFF55C7FF, // meter
    };

    /** See {@link XenoHudConfig#DEFAULT_FONT} for why the DMZ face is safe as a blanket default. */
    public static final String DEFAULT_FONT = "dragonminez:smooth";

    /** What {@link #DEFAULT_FONT} used to be, for the v2 migration. */
    private static final String LEGACY_DEFAULT_FONT_V1 = "minecraft:default";

    static {
        java.util.Arrays.fill(partScale, 1.0f);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    /** Font for one element. Falls back to the default face rather than throwing on a bad id. */
    public static net.minecraft.resources.ResourceLocation partFontLocation(int part) {
        return XenoHudConfig.parseFont(partFont[part]);
    }

    /** Effective scale for one part: its own multiplier times the strip-wide setting. */
    public static float partScale(int part) {
        return clampTextScale(textScale) * Math.max(0.25f, Math.min(3.0f, partScale[part]));
    }

    public static int partColor(int part) {
        return partColor[part];
    }

    public static int defaultPartColor(int part) {
        return DEFAULT_PART_COLOR[part];
    }

    public static void adjustPartColor(int part, int channelShift, int delta) {
        int c = partColor[part];
        int v = (c >> channelShift) & 0xFF;
        v = Math.max(0, Math.min(255, v + delta));
        partColor[part] = (c & ~(0xFF << channelShift)) | (v << channelShift);
    }

    public static int partX(int part) {
        return textCustomLayout ? clampTextOffset(partX[part]) : 0;
    }

    public static int partY(int part) {
        return textCustomLayout ? clampTextOffset(partY[part]) : 0;
    }

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
            final int fromVersion = data.configVersion;
            boolean migrated = fromVersion < CURRENT_CONFIG_VERSION;
            // Gated to v1 specifically. It used to fire on any version bump, which would have made
            // the v2 font migration silently reset a deliberate showOnlyWhenActive as a side effect.
            if (fromVersion < 1) {
                data.showOnlyWhenActive = DEFAULT_SHOW_ONLY_WHEN_ACTIVE;
            }
            if (fromVersion < 2 && data.partFont != null) {
                // Pre-v2 stored the old default for every part; only untouched ones move over.
                for (int i = 0; i < data.partFont.length; i++) {
                    if (LEGACY_DEFAULT_FONT_V1.equals(data.partFont[i])) {
                        data.partFont[i] = DEFAULT_FONT;
                    }
                }
            }
            if (migrated) {
                data.configVersion = CURRENT_CONFIG_VERSION;
            }
            apply(data);
            if (migrated) save();
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load cooldown HUD config", e);
        }
    }

    /** Bounded so a stray value cannot fling captions off the strip entirely. */
    public static int clampTextOffset(int value) {
        return Math.max(-40, Math.min(40, value));
    }

    /** All part offsets in one line, for the command readout and for reporting a finished layout. */
    public static String describeParts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Part.COUNT; i++) {
            if (i > 0) sb.append(' ');
            sb.append(Part.NAMES[i]).append('=').append(partX[i]).append(',').append(partY[i]);
        }
        return sb.toString();
    }

    /**
     * Put one element back to stock: position, colour, size and weight.
     *
     * <p>Separate from the whole-layout reset so a single part can be undone after experimenting
     * without discarding every other part that was already dialled in.
     */
    public static void resetPart(int part) {
        partX[part] = 0;
        partY[part] = 0;
        partScale[part] = 1.0f;
        partBold[part] = false;
        partColor[part] = DEFAULT_PART_COLOR[part];
        partFont[part] = DEFAULT_FONT;
        partHidden[part] = false;
    }

    public static void resetTextLayout() {
        textCustomLayout = false;
        java.util.Arrays.fill(partX, 0);
        java.util.Arrays.fill(partY, 0);
        java.util.Arrays.fill(partScale, 1.0f);
        java.util.Arrays.fill(partBold, false);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
        java.util.Arrays.fill(partHidden, false);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
        textScale = 0.6f;
    }

    public static float clampTextScale(float value) {
        return Math.max(0.35f, Math.min(1.0f, value));
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
        d.configVersion = CURRENT_CONFIG_VERSION;
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
        d.xenoverseShell = xenoverseShell;
        d.textScale = textScale;
        d.textCustomLayout = textCustomLayout;
        d.partX = partX.clone();
        d.partY = partY.clone();
        d.partScale = partScale.clone();
        d.partColor = partColor.clone();
        d.partBold = partBold.clone();
        d.partFont = partFont.clone();
        d.partHidden = partHidden.clone();
        return d;
    }

    public static void apply(Data d) {
        if (d == null) return;
        visible = d.visible;
        x = d.x;
        y = d.y;
        scale = clampScale(d.scale <= 0f ? DEFAULT_SCALE : d.scale);
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
        xenoverseShell = d.xenoverseShell;
        textScale = clampTextScale(d.textScale);
        textCustomLayout = d.textCustomLayout;
        for (int i = 0; i < Part.COUNT; i++) {
            partX[i] = d.partX != null && i < d.partX.length ? clampTextOffset(d.partX[i]) : 0;
            partY[i] = d.partY != null && i < d.partY.length ? clampTextOffset(d.partY[i]) : 0;
            partScale[i] = d.partScale != null && i < d.partScale.length && d.partScale[i] > 0f
                    ? Math.max(0.25f, Math.min(3.0f, d.partScale[i])) : 1.0f;
            partColor[i] = d.partColor != null && i < d.partColor.length && d.partColor[i] != 0
                    ? d.partColor[i] : DEFAULT_PART_COLOR[i];
            partBold[i] = d.partBold != null && i < d.partBold.length && d.partBold[i];
            String storedFont = d.partFont != null && i < d.partFont.length ? d.partFont[i] : null;
            partFont[i] = storedFont == null || storedFont.isBlank() ? DEFAULT_FONT : storedFont;
            partHidden[i] = d.partHidden != null && i < d.partHidden.length && d.partHidden[i];
        }
    }

    public static class Data {
        public int configVersion;
        public boolean visible = true;
        public int x = DEFAULT_X;
        public int y = DEFAULT_Y;
        public float scale = DEFAULT_SCALE;
        public boolean showOnlyWhenActive = DEFAULT_SHOW_ONLY_WHEN_ACTIVE;
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
        public boolean xenoverseShell = true;
        public float textScale = 0.6f;
        public boolean textCustomLayout = false;
        public int[] partX = new int[Part.COUNT];
        public int[] partY = new int[Part.COUNT];
        public float[] partScale = new float[Part.COUNT];
        public int[] partColor = new int[Part.COUNT];
        public boolean[] partBold = new boolean[Part.COUNT];
        public String[] partFont = new String[Part.COUNT];
        public boolean[] partHidden = new boolean[Part.COUNT];
    }
}
