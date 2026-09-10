package net.bullettrain.xenopixelsmod.client.effect;

import java.util.ArrayList;
import java.util.List;

/** Pure layout for the vertical Xeno effect rail beside the player inventory. */
public final class XenoEffectRailLayout {
    public static final int CELL_SIZE = 24;
    public static final int CELL_GAP = 3;
    private static final int SCREEN_MARGIN = 4;

    private XenoEffectRailLayout() {}

    public static List<Cell> layout(int guiRight, int guiTop, int screenWidth, int screenHeight, int count) {
        if (count <= 0) return List.of();

        int startY = Math.max(SCREEN_MARGIN, Math.min(guiTop, screenHeight - SCREEN_MARGIN - CELL_SIZE));
        int availableHeight = Math.max(CELL_SIZE, screenHeight - SCREEN_MARGIN - startY);
        int stride = CELL_SIZE + CELL_GAP;
        int rows = Math.max(1, (availableHeight + CELL_GAP) / stride);
        int columns = (count + rows - 1) / rows;
        int railWidth = columns * CELL_SIZE + Math.max(0, columns - 1) * CELL_GAP;
        int preferredX = guiRight + SCREEN_MARGIN;
        int startX = Math.min(preferredX, Math.max(SCREEN_MARGIN, screenWidth - SCREEN_MARGIN - railWidth));

        List<Cell> cells = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            cells.add(new Cell(startX + index / rows * stride,
                    startY + index % rows * stride));
        }
        return List.copyOf(cells);
    }

    public record Cell(int x, int y) {}
}
