package net.bullettrain.xenopixelsmod.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Technique hotbar slot chrome — vanilla {@link GuiGraphics} only.
 * (Previously LDLib {@code ResourceTexture}; that hard-crashed when LDLib was not installed.)
 */
public final class TechniqueSlotWidget {
    /** Solid tinted background rectangle. */
    public void drawBackground(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + h, color);
    }

    /** Thin tinted border made of four solid strips. */
    public void drawOutline(GuiGraphics g, int x, int y, int w, int h, int color, int thickness) {
        int t = Math.max(1, thickness);
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    public void drawBadgeBackground(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xEE000000);
    }
}
