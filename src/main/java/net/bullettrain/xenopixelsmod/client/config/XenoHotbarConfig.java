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

    /**
     * Schema version, added when the default font moved to the DragonMineZ face.
     *
     * <p>A file written before this has no such field and deserialises to 0, which is what the
     * migration in {@link #load()} keys off. Without a version the font migration would have to run
     * on every load and would permanently override anyone who deliberately picked the old default.
     */
    private static final int CURRENT_CONFIG_VERSION = 1;

    /** Technique slot panel — offset (px) from its default bottom-left anchor, plus scale. */
    public static int hotbarOffsetX = 0;
    public static int hotbarOffsetY = 0;
    public static float hotbarScale = 1.0f;

    /** Ki attack charge meter bar — offset (px) from its default centered anchor, plus scale. */
    public static int chargeOffsetX = 0;
    public static int chargeOffsetY = 0;
    public static float chargeScale = 1.0f;

    /** Draw the ki menu on the Xenoverse blank panel instead of the procedural plate. */
    public static boolean xenoverseShell = true;
    /** Opt-in per-element override. Off means the shipped positions and colours are used. */
    public static boolean customLayout = false;
    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];
    public static final float[] partScale = new float[Part.COUNT];
    public static final int[] partColor = new int[Part.COUNT];
    public static final boolean[] partBold = new boolean[Part.COUNT];
    /** Font per element, so a readout can use a fixed-width face while its label stays default. */
    public static final String[] partFont = new String[Part.COUNT];

    /** The independently positionable pieces of a ki menu row and its header. */
    public static final class Part {
        public static final int HEADER = 0;
        public static final int COUNT_LABEL = 1;
        public static final int KEY = 2;
        public static final int SLOT = 3;
        public static final int NAME = 4;
        public static final int COOLDOWN = 5;
        public static final int STATUS = 6;
        /**
         * The selected-row glow bar.
         *
         * <p>The only part that is a texture rather than text, so bold and font do nothing for it
         * while position, size and colour all apply — colour multiplies the artwork, and its alpha
         * sets how strongly the bar reads.
         */
        public static final int HIGHLIGHT = 7;
        public static final int COUNT = 8;
        public static final String[] NAMES = {
                "header", "count", "key", "slot", "name", "cooldown", "status", "highlight"};

        private Part() {}
    }

    private static final int[] DEFAULT_PART_COLOR = {
            0xFF90CAF9, // header
            0xFF78909C, // count
            0xFFFFFFFF, // key
            0xFF607D8B, // slot
            0xFFE3F2FD, // name
            0xFFFFCC80, // cooldown
            0xFF69F0AE, // status
            0xFFFFFFFF, // highlight — white leaves the artwork's own blue untinted
    };

    /**
     * Tuned stock offsets, measured in game. {@link #partX}/{@link #partY} stack on top of these,
     * so a fresh install looks right with {@link #customLayout} off and a per-part reset returns to
     * the tuned position rather than the raw computed one.
     *
     */
    private static final int[] DEFAULT_PART_DX = {0, 10, 0, 0, 0, 0, 0, 7};
    private static final int[] DEFAULT_PART_DY = {0, 2, 0, 0, 0, 0, 0, 0};

    /** See {@link XenoHudConfig#DEFAULT_FONT} for why the DMZ face is safe as a blanket default. */
    public static final String DEFAULT_FONT = "dragonminez:smooth";

    /** What {@link #DEFAULT_FONT} used to be, for the v1 migration. */
    private static final String LEGACY_DEFAULT_FONT_V0 = "minecraft:default";

    static {
        java.util.Arrays.fill(partScale, 1.0f);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    public static int defaultPartDx(int part) { return DEFAULT_PART_DX[part]; }

    public static int defaultPartDy(int part) { return DEFAULT_PART_DY[part]; }

    /** Font for one element. Falls back to the default face rather than throwing on a bad id. */
    public static net.minecraft.resources.ResourceLocation partFontLocation(int part) {
        return XenoHudConfig.parseFont(partFont[part]);
    }

    public static int partX(int part) { return customLayout ? clampPartOffset(partX[part]) : 0; }

    public static int partY(int part) { return customLayout ? clampPartOffset(partY[part]) : 0; }

    public static float partScale(int part) {
        return Math.max(0.25f, Math.min(3.0f, partScale[part]));
    }

    public static int partColor(int part) { return partColor[part]; }

    public static int defaultPartColor(int part) { return DEFAULT_PART_COLOR[part]; }

    public static int clampPartOffset(int value) {
        return Math.max(-160, Math.min(160, value));
    }

    public static void adjustPartColor(int part, int channelShift, int delta) {
        int c = partColor[part];
        int v = (c >> channelShift) & 0xFF;
        v = Math.max(0, Math.min(255, v + delta));
        partColor[part] = (c & ~(0xFF << channelShift)) | (v << channelShift);
    }

    public static String describeParts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Part.COUNT; i++) {
            boolean refaced = !DEFAULT_FONT.equals(partFont[i]);
            boolean changed = partX[i] != 0 || partY[i] != 0 || partScale[i] != 1.0f
                    || partBold[i] || partColor[i] != DEFAULT_PART_COLOR[i] || refaced;
            if (!changed) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Part.NAMES[i]).append('=').append(partX[i]).append(',').append(partY[i]);
            if (partColor[i] != DEFAULT_PART_COLOR[i]) sb.append(String.format("#%08X", partColor[i]));
            if (partScale[i] != 1.0f) sb.append(String.format("@%.2f", partScale[i]));
            if (partBold[i]) sb.append("+bold");
            if (refaced) sb.append('/').append(partFont[i]);
        }
        return sb.length() == 0 ? "all default" : sb.toString();
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
    }

    public static void resetParts() {
        customLayout = false;
        java.util.Arrays.fill(partX, 0);
        java.util.Arrays.fill(partY, 0);
        java.util.Arrays.fill(partScale, 1.0f);
        java.util.Arrays.fill(partBold, false);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    private XenoHotbarConfig() {}

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
            hotbarOffsetX = data.hotbarOffsetX;
            hotbarOffsetY = data.hotbarOffsetY;
            hotbarScale = clampScale(data.hotbarScale <= 0f ? 1.0f : data.hotbarScale);
            chargeOffsetX = data.chargeOffsetX;
            chargeOffsetY = data.chargeOffsetY;
            chargeScale = clampScale(data.chargeScale <= 0f ? 1.0f : data.chargeScale);
            xenoverseShell = data.xenoverseShell;
            customLayout = data.customLayout;
            for (int i = 0; i < Part.COUNT; i++) {
                partX[i] = data.partX != null && i < data.partX.length ? clampPartOffset(data.partX[i]) : 0;
                partY[i] = data.partY != null && i < data.partY.length ? clampPartOffset(data.partY[i]) : 0;
                partScale[i] = data.partScale != null && i < data.partScale.length && data.partScale[i] > 0f
                        ? Math.max(0.25f, Math.min(3.0f, data.partScale[i])) : 1.0f;
                partColor[i] = data.partColor != null && i < data.partColor.length && data.partColor[i] != 0
                        ? data.partColor[i] : DEFAULT_PART_COLOR[i];
                partBold[i] = data.partBold != null && i < data.partBold.length && data.partBold[i];
                String stored = data.partFont != null && i < data.partFont.length ? data.partFont[i] : null;
                if (stored == null || stored.isBlank()) stored = DEFAULT_FONT;
                // Pre-v1 stored the old default for every part; only untouched ones move over, so a
                // deliberately chosen font survives.
                if (fromVersion < 1 && LEGACY_DEFAULT_FONT_V0.equals(stored)) stored = DEFAULT_FONT;
                partFont[i] = stored;
            }
            if (migrated) save();
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load hotbar config", e);
        }
    }

    public static void save() {
        Data data = new Data();
        data.configVersion = CURRENT_CONFIG_VERSION;
        data.hotbarOffsetX = hotbarOffsetX;
        data.hotbarOffsetY = hotbarOffsetY;
        data.hotbarScale = hotbarScale;
        data.chargeOffsetX = chargeOffsetX;
        data.chargeOffsetY = chargeOffsetY;
        data.chargeScale = chargeScale;
        data.xenoverseShell = xenoverseShell;
        data.customLayout = customLayout;
        data.partX = partX.clone();
        data.partY = partY.clone();
        data.partScale = partScale.clone();
        data.partColor = partColor.clone();
        data.partBold = partBold.clone();
        data.partFont = partFont.clone();
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
        resetParts();
        save();
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    private static class Data {
        int configVersion;
        int hotbarOffsetX = 0;
        int hotbarOffsetY = 0;
        float hotbarScale = 1.0f;
        int chargeOffsetX = 0;
        int chargeOffsetY = 0;
        float chargeScale = 1.0f;
        boolean xenoverseShell = true;
        boolean customLayout = false;
        int[] partX = new int[Part.COUNT];
        int[] partY = new int[Part.COUNT];
        float[] partScale = new float[Part.COUNT];
        int[] partColor = new int[Part.COUNT];
        boolean[] partBold = new boolean[Part.COUNT];
        String[] partFont = new String[Part.COUNT];
    }
}
