package net.bullettrain.xenopixelsmod.client.screen;

import com.dragonminez.client.gui.character.ConfigMenuScreen;
import com.dragonminez.client.gui.character.MinigamesScreen;
import com.dragonminez.client.gui.character.PartyMenuScreen;
import com.dragonminez.client.gui.character.QuestTreeScreen;
import com.dragonminez.client.gui.character.SkillsMenuScreen;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import com.dragonminez.common.network.C2S.IncreaseStatC2S;
import com.dragonminez.common.network.NetworkHandler;
import com.dragonminez.common.stats.StatsCapability;
import com.dragonminez.common.stats.StatsData;
import com.dragonminez.common.stats.StatsProvider;
import com.dragonminez.common.stats.character.Character;
import com.dragonminez.common.stats.character.Stats;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoDmzScreenConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas.Sprite;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.function.Supplier;

/**
 * XenoPixels' own DragonMineZ character screen, the one V opens.
 *
 * <p>Built from the HD element kit rather than from DragonMineZ's textures: separate panels, header
 * shells, row shells, navigation buttons and icons, each drawn at its own proportions. That kit's
 * README asks for exactly this — the shells carry no baked numbers, and the label, value and
 * multiplier are drawn in code over them.
 *
 * <p><b>The numbers are DragonMineZ's, not a copy of them.</b> Everything is read live from the
 * player's {@link StatsData}, and the {@code +} buttons send DMZ's own {@link IncreaseStatC2S}, so
 * the server path is untouched and this screen cannot drift out of agreement with the stock one.
 *
 * <p>Every piece is a {@link Part}, so the whole screen is editable through the same elements editor
 * the HUDs use. With the override off it draws exactly as designed.
 */
public class XenoDmzStatsScreen extends ScaledScreen {

    /** Stat rows, in the order DMZ lists them, paired with the packet type each button sends. */
    private enum StatRow {
        STR("STR", IncreaseStatC2S.StatType.STR),
        SKP("SKP", IncreaseStatC2S.StatType.SKP),
        RES("RES", IncreaseStatC2S.StatType.RES),
        VIT("VIT", IncreaseStatC2S.StatType.VIT),
        PWR("PWR", IncreaseStatC2S.StatType.PWR),
        ENE("ENE", IncreaseStatC2S.StatType.ENE);

        private final String label;
        private final IncreaseStatC2S.StatType type;

