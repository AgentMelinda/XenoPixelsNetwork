package net.bullettrain.xenopixelsmod.client.screen;

/** Shared geometry for DragonMineZ's themed skills list and its sliding category tabs. */
public final class DmzSkillsHitboxes {

    public static final int PANEL_WIDTH = 141;
    public static final int PANEL_HEIGHT = 213;
    public static final int ROW_LEFT = 10;
    public static final int ROW_RIGHT_THEMED = 132;

    private DmzSkillsHitboxes() {
    }

    public static int rowRightOffset(boolean themed, int original) {
        return themed ? ROW_RIGHT_THEMED : original;
    }

    public static boolean insidePanel(boolean themed, double mouseX, double mouseY,
                                      int panelX, int panelY) {
        return themed
                && mouseX >= panelX && mouseX < panelX + PANEL_WIDTH
                && mouseY >= panelY && mouseY < panelY + PANEL_HEIGHT;
    }

    public static boolean insideRowX(double mouseX, int panelX) {
        return mouseX >= panelX + ROW_LEFT && mouseX < panelX + ROW_RIGHT_THEMED;
    }
}
