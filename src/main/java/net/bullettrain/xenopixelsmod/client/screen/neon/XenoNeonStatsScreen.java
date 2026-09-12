package net.bullettrain.xenopixelsmod.client.screen.neon;

import com.dragonminez.client.gui.character.ConfigMenuScreen;
import com.dragonminez.client.gui.character.MinigamesScreen;
import com.dragonminez.client.gui.character.PartyMenuScreen;
import com.dragonminez.client.gui.character.QuestTreeScreen;
import com.dragonminez.client.gui.character.SkillsMenuScreen;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.client.render.EntityPreviewRenderContext;
import com.dragonminez.common.network.C2S.IncreaseStatC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Resources;
import com.dragonminez.common.stats.character.Stats;
import com.mojang.blaze3d.systems.RenderSystem;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas.Sprite;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Block;
import net.bullettrain.xenopixelsmod.client.screen.neon.NeonPartTransform.Rect;
import net.bullettrain.xenopixelsmod.client.screen.StatText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * The neon rebuild of DragonMineZ's character screen, opened by {@code /xenohud menus neon}.
 *
 * <p>Built from the tracked transparent V3 single-elements kit. Panels, headers, rows, multiplier
 * fields, plus controls, scan ring, nameplate, navigation bases, and divider are separate sprites.
 * The generated atlas owns their shared logical geometry; changing values are always rendered live.
 *
 * <p>Kept alongside {@code XenoDmzStatsScreen} rather than replacing it. That screen is the first
 * rebuild, from the older HD kit; both stay reachable so they can be compared in play.
 *
 * <p><b>The numbers are DragonMineZ's.</b> Everything is read live from the player's
 * {@link StatsData}, and the {@code +} buttons send DMZ's own {@link IncreaseStatC2S}, so the server
 * path is untouched and this screen cannot grant a point the stock one would have refused.
 */
public class XenoNeonStatsScreen extends ScaledScreen {

    /**
     * The six spendable stats, in DMZ's own order, with the colour the reference render gives each.
     *
     * <p>The art has a seventh row; it carries the training-point total, which is not a spendable
     * stat and has no packet — see {@link #renderStatRows}.
     */
    private enum StatRow {
        STR("STR", IncreaseStatC2S.StatType.STR, XenoNeonAtlas.RED),
        SKP("SKP", IncreaseStatC2S.StatType.SKP, XenoNeonAtlas.RED),
        RES("RES", IncreaseStatC2S.StatType.RES, XenoNeonAtlas.RED),
        VIT("VIT", IncreaseStatC2S.StatType.VIT, XenoNeonAtlas.GREEN),
        PWR("PWR", IncreaseStatC2S.StatType.PWR, XenoNeonAtlas.MAGENTA),
        ENE("ENE", IncreaseStatC2S.StatType.ENE, XenoNeonAtlas.CYAN);

        private final String label;
        private final IncreaseStatC2S.StatType type;
        private final int colour;

        StatRow(String label, IncreaseStatC2S.StatType type, int colour) {
            this.label = label;
            this.type = type;
            this.colour = colour;
        }

        int value(Stats stats) {
            return switch (this) {
                case STR -> stats.getStrength();
                case SKP -> stats.getStrikePower();
                case RES -> stats.getResistance();
                case VIT -> stats.getVitality();
                case PWR -> stats.getKiPower();
                case ENE -> stats.getEnergy();
            };
        }

        /** The key DMZ's multiplier getters expect, which is the label. */
        String multiplierKey() {
            return label;
        }
    }

    /**
     * The navigation row. Every entry but the first opens DragonMineZ's own screen — this rebuild
     * covers the character page alone, so nothing behind V becomes unreachable.
     *
     * <p>The kit's fourth button reads ITEMS; it carries minigames, because that is the fourth menu
     * DragonMineZ actually has, and the tooltip says so.
     */
    private enum NavButton {
        CHARACTER("Character", null),
        SKILLS("Skills", SkillsMenuScreen::new),
        QUESTS("Quests", QuestTreeScreen::new),
        MINIGAMES("Minigames", MinigamesScreen::new),
        PARTY("Party", PartyMenuScreen::new),
        SETTINGS("Settings", ConfigMenuScreen::new);

        private final String label;
        private final Supplier<Screen> target;