        StatRow(String label, IncreaseStatC2S.StatType type) {
            this.label = label;
            this.type = type;
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
     * <p>The kit's sixth button is an item pouch; it carries minigames, because that is the sixth
     * menu DragonMineZ actually has, and the tooltip says so.
     */
    private enum NavButton {
        CHARACTER("Character", XenoDmzHdAtlas.NAV_CHARACTER, null),
        SKILLS("Skills", XenoDmzHdAtlas.NAV_SKILLS, SkillsMenuScreen::new),
        QUESTS("Quests", XenoDmzHdAtlas.NAV_QUESTS, QuestTreeScreen::new),
        MINIGAMES("Minigames", XenoDmzHdAtlas.NAV_ITEMS, MinigamesScreen::new),
        PARTY("Party", XenoDmzHdAtlas.NAV_PARTY, PartyMenuScreen::new),
        SETTINGS("Settings", XenoDmzHdAtlas.NAV_SETTINGS, ConfigMenuScreen::new);

        private final String label;
        private final Sprite sprite;
        private final Supplier<Screen> target;

        NavButton(String label, Sprite sprite, Supplier<Screen> target) {
            this.label = label;
            this.sprite = sprite;
            this.target = target;
        }
    }

    private static final int PANEL_GAP = 16;
    private static final int NAV_GAP = 6;
    private static final int STAT_ROW_STEP = 17;
    private static final int BASIC_ROW_STEP = 13;
    private static final int STAT_BUTTON_OFFSET = 4;

    private int infoX;
    private int statsX;
    private int panelsY;
    private int nameplateX;
    private int nameplateY;
    private int navX;
    private int navY;
    private int statRowsY;

    public XenoDmzStatsScreen() {
        super(Component.translatable("screen.xenopixelsmod.dmz_stats"));
    }

    @Override
    protected void init() {
        int uiWidth = getUiWidth();
        int uiHeight = getUiHeight();
        int panelWidth = XenoDmzHdAtlas.INFO_PANEL.width();
        int total = panelWidth + PANEL_GAP + XenoDmzHdAtlas.STATS_PANEL.width();

        infoX = (uiWidth - total) / 2;
        statsX = infoX + panelWidth + PANEL_GAP;
        panelsY = (uiHeight - XenoDmzHdAtlas.STATS_PANEL.height()) / 2 + 14;

        nameplateX = (uiWidth - XenoDmzHdAtlas.NAMEPLATE.width()) / 2;
        nameplateY = Math.max(2, panelsY - XenoDmzHdAtlas.NAMEPLATE.height() - 6);

        navX = (uiWidth - navWidth()) / 2;
        navY = panelsY + XenoDmzHdAtlas.STATS_PANEL.height() + NAV_GAP;

        statRowsY = panelsY + 108;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        int uiMouseX = (int) Math.round(toUiX(mouseX));
        int uiMouseY = (int) Math.round(toUiY(mouseY));
        beginUiScale(graphics);
        try {
            StatsData data = stats();

            sprite(graphics, XenoDmzHdAtlas.NAMEPLATE, nameplateX, nameplateY, Part.NAMEPLATE);
            sprite(graphics, XenoDmzHdAtlas.INFO_PANEL, infoX, panelsY, Part.INFO_PANEL);
            sprite(graphics, XenoDmzHdAtlas.STATS_PANEL, statsX, panelsY, Part.STATS_PANEL);
            renderNav(graphics, uiMouseX, uiMouseY);

            if (data == null) {
                centred(graphics, "No DragonMineZ character",
                        nameplateX + XenoDmzHdAtlas.NAMEPLATE.width() / 2, nameplateY + 16,
                        Part.NAME);
            } else {
                renderNameplate(graphics, data);
                renderInformation(graphics, data, uiMouseX, uiMouseY);
                renderStatistics(graphics, data);
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

    private void renderNameplate(GuiGraphics graphics, StatsData data) {
        Character character = data.getCharacter();
        int centre = nameplateX + XenoDmzHdAtlas.NAMEPLATE.width() / 2;
        String name = this.minecraft != null && this.minecraft.player != null
                ? this.minecraft.player.getGameProfile().getName() : "";
        centred(graphics, name, centre, nameplateY + 11, Part.NAME);
        if (character != null) {
            String subtitle = StatText.title(character.getRaceName());
            String gender = StatText.title(character.getGender());
            if (!gender.isBlank()) {
                subtitle = subtitle + "  -  " + gender;
            }
            centred(graphics, subtitle, centre, nameplateY + 24, Part.RACE);
        }
    }

    private void renderInformation(GuiGraphics graphics, StatsData data,
                                   int mouseX, int mouseY) {
        Character character = data.getCharacter();
        sprite(graphics, XenoDmzHdAtlas.INFO_HEADER, infoX + 15, panelsY + 5, Part.INFO_HEADER);
        sprite(graphics, XenoDmzHdAtlas.KANJI_CIRCLE, infoX + 6, panelsY + 8, Part.INFO_HEADER);
        centred(graphics, "INFORMATION", infoX + XenoDmzHdAtlas.INFO_PANEL.width() / 2 + 6,
                panelsY + 11, Part.INFO_HEADER);

        int x = infoX + 12;
        int y = panelsY + 34;
        y = basicRow(graphics, x, y, "Level", Integer.toString(data.getLevel()));
        y = basicRow(graphics, x, y, "Form", character == null || character.getActiveForm().isBlank()
                ? "Base" : StatText.title(character.getActiveForm()));
        y = basicRow(graphics, x, y, "Class",
                character == null ? "" : StatText.title(character.getCharacterClass()));
        basicRow(graphics, x, y, "Points", Integer.toString(data.getRemainingAssignableStats()));

        sprite(graphics, XenoDmzHdAtlas.STATS_SUBHEADER, infoX + 27, panelsY + 84,
                Part.STATS_SUBHEADER);
        centred(graphics, "STATS", infoX + XenoDmzHdAtlas.INFO_PANEL.width() / 2,
                panelsY + 90, Part.STATS_SUBHEADER);

        Stats stats = data.getStats();
        if (stats == null) {
            return;
        }
        boolean spendable = data.getRemainingAssignableStats() > 0;
        int rowX = infoX + XenoDmzScreenConfig.partX(Part.STAT_ROWS) + 10;
        int rowY = statRowsY + XenoDmzScreenConfig.partY(Part.STAT_ROWS);
        for (StatRow row : StatRow.values()) {
            blit(graphics, XenoDmzHdAtlas.STAT_ROW, rowX, rowY);
            // The + is dimmed rather than hidden with no points left, so the rows do not shift.
            int buttonX = statButtonX(rowX);
            int buttonY = statButtonY(rowY);
            int tint = !spendable ? 0x60FFFFFF
                    : overStatButton(mouseX, mouseY, buttonX, buttonY) ? 0xFFB8FFFF
                    : 0xFFFFFFFF;
            blit(graphics, XenoDmzHdAtlas.PLUS_BUTTON, buttonX, buttonY, tint);

            text(graphics, row.label, rowX + 19, rowY + 5, Part.STAT_LABEL);

            double multiplier = data.getTotalMultiplier(row.multiplierKey());
            String mult = StatText.multiplier(multiplier);
            String value = StatText.format(row.value(stats) * multiplier);
            int right = rowX + XenoDmzHdAtlas.STAT_ROW.width() - 6;
            right(graphics, mult, right, rowY + 5, Part.STAT_MULTIPLIER,
                    StatText.isNeutral(multiplier) ? StatText.MULTIPLIER_NEUTRAL : -1);
            right(graphics, value, right - width(mult, Part.STAT_MULTIPLIER) - 4, rowY + 5,
                    Part.STAT_VALUE, -1);
            rowY += STAT_ROW_STEP;
        }
    }

    private void renderStatistics(GuiGraphics graphics, StatsData data) {
        sprite(graphics, XenoDmzHdAtlas.STATS_HEADER, statsX + 15, panelsY + 5, Part.STATS_HEADER);
        sprite(graphics, XenoDmzHdAtlas.STATS_BARS_ICON, statsX + 6, panelsY + 8, Part.STATS_HEADER);
        centred(graphics, "STATISTICS", statsX + XenoDmzHdAtlas.STATS_PANEL.width() / 2 + 6,
                panelsY + 11, Part.STATS_HEADER);

        int x = statsX + XenoDmzScreenConfig.partX(Part.STATISTICS) + 12;
        int y = panelsY + 36 + XenoDmzScreenConfig.partY(Part.STATISTICS);
        int right = statsX + XenoDmzHdAtlas.STATS_PANEL.width() - 12;
        y = statLine(graphics, x, y, right, "Melee DMG", data.getMeleeDamage());
        y = statLine(graphics, x, y, right, "Strike DMG", data.getStrikeDamage());
        y = statLine(graphics, x, y, right, "Ki DMG", data.getKiDamage());
        y = statLine(graphics, x, y, right, "Defense", data.getDefense());
        y = statLine(graphics, x, y, right, "Health", data.getMaxHealth());
        y = statLine(graphics, x, y, right, "Stamina", data.getMaxStamina());
        statLine(graphics, x, y, right, "Max Ki", data.getMaxEnergy());

        int boxX = statsX + 10 + XenoDmzScreenConfig.partX(Part.BOTTOM_BOX);
        int boxY = panelsY + XenoDmzHdAtlas.STATS_PANEL.height() - 60
                + XenoDmzScreenConfig.partY(Part.BOTTOM_BOX);
        blit(graphics, XenoDmzHdAtlas.BOTTOM_BOX, boxX, boxY);
        text(graphics, "Power Level", boxX + 8, boxY + 10, Part.STATISTIC_LABEL);
        right(graphics, StatText.format(data.getBattlePower()),
                boxX + XenoDmzHdAtlas.BOTTOM_BOX.width() - 8, boxY + 10, Part.STATISTIC_VALUE, -1);
    }

    private void renderNav(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = navX + XenoDmzScreenConfig.partX(Part.NAV_ROW);
        int y = navY + XenoDmzScreenConfig.partY(Part.NAV_ROW);
        for (NavButton button : NavButton.values()) {
            blit(graphics, button.sprite, x, y);
            if (button == NavButton.CHARACTER) {
                // This screen. Underlined so the row reads as a tab strip rather than six links.
                graphics.fill(x, y + button.sprite.height() + 1,
                        x + button.sprite.width(), y + button.sprite.height() + 3, 0xFFFECC22);
            } else if (overNav(button, mouseX, mouseY)) {
                graphics.fill(x, y, x + button.sprite.width(), y + button.sprite.height(),
                        0x40FFFFFF);
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
        double uiMouseX = toUiX(mouseX);
        double uiMouseY = toUiY(mouseY);
        if (button == 0) {
            for (NavButton nav : NavButton.values()) {
                if (nav.target != null && overNav(nav, (int) uiMouseX, (int) uiMouseY)) {
                    if (this.minecraft != null) {
                        this.minecraft.setScreen(nav.target.get());
                    }
                    return true;
                }
            }
            StatsData data = stats();
            if (data != null && data.getRemainingAssignableStats() > 0) {
                int rowX = infoX + XenoDmzScreenConfig.partX(Part.STAT_ROWS) + 10;
                int rowY = statRowsY + XenoDmzScreenConfig.partY(Part.STAT_ROWS);
                for (StatRow row : StatRow.values()) {
                    int buttonX = statButtonX(rowX);
                    int buttonY = statButtonY(rowY);
                    if (overStatButton(uiMouseX, uiMouseY, buttonX, buttonY)) {
                        // DragonMineZ's own packet: it validates the spend and owns the result, so
                        // this screen can never grant a point the stock screen would have refused.
                        NetworkHandler.sendToServer(new IncreaseStatC2S(row.type, 1));
                        return true;
                    }
                    rowY += STAT_ROW_STEP;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private static int statButtonX(int rowX) {
        return rowX + STAT_BUTTON_OFFSET + XenoDmzScreenConfig.partX(Part.PLUS_BUTTON);
    }

    private static int statButtonY(int rowY) {
        return rowY + STAT_BUTTON_OFFSET + XenoDmzScreenConfig.partY(Part.PLUS_BUTTON);
    }

    private static boolean overStatButton(double mouseX, double mouseY, int x, int y) {
        int size = XenoDmzHdAtlas.PLUS_BUTTON.width();
        return mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int basicRow(GuiGraphics graphics, int x, int y, String key, String value) {
        text(graphics, key, x, y, Part.BASIC_LABEL);
        text(graphics, value, x + 56, y, Part.BASIC_VALUE);
        blit(graphics, XenoDmzHdAtlas.INFO_DIVIDER, infoX + 10, y + 10);
        return y + BASIC_ROW_STEP;
    }

    private int statLine(GuiGraphics graphics, int x, int y, int right, String key, double value) {
        text(graphics, key, x, y, Part.STATISTIC_LABEL);
        right(graphics, StatText.format(value), right, y, Part.STATISTIC_VALUE, -1);
        blit(graphics, XenoDmzHdAtlas.ROW_DIVIDER, statsX + 10, y + 10);
        return y + BASIC_ROW_STEP;
    }

    private boolean overNav(NavButton button, int mouseX, int mouseY) {
        int x = navX + XenoDmzScreenConfig.partX(Part.NAV_ROW);
        int y = navY + XenoDmzScreenConfig.partY(Part.NAV_ROW);
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
        blit(graphics, sprite, x + XenoDmzScreenConfig.partX(part),
                y + XenoDmzScreenConfig.partY(part));
    }

    private void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoDmzHdAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoDmzHdAtlas.ATLAS_WIDTH, XenoDmzHdAtlas.ATLAS_HEIGHT);
    }

    private void blit(GuiGraphics graphics, Sprite sprite, int x, int y, int tint) {
        graphics.setColor(((tint >> 16) & 0xFF) / 255f, ((tint >> 8) & 0xFF) / 255f,
                (tint & 0xFF) / 255f, ((tint >>> 24) & 0xFF) / 255f);
        blit(graphics, sprite, x, y);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private Component styled(String text, int part) {
        return Component.literal(text).setStyle(Style.EMPTY
                .withBold(XenoDmzScreenConfig.partBold[part])
                .withFont(XenoDmzScreenConfig.partFontLocation(part)));
    }

    private int width(String text, int part) {
        return (int) (this.font.width(styled(text, part)) * XenoDmzScreenConfig.partScale(part));
    }

    private void text(GuiGraphics graphics, String text, int x, int y, int part) {
        draw(graphics, text, x, y, part, -1);
    }

    private void right(GuiGraphics graphics, String text, int rightEdge, int y, int part, int colour) {
        draw(graphics, text, rightEdge - width(text, part), y, part, colour);
    }

    private void centred(GuiGraphics graphics, String text, int centreX, int y, int part) {
        draw(graphics, text, centreX - width(text, part) / 2, y, part, -1);
    }

    /** One styled, scaled, positioned readout. {@code colour} of -1 means the part's own colour. */
    private void draw(GuiGraphics graphics, String text, int x, int y, int part, int colour) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = XenoDmzScreenConfig.partScale(part);
        Font font = this.font;
        graphics.pose().pushPose();
        graphics.pose().translate(x + XenoDmzScreenConfig.partX(part),
                y + XenoDmzScreenConfig.partY(part), 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, styled(text, part), 0, 0,
                colour == -1 ? XenoDmzScreenConfig.partColor(part) : 0xFF000000 | colour, false);
        graphics.pose().popPose();
    }
}
