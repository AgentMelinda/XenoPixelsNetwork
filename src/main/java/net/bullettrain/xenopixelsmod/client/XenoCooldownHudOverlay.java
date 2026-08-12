package net.bullettrain.xenopixelsmod.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import net.bullettrain.xenopixelsmod.client.combat.Bt3CombatClient;
import net.bullettrain.xenopixelsmod.client.config.XenoClientConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoCooldownHudConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.bullettrain.xenopixelsmod.client.hud.HudDraw;
import net.bullettrain.xenopixelsmod.network.Bt3CombatPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * BT3 combat cooldown strip — polished XV2 glass chips with a soft parallelogram
 * slant (matches main HUD gauges), neon accents, and gradient meters.
 */
@OnlyIn(Dist.CLIENT)
public class XenoCooldownHudOverlay {

    public static final int CHIP_W = 52;
    public static final int CHIP_H = 34;
    public static final int GAP = 4;
    public static final int MODERN_CHIP_W = 82;
    public static final int MODERN_CHIP_H = 25;
    public static final int MODERN_GAP = 3;
    public static final int MODERN_TITLE_H = 0;
    public static final int MODERN_MAX_COLUMNS = 4;
    private static final int PAD_X = 8;
    private static final int PAD_Y = 6;
    private static final int TITLE_H = 12;
    private static final int METER_H = 4;

    /** Soft italic skew — enough for XV2 feel, not enough to smear text. */
    private static final int PANEL_SKEW = 6;
    private static final int CHIP_SKEW = 4;
    private static final int METER_SKEW = 3;

    private static final ResourceLocation MODERN_READY = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_cooldown_plate_ready.png");
    private static final ResourceLocation MODERN_ACTIVE = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_cooldown_plate_active.png");
    private static final ResourceLocation MODERN_DISABLED = ResourceLocation.fromNamespaceAndPath(
            XenoPixelsMod.MOD_ID, "textures/gui/xeno_cooldown_plate_disabled.png");

    private static int lastX, lastY, lastW, lastH;

    private static int panelSkew() {
        return XenoCooldownHudConfig.squareShape ? 0 : PANEL_SKEW;
    }

    private static int chipSkew() {
        return XenoCooldownHudConfig.squareShape ? 0 : CHIP_SKEW;
    }

    private static int meterSkew() {
        return XenoCooldownHudConfig.squareShape ? 0 : METER_SKEW;
    }

    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        // The unified renderer draws this strip inside the main HUD's panel; drawing it here
        // as well would put a second copy on screen at its own position.
        if (XenoHudConfig.unifiedActive()) return;
        if (!enabledNow()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        draw(g, g.guiWidth(), g.guiHeight(), false);
    }

