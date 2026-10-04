package net.bullettrain.xenopixelsmod.ui;

/**
 * Where a Studio document is drawn inside the leftover canvas. Menus are a centered
 * TechniqueCreator-sized plate; HUD overlays map from the top-left like in-game.
 */
public final class StudioPreview {
    private StudioPreview() {
    }

    public static int[] origin(String kind, int canvasW, int canvasH, int docW, int docH) {
        if ("dmz_menu".equals(kind)) {
            int w = Math.max(1, docW);
            int h = Math.max(1, docH);
            return new int[]{Math.max(0, (canvasW - w) / 2), Math.max(0, (canvasH - h) / 2)};
        }
        return new int[]{0, 0};
    }

    public static int previewW(String kind, int canvasW, int docW) {
        return "dmz_menu".equals(kind) ? Math.max(1, docW) : Math.max(1, canvasW);
    }

    public static int previewH(String kind, int canvasH, int docH) {
        return "dmz_menu".equals(kind) ? Math.max(1, docH) : Math.max(1, canvasH);
    }
}
