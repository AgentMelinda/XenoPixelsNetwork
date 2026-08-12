package net.bullettrain.xenopixelsmod.client.hud;

import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Renders the supplied Xenoverse-style art with live player data layered into its wells. */
public final class XenoModernHudView {
    private static final ResourceLocation ART = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_hud_xv.png");
    private static final int ART_W = 1536;
    private static final int ART_H = 1024;
    private static final int CROP_Y = 100;
    private static final int CROP_H = 720;
    private static final int W = 420;
    private static final int H = 180;
    /** Visible artwork ends here; the remaining canvas is transparent glow/padding. */
    public static final int VISIBLE_BOTTOM = 166;

    /**
     * Panel-space scale of the artwork blit. <b>Non-uniform</b> — x and y differ, because a
     * 1536x720 crop is drawn into a 420x180 panel. Every lane constant below is therefore kept in
     * source-texture pixels, where it can be measured directly off {@code xeno_hud_xv.png} and
     * checked in any image editor, and converted here exactly once. Hand-tuning these in panel
     * space is what let them drift out of their slots in the first place.
     */
    private static final float SX = W / (float) ART_W;
    private static final float SY = H / (float) CROP_H;

    /**
     * A bar slot in the artwork, in source-texture pixels.
     *
     * <p>{@code x}/{@code y} are the lane's <i>bottom</i>-left corner and {@code w} its full
     * footprint out to the top-right, matching what {@link HudDraw#fillPara} expects: it shifts the
     * top row right by {@code skew} and leaves the bottom row at {@code x}. {@code slant} is how
     * far the top edge sits right of the bottom edge in the art.
     *
     * <p>Produced by {@code scripts/measure_hud_lanes.py}.
     */
    private record Lane(int texX, int texY, int texW, int texH, int texSlant) {
        int x() { return Math.round(texX * SX); }
        int y() { return Math.round((texY - CROP_Y) * SY); }
        int w() { return Math.round(texW * SX); }
        int h() { return Math.round(texH * SY); }
        int skew() { return Math.round(texSlant * SX); }
    }

    private static final Lane RELEASE = new Lane(522, 409, 846, 39, 30);
    private static final Lane HP = new Lane(629, 474, 765, 35, 26);
    private static final Lane KI = new Lane(619, 532, 690, 41, 31);
    private static final Lane STM = new Lane(582, 598, 663, 35, 26);

    /** The dark circular well the portrait sits in. Measured the same way as the lanes. */
    private static final int WELL_TEX_CX = 298;
    private static final int WELL_TEX_CY = 462;
    private static final int WELL_TEX_R = 149;

    /** Source region {@code make_portrait_mask.py} cut the mask from. */
    private static final ResourceLocation PORTRAIT_MASK = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_portrait_mask.png");
    private static final int MASK_TEX_X = 98;
    private static final int MASK_TEX_Y = 262;
    private static final int MASK_TEX_SIZE = 400;

    private static final int NAME_X = 151;
    private static final int NAME_Y = 56;
    /** Clear of the release lane, whose top the artwork puts at panel y 77. */
    private static final int FORM_Y = 65;
    private static final int NAME_W = 177;
    private static final int LEVEL_RIGHT = 337;

    private XenoHudSnapshot snapshot;
    private int boundsX;
    private int boundsY;
    private float scale = 1f;
    private boolean editorMode;
    private float displayedHp = 1f;
    private float displayedKi = 1f;
    private float displayedStm = 1f;
    private long lastNanos;
    private boolean firstFrame = true;

    public void setSnapshot(XenoHudSnapshot snapshot) { this.snapshot = snapshot; }

    public void setBounds(int x, int y, float scale) {
        boundsX = x;
        boundsY = y;
        this.scale = scale <= 0f ? 1f : scale;
    }

    public void setEditorMode(boolean editorMode) { this.editorMode = editorMode; }

    public void render(GuiGraphics graphics) {
        if (snapshot == null) return;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(boundsX, boundsY, 0);
        pose.scale(scale, scale, 1f);
        renderContent(graphics);
        pose.popPose();
    }

