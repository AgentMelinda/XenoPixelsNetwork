package net.bullettrain.xenopixelsmod.client.hud;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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

    /** Blit a whole atlas region at 1:1. */
    public static void blitRegion(GuiGraphics g, ResourceLocation atlas,
                                  XenoHudLayout.Region region, int x, int y) {
        if (region == null) return;
        g.blit(atlas, x, y, region.w(), region.h(),
                region.u(), region.v(), region.w(), region.h(),
                XenoHudLayout.ATLAS, XenoHudLayout.ATLAS);
    }

    /**
     * Blit the left {@code fraction} of a region.
     *
     * <p>Bars are filled by clipping the full-bar texture rather than by tinting a 1×1 white
     * pixel — that is what preserves the gradient, gloss band and lit leading tip the
     * generator bakes in. Stretching or tinting would flatten all three.
     */
    public static void blitClipped(GuiGraphics g, ResourceLocation atlas,
                                   XenoHudLayout.Region region, int x, int y, float fraction) {
        if (region == null) return;
        int w = Math.round(region.w() * Math.max(0f, Math.min(1f, fraction)));
        if (w <= 0) return;
        g.blit(atlas, x, y, w, region.h(),
                region.u(), region.v(), w, region.h(),
                XenoHudLayout.ATLAS, XenoHudLayout.ATLAS);
    }

    /**
     * Blit a region at 1:1 with an ARGB tint applied.
     *
     * <p>Used for the shared leading-edge bar cap, which is authored uncoloured so one sprite
     * can serve HP, KI and the critical variant rather than needing a baked cap per bar.
     */
    public static void blitTinted(GuiGraphics g, ResourceLocation atlas,
                                  XenoHudLayout.Region region, int x, int y, int argb) {
        if (region == null) return;
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        blitRegion(g, atlas, region, x, y);
        g.setColor(1f, 1f, 1f, 1f);
    }

    /**
     * Nine-slice a region across an arbitrary rectangle.
     *
     * <p>The backing plate is authored with its whole bevel inside a {@code corner}-wide band,
     * which only pays off if the corners are drawn unscaled and just the edges and centre are
     * stretched. Blitting the plate whole — which this replaced — smeared the bevel across the
     * cluster and, because the source region is square while the cluster is wide and short,
     * also sampled well past the painted area.
     */
    public static void blitNineSlice(GuiGraphics g, ResourceLocation atlas,
                                     XenoHudLayout.Region region, int x, int y, int w, int h,
                                     int corner) {
        if (region == null || w <= 0 || h <= 0) return;
        // A rectangle smaller than two corners has no room for a centre; shrink the inset so
        // opposite corners cannot overlap and double-darken.
        int c = Math.max(1, Math.min(corner, Math.min(w, h) / 2));
        int su = region.u();
        int sv = region.v();
        int sw = region.w();
        int sh = region.h();
        int innerW = w - c * 2;
        int innerH = h - c * 2;
        int srcInnerW = sw - c * 2;
        int srcInnerH = sh - c * 2;

        // Corners, unscaled.
        blit(g, atlas, x, y, c, c, su, sv, c, c);
        blit(g, atlas, x + w - c, y, c, c, su + sw - c, sv, c, c);
        blit(g, atlas, x, y + h - c, c, c, su, sv + sh - c, c, c);
        blit(g, atlas, x + w - c, y + h - c, c, c, su + sw - c, sv + sh - c, c, c);

        // Edges, stretched along one axis only.
        if (innerW > 0 && srcInnerW > 0) {
            blit(g, atlas, x + c, y, innerW, c, su + c, sv, srcInnerW, c);
            blit(g, atlas, x + c, y + h - c, innerW, c, su + c, sv + sh - c, srcInnerW, c);
        }
        if (innerH > 0 && srcInnerH > 0) {
            blit(g, atlas, x, y + c, c, innerH, su, sv + c, c, srcInnerH);
            blit(g, atlas, x + w - c, y + c, c, innerH, su + sw - c, sv + c, c, srcInnerH);
        }
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            blit(g, atlas, x + c, y + c, innerW, innerH, su + c, sv + c, srcInnerW, srcInnerH);
        }
    }

    /**
     * Stretch a region to an arbitrary rectangle, optionally tinted.
     *
     * <p>Used for the cooldown rail and meters, which are authored white so a single sprite can
     * carry every per-move accent colour. Scaling rather than clipping is correct for the meter
     * fill: its bright leading edge is baked at the right end, so scaling keeps that edge on the
     * fill boundary where it belongs.
     */
    public static void blitScaledTinted(GuiGraphics g, ResourceLocation atlas,
                                        XenoHudLayout.Region region, int x, int y, int w, int h,
                                        int argb) {
        if (region == null || w <= 0 || h <= 0) return;
        g.setColor(((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f, ((argb >>> 24) & 0xFF) / 255f);
        blit(g, atlas, x, y, w, h, region.u(), region.v(), region.w(), region.h());
        g.setColor(1f, 1f, 1f, 1f);
    }

    /** Thickness of the transform charge border, in unscaled pixels. */
    private static final int CHARGE_BORDER_T = 4;
    private static final int CHARGE_TRACK = 0xAA3A0A12;
    private static final int CHARGE_CORE = 0xFFFF1744;
    private static final int CHARGE_HOT = 0xFFFF8A80;

    /**
     * Red border that fills clockwise from the top-left with transform charge (hold G).
     *
     * <p>Dim track, bright core with a hotter inner line, no percentage text — the progression is
     * the whole message, and a number beside a bar the player is watching mid-transform is noise.
     *
     * <p>Lives here rather than in one renderer because all three draw it: this was written for
     * the legacy overlay, and the modern and unified views were showing no transform progress at
     * all. The fill is a perimeter walk — top edge, right, bottom, left — so the four bands are
     * four fills regardless of charge, rather than one fill per row.
     */
    public static void transformChargeBorder(GuiGraphics g, int x, int y, int w, int h,
                                             float percent) {
        percent = Math.max(0f, Math.min(1f, percent));
        if (percent <= 0f) return;

        int t = CHARGE_BORDER_T;
        borderRect(g, x, y, w, h, CHARGE_TRACK, t);

        int top = w;
        int right = h - t;
        int bottom = w - t;
        int left = h - 2 * t;
        int perimeter = Math.max(1, top + right + bottom + left);
        int rem = Math.max(1, Math.round(perimeter * percent));

        int take = Math.min(rem, top);
        if (take > 0) {
            g.fill(x, y, x + take, y + t, CHARGE_CORE);
            g.fill(x, y + 1, x + take, y + 2, CHARGE_HOT);
            rem -= take;
        }
        take = Math.min(rem, right);
        if (take > 0) {
            g.fill(x + w - t, y + t, x + w, y + t + take, CHARGE_CORE);
            g.fill(x + w - 2, y + t, x + w - 1, y + t + take, CHARGE_HOT);
            rem -= take;
        }
        take = Math.min(rem, bottom);
        if (take > 0) {
            g.fill(x + w - take, y + h - t, x + w, y + h, CHARGE_CORE);
            g.fill(x + w - take, y + h - 2, x + w, y + h - 1, CHARGE_HOT);
            rem -= take;
        }
        take = Math.min(rem, left);
        if (take > 0) {
            g.fill(x, y + h - t - take, x + t, y + h - t, CHARGE_CORE);
            g.fill(x + 1, y + h - t - take, x + 2, y + h - t, CHARGE_HOT);
        }
    }

    private static void blit(GuiGraphics g, ResourceLocation atlas, int x, int y, int w, int h,
                             int u, int v, int uw, int vh) {
        g.blit(atlas, x, y, w, h, u, v, uw, vh, XenoHudLayout.ATLAS, XenoHudLayout.ATLAS);
    }

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
