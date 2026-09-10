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
    public static final int BASE_HEIGHT = 180;
    public static final float MIN_SCALE = 0.5f;
    public static final float MAX_SCALE = 2.5f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FMLPaths.CONFIGDIR.get().resolve("xenopixelsmod-hud.json");
    private static final int CURRENT_CONFIG_VERSION = 11;

    /** What fills the round portrait well on the main panel. */
    public enum PortraitMode {
        /** Flat player skin face. */
        SKIN,
        /** Live DragonMineZ character — race model, hair, active form. */
        CHARACTER;

        public static PortraitMode parse(String value) {
            if (value == null) return CHARACTER;
            return "character".equalsIgnoreCase(value.trim()) ? CHARACTER : SKIN;
        }
    }

    /**
     * Per-element nudges for the main panel, in panel space. Ignored unless {@link #customLayout}.
     *
     * <p>The artwork's wells are not evenly spaced and a single block offset cannot satisfy all of
     * them, so every piece the panel draws over the chrome moves independently.
     */
    public static final int[] partX = new int[Part.COUNT];
    public static final int[] partY = new int[Part.COUNT];
    /** Opt-in override of the built-in layout. Off means the shipped positions are used verbatim. */
    public static boolean customLayout = false;

    /** The independently positionable pieces of the main HUD panel. */
    public static final class Part {
        public static final int PORTRAIT = 0;
        public static final int NAME = 1;
        public static final int LEVEL = 2;
        public static final int FORM = 3;
        public static final int RELEASE = 4;
        public static final int RELEASE_TEXT = 5;
        public static final int HP = 6;
        public static final int HP_TEXT = 7;
        public static final int KI = 8;
        public static final int KI_TEXT = 9;
        public static final int STM = 10;
        public static final int STM_TEXT = 11;
        public static final int SPARKING = 12;
        public static final int COUNT = 13;
        public static final String[] NAMES = {
                "portrait", "name", "level", "form", "release", "releaseText",
                "hp", "hpText", "ki", "kiText", "stm", "stmText", "sparking"};

        private Part() {}
    }

    /**
     * Text colour per element, ARGB.
     *
     * <p>Always applied — the defaults are exactly the colours the panel shipped with, so nothing
     * changes until one is edited. Unlike the offsets there is no master toggle, because a colour
     * that silently reverts when a toggle is off is far more confusing than one that just applies.
     */
    public static final int[] partColor = new int[Part.COUNT];

    private static final int[] DEFAULT_PART_COLOR = {
            0xFFFFFFFF, // portrait (unused, kept so the array is index-parallel with Part)
            0xFFFFFFFF, // name
            0xFFFFD54F, // level
            0xFF80D8FF, // form
            0xFFFFFFFF, // release bar (unused)
            0xFF00FF00, // releaseText
            0xFFFFFFFF, // hp bar (unused)
            0xFF00FF00, // hpText
            0xFFFFFFFF, // ki bar (unused)
            0xFF00FF00, // kiText
            0xFFFFFFFF, // stm bar (unused)
            0xFFFFFF11, // stmText
            0xFFFFC107, // sparking
    };

    /**
     * The colours the four bar readouts shipped with before they were tuned in game.
     *
     * <p>Kept only so the v4 migration can tell an untouched value from a deliberate one: a config
     * still carrying these gets the new high-contrast defaults, while anything else is a real player
     * choice and is left alone. Pale tints over a lit bar never had the contrast to be readable,
     * which is the whole reason the defaults moved.
     */
    private static final int[] LEGACY_PART_COLOR_V3 = {
            0xFFFFFFFF, 0xFFFFFFFF, 0xFFFFD54F, 0xFF80D8FF, 0xFFFFFFFF, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFFFE0B2, 0xFFFFFFFF, 0xFFCCF2FF, 0xFFFFFFFF, 0xFFC4FFF6, 0xFFFFC107,
    };

    /**
     * Tuned stock layout, in panel space, measured in game.
     *
     * <p>These are the shipped positions, not player edits: {@link #partX}/{@link #partY} stack on
     * top of them. Keeping the two separate is what lets {@link #resetPart} return an element to the
     * tuned look rather than to the raw computed position, and lets {@link #customLayout} stay off
     * on a fresh install while the panel still looks right.
     */
    private static final int[] DEFAULT_PART_DX = {
            0,   //  portrait
            0,   //  name
            -24, //  level
            0,   //  form (its nudge is baked into the renderer's FORM_X_OFFSET)
            0,   //  release bar
            0,   //  releaseText
            0,   //  hp bar
            -4,  //  hpText
            0,   //  ki bar
            -6,  //  kiText
            0,   //  stm bar
            0,   //  stmText
            0,   //  sparking
    };
    private static final int[] DEFAULT_PART_DY = {
            8, 0, 5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
    };

    /** The four numeric readouts run small and bold; everything else ships at 1.0 and normal. */
    private static final float[] DEFAULT_PART_SCALE = new float[Part.COUNT];
    private static final boolean[] DEFAULT_PART_BOLD = new boolean[Part.COUNT];

    static {
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
        java.util.Arrays.fill(DEFAULT_PART_SCALE, 1.0f);
        for (int p : new int[]{Part.RELEASE_TEXT, Part.HP_TEXT, Part.KI_TEXT, Part.STM_TEXT}) {
            DEFAULT_PART_SCALE[p] = 0.60f;
            DEFAULT_PART_BOLD[p] = true;
        }
    }

    public static int defaultPartDx(int part) {
        return DEFAULT_PART_DX[part];
    }

    public static int defaultPartDy(int part) {
        return DEFAULT_PART_DY[part];
    }

    /** Per-element scale, 1.0 = as shipped. Lets the player render and each text run size alone. */
    public static final float[] partScale = new float[Part.COUNT];

    /** Per-element bold. Minecraft's bold is a synthetic double-draw, so it also thickens. */
    public static final boolean[] partBold = new boolean[Part.COUNT];

    /**
     * Font for HUD text, as a {@code namespace:path} font id.
     *
     * <p>Minecraft resolves this through its own font system, so anything a resource pack provides
     * at {@code assets/<ns>/font/<path>.json} works. Vanilla ships {@code minecraft:default},
     * {@code minecraft:uniform} (fixed width, good for numbers), {@code minecraft:alt} and
     * {@code minecraft:illageralt}. No third-party mod is needed for this — Modern UI restyles the
     * default font globally rather than offering a per-element choice.
     */
    public static String fontId = "minecraft:default";

    /**
     * Font per element, so a numeric readout can use a fixed-width face while its label stays on the
     * default one. Seeded from the older global {@link #fontId} when an existing config is loaded.
     */
    public static final String[] partFont = new String[Part.COUNT];

    /**
     * The id every part starts on.
     *
     * <p>DragonMineZ is a hard dependency and its face is the one this HUD's artwork was drawn
     * against, so it ships as the default rather than being something to pick thirteen times. It is
     * safe as a blanket default because {@code assets/dragonminez/font/smooth.json} carries the full
     * vanilla provider set and ends with a {@code reference} to {@code minecraft:default}, so any
     * glyph it does not draw itself falls through instead of rendering as a box.
     */
    public static final String DEFAULT_FONT = "dragonminez:smooth";

    /** What {@link #DEFAULT_FONT} used to be, for the v5 migration below. */
    private static final String LEGACY_DEFAULT_FONT_V4 = "minecraft:default";

    static {
        System.arraycopy(DEFAULT_PART_SCALE, 0, partScale, 0, Part.COUNT);
        System.arraycopy(DEFAULT_PART_BOLD, 0, partBold, 0, Part.COUNT);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
    }

    public static net.minecraft.resources.ResourceLocation fontLocation() {
        return parseFont(fontId);
    }

    /** Font for one element. Falls back to the default face rather than throwing on a bad id. */
    public static net.minecraft.resources.ResourceLocation partFontLocation(int part) {
        return parseFont(partFont[part]);
    }

    static net.minecraft.resources.ResourceLocation parseFont(String id) {
        net.bullettrain.xenopixelsmod.util.XenoIdentifierDiagnostics.reportIfMalformed(
                id, "XenoHudConfig font");
        net.minecraft.resources.ResourceLocation rl =
                id == null || id.isBlank() || id.endsWith(":")
                        ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        return rl != null ? rl : net.minecraft.resources.ResourceLocation.withDefaultNamespace("default");
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

    /** Nudge one channel, keeping alpha. Used by the editor's R/G/B buttons. */
    public static void adjustPartColor(int part, int channelShift, int delta) {
        int c = partColor[part];
        int v = (c >> channelShift) & 0xFF;
        v = Math.max(0, Math.min(255, v + delta));
        partColor[part] = (c & ~(0xFF << channelShift)) | (v << channelShift);
    }

    public static int partX(int part) {
        return customLayout ? clampPartOffset(partX[part]) : 0;
    }

    public static int partY(int part) {
        return customLayout ? clampPartOffset(partY[part]) : 0;
    }

    /** Bounded so a stray value cannot fling an element off the panel entirely. */
    public static int clampPartOffset(int value) {
        return Math.max(-120, Math.min(120, value));
    }

    /** All part offsets in one line, for the command readout and for reporting a finished layout. */
    public static String describeParts() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Part.COUNT; i++) {
            // Compared against the tuned baseline, not against zero — otherwise a fresh install
            // would report every part as modified.
            boolean movedPart = partX[i] != 0 || partY[i] != 0;
            boolean tinted = partColor[i] != DEFAULT_PART_COLOR[i];
            boolean resized = partScale[i] != DEFAULT_PART_SCALE[i];
            boolean weighted = partBold[i] != DEFAULT_PART_BOLD[i];
            boolean refaced = !DEFAULT_FONT.equals(partFont[i]);
            if (!movedPart && !tinted && !resized && !weighted && !refaced) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Part.NAMES[i]).append('=').append(partX[i]).append(',').append(partY[i]);
            if (tinted) sb.append('#').append(String.format("%08X", partColor[i]));
            if (resized) sb.append('@').append(String.format("%.2f", partScale[i]));
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
        partScale[part] = DEFAULT_PART_SCALE[part];
        partBold[part] = DEFAULT_PART_BOLD[part];
        partColor[part] = DEFAULT_PART_COLOR[part];
        partFont[part] = DEFAULT_FONT;
    }

    public static void resetParts() {
        customLayout = false;
        java.util.Arrays.fill(partX, 0);
        java.util.Arrays.fill(partY, 0);
        System.arraycopy(DEFAULT_PART_COLOR, 0, partColor, 0, Part.COUNT);
        System.arraycopy(DEFAULT_PART_SCALE, 0, partScale, 0, Part.COUNT);
        System.arraycopy(DEFAULT_PART_BOLD, 0, partBold, 0, Part.COUNT);
        java.util.Arrays.fill(partFont, DEFAULT_FONT);
    }

    /** Top-left corner. Previously a {@code -1} sentinel resolved to top-right on first render. */
    public static int x = 0;
    public static int y = 0;
    public static PortraitMode portraitMode = PortraitMode.CHARACTER;
    /** Clip the portrait to the well's circle. Off leaves a square with visible corners. */
    public static boolean portraitMask = true;
    /** Trace transform charge around the well instead of boxing it in a rectangle. */
    public static boolean transformRing = true;
    /**
     * Entity render scale for {@link PortraitMode#CHARACTER}.
     *
     * <p>Same units as vanilla's inventory paper doll, which fits a whole player in a 49x70 box at
     * 30. The well is 81x75, so 28 leaves a little margin for the circular mask to bite into.
     */
    public static int portraitScale = 28;
    /** Vertical framing for the character portrait, in entity heights. Vanilla centres at 0.0625. */
    public static float portraitOffset = 0.0625f;
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

    /**
     * The renderer actually in use, and the value that is stored.
     *
     * <p>{@link #legacyHudRenderer} and {@link #unifiedHudRenderer} are kept in step with it by
     * {@link #setRenderer} so the call sites that already read them keep working. Write through
     * {@code setRenderer} rather than assigning those booleans, or the three disagree.
     */
    public static XenoHudRenderer renderer = XenoHudRenderer.MODERN_UNIFIED;

    /**
     * Chooses a renderer and republishes the two compatibility booleans.
     *
     * <p>BT3 reports itself as neither legacy nor unified, so any caller still branching on those
     * booleans falls through to its modern path instead of the procedural one -- the closer of the
     * two, and never a crash.
     */
    public static void setRenderer(XenoHudRenderer next) {
        renderer = next == null ? XenoHudRenderer.MODERN_UNIFIED : next;
        legacyHudRenderer = renderer == XenoHudRenderer.LEGACY;
        unifiedHudRenderer = renderer == XenoHudRenderer.MODERN_UNIFIED;
    }

    /** True when the unified renderer owns drawing the cooldown strip. */
    public static boolean unifiedActive() {
        return renderer == XenoHudRenderer.MODERN_UNIFIED;
    }

    /**
     * What happens to the DragonMineZ menus behind V, and to the DMZ HUD textures that follow the
     * same setting -- lock-on, radar and the four scouters.
     *
     * <p>Defaults to {@link DmzMenuMode#THEME}: DragonMineZ's own screens, its own widgets,
     * scrolling, packets and validation, dressed in Xeno chrome. This is the look the mod is meant
     * to ship with.
     *
     * <p>Every other route stays one command away and none of them was removed.
     * {@code /xenohud menus stock} is the way back to DragonMineZ untouched, which is what makes
     * "is this a Xeno bug or a DMZ one?" answerable in game; {@code screen} and {@code neon} are the
     * two full rebuilds of the character page.
     */
    public static DmzMenuMode dmzMenuMode = DmzMenuMode.DEFAULT;

    /** True when Xeno draws over DMZ's menus, in either rework. Read by the theme and the swap. */
    public static boolean dmzMenusThemed() {
        return dmzMenuMode.themed();
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
            // Captured before the stamp below rewrites it. Every migration gate has to read this
            // rather than data.configVersion, which is CURRENT by the time the part loop runs.
            final int fromVersion = data.configVersion;
            boolean migrated = fromVersion < CURRENT_CONFIG_VERSION;
            if (fromVersion < 2) {
                data.legacyHudRenderer = false;
                data.unifiedHudRenderer = true;
            }
            if (fromVersion < 3) {
                data.portraitMode = "character";
            }
            if (migrated) {
                data.configVersion = CURRENT_CONFIG_VERSION;
            }
            // Older builds stored -1 as "resolve me to the top-right on first render". Nothing
            // resolves it now, so a negative would survive as a real off-screen coordinate.
            x = Math.max(0, data.x);
            y = Math.max(0, data.y);
            scale = clampScale(data.scale <= 0f ? 0.55f : data.scale);
            visible = data.visible;
            legacyTechniqueRenderer = data.legacyTechniqueRenderer;
            // Version 6 replaced the two renderer booleans with a named renderer. A config written
            // before that has no name to read, so the choice is reconstructed from the booleans
            // exactly -- the HUD must look identical across the upgrade, and nobody is moved to
            // bt3, which is opt-in.
            setRenderer(fromVersion < 6 || data.renderer == null
                    ? XenoHudRenderer.fromLegacyFlags(data.legacyHudRenderer, data.unifiedHudRenderer)
                    : XenoHudRenderer.parse(data.renderer,
                            XenoHudRenderer.fromLegacyFlags(data.legacyHudRenderer,
                                    data.unifiedHudRenderer)));
            // Version 11 makes the themed menus the default. Same shape as version 10's rule, which
            // put them back to stock, and for the same reason: a config already written at 10
            // records "stock" explicitly and would otherwise be parsed straight back onto it. A
            // choice made at 11 or later is kept, so anyone who picks stock, screen or neon after
            // this point keeps it.
            dmzMenuMode = fromVersion < 11
                    ? DmzMenuMode.DEFAULT
                    : DmzMenuMode.parse(data.dmzMenuMode, DmzMenuMode.DEFAULT);
            portraitMode = PortraitMode.parse(data.portraitMode);
            portraitMask = data.portraitMask;
            transformRing = data.transformRing;
            portraitScale = clampPortraitScale(data.portraitScale);
            portraitOffset = clampPortraitOffset(data.portraitOffset);
            customLayout = data.customLayout;
            // Pre-v4 configs predate the tuned baseline: they stored the old stock colour, weight
            // and size for every part. Adopting the new defaults wholesale would throw away real
            // player tints, so only values still sitting on the old stock value are moved over.
            boolean adoptTunedDefaults = fromVersion < 4;
            for (int i = 0; i < Part.COUNT; i++) {
                partX[i] = data.partX != null && i < data.partX.length ? clampPartOffset(data.partX[i]) : 0;
                partY[i] = data.partY != null && i < data.partY.length ? clampPartOffset(data.partY[i]) : 0;
                partColor[i] = data.partColor != null && i < data.partColor.length && data.partColor[i] != 0
                        ? data.partColor[i] : DEFAULT_PART_COLOR[i];
                partScale[i] = data.partScale != null && i < data.partScale.length && data.partScale[i] > 0f
                        ? Math.max(0.25f, Math.min(3.0f, data.partScale[i])) : DEFAULT_PART_SCALE[i];
                partBold[i] = data.partBold != null && i < data.partBold.length
                        ? data.partBold[i] : DEFAULT_PART_BOLD[i];
                if (adoptTunedDefaults) {
                    if (partColor[i] == LEGACY_PART_COLOR_V3[i]) partColor[i] = DEFAULT_PART_COLOR[i];
                    if (partScale[i] == 1.0f) partScale[i] = DEFAULT_PART_SCALE[i];
                    if (!partBold[i]) partBold[i] = DEFAULT_PART_BOLD[i];
                }
                // The old single font applied to everything, so it seeds every part rather than
                // silently reverting a choice the player already made.
                String stored = data.partFont != null && i < data.partFont.length ? data.partFont[i] : null;
                if (stored == null || stored.isBlank()) {
                    stored = data.fontId != null && !data.fontId.isBlank() ? data.fontId : DEFAULT_FONT;
                }
                // Pre-v5 configs stored the old default explicitly for every part, so without this
                // nobody already running the mod would ever see the new one. A part on any other
                // font is a deliberate choice and is left alone.
                if (fromVersion < 5 && LEGACY_DEFAULT_FONT_V4.equals(stored)) {
                    stored = DEFAULT_FONT;
                }
                partFont[i] = stored;
            }
            if (data.fontId != null && !data.fontId.isBlank()) fontId = data.fontId;
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
        data.renderer = renderer.id();
        data.dmzMenuMode = dmzMenuMode.id();
        data.legacyHudRenderer = legacyHudRenderer;
        data.legacyTechniqueRenderer = legacyTechniqueRenderer;
        data.unifiedHudRenderer = unifiedHudRenderer;
        data.portraitMode = portraitMode.name().toLowerCase(java.util.Locale.ROOT);
        data.portraitMask = portraitMask;
        data.transformRing = transformRing;
        data.portraitScale = portraitScale;
        data.portraitOffset = portraitOffset;
        data.customLayout = customLayout;
        data.partX = partX.clone();
        data.partY = partY.clone();
        data.partColor = partColor.clone();
        data.partScale = partScale.clone();
        data.partBold = partBold.clone();
        data.partFont = partFont.clone();
        data.fontId = fontId;
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
        x = 0;
        y = 0;
        scale = 0.55f;
        visible = true;
        portraitMode = PortraitMode.CHARACTER;
        portraitMask = true;
        transformRing = true;
        portraitScale = 28;
        portraitOffset = 0.0625f;
        resetParts();
        save();
    }

    public static void toggleVisible() {
        visible = !visible;
        save();
    }

    public static float clampScale(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    public static int clampPortraitScale(int value) {
        return Math.max(4, Math.min(120, value));
    }

    public static float clampPortraitOffset(float value) {
        return Math.max(-2.0f, Math.min(2.0f, value));
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
        /** Null in a config written before version 6; the booleans are read instead. */
        String renderer;
        /** Null in a config written before version 7; the theming was unconditional then. */
        String dmzMenuMode;
        int x = 0;
        int y = 0;
        float scale = 0.55f;
        boolean visible = true;
        boolean legacyHudRenderer = false;
        boolean legacyTechniqueRenderer = true;
        boolean unifiedHudRenderer = true;
        String portraitMode = "character";
        boolean portraitMask = true;
        boolean transformRing = true;
        int portraitScale = 28;
        float portraitOffset = 0.0625f;
        boolean customLayout = false;
        int[] partX = new int[Part.COUNT];
        int[] partY = new int[Part.COUNT];
        int[] partColor = new int[Part.COUNT];
        float[] partScale = new float[Part.COUNT];
        boolean[] partBold = new boolean[Part.COUNT];
        String[] partFont = new String[Part.COUNT];
        String fontId = "minecraft:default";
    }
}
