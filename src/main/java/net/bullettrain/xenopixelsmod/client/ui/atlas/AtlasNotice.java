package net.bullettrain.xenopixelsmod.client.ui.atlas;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A status strip drawn with {@code mynpcs_toast} at its native 160x32.
 *
 * <p>The strip does not widen to fit longer text; the text is trimmed to the strip instead, so the
 * toast keeps the border proportions it was generated with.
 */
public record AtlasNotice(String text, int x, int y) {
    private static final String SPRITE = "mynpcs_toast";

    public static int width() {
        return XenoAtlasSprites.get(SPRITE).width();
    }

    public static int height() {
        return XenoAtlasSprites.get(SPRITE).height();
    }

    /** The editor's slim strip: same toast art, 124x18, so it fits between the last row and the footer. */
    public static final int COMPACT_W = 124;
    public static final int COMPACT_H = 18;

    public void renderCompact(GuiGraphics graphics, Font font) {
        XenoAtlasSprites.blitSized(graphics, SPRITE, x, y, COMPACT_W, COMPACT_H);
        int inner = COMPACT_W - 10;
        String shown = font.width(text) <= inner ? text : font.plainSubstrByWidth(text, inner);
        graphics.drawString(font, shown, x + 5, y + (COMPACT_H - 8) / 2, 0xFFE0F0FF, false);
    }

    public void render(GuiGraphics graphics, Font font) {
        XenoAtlasSprites.blit(graphics, SPRITE, x, y);
        int inner = width() - 16;
        String shown = font.width(text) <= inner ? text : font.plainSubstrByWidth(text, inner);
        graphics.drawString(font, shown, x + 8, y + (height() - 8) / 2, 0xFFE0F0FF, false);
    }
}
