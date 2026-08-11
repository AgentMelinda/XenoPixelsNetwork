package net.bullettrain.xenopixelsmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * Alternate HUD view (no LDLib dependency). Uses vanilla {@link GuiGraphics}
 * for parallelogram bars / portrait plate so the mod boots without LDLib installed.
 */
public final class XenoHudView {

    private static final int PORTRAIT = 68;
    private static final int CONTENT_LEFT = 80;
    private static final int BAR_W = 300;
    private static final int HP_H = 9;
    private static final int KI_H = 12;
    private static final int NAME_Y = 6;
    private static final int HP_Y = 24;
    private static final int KI_Y = 38;
    private static final int STM_Y = 56;
    private static final int STM_SEGMENTS = 16;
    private static final int STM_SEG_W = 14;
    private static final int STM_SEG_H = 12;

    /** Bar skew (px). Lower = fewer draw calls. 4 still reads as a gauge without killing FPS. */
    private static final int BAR_SKEW = 4;
    private static final int STM_SKEW = 0;

    // XV2-authentic bar fill colors: HP = red, KI = blue/cyan, STM = gold (kept visually distinct from KI).
    private static final int HP_EMPTY = 0xFF3A0808;
    private static final int KI_EMPTY = 0xFF0A2038;
    private static final int KI_FILLED = 0xFF29B6F6;

    private static final int HP_NORMAL = 0xFFE53935;
    private static final int HP_CRITICAL_FLASH = 0xFFFF8A65;
    private static final int STM_LIT_BASE = 0xFFFFC107;
    private static final int STM_LIT_SHIMMER = 0xFFFFE082;

    /** Tech-HUD plate palette (matches technique hotbar navy + cyan). */
    private static final int PANEL_BG = 0xEE0A1428;
    private static final int PANEL_BORDER = 0xFF42A5F5;
    private static final int PANEL_BORDER_DIM = 0xCC050510;
    private static final int PORTRAIT_OUTER = 0xCC050510;
    private static final int PORTRAIT_GOLD = 0xFF42A5F5;
    private static final int PORTRAIT_INNER = 0xFF0A2038;
    /** Plate skew (px). Tall plates used to do ~90 fill calls each layer. */
    private static final int PANEL_SKEW = 6;

    private XenoHudSnapshot snapshot;
    private int boundsX;
    private int boundsY;
    private int boundsWidth;
    private int boundsHeight;
    private float scale = 1f;
    private boolean editorMode;

    // Smoothed (eased) displayed values so bars glide toward their target
    // instead of snapping instantly — pure visual polish, no data change.
    private float displayedHp = 1f;
    private float displayedKi = 1f;
    private float displayedStm = 1f;
    private long lastAnimationNanos;
    private boolean firstFrame = true;

