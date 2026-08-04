package net.bullettrain.xenopixelsmod.client.hud;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Fast HUD primitives. Slanted shapes are quantized into at most four pixel-art
 * bands instead of issuing one draw for every row.
 *
 * <ul>
 *   <li>skew == 0 → single rectangle fill</li>
 *   <li>skew != 0 → at most four rectangular bands</li>
 *   <li>borders use the same bounded band count</li>
 * </ul>
 */
public final class HudDraw {
    private HudDraw() {}

    public static void fillRect(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        g.fill(x, y, x + w, y + h, color);
    }

    public static void borderRect(GuiGraphics g, int x, int y, int w, int h, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y + t, x + t, y + h - t, color);
        g.fill(x + w - t, y + t, x + w, y + h - t, color);
    }

    /**
     * Filled parallelogram: top edge shifted right by {@code skew} relative to bottom.
     */
    public static void fillPara(GuiGraphics g, int x, int y, int w, int h, int skew, int color) {
        if (w <= 0 || h <= 0) return;
        int s = Math.max(0, skew);
        if (s == 0 || h <= 1) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        int bands = bandCount(h, s);
        for (int band = 0; band < bands; band++) {
            int y0 = band * h / bands;
            int y1 = (band + 1) * h / bands;
            int sampleRow = (y0 + y1 - 1) / 2;
            int offset = rowOffset(sampleRow, h, s);
            g.fill(x + offset, y + y0, x + offset + w, y + y1, color);
        }
    }

    public static void borderPara(GuiGraphics g, int x, int y, int w, int h, int skew, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        int s = Math.max(0, skew);
        if (s == 0 || h <= 1) {
            borderRect(g, x, y, w, h, color, t);
            return;
        }
        // Top + bottom full edges
        int topOff = rowOffset(0, h, s);
        int botOff = rowOffset(h - 1, h, s);
        g.fill(x + topOff, y, x + topOff + w, y + t, color);
        g.fill(x + botOff, y + h - t, x + botOff + w, y + h, color);
        // Quantized pixel-art sides. This is intentionally bounded: even an 80px
        // plate costs at most eight side fills instead of roughly 160.
        int innerH = Math.max(0, h - 2 * t);
        int bands = bandCount(innerH, s);
        for (int band = 0; band < bands; band++) {
            int row0 = t + band * innerH / bands;
            int row1 = t + (band + 1) * innerH / bands;
            if (row1 <= row0) continue;
            int offset = rowOffset((row0 + row1 - 1) / 2, h, s);
            int left = x + offset;
            int right = left + w;
            g.fill(left, y + row0, Math.min(left + t, right), y + row1, color);
            g.fill(Math.max(right - t, left), y + row0, right, y + row1, color);
        }
    }

    /** Horizontal gradient in few strips (not per-pixel). */
    public static void fillGradientH(GuiGraphics g, int x, int y, int w, int h, int skew, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        int strips = Math.min(12, Math.min(w, Math.max(4, w / 24)));
        for (int i = 0; i < strips; i++) {
            float t = strips <= 1 ? 1f : i / (float) (strips - 1);
            int color = lerpColor(c1, c2, t);
            int sx = x + Math.round(i * (w / (float) strips));
            int sw = Math.max(1, Math.round((i + 1) * (w / (float) strips)) - (sx - x));
            fillPara(g, sx, y, sw, h, skew, color);
        }
    }

    public static int rowOffset(int row, int h, int skew) {
        if (h <= 1 || skew == 0) return skew;
        return Math.round(skew * (1f - row / (float) (h - 1)));
    }

    private static int bandCount(int h, int skew) {
        if (h <= 0) return 1;
        return Math.max(1, Math.min(4, Math.min(h, skew + 1)));
    }

    public static int lerpColor(int c1, int c2, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int a1 = (c1 >>> 24) & 0xFF, r1 = (c1 >>> 16) & 0xFF, g1 = (c1 >>> 8) & 0xFF, b1 = c1 & 0xFF;
        int a2 = (c2 >>> 24) & 0xFF, r2 = (c2 >>> 16) & 0xFF, g2 = (c2 >>> 8) & 0xFF, b2 = c2 & 0xFF;
        int a = Math.round(a1 + (a2 - a1) * t);
        int r = Math.round(r1 + (r2 - r1) * t);
        int g = Math.round(g1 + (g2 - g1) * t);
        int b = Math.round(b1 + (b2 - b1) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
