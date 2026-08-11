package net.bullettrain.xenopixelsmod.client.hud;

import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.XenoCooldownHudOverlay;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshot;
import net.bullettrain.xenopixelsmod.client.config.XenoHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/**
 * The modern-unified HUD: the stat cluster and the BT3 combat cooldown strip as one panel.
 *
 * <p>Selected with {@code /xenohud renderer modernunified}. Previously these were two separate
 * overlays with independent positions, scales and backing plates, which meant a player had to
 * align them by hand and they drifted apart again on any resolution change. Here the combat
 * strip is laid out relative to the stat cluster and both sit under a single nine-sliced plate,
 * so the whole thing moves and scales as one object at {@code XenoHudConfig}'s x/y/scale.
 *
 * <p>The cooldown strip's own position and scale settings are deliberately ignored in this mode
 * — that is what "unified" means. Its content settings ({@code showLabels}, {@code showSeconds},
 * {@code showOnlyWhenActive}, and which chips are enabled) are all still honoured, because those
 * describe what to draw rather than where.
 *
 * <p>Nothing is re-derived here: the stat half delegates to {@link XenoModernHudView#renderContent}
 * and the chips come from {@link XenoCooldownHudOverlay#chipsForUnified} and are drawn by that
 * class's own modern chip painter. This view owns layout and the shared plate, nothing else.
 */
public final class XenoUnifiedHudView {

    /** Gap between the stat cluster and the combat row. */
    private static final int SECTION_GAP = 4;
    /** Inset from the shared plate's edge to the content inside it. */
    private static final int PLATE_PAD = 4;
    private static final int TITLE_H = 10;

    private final XenoModernHudView stats = new XenoModernHudView();

    private int boundsX;
    private int boundsY;
    private float scale = 1f;
    private boolean editorMode;

    public void setSnapshot(XenoHudSnapshot snapshot) {
        stats.setSnapshot(snapshot);
    }

    public void setBounds(int x, int y, float scale) {
        this.boundsX = x;
        this.boundsY = y;
        this.scale = scale <= 0f ? 1f : scale;
        stats.setBounds(0, 0, 1f);
    }

    public void setEditorMode(boolean editorMode) {
        this.editorMode = editorMode;
        // Never propagated: the stat half would draw its own editor outline inside ours, and
        // two nested boxes suggest two draggable objects when there is only one.
        stats.setEditorMode(false);
    }

    public void render(GuiGraphics graphics) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<XenoCooldownHudOverlay.Chip> chips = XenoCooldownHudOverlay.chipsForUnified(editorMode);

        int statsW = XenoHudLayout.width();
        int statsH = XenoHudLayout.height();
        int chipW = XenoCooldownHudOverlay.CHIP_W;
        int chipH = XenoCooldownHudOverlay.CHIP_H;
        int gap = XenoCooldownHudOverlay.GAP;

        // The combat row wraps rather than running off the plate: at the stat cluster's width a
        // full seven-chip row would otherwise overhang it by a wide margin.
        int perRow = Math.max(1, (statsW + gap) / (chipW + gap));
        int rows = chips.isEmpty() ? 0 : (chips.size() + perRow - 1) / perRow;
        int combatH = rows == 0 ? 0 : TITLE_H + rows * chipH + (rows - 1) * gap;

        int contentH = statsH + (combatH == 0 ? 0 : SECTION_GAP + combatH);
        int plateW = statsW + PLATE_PAD * 2;
        int plateH = contentH + PLATE_PAD * 2;
        // The plate grows with the number of wrapped chip rows, so the screen clamp and the
        // editor's drag bounds have to learn its real size from here rather than assume the
        // stat cluster's constant height.
        XenoHudConfig.reportUnifiedSize(plateW, plateH);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(boundsX, boundsY, 0);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        // One plate for both halves — the point of the mode.
        HudDraw.blitNineSlice(graphics, XenoHudTextures.HUD_ATLAS, XenoHudLayout.PANEL,
                0, 0, plateW, plateH, XenoHudLayout.PANEL_CORNER);

        pose.pushPose();
        pose.translate(PLATE_PAD, PLATE_PAD, 0);
        stats.renderContent(graphics);
        pose.popPose();

        if (rows > 0) {
            pose.pushPose();
            pose.translate(PLATE_PAD, PLATE_PAD + statsH + SECTION_GAP, 0);
            drawCombat(graphics, font, chips, perRow, statsW);
            pose.popPose();
        }

        if (editorMode) {
            HudDraw.borderRect(graphics, 0, 0, plateW, plateH, 0x88FFFFFF, 1);
        }

        RenderSystem.disableBlend();
        pose.popPose();
    }

    private void drawCombat(GuiGraphics graphics, Font font,
                            List<XenoCooldownHudOverlay.Chip> chips, int perRow, int width) {
        // Divider plus label, so the combat half is legibly its own section of the one panel
        // rather than chips floating under the stamina meter.
        HudDraw.fillRect(graphics, 0, 0, width, 1, 0x5542A5F5);
        graphics.drawString(font, "COMBAT", 1, 2, 0xFF90CAF9, true);

        int chipW = XenoCooldownHudOverlay.CHIP_W;
        int chipH = XenoCooldownHudOverlay.CHIP_H;
        int gap = XenoCooldownHudOverlay.GAP;
        for (int i = 0; i < chips.size(); i++) {
            int row = i / perRow;
            int col = i % perRow;
            XenoCooldownHudOverlay.drawModernChip(graphics, font,
                    col * (chipW + gap), TITLE_H + row * (chipH + gap), chips.get(i));
        }
    }

    /** Unscaled width of the whole unified panel, for editor bounds and screen clamping. */
    public static int width() {
        return XenoHudLayout.width() + PLATE_PAD * 2;
    }
}
