package net.bullettrain.xenopixelsmod.client.npc.script;

/**
 * Geometry of the script screen: CustomNPCs' {@code GuiScriptInterface} arrangement (code area
 * left, 121-wide button column right; console left, settings column right) fitted inside our frame.
 *
 * <p>The reference sizes its frame to 88% of the screen width at a 0.56 aspect and places its
 * right column 104 px from the edge with 121 px buttons, which only works because its background
 * texture is drawn wider than {@code imageWidth}. Our frame is the whole panel, so the columns are
 * measured from the frame's inner edge instead; the first version copied the reference's numbers
 * and its buttons ran off the frame. The atlas is never stretched, so the frame exists in a ladder
 * of generated widths and {@link #fit} picks the largest that fits; every inner panel is generated
 * at exactly the size computed here ({@code tools/atlas-panels/xeno_extra_specs.py script_layout}),
 * and {@code XenoScriptLayoutTest} keeps the two copies equal.
 *
 * <p>Pure arithmetic, no Minecraft types.
 */
public final class XenoScriptLayout {
    /** Generated frame widths, ascending. */
    public static final int[] FRAME_WIDTHS = {320, 400, 480, 560};
    /** Inner margin inside the frame's 6 px border. */
    public static final int MARGIN = 8;
    /** The script tab's button column: the reference's 121 px buttons. */
    public static final int SIDE_W = 121;
    /** The settings column: two 80 px buttons and a 1 px gap. */
    public static final int SETTINGS_W = 161;
    public static final int GAP = 6;

    public final int w;
    public final int h;
    public final int codeW;
    public final int codeH;
    public final int consoleW;
    public final int consoleH;
    public final int sideW;
    public final int filesH;
    public final int hooksH;
    public final int constsH;

    private XenoScriptLayout(int w) {
        this.w = w;
        this.h = (int) (w * 0.56);
        this.codeW = w - 2 * MARGIN - SIDE_W - GAP;
        this.codeH = h - 2 * MARGIN;
        this.consoleW = w - 2 * MARGIN - SETTINGS_W - GAP;
        this.consoleH = h - 2 * MARGIN;
        this.sideW = SIDE_W;
        this.filesH = h - MARGIN - FILES_TOP;
        int lists = h - 2 * MARGIN - 22 - 4;
        this.hooksH = (int) (lists * 0.62);
        this.constsH = lists - hooksH - 4;
    }

    /** Where the loaded-scripts list starts, below the four button rows. */
    public static final int FILES_TOP = 96;

    public static XenoScriptLayout of(int frameWidth) {
        return new XenoScriptLayout(frameWidth);
    }

    /**
     * The largest generated frame within 88% of the width and 95% of the height (the reference's
     * two limits), leaving room above for the tab row. Falls back to the smallest.
     */
    public static XenoScriptLayout fit(int screenW, int screenH) {
        int chosen = FRAME_WIDTHS[0];
        for (int w : FRAME_WIDTHS) {
            int h = (int) (w * 0.56);
            if (w <= screenW * 0.88 && h + 24 <= screenH * 0.95) {
                chosen = w;
            }
        }
        return new XenoScriptLayout(chosen);
    }

    /** X of the script tab's button column, relative to the frame. */
    public int sideX() {
        return w - MARGIN - SIDE_W;
    }

    /** X of the settings column, relative to the frame. */
    public int settingsX() {
        return w - MARGIN - SETTINGS_W;
    }

    public String frame() {
        return "xeno_script_frame_w" + w;
    }

    public String code() {
        return "xeno_script_code_w" + codeW + "_h" + codeH;
    }

    public String console() {
        return "xeno_script_console_w" + consoleW + "_h" + consoleH;
    }

    public String files() {
        return "xeno_script_files_w" + sideW + "_h" + filesH;
    }

    public String hooks() {
        return "xeno_script_hooks_w" + sideW + "_h" + hooksH;
    }

    public String consts() {
        return "xeno_script_consts_w" + sideW + "_h" + constsH;
    }

    /** Button sprite at one of the reference's sizes (50, 55, 60, 80, 121, 150 wide; 20 high). */
    public static String button(int width) {
        return "xeno_btn_w" + width + "_h20";
    }

    public static final int[] BUTTON_WIDTHS = {50, 55, 60, 80, 121, 150};
}
