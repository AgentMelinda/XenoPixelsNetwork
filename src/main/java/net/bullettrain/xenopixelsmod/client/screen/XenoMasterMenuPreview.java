package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Chrome-only preview of the skill-master interact menu for {@link XenoElementsEditScreen}.
 *
 * <p>Mirrors {@link DmzFormTrainerScreen} sprite placement. Does not construct that ScaledScreen.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoMasterMenuPreview {

    static final int PANEL_GAP = 4;
    static final int ROW_INSET = 10;
    static final int ROW_STEP = 19;
    private static final int PANEL_INSET = 7;
    private static final int PANEL_FILL = 0xFF0B1220;

    private XenoMasterMenuPreview() {
    }

    public static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        Layout layout = layout(width, height);
        int left = Math.min(layout.nameplateX, layout.bodyX);
        int top = layout.nameplateY;
        int right = Math.max(layout.nameplateX + XenoDmzHdAtlas.NAMEPLATE.width(),
                layout.formsX + XenoDmzHdAtlas.STATS_PANEL.width());
        int bottom = layout.panelsY + XenoDmzHdAtlas.STATS_PANEL.height();
        return new int[]{left, top, right - left, bottom - top};
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Layout layout = layout(screenWidth, screenHeight);
        int nameplateX = layout.nameplateX + XenoMasterMenuConfig.partX(Part.NAMEPLATE);
        int nameplateY = layout.nameplateY + XenoMasterMenuConfig.partY(Part.NAMEPLATE);
        int bodyX = layout.bodyX + XenoMasterMenuConfig.partX(Part.MASTER_PANEL);
        int formsX = layout.formsX + XenoMasterMenuConfig.partX(Part.FORMS_PANEL);
        int panelsY = layout.panelsY;

        blit(graphics, XenoDmzHdAtlas.NAMEPLATE, nameplateX, nameplateY);
        blit(graphics, XenoDmzHdAtlas.INFO_PANEL, bodyX, panelsY);
        blit(graphics, XenoDmzHdAtlas.STATS_PANEL, formsX, panelsY);
        fillInterior(graphics, bodyX, panelsY, XenoDmzHdAtlas.INFO_PANEL);
        fillInterior(graphics, formsX, panelsY, XenoDmzHdAtlas.STATS_PANEL);

        header(graphics, bodyX, panelsY, XenoDmzHdAtlas.INFO_HEADER, XenoDmzHdAtlas.KANJI_CIRCLE,
                XenoDmzHdAtlas.INFO_PANEL.width(), Part.MASTER_HEADER, Part.MASTER_HEADER_ICON,
                Part.MASTER_HEADER_LABEL, "MASTER");
        header(graphics, formsX, panelsY, XenoDmzHdAtlas.STATS_HEADER, XenoDmzHdAtlas.STATS_BARS_ICON,
                XenoDmzHdAtlas.STATS_PANEL.width(), Part.FORMS_HEADER, Part.FORMS_HEADER_ICON,
                Part.FORMS_HEADER_LABEL, "FORMS");

        int rowX = formsX + ROW_INSET + XenoMasterMenuConfig.partX(Part.FORM_ROWS);
        int rowY = panelsY + 36 + XenoMasterMenuConfig.partY(Part.FORM_ROWS);
        for (int i = 0; i < 3; i++) {
            blit(graphics, XenoDmzHdAtlas.STAT_ROW, rowX, rowY);
            blit(graphics, XenoDmzHdAtlas.ORB_BLUE,
                    rowX + 4 + XenoMasterMenuConfig.partX(Part.FORM_ICON),
                    rowY + 3 + XenoMasterMenuConfig.partY(Part.FORM_ICON));
            rowY += ROW_STEP;
        }

        int boxX = bodyX + 10 + XenoMasterMenuConfig.partX(Part.CLOSE_BOX);
        int boxY = panelsY + XenoDmzHdAtlas.INFO_PANEL.height() - XenoDmzHdAtlas.BOTTOM_BOX.height() - 6
                + XenoMasterMenuConfig.partY(Part.CLOSE_BOX);
        blitPart(graphics, XenoDmzHdAtlas.BOTTOM_BOX, boxX, boxY, Part.CLOSE_BOX);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.font != null) {
            int centre = boxX + Math.round(XenoDmzHdAtlas.BOTTOM_BOX.width()
                    * XenoMasterMenuConfig.partScale(Part.CLOSE_BOX) / 2.0f);
            graphics.drawCenteredString(mc.font, "Close",
                    centre + XenoMasterMenuConfig.partX(Part.CLOSE_LABEL),
                    boxY + 12 + XenoMasterMenuConfig.partY(Part.CLOSE_LABEL),
                    XenoMasterMenuConfig.partColor(Part.CLOSE_LABEL));
            graphics.drawCenteredString(mc.font, "ESC",
                    centre + XenoMasterMenuConfig.partX(Part.CLOSE_HINT),
                    boxY + 26 + XenoMasterMenuConfig.partY(Part.CLOSE_HINT),
                    XenoMasterMenuConfig.partColor(Part.CLOSE_HINT));
        }
    }

    static Layout layout(int uiWidth, int uiHeight) {
        int bodyWidth = XenoDmzHdAtlas.INFO_PANEL.width();
        int formsWidth = XenoDmzHdAtlas.STATS_PANEL.width();
        int total = bodyWidth + PANEL_GAP + formsWidth;
        int bodyX = Math.max(0, (uiWidth - total) / 2);
        int formsX = bodyX + bodyWidth + PANEL_GAP;
        int panelsY = Math.max(0, (uiHeight - XenoDmzHdAtlas.STATS_PANEL.height()) / 2 + 12);
        int nameplateX = Math.max(0, (uiWidth - XenoDmzHdAtlas.NAMEPLATE.width()) / 2);
        int nameplateY = Math.max(2, panelsY - XenoDmzHdAtlas.NAMEPLATE.height() - 4);
        return new Layout(bodyX, formsX, panelsY, nameplateX, nameplateY);
    }

    private static void header(GuiGraphics graphics, int panelX, int panelY, Sprite bar, Sprite icon,
                               int panelWidth, int barPart, int iconPart, int labelPart, String label) {
        int barX = panelX + 15 + XenoMasterMenuConfig.partX(barPart);
        int barY = panelY + 5 + XenoMasterMenuConfig.partY(barPart);
        blitPart(graphics, bar, barX, barY, barPart);
        blitPart(graphics, icon,
                panelX + 6 + XenoMasterMenuConfig.partX(barPart) + XenoMasterMenuConfig.partX(iconPart),
                panelY + 8 + XenoMasterMenuConfig.partY(barPart) + XenoMasterMenuConfig.partY(iconPart),
                iconPart);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.font != null) {
            int centreX = panelX + panelWidth / 2 + 6 + XenoMasterMenuConfig.partX(barPart)
                    + XenoMasterMenuConfig.partX(labelPart);
            int labelY = panelY + 13 + XenoMasterMenuConfig.partY(barPart)
                    + XenoMasterMenuConfig.partY(labelPart);
            graphics.drawCenteredString(mc.font, label, centreX, labelY,
                    XenoMasterMenuConfig.partColor(labelPart));
        }
    }

    private static void blitPart(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
        if (XenoMasterMenuConfig.hidden(part)) return;
        float scale = XenoMasterMenuConfig.partScale(part);
        int tint = XenoMasterMenuConfig.partColor(part);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            graphics.pose().scale(scale, scale, 1f);
        }
        graphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f, ((tint >>> 24) & 0xFF) / 255f);
        blit(graphics, sprite, 0, 0);
        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.pose().popPose();
    }

    private static void fillInterior(GuiGraphics graphics, int x, int y, Sprite panel) {
        graphics.fill(x + PANEL_INSET, y + PANEL_INSET,
                x + panel.width() - PANEL_INSET, y + panel.height() - PANEL_INSET, PANEL_FILL);
    }

    private static void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoDmzHdAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoDmzHdAtlas.ATLAS_WIDTH, XenoDmzHdAtlas.ATLAS_HEIGHT);
    }

    record Layout(int bodyX, int formsX, int panelsY, int nameplateX, int nameplateY) {}
}
