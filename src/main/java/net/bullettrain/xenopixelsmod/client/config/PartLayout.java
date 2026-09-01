package net.bullettrain.xenopixelsmod.client.config;

import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * One editable HUD surface, seen through the only shape its editor cares about.
 *
 * <p>The main panel, the combat chips and the ki menu each keep their own config with its own file,
 * its own extra settings and its own part list, but the per-element story is identical across all
 * three: every piece has an offset, a size, a colour, a weight and a font, and can be reset alone or
 * together. This interface is that common story, so a single editor screen can drive all three
 * instead of three near-identical screens drifting apart — which is exactly how the font control
 * ended up existing on one surface and not the other two.
 *
 * <p>The accessors hand back the <em>live</em> arrays rather than copies. That is deliberate: the
 * editor previews the real HUD by mutating the real config, so an edit has to be visible the same
 * frame. The screen is responsible for snapshotting on entry and restoring on cancel.
 */
@OnlyIn(Dist.CLIENT)
public interface PartLayout {
    /** Tab label. */
    String name();

    int partCount();

    String partName(int part);

    int[] x();

    int[] y();

    float[] scale();

    int[] color();

    boolean[] bold();

    String[] font();

    int defaultColor(int part);

    /** Restore one element's position, size, weight, colour and font. */
    void resetPart(int part);

    /** Restore every element on this surface. */
    void resetAll();

    /**
     * Whether this surface's per-element overrides are in effect.
     *
     * <p>Editing a layout that is switched off looks like a broken editor, so the screen turns this
     * on for whichever surface is being edited and restores it on cancel.
     */
    boolean customLayout();

    void setCustomLayout(boolean on);

    /** Bound an offset so a stray drag cannot fling an element off the surface entirely. */
    int clampOffset(int value);

    void save();

    /** Draw this surface as it really renders, so the editor previews the true result. */
    void drawPreview(GuiGraphics g, int screenWidth, int screenHeight);

    /** Last drawn {@code [x, y, w, h]}, for the drag hit-test and the highlight outline. */
    int[] bounds();
}
