package net.bullettrain.xenopixelsmod.client.maker;

import net.bullettrain.xenopixelsmod.hair.HairStrandModel;

/**
 * Viewport drag tools for the Xeno Hair Studio (design-matched to DMZ 2.2 GROW/ROTATE/CURVE
 * idea; original code on 2.1.3 cube {@link HairStrandModel} fields).
 */
public enum HairViewportTool {
    /** Drag vertically to change cube length. */
    GROW,
    /** Drag to change rotation X/Y. */
    ROTATE,
    /** Drag to change curve X/Z. */
    CURVE;

    public String label() {
        return switch (this) {
            case GROW -> "Grow";
            case ROTATE -> "Rotate";
            case CURVE -> "Curve";
        };
    }

    /**
     * Apply a UI-pixel drag delta to the selected strand.
     *
     * @param dx horizontal drag in UI pixels
     * @param dy vertical drag in UI pixels (screen Y down)
     */
    public void applyDrag(HairStrandModel strand, double dx, double dy) {
        if (strand == null) {
            return;
        }
        switch (this) {
            case GROW -> {
                // Drag up grows; down shrinks.
                int delta = (int) Math.round(-dy / 8.0);
                if (delta != 0) {
                    strand.length(Math.max(0, Math.min(50, strand.length() + delta)));
                }
            }
            case ROTATE -> {
                strand.rotationY(strand.rotationY() + (float) dx * 0.45f);
                strand.rotationX(strand.rotationX() + (float) (-dy) * 0.45f);
            }
            case CURVE -> {
                strand.curveX(strand.curveX() + (float) dx * 0.05f);
                strand.curveZ(strand.curveZ() + (float) (-dy) * 0.05f);
            }
        }
    }

    public HairViewportTool next() {
        HairViewportTool[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
