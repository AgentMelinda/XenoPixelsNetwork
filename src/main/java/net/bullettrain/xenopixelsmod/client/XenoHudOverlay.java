package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Xeno combat HUD — tech-HUD palette (navy glass + cyan rails) with parallelogram
 * plate/bars. Portrait + name + HP/KI gauges + STM segments.
 */
@OnlyIn(Dist.CLIENT)
public class XenoHudOverlay implements IGuiOverlay {
    private static final ResourceLocation TEX = new ResourceLocation(XenoPixelsMod.MOD_ID, "textures/gui/xeno_hud.png");

    private static final int PORTRAIT = 68;
    private static final int PORTRAIT_PAD = 4;
    private static final int CONTENT_LEFT = 80;
    private static final int BAR_W = 300;
    private static final int HP_H = 10;
    private static final int KI_H = 12;
    private static final int NAME_Y = 6;
    private static final int HP_Y = 24;
    private static final int KI_Y = 38;
    private static final int STM_Y = 56;
    private static final int STM_SEGMENTS = 16;
    private static final int STM_SEG_W = 14;
    private static final int STM_SEG_H = 10;
    private static final int BAR_SKEW = 4;
    private static final int PANEL_SKEW = 6;
    private static final int STM_SKEW = 0;

    // Tech-HUD palette (shared with technique bar)
    private static final int TECH_BG = 0xEE0A1428;
    private static final int TECH_BG_OUTER = 0xCC050510;
    private static final int TECH_ACCENT = 0xFF42A5F5;
    private static final int TECH_ACCENT_SOFT = 0xFF90CAF9;
    private static final int TECH_SLOT = 0xAA122038;
    private static final int HP_EMPTY = 0xFF3A0808;
    private static final int HP_FILL = 0xFFE53935;
    private static final int HP_SHINE = 0xFFFF8A80;
    private static final int KI_EMPTY = 0xFF0A2038;
    private static final int KI_FILL = 0xFF1E88E5;
    private static final int KI_SHINE = 0xFF64B5F6;
    private static final int STM_ON = 0xFFFFC107;
    private static final int STM_OFF = 0xFF2A2415;

    /** Frame-rate independent display state for the default renderer. */
    private static float displayedHp = 1f;
    private static float displayedKi = 1f;
    private static float displayedStm = 1f;
    private static long lastAnimationNanos;
    private static int animatedPlayerId = Integer.MIN_VALUE;
    /** Numeric strings only change with the tick-cached snapshot, not every rendered frame. */
    private static XenoHudSnapshot formattedSnapshot;
    private static String formattedHp = "";
    private static String formattedKi = "";