        NavButton(String label, Supplier<Screen> target) {
            this.label = label;
            this.target = target;
        }
    }

    /** The seven derived statistics, right slab, in the order the reference render lists them. */
    private record Statistic(String label, int colour) {}

    private static final Statistic[] STATISTICS = {
            new Statistic("Melee DMG", XenoNeonAtlas.GOLD),
            new Statistic("Strike DMG", XenoNeonAtlas.GOLD),
            new Statistic("Stamina", XenoNeonAtlas.VALUE),
            new Statistic("Defense", XenoNeonAtlas.GOLD),
            new Statistic("Health", XenoNeonAtlas.VALUE),
            new Statistic("Ki DMG", XenoNeonAtlas.GOLD),
            new Statistic("Max Ki", XenoNeonAtlas.VALUE)};

    private static final String DEFAULT_FONT = "dragonminez:smooth";

    /**
     * Gap between the two slabs, wide enough for the scan ring (82 logical px) to sit between them.
     *
     * <p>Sized against DragonMineZ's guaranteed canvas rather than the window: {@code ScaledScreen}
     * promises only 320x240, and 101 + 90 + 103 = 294 leaves a margin inside that.
     */
    private static final int PANEL_GAP = 90;
    private static final int NAV_GAP = 3;
    private static final int ORB_GAP = 4;
    /** Text sits this far below its band's top edge; the bands are 9-10px and the font is 9px. */
    private static final int TEXT_INSET = 1;
    /** Where each group's header sits, relative to the panels' top edge. */
    private static final int STAT_HEADER_Y = 72;
    private static final int STATISTICS_HEADER_Y = 3;

    private int infoX;
    private int statsX;
    private int panelsY;
    private int nameplateX;
    private int nameplateY;
    private int navX;
    private int navY;
    private int ringX;
    private int ringY;
    private int orbsX;
    private int orbsY;

    public XenoNeonStatsScreen() {
        super(Component.translatable("screen.xenopixelsmod.dmz_neon"));
    }

