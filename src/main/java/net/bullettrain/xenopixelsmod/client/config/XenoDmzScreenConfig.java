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
import java.util.Arrays;

/**
 * Layout for the rebuilt DragonMineZ character screen.
 *
 * <p>Its own file, deliberately. The Xeno HUD's layout lives in {@link XenoHudConfig} and is not
 * touched by any of this: that HUD is settled, and a screen is a different surface with different
 * pieces. Sharing a config between them would only couple two things that have no reason to move
 * together.
 *
 * <p>Shaped exactly like the other editable surfaces so {@code XenoElementsEditScreen} can drive it
 * through the same {@link PartLayout} contract the Panel, Chips, Ki Menu and Party HUDs use.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoDmzScreenConfig {

    private static final Path PATH =
            FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-dmz-screen.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_FONT = "dragonminez:smooth";

    /**
     * The independently positionable pieces of the character screen.
     *
     * <p>Offsets are in screen space, applied on top of the shipped layout. Blocks (the panels, the
     * stat rows, the navigation row) move as a unit; the text kinds carry their own colour, bold and
     * font so a server can restyle the readouts without touching where anything sits.
     */
    public static final class Part {
        public static final int NAMEPLATE = 0;
        public static final int INFO_PANEL = 1;
        public static final int STATS_PANEL = 2;
        public static final int INFO_HEADER = 3;
        public static final int STATS_HEADER = 4;
        public static final int STATS_SUBHEADER = 5;
        public static final int NAME = 6;
        public static final int RACE = 7;
        public static final int BASIC_LABEL = 8;
        public static final int BASIC_VALUE = 9;
        public static final int STAT_ROWS = 10;
        public static final int STAT_LABEL = 11;
        public static final int STAT_VALUE = 12;
        public static final int STAT_MULTIPLIER = 13;
        public static final int PLUS_BUTTON = 14;
        public static final int STATISTICS = 15;
        public static final int STATISTIC_LABEL = 16;
        public static final int STATISTIC_VALUE = 17;
        public static final int BOTTOM_BOX = 18;
        public static final int NAV_ROW = 19;
        public static final int COUNT = 20;

        public static final String[] NAMES = {
                "nameplate", "infoPanel", "statsPanel", "infoHeader", "statsHeader",
                "statsSubheader", "name", "race", "basicLabel", "basicValue", "statRows",
                "statLabel", "statValue", "statMultiplier", "plusButton", "statistics",
                "statisticLabel", "statisticValue", "bottomBox", "navRow"};

        private Part() {}

        public static int byName(String name) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equalsIgnoreCase(name)) return i;
            }
            return -1;
        }
    }

    /** Opt-in override of the shipped layout. Off means the screen draws exactly as designed. */
    public static boolean customLayout = false;

    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];
    public static final float[] partScale = new float[Part.COUNT];
    public static final int[] partColor = new int[Part.COUNT];
    public static final boolean[] partBold = new boolean[Part.COUNT];
    public static final String[] partFont = new String[Part.COUNT];

    /**
     * The colours the screen ships with.
     *
     * <p>The multiplier is the bundle's own {@code #FECC22}, sampled from its multiplier glyphs
     * rather than picked, and is shared with the themed DragonMineZ panel through
     * {@code StatText.MULTIPLIER} so the two cannot drift.
     */
    private static final int[] DEFAULT_PART_COLOR = {
            0xFF8FD8FF, // nameplate — sprite tint, left white-ish
            0xFFFFFFFF, // info panel
            0xFFFFFFFF, // stats panel
            0xFFFFFFFF, // info header
            0xFFFFFFFF, // stats header
            0xFFFFFFFF, // stats subheader
            0xFF8FD8FF, // name
            0xFFFECC22, // race
            0xFFBFD8F0, // basic label
            0xFFFFFFFF, // basic value
            0xFFFFFFFF, // stat rows
            0xFFE04A4A, // stat label
            0xFFFFFFFF, // stat value
            0xFFFECC22, // stat multiplier
            0xFFFFFFFF, // plus button
            0xFFFFFFFF, // statistics block
            0xFF8FD8FF, // statistic label
            0xFFFFFFFF, // statistic value
            0xFFFFFFFF, // bottom box
            0xFFFFFFFF, // nav row
    };

    static {
        Arrays.fill(partScale, 1.0f);
        Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    private XenoDmzScreenConfig() {}

    public static net.minecraft.resources.ResourceLocation partFontLocation(int part) {
        return XenoHudConfig.parseFont(partFont[part]);
    }

    public static float partScale(int part) {
        return Math.max(0.25f, Math.min(3.0f, partScale[part]));
    }

    public static int partColor(int part) {
        return partColor[part];
    }

    public static int defaultPartColor(int part) {
        return DEFAULT_PART_COLOR[part];
    }

    /** Zero unless the override is on, so the shipped layout cannot drift by accident. */
    public static int partX(int part) {
        return customLayout ? clampPartOffset(partX[part]) : 0;
    }

    public static int partY(int part) {
        return customLayout ? clampPartOffset(partY[part]) : 0;
    }

    /** A screen is far larger than a HUD corner, so a part can travel further before it is lost. */
    public static int clampPartOffset(int value) {
        return Math.max(-200, Math.min(200, value));
    }

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
        for (int part = 0; part < Part.COUNT; part++) {
            resetPart(part);
        }
    }

    public static void load() {
        if (!Files.exists(PATH)) { save(); return; }
        try (Reader reader = Files.newBufferedReader(PATH)) {
            Data d = GSON.fromJson(reader, Data.class);
            if (d == null) return;
            customLayout = d.customLayout;
            copyInto(d.partX, partX);
            copyInto(d.partY, partY);
            copyInto(d.partScale, partScale);
            copyInto(d.partColor, partColor);
            copyInto(d.partBold, partBold);
            copyInto(d.partFont, partFont);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load DMZ screen layout", e);
        }
    }

    // A config written before a part was added is shorter than the array; the shipped default stays
    // for whatever is missing rather than the load failing outright.
    private static void copyInto(int[] from, int[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(float[] from, float[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(boolean[] from, boolean[] into) {
        if (from != null) System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(String[] from, String[] into) {
        if (from == null) return;
        for (int i = 0; i < Math.min(from.length, into.length); i++) {
            if (from[i] != null && !from[i].isBlank()) into[i] = from[i];
        }
    }

    public static void save() {
        Data d = new Data();
        d.customLayout = customLayout;
        d.partX = partX.clone();
        d.partY = partY.clone();
        d.partScale = partScale.clone();
        d.partColor = partColor.clone();
        d.partBold = partBold.clone();
        d.partFont = partFont.clone();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) { GSON.toJson(d, writer); }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save DMZ screen layout", e);
        }
    }

    private static final class Data {
        boolean customLayout = false;
        int[] partX;
        int[] partY;
        float[] partScale;
        int[] partColor;
        boolean[] partBold;
        String[] partFont;
    }
}