    /** Draws at the current pose origin so the unified HUD can append combat chips below it. */
    public void renderContent(GuiGraphics graphics) {
        XenoHudSnapshot snap = snapshot;
        if (snap == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ease(snap);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ART, 0, 0, W, H, 0f, CROP_Y, ART_W, CROP_H, ART_W, ART_H);

        // The source artwork supplies the chrome. These layers replace its demonstration data.
        drawPortrait(graphics, mc);

        drawContainedBar(graphics, RELEASE, snap.releasePercent,
                0xFFFFC107, 0xFF271B08, 0xFFFFD65A);
        drawContainedBar(graphics, HP, displayedHp,
                displayedHp < 0.25f ? 0xFFFF3B30 : 0xFFFFA000,
                0xFF180A09, 0xFFFFC247);
        drawContainedSegments(graphics, KI, displayedKi,
                0xFF19B9FF, 0xFF071929, 0xFF55D8FF, 8);
        drawContainedSegments(graphics, STM, displayedStm,
                0xFF18E0D2, 0xFF071E24, 0xFF62FFF0, 8);

        String name = snap.name == null ? "" : font.plainSubstrByWidth(snap.name, NAME_W);
        // Cover the baked "P1" badge before placing live level data. Slanted to match the badge —
        // an axis-aligned fill left a visible square notch in the chrome.
        HudDraw.fillPara(graphics, 348, 56, 38, 20, 5, 0xFF080A11);
        graphics.drawString(font, name, NAME_X, NAME_Y, 0xFFFFFFFF, true);
        String level = "Lv. " + Math.max(0, snap.level);
        graphics.drawString(font, level, LEVEL_RIGHT - font.width(level), NAME_Y, 0xFFFFD54F, true);
        if (snap.releaseText != null) {
            graphics.drawString(font, snap.releaseText,
                    RELEASE.x() + RELEASE.w() - 10 - font.width(snap.releaseText), RELEASE.y() + 1,
                    0xFFFFFFFF, true);
        }
        if (!snap.activeForm.isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(snap.activeForm, NAME_W),
                    NAME_X, FORM_Y, 0xFF80D8FF, true);
        }
        String sparking = snap.sparkingActive ? "SPARKING" : snap.sparking >= 99f ? "READY" : "";
        if (!sparking.isEmpty()) graphics.drawString(font, sparking, 292, 142, 0xFFFFC107, true);

