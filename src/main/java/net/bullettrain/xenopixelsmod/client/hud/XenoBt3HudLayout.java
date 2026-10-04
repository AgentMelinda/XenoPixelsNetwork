package net.bullettrain.xenopixelsmod.client.hud;

import java.util.function.IntUnaryOperator;

/**
 * Where every piece of the BT3 HUD sits, and how far each bar is filled.
 *
 * <p>Separate from {@link XenoBt3HudView} and free of Minecraft types so the arithmetic can be
 * tested without a client. The placement constants are panel-space offsets stacked on top of
 * {@link net.bullettrain.xenopixelsmod.client.config.XenoHudConfig}'s x/y/scale, so the whole
 * composition still moves and scales as one object in {@code /xenohud edit}.
 *
 * <p>Sprite-internal geometry — the portrait well, the bar tracks, the segment starts — is not
 * here. It is measured off the art by {@code tools/gen_bt3_hud_atlas.py} and lives in
 * {@link XenoBt3HudAtlas}; this class only decides where the sprites go relative to each other.
 *
 * <p>The cluster is laid out at the atlas's own pixel density (a 186px nameplate, a 62px portrait
 * ring), which is the density the generator normalises to. That makes the panel narrower than the
 * modern renderer's 420px artwork; the HUD scale is how a player makes it bigger, rather than this
 * view upscaling art it has no extra resolution for.
 */
public final class XenoBt3HudLayout {

    private XenoBt3HudLayout() {
    }

    /** Nameplate left edge. The portrait ring overlaps its first few pixels, as the art expects. */
    public static final int NAMEPLATE_X = 56;
    public static final int NAMEPLATE_Y = 0;

    /**
     * Portrait ring origin.
     *
     * <p>Dropped below the nameplate's top edge so the ring reads as sitting in front of the plate
     * rather than balanced on it, which is how BT3 frames its player one card.
     */
    public static final int PORTRAIT_X = 0;
    public static final int PORTRAIT_Y = 6;

    /** Left edge shared by the three bars, clear of the portrait ring. */
    public static final int BAR_X = 64;
    public static final int HP_Y = 36;
    public static final int KI_Y = 57;
    public static final int STM_Y = 78;
    /** Form name, on its own line under the bars rather than crowded into the nameplate well. */
    public static final int FORM_Y = 99;

    /** Bottom of the stat cluster: the form line plus a line's descent. */
    public static final int CLUSTER_HEIGHT = 110;

    /**
     * Panel width.
     *
     * <p>The nameplate is the widest thing in the cluster, so the panel ends where it ends. The
     * combat rail is laid out to fit inside this rather than the other way round.
     */
    public static final int PANEL_WIDTH = NAMEPLATE_X + 186;

    // --- combat rail ---------------------------------------------------------------------

    /** Four across, because the bundle ships exactly four ability-icon colours. */
    public static final int RAIL_COLUMNS = 4;
    public static final int RAIL_TOP = CLUSTER_HEIGHT + 2;
    /** Widest technique shell (36) plus a pixel of air. */
    public static final int SLOT_PITCH_X = 37;
    /** Tallest technique shell. */
    public static final int SLOT_HEIGHT = 39;
    /** Shell plus the key label drawn beneath it. */
    public static final int SLOT_ROW_HEIGHT = SLOT_HEIGHT + 10;

    // --- follow-up prompt ----------------------------------------------------------------

    public static final int PROMPT_GAP = 4;
    /** The arrow, which is taller than the plate beside it. */
    public static final int PROMPT_HEIGHT = 35;
    public static final int PROMPT_ARROW_X = 0;
    /** Plate left edge: clear of the arrow, which is 29 wide. */
    public static final int PROMPT_PLATE_X = 32;
    /** The plate is 25 tall against the arrow's 35, so it rides the arrow's middle. */
    public static final int PROMPT_PLATE_INSET_Y = 5;

    /** How many rail rows {@code chips} wrap into. */
    public static int railRows(int chips) {
        if (chips <= 0) return 0;
        return (chips + RAIL_COLUMNS - 1) / RAIL_COLUMNS;
    }

    /** Height the rail occupies, zero when there is nothing to draw in it. */
    public static int railHeight(int chips) {
        return railRows(chips) * SLOT_ROW_HEIGHT;
    }

    /** Left edge of a rail slot. */
    public static int slotX(int index) {
        return (index % RAIL_COLUMNS) * SLOT_PITCH_X;
    }

    /** Top edge of a rail slot. */
    public static int slotY(int index) {
        return RAIL_TOP + (index / RAIL_COLUMNS) * SLOT_ROW_HEIGHT;
    }

