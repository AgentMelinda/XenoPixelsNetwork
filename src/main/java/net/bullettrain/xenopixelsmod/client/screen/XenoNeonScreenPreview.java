package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas.Sprite;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws the neon character screen's chrome for the elements editor.
 *
 * <p>The editor asks a surface to render itself and to report its bounds. Instantiating the real
 * {@code XenoNeonStatsScreen} inside another screen is not something Minecraft's screen lifecycle
 * invites, and the editor only needs to show where each piece sits — so this lays out the same
 * sprites at the same offsets and skips the live DragonMineZ readouts, which have nothing to say
 * about position.
 *
 * <p>Deliberately mirrors {@code XenoNeonStatsScreen}'s own geometry constants. If that screen's
 * layout changes, this changes with it.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoNeonScreenPreview {

    private static final int PANEL_GAP = 90;
    private static final int NAV_GAP = 3;
    private static final int ORB_GAP = 4;

    private static final Sprite[] NAV = {
            XenoNeonAtlas.NAV_CHARACTER, XenoNeonAtlas.NAV_SKILLS, XenoNeonAtlas.NAV_QUESTS,
            XenoNeonAtlas.NAV_ITEMS, XenoNeonAtlas.NAV_PARTY, XenoNeonAtlas.NAV_SETTINGS};
    private static final Sprite[] ORBS = {
            XenoNeonAtlas.ORB_BLUE, XenoNeonAtlas.ORB_GOLD, XenoNeonAtlas.ORB_RED};

    private XenoNeonScreenPreview() {
    }

    /** Where the screen's panels sit, for the editor's selection outline. */
    public static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        int total = XenoNeonAtlas.INFO_PANEL.width() + PANEL_GAP
                + XenoNeonAtlas.STATS_PANEL.width();
        int left = (width - total) / 2;
        int top = panelsY(height);
        return new int[]{left, top, total, XenoNeonAtlas.INFO_PANEL.height()};
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        int panelWidth = XenoNeonAtlas.INFO_PANEL.width();
        int total = panelWidth + PANEL_GAP + XenoNeonAtlas.STATS_PANEL.width();
        int infoX = (screenWidth - total) / 2;
        int statsX = infoX + panelWidth + PANEL_GAP;
        int panelsY = panelsY(screenHeight);
        int nameplateX = (screenWidth - XenoNeonAtlas.NAMEPLATE.width()) / 2;
        int nameplateY = Math.max(2, panelsY - XenoNeonAtlas.NAMEPLATE.height() - 2);

        sprite(graphics, XenoNeonAtlas.NAMEPLATE, nameplateX, nameplateY, Part.NAMEPLATE);
        sprite(graphics, XenoNeonAtlas.INFO_PANEL, infoX, panelsY, Part.INFO_PANEL);
        sprite(graphics, XenoNeonAtlas.STATS_PANEL, statsX, panelsY, Part.STATS_PANEL);
        sprite(graphics, XenoNeonAtlas.SCAN_RING,
                infoX + panelWidth + (PANEL_GAP - XenoNeonAtlas.SCAN_RING.width()) / 2,
                panelsY + 30, Part.SCAN_RING);

        int orbX = statsX + XenoNeonAtlas.STATS_PANEL.width() - orbsWidth();
        int orbY = Math.max(2, nameplateY + 2);
        for (Sprite orb : ORBS) {
            sprite(graphics, orb, orbX, orbY, Part.ORBS);
            orbX += orb.width() + ORB_GAP;
        }

        int navX = (screenWidth - navWidth()) / 2;
        int navY = panelsY + XenoNeonAtlas.INFO_PANEL.height() + NAV_GAP;
        for (Sprite button : NAV) {
            sprite(graphics, button, navX, navY, Part.NAV_ROW);
            navX += button.width() + NAV_GAP;
        }
    }

    private static int panelsY(int screenHeight) {
        return Math.max(XenoNeonAtlas.NAMEPLATE.height() + 4,
                (screenHeight - XenoNeonAtlas.INFO_PANEL.height()) / 2 + 6);
    }

    private static int navWidth() {
        int width = -NAV_GAP;
        for (Sprite button : NAV) {
            width += button.width() + NAV_GAP;
        }
        return width;
    }

    private static int orbsWidth() {
        int width = -ORB_GAP;
        for (Sprite orb : ORBS) {
            width += orb.width() + ORB_GAP;
        }
        return width;
    }

    private static void sprite(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
        blit(graphics, sprite, x + XenoDmzNeonConfig.partX(part),
                y + XenoDmzNeonConfig.partY(part));
    }

    private static void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoNeonAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoNeonAtlas.ATLAS_WIDTH, XenoNeonAtlas.ATLAS_HEIGHT);
    }
}
