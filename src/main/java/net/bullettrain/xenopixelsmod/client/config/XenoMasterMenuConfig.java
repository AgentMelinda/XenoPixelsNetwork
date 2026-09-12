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
 * Layout for the skill-master interact menu ({@code DmzFormTrainerScreen}).
 *
 * <p>Own file, same {@link PartLayout} shape as the character screen. Offsets sit on top of the
 * shipped MASTER/FORMS chrome; {@code customLayout=false} keeps every offset at zero.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoMasterMenuConfig {

    private static final Path PATH =
            FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-master-menu.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String DEFAULT_FONT = "dragonminez:smooth";

    public static final class Part {
        public static final int NAMEPLATE = 0;
        public static final int TITLE = 1;
        public static final int TRAINER_NAME = 2;
        public static final int MASTER_PANEL = 3;
        public static final int MASTER_HEADER = 4;
        public static final int MASTER_BODY = 5;
        public static final int CLOSE_BOX = 6;
        public static final int FORMS_PANEL = 7;
        public static final int FORMS_HEADER = 8;
        public static final int FORM_ROWS = 9;
        public static final int FORM_ICON = 10;
        public static final int FORM_LABEL = 11;
        /** Kanji circle on the MASTER header; offset is on top of {@link #MASTER_HEADER}. */
        public static final int MASTER_HEADER_ICON = 12;
        /** "MASTER" title; offset is on top of {@link #MASTER_HEADER}. */
        public static final int MASTER_HEADER_LABEL = 13;
        /** Bars icon on the FORMS header; offset is on top of {@link #FORMS_HEADER}. */
        public static final int FORMS_HEADER_ICON = 14;
        /** "FORMS" title; offset is on top of {@link #FORMS_HEADER}. */
        public static final int FORMS_HEADER_LABEL = 15;
        /** "Close" on the close button; offset is on top of {@link #CLOSE_BOX}. */
        public static final int CLOSE_LABEL = 16;
        /** "ESC" hint on the close button; offset is on top of {@link #CLOSE_BOX}. */
        public static final int CLOSE_HINT = 17;
        public static final int COUNT = 18;

        public static final String[] NAMES = {
                "nameplate", "title", "trainerName", "masterPanel", "masterHeader", "masterBody",
                "closeBox", "formsPanel", "formsHeader", "formRows", "formIcon", "formLabel",
                "masterHeaderIcon", "masterHeaderLabel", "formsHeaderIcon", "formsHeaderLabel",
                "closeLabel", "closeHint"};

        private Part() {}

        public static int byName(String name) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equalsIgnoreCase(name)) return i;
            }
            return -1;
        }
    }

    public static boolean customLayout = false;

    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];
    public static final float[] partScale = new float[Part.COUNT];
    public static final int[] partColor = new int[Part.COUNT];
    public static final boolean[] partBold = new boolean[Part.COUNT];
    public static final String[] partFont = new String[Part.COUNT];
    public static final boolean[] partHidden = new boolean[Part.COUNT];

    private static final int[] DEFAULT_PART_COLOR = {
            0xFFFFFFFF, // nameplate
            0xFFE2C078, // title
            0xFF9A9A9A, // trainer name
            0xFFFFFFFF, // master panel
            0xFFE2C078, // master header
            0xFFD8D8D8, // master body
            0xFFFFFFFF, // close box
            0xFFFFFFFF, // forms panel
            0xFFE2C078, // forms header
            0xFFFFFFFF, // form rows
            0xFFFFFFFF, // form icon
            0xFFFFFFFF, // form label
            0xFFFFFFFF, // master header icon
            0xFFE2C078, // master header label
            0xFFFFFFFF, // forms header icon
            0xFFE2C078, // forms header label
            0xFFFFFFFF, // close label
            0xFF9A9A9A, // close hint
    };

    static {
        Arrays.fill(partScale, 1.0f);
        Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    private XenoMasterMenuConfig() {}

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

    public static int partX(int part) {
        return customLayout ? clampPartOffset(partX[part]) : 0;
    }

    public static int partY(int part) {
        return customLayout ? clampPartOffset(partY[part]) : 0;
    }

    public static int clampPartOffset(int value) {
        return Math.max(-200, Math.min(200, value));
    }

    public static boolean hidden(int part) {
        return part >= 0 && part < partHidden.length && partHidden[part];
    }

    public static void resetPart(int part) {
        partX[part] = 0;
        partY[part] = 0;
        partScale[part] = 1.0f;
        partBold[part] = false;
        partColor[part] = DEFAULT_PART_COLOR[part];
        partFont[part] = DEFAULT_FONT;
        partHidden[part] = false;
    }

    public static void resetParts() {
        customLayout = false;
        java.util.Arrays.fill(partHidden, false);
        for (int part = 0; part < Part.COUNT; part++) {
            resetPart(part);
        }
    }

    public static void load() {
        if (!Files.exists(PATH)) {
            save();
            return;
        }
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
            copyInto(d.partHidden, partHidden);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load master menu layout", e);
        }
    }

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
        d.partHidden = partHidden.clone();
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(PATH)) {
                GSON.toJson(d, writer);
            }
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to save master menu layout", e);
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
        boolean[] partHidden;
    }
}