    /** Top edge of the follow-up prompt, below whatever the rail took. */
    public static int promptY(int chips) {
        int railBottom = RAIL_TOP + railHeight(chips);
        return (chips <= 0 ? CLUSTER_HEIGHT : railBottom) + PROMPT_GAP;
    }

    /**
     * Unscaled height of everything currently drawn.
     *
     * <p>Reported to {@code XenoHudConfig} each frame the way the unified renderer reports its own,
     * because a panel whose height depends on how many chips wrapped into it cannot have a constant
     * one: clamping it against a constant lets it hang off the bottom of the screen and stops the
     * editor's drag short of the real edge.
     */
    public static int panelHeight(int chips, boolean prompt) {
        if (prompt) return promptY(chips) + PROMPT_HEIGHT;
        if (chips > 0) return RAIL_TOP + railHeight(chips);
        return CLUSTER_HEIGHT;
    }

    // --- fills ---------------------------------------------------------------------------

    /** Pixels of {@link XenoBt3HudAtlas#HP_BAR}'s lit track to show for a health fraction. */
    public static int hpFillWidth(float fraction) {
        return Math.round(XenoBt3HudAtlas.HP_TRACK_WIDTH * clamp(fraction));
    }

    /**
     * How much of a segmented bar sprite to blit for a fraction.
     *
     * <p>Clipping the lit art rather than tinting a rectangle is what keeps the gloss and the
     * slanted end caps the bundle drew; the cut lands on a real segment boundary because the
     * boundaries were measured off the art rather than divided out of the bar's width.
     *
     * <p>The leading segment fills partially, so a bar that is a third of the way through a segment
     * shows that, instead of snapping between whole segments and hiding small changes entirely.
     *
     * @param segmentStart left edge of segment {@code i}, from the generated atlas geometry
     */
    public static int segmentClipWidth(float fraction, int count, IntUnaryOperator segmentStart,
                                       int segmentWidth, int spriteWidth) {
        float f = clamp(fraction);
        if (f <= 0f || count <= 0) return 0;
        if (f >= 1f) return spriteWidth;
        float units = f * count;
        int whole = (int) units;
        if (whole >= count) return spriteWidth;
        float part = units - whole;
        int x = segmentStart.applyAsInt(whole);
        return Math.min(spriteWidth, x + Math.round(segmentWidth * part));
    }

    /** {@link #segmentClipWidth} for the ki bar. */
    public static int kiClipWidth(float fraction) {
        return segmentClipWidth(fraction, XenoBt3HudAtlas.kiSegmentCount(),
                XenoBt3HudAtlas::kiSegmentStart, XenoBt3HudAtlas.KI_SEGMENT_WIDTH,
                XenoBt3HudAtlas.KI_BAR.width());
    }

    /** {@link #segmentClipWidth} for the stamina bar. */
    public static int staminaClipWidth(float fraction) {
        return segmentClipWidth(fraction, XenoBt3HudAtlas.staminaSegmentCount(),
                XenoBt3HudAtlas::staminaSegmentStart, XenoBt3HudAtlas.STAMINA_SEGMENT_WIDTH,
                XenoBt3HudAtlas.STAMINA_BAR.width());
    }

    /**
     * Which technique shell a combat chip gets.
     *
     * <p>Nearest hue to the chip's own accent colour rather than the chip's position in the rail,
     * so Z-Burst keeps its purple and the charge moves keep their amber wherever a player's chip
     * settings happen to put them. Four shells cannot be exact; they can be consistent.
     *
     * @return 0 purple, 1 blue, 2 green, 3 orange
     */
    public static int shellForAccent(int argb) {
        float hue = hue(argb);
        float[] shells = {285f, 205f, 130f, 30f};
        int best = 0;
        float bestGap = Float.MAX_VALUE;
        for (int i = 0; i < shells.length; i++) {
            float gap = Math.abs(hue - shells[i]);
            if (gap > 180f) gap = 360f - gap;
            if (gap < bestGap) {
                bestGap = gap;
                best = i;
            }
        }
        return best;
    }

    /** Hue in degrees, 0..360. Grey returns 0, which lands on the orange shell. */
    private static float hue(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float span = max - min;
        if (span <= 0f) return 0f;
        float h;
        if (max == r) {
            h = 60f * (((g - b) / span) % 6f);
        } else if (max == g) {
            h = 60f * ((b - r) / span + 2f);
        } else {
            h = 60f * ((r - g) / span + 4f);
        }
        return h < 0f ? h + 360f : h;
    }

    private static float clamp(float value) {
        if (Float.isNaN(value)) return 0f;
        return Math.max(0f, Math.min(1f, value));
    }
}