    public void setSnapshot(XenoHudSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public void setBounds(int x, int y, int width, int height, float scale) {
        this.boundsX = x;
        this.boundsY = y;
        this.boundsWidth = width;
        this.boundsHeight = height;
        this.scale = scale;
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
    }

    /** Resets smoothing state (e.g. on respawn or renderer switch) to avoid a stale glide-in. */
    public void resetAnimationState() {
        displayedHp = 1f;
        displayedKi = 1f;
        displayedStm = 1f;
        lastAnimationNanos = 0L;
        firstFrame = true;
    }

    public void render(GuiGraphics graphics, float partialTick) {
        if (snapshot == null) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (font == null) return;

        float targetHp = clamp01(snapshot.hpPercent);
        float targetKi = clamp01(snapshot.kiPercent);
        if (firstFrame) {
            displayedHp = targetHp;
            displayedKi = targetKi;
            displayedStm = clamp01(snapshot.stmPercent);
            firstFrame = false;
        } else {
            long now = System.nanoTime();
            double dt = lastAnimationNanos == 0L ? 1.0 / 60.0
                    : Math.min(0.1, Math.max(0.0, (now - lastAnimationNanos) / 1_000_000_000.0));
            float blend = (float) (1.0 - Math.exp(-12.0 * dt));
            displayedHp += (targetHp - displayedHp) * blend;
            displayedKi += (targetKi - displayedKi) * blend;
            displayedStm += (clamp01(snapshot.stmPercent) - displayedStm) * blend;
        }
        lastAnimationNanos = System.nanoTime();

        graphics.pose().pushPose();
        graphics.pose().translate(boundsX, boundsY, 0);
        graphics.pose().scale(scale, scale, 1f);

        // Full-width slanted plate: starts left of the portrait so the skin sits on
        // the same navy glass as the name/HP/KI/STM cluster (drawn before portrait).
        int panelX = -8;
        int panelY = -6;
        int panelRight = CONTENT_LEFT + BAR_W + 14;
        int panelW = panelRight - panelX;
        int panelBottom = Math.max(PORTRAIT + 6, STM_Y + STM_SEG_H + 6);
        int panelH = panelBottom - panelY;
        fillParallelogram(graphics, panelX - 1, panelY - 1, panelW + 2, panelH + 2, PANEL_SKEW, PANEL_BORDER_DIM);
        fillParallelogram(graphics, panelX, panelY, panelW, panelH, PANEL_SKEW, PANEL_BG);
        fillParallelogram(graphics, panelX, panelY + 2, 3, panelH - 4, 0, PANEL_BORDER);
        drawParallelogramBorder(graphics, panelX, panelY, panelW, panelH, PANEL_SKEW, 0x5542A5F5, 1);

        // Layered square portrait frame (on top of the plate).
        graphics.fill(-3, -3, PORTRAIT + 6, PORTRAIT + 6, PORTRAIT_OUTER);
        graphics.fill(-2, -2, PORTRAIT + 4, PORTRAIT + 4, PORTRAIT_GOLD);
        graphics.fill(0, 0, PORTRAIT, PORTRAIT, PORTRAIT_INNER);
        drawPortraitBust(graphics, mc);
        // Thin gold corner accents re-drawn on top so the bust doesn't cover the frame edge.
        drawOctagonBorder(graphics, 0, 0, PORTRAIT, PORTRAIT, PORTRAIT_GOLD, 2);

        if (snapshot.transforming) {
            // Solid charge ring (no sin pulse every frame)
            float chargePct = clamp01(snapshot.transformChargePercent);
            int ringPad = 4;
            int ringThickness = 2 + Math.round(chargePct * 3f);
            int ringColor = 0xE0FF5252;
            drawOctagonBorder(graphics, -ringPad, -ringPad, PORTRAIT + ringPad * 2, PORTRAIT + ringPad * 2,
                    ringColor, ringThickness);
        }

        String name = snapshot.name;
        // Name/charge-% text sits near the very top of the slanted backing plate, where the
        // parallelogram's top edge is shifted right by its full skew — draw past that edge
        // (NAME_X) instead of at the vertical bars' CONTENT_LEFT, or the text pokes out to the
        // left of the plate over the background.
        int nameX = CONTENT_LEFT + PANEL_SKEW + 2;
        graphics.drawString(font, name, nameX, NAME_Y, 0xFFF5F5F5, true);
        if (snapshot.dmzPresent && snapshot.releaseText != null) {
            int rx = nameX + font.width(name) + 8;
            graphics.drawString(font, snapshot.releaseText, rx, NAME_Y, 0xFF00E5FF, true);
        }

        // Low-HP warning: solid critical color (no per-frame pulse — saves draw + sin cost)
        int hpColor = targetHp < 0.25f ? HP_CRITICAL_FLASH : HP_NORMAL;
        drawParallelogramBorder(graphics, CONTENT_LEFT - 2, HP_Y - 2, BAR_W + 4, HP_H + 4, BAR_SKEW, PANEL_BORDER, 1);
        fillParallelogram(graphics, CONTENT_LEFT, HP_Y, BAR_W, HP_H, BAR_SKEW, HP_EMPTY);
        int hpFillW = Math.max(0, Math.round(BAR_W * displayedHp));
        if (hpFillW > 0) fillParallelogram(graphics, CONTENT_LEFT, HP_Y, hpFillW, HP_H, BAR_SKEW, hpColor);
        drawBarValue(graphics, font, CONTENT_LEFT, HP_Y, BAR_W, HP_H, formatPair(snapshot.curHp, snapshot.maxHp));

        drawParallelogramBorder(graphics, CONTENT_LEFT - 2, KI_Y - 2, BAR_W + 4, KI_H + 4, BAR_SKEW, PANEL_BORDER, 1);
        fillParallelogram(graphics, CONTENT_LEFT, KI_Y, BAR_W, KI_H, BAR_SKEW, KI_EMPTY);
        int kiFillW = Math.max(0, Math.round(BAR_W * displayedKi));
        if (kiFillW > 0) fillParallelogram(graphics, CONTENT_LEFT, KI_Y, kiFillW, KI_H, BAR_SKEW, KI_FILLED);
        drawBarValue(graphics, font, CONTENT_LEFT, KI_Y, BAR_W, KI_H, formatPair(snapshot.curKi, snapshot.maxKi));

        // STM: solid segments (shimmer was 16× fillPara + pulse per frame)
        int lit = Math.round(STM_SEGMENTS * displayedStm);
        int gap = 2;
        for (int i = 0; i < STM_SEGMENTS; i++) {
            int sx = CONTENT_LEFT + i * (STM_SEG_W + gap);
            fillParallelogram(graphics, sx, STM_Y, STM_SEG_W, STM_SEG_H, STM_SKEW,
                    i < lit ? STM_LIT_BASE : 0xFF2A2415);
        }
        if (snapshot.maxStm > 0f) {
            String stmText = formatPair(snapshot.curStm, snapshot.maxStm);
            int stmTextX = CONTENT_LEFT + STM_SEGMENTS * (STM_SEG_W + gap) + STM_SKEW + 4;
            graphics.drawString(font, stmText, stmTextX, STM_Y + (STM_SEG_H - 8) / 2, 0xFF00E5FF, true);
        }

        if (editorMode) {
            graphics.renderOutline(0, 0, boundsWidth, boundsHeight, 0xFF42A5F5);
            graphics.fill(boundsWidth - 10, boundsHeight - 10, boundsWidth, boundsHeight, 0xFF42A5F5);
        }

        graphics.pose().popPose();
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /**
     * Filled parallelogram: horizontal top/bottom edges, diagonally slanted
     * left/right sides (top shifted right of bottom by {@code skew} px) —
     * the "italic" gauge look requested for the HP/KI/Stamina boxes.
     */
    private static void fillParallelogram(GuiGraphics g, int x, int y, int w, int h, int skew, int color) {
        HudDraw.fillPara(g, x, y, w, h, skew, color);
    }

    /** Thin border outline of {@link #fillParallelogram}'s slanted silhouette. */
    private static void drawParallelogramBorder(GuiGraphics g, int x, int y, int w, int h, int skew, int color, int t) {
        HudDraw.borderPara(g, x, y, w, h, skew, color, t);
    }

    private static void fillOctagon(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        // Deprecated: this method is kept for compatibility but now just fills a plain rectangle.
        g.fill(x, y, x + w, y + h, color);
    }

    private static void drawOctagonBorder(GuiGraphics g, int x, int y, int w, int h, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        HudDraw.borderRect(g, x, y, w, h, color, t);
    }

    /** Deprecated: no longer used since the portrait frame is now a square. */
    private static int octagonCut(int w, int h) {
        return Math.max(1, Math.round(Math.min(w, h) * 0.22f));
    }


    /** Player face+bust drawn inside the portrait frame — LDLib has no dynamic-skin texture primitive, so
     *  this uses the same vanilla skin-blit approach as the legacy renderer, just clipped to the octagon frame. */
    private void drawPortraitBust(GuiGraphics g, Minecraft mc) {
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        ResourceLocation skin = player.getSkin().texture();
        if (skin == null) return;

        int pad = 5;
        int ix = pad;
        int iy = pad;
        int iw = PORTRAIT - pad * 2;
        int ih = PORTRAIT - pad * 2;

        // Dark glass backdrop matching the rest of the tech plate.
        fillOctagon(g, ix, iy, iw, ih, 0xFF0D1B2A);
        g.fill(ix, iy, ix + iw, iy + ih / 3, 0xFF122A42);
        g.fill(ix, iy + ih - 2, ix + iw, iy + ih, 0x5542A5F5);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, skin);

        // Note: The square backdrop (drawn via g.fill) provides full rectangular area for the bust.

        int head = Math.max(16, Math.round(iw * 0.70f));
        int headX = ix + (iw - head) / 2;
        int headY = iy + 3;
        PlayerFaceRenderer.draw(g, skin, headX, headY, head);

        int bodyW = Math.round(head * 1.05f);
        int bodyH = Math.max(8, Math.round(ih * 0.36f));
        int bodyX = ix + (iw - bodyW) / 2;
        int bodyY = headY + head - Math.max(2, head / 10);
        g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);
        g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 36f, 8, 12, 64, 64);

