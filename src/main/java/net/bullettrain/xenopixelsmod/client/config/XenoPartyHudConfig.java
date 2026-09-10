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

/** Independent top-left layout for the nearby party-card stack. */
@OnlyIn(Dist.CLIENT)
public final class XenoPartyHudConfig {
    public static final int CARD_W = 765;
    public static final int CARD_H = 295;
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-party-hud.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int CURRENT_CONFIG_VERSION = 1;

    public static final String DEFAULT_FONT = "dragonminez:smooth";

    public static int x = 6;
    public static int y = 6;
    public static float scale = 0.24f;
    public static boolean visible = true;

    /**
     * The independently positionable pieces of one party card.
     *
     * <p>The card art bakes its own wells and gauge chrome, so these are the live pieces drawn over
     * it. Offsets are in card space, which is {@link #CARD_W} x {@link #CARD_H} before the stack's
     * own scale is applied.
     */
    public static final class Part {
        public static final int PORTRAIT = 0;
        public static final int NAME = 1;
        public static final int LEVEL = 2;
        public static final int LEADER = 3;
        public static final int FORM = 4;
        public static final int SPARKING = 5;
        public static final int HP = 6;
        public static final int KI = 7;
        public static final int STM = 8;
        public static final int COUNT = 9;
        public static final String[] NAMES = {
                "portrait", "name", "level", "leader", "form", "sparking", "hp", "ki", "stm"};

        private Part() {}

        public static int byName(String name) {
            for (int i = 0; i < NAMES.length; i++) {
                if (NAMES[i].equalsIgnoreCase(name)) return i;
            }
            return -1;
        }
    }

    /** Opt-in override of the shipped card layout. Off means the baked positions are used verbatim. */
    public static boolean customLayout = false;

    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];
    public static final float[] partScale = new float[Part.COUNT];
    public static final int[] partColor = new int[Part.COUNT];
    public static final boolean[] partBold = new boolean[Part.COUNT];
    public static final String[] partFont = new String[Part.COUNT];

    /** The colours the card shipped with, so a reset restores exactly what the art was drawn for. */
    private static final int[] DEFAULT_PART_COLOR = {
            0xFFFFFFFF, // portrait — the face is untinted
            0xFFFFFFFF, // name
            0xFFFFD54F, // level
            0xFFFFC107, // leader star
            0xFF80D8FF, // form
            0xFFFFC107, // sparking
            0xFFFFA000, // hp gauge fill
            0xFF19B9FF, // ki gauge fill
            0xFF18E6D2, // stamina gauge fill
    };

    static {
        Arrays.fill(partScale, 1.0f);
        Arrays.fill(partFont, DEFAULT_FONT);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
    }

    private XenoPartyHudConfig() {}

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

    /** Zero unless the layout override is on, so the shipped positions cannot drift by accident. */
    public static int partX(int part) {
        return customLayout ? clampPartOffset(partX[part]) : 0;
    }

    public static int partY(int part) {
        return customLayout ? clampPartOffset(partY[part]) : 0;
    }

    /**
     * Offsets are in card space, which is far larger than the screen space the other surfaces use --
     * a card is 765 wide before scaling -- so the bound is correspondingly larger.
     */
    public static int clampPartOffset(int value) {
        return Math.max(-300, Math.min(300, value));
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
            x = d.x;
            y = d.y;
            scale = clampScale(d.scale);
            visible = d.visible;
            customLayout = d.customLayout;
            copyInto(d.partX, partX);
            copyInto(d.partY, partY);
            copyInto(d.partScale, partScale);
            copyInto(d.partColor, partColor);
            copyInto(d.partBold, partBold);
            copyInto(d.partFont, partFont);
        } catch (IOException e) {
            XenoPixelsMod.LOGGER.warn("Failed to load party HUD config", e);
        }
    }

    // A config written before the card became editable has no arrays at all, and a hand-edited one
    // can be the wrong length; either way the shipped defaults stay put for whatever is missing.
    private static void copyInto(int[] from, int[] into) {
        if (from == null) return;
        System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(float[] from, float[] into) {
        if (from == null) return;
        System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(boolean[] from, boolean[] into) {
        if (from == null) return;
        System.arraycopy(from, 0, into, 0, Math.min(from.length, into.length));
    }

    private static void copyInto(String[] from, String[] into) {
        if (from == null) return;
        for (int i = 0; i < Math.min(from.length, into.length); i++) {
            if (from[i] != null && !from[i].isBlank()) into[i] = from[i];
        }
    }

    public static void save() {
        Data d = new Data();
        d.configVersion = CURRENT_CONFIG_VERSION;
        d.x = x; d.y = y; d.scale = scale; d.visible = visible;
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
            XenoPixelsMod.LOGGER.warn("Failed to save party HUD config", e);
        }
    }

    public static void reset() {
        x = 6; y = 6; scale = 0.24f; visible = true;
        resetParts();
        save();
    }

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
        int configVersion;
        int x = 6; int y = 6; float scale = 0.24f; boolean visible = true;
        boolean customLayout = false;
        int[] partX;
        int[] partY;
        float[] partScale;
        int[] partColor;
        boolean[] partBold;
        String[] partFont;
    }
}
