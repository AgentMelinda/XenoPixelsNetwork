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
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzNeonConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoNeonAtlas.Sprite;
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
 * <p>Built from `dragonminez_our_style_clean_example_dimensions_2.zip`, whose panels are single
 * slabs: {@code INFO_PANEL} and {@code STATS_PANEL} already contain their frame, header, icons and
 * every row shell. So this screen draws two sprites and then writes the readouts into the row bands
 * {@code tools/gen_dmz_neon_atlas.py} measured off that art. Nothing here positions a row by hand,
 * and the generator's anchor overlay is the proof the bands are where the rows are.
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
        CHARACTER("Character", XenoNeonAtlas.NAV_CHARACTER, null),
        SKILLS("Skills", XenoNeonAtlas.NAV_SKILLS, SkillsMenuScreen::new),
        QUESTS("Quests", XenoNeonAtlas.NAV_QUESTS, QuestTreeScreen::new),
        MINIGAMES("Minigames", XenoNeonAtlas.NAV_ITEMS, MinigamesScreen::new),
        PARTY("Party", XenoNeonAtlas.NAV_PARTY, PartyMenuScreen::new),
        SETTINGS("Settings", XenoNeonAtlas.NAV_SETTINGS, ConfigMenuScreen::new);

        private final String label;
        private final Sprite sprite;
        private final Supplier<Screen> target;

        NavButton(String label, Sprite sprite, Supplier<Screen> target) {
            this.label = label;
            this.sprite = sprite;
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
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int uiMouseX = (int) Math.round(toUiX(mouseX));
        int uiMouseY = (int) Math.round(toUiY(mouseY));
        beginUiScale(graphics);
        try {
            StatsData data = stats();

            sprite(graphics, XenoNeonAtlas.NAMEPLATE, nameplateX, nameplateY, Part.NAMEPLATE);
            sprite(graphics, XenoNeonAtlas.INFO_PANEL, infoX, panelsY, Part.INFO_PANEL);
            sprite(graphics, XenoNeonAtlas.STATS_PANEL, statsX, panelsY, Part.STATS_PANEL);
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
        renderTransparentBackground(graphics);
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

    /** Level, TPs, Form, Class and the remaining assignable points, in the left slab's top block. */
    private void renderInformation(GuiGraphics graphics, StatsData data) {
        Character character = data.getCharacter();
        Resources resources = data.getResources();
        String[][] rows = {
                {"Level", Integer.toString(data.getLevel())},
                {"TPs", resources == null ? "" : StatText.format(resources.getTrainingPoints())},
                {"Form", character == null || character.getActiveForm().isBlank()
                        ? "Base" : StatText.title(character.getActiveForm())},
                {"Class", character == null ? "" : StatText.title(character.getCharacterClass())},
                {"Points", Integer.toString(data.getRemainingAssignableStats())}};

        int count = Math.min(rows.length, XenoNeonAtlas.INFO_ROWS.length);
        for (int i = 0; i < count; i++) {
            int y = panelsY + XenoNeonAtlas.INFO_ROWS[i][0] + TEXT_INSET;
            text(graphics, rows[i][0], infoX + XenoNeonAtlas.INFO_LABEL_X, y,
                    Part.INFO_LABEL, XenoNeonAtlas.LABEL);
            // Class is the one information row the reference render colours; the rest read white.
            int colour = "Class".equals(rows[i][0]) ? XenoNeonAtlas.RED : XenoNeonAtlas.VALUE;
            text(graphics, rows[i][1], infoX + XenoNeonAtlas.INFO_VALUE_X, y,
                    Part.INFO_VALUE, colour);
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

        for (int i = 0; i < XenoNeonAtlas.STAT_ROWS.length; i++) {
            int top = statRowY(i);
            int y = top + TEXT_INSET;
            boolean last = i >= values.length;

            int tint = last || !spendable ? 0x60FFFFFF
                    : overPlus(mouseX, mouseY, i) ? 0xFFB8FFFF : 0xFFFFFFFF;
            if (tint != 0xFFFFFFFF) {
                // The shells are part of the panel art, so only the button needs dimming, and it is
                // dimmed rather than hidden so no row moves when the last point is spent.
                graphics.fill(plusX(), plusY(i), plusX() + XenoNeonAtlas.PLUS_WIDTH,
                        plusY(i) + XenoNeonAtlas.PLUS_HEIGHT,
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

            int rowX = infoX + XenoDmzNeonConfig.partX(Part.STAT_ROWS);
            text(graphics, label, rowX + XenoNeonAtlas.STAT_LABEL_X, y, Part.STAT_LABEL, colour);
            String mult = StatText.multiplier(multiplier);
            int right = rowX + XenoNeonAtlas.STAT_RIGHT_X;
            right(graphics, mult, right, y, Part.STAT_MULTIPLIER,
                    StatText.isNeutral(multiplier) ? StatText.MULTIPLIER_NEUTRAL | 0xFF000000
                            : XenoNeonAtlas.GOLD);
            right(graphics, value, right - width(mult, Part.STAT_MULTIPLIER) - 4, y,
                    Part.STAT_VALUE, XenoNeonAtlas.VALUE);
        }
    }

    /** The right slab: seven derived statistics, then the summary box. */
    private void renderStatistics(GuiGraphics graphics, StatsData data) {
        double[] statistics = {
                data.getMeleeDamage(), data.getStrikeDamage(), data.getMaxStamina(),
                data.getDefense(), data.getMaxHealth(), data.getKiDamage(), data.getMaxEnergy()};
        int count = Math.min(statistics.length, XenoNeonAtlas.STATISTIC_ROWS.length);
        for (int i = 0; i < count; i++) {
            int blockX = statsX + XenoDmzNeonConfig.partX(Part.STATISTICS);
            int y = panelsY + XenoDmzNeonConfig.partY(Part.STATISTICS)
                    + XenoNeonAtlas.STATISTIC_ROWS[i][0] + TEXT_INSET;
            text(graphics, STATISTICS[i].label(), blockX + XenoNeonAtlas.STATISTIC_LABEL_X, y,
                    Part.STATISTIC_LABEL, XenoNeonAtlas.LABEL);
            right(graphics, StatText.format(statistics[i]),
                    blockX + XenoNeonAtlas.STATISTIC_RIGHT_X, y, Part.STATISTIC_VALUE,
                    STATISTICS[i].colour());
        }

        String[][] summary = {
                {"Power Level", StatText.format(data.getBattlePower())},
                {"Gravity", StatText.multiplier(data.getGravityStatMultiplier())},
                {"TP Multiplier", StatText.multiplier(data.getTpTotalMultiplier())}};
        int[] colours = {XenoNeonAtlas.VALUE, XenoNeonAtlas.ORANGE, XenoNeonAtlas.GOLD};
        int rows = Math.min(summary.length, XenoNeonAtlas.SUMMARY_ROWS.length);
        for (int i = 0; i < rows; i++) {
            int blockX = statsX + XenoDmzNeonConfig.partX(Part.SUMMARY);
            int y = panelsY + XenoDmzNeonConfig.partY(Part.SUMMARY)
                    + XenoNeonAtlas.SUMMARY_ROWS[i][0] + TEXT_INSET;
            text(graphics, summary[i][0], blockX + XenoNeonAtlas.SUMMARY_LABEL_X, y,
                    Part.SUMMARY_LABEL, i == 1 ? XenoNeonAtlas.ORANGE : XenoNeonAtlas.LABEL);
            right(graphics, summary[i][1], blockX + XenoNeonAtlas.SUMMARY_RIGHT_X, y,
                    Part.SUMMARY_VALUE, colours[i]);
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
        int centreX = ringX + XenoNeonAtlas.SCAN_RING.width() / 2
                + XenoDmzNeonConfig.partX(Part.SCAN_RING) + XenoDmzNeonConfig.partX(Part.CHARACTER);
        int bottomY = ringY + XenoNeonAtlas.SCAN_RING.height() - 8
                + XenoDmzNeonConfig.partY(Part.SCAN_RING) + XenoDmzNeonConfig.partY(Part.CHARACTER);
        int size = Math.max(8, XenoNeonAtlas.SCAN_RING.height() / 3);

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
            sprite(graphics, button.sprite, x, navY, Part.NAV_ROW);
            int drawX = x + XenoDmzNeonConfig.partX(Part.NAV_ROW);
            int drawY = navY + XenoDmzNeonConfig.partY(Part.NAV_ROW);
            if (button == NavButton.CHARACTER) {
                // This screen. Underlined so the row reads as a tab strip rather than six links.
                graphics.fill(drawX, drawY + button.sprite.height(),
                        drawX + button.sprite.width(), drawY + button.sprite.height() + 1,
                        XenoNeonAtlas.GOLD);
            } else if (overNav(button, mouseX, mouseY)) {
                graphics.fill(drawX, drawY, drawX + button.sprite.width(),
                        drawY + button.sprite.height(), 0x40FFFFFF);
            }
            x += button.sprite.width() + NAV_GAP;
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

    /** The top of one stat row shell, with the row block's own offset applied. */
    private int statRowY(int row) {
        return panelsY + XenoDmzNeonConfig.partY(Part.STAT_ROWS) + XenoNeonAtlas.STAT_ROWS[row][0];
    }

    private int plusX() {
        return infoX + XenoDmzNeonConfig.partX(Part.STAT_ROWS) + XenoNeonAtlas.PLUS_X
                + XenoDmzNeonConfig.partX(Part.PLUS_BUTTON);
    }

    private int plusY(int row) {
        return statRowY(row) + XenoNeonAtlas.PLUS_Y + XenoDmzNeonConfig.partY(Part.PLUS_BUTTON);
    }

    private boolean overPlus(double mouseX, double mouseY, int row) {
        if (row < 0 || row >= XenoNeonAtlas.STAT_ROWS.length) {
            return false;
        }
        int x = plusX();
        int y = plusY(row);
        return mouseX >= x && mouseX < x + XenoNeonAtlas.PLUS_WIDTH
                && mouseY >= y && mouseY < y + XenoNeonAtlas.PLUS_HEIGHT;
    }

    private boolean overNav(NavButton button, int mouseX, int mouseY) {
        int x = navX + XenoDmzNeonConfig.partX(Part.NAV_ROW);
        int y = navY + XenoDmzNeonConfig.partY(Part.NAV_ROW);
        for (NavButton candidate : NavButton.values()) {
            if (candidate == button) {
                break;
            }
            x += candidate.sprite.width() + NAV_GAP;
        }
        return mouseX >= x && mouseX < x + button.sprite.width()
                && mouseY >= y && mouseY < y + button.sprite.height();
    }

    private static int navWidth() {
        int width = -NAV_GAP;
        for (NavButton button : NavButton.values()) {
            width += button.sprite.width() + NAV_GAP;
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

    /** A sprite whose own part can move it. */
    private void sprite(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
        blit(graphics, sprite, x + XenoDmzNeonConfig.partX(part),
                y + XenoDmzNeonConfig.partY(part));
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

    private int width(String text, int part) {
        return (int) (this.font.width(styled(text, part)) * XenoDmzNeonConfig.partScale(part));
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int part, int designColour) {
        draw(graphics, text, x, y, part, designColour);
    }

    private void right(GuiGraphics graphics, String text, int rightEdge, int y, int part,
                       int designColour) {
        draw(graphics, text, rightEdge - width(text, part), y, part, designColour);
    }

    private void centred(GuiGraphics graphics, String text, int centreX, int y, int part,
                         int designColour) {
        draw(graphics, text, centreX - width(text, part) / 2, y, part, designColour);
    }

    /**
     * One styled, scaled, positioned readout.
     *
     * <p>{@code designColour} is the colour this particular row has in the reference render. It is
     * what shows unless the elements editor's override is on, because the design colours several of
     * these kinds per row — the stat labels alone run red, green, magenta and cyan.
     */
    private void draw(GuiGraphics graphics, String text, int x, int y, int part, int designColour) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = XenoDmzNeonConfig.partScale(part);
        graphics.pose().pushPose();
        graphics.pose().translate(x + XenoDmzNeonConfig.partX(part),
                y + XenoDmzNeonConfig.partY(part), 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(this.font, styled(text, part), 0, 0,
                XenoDmzNeonConfig.partColor(part, designColour), false);
        graphics.pose().popPose();
    }
}