        int armW = Math.max(4, bodyW / 4);
        int armH = Math.round(bodyH * 0.9f);
        g.blit(skin, bodyX - armW + 2, bodyY + 1, armW, armH, 44f, 20f, 4, 12, 64, 64);
        g.blit(skin, bodyX + bodyW - 2, bodyY + 1, armW, armH, 36f, 52f, 4, 12, 64, 64);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Centered cyan "current / max" readout drawn over a bar, matching the legacy renderer's look.
     *  Drawn at the group's native text scale (no extra nested pushPose scale) — an earlier version
     *  applied a second ~0.6x scale on top of the HUD's own scale, which at small HUD scales (e.g. the
     *  0.55x default) compounded into blurry, near-illegible glyphs. Centered on the bar's mid-height
     *  row span (accounting for the parallelogram skew) so it stays legible over the slanted fill. */
    private static void drawBarValue(GuiGraphics g, Font font, int x, int y, int w, int h, String text) {
        if (text == null || text.isEmpty()) return;
        int tw = font.width(text);
        int cx = x + (w - tw) / 2 + BAR_SKEW / 2;
        int cy = y + Math.max(0, (h - 8) / 2);
        g.drawString(font, text, cx, cy, 0xFF00E5FF, true);
    }

    private static String formatPair(float current, float max) {
        if (max <= 0f) return formatNum(current);
        return formatNum(current) + " / " + formatNum(max);
    }

    private static String formatNum(float value) {
        return HudNumbers.format(value);
    }
}
