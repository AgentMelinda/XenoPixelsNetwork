package net.bullettrain.xenopixelsmod.client.screen;

import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas.Sprite;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Block;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Rect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.systems.RenderSystem;
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
 * <p>Deliberately mirrors {@code XenoNeonStatsScreen}'s own geometry constants, and shares its
 * transform maths through {@link NeonPartTransform} rather than repeating it — the two having their
 * own copies is what let the preview scale a sprite while reporting an unscaled rectangle for it.
 */
@OnlyIn(Dist.CLIENT)
public final class XenoNeonScreenPreview {

    private static final int PANEL_GAP = 90;
    private static final int NAV_GAP = 3;
    private static final int ORB_GAP = 4;
    private static final int STAT_HEADER_Y = 72;
    private static final int STATISTICS_HEADER_Y = 3;

    private static final Sprite[] ORBS = {
            XenoNeonAtlas.ORB_BLUE, XenoNeonAtlas.ORB_GOLD, XenoNeonAtlas.ORB_RED};

    private XenoNeonScreenPreview() {
    }

    /**
     * Where the screen's chrome actually sits, for the editor's selection outline.
     *
     * <p>The union of every piece's transformed rectangle, not the panels' nominal box. Scaling a
     * panel past 1 used to push it outside an outline that had not moved, so the editor drew a frame
     * around less than it was editing and the drag hit-test disagreed with the screen.
     */
    public static int[] bounds() {
        Minecraft mc = Minecraft.getInstance();
        return bounds(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    static int[] bounds(int screenWidth, int screenHeight) {
        Layout layout = new Layout(screenWidth, screenHeight);
        Rect box = layout.infoBlock().map(layout.infoX, layout.panelsY,
                XenoNeonAtlas.INFO_PANEL.width(), XenoNeonAtlas.INFO_PANEL.height());
        box = box.union(NeonPartTransform.rect(XenoNeonAtlas.STATS_PANEL.width(),
                XenoNeonAtlas.STATS_PANEL.height(), layout.statsX, layout.panelsY,
                Part.STATS_PANEL));
        box = box.union(NeonPartTransform.rect(XenoNeonAtlas.NAMEPLATE.width(),
                XenoNeonAtlas.NAMEPLATE.height(), layout.nameplateX, layout.nameplateY,
                Part.NAMEPLATE));
        box = box.union(NeonPartTransform.rect(XenoNeonAtlas.SCAN_RING.width(),
                XenoNeonAtlas.SCAN_RING.height(), layout.ringX, layout.ringY, Part.SCAN_RING));
        box = box.union(NeonPartTransform.rect(XenoNeonAtlas.NAV_BASE.width(),
                XenoNeonAtlas.NAV_BASE.height(), layout.navX, layout.navY, Part.NAV_ROW));
        box = box.union(NeonPartTransform.rect(XenoNeonAtlas.NAV_BASE.width(),
                XenoNeonAtlas.NAV_BASE.height(),
                layout.navX + (XenoNeonAtlas.NAV_BASE.width() + NAV_GAP) * 5, layout.navY,
                Part.NAV_ROW));
        return new int[]{box.left(), box.top(), box.right() - box.left(),
                box.bottom() - box.top()};
    }

    public static void render(GuiGraphics graphics, int screenWidth, int screenHeight) {
        Layout layout = new Layout(screenWidth, screenHeight);
        Block info = layout.infoBlock();
        Block stats = layout.statBlock();
        Block statistics = layout.statisticsBlock();
        Block summary = layout.summaryBlock();

        sprite(graphics, XenoNeonAtlas.NAMEPLATE, layout.nameplateX, layout.nameplateY,
                Part.NAMEPLATE);
        blockSprite(graphics, XenoNeonAtlas.INFO_PANEL, layout.infoX, layout.panelsY, info);
        sprite(graphics, XenoNeonAtlas.STATS_PANEL, layout.statsX, layout.panelsY,
                Part.STATS_PANEL);
        inBlock(graphics, XenoNeonAtlas.INFO_HEADER, layout.infoX + 3, layout.panelsY + 2,
                Part.INFO_PANEL, info);
        inBlock(graphics, XenoNeonAtlas.INFO_STATS_HEADER, layout.infoX + 10,
                layout.panelsY + STAT_HEADER_Y, Part.STAT_ROWS, stats);
        inBlock(graphics, XenoNeonAtlas.STATISTICS_HEADER, layout.statsX + 8,
                layout.panelsY + STATISTICS_HEADER_Y, Part.STATISTICS, statistics);
        for (int[] row : XenoNeonAtlas.INFO_ROWS) {
            inBlock(graphics, XenoNeonAtlas.BASIC_ROW, layout.infoX + 6, layout.panelsY + row[0],
                    Part.INFO_PANEL, info);
        }
        for (int[] row : XenoNeonAtlas.STAT_ROWS) {
            blockSprite(graphics, XenoNeonAtlas.STAT_ROW, layout.infoX - 1,
                    layout.panelsY + row[0], stats);
            blockSprite(graphics, XenoNeonAtlas.MULTIPLIER_FIELD, layout.infoX + 75,
                    layout.panelsY + row[0] + 1, stats);
            inBlock(graphics, XenoNeonAtlas.PLUS, layout.infoX + XenoNeonAtlas.PLUS_X,
                    layout.panelsY + row[0] + XenoNeonAtlas.PLUS_Y, Part.PLUS_BUTTON, stats);
        }
        for (int[] row : XenoNeonAtlas.STATISTIC_ROWS) {
            blockSprite(graphics, XenoNeonAtlas.STATISTIC_ROW, layout.statsX - 1,
                    layout.panelsY + row[0], statistics);
        }
        for (int[] row : XenoNeonAtlas.SUMMARY_ROWS) {
            blockSprite(graphics, XenoNeonAtlas.STATISTIC_ROW, layout.statsX - 1,
                    layout.panelsY + row[0], summary);
        }
        sprite(graphics, XenoNeonAtlas.DIVIDER,
                layout.infoX + XenoNeonAtlas.INFO_PANEL.width()
                        + (PANEL_GAP - XenoNeonAtlas.DIVIDER.width()) / 2,
                layout.panelsY + 20, Part.SCAN_RING);
        sprite(graphics, XenoNeonAtlas.SCAN_RING, layout.ringX, layout.ringY, Part.SCAN_RING);

        int orbX = layout.statsX + XenoNeonAtlas.STATS_PANEL.width() - orbsWidth();
        int orbY = Math.max(2, layout.nameplateY + 2);
        for (Sprite orb : ORBS) {
            sprite(graphics, orb, orbX, orbY, Part.ORBS);
            orbX += orb.width() + ORB_GAP;
        }

        int navX = layout.navX;
        for (int i = 0; i < 6; i++) {
            sprite(graphics, XenoNeonAtlas.NAV_BASE, navX, layout.navY, Part.NAV_ROW);
            navX += XenoNeonAtlas.NAV_BASE.width() + NAV_GAP;
        }
    }

    /**
     * The screen's shipped geometry, computed once.
     *
     * <p>Mirrors {@code XenoNeonStatsScreen.init} and its block accessors. The live screen works in
     * DragonMineZ's scaled UI space and this works in the window's GUI space, which is why the
     * layout is recomputed here rather than shared.
     */
    private static final class Layout {
        private final int infoX;
        private final int statsX;
        private final int panelsY;
        private final int nameplateX;
        private final int nameplateY;
        private final int navX;
        private final int navY;
        private final int ringX;
        private final int ringY;

        Layout(int screenWidth, int screenHeight) {
            int total = XenoNeonAtlas.INFO_PANEL.width() + PANEL_GAP
                    + XenoNeonAtlas.STATS_PANEL.width();
            infoX = (screenWidth - total) / 2;
            statsX = infoX + XenoNeonAtlas.INFO_PANEL.width() + PANEL_GAP;
            panelsY = Math.max(XenoNeonAtlas.NAMEPLATE.height() + 4,
                    (screenHeight - XenoNeonAtlas.INFO_PANEL.height()) / 2 + 6);
            nameplateX = (screenWidth - XenoNeonAtlas.NAMEPLATE.width()) / 2;
            nameplateY = Math.max(2, panelsY - XenoNeonAtlas.NAMEPLATE.height() - 2);
            navX = (screenWidth - navWidth()) / 2;
            navY = panelsY + XenoNeonAtlas.INFO_PANEL.height() + NAV_GAP;
            ringX = infoX + XenoNeonAtlas.INFO_PANEL.width()
                    + (PANEL_GAP - XenoNeonAtlas.SCAN_RING.width()) / 2;
            ringY = panelsY + 30;
        }

        Block infoBlock() {
            return new Block(Part.INFO_PANEL, infoX, panelsY,
                    XenoNeonAtlas.INFO_PANEL.width(), XenoNeonAtlas.INFO_PANEL.height());
        }

        Block statBlock() {
            int[][] rows = XenoNeonAtlas.STAT_ROWS;
            float top = panelsY + STAT_HEADER_Y;
            float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
            return new Block(Part.STAT_ROWS, infoX - 1, top,
                    XenoNeonAtlas.STAT_ROW.width(), bottom - top);
        }

        Block statisticsBlock() {
            int[][] rows = XenoNeonAtlas.STATISTIC_ROWS;
            float top = panelsY + STATISTICS_HEADER_Y;
            float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
            return new Block(Part.STATISTICS, statsX - 1, top,
                    XenoNeonAtlas.STATISTIC_ROW.width(), bottom - top);
        }

        Block summaryBlock() {
            int[][] rows = XenoNeonAtlas.SUMMARY_ROWS;
            float top = panelsY + rows[0][0];
            float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
            return new Block(Part.SUMMARY, statsX - 1, top,
                    XenoNeonAtlas.STATISTIC_ROW.width(), bottom - top);
        }
    }

    private static int navWidth() {
        int width = -NAV_GAP;
        for (int i = 0; i < 6; i++) {
            width += XenoNeonAtlas.NAV_BASE.width() + NAV_GAP;
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
        draw(graphics, sprite, NeonPartTransform.rect(sprite.width(), sprite.height(), x, y, part),
                part);
    }

    /** A sprite that is its group's own body: a row shell, a panel slab. */
    private static void blockSprite(GuiGraphics graphics, Sprite sprite, int x, int y,
                                    Block block) {
        if (block.hidden()) {
            return;
        }
        draw(graphics, sprite, block.map(x, y, sprite.width(), sprite.height()), block.part());
    }

    /** A sprite inside a group that also has a part of its own to scale by. */
    private static void inBlock(GuiGraphics graphics, Sprite sprite, int x, int y, int part,
                                Block block) {
        if (block.hidden()) {
            return;
        }
        draw(graphics, sprite,
                NeonPartTransform.rect(sprite.width(), sprite.height(), x, y, part, block), part);
    }

    private static void draw(GuiGraphics graphics, Sprite sprite, Rect rect, int part) {
        if (XenoDmzNeonConfig.partHidden[part]) {
            return;
        }
        float scaleX = rect.width() / sprite.width();
        float scaleY = rect.height() / sprite.height();
        int tint = XenoDmzNeonConfig.partColor(part, 0xFFFFFFFF);
        graphics.pose().pushPose();
        graphics.pose().translate(rect.x(), rect.y(), 0.0f);
        graphics.pose().scale(scaleX, scaleY, 1.0f);
        RenderSystem.setShaderColor((tint >>> 16 & 0xFF) / 255.0f,
                (tint >>> 8 & 0xFF) / 255.0f, (tint & 0xFF) / 255.0f,
                (tint >>> 24 & 0xFF) / 255.0f);
        try {
            blit(graphics, sprite, 0, 0);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().popPose();
        }
    }

    private static void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoNeonAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoNeonAtlas.ATLAS_WIDTH, XenoNeonAtlas.ATLAS_HEIGHT);
    }
}
