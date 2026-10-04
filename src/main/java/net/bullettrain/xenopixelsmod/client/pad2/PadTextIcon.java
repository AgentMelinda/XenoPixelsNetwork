package net.bullettrain.xenopixelsmod.client.pad2;

import dev.isxander.controlify.api.bind.RadialIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A two- or three-letter radial icon in a colour.
 *
 * <p>Most of this mod's radial entries have no artwork: they are actions like "cycle target" or
 * "cancel ki blast" that were never items or effects. A short label in the area's colour reads
 * faster on a ring of twenty than twenty variations of the same generic glyph would.
 *
 * <p>A copy rather than a reuse of {@code client.pad.PadModeIcon}, which is package-private. The
 * drawing is four lines and duplicating it costs less than widening another package's API for it.
 */
public record PadTextIcon(String label, int color) implements RadialIcon {

    @Override
    public void draw(GuiGraphics graphics, int x, int y, float delta) {
        Minecraft minecraft = Minecraft.getInstance();
        int width = minecraft.font.width(label);
        // x and y are the icon cell's top-left in a 16x16 box; centre the glyphs inside it.
        graphics.drawString(minecraft.font, label,
                x + (16 - width) / 2, y + (16 - minecraft.font.lineHeight) / 2 + 1,
                color, true);
    }
}
