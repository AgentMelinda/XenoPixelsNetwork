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
 * Xenoverse 2 style floating combat HUD (rounded chrome):
 * portrait + name + HP/KI pills + STM diamonds. No STM/release numbers.
 */
@OnlyIn(Dist.CLIENT)
public class XenoHudOverlay implements IGuiOverlay {
    private static final ResourceLocation TEX = new ResourceLocation(XenoPixelsMod.MOD_ID, "textures/gui/xeno_hud.png");

    private static final int PORTRAIT = 68;
    private static final int PORTRAIT_R = 10;
    private static final int PORTRAIT_PAD = 4;
    private static final int CONTENT_LEFT = 80;
    private static final int BAR_W = 300;
    private static final int HP_H = 9;
    private static final int KI_H = 12;
    private static final int BAR_R = 5;
    private static final int NAME_Y = 6;
    private static final int HP_Y = 24;
    private static final int KI_Y = 38;
    private static final int STM_Y = 56;
    private static final int STM_SEGMENTS = 16;
    private static final int DIAMOND = 10;

    @Override
    public void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || !XenoClientConfig.xenoHudEnabled || !XenoHudConfig.visible) return;
        renderHud(graphics, screenWidth, screenHeight, false);
    }

    public static void renderHud(GuiGraphics graphics, int screenWidth, int screenHeight, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        graphics.pose().pushPose();
        graphics.pose().translate(XenoHudConfig.x, XenoHudConfig.y, 0);
        graphics.pose().scale(XenoHudConfig.scale, XenoHudConfig.scale, 1f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        DmzClientStats.Snapshot dmz = DmzClientStats.read(mc.player);

        drawPortraitDropShadow(graphics);
        drawPortrait(graphics, mc, dmz);
        drawBarsCluster(graphics, mc, dmz);
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
        int s = PORTRAIT;
        for (int i = 5; i >= 1; i--) {
            int a = 8 + i * 10;
            fillRoundedRect(g, 1 - i, 3 + i, s + 2 + i * 2, s + 4 + i, PORTRAIT_R + i, (a << 24));
        }
    }

    private static void drawPortrait(GuiGraphics g, Minecraft mc, DmzClientStats.Snapshot dmz) {
        int s = PORTRAIT;
        int r = PORTRAIT_R;

        // Soft rounded blue frame
        fillRoundedRect(g, -2, -2, s + 4, s + 4, r + 2, 0xFF0A2F5C);
        fillRoundedRect(g, -1, -1, s + 2, s + 2, r + 1, 0xFF1E6BB8);
        fillRoundedRect(g, 0, 0, s, s, r, 0xFF2F8FE0);
        fillRoundedRect(g, 2, 2, s - 4, s - 4, r - 2, 0xFF163A68);

        int ix = PORTRAIT_PAD;
        int iy = PORTRAIT_PAD;
        int iw = s - PORTRAIT_PAD * 2;
        int ih = s - PORTRAIT_PAD * 2;
        int ir = Math.max(4, r - 3);

        // Sky / ground backdrop (rounded clip approx)
        fillRoundedRect(g, ix, iy, iw, ih, ir, 0xFF6BB7E8);
        // top sky half
        g.fill(ix, iy, ix + iw, iy + ih / 2, 0xFF8FD0F5);
        // ground
        g.fill(ix, iy + ih * 2 / 3, ix + iw, iy + ih, 0xFF4A8A45);
        g.fill(ix, iy + ih / 2, ix + iw, iy + ih * 2 / 3, 0xFF5FA35A);

        LocalPlayer player = mc.player;
        if (player != null) {
            ResourceLocation skin = player.getSkinTextureLocation();
            if (skin != null) {
                drawPlayerBust(g, skin, ix, iy, iw, ih);
            }
        }

        // Thin dark rim only (must NOT fill over the bust)
        drawRoundedBorder(g, 0, 0, s, s, r, 0xFF0A2A55, 2);
        // Soft top highlight strip (alpha)
        g.fill(r, 0, s - r, 2, 0x55FFFFFF);

        if (dmz != null && dmz.present && dmz.isTransforming()) {
            drawTransformChargeBorder(g, -4, -4, s + 8, s + 8, r + 3, dmz.transformChargePercent());
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
        drawRoundedBorder(g, x, y, w, h, radius, 0xAA3A0A12, t);

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

    private static void drawBarsCluster(GuiGraphics g, Minecraft mc, DmzClientStats.Snapshot dmz) {
        Font font = mc.font;
        String name = resolveName(mc);

        // Name + release % only (XV2 style)
        g.drawString(font, name, CONTENT_LEFT + 1, NAME_Y + 1, 0x88000000, false);
        g.drawString(font, name, CONTENT_LEFT, NAME_Y, 0xFFF5F5F5, false);
        if (dmz.present) {
            String releaseText = dmz.powerRelease + "%";
            int rx = CONTENT_LEFT + font.width(name) + 8;
            // Always cyan for release % (XenoPixels style)
            g.drawString(font, releaseText, rx + 1, NAME_Y + 1, 0x88000000, false);
            g.drawString(font, releaseText, rx, NAME_Y, 0xFF00E5FF, false);
        }

        float hp = resolveHp(mc, dmz);
        float ki = dmz.present ? dmz.energyPercent() : resolveKiFallback();
        float stm = dmz.present ? dmz.staminaPercent() : resolveStmFallback();

        float curHp = resolveCurrentHp(mc);
        float maxHp = resolveMaxHp(mc, dmz);
        float curKi = dmz.present ? dmz.energy : XenoClientData.ki;
        float maxKi = dmz.present ? dmz.maxEnergy : XenoClientData.maxKi;

        // Rounded HP / KI pills — numbers only on these two
        drawRoundedBar(g, CONTENT_LEFT, HP_Y, BAR_W, HP_H, hp,
                0xFF4A0808, 0xFFFF6E6E, 0xFFE53935, 0xFFB71C1C);
        drawBarValue(g, font, CONTENT_LEFT, HP_Y, BAR_W, HP_H,
                formatPair(curHp, maxHp), 0xFF00E5FF);

        drawRoundedBar(g, CONTENT_LEFT, KI_Y, BAR_W, KI_H, ki,
                0xFF3A3008, 0xFFFFF59D, 0xFFFDD835, 0xFFF9A825);
        drawBarValue(g, font, CONTENT_LEFT, KI_Y, BAR_W, KI_H,
                formatPair(curKi, maxKi), 0xFF00E5FF);

        // STM diamonds only — no numbers
        drawStmRow(g, font, CONTENT_LEFT, STM_Y, BAR_W, stm);
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

    private static void drawBarValue(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int color) {
        float scale = h <= 10 ? 0.62f : 0.70f;
        int cx = x + w / 2;
        int cy = y + Math.max(0, (h - Math.round(9 * scale)) / 2);
        drawScaledCentered(g, font, text, cx, cy, scale, color);
    }

    private static void drawScaledCentered(GuiGraphics g, Font font, String text, int cx, int y, float scale, int color) {
        if (text == null || text.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(cx, y, 0);
        g.pose().scale(scale, scale, 1f);
        int tw = font.width(text);
        g.drawString(font, text, -tw / 2 + 1, 1, 0xAA000000, false);
        g.drawString(font, text, -tw / 2, 0, color, false);
        g.pose().popPose();
    }

    private static float resolveCurrentHp(Minecraft mc) {
        if (mc.player != null) return mc.player.getHealth();
        return XenoClientData.health;
    }

    private static float resolveMaxHp(Minecraft mc, DmzClientStats.Snapshot dmz) {
        float max = mc.player != null ? mc.player.getMaxHealth() : XenoClientData.maxHealth;
        if (dmz.present && dmz.maxHealth > 0f) max = Math.max(max, dmz.maxHealth);
        return max;
    }

    /** Soft pill bar (rounded ends) like XV2 HP/KI. */
    private static void drawRoundedBar(GuiGraphics g, int x, int y, int w, int h, float percent,
                                       int empty, int shine, int mid, int deep) {
        percent = clamp01(percent);
        int filled = Math.max(0, Math.round(w * percent));
        int r = Math.min(BAR_R, h / 2);

        // Black outline shell + empty trough
        fillRoundedRect(g, x - 2, y - 2, w + 4, h + 4, r + 2, 0xFF000000);
        fillRoundedRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, 0xFF000000);
        fillRoundedRect(g, x, y, w, h, r, empty);

        if (filled <= 0) return;

        // Draw fill as a rounded pill clipped to [x, x+filled]
        int fw = Math.max(r * 2, filled);
        if (fw > w) fw = w;
        fillRoundedRect(g, x, y, fw, h, r, mid);

        // If not full, square the right edge of the fill so it doesn't over-round past fill %
        if (filled < w && filled > r) {
            g.fill(x + filled - r, y, x + filled, y + h, mid);
        }

        int shineH = Math.max(2, h / 3);
        int fillRight = x + Math.min(filled, w);
        g.fill(x + 2, y + 1, fillRight, y + shineH, shine);
        g.fill(x + 2, y + h - 2, fillRight, y + h - 1, deep);
        if (filled < w && filled > 2) {
            g.fill(fillRight - 2, y + 1, fillRight, y + h - 1, shine);
        }
    }

    private static void drawStmRow(GuiGraphics g, Font font, int x, int y, int w, float percent) {
        percent = clamp01(percent);

        int labelW = 28;
        int labelH = DIAMOND + 2;
        fillRoundedRect(g, x - 1, y - 1, labelW, labelH, 4, 0xEE061018);
        fillRoundedRect(g, x, y, labelW - 2, labelH - 2, 3, 0xFF132A4A);
        g.drawString(font, "STM", x + 3, y + 1, 0xFF90CAF9, false);

        int start = x + labelW + 4;
        int gap = 2;
        int available = w - labelW - 4;
        int segW = Math.max(DIAMOND, (available - (STM_SEGMENTS - 1) * gap) / STM_SEGMENTS);
        int lit = Math.round(STM_SEGMENTS * percent);

        for (int i = 0; i < STM_SEGMENTS; i++) {
            int sx = start + i * (segW + gap);
            drawDiamond(g, sx, y, segW, DIAMOND, i < lit);
        }
    }

    private static void drawDiamond(GuiGraphics g, int x, int y, int w, int h, boolean on) {
        int cx = x + w / 2;
        int cy = y + h / 2;
        int hw = w / 2;
        int hh = h / 2;

        int mid = on ? 0xFF1E88E5 : 0xFF2C2C3A;
        int inner = on ? 0xFF64B5F6 : 0xFF3A3A4A;
        int core = on ? 0xCCFFFFFF : 0x33888899;

        for (int dy = -hh; dy <= hh; dy++) {
            float t = 1f - (Math.abs(dy) / (float) Math.max(1, hh));
            int span = Math.max(1, Math.round(hw * t));
            int color = Math.abs(dy) <= 1 ? core : (Math.abs(dy) < hh / 2 ? inner : mid);
            if (!on && Math.abs(dy) <= 1) color = mid;
            g.fill(cx - span, cy + dy, cx + span + 1, cy + dy + 1, color);
        }
    }

    private static void drawP1Badge(GuiGraphics g, Font font) {
        int bx = -2;
        int by = PORTRAIT - 16;
        int bw = 34;
        int bh = 16;
        fillRoundedRect(g, bx, by, bw, bh, 6, 0xFF0D47A1);
        fillRoundedRect(g, bx + 1, by + 1, bw - 2, bh - 2, 5, 0xFF1E88E5);
        fillRoundedRect(g, bx + 2, by + 2, bw - 4, 4, 3, 0xFF64B5F6);
        g.drawCenteredString(font, "P1", bx + bw / 2, by + 4, 0xFFFFFFFF);
    }

    private static void drawSkillOrb(GuiGraphics g) {
        int cx = 16;
        int cy = PORTRAIT + 12;
        fillCircle(g, cx, cy, 9, 0xFF0A1A30);
        fillCircle(g, cx, cy, 8, 0xFF1565C0);
        fillCircle(g, cx, cy, 6, 0xFF42A5F5);
        fillCircle(g, cx - 1, cy - 2, 2, 0xCCFFFFFF);
        g.blit(TEX, cx - 8, cy - 8, 16, 16, 70f, 80f, 16, 16, 256, 128);
    }

    private static float resolveHp(Minecraft mc, DmzClientStats.Snapshot dmz) {
        if (mc.player != null) {
            float max = mc.player.getMaxHealth();
            if (dmz.present && dmz.maxHealth > 0f) max = Math.max(max, dmz.maxHealth);
            return safePercent(mc.player.getHealth(), max);
        }
        if (XenoClientData.maxHealth > 0f) {
            return safePercent(XenoClientData.health, XenoClientData.maxHealth);
        }
        return 1f;
    }

    private static float resolveKiFallback() {
        if (XenoClientData.maxKi > 0f) return safePercent(XenoClientData.ki, XenoClientData.maxKi);
        return 1f;
    }

    private static float resolveStmFallback() {
        if (XenoClientData.maxStamina > 0f) return safePercent(XenoClientData.stamina, XenoClientData.maxStamina);
        return 1f;
    }

    private static String resolveName(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (player != null) {
            String n = player.getGameProfile().getName();
            if (n != null && !n.isEmpty()) return n;
            return player.getName().getString();
        }
        return "Player";
    }

    private static float safePercent(float value, float max) {
        if (max <= 0f) return 0f;
        return value / max;
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