    private static final net.bullettrain.xenopixelsmod.client.hud.XenoHudView LDLIB_VIEW =
            new net.bullettrain.xenopixelsmod.client.hud.XenoHudView();

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !XenoClientConfig.xenoHudEnabled || !XenoHudConfig.visible) return;
        renderHud(graphics, screenWidth, screenHeight, false);
    }

    public static void renderHud(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        XenoHudSnapshot snap = XenoHudSnapshotFactory.capture(mc);

        if (!XenoHudConfig.legacyHudRenderer) {
            // Phase 4 LDLib-backed renderer (migration-testing toggle).
            LDLIB_VIEW.setSnapshot(snap);
            LDLIB_VIEW.setBounds(XenoHudConfig.x, XenoHudConfig.y,
                    XenoHudConfig.BASE_WIDTH, XenoHudConfig.BASE_HEIGHT, XenoHudConfig.scale);
            LDLIB_VIEW.setEditorMode(editing);
            LDLIB_VIEW.render(graphics, 1f);
            return;
        }

        updateVisualState(mc, snap, editing);

        graphics.pose().pushPose();
        graphics.pose().translate(XenoHudConfig.x, XenoHudConfig.y, 0);
        graphics.pose().scale(XenoHudConfig.scale, XenoHudConfig.scale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // Plate first so it reaches under the portrait; skin/frame drawn on top
        drawBackingPlate(graphics);
        drawPortraitDropShadow(graphics);
        drawPortrait(graphics, mc, snap);
        drawBarsCluster(graphics, mc, snap);
        drawP1Badge(graphics, mc.font);
        drawSkillOrb(graphics);

        if (editing) {
            graphics.renderOutline(0, 0, XenoHudConfig.BASE_WIDTH, XenoHudConfig.BASE_HEIGHT, 0xFF42A5F5);
            graphics.fill(XenoHudConfig.BASE_WIDTH - 10, XenoHudConfig.BASE_HEIGHT - 10,
                    XenoHudConfig.BASE_WIDTH, XenoHudConfig.BASE_HEIGHT, 0xFF42A5F5);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();
    }

    private static void drawPortraitDropShadow(GuiGraphics g) {
        // Single soft shadow (was 4 layered fills every frame)
        int s = PORTRAIT;
        g.fill(4, 6, s + 4, s + 8, 0x40000000);
    }

    private static void drawPortrait(GuiGraphics g, Minecraft mc, XenoHudSnapshot snap) {
        int s = PORTRAIT;

        // Tech-style square frame (navy + cyan, same as technique panel)
        g.fill(-3, -3, s + 3, s + 3, TECH_BG_OUTER);
        g.fill(-2, -2, s + 2, s + 2, 0xFF1E4A7A);
        g.fill(-1, -1, s + 1, s + 1, TECH_ACCENT);
        g.fill(0, 0, s, s, 0xFF0A2038);
        // left cyan accent strip
        g.fill(0, 0, 3, s, TECH_ACCENT);

        int ix = PORTRAIT_PAD;
        int iy = PORTRAIT_PAD;
        int iw = s - PORTRAIT_PAD * 2;
        int ih = s - PORTRAIT_PAD * 2;

        // Dark tech backdrop instead of cartoon sky
        g.fill(ix, iy, ix + iw, iy + ih, 0xFF0D1B2A);
        g.fill(ix, iy, ix + iw, iy + ih / 3, 0xFF12253A);

        LocalPlayer player = mc.player;
        if (player != null) {
            ResourceLocation skin = player.getSkinTextureLocation();
            if (skin != null) {
                drawPlayerBust(g, skin, ix, iy, iw, ih);
            }
        }

        // Outer cyan rim
        g.fill(0, 0, s, 2, 0x6642A5F5);
        g.fill(0, s - 2, s, s, 0x442A2A3A);

        if (snap.transforming) {
            drawTransformChargeBorder(g, -4, -4, s + 8, s + 8, 0, snap.transformChargePercent);
        }
    }

    /**
     * Red rounded border that fills clockwise with transform charge (hold G).
     * Track is dim; fill is bright red — no percentage text.
     */
    private static void drawTransformChargeBorder(GuiGraphics g, int x, int y, int w, int h, int radius, float percent) {
        percent = clamp01(percent);
        if (percent <= 0f) return;

        int t = 4;
        net.bullettrain.xenopixelsmod.client.hud.HudDraw.borderRect(
                g, x, y, w, h, 0xAA3A0A12, t);

        // Approximate progressive fill with rounded outline segments via perimeter walk
        int top = w;
        int right = h - t;
        int bottom = w - t;
        int left = h - 2 * t;
        int perimeter = Math.max(1, top + right + bottom + left);
        int filled = Math.max(1, Math.round(perimeter * percent));

        int core = 0xFFFF1744;
        int hot = 0xFFFF8A80;
        int rem = filled;

        int take = Math.min(rem, top);
        if (take > 0) {
            g.fill(x, y, x + take, y + t, core);
            g.fill(x, y + 1, x + take, y + 2, hot);
            rem -= take;
        }
        take = Math.min(rem, right);
        if (take > 0) {
            g.fill(x + w - t, y + t, x + w, y + t + take, core);
            g.fill(x + w - 2, y + t, x + w - 1, y + t + take, hot);
            rem -= take;
        }
        take = Math.min(rem, bottom);
        if (take > 0) {
            g.fill(x + w - take, y + h - t, x + w, y + h, core);
            g.fill(x + w - take, y + h - 2, x + w, y + h - 1, hot);
            rem -= take;
        }
        take = Math.min(rem, left);
        if (take > 0) {
            g.fill(x, y + h - t - take, x + t, y + h - t, core);
            g.fill(x + 1, y + h - t - take, x + 2, y + h - t, hot);
        }
    }

    private static void drawPlayerBust(GuiGraphics g, ResourceLocation skin, int x, int y, int w, int h) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, skin);

        // Face first (simpler 5-arg API is reliable)
        int head = Math.max(16, Math.round(w * 0.70f));
        int headX = x + (w - head) / 2;
        int headY = y + 3;
        PlayerFaceRenderer.draw(g, skin, headX, headY, head);

        // Upper body under chin
        int bodyW = Math.round(head * 1.05f);
        int bodyH = Math.max(8, Math.round(h * 0.36f));
        int bodyX = x + (w - bodyW) / 2;
        int bodyY = headY + head - Math.max(2, head / 10);
        g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 20f, 8, 12, 64, 64);
        g.blit(skin, bodyX, bodyY, bodyW, bodyH, 20f, 36f, 8, 12, 64, 64);

        int armW = Math.max(4, bodyW / 4);
        int armH = Math.round(bodyH * 0.9f);
        g.blit(skin, bodyX - armW + 2, bodyY + 1, armW, armH, 44f, 20f, 4, 12, 64, 64);
        g.blit(skin, bodyX + bodyW - 2, bodyY + 1, armW, armH, 36f, 52f, 4, 12, 64, 64);

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Full-width tech parallelogram plate: starts left of the portrait so the
     * skin sits on top of the same navy glass as the HP/KI/STM cluster.
     */
    private static void drawBackingPlate(GuiGraphics g) {
        // Left edge behind portrait frame (portrait is ~-3..PORTRAIT+3 with rim)
        int panelX = -8;
        int panelY = -6;
        // Right edge still covers name + bars (CONTENT_LEFT + BAR_W + padding)
        int panelRight = CONTENT_LEFT + BAR_W + 14;
        int panelW = panelRight - panelX;
        // Tall enough to cover portrait (68) and STM row
        int panelBottom = Math.max(PORTRAIT + 6, STM_Y + STM_SEG_H + 8);
        int panelH = panelBottom - panelY;

        fillParallelogram(g, panelX - 2, panelY - 2, panelW + 4, panelH + 4, PANEL_SKEW + 1, TECH_BG_OUTER);
        fillParallelogram(g, panelX, panelY, panelW, panelH, PANEL_SKEW, TECH_BG);
        // Cyan rail along the left slant of the plate (under portrait left edge)
        fillParallelogram(g, panelX, panelY + 2, 3, panelH - 4, 0, TECH_ACCENT);
        drawParallelogramBorder(g, panelX, panelY, panelW, panelH, PANEL_SKEW, 0x5542A5F5, 1);
        // Two cheap rails give the plate depth and keep the information cluster
        // visually anchored without another translucent full-panel layer.
        g.fill(CONTENT_LEFT, panelY + 4, panelRight - 8, panelY + 5, 0x3342A5F5);
        g.fill(CONTENT_LEFT, panelBottom - 4, panelRight - 14, panelBottom - 3, 0x222A80B9);
    }

    private static void drawBarsCluster(GuiGraphics g, Minecraft mc, XenoHudSnapshot snap) {
        Font font = mc.font;
        String name = snap.name;

        // Name sits inside the plate (past top skew so it doesn't poke left)
        int nameX = CONTENT_LEFT + PANEL_SKEW / 2 + 2;
        g.drawString(font, name, nameX, NAME_Y, 0xFFE3F2FD, true);
        if (snap.dmzPresent && snap.releaseText != null) {
            int rx = nameX + font.width(name) + 8;
            g.drawString(font, snap.releaseText, rx, NAME_Y, TECH_ACCENT_SOFT, true);
        }

        float hp = displayedHp;
        float ki = displayedKi;
        float stm = displayedStm;

        // HP — tech-style parallelogram gauge (red)
        drawParaBar(g, CONTENT_LEFT, HP_Y, BAR_W, HP_H, hp, HP_EMPTY, HP_FILL, HP_SHINE);
        drawBarLabel(g, font, CONTENT_LEFT, HP_Y, HP_H, "HP", 0xFFFFCDD2);
        drawBarValue(g, font, CONTENT_LEFT, HP_Y, BAR_W, HP_H, formattedHp, 0xFFE3F2FD);

        // KI — cyan/blue like technique charge bar
        drawParaBar(g, CONTENT_LEFT, KI_Y, BAR_W, KI_H, ki, KI_EMPTY, KI_FILL, KI_SHINE);
        drawBarLabel(g, font, CONTENT_LEFT, KI_Y, KI_H, "KI", 0xFFB3E5FC);
        drawBarValue(g, font, CONTENT_LEFT, KI_Y, BAR_W, KI_H, formattedKi, 0xFFB3E5FC);

        // STM — segmented parallelogram pips (tech slot language)
        drawStmRow(g, font, CONTENT_LEFT, STM_Y, BAR_W, stm);
    }

    private static String formatPair(float current, float max) {
        if (max <= 0f) return formatNum(current);
        return formatNum(current) + " / " + formatNum(max);
    }

    private static String formatNum(float value) {
        if (value >= 1_000_000f) return oneDecimal(value / 1_000_000f) + "M";
        if (value >= 10_000f) return oneDecimal(value / 1000f) + "K";
        if (Math.abs(value - Math.round(value)) < 0.05f) return String.valueOf(Math.round(value));
        return String.valueOf(Math.round(value));
    }

    private static String oneDecimal(float value) {
        return Float.toString(Math.round(value * 10f) / 10f);
    }

    private static void updateVisualState(Minecraft mc, XenoHudSnapshot snap, boolean editing) {
        long now = System.nanoTime();
        int playerId = mc.player == null ? -1 : mc.player.getId();
        boolean reset = lastAnimationNanos == 0L || animatedPlayerId != playerId || editing;
        if (reset) {
            displayedHp = clamp01(snap.hpPercent);
            displayedKi = clamp01(snap.kiPercent);
            displayedStm = clamp01(snap.stmPercent);
        } else {
            double dt = Math.min(0.1, Math.max(0.0, (now - lastAnimationNanos) / 1_000_000_000.0));
            float blend = (float) (1.0 - Math.exp(-12.0 * dt));
            displayedHp += (clamp01(snap.hpPercent) - displayedHp) * blend;
            displayedKi += (clamp01(snap.kiPercent) - displayedKi) * blend;
            displayedStm += (clamp01(snap.stmPercent) - displayedStm) * blend;
        }
        lastAnimationNanos = now;
        animatedPlayerId = playerId;

        if (formattedSnapshot != snap) {
            formattedSnapshot = snap;
            formattedHp = formatPair(snap.curHp, snap.maxHp);
            formattedKi = formatPair(snap.curKi, snap.maxKi);
        }
    }

    private static void drawBarLabel(GuiGraphics g, Font font, int x, int y, int h,
                                     String label, int color) {
        g.fill(x + 3, y + 1, x + 22, y + h - 1, 0x99050A14);
        g.fill(x + 3, y + 1, x + 5, y + h - 1, color);
        g.drawString(font, label, x + 8, y + Math.max(0, (h - 8) / 2), color, false);
    }

    private static void drawBarValue(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int color) {
        // Draw at native text scale (no extra nested pushPose scale) — an earlier version applied a
        // second ~0.6x scale on top of the HUD's own overall scale, which at small HUD scales (e.g. the
        // 0.55x default) compounded into blurry, near-illegible glyphs.
        if (text == null || text.isEmpty()) return;
        int tw = font.width(text);
        int cx = x + (w - tw) / 2;
        int cy = y + Math.max(0, (h - 8) / 2);
        g.drawString(font, text, cx, cy, color, true);
    }

    /** Tech-style parallelogram HP/KI gauge. */
    private static void drawParaBar(GuiGraphics g, int x, int y, int w, int h, float percent,
                                    int empty, int fill, int shine) {
        percent = clamp01(percent);
        int filled = Math.max(0, Math.round(w * percent));
        drawParallelogramBorder(g, x - 2, y - 2, w + 4, h + 4, BAR_SKEW, 0xFF050510, 1);
        fillParallelogram(g, x, y, w, h, BAR_SKEW, empty);
        if (filled > 0) {
            fillParallelogram(g, x, y, filled, h, BAR_SKEW, fill);
            fillParallelogram(g, x, y, filled, Math.max(2, h / 3), BAR_SKEW, shine);
            if (filled > 3 && filled < w) {
                fillParallelogram(g, x + filled - 2, y, 2, h, 0, shine);
            }
        }
    }

    private static void drawStmRow(GuiGraphics g, Font font, int x, int y, int w, float percent) {
        percent = clamp01(percent);

        int labelW = 28;
        // STM label chip (tech badge style)
        g.fill(x, y, x + labelW, y + STM_SEG_H, TECH_SLOT);
        g.fill(x, y, x + 2, y + STM_SEG_H, TECH_ACCENT);
        g.drawString(font, "STM", x + 5, y + 1, TECH_ACCENT_SOFT, false);

        int start = x + labelW + 4;
        int gap = 2;
        int lit = Math.round(STM_SEGMENTS * percent);

        for (int i = 0; i < STM_SEGMENTS; i++) {
            int sx = start + i * (STM_SEG_W + gap);
            // Axis-aligned segments (skew=0) — 1 fill each instead of per-row
            fillParallelogram(g, sx, y, STM_SEG_W, STM_SEG_H, 0, i < lit ? STM_ON : STM_OFF);
        }
    }

    private static void drawP1Badge(GuiGraphics g, Font font) {
        int bx = 0;
        int by = PORTRAIT - 14;
        int bw = 32;
        int bh = 14;
        g.fill(bx, by, bx + bw, by + bh, TECH_BG_OUTER);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, 0xFF1E4A7A);
        g.fill(bx, by, bx + 2, by + bh, TECH_ACCENT);
        g.drawCenteredString(font, "P1", bx + bw / 2, by + 3, 0xFFE3F2FD);
    }

    private static void drawSkillOrb(GuiGraphics g) {
        int cx = 16;
        int cy = PORTRAIT + 12;
        // Tech square pip instead of soft orb
        g.fill(cx - 9, cy - 9, cx + 9, cy + 9, TECH_BG_OUTER);
        g.fill(cx - 7, cy - 7, cx + 7, cy + 7, 0xFF1E4A7A);
        g.fill(cx - 5, cy - 5, cx + 5, cy + 5, TECH_ACCENT);
        g.fill(cx - 2, cy - 3, cx + 1, cy, 0x88FFFFFF);
        g.blit(TEX, cx - 8, cy - 8, 16, 16, 70f, 80f, 16, 16, 256, 128);
    }

    private static void fillParallelogram(GuiGraphics g, int x, int y, int w, int h, int skew, int color) {
        net.bullettrain.xenopixelsmod.client.hud.HudDraw.fillPara(g, x, y, w, h, skew, color);
    }

    private static void drawParallelogramBorder(GuiGraphics g, int x, int y, int w, int h, int skew, int color, int t) {
        net.bullettrain.xenopixelsmod.client.hud.HudDraw.borderPara(g, x, y, w, h, skew, color, t);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static void fillCircle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int span = (int) Math.sqrt(r * r - dy * dy);
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    /** Filled rounded rectangle (pixel approx). */
    private static void fillRoundedRect(GuiGraphics g, int x, int y, int w, int h, int radius, int color) {
        if (w <= 0 || h <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        // center
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + r, y + h - r, color);
        g.fill(x + w - r, y + r, x + w, y + h - r, color);
        // corners
        fillQuarterCircle(g, x + r, y + r, r, 0, color);
        fillQuarterCircle(g, x + w - r - 1, y + r, r, 1, color);
        fillQuarterCircle(g, x + r, y + h - r - 1, r, 2, color);
        fillQuarterCircle(g, x + w - r - 1, y + h - r - 1, r, 3, color);
    }

    private static void fillQuarterCircle(GuiGraphics g, int cx, int cy, int r, int quadrant, int color) {
        for (int dy = 0; dy <= r; dy++) {
            int span = (int) Math.sqrt(r * r - dy * dy);
            switch (quadrant) {
                case 0 -> g.fill(cx - span, cy - dy, cx + 1, cy - dy + 1, color); // TL
                case 1 -> g.fill(cx, cy - dy, cx + span + 1, cy - dy + 1, color); // TR
                case 2 -> g.fill(cx - span, cy + dy, cx + 1, cy + dy + 1, color); // BL
                case 3 -> g.fill(cx, cy + dy, cx + span + 1, cy + dy + 1, color); // BR
                default -> {
                }
            }
        }
    }

    /** Border-only rounded rectangle (does not cover interior). */
    private static void drawRoundedBorder(GuiGraphics g, int x, int y, int w, int h, int radius, int color, int t) {
        if (w <= 0 || h <= 0 || t <= 0) return;
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        // edges
        g.fill(x + r, y, x + w - r, y + t, color);
        g.fill(x + r, y + h - t, x + w - r, y + h, color);
        g.fill(x, y + r, x + t, y + h - r, color);
        g.fill(x + w - t, y + r, x + w, y + h - r, color);
        // corner arcs (ring thickness t)
        drawCornerRing(g, x + r, y + r, r, t, 0, color);
        drawCornerRing(g, x + w - r - 1, y + r, r, t, 1, color);
        drawCornerRing(g, x + r, y + h - r - 1, r, t, 2, color);
        drawCornerRing(g, x + w - r - 1, y + h - r - 1, r, t, 3, color);
    }

    private static void drawCornerRing(GuiGraphics g, int cx, int cy, int r, int t, int quadrant, int color) {
        int inner = Math.max(0, r - t);
        for (int dy = 0; dy <= r; dy++) {
            int outerSpan = (int) Math.sqrt(r * r - dy * dy);
            int innerSpan = dy <= inner ? (int) Math.sqrt(inner * inner - dy * dy) : 0;
            switch (quadrant) {
                case 0 -> { // TL
                    g.fill(cx - outerSpan, cy - dy, cx - innerSpan + 1, cy - dy + 1, color);
                }
                case 1 -> { // TR
                    g.fill(cx + innerSpan, cy - dy, cx + outerSpan + 1, cy - dy + 1, color);
                }
                case 2 -> { // BL
                    g.fill(cx - outerSpan, cy + dy, cx - innerSpan + 1, cy + dy + 1, color);
                }
                case 3 -> { // BR
                    g.fill(cx + innerSpan, cy + dy, cx + outerSpan + 1, cy + dy + 1, color);
                }
                default -> {
                }
            }
        }
    }
}