    @Override
    protected void init() {
        int uiWidth = getUiWidth();
        int uiHeight = getUiHeight();
        int total = XenoNeonAtlas.INFO_PANEL.width() + PANEL_GAP + XenoNeonAtlas.STATS_PANEL.width();

        infoX = (uiWidth - total) / 2;
        statsX = infoX + XenoNeonAtlas.INFO_PANEL.width() + PANEL_GAP;
        panelsY = Math.max(XenoNeonAtlas.NAMEPLATE.height() + 4,
                (uiHeight - XenoNeonAtlas.INFO_PANEL.height()) / 2 + 6);

        nameplateX = (uiWidth - XenoNeonAtlas.NAMEPLATE.width()) / 2;
        nameplateY = Math.max(2, panelsY - XenoNeonAtlas.NAMEPLATE.height() - 2);

        navX = (uiWidth - navWidth()) / 2;
        navY = panelsY + XenoNeonAtlas.INFO_PANEL.height() + NAV_GAP;

        ringX = infoX + XenoNeonAtlas.INFO_PANEL.width()
                + (PANEL_GAP - XenoNeonAtlas.SCAN_RING.width()) / 2;
        ringY = panelsY + 30;

        orbsX = statsX + XenoNeonAtlas.STATS_PANEL.width() - orbsWidth();
        orbsY = Math.max(2, nameplateY + 2);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int uiMouseX = (int) Math.round(toUiX(mouseX));
        int uiMouseY = (int) Math.round(toUiY(mouseY));
        beginUiScale(graphics);
        try {
            StatsData data = stats();

            sprite(graphics, XenoNeonAtlas.NAMEPLATE, nameplateX, nameplateY, Part.NAMEPLATE);
            renderPanelChrome(graphics);
            sprite(graphics, XenoNeonAtlas.SCAN_RING, ringX, ringY, Part.SCAN_RING);
            renderOrbs(graphics);
            renderNav(graphics, uiMouseX, uiMouseY);

            if (data == null) {
                centred(graphics, "No DragonMineZ character",
                        nameplateX + XenoNeonAtlas.NAMEPLATE.width() / 2, nameplateY + 12,
                        Part.NAME, XenoNeonAtlas.VALUE);
            } else {
                renderNameplate(graphics, data);
                renderInformation(graphics, data);
                renderStatRows(graphics, data, uiMouseX, uiMouseY);
                renderStatistics(graphics, data);
                renderCharacter(graphics, mouseX, mouseY);
            }

            super.render(graphics, uiMouseX, uiMouseY, partialTick);
            renderNavTooltip(graphics, uiMouseX, uiMouseY);
        } finally {
            endUiScale(graphics);
        }
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally no dimming layer: every V3 asset carries its own RGBA transparency.
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void renderNameplate(GuiGraphics graphics, StatsData data) {
        Character character = data.getCharacter();
        int centre = nameplateX + XenoNeonAtlas.NAMEPLATE.width() / 2;
        String name = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().getName() : "";
        centred(graphics, name, centre, nameplateY + 9, Part.NAME, XenoNeonAtlas.LABEL);
        if (character != null) {
            centred(graphics, StatText.title(character.getRaceName()), centre, nameplateY + 22,
                    Part.RACE, XenoNeonAtlas.HEADER);
        }
    }

    private void renderPanelChrome(GuiGraphics graphics) {
        Block info = infoBlock();
        Block stats = statBlock();
        Block statistics = statisticsBlock();
        Block summary = summaryBlock();

        // The panel is its group: scaling INFO_PANEL resizes the slab and takes its header and
        // basic-information rows with it, instead of leaving them floating at their old size.
        drawBlockSprite(graphics, XenoNeonAtlas.INFO_PANEL, infoX, panelsY, info);
        sprite(graphics, XenoNeonAtlas.STATS_PANEL, statsX, panelsY, Part.STATS_PANEL);
        sprite(graphics, XenoNeonAtlas.INFO_HEADER, infoX + 3, panelsY + 2, Part.INFO_PANEL, info);
        sprite(graphics, XenoNeonAtlas.INFO_STATS_HEADER, infoX + 10, panelsY + STAT_HEADER_Y,
                Part.STAT_ROWS, stats);
        sprite(graphics, XenoNeonAtlas.STATISTICS_HEADER, statsX + 8,
                panelsY + STATISTICS_HEADER_Y, Part.STATISTICS, statistics);
        sprite(graphics, XenoNeonAtlas.DIVIDER, ringX - 3, panelsY + 20, Part.SCAN_RING);
        for (int[] row : XenoNeonAtlas.INFO_ROWS) {
            sprite(graphics, XenoNeonAtlas.BASIC_ROW, infoX + 6, panelsY + row[0],
                    Part.INFO_PANEL, info);
        }
        for (int i = 0; i < XenoNeonAtlas.STAT_ROWS.length; i++) {
            int y = panelsY + XenoNeonAtlas.STAT_ROWS[i][0];
            drawBlockSprite(graphics, XenoNeonAtlas.STAT_ROW, infoX - 1, y, stats);
            drawBlockSprite(graphics, XenoNeonAtlas.MULTIPLIER_FIELD, infoX + 75, y + 1, stats);
            sprite(graphics, XenoNeonAtlas.PLUS, infoX + XenoNeonAtlas.PLUS_X,
                    y + XenoNeonAtlas.PLUS_Y, Part.PLUS_BUTTON, stats);
        }
        for (int[] row : XenoNeonAtlas.STATISTIC_ROWS) {
            drawBlockSprite(graphics, XenoNeonAtlas.STATISTIC_ROW, statsX - 1, panelsY + row[0],
                    statistics);
        }
        for (int[] row : XenoNeonAtlas.SUMMARY_ROWS) {
            drawBlockSprite(graphics, XenoNeonAtlas.STATISTIC_ROW, statsX - 1, panelsY + row[0],
                    summary);
        }
    }

    /**
     * A sprite that <em>is</em> part of its group's own body -- a row shell, the panel slab.
     *
     * <p>Carries the group's transform and nothing else. {@link #sprite(GuiGraphics, Sprite, int,
     * int, int, Block)} is for a piece that sits inside a group but has a part of its own to scale
     * by as well, such as the {@code +} button; using that here would apply the group's scale twice.
     */
    private void drawBlockSprite(GuiGraphics graphics, Sprite sprite, int x, int y, Block block) {
        if (block.hidden()) {
            return;
        }
        draw(graphics, sprite, block.map(x, y, sprite.width(), sprite.height()), block.part());
    }

    /** Level, TPs, Form and Class, in the left panel's basic-information block. */
    private void renderInformation(GuiGraphics graphics, StatsData data) {
        Character character = data.getCharacter();
        Resources resources = data.getResources();
        String[][] rows = {
                {"Level", Integer.toString(data.getLevel())},
                {"TPs", resources == null ? "" : StatText.format(resources.getTrainingPoints())},
                {"Form", character == null || character.getActiveForm().isBlank()
                        ? "Base" : StatText.title(character.getActiveForm())},
                {"Class", character == null ? "" : StatText.title(character.getCharacterClass())}};

        Block info = infoBlock();
        int count = Math.min(rows.length, XenoNeonAtlas.INFO_ROWS.length);
        for (int i = 0; i < count; i++) {
            int y = panelsY + XenoNeonAtlas.INFO_ROWS[i][0] + TEXT_INSET;
            text(graphics, rows[i][0], infoX + XenoNeonAtlas.INFO_LABEL_X, y,
                    Part.INFO_LABEL, XenoNeonAtlas.LABEL, info);
            // Class is the one information row the reference render colours; the rest read white.
            int colour = "Class".equals(rows[i][0]) ? XenoNeonAtlas.RED : XenoNeonAtlas.VALUE;
            text(graphics, rows[i][1], infoX + XenoNeonAtlas.INFO_VALUE_X, y,
                    Part.INFO_VALUE, colour, info);
        }
    }

    /**
     * The seven shells under STATS: DMZ's six spendable stats, then the training-point total.
     *
     * <p>The seventh row's {@code +} is drawn dimmed and does nothing. DragonMineZ has no packet
     * that spends into training points, and this screen does not invent one.
     */
    private void renderStatRows(GuiGraphics graphics, StatsData data, int mouseX, int mouseY) {
        Stats stats = data.getStats();
        if (stats == null) {
            return;
        }
        boolean spendable = data.getRemainingAssignableStats() > 0;
        StatRow[] values = StatRow.values();
        Block block = statBlock();

        for (int i = 0; i < XenoNeonAtlas.STAT_ROWS.length; i++) {
            int y = Math.round(statRowY(i)) + TEXT_INSET;
            boolean last = i >= values.length;

            int tint = last || !spendable ? 0x60FFFFFF
                    : overPlus(mouseX, mouseY, i) ? 0xFFB8FFFF : 0xFFFFFFFF;
            if (tint != 0xFFFFFFFF && !NeonPartTransform.hidden(Part.PLUS_BUTTON)
                    && !block.hidden()) {
                // The shells are part of the panel art, so only the button needs dimming, and it is
                // dimmed rather than hidden so no row moves when the last point is spent. Drawn on
                // the same rectangle the button itself uses, so the dim tracks it at any scale --
                // and skipped entirely when the button is hidden, which used to leave a floating
                // grey square where the control had been.
                Rect plus = plusRect(i);
                graphics.fill(plus.left(), plus.top(), plus.right(), plus.bottom(),
                        last || !spendable ? 0x50000000 : 0x30FFFFFF);
            }

            String label;
            String value;
            double multiplier;
            int colour;
            if (last) {
                label = "TPC";
                value = StatText.format(stats.getTotalStats());
                multiplier = data.getTpTotalMultiplier();
                colour = XenoNeonAtlas.VALUE;
            } else {
                StatRow row = values[i];
                label = row.label;
                multiplier = data.getTotalMultiplier(row.multiplierKey());
                value = StatText.format(row.value(stats) * multiplier);
                colour = row.colour;
            }

            // In the group's own unscaled space; the group applies its offset and scale.
            text(graphics, label, infoX + XenoNeonAtlas.STAT_LABEL_X, y, Part.STAT_LABEL, colour,
                    block);
            String mult = StatText.multiplier(multiplier);
            int right = infoX + XenoNeonAtlas.STAT_RIGHT_X;
            right(graphics, mult, right, y, Part.STAT_MULTIPLIER,
                    StatText.isNeutral(multiplier) ? StatText.MULTIPLIER_NEUTRAL | 0xFF000000
                            : XenoNeonAtlas.GOLD, block);
            right(graphics, value, right - width(mult, Part.STAT_MULTIPLIER) - 4, y,
                    Part.STAT_VALUE, XenoNeonAtlas.VALUE, block);
        }
    }

    /** The right slab: seven derived statistics, then the summary box. */
    private void renderStatistics(GuiGraphics graphics, StatsData data) {
        double[] statistics = {
                data.getMeleeDamage(), data.getStrikeDamage(), data.getMaxStamina(),
                data.getDefense(), data.getMaxHealth(), data.getKiDamage(), data.getMaxEnergy()};
        Block block = statisticsBlock();
        int count = Math.min(statistics.length, XenoNeonAtlas.STATISTIC_ROWS.length);
        for (int i = 0; i < count; i++) {
            int y = panelsY + XenoNeonAtlas.STATISTIC_ROWS[i][0] + TEXT_INSET;
            text(graphics, STATISTICS[i].label(), statsX + XenoNeonAtlas.STATISTIC_LABEL_X, y,
                    Part.STATISTIC_LABEL, XenoNeonAtlas.LABEL, block);
            right(graphics, StatText.format(statistics[i]),
                    statsX + XenoNeonAtlas.STATISTIC_RIGHT_X, y, Part.STATISTIC_VALUE,
                    STATISTICS[i].colour(), block);
        }

        String[][] summary = {
                {"Power Level", StatText.format(data.getBattlePower())},
                {"Gravity", StatText.multiplier(data.getGravityStatMultiplier())},
                {"TP Multiplier", StatText.multiplier(data.getTpTotalMultiplier())}};
        int[] colours = {XenoNeonAtlas.VALUE, XenoNeonAtlas.ORANGE, XenoNeonAtlas.GOLD};
        Block summaryGroup = summaryBlock();
        int rows = Math.min(summary.length, XenoNeonAtlas.SUMMARY_ROWS.length);
        for (int i = 0; i < rows; i++) {
            int y = panelsY + XenoNeonAtlas.SUMMARY_ROWS[i][0] + TEXT_INSET;
            text(graphics, summary[i][0], statsX + XenoNeonAtlas.SUMMARY_LABEL_X, y,
                    Part.SUMMARY_LABEL, i == 1 ? XenoNeonAtlas.ORANGE : XenoNeonAtlas.LABEL,
                    summaryGroup);
            right(graphics, summary[i][1], statsX + XenoNeonAtlas.SUMMARY_RIGHT_X, y,
                    Part.SUMMARY_VALUE, colours[i], summaryGroup);
        }
    }

    private void renderOrbs(GuiGraphics graphics) {
        int x = orbsX;
        for (Sprite orb : new Sprite[]{
                XenoNeonAtlas.ORB_BLUE, XenoNeonAtlas.ORB_GOLD, XenoNeonAtlas.ORB_RED}) {
            sprite(graphics, orb, x, orbsY, Part.ORBS);
            x += orb.width() + ORB_GAP;
        }
    }

    /**
     * The live character, inside the scan ring.
     *
     * <p>Uses DragonMineZ's own preview renderer, so forms, hair and the DMZ skin layer come out the
     * way they do on DMZ's screen. The player's rotations are saved and restored around the call:
     * this is a preview, and leaving the local player facing the mouse would be a real bug.
     *
     * <p>Mouse coordinates are the raw screen ones, because the pose is aimed at the cursor and the
     * cursor lives in screen space regardless of the UI scale this screen renders at.
     */
    private void renderCharacter(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        LocalPlayer player = this.minecraft.player;
        Rect ring = spriteRect(XenoNeonAtlas.SCAN_RING, ringX, ringY, Part.SCAN_RING);
        float characterScale = XenoDmzNeonConfig.partScale(Part.CHARACTER);
        int centreX = Math.round(ring.x() + ring.width() / 2.0f)
                + XenoDmzNeonConfig.partX(Part.CHARACTER);
        int bottomY = Math.round(ring.y() + ring.height() - 8.0f)
                + XenoDmzNeonConfig.partY(Part.CHARACTER);
        int size = Math.max(8, Math.round(XenoNeonAtlas.SCAN_RING.height() / 3.0f
                * XenoDmzNeonConfig.partScale(Part.SCAN_RING) * characterScale));

        float lookX = (float) Math.atan((toScreenCoord(centreX) - mouseX) / 40.0f);
        float lookY = (float) Math.atan((toScreenCoord(bottomY - size) - mouseY) / 40.0f);
        Quaternionf poseRotation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf cameraRotation = new Quaternionf().rotateX(lookY * 20.0f * ((float) Math.PI / 180f));
        poseRotation.mul(cameraRotation);

        float bodyRot = player.yBodyRot;
        float yRot = player.getYRot();
        float xRot = player.getXRot();
        float headRot = player.yHeadRot;
        float headRotO = player.yHeadRotO;

        player.yBodyRot = 180.0f + lookX * 20.0f;
        player.setYRot(180.0f + lookX * 40.0f);
        player.setXRot(-lookY * 20.0f);
        player.yHeadRot = player.getYRot();
        player.yHeadRotO = player.getYRot();

        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 150.0);
        try {
            EntityPreviewRenderContext.renderEntityInInventory(graphics, centreX, bottomY,
                    (float) size, new Vector3f(), poseRotation, cameraRotation, player);
        } finally {
            graphics.pose().popPose();
            player.yBodyRot = bodyRot;
            player.setYRot(yRot);
            player.setXRot(xRot);
            player.yHeadRot = headRot;
            player.yHeadRotO = headRotO;
        }
    }

