package net.bullettrain.xenopixelsmod.client.hud;

import com.lowdragmc.lowdraglib.gui.texture.ResourceTexture;
import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;

/**
 * LDLib-backed HUD view (Phase 4 of the LDLib HUD migration plan).
 *
 * <p>Per the Phase 2 spike decision ({@code docs/ldlib-api-notes.md}), LDLib's
 * full {@code Widget}/{@code WidgetGroup} tree requires a {@code ModularUI}
 * container to be safe, so this view does NOT build a widget tree. Instead it
 * drives LDLib's verified, container-free texture primitives
 * ({@link ResourceTexture}) directly from a Forge overlay, per the plan's own
 * fallback guidance.</p>
 *
 * <p>The HP/KI/Stamina bars and the portrait frame use custom angular
 * silhouettes (slanted parallelogram bars, an octagon portrait plate) rather
 * than plain rectangles — LDLib's texture primitives are rectangle-only, so
 * these non-rectangular shapes are drawn as per-row vanilla
 * {@code GuiGraphics.fill} spans, the same technique the legacy renderer
 * already uses for its own non-rectangular shapes (diamonds/circles/borders).</p>
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

    /** Slant (px) of the parallelogram HP/KI/Stamina bars — leaning like italic fighting-game gauges. */
    private static final int BAR_SKEW = 10;
    private static final int STM_SKEW = 4;

    // XV2-authentic bar fill colors: HP = red, KI = blue/cyan, STM = gold (kept visually distinct from KI).
    private static final int HP_EMPTY = 0xFF3A0808;
    private static final int KI_EMPTY = 0xFF0A2038;
    private static final int KI_FILLED = 0xFF29B6F6;

    private static final int HP_NORMAL = 0xFFE53935;
    private static final int HP_CRITICAL_FLASH = 0xFFFF8A65;
    private static final int STM_LIT_BASE = 0xFFFFC107;
    private static final int STM_LIT_SHIMMER = 0xFFFFE082;

    /** XV2-style plate palette: dark navy body, gold trim, angular (diagonal-cut) corners. */
    private static final int PANEL_BG = 0xD00B1220;
    private static final int PANEL_BORDER = 0xFFC9A227;
    private static final int PANEL_BORDER_DIM = 0xFF6B5416;
    private static final int PORTRAIT_OUTER = 0xFF0B0F16;
    private static final int PORTRAIT_GOLD = 0xFFC9A227;
    private static final int PORTRAIT_INNER = 0xFF163A5C;
    /** Slant (px) of the outer backing plate — matches the bars' parallelogram look. */
    private static final int PANEL_SKEW = 14;

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
            firstFrame = false;
        } else {
            displayedHp = AnimUtil.ease(displayedHp, targetHp, 0.15f);
            displayedKi = AnimUtil.ease(displayedKi, targetKi, 0.15f);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(boundsX, boundsY, 0);
        graphics.pose().scale(scale, scale, 1f);

        // XV2-style slanted backing plate behind the name/bars cluster — parallelogram to match
        // the HP/KI/Stamina bars, instead of the old angular cut-rect shape.
        int panelX = CONTENT_LEFT - 10;
        int panelY = NAME_Y - 4;
        int panelW = BAR_W + 20;
        int panelH = (STM_Y + STM_SEG_H) - panelY + 6;
        fillParallelogram(graphics, panelX - 1, panelY - 1, panelW + 2, panelH + 2, PANEL_SKEW, PANEL_BORDER_DIM);
        fillParallelogram(graphics, panelX, panelY, panelW, panelH, PANEL_SKEW, PANEL_BG);

        // Layered octagon portrait frame — XV2-style plate, not a plain rounded square.
        // Non-rectangular shapes require per-row fills (LDLib's rect-only texture primitives can't do
        // this), so this chrome is drawn with vanilla GuiGraphics fills, same technique the legacy
        // renderer already uses for its own non-rectangular shapes (diamonds/circles/borders).
        fillOctagon(graphics, -3, -3, PORTRAIT + 6, PORTRAIT + 6, PORTRAIT_OUTER);
        fillOctagon(graphics, -2, -2, PORTRAIT + 4, PORTRAIT + 4, PORTRAIT_GOLD);
        fillOctagon(graphics, 0, 0, PORTRAIT, PORTRAIT, PORTRAIT_INNER);
        drawPortraitBust(graphics, mc);
        // Thin gold corner accents re-drawn on top so the bust doesn't cover the frame edge.
        drawOctagonBorder(graphics, 0, 0, PORTRAIT, PORTRAIT, PORTRAIT_GOLD, 2);

        if (snapshot.transforming) {
            // Animated charge ring drawn OUTSIDE the frame, growing thicker and brighter as charge
            // fills, plus a fast pulse, so it reads clearly as "charging". Matches the octagon shape.
            float pulse = AnimUtil.pulse01(500L);
            float chargePct = clamp01(snapshot.transformChargePercent);
            int ringPad = 4 + Math.round(pulse * 2f);
            int ringThickness = 2 + Math.round(chargePct * 4f);
            int glowAlpha = Math.round(160 + pulse * 95);
            glowAlpha = Math.max(0, Math.min(255, glowAlpha));
            int ringColor = (glowAlpha << 24) | (AnimUtil.lerpColor(0xFF1744, 0xFF8A65, pulse * 0.5f) & 0xFFFFFF);
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

        // Low-HP warning pulse: fill color flashes toward a brighter orange-red under 25%.
        int hpColor = HP_NORMAL;
        if (targetHp < 0.25f) {
            float alarm = AnimUtil.pulse01(500L);
            hpColor = AnimUtil.lerpColor(HP_NORMAL, HP_CRITICAL_FLASH, alarm);
        }
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

        int lit = Math.round(STM_SEGMENTS * clamp01(snapshot.stmPercent));
        int gap = 2;
        for (int i = 0; i < STM_SEGMENTS; i++) {
            int sx = CONTENT_LEFT + i * (STM_SEG_W + gap);
            if (i < lit) {
                // Traveling shimmer: each segment pulses slightly out of phase with its neighbors.
                float shimmer = AnimUtil.pulse01(1400L, i * 90L);
                int color = AnimUtil.lerpColor(STM_LIT_BASE, STM_LIT_SHIMMER, shimmer * 0.5f);
                fillParallelogram(graphics, sx, STM_Y, STM_SEG_W, STM_SEG_H, STM_SKEW, color);
            } else {
                fillParallelogram(graphics, sx, STM_Y, STM_SEG_W, STM_SEG_H, STM_SKEW, 0xFF2A2415);
            }
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
        if (w <= 0 || h <= 0) return;
        for (int row = 0; row < h; row++) {
            int offset = rowOffset(row, h, skew);
            g.fill(x + offset, y + row, x + offset + w, y + row + 1, color);
        }
    }

    /** Thin border outline of {@link #fillParallelogram}'s slanted silhouette. */
    private static void drawParallelogramBorder(GuiGraphics g, int x, int y, int w, int h, int skew, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        for (int row = 0; row < h; row++) {
            int offset = rowOffset(row, h, skew);
            int left = x + offset;
            int right = left + w;
            if (row < t || row >= h - t) {
                g.fill(left, y + row, right, y + row + 1, color);
            } else {
                g.fill(left, y + row, Math.min(left + t, right), y + row + 1, color);
                g.fill(Math.max(right - t, left), y + row, right, y + row + 1, color);
            }
        }
    }

    private static int rowOffset(int row, int h, int skew) {
        if (h <= 1) return skew;
        return Math.round(skew * (1f - row / (float) (h - 1)));
    }

    /**
     * Filled octagon: a rectangle with all four corners diagonally cut off
     * evenly — the pentagon's pointed roof clipped the player's head/face
     * bust at the apex, so this gives the portrait plate an XV2-style
     * angular silhouette with full rectangular headroom for the bust.
     */
    private static void fillOctagon(GuiGraphics g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        int c = octagonCut(w, h);
        for (int row = 0; row < h; row++) {
            int left = x;
            int right = x + w;
            if (row < c) {
                int inset = c - row;
                left = x + inset;
                right = x + w - inset;
            } else {
                int distFromBottom = h - 1 - row;
                if (distFromBottom < c) {
                    int inset = c - distFromBottom;
                    left = x + inset;
                    right = x + w - inset;
                }
            }
            if (right > left) g.fill(left, y + row, right, y + row + 1, color);
        }
    }

    /** Thin border outline of {@link #fillOctagon}'s silhouette. */
    private static void drawOctagonBorder(GuiGraphics g, int x, int y, int w, int h, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        int c = octagonCut(w, h);
        for (int row = 0; row < h; row++) {
            int left = x;
            int right = x + w;
            if (row < c) {
                int inset = c - row;
                left = x + inset;
                right = x + w - inset;
            } else {
                int distFromBottom = h - 1 - row;
                if (distFromBottom < c) {
                    int inset = c - distFromBottom;
                    left = x + inset;
                    right = x + w - inset;
                }
            }
            if (right <= left) continue;
            g.fill(left, y + row, Math.min(left + t, right), y + row + 1, color);
            g.fill(Math.max(right - t, left), y + row, right, y + row + 1, color);
        }
    }

    private static int octagonCut(int w, int h) {
        return Math.max(1, Math.round(Math.min(w, h) * 0.22f));
    }


    /** Player face+bust drawn inside the portrait frame — LDLib has no dynamic-skin texture primitive, so
     *  this uses the same vanilla skin-blit approach as the legacy renderer, just clipped to the octagon frame. */
    private void drawPortraitBust(GuiGraphics g, Minecraft mc) {
        AbstractClientPlayer player = mc.player;
        if (player == null) return;
        ResourceLocation skin = player.getSkinTextureLocation();
        if (skin == null) return;

        int pad = 5;
        int ix = pad;
        int iy = pad;
        int iw = PORTRAIT - pad * 2;
        int ih = PORTRAIT - pad * 2;

        // Sky/ground backdrop behind the bust, clipped to the same octagon silhouette as the frame.
        fillOctagon(g, ix, iy, iw, ih, 0xFF6BB7E8);
        g.fill(ix, iy, ix + iw, iy + ih / 2, 0xFF8FD0F5);
        g.fill(ix, iy + ih * 2 / 3, ix + iw, iy + ih, 0xFF4A8A45);
        g.fill(ix, iy + ih / 2, ix + iw, iy + ih * 2 / 3, 0xFF5FA35A);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, skin);

        // Note: No scissor clipping needed here — the octagon backdrop (drawn via fillOctagon) already
        // provides visual containment. Removing this clip prevents asymmetric cutting of body/arms.

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
        if (value >= 1_000_000f) return String.format("%.1fM", value / 1_000_000f);
        if (value >= 10_000f) return String.format("%.1fK", value / 1000f);
        if (Math.abs(value - Math.round(value)) < 0.05f) return String.valueOf(Math.round(value));
        return String.format("%.0f", value);
    }
}
