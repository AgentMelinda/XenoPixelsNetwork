package net.bullettrain.xenopixelsmod.client.hud;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Atlas regions for the v2 computer and seated HUD.
 *
 * <p><b>Paired by hand with {@code tools/guidance_v2_art/atlas.py}.</b> Changing one
 * without the other produces a stretched or mis-sampled v2 chrome. Regenerate with
 * {@code python tools/gen_guidance_v2_art.py} after editing either.
 *
 * <p>Does not touch {@link XenoHudLayout} or the v1 512 atlas.
 */
public final class GuidanceV2ArtLayout {

    private GuidanceV2ArtLayout() {
    }

    public static final int ATLAS = 1024;
    public static final int PANEL_CORNER = 16;

    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/guidance_v2_atlas.png");

    public record Region(int u, int v, int w, int h) {
    }

    public static final Region PANEL = new Region(0, 0, 64, 64);
    public static final Region TAB = new Region(64, 0, 96, 20);
    public static final Region TAB_HOT = new Region(64, 20, 96, 20);
    public static final Region BAR_EMPTY = new Region(0, 64, 300, 14);
    public static final Region BAR_FULL = new Region(0, 78, 300, 14);
    public static final Region BAR_FLAP = new Region(0, 92, 300, 14);
    public static final Region MODE_CHIP = new Region(320, 0, 48, 20);
    public static final Region WARN = new Region(320, 24, 80, 16);
    public static final Region EDGE = new Region(400, 0, 16, 16);

    /** Blit a whole atlas region at 1:1. */
    public static void blit(GuiGraphics g, Region region, int x, int y) {
        if (region == null) {
            return;
        }
        g.blit(TEXTURE, x, y, region.w(), region.h(),
                region.u(), region.v(), region.w(), region.h(), ATLAS, ATLAS);
    }

    /** Blit the left {@code fraction} of a region at 1:1. */
    public static void blitClipped(GuiGraphics g, Region region, int x, int y, float fraction) {
        if (region == null) {
            return;
        }
        int w = Math.round(region.w() * Math.max(0f, Math.min(1f, fraction)));
        if (w <= 0) {
            return;
        }
        g.blit(TEXTURE, x, y, w, region.h(),
                region.u(), region.v(), w, region.h(), ATLAS, ATLAS);
    }

    /** Stretch a region to {@code w}×{@code h}, optionally clipping the source width. */
    public static void blitBar(GuiGraphics g, Region region, int x, int y, int w, int h, float fraction) {
        if (region == null || w <= 0 || h <= 0) {
            return;
        }
        int dw = Math.round(w * Math.max(0f, Math.min(1f, fraction)));
        if (dw <= 0) {
            return;
        }
        int srcW = Math.max(1, Math.round(region.w() * (dw / (float) w)));
        g.blit(TEXTURE, x, y, dw, h, region.u(), region.v(), srcW, region.h(), ATLAS, ATLAS);
    }

    /** Nine-slice a region. Same inset rule as {@link HudDraw#blitNineSlice}, 1024 atlas. */
    public static void blitNineSlice(GuiGraphics g, Region region, int x, int y, int w, int h, int corner) {
        if (region == null || w <= 0 || h <= 0) {
            return;
        }
        int c = Math.max(1, Math.min(corner, Math.min(w, h) / 2));
        int su = region.u();
        int sv = region.v();
        int sw = region.w();
        int sh = region.h();
        int innerW = w - c * 2;
        int innerH = h - c * 2;
        int srcInnerW = sw - c * 2;
        int srcInnerH = sh - c * 2;
        blitRaw(g, x, y, c, c, su, sv, c, c);
        blitRaw(g, x + w - c, y, c, c, su + sw - c, sv, c, c);
        blitRaw(g, x, y + h - c, c, c, su, sv + sh - c, c, c);
        blitRaw(g, x + w - c, y + h - c, c, c, su + sw - c, sv + sh - c, c, c);
        if (innerW > 0 && srcInnerW > 0) {
            blitRaw(g, x + c, y, innerW, c, su + c, sv, srcInnerW, c);
            blitRaw(g, x + c, y + h - c, innerW, c, su + c, sv + sh - c, srcInnerW, c);
        }
        if (innerH > 0 && srcInnerH > 0) {
            blitRaw(g, x, y + c, c, innerH, su, sv + c, c, srcInnerH);
            blitRaw(g, x + w - c, y + c, c, innerH, su + sw - c, sv + c, c, srcInnerH);
        }
        if (innerW > 0 && innerH > 0 && srcInnerW > 0 && srcInnerH > 0) {
            blitRaw(g, x + c, y + c, innerW, innerH, su + c, sv + c, srcInnerW, srcInnerH);
        }
    }

    private static void blitRaw(GuiGraphics g, int x, int y, int w, int h, int u, int v, int uw, int vh) {
        g.blit(TEXTURE, x, y, w, h, u, v, uw, vh, ATLAS, ATLAS);
    }
}
