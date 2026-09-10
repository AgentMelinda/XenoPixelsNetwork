package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the character screen's chrome for the elements editor.
 *
 * <p>The editor asks a surface to render itself and to report its bounds. Instantiating the real
 * {@link XenoDmzStatsScreen} inside another screen is not something Minecraft's screen lifecycle
 * invites, and the editor only needs to show where each piece sits — so this lays out the same
 * sprites at the same offsets and skips the live DragonMineZ readouts, which have nothing to say
 * about position.
 *
 * <p>Deliberately mirrors {@code XenoDmzStatsScreen}'s own geometry constants. If that screen's
 * layout changes, this changes with it.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoDmzScreenPreview {

    private static final int PANEL_GAP = 16;
    private static final int NAV_GAP = 6;
    private static final int STAT_ROW_STEP = 17;

    private XenoDmzScreenPreview() {
    }

    /** Where the screen's panels sit, for the editor's selection outline. */
    public static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int total = XenoDmzHdAtlas.INFO_PANEL.width() + PANEL_GAP
                + XenoDmzHdAtlas.STATS_PANEL.width();
        int left = (width - total) / 2;
        int top = (height - XenoDmzHdAtlas.STATS_PANEL.height()) / 2 + 14;
        return new int[]{left, top, total, XenoDmzHdAtlas.STATS_PANEL.height()};
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int panelWidth = XenoDmzHdAtlas.INFO_PANEL.width();
        int total = panelWidth + PANEL_GAP + XenoDmzHdAtlas.STATS_PANEL.width();
        int infoX = (screenWidth - total) / 2;
        int statsX = infoX + panelWidth + PANEL_GAP;
        int panelsY = (screenHeight - XenoDmzHdAtlas.STATS_PANEL.height()) / 2 + 14;
        int nameplateX = (screenWidth - XenoDmzHdAtlas.NAMEPLATE.width()) / 2;
        int nameplateY = Math.max(2, panelsY - XenoDmzHdAtlas.NAMEPLATE.height() - 6);

        sprite(graphics, XenoDmzHdAtlas.NAMEPLATE, nameplateX, nameplateY, Part.NAMEPLATE);
        sprite(graphics, XenoDmzHdAtlas.INFO_PANEL, infoX, panelsY, Part.INFO_PANEL);
        sprite(graphics, XenoDmzHdAtlas.STATS_PANEL, statsX, panelsY, Part.STATS_PANEL);
        sprite(graphics, XenoDmzHdAtlas.INFO_HEADER, infoX + 15, panelsY + 5, Part.INFO_HEADER);
        sprite(graphics, XenoDmzHdAtlas.KANJI_CIRCLE, infoX + 6, panelsY + 8, Part.INFO_HEADER);
        sprite(graphics, XenoDmzHdAtlas.STATS_HEADER, statsX + 15, panelsY + 5, Part.STATS_HEADER);
        sprite(graphics, XenoDmzHdAtlas.STATS_BARS_ICON, statsX + 6, panelsY + 8, Part.STATS_HEADER);
        sprite(graphics, XenoDmzHdAtlas.STATS_SUBHEADER, infoX + 27, panelsY + 84,
                Part.STATS_SUBHEADER);

        int rowX = infoX + XenoDmzScreenConfig.partX(Part.STAT_ROWS) + 10;
        int rowY = panelsY + 108 + XenoDmzScreenConfig.partY(Part.STAT_ROWS);
        for (int i = 0; i < 6; i++) {
            blit(graphics, XenoDmzHdAtlas.STAT_ROW, rowX, rowY);
            blit(graphics, XenoDmzHdAtlas.PLUS_BUTTON,
                    rowX + 4 + XenoDmzScreenConfig.partX(Part.PLUS_BUTTON),
                    rowY + 4 + XenoDmzScreenConfig.partY(Part.PLUS_BUTTON));
            rowY += STAT_ROW_STEP;
        }

        int boxX = statsX + 10 + XenoDmzScreenConfig.partX(Part.BOTTOM_BOX);
        int boxY = panelsY + XenoDmzHdAtlas.STATS_PANEL.height() - 60
                + XenoDmzScreenConfig.partY(Part.BOTTOM_BOX);
        blit(graphics, XenoDmzHdAtlas.BOTTOM_BOX, boxX, boxY);

        int navWidth = 6 * XenoDmzHdAtlas.NAV_CHARACTER.width() + 5 * NAV_GAP;
        int navX = (screenWidth - navWidth) / 2 + XenoDmzScreenConfig.partX(Part.NAV_ROW);
        int navY = panelsY + XenoDmzHdAtlas.STATS_PANEL.height() + NAV_GAP
                + XenoDmzScreenConfig.partY(Part.NAV_ROW);
        for (Sprite nav : new Sprite[]{XenoDmzHdAtlas.NAV_CHARACTER, XenoDmzHdAtlas.NAV_SKILLS,
                XenoDmzHdAtlas.NAV_QUESTS, XenoDmzHdAtlas.NAV_ITEMS, XenoDmzHdAtlas.NAV_PARTY,
                XenoDmzHdAtlas.NAV_SETTINGS}) {
            blit(graphics, nav, navX, navY);
            navX += nav.width() + NAV_GAP;
        }
    }

    private static void sprite(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
        blit(graphics, sprite, x + XenoDmzScreenConfig.partX(part),
                y + XenoDmzScreenConfig.partY(part));
    }

    private static void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoDmzHdAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoDmzHdAtlas.ATLAS_WIDTH, XenoDmzHdAtlas.ATLAS_HEIGHT);
    }
}
