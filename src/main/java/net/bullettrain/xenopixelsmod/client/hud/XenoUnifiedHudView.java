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
 * The modern-unified HUD: the stat cluster and its attached BT3 combat cooldown rail.
 *
 * <p>Selected with {@code /xenohud renderer modernunified}. Previously these were two separate
 * overlays with independent positions and scales. Here the combat plates are laid out relative
 * to the stat cluster, so the whole composition moves and scales as one object at
 * {@code XenoHudConfig}'s x/y/scale without the obsolete rectangular backing panel.
 *
 * <p>The cooldown strip's own position and scale settings are deliberately ignored in this mode
 * — that is what "unified" means. Its content settings ({@code showLabels}, {@code showSeconds},
 * {@code showOnlyWhenActive}, and which chips are enabled) are all still honoured, because those
 * describe what to draw rather than where.
 *
 * <p>Nothing is re-derived here: the stat half delegates to {@link XenoModernHudView#renderContent}
 * and the chips come from {@link XenoCooldownHudOverlay#chipsForUnified}. This view owns only
 * their shared placement and reported editor bounds.
 */
public final class XenoUnifiedHudView {

    /** Gap between the stat cluster and the combat row. */
    private static final int SECTION_GAP = 0;
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
        int statsH = XenoModernHudView.VISIBLE_BOTTOM;
        int columns = chips.isEmpty() ? 1 : XenoCooldownHudOverlay.modernColumns(chips.size());
        int combatH = chips.isEmpty() ? 0
                : XenoCooldownHudOverlay.modernRailHeight(chips.size(), columns);

        int contentH = statsH + (combatH == 0 ? 0 : SECTION_GAP + combatH);
        int plateW = statsW;
        int plateH = contentH;
        // The plate grows with the number of wrapped chip rows, so the screen clamp and the
        // editor's drag bounds have to learn its real size from here rather than assume the
        // stat cluster's constant height.
        XenoHudConfig.reportUnifiedSize(plateW, plateH);

        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(boundsX, boundsY, 0);
        pose.scale(scale, scale, 1f);
        RenderSystem.enableBlend();

        stats.renderContent(graphics);

        if (!chips.isEmpty()) {
            pose.pushPose();
            pose.translate(0, statsH + SECTION_GAP, 0);
            XenoCooldownHudOverlay.drawModernRail(graphics, font, chips, statsW, columns);
            pose.popPose();
        }

        if (editorMode) {
            HudDraw.borderRect(graphics, 0, 0, plateW, plateH, 0x88FFFFFF, 1);
        }

        RenderSystem.disableBlend();
        pose.popPose();
    }

    /** Unscaled width of the whole unified panel, for editor bounds and screen clamping. */
    public static int width() {
        return XenoHudLayout.width();
    }
}