    private void renderNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = navX;
        for (NavButton button : NavButton.values()) {
            sprite(graphics, XenoNeonAtlas.NAV_BASE, x, navY, Part.NAV_ROW);
            Rect rect = spriteRect(XenoNeonAtlas.NAV_BASE, x, navY, Part.NAV_ROW);
            centred(graphics, button.label, x + XenoNeonAtlas.NAV_BASE.width() / 2,
                    navY + 8, Part.NAV_ROW, XenoNeonAtlas.LABEL);
            if (button == NavButton.CHARACTER) {
                graphics.fill(rect.left(), rect.bottom(), rect.right(), rect.bottom() + 1,
                        XenoNeonAtlas.GOLD);
            } else if (overNav(button, mouseX, mouseY)) {
                graphics.fill(rect.left(), rect.top(), rect.right(), rect.bottom(), 0x40FFFFFF);
            }
            x += XenoNeonAtlas.NAV_BASE.width() + NAV_GAP;
        }
    }

    private void renderNavTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (NavButton button : NavButton.values()) {
            if (overNav(button, mouseX, mouseY)) {
                graphics.renderTooltip(this.font, Component.literal(button.label), mouseX, mouseY);
                return;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int uiMouseX = (int) toUiX(mouseX);
        int uiMouseY = (int) toUiY(mouseY);
        if (button == 0) {
            for (NavButton nav : NavButton.values()) {
                if (nav.target != null && overNav(nav, uiMouseX, uiMouseY)) {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(nav.target.get());
                    }
                    return true;
                }
            }
            StatsData data = stats();
            if (data != null && data.getRemainingAssignableStats() > 0) {
                StatRow[] values = StatRow.values();
                for (int i = 0; i < values.length; i++) {
                    if (overPlus(uiMouseX, uiMouseY, i)) {
                        // DragonMineZ's own packet: it validates the spend and owns the result, so
                        // this screen can never grant a point the stock screen would have refused.
                        NetworkHandler.sendToServer(new IncreaseStatC2S(values[i].type, 1));
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * The stat-row group: the STATS header and the seven row shells under it.
     *
     * <p>A group rather than seven independent sprites, so resizing it keeps the rows evenly spaced
     * and carries their labels, values and {@code +} buttons with them.
     */
    private Block statBlock() {
        int[][] rows = XenoNeonAtlas.STAT_ROWS;
        float top = panelsY + STAT_HEADER_Y;
        float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
        return new Block(Part.STAT_ROWS, infoX - 1, top,
                XenoNeonAtlas.STAT_ROW.width(), bottom - top);
    }

    /** The left panel and the basic-information rows printed on it. */
    private Block infoBlock() {
        return new Block(Part.INFO_PANEL, infoX, panelsY,
                XenoNeonAtlas.INFO_PANEL.width(), XenoNeonAtlas.INFO_PANEL.height());
    }

    /** The right panel's derived-statistics group: its header and its seven rows. */
    private Block statisticsBlock() {
        int[][] rows = XenoNeonAtlas.STATISTIC_ROWS;
        float top = panelsY + STATISTICS_HEADER_Y;
        float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
        return new Block(Part.STATISTICS, statsX - 1, top,
                XenoNeonAtlas.STATISTIC_ROW.width(), bottom - top);
    }

    /** The summary box under the statistics. */
    private Block summaryBlock() {
        int[][] rows = XenoNeonAtlas.SUMMARY_ROWS;
        float top = panelsY + rows[0][0];
        float bottom = panelsY + rows[rows.length - 1][0] + rows[rows.length - 1][1];
        return new Block(Part.SUMMARY, statsX - 1, top,
                XenoNeonAtlas.STATISTIC_ROW.width(), bottom - top);
    }

    /** The top of one stat row shell, in the row group's own unscaled space. */
    private float statRowY(int row) {
        return panelsY + XenoNeonAtlas.STAT_ROWS[row][0];
    }

    /**
     * The {@code +} button's rectangle, exactly as it is drawn.
     *
     * <p>Through the row group, so the button tracks its row at any group scale. It used to take the
     * group's offset but not its scale, which left the hit area behind whenever the group was
     * resized -- the button moved and the click did not.
     */
    private Rect plusRect(int row) {
        return NeonPartTransform.rect(XenoNeonAtlas.PLUS.width(), XenoNeonAtlas.PLUS.height(),
                infoX + XenoNeonAtlas.PLUS_X, Math.round(statRowY(row)) + XenoNeonAtlas.PLUS_Y,
                Part.PLUS_BUTTON, statBlock());
    }

    private boolean overPlus(double mouseX, double mouseY, int row) {
        if (row < 0 || row >= XenoNeonAtlas.STAT_ROWS.length
                || XenoDmzNeonConfig.partHidden[Part.PLUS_BUTTON]
                || XenoDmzNeonConfig.partHidden[Part.STAT_ROWS]) {
            return false;
        }
        return plusRect(row).contains(mouseX, mouseY);
    }

    private boolean overNav(NavButton button, int mouseX, int mouseY) {
        if (XenoDmzNeonConfig.partHidden[Part.NAV_ROW]) {
            return false;
        }
        int x = navX;
        for (NavButton candidate : NavButton.values()) {
            if (candidate == button) {
                break;
            }
            x += XenoNeonAtlas.NAV_BASE.width() + NAV_GAP;
        }
        return spriteRect(XenoNeonAtlas.NAV_BASE, x, navY, Part.NAV_ROW)
                .contains(mouseX, mouseY);
    }

    private static int navWidth() {
        int width = -NAV_GAP;
        for (NavButton ignored : NavButton.values()) {
            width += XenoNeonAtlas.NAV_BASE.width() + NAV_GAP;
        }
        return width;
    }

    private static int orbsWidth() {
        return XenoNeonAtlas.ORB_BLUE.width() + XenoNeonAtlas.ORB_GOLD.width()
                + XenoNeonAtlas.ORB_RED.width() + ORB_GAP * 2;
    }

    private StatsData stats() {
        if (this.minecraft == null || this.minecraft.player == null) {
            return null;
        }
        try {
            return StatsProvider.get(StatsCapability.INSTANCE, this.minecraft.player).orElse(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** A standalone sprite, scaled around its own centre, at the rectangle used for input. */
    private void sprite(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
        draw(graphics, sprite, spriteRect(sprite, x, y, part), part);
    }

    /** A sprite belonging to a group, carrying the group's transform as well as its own. */
    private void sprite(GuiGraphics graphics, Sprite sprite, int x, int y, int part, Block block) {
        if (block.hidden()) {
            return;
        }
        draw(graphics, sprite, NeonPartTransform.rect(sprite.width(), sprite.height(),
                x, y, part, block), part);
    }

    private void draw(GuiGraphics graphics, Sprite sprite, Rect rect, int part) {
        if (XenoDmzNeonConfig.partHidden[part]) {
            return;
        }
        // From the rectangle rather than from the scale, so what is drawn is exactly what the
        // hit-test measured -- a group's scale is already folded into the width.
        float scaleX = rect.width() / sprite.width();
        float scaleY = rect.height() / sprite.height();
        int tint = XenoDmzNeonConfig.partColor(part, 0xFFFFFFFF);
        float alpha = (tint >>> 24 & 0xFF) / 255.0f;
        float red = (tint >>> 16 & 0xFF) / 255.0f;
        float green = (tint >>> 8 & 0xFF) / 255.0f;
        float blue = (tint & 0xFF) / 255.0f;
        graphics.pose().pushPose();
        graphics.pose().translate(rect.x(), rect.y(), 0.0f);
        graphics.pose().scale(scaleX, scaleY, 1.0f);
        RenderSystem.setShaderColor(red, green, blue, alpha);
        try {
            blit(graphics, sprite, 0, 0);
        } finally {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            graphics.pose().popPose();
        }
    }

    private static Rect spriteRect(Sprite sprite, int x, int y, int part) {
        return NeonPartTransform.rect(sprite.width(), sprite.height(), x, y, part);
    }

    private void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoNeonAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoNeonAtlas.ATLAS_WIDTH, XenoNeonAtlas.ATLAS_HEIGHT);
    }

    /** DMZ's own menu font by default, so the readouts match the rest of the V menus. */
    private Component styled(String text, int part) {
        return Component.literal(text).setStyle(Style.EMPTY
                .withBold(XenoDmzNeonConfig.partBold[part])
                .withFont(XenoDmzNeonConfig.partFontLocation(part)));
    }

    /** How wide a readout comes out, at whatever scale its part and its group give it. */
    private int width(String text, int part) {
        return width(text, part, null);
    }

    private int width(String text, int part, Block block) {
        float scale = block == null ? XenoDmzNeonConfig.partScale(part)
                : NeonPartTransform.textScale(part, block);
        return (int) (this.font.width(styled(text, part)) * scale);
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int part, int designColour) {
        draw(graphics, text, x, y, part, designColour, null);
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int part, int designColour,
                      Block block) {
        draw(graphics, text, x, y, part, designColour, block);
    }

    private void right(GuiGraphics graphics, String text, int rightEdge, int y, int part,
                       int designColour) {
        right(graphics, text, rightEdge, y, part, designColour, null);
    }

    /**
     * A right-aligned readout.
     *
     * <p>Measured at the same scale it is drawn at, so a value still ends on its column when its
     * group is resized instead of sliding off the shell.
     */
    private void right(GuiGraphics graphics, String text, int rightEdge, int y, int part,
                       int designColour, Block block) {
        if (block == null) {
            draw(graphics, text, rightEdge - width(text, part), y, part, designColour, null);
            return;
        }
        // Aligned in the group's own unscaled space, so the group's scale is not applied twice --
        // once to the alignment offset here and again when the position is mapped.
        float unscaled = this.font.width(styled(text, part)) * XenoDmzNeonConfig.partScale(part);
        draw(graphics, text, Math.round(rightEdge - unscaled), y, part, designColour, block);
    }

    private void centred(GuiGraphics graphics, String text, int centreX, int y, int part,
                         int designColour) {
        draw(graphics, text, centreX - width(text, part) / 2, y, part, designColour, null);
    }

    /**
     * One styled, scaled, positioned readout.
     *
     * <p>{@code designColour} is the colour this particular row has in the reference render. It is
     * what shows unless the elements editor's override is on, because the design colours several of
     * these kinds per row -- the stat labels alone run red, green, magenta and cyan.
     *
     * <p>{@code block} is the group the readout belongs to, or null for one that stands alone. A
     * grouped readout is positioned in its group's space and drawn at both scales, which is what
     * keeps a label on its row shell when the group is resized. The two multiply rather than either
     * replacing the other: the part's own scale still means the text's own size.
     */
    private void draw(GuiGraphics graphics, String text, int x, int y, int part, int designColour,
                      Block block) {
        if (text == null || text.isEmpty() || XenoDmzNeonConfig.partHidden[part]
                || (block != null && block.hidden())) {
            return;
        }
        float scale;
        float drawX;
        float drawY;
        if (block == null) {
            scale = XenoDmzNeonConfig.partScale(part);
            drawX = x + XenoDmzNeonConfig.partX(part);
            drawY = y + XenoDmzNeonConfig.partY(part);
        } else {
            scale = NeonPartTransform.textScale(part, block);
            drawX = block.mapX(x + XenoDmzNeonConfig.partX(part));
            drawY = block.mapY(y + XenoDmzNeonConfig.partY(part));
        }
        graphics.pose().pushPose();
        graphics.pose().translate(drawX, drawY, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(this.font, styled(text, part), 0, 0,
                XenoDmzNeonConfig.partColor(part, designColour), false);
        graphics.pose().popPose();
    }
}