        if (snap.transforming) {
            if (XenoHudConfig.transformRing) {
                // Trace the well rather than boxing it. The well is an ellipse on screen because
                // the artwork blit is scaled differently in x and y.
                HudDraw.transformChargeArc(graphics, wellCx(), wellCy(),
                        wellRx() + 3, wellRy() + 3, 3, snap.transformChargePercent);
            } else {
                HudDraw.transformChargeBorder(graphics, 39, 46, 86, 86, snap.transformChargePercent);
            }
        }
        if (displayedHp < 0.25f) {
            int alpha = 90 + (int) (60 * (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 130.0)));
            // Tracks the HP lane rather than a separate hardcoded rect that could drift from it.
            HudDraw.borderPara(graphics, HP.x() - 2, HP.y() - 2, HP.w() + 4, HP.h() + 4,
                    HP.skew(), (alpha << 24) | 0x00FF3028, 1);
        }
        if (editorMode) {
            graphics.renderOutline(0, 0, W, H, 0xFF42A5F5);
            // Lane outlines, so the computed slots can be compared against the artwork underneath
            // in one glance instead of by trial and error.
            outlineLane(graphics, RELEASE, 0xFFFFC107);
            outlineLane(graphics, HP, 0xFFFF6E40);
            outlineLane(graphics, KI, 0xFF40C4FF);
            outlineLane(graphics, STM, 0xFF1DE9B6);
            graphics.renderOutline(wellCx() - wellRx(), wellCy() - wellRy(),
                    wellRx() * 2, wellRy() * 2, 0xFFE040FB);
        }
        RenderSystem.disableBlend();
    }

    private static void outlineLane(GuiGraphics g, Lane lane, int color) {
        HudDraw.borderPara(g, lane.x(), lane.y(), lane.w() - lane.skew(), lane.h(),
                lane.skew(), color, 1);
    }

    private static int wellCx() { return Math.round(WELL_TEX_CX * SX); }
    private static int wellCy() { return Math.round((WELL_TEX_CY - CROP_Y) * SY); }
    private static int wellRx() { return Math.round(WELL_TEX_R * SX); }
    private static int wellRy() { return Math.round(WELL_TEX_R * SY); }

    /**
     * The portrait: player skin by default, or the live DragonMineZ character.
     *
     * <p>Drawn first and then clipped by {@link #PORTRAIT_MASK}, which was cut from the artwork
     * itself, so the chrome around the circle is the original art rather than an approximation of
     * it. With the mask in play the portrait can fill the whole well instead of being a square
     * inscribed in it.
     */
    private void drawPortrait(GuiGraphics graphics, Minecraft mc) {
        if (!(mc.player instanceof AbstractClientPlayer player)) return;
        boolean masked = XenoHudConfig.portraitMask;

        int x = wellCx() - wellRx();
        int y = wellCy() - wellRy();
        int w = wellRx() * 2;
        int h = wellRy() * 2;
        if (!masked) {
            // Unmasked, the square has to stay inside the ellipse or its corners cover the ring.
            int half = (int) Math.floor(1.0 / Math.sqrt(
                    1.0 / (wellRx() * (double) wellRx()) + 1.0 / (wellRy() * (double) wellRy())));
            x = wellCx() - half;
            y = wellCy() - half;
            w = h = half * 2;
        }

        if (XenoHudConfig.portraitMode == XenoHudConfig.PortraitMode.CHARACTER) {
            drawCharacter(graphics, player, x, y, w, h);
        } else {
            ResourceLocation skin = player.getSkin().texture();
            // Head layer then hat layer, from the 64x64 skin's 8x8 face cells.
            graphics.blit(skin, x, y, w, h, 8f, 8f, 8, 8, 64, 64);
            graphics.blit(skin, x, y, w, h, 40f, 8f, 8, 8, 64, 64);
        }

        if (masked) {
            graphics.blit(PORTRAIT_MASK,
                    Math.round(MASK_TEX_X * SX), Math.round((MASK_TEX_Y - CROP_Y) * SY),
                    Math.round(MASK_TEX_SIZE * SX), Math.round(MASK_TEX_SIZE * SY),
                    0f, 0f, MASK_TEX_SIZE, MASK_TEX_SIZE, MASK_TEX_SIZE, MASK_TEX_SIZE);
        }
    }

    /**
     * The DragonMineZ character in the well.
     *
     * <p>Renders the live player entity rather than sourcing a portrait asset. DMZ swaps the player
     * renderer for {@code DMZPlayerRenderer} (a GeckoLib {@code GeoEntityRenderer}) with
     * {@code DMZHairLayer} on top, so an ordinary entity render already carries the race model,
     * hair and active form — that <i>is</i> the character, and there is nothing else to draw.
     *
     * <p>Goes through {@code EntityPreviewRenderContext} rather than calling
     * {@code InventoryScreen} directly. DMZ has first-person rules that hide the head bone and
     * suppress the hair layer, and that context is how it tells them apart from a real
     * through-the-eyes view. Its own menus get this for free because a menu means a screen is open
     * and the rules never fire; a HUD portrait draws with no screen open, so it has to say so
     * explicitly.
     */
    private void drawCharacter(GuiGraphics graphics, AbstractClientPlayer player,
                               int x, int y, int w, int h) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(0f);
        pose.mul(camera);

        // Face the viewer. These are live fields on an entity the world renderer is also drawing,
        // so they must go back exactly as they were — hence the finally.
        float bodyRot = player.yBodyRot;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float headRotO = player.yHeadRotO;
        float headRot = player.yHeadRot;
        player.yBodyRot = 180f;
        player.setYRot(180f);
        player.setXRot(0f);
        player.yHeadRot = 180f;
        player.yHeadRotO = 180f;

        try {
            // Dividing by getScale() is what keeps a Namekian or a transformed form the same
            // apparent size in the well as a base human — DMZ scales the player entity per race.
            float entityScale = player.getScale();
            if (entityScale <= 0f) entityScale = 1f;
            Vector3f translate = new Vector3f(0f,
                    player.getBbHeight() / 2f + XenoHudConfig.portraitOffset * entityScale, 0f);

            // enableScissor takes raw GUI coordinates and ignores the pose stack, but this is drawn
            // inside the panel's translate+scale — so map the rect through the live matrix first.
            // Without this the clip lands somewhere else entirely on screen. This is also why the
            // vanilla renderEntityInInventoryFollowsAngle wrapper cannot be used directly: its own
            // internal scissor has exactly that bug from in here.
            Matrix4f matrix = graphics.pose().last().pose();
            Vector3f topLeft = matrix.transformPosition(new Vector3f(x, y, 0f));
            Vector3f bottomRight = matrix.transformPosition(new Vector3f(x + w, y + h, 0f));
            graphics.enableScissor(
                    (int) Math.floor(topLeft.x), (int) Math.floor(topLeft.y),
                    (int) Math.ceil(bottomRight.x), (int) Math.ceil(bottomRight.y));
            try {
                // DMZ's own preview entry point, the one all seven of its menus use. It marks the
                // render so DMZ's first-person-only rules (hidden head bone, suppressed hair layer)
                // stand down — those exist for a camera sitting inside the model, not a portrait.
                EntityPreviewRenderContext.renderEntityInInventory(graphics,
                        Math.round(cx), Math.round(cy),
                        Math.round(XenoHudConfig.portraitScale / entityScale),
                        translate, pose, camera, player);
            } finally {
                graphics.disableScissor();
                // The panel still draws bars and text after this and expects blend on; the entity
                // render path makes no promise about leaving it that way.
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
            }
        } finally {
            player.yBodyRot = bodyRot;
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yHeadRotO = headRotO;
            player.yHeadRot = headRot;
        }
    }


    private static void drawContainedBar(GuiGraphics g, Lane lane, float fraction,
                                         int fill, int empty, int outline) {
        drawContainedBar(g, lane.x(), lane.y(), lane.w(), lane.h(), lane.skew(),
                fraction, fill, empty, outline);
    }

    private static void drawContainedSegments(GuiGraphics g, Lane lane, float fraction,
                                              int fill, int empty, int outline, int count) {
        drawContainedSegments(g, lane.x(), lane.y(), lane.w(), lane.h(), lane.skew(),
                fraction, fill, empty, outline, count);
    }

    /** Paints inside a slanted lane and redraws its rim last, so no fill can cover the outline. */
    private static void drawContainedBar(GuiGraphics g, int x, int y, int w, int h, int skew,
                                         float fraction, int fill, int empty, int outline) {
        fraction = clamp(fraction);
        int rowW = Math.max(1, w - skew);
        HudDraw.fillPara(g, x, y, rowW, h, skew, empty);
        int filled = Math.round(rowW * fraction);
        if (filled > 0) {
            int fillSkew = Math.min(skew, filled);
            HudDraw.fillPara(g, x, y, filled, h, fillSkew, fill);
            HudDraw.fillPara(g, x, y, filled, Math.max(2, h / 4), fillSkew, 0x77FFFFFF);
        }
        HudDraw.borderPara(g, x, y, rowW, h, skew, outline, 1);
    }

    /** Segmented lane with a partially filled leading segment for smooth KI/STM changes. */
    private static void drawContainedSegments(GuiGraphics g, int x, int y, int w, int h, int skew,
                                              float fraction, int fill, int empty, int outline,
                                              int count) {
        fraction = clamp(fraction);
        int gap = 2;
        int segmentSkew = 2;
        int usable = Math.max(count, w - skew - gap * (count - 1));
        int segmentW = Math.max(1, usable / count);
        float units = fraction * count;
        for (int i = 0; i < count; i++) {
            int sx = x + i * (segmentW + gap);
            HudDraw.fillPara(g, sx, y, segmentW, h, segmentSkew, empty);
            float part = clamp(units - i);
            int filled = Math.round(Math.max(0, segmentW - segmentSkew) * part);
            if (filled > 0) {
                int fillSkew = Math.min(segmentSkew, filled);
                HudDraw.fillPara(g, sx, y, filled, h, fillSkew, fill);
                HudDraw.fillPara(g, sx, y, filled, 2, fillSkew, 0x66FFFFFF);
            }
        }
        HudDraw.borderPara(g, x, y, Math.max(1, w - skew), h, skew, outline, 1);
    }

    private void ease(XenoHudSnapshot snap) {
        long now = System.nanoTime();
        float dt = firstFrame ? 1f : (float) ((now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        firstFrame = false;
        float k = Math.min(1f, dt * 8f);
        displayedHp += (safeFraction(snap.curHp, snap.maxHp) - displayedHp) * k;
        displayedKi += (safeFraction(snap.curKi, snap.maxKi) - displayedKi) * k;
        displayedStm += (safeFraction(snap.curStm, snap.maxStm) - displayedStm) * k;
    }

    private static float safeFraction(double current, double max) {
        if (max <= 0 || !Double.isFinite(max) || !Double.isFinite(current)) return 0f;
        return clamp((float) (current / max));
    }

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
}
