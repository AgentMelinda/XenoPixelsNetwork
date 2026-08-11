package net.bullettrain.xenopixelsmod.client.hud;

/**
 * Atlas regions and layout coordinates for the modern (textured) HUD.
 *
 * <p><b>Paired by hand with {@code tools/hud_layout.py}.</b> The element sizes and atlas
 * regions below are the same numbers that generator emits art at; changing one without the
 * other produces a stretched or mis-sampled HUD. Regenerate with
 * {@code python tools/gen_hud_textures.py} after editing either.
 *
 * <p>Sizes are authored at 1:1 with the atlas so nothing is stretched. The HUD applies its own
 * uniform {@code XenoHudConfig.scale} on top.
 */
public final class XenoHudLayout {

    private XenoHudLayout() {
    }

    public static final int ATLAS = 512;

    // --- element sizes (mirror tools/hud_layout.py) ---
    public static final int BAR_W = 300;
    public static final int BAR_H = 14;
    public static final int STM_SEG_W = 14;
    public static final int STM_SEG_H = 10;
    public static final int PORTRAIT = 64;
    /** Backing-plate source size. Drawn nine-sliced, so this is never the on-screen size. */
    public static final int PANEL_SIZE = 48;
    /** Nine-slice inset; the generator holds the whole bevel inside this band. */
    public static final int PANEL_CORNER = 16;
    /** Width of the leading-edge cap stamped at a partial bar's fill boundary. */
    public static final int BAR_TIP_W = 6;

    /** One atlas region. */
    public record Region(int u, int v, int w, int h) {
    }

    public static final Region HP_EMPTY = new Region(0, 0, BAR_W, BAR_H);
    public static final Region HP_FULL = new Region(0, BAR_H, BAR_W, BAR_H);
    public static final Region KI_EMPTY = new Region(0, BAR_H * 2, BAR_W, BAR_H);
    public static final Region KI_FULL = new Region(0, BAR_H * 3, BAR_W, BAR_H);
    public static final Region BAR_FRAME = new Region(0, BAR_H * 4, BAR_W, BAR_H);
    public static final Region STM_OFF = new Region(0, BAR_H * 5, STM_SEG_W, STM_SEG_H);
    public static final Region STM_ON = new Region(STM_SEG_W, BAR_H * 5, STM_SEG_W, STM_SEG_H);
    /** Brighter cap for the last lit stamina segment. */
    public static final Region STM_TIP = new Region(STM_SEG_W * 2, BAR_H * 5, STM_SEG_W, STM_SEG_H);
    /** Hot variant swapped in below {@link #HP_CRIT_FRACTION}. */
    public static final Region HP_CRIT = new Region(0, BAR_H * 6, BAR_W, BAR_H);
    public static final Region BAR_TIP = new Region(0, BAR_H * 7, BAR_TIP_W, BAR_H);
    public static final Region PORTRAIT_FRAME = new Region(310, 0, PORTRAIT, PORTRAIT);
    public static final Region PANEL = new Region(310, 68, PANEL_SIZE, PANEL_SIZE);
    public static final Region SPARK_OFF = new Region(340, 128, 12, 12);
    public static final Region SPARK_ON = new Region(356, 128, 12, 12);

    // --- BT3 combat cooldown strip (mirror tools/hud_layout.py) ---
    /** Chip plate source square; nine-sliced to the real chip rectangle. */
    public static final int CD_CHIP = 32;
    public static final int CD_CHIP_CORNER = 8;
    public static final int CD_BADGE_W = 24;
    public static final int CD_BADGE_H = 14;
    public static final int CD_BADGE_CORNER = 4;
    public static final int CD_RAIL_W = 4;
    public static final int CD_RAIL_H = 24;
    public static final int CD_METER_W = 48;
    public static final int CD_METER_H = 6;

    /** Idle chip. */
    public static final Region CD_CHIP_IDLE = new Region(0, 210, CD_CHIP, CD_CHIP);
    /** Chip whose move is charging or cooling down. */
    public static final Region CD_CHIP_HOT = new Region(36, 210, CD_CHIP, CD_CHIP);
    /** Chip whose move is disabled by config or server state. */
    public static final Region CD_CHIP_OFF = new Region(72, 210, CD_CHIP, CD_CHIP);
    public static final Region CD_BADGE = new Region(108, 210, CD_BADGE_W, CD_BADGE_H);
    /** Painted white; tinted with the move's accent at draw time. */
    public static final Region CD_RAIL = new Region(108, 228, CD_RAIL_W, CD_RAIL_H);
    public static final Region CD_METER_TRACK = new Region(140, 210, CD_METER_W, CD_METER_H);
    /** Also painted white, so one sprite serves every accent colour. */
    public static final Region CD_METER_FILL = new Region(140, 218, CD_METER_W, CD_METER_H);

    // --- redesigned placement ---
    // Tighter cluster than the legacy view: smaller portrait, longer stacked bars, stamina
    // beneath, and a sparking pip column beside the portrait (which the HUD never had).
    public static final int PORTRAIT_X = 2;
    public static final int PORTRAIT_Y = 12;
    public static final int CONTENT_LEFT = PORTRAIT_X + PORTRAIT + 8;
    public static final int NAME_Y = 2;
    public static final int HP_Y = 16;
    public static final int KI_Y = HP_Y + BAR_H + 3;
    public static final int STM_Y = KI_Y + BAR_H + 4;
    public static final int STM_SEGMENTS = 16;
    public static final int STM_GAP = 2;

    /** Below this HP fraction the bar swaps to {@link #HP_CRIT} instead of only shrinking. */
    public static final float HP_CRIT_FRACTION = 0.25f;

    /** Gap between the player name and the DMZ power-release percentage beside it. */
    public static final int RELEASE_GAP = 8;
    public static final int RELEASE_COLOR = 0xFF68CAE6;

    public static final int SPARK_PIPS = 5;
    public static final int SPARK_X = PORTRAIT_X + PORTRAIT + 2;
    public static final int SPARK_Y = PORTRAIT_Y;

    /** Unscaled width of the whole cluster; {@code XenoHudConfig.scaledWidth()} scales it. */
    public static int width() {
        return CONTENT_LEFT + BAR_W + 4;
    }

    /** Unscaled height of the whole cluster. */
    public static int height() {
        return Math.max(PORTRAIT_Y + PORTRAIT, STM_Y + STM_SEG_H) + 4;
    }
}
