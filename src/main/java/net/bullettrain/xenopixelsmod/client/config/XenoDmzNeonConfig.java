package net.bullettrain.xenopixelsmod.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas;
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
 * Layout for the neon DragonMineZ character screen.
 *
 * <p>Its own file and its own part table, deliberately. {@link XenoDmzScreenConfig} is the first
 * rebuild's layout; that screen has different pieces in different places, and sharing a table would
 * mean editing one screen moved the other. The two rebuilds are kept side by side so they can be
 * compared, which only works if they are independent.
 *
 * <p>Shaped exactly like the other editable surfaces so {@code XenoElementsEditScreen} can drive it
 * through the same {@link PartLayout} contract the Panel, Chips, Ki Menu, Party and DMZ Screen
 * surfaces use.
 *
 * <p>The shipped colours are not chosen here: they come from {@link XenoNeonAtlas}, which the atlas
 * generator fills by sampling the bundle's own reference render. Several of the screen's readouts
 * are coloured per row by the design — the stat labels run red, green, magenta and cyan; some
 * statistics read gold and others white. A part's colour therefore applies only while
 * {@link #customLayout} is on; with the override off, each row keeps the colour the design gave it.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoDmzNeonConfig {

    private static final Path PATH =
            FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-dmz-neon.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_FONT = "dragonminez:smooth";

    /**
     * The independently positionable pieces of the neon character screen.
     *
     * <p>Offsets are in screen space, applied on top of the shipped layout. Blocks (the panels, the
     * ring, the stat rows, the navigation row) move as a unit; the text kinds carry their own
     * colour, bold and font so a server can restyle the readouts without moving anything.
     */
    public static final class Part {
        public static final int NAMEPLATE = 0;
        public static final int INFO_PANEL = 1;
        public static final int STATS_PANEL = 2;
        public static final int SCAN_RING = 3;
        public static final int ORBS = 4;
        /** The DragonMineZ character rendered inside the ring. */
        public static final int CHARACTER = 5;
        public static final int NAME = 6;
        public static final int RACE = 7;
        public static final int INFO_LABEL = 8;
        public static final int INFO_VALUE = 9;
        public static final int STAT_ROWS = 10;
        public static final int STAT_LABEL = 11;
        public static final int STAT_VALUE = 12;
        public static final int STAT_MULTIPLIER = 13;
        public static final int PLUS_BUTTON = 14;
        public static final int STATISTICS = 15;
        public static final int STATISTIC_LABEL = 16;
        public static final int STATISTIC_VALUE = 17;
        public static final int SUMMARY = 18;
        public static final int SUMMARY_LABEL = 19;
        public static final int SUMMARY_VALUE = 20;
        public static final int NAV_ROW = 21;
        public static final int COUNT = 22;

        public static final String[] NAMES = {
                "nameplate", "infoPanel", "statsPanel", "scanRing", "orbs", "character",
                "name", "race", "infoLabel", "infoValue",
                "statRows", "statLabel", "statValue", "statMultiplier", "plusButton",
                "statistics", "statisticLabel", "statisticValue",
                "summary", "summaryLabel", "summaryValue", "navRow"};

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
     * The colours the screen ships with, every one of them sampled from the bundle's reference
     * render by {@code tools/gen_dmz_neon_atlas.py}. The sprite parts are white because they tint
     * artwork rather than draw text.
     */
    private static final int[] DEFAULT_PART_COLOR = {
            0xFFFFFFFF,                 // nameplate
            0xFFFFFFFF,                 // info panel
            0xFFFFFFFF,                 // stats panel
            0xFFFFFFFF,                 // scan ring
            0xFFFFFFFF,                 // orbs
            0xFFFFFFFF,                 // character
            XenoNeonAtlas.LABEL,        // name
            XenoNeonAtlas.HEADER,       // race
            XenoNeonAtlas.LABEL,        // info label
            XenoNeonAtlas.VALUE,        // info value
            0xFFFFFFFF,                 // stat rows
            XenoNeonAtlas.RED,          // stat label — the design varies this per row
            XenoNeonAtlas.VALUE,        // stat value
            XenoNeonAtlas.GOLD,         // stat multiplier
            0xFFFFFFFF,                 // plus button
            0xFFFFFFFF,                 // statistics block
            XenoNeonAtlas.LABEL,        // statistic label
            XenoNeonAtlas.VALUE,        // statistic value — the design varies this per row
            0xFFFFFFFF,                 // summary block
            XenoNeonAtlas.LABEL,        // summary label
            XenoNeonAtlas.VALUE,        // summary value
            0xFFFFFFFF,                 // nav row
    };

    static {
        Arrays.fill(partScale, 1.0f);
        Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    private XenoDmzNeonConfig() {}

    public static net.minecraft.resources.ResourceLocation partFontLocation(int part) {
        return XenoHudConfig.parseFont(partFont[part]);
    }

    public static float partScale(int part) {
        return Math.max(0.25f, Math.min(3.0f, partScale[part]));
    }

    /**
     * The colour to draw a part in, given the colour the design assigns this particular row.
     *
     * <p>With the override off the design wins, which is how the stat labels keep their red, green,
     * magenta and cyan and the statistics keep their mix of gold and white. With it on the part's
     * own colour applies to every row of that kind, which is what restyling the screen means.
     */
    public static int partColor(int part, int designColor) {
        return customLayout ? partColor[part] : designColor;
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
            XenoPixelsMod.LOGGER.warn("Failed to load neon DMZ screen layout", e);
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
            XenoPixelsMod.LOGGER.warn("Failed to save neon DMZ screen layout", e);
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