    private static boolean enabledNow() {
        if (!XenoClientConfig.cooldownHudEnabled || !XenoCooldownHudConfig.visible) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.getDebugOverlay().showDebugScreen()) return false;
        return XenoClientConfig.bt3CombatClient && XenoServerClientState.combat();
    }

    /**
     * Chips for the unified renderer, or an empty list when the strip should not be shown.
     *
     * <p>Centralising the gate here keeps one answer to "is the cooldown strip on right now",
     * including the {@code showOnlyWhenActive} rule, rather than duplicating that logic in the
     * unified view where it could drift.
     */
    public static List<Chip> chipsForUnified(boolean editing) {
        if (!editing && !enabledNow()) return List.of();
        List<Chip> chips = buildChips(editing);
        if (chips.isEmpty()) return List.of();
        if (XenoCooldownHudConfig.showOnlyWhenActive && !editing) {
            boolean any = false;
            for (Chip c : chips) {
                if (c.busy) {
                    any = true;
                    break;
                }
            }
            if (!any) return List.of();
        }
        return chips;
    }

    public static void renderEditorPreview(GuiGraphics g, int screenWidth, int screenHeight) {
        draw(g, screenWidth, screenHeight, true);
    }

    public static int[] bounds() {
        return new int[]{lastX, lastY, lastW, lastH};
    }

    private static void draw(GuiGraphics g, int screenWidth, int screenHeight, boolean editing) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        List<Chip> chips = buildChips(editing);
        if (chips.isEmpty()) return;

        if (XenoCooldownHudConfig.showOnlyWhenActive && !editing) {
            boolean any = false;
            for (Chip c : chips) {
                if (c.busy) {
                    any = true;
                    break;
                }
            }
            if (!any) return;
        }

        if (!XenoHudConfig.legacyHudRenderer) {
            drawModernStandalone(g, screenWidth, screenHeight, font, chips, editing);
            return;
        }

        float scale = XenoCooldownHudConfig.scale;
        int n = chips.size();
        boolean horiz = XenoCooldownHudConfig.horizontal;
        int pSkew = panelSkew();
        int contentW = horiz ? n * CHIP_W + (n - 1) * GAP : CHIP_W;
        int contentH = horiz ? CHIP_H : n * CHIP_H + (n - 1) * GAP;
        int panelW = contentW + PAD_X * 2 + pSkew;
        int panelH = contentH + PAD_Y * 2 + TITLE_H;

        int baseX = Math.max(0, Math.min(Math.max(0, screenWidth - Math.round(panelW * scale)), XenoCooldownHudConfig.x));
        int baseY = Math.max(0, Math.min(Math.max(0, screenHeight - Math.round(panelH * scale)), XenoCooldownHudConfig.y));

        lastX = baseX;
        lastY = baseY;
        lastW = Math.round(panelW * scale);
        lastH = Math.round(panelH * scale);

        g.pose().pushPose();
        g.pose().translate(baseX, baseY, 0);
        g.pose().scale(scale, scale, 1f);

        // --- Tech-HUD palette plate (navy glass + cyan rail, same family as KI technique bar) ---
        // Outer shadow
        fillPara(g, -3, -3, panelW + 6, panelH + 6, pSkew > 0 ? pSkew + 1 : 0, 0xCC050510);
        // Body (match tech: 0xEE0A1428)
        int a = Math.round(Math.max(0.55f, XenoCooldownHudConfig.panelOpacity) * 255f) & 0xFF;
        fillPara(g, 0, 0, panelW, panelH, pSkew, (a << 24) | 0x0A1428);
        fillPara(g, 1, 1, panelW - 2, panelH - 2, Math.max(0, pSkew - 1), 0x22050A14);
        // Cyan left accent rail (tech ALT bar language)
        fillPara(g, 0, 2, 3, panelH - 4, 0, 0xFF42A5F5);
        // Soft right edge
        fillPara(g, panelW - Math.max(1, pSkew) - 2, 2, 2, panelH - 4, 0, 0x6642A5F5);
        drawParaBorder(g, 0, 0, panelW, panelH, pSkew, 0x5542A5F5, 1);
        // top sheen
        fillPara(g, 4, 1, panelW - 8 - pSkew / 2, 1, 0, 0x33FFFFFF);

        // --- title row ---
        g.drawString(font, "COMBAT", PAD_X + 2, 3, 0xFF90CAF9, true);
        int pipX = PAD_X + font.width("COMBAT") + 8;
        for (int i = 0; i < Math.min(5, chips.size()); i++) {
            Chip c = chips.get(i);
            int col = !c.enabled ? 0x553A4550 : (c.busy ? (c.accent & 0x00FFFFFF) | 0xEE000000 : 0xAA66BB6A);
            fillPara(g, pipX + i * 5, 5, 3, 3, 0, col);
        }
        fillPara(g, PAD_X + 2, TITLE_H - 1, Math.min(contentW, 48), 1, 0, 0x6642A5F5);

        int originY = PAD_Y + TITLE_H;
        for (int i = 0; i < chips.size(); i++) {
            int cx = horiz ? PAD_X + i * (CHIP_W + GAP) : PAD_X;
            int cy = horiz ? originY : originY + i * (CHIP_H + GAP);
            drawChip(g, font, cx, cy, chips.get(i));
        }

        g.pose().popPose();
    }

    private static void drawModernStandalone(GuiGraphics g, int screenWidth, int screenHeight,
                                             Font font, List<Chip> chips, boolean editing) {
        int columns = XenoCooldownHudConfig.horizontal ? modernColumns(chips.size()) : 1;
        int railW = modernRailWidth(chips.size(), columns);
        int railH = modernRailHeight(chips.size(), columns);
        float scale = XenoCooldownHudConfig.scale;
        int baseX = Math.max(0, Math.min(Math.max(0, screenWidth - Math.round(railW * scale)),
                XenoCooldownHudConfig.x));
        int baseY = Math.max(0, Math.min(Math.max(0, screenHeight - Math.round(railH * scale)),
                XenoCooldownHudConfig.y));
        lastX = baseX;
        lastY = baseY;
        lastW = Math.round(railW * scale);
        lastH = Math.round(railH * scale);

        g.pose().pushPose();
        g.pose().translate(baseX, baseY, 0);
        g.pose().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        drawModernRail(g, font, chips, railW, columns);
        if (editing) g.renderOutline(0, 0, railW, railH, 0xFF42A5F5);
        RenderSystem.disableBlend();
        g.pose().popPose();
    }

    public static int modernColumns(int count) {
        return Math.max(1, Math.min(MODERN_MAX_COLUMNS, count));
    }

    public static int modernRailWidth(int count, int columns) {
        int used = Math.max(1, Math.min(Math.max(1, count), Math.max(1, columns)));
        return used * MODERN_CHIP_W + (used - 1) * MODERN_GAP;
    }

    public static int modernRailHeight(int count, int columns) {
        int rows = Math.max(1, (Math.max(1, count) + Math.max(1, columns) - 1) / Math.max(1, columns));
        return MODERN_TITLE_H + rows * MODERN_CHIP_H + (rows - 1) * MODERN_GAP;
    }

    /** Draws balanced rows of supplied XV plates inside the caller's available width. */
    public static void drawModernRail(GuiGraphics g, Font font, List<Chip> chips,
                                      int availableWidth, int columns) {
        if (chips.isEmpty()) return;
        columns = Math.max(1, Math.min(columns, chips.size()));
        int gridLeft = (availableWidth - modernRailWidth(chips.size(), columns)) / 2;
        int titleW = font.width("COMBAT");
        if (gridLeft >= titleW + 4) {
            g.drawString(font, "COMBAT", 2, 8, 0xFF90CAF9, true);
        }
        int rows = (chips.size() + columns - 1) / columns;
        for (int row = 0; row < rows; row++) {
            int first = row * columns;
            int rowCount = Math.min(columns, chips.size() - first);
            int rowW = rowCount * MODERN_CHIP_W + (rowCount - 1) * MODERN_GAP;
            int startX = (availableWidth - rowW) / 2;
            int y = MODERN_TITLE_H + row * (MODERN_CHIP_H + MODERN_GAP);
            for (int col = 0; col < rowCount; col++) {
                drawModernChip(g, font, startX + col * (MODERN_CHIP_W + MODERN_GAP), y,
                        chips.get(first + col));
            }
        }
    }

    public static void drawModernChip(GuiGraphics g, Font font, int x, int y, Chip chip) {
        ResourceLocation plate;
        int sourceW;
        int sourceH;
        if (!chip.enabled) {
            plate = MODERN_DISABLED;
            sourceW = 278;
            sourceH = 78;
        } else if (chip.busy) {
            plate = MODERN_ACTIVE;
            sourceW = 320;
            sourceH = 66;
        } else {
            plate = MODERN_READY;
            sourceW = 320;
            sourceH = 64;
        }
        if (!chip.enabled) g.setColor(0.58f, 0.58f, 0.58f, 0.72f);
        g.blit(plate, x, y, MODERN_CHIP_W, MODERN_CHIP_H, 0f, 0f,
                sourceW, sourceH, sourceW, sourceH);
        g.setColor(1f, 1f, 1f, 1f);

        int textColor = chip.enabled ? 0xFFF4F8FF : 0xFF68727D;
        int nameX = x + 8;
        if (XenoCooldownHudConfig.showLabels && !chip.key.isEmpty()) {
            String key = font.plainSubstrByWidth(chip.key, 18);
            g.drawString(font, key, x + 8, y + 6, chip.enabled ? 0xFFB6DDF1 : 0xFF59636D, false);
            nameX = x + 28;
        }
        String status = chip.meterMode == MeterMode.COMBO ? "x" + Math.max(0, chip.comboStep)
                : XenoCooldownHudConfig.showSeconds ? chip.timeText : "";
        int statusW = status.isEmpty() ? 0 : font.width(status) + 3;
        int nameWidth = Math.max(8, x + MODERN_CHIP_W - 7 - statusW - nameX);
        String name = font.width(chip.name) <= nameWidth ? chip.name
                : font.width(chip.shortName) <= nameWidth ? chip.shortName
                : font.plainSubstrByWidth(chip.shortName, nameWidth);
        g.drawString(font, name, nameX, y + 6, textColor, false);

        if (!status.isEmpty()) {
            g.drawString(font, status, x + MODERN_CHIP_W - 7 - font.width(status), y + 6,
                    chip.enabled ? 0xFFFFE0B2 : 0xFF59636D, false);
        }

        int meterX = x + 10;
        int meterY = y + 17;
        int meterW = MODERN_CHIP_W - 20;
        if (chip.meterMode == MeterMode.COMBO) {
            drawContainedMeter(g, meterX, meterY, meterW, 3,
                    chip.comboStep <= 0 ? 0f : Math.min(1f, chip.comboStep / 5f),
                    chip.accent, chip.enabled);
        } else {
            float value = chip.busy ? chip.fraction : chip.enabled ? 1f : 0f;
            int color = chip.meterMode == MeterMode.COOLDOWN ? 0xFF55C7FF : chip.accent;
            drawContainedMeter(g, meterX, meterY, meterW, 3, value, color, chip.enabled);
        }
    }

    private static void drawContainedMeter(GuiGraphics g, int x, int y, int w, int h,
                                           float fraction, int fill, boolean enabled) {
        int skew = 2;
        int rowW = Math.max(1, w - skew);
        fraction = Math.max(0f, Math.min(1f, fraction));
        HudDraw.fillPara(g, x, y, rowW, h, skew, enabled ? 0xCC071624 : 0xAA15191D);
        int filled = Math.round(rowW * fraction);
        if (filled > 0) {
            int fillSkew = Math.min(skew, filled);
            HudDraw.fillPara(g, x, y, filled, h, fillSkew,
                    enabled ? (fill | 0xFF000000) : 0xFF343A40);
            HudDraw.fillPara(g, x, y, filled, 1, fillSkew, 0x88FFFFFF);
        }
        HudDraw.borderPara(g, x, y, rowW, h, skew,
                enabled ? 0xBB90CAF9 : 0x88505A64, 1);
    }

    private static void drawChip(GuiGraphics g, Font font, int x, int y, Chip chip) {
        int cSkew = chipSkew();
        int mSkew = meterSkew();

        // Static busy halo (no per-frame sin pulse — that alone cost a lot of fills)
        if (chip.busy && chip.enabled) {
            fillPara(g, x - 1, y - 1, CHIP_W + 2, CHIP_H + 2, cSkew > 0 ? cSkew : 0,
                    (chip.accent & 0x00FFFFFF) | 0x40000000);
        }

        // chip body — tech slot colors (same family as technique rows)
        int body;
        if (!chip.enabled) {
            body = 0x66101828;
        } else if (chip.busy) {
            body = 0xCC1E4A7A;
        } else {
            body = 0xAA122038;
        }
        fillPara(g, x, y, CHIP_W, CHIP_H, cSkew, body);

        // accent border (chip color)
        int border = chip.enabled
                ? ((chip.accent & 0x00FFFFFF) | 0x99000000)
                : 0x553A4550;
        drawParaBorder(g, x, y, CHIP_W, CHIP_H, cSkew, border, 1);

        // glass top sheen strip
        fillPara(g, x + 2, y + 1, CHIP_W - 6, 1, 0, 0x28FFFFFF);

        // left accent rail (always square — no drift)
        int rail = chip.enabled ? (chip.accent | 0xFF000000) : 0xFF3A4550;
        fillPara(g, x + 2, y + 3, 2, CHIP_H - 6, 0, rail);

        // Layout (top → bottom, all centered):
        //   [ A/D ]   key badge
        //   Vanish    name under badge
        //   ██████    meter / combo counter
        int contentW = CHIP_W - 10 - cSkew;

        // key badge — square, centered on chip
        int badgeBottomY = y + 2;
        if (XenoCooldownHudConfig.showLabels && chip.key != null && !chip.key.isEmpty()) {
            int kw = font.width(chip.key);
            int bw = kw + 6;
            int bh = 9;
            int bx = x + (CHIP_W - bw) / 2;
            int by = y + 2;
            fillPara(g, bx, by, bw, bh, 0, 0xCC000000);
            drawParaBorder(g, bx, by, bw, bh, 0, 0x44FFFFFF, 1);
            g.drawString(font, chip.key, bx + 3, by + 1, chip.enabled ? 0xFFB0BEC5 : 0xFF5A6570, false);
            badgeBottomY = by + bh;
        }

        // name — centered, always under the key badge Y
        int textColor = chip.enabled ? 0xFFF0F4FA : 0xFF5A6570;
        String name = font.width(chip.name) > contentW ? chip.shortName : chip.name;
        int nameW = font.width(name);
        int nameX = x + (CHIP_W - nameW) / 2;
        int nameY = badgeBottomY + 2;
        g.drawString(font, name, nameX, nameY, textColor, false);

        // --- meter / counter at bottom ---
        int mx = x + 6;
        int my = y + CHIP_H - METER_H - 3;
        int mw = CHIP_W - 12 - cSkew;

        if (chip.meterMode == MeterMode.COMBO) {
            // Hit COUNTER centered under the name
            int step = Math.max(0, chip.comboStep);
            String counter = "x" + step;
            int cw = font.width(counter);
            int cxText = x + (CHIP_W - cw) / 2;
            int cyText = my - 2;
            fillPara(g, cxText - 3, cyText - 1, cw + 6, 10, 0, 0xCC000000);
            drawParaBorder(g, cxText - 3, cyText - 1, cw + 6, 10, 0,
                    step > 0 ? 0xCCEF5350 : 0x553A4550, 1);
            int col = !chip.enabled ? 0xFF5A6570 : (step > 0 ? 0xFFFFCDD2 : 0xFF78909C);
            g.drawString(font, counter, cxText, cyText, col, false);
        } else {
            fillPara(g, mx, my, mw, METER_H, mSkew, 0xCC000000);
            drawParaBorder(g, mx, my, mw, METER_H, mSkew, 0x22000000, 1);

            if (chip.meterMode == MeterMode.COOLDOWN && chip.fraction > 0.001f) {
                int fill = Math.max(1, Math.round(mw * chip.fraction));
                drawGradientMeter(g, mx, my, fill, METER_H, mSkew, 0xFF1565C0, 0xFF81D4FA);
                // seconds float just above meter, centered
                if (XenoCooldownHudConfig.showSeconds && !chip.timeText.isEmpty()) {
                    int tw = font.width(chip.timeText);
                    g.drawString(font, chip.timeText, x + (CHIP_W - tw) / 2, my - 9, 0xFFE1F5FE, false);
                }
            } else if (chip.meterMode == MeterMode.CHARGE && chip.fraction > 0.001f) {
                int fill = Math.max(1, Math.round(mw * chip.fraction));
                int c1 = chip.accent | 0xFF000000;
                // Solid charge fill (gradient optional strip count is already cheap)
                fillPara(g, mx, my, fill, METER_H, mSkew, c1);
                if (fill > 3) {
                    fillPara(g, mx + fill - 2, my, 2, METER_H, 0, 0xAAFFFFFF);
                }
            } else if (chip.enabled && !chip.busy) {
                // Ready tick — solid, no sin wave
                fillPara(g, mx, my, mw, METER_H, mSkew, (chip.accent & 0x00FFFFFF) | 0x88000000);
            } else if (!chip.enabled) {
                fillPara(g, mx, my, mw, METER_H, mSkew, 0x443A4550);
            }
        }
    }

    /** Horizontal gradient with few strips (was per-pixel — FPS killer). */
    private static void drawGradientMeter(GuiGraphics g, int x, int y, int w, int h, int skew, int c1, int c2) {
        HudDraw.fillGradientH(g, x, y, w, h, skew, c1, c2);
    }

    private static void fillPara(GuiGraphics g, int x, int y, int w, int h, int skew, int color) {
        HudDraw.fillPara(g, x, y, w, h, skew, color);
    }

    private static void drawParaBorder(GuiGraphics g, int x, int y, int w, int h, int skew, int color, int t) {
        HudDraw.borderPara(g, x, y, w, h, skew, color, t);
    }

    /**
     * The chip strip for the current tick.
     *
     * <p>Rebuilt once per client tick, not once per frame. The strip is ten-odd chips and the old
     * shape allocated a list plus a {@code Chip} for each of them on every single frame, so at
     * 200fps this churned roughly two thousand short-lived objects a second to describe state that
     * only changes twenty times a second. Chips come from a pool and are mutated in place.
     *
     * <p>The returned list is shared and read-only to callers. That is safe because the standalone
     * overlay stands down whenever {@code XenoHudConfig.unifiedActive()}, so only one consumer ever
     * reads it in a given frame.
     */
    public static List<Chip> buildChips(boolean editing) {
        Minecraft chipMc = Minecraft.getInstance();
        long now = chipMc.level == null ? Long.MIN_VALUE : chipMc.level.getGameTime();
        if (now != Long.MIN_VALUE && now == chipsGameTime && editing == chipsEditing) return CHIPS;
        chipsGameTime = now;
        chipsEditing = editing;
        CHIPS.clear();
        poolCursor = 0;

        List<Chip> list = CHIPS;
        float moveFrac = editing ? 0.45f : Bt3CombatClient.getMoveCooldownFraction();
        boolean moveCd = editing || Bt3CombatClient.isMoveOnCooldown();
        Bt3CombatPacket.Action last = Bt3CombatClient.getLastMoveAction();
        float secs = editing ? 0.4f : (Bt3CombatClient.getMoveCooldownTicks() / 20f);
        String time = formatTime(secs);

        if (XenoCooldownHudConfig.showVanish) {
            list.add(chip("Vanish", "Van", "A/D", 0xFF42A5F5,
                    moveCd, moveFrac, time, MeterMode.COOLDOWN, 0,
                    XenoClientConfig.bt3VanishClient && XenoServerClientState.vanish(),
                    last == Bt3CombatPacket.Action.VANISH && moveCd));
        }
        if (XenoCooldownHudConfig.showChase) {
            list.add(chip("Chase", "Chs", "W", 0xFFFF8A65,
                    moveCd, moveFrac, time, MeterMode.COOLDOWN, 0,
                    XenoClientConfig.bt3ChaseDashClient && XenoServerClientState.chase(),
                    last == Bt3CombatPacket.Action.CHASE_DASH && moveCd));
        }
        if (XenoCooldownHudConfig.showBackstep) {
            list.add(chip("Back", "Bck", "S", 0xFF90A4AE,
                    moveCd, moveFrac, time, MeterMode.COOLDOWN, 0,
                    XenoClientConfig.bt3BackstepClient && XenoServerClientState.backstep(),
                    last == Bt3CombatPacket.Action.BACKSTEP && moveCd));
        }
        if (XenoCooldownHudConfig.showCombo) {
            float comboFrac = editing ? 0.65f : Bt3CombatClient.getComboWindowFraction();
            int step = editing ? 3 : Bt3CombatClient.getComboStep();
            // Highlight whenever a combo is active (step > 0), not just while window meter is full
            boolean busy = editing || step > 0 || comboFrac > 0f;
            list.add(chip("Combo", "Cmb", "ATK", 0xFFEF5350,
                    busy, comboFrac, "",
                    MeterMode.COMBO, step,
                    XenoClientConfig.bt3ComboClient && XenoServerClientState.combo(),
                    step > 0));
        }
        // Guard chip
        {
            boolean guarding = editing || Bt3CombatClient.isGuarding();
            boolean counter = !editing && Bt3CombatClient.isCounterWindowFlash();
            list.add(chip(counter ? "Counter!" : "Guard", counter ? "Ctr" : "Grd", "B",
                    counter ? 0xFF80DEEA : 0xFFB0BEC5,
                    guarding || counter, guarding ? 1f : (counter ? 0.85f : 0f), "",
                    MeterMode.CHARGE, 0,
                    XenoClientConfig.bt3GuardClient && XenoServerClientState.guard(),
                    guarding || counter));
        }
        // Z-Burst / Ki cancel chips (mid-combo tools)
        {
            int zb = editing ? 0 : Bt3CombatClient.getZBurstCd();
            float zf = editing ? 0f : (zb > 0 ? zb / 12f : 0f);
            list.add(chip("Z-Burst", "ZB", "V", 0xFFCE93D8,
                    zb > 0 || (moveCd && last == Bt3CombatPacket.Action.Z_BURST),
                    zf > 0 ? zf : moveFrac, "",
                    MeterMode.COOLDOWN, 0,
                    XenoClientConfig.bt3ZBurstClient && XenoServerClientState.zBurst(),
                    last == Bt3CombatPacket.Action.Z_BURST && moveCd));
            int kb = editing ? 0 : Bt3CombatClient.getKiBlastCd();
            float kf = editing ? 0f : (kb > 0 ? kb / 10f : 0f);
            list.add(chip("Ki Cancel", "KiC", "C", 0xFF4FC3F7,
                    kb > 0 || (moveCd && last == Bt3CombatPacket.Action.KI_BLAST_CANCEL),
                    kf > 0 ? kf : moveFrac, "",
                    MeterMode.COOLDOWN, 0,
                    XenoClientConfig.bt3KiBlastCancelClient && XenoServerClientState.kiBlastCancel(),
                    last == Bt3CombatPacket.Action.KI_BLAST_CANCEL && moveCd));
        }

        if (XenoCooldownHudConfig.showCharge) {
            boolean charging = editing || Bt3CombatClient.isCharging();
            float charge = editing ? 0.7f : Bt3CombatClient.getChargeProgress();
            float meter;
            MeterMode mode;
            boolean busy;

            boolean kick = Bt3CombatClient.isKickCharge()
                    || (!charging && last == Bt3CombatPacket.Action.CHARGE_KICK);
            boolean dragon = Bt3CombatClient.isDragonCharge()
                    || (!charging && last == Bt3CombatPacket.Action.DRAGON_DASH);
            boolean fist = (Bt3CombatClient.isCharging() && !kick && !dragon)
                    || (!charging && last == Bt3CombatPacket.Action.CHARGE_FIST);

            String name;
            String shortN;
            String key;
            int accent;
            if (editing || kick) {
                name = "Kick";
                shortN = "Kik";
                key = "MMB";
                accent = 0xFFF48FB1;
            } else if (dragon) {
                name = "Dragon";
                shortN = "Drg";
                key = "N";
                accent = 0xFFFFD54F;
            } else if (fist) {
                name = "Fist";
                shortN = "Fst";
                key = "R";
                accent = 0xFFFFB74D;
            } else {
                name = "Charge";
                shortN = "Chg";
                key = "R/M";
                accent = 0xFFFFB74D;
            }

            if (charging) {
                meter = charge;
                mode = MeterMode.CHARGE;
                busy = true;
            } else if (moveCd && isChargeAction(last)) {
                meter = moveFrac;
                mode = MeterMode.COOLDOWN;
                busy = true;
            } else {
                meter = 0f;
                mode = MeterMode.CHARGE;
                busy = false;
            }

            boolean enabled = (XenoClientConfig.bt3ChargeAttackClient && XenoServerClientState.chargeAttack())
                    || (XenoClientConfig.bt3DragonDashClient && XenoServerClientState.dragonDash());
            list.add(chip(name, shortN, key, accent, busy, meter, time, mode, 0, enabled, busy));
        }
        return list;
    }

    /** Chips for the current tick, and the pool they are drawn from. */
    private static final List<Chip> CHIPS = new ArrayList<>();
    private static final List<Chip> CHIP_POOL = new ArrayList<>();
    private static int poolCursor;
    private static long chipsGameTime = Long.MIN_VALUE;
    private static boolean chipsEditing;

    private static Chip nextPooled() {
        if (poolCursor < CHIP_POOL.size()) return CHIP_POOL.get(poolCursor++);
        Chip fresh = new Chip();
        CHIP_POOL.add(fresh);
        poolCursor++;
        return fresh;
    }

    private static boolean isChargeAction(Bt3CombatPacket.Action a) {
        return a == Bt3CombatPacket.Action.CHARGE_FIST
                || a == Bt3CombatPacket.Action.CHARGE_KICK
                || a == Bt3CombatPacket.Action.DRAGON_DASH;
    }

    private static Chip chip(
            String name, String shortName, String key, int accent,
            boolean busy, float fraction, String timeText, MeterMode mode, int comboStep,
            boolean enabled, boolean highlight) {
        Chip c = nextPooled();
        c.name = name;
        c.shortName = shortName;
        c.key = key;
        c.accent = accent;
        c.busy = busy || highlight;
        c.fraction = Math.max(0f, Math.min(1f, fraction));
        c.timeText = timeText;
        c.meterMode = mode;
        c.comboStep = comboStep;
        c.enabled = enabled;
        return c;
    }

    /**
     * Sub-second times as "0.4", whole seconds otherwise.
     *
     * <p>Integer math rather than {@code String.format}: this runs every frame for the whole chip
     * strip while any cooldown is live, and {@code String.format} allocates a {@code Formatter}
     * and re-parses its pattern on each call.
     */
    private static String formatTime(float seconds) {
        if (seconds <= 0f) return "";
        if (seconds < 1f) {
            int tenths = Math.max(0, Math.min(9, (int) (seconds * 10f)));
            return "0." + tenths;
        }
        return String.valueOf(Math.max(1, Math.round(seconds)));
    }

    public enum MeterMode {
        COOLDOWN, CHARGE, COMBO
    }

    public static final class Chip {
        String name = "";
        String shortName = "";
        String key = "";
        int accent = 0xFF42A5F5;
        boolean busy;
        float fraction;
        String timeText = "";
        MeterMode meterMode = MeterMode.COOLDOWN;
        int comboStep;
        boolean enabled = true;
    }
}
