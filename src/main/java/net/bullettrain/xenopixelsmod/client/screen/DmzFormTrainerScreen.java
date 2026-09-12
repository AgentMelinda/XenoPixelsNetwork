package net.bullettrain.xenopixelsmod.client.screen;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig;
import net.bullettrain.xenopixelsmod.client.config.XenoMasterMenuConfig.Part;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas;
import net.bullettrain.xenopixelsmod.client.hud.XenoDmzHdAtlas.Sprite;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The DragonMineZ skill-master menu opened by interacting with a configured trainer NPC.
 *
 * <p>Replaces the placeholder list of raw form-type ids with the kit's own chrome: a nameplate
 * carrying the trainer's title, a left panel for the configured body text and a right panel for
 * the forms that trainer offers. Every row is one purchasable form, so the screen is also the
 * purchase affordance — clicking a row sends the same request the command path uses.
 *
 * <p>Player-facing names are resolved on the client, because only the client knows the player's
 * language. The server sends an {@code en_us} label with each row as a safety net; the resolution
 * order is the player's locale, then {@code en_us} inside the registry, then that sent label, and
 * finally the internal form id, so a row is never blank.
 */
public final class DmzFormTrainerScreen extends ScaledScreen {

    private static final int PANEL_GAP = 4;
    private static final int ROW_STEP = 19;
    private static final int MAX_ROWS = 8;
    private static final int BODY_LINE_STEP = 10;
    private static final int MAX_BODY_LINES = 11;
    private static final int ROW_INSET = 10;
    private static final int PANEL_INSET = 7;
    private static final int ICON_SIZE = 12;
    /** Bundled icons are authored at 64x64; see the Form Studio icon set. */
    private static final int ICON_TEXTURE_SIZE = 64;
    private static final int PANEL_FILL = 0xFF0B1220;

    private static final int TITLE_COLOUR = 0xFFE2C078;
    private static final int BODY_COLOUR = 0xFFD8D8D8;
    private static final int LABEL_COLOUR = 0xFFFFFFFF;
    private static final int MUTED_COLOUR = 0xFF9A9A9A;

    private final int trainerEntityId;
    private final String trainerName;
    private final String title;
    private final String body;
    private final List<DmzTrainerMenu.Entry> entries;

    private int bodyX;
    private int formsX;
    private int panelsY;
    private int nameplateX;
    private int nameplateY;

    public DmzFormTrainerScreen(int trainerEntityId, String trainerName, String title, String body,
                                List<DmzTrainerMenu.Entry> entries) {
        super(Component.literal("DMZ Form Training"));
        this.trainerEntityId = trainerEntityId;
        this.trainerName = trainerName == null ? "Trainer" : trainerName;
        this.title = title == null ? "" : title;
        this.body = body == null ? "" : body;
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    @Override
    protected void init() {
        int uiWidth = getUiWidth();
        int uiHeight = getUiHeight();
        int bodyWidth = XenoDmzHdAtlas.INFO_PANEL.width();
        int formsWidth = XenoDmzHdAtlas.STATS_PANEL.width();
        int total = bodyWidth + PANEL_GAP + formsWidth;

        bodyX = Math.max(0, (uiWidth - total) / 2);
        formsX = bodyX + bodyWidth + PANEL_GAP;
        panelsY = Math.max(0, (uiHeight - XenoDmzHdAtlas.STATS_PANEL.height()) / 2 + 12);

        nameplateX = Math.max(0, (uiWidth - XenoDmzHdAtlas.NAMEPLATE.width()) / 2);
        nameplateY = Math.max(2, panelsY - XenoDmzHdAtlas.NAMEPLATE.height() - 4);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int uiMouseX = (int) Math.round(toUiX(mouseX));
        int uiMouseY = (int) Math.round(toUiY(mouseY));
        beginUiScale(graphics);
        try {
            fillCluster(graphics);
            blit(graphics, XenoDmzHdAtlas.NAMEPLATE,
                    nameplateX + px(Part.NAMEPLATE), nameplateY + py(Part.NAMEPLATE));
            blit(graphics, XenoDmzHdAtlas.INFO_PANEL,
                    bodyX + px(Part.MASTER_PANEL), panelsY + py(Part.MASTER_PANEL));
            blit(graphics, XenoDmzHdAtlas.STATS_PANEL,
                    formsX + px(Part.FORMS_PANEL), panelsY + py(Part.FORMS_PANEL));
            fillPanelInterior(graphics, bodyX + px(Part.MASTER_PANEL),
                    panelsY + py(Part.MASTER_PANEL), XenoDmzHdAtlas.INFO_PANEL);
            fillPanelInterior(graphics, formsX + px(Part.FORMS_PANEL),
                    panelsY + py(Part.FORMS_PANEL), XenoDmzHdAtlas.STATS_PANEL);

            renderNameplate(graphics);
            renderBody(graphics);
            renderForms(graphics, uiMouseX, uiMouseY);

            super.render(graphics, uiMouseX, uiMouseY, partialTick);
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

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            double uiMouseX = toUiX(mouseX);
            double uiMouseY = toUiY(mouseY);
            int rowX = formsX + ROW_INSET + px(Part.FORM_ROWS) + px(Part.FORMS_PANEL);
            int rowY = panelsY + 36 + py(Part.FORM_ROWS) + py(Part.FORMS_PANEL);
            float rowScale = XenoMasterMenuConfig.partScale(Part.FORM_ROWS);
            int rowWidth = Math.round(XenoDmzHdAtlas.STAT_ROW.width() * rowScale);
            int rowHeight = Math.round(XenoDmzHdAtlas.STAT_ROW.height() * rowScale);
            int visible = Math.min(MAX_ROWS, entries.size());
            for (int i = 0; i < visible; i++) {
                DmzTrainerMenu.Entry entry = entries.get(i);
                if (!XenoMasterMenuConfig.hidden(Part.FORM_ROWS)
                        && uiMouseX >= rowX && uiMouseX < rowX + rowWidth
                        && uiMouseY >= rowY && uiMouseY < rowY + rowHeight) {
                    FormEditorNetwork.purchase(trainerEntityId, entry.formType());
                    return true;
                }
                rowY += ROW_STEP;
            }
            if (overClose(uiMouseX, uiMouseY)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void renderNameplate(GuiGraphics graphics) {
        int centre = nameplateX + px(Part.NAMEPLATE) + XenoDmzHdAtlas.NAMEPLATE.width() / 2;
        String heading = title == null || title.isBlank() ? trainerName : title;
        graphics.drawCenteredString(this.font, trim(heading, 34),
                centre + px(Part.TITLE), nameplateY + py(Part.NAMEPLATE) + 10 + py(Part.TITLE),
                XenoMasterMenuConfig.partColor(Part.TITLE));
        graphics.drawCenteredString(this.font, trim(trainerName, 34),
                centre + px(Part.TRAINER_NAME),
                nameplateY + py(Part.NAMEPLATE) + 23 + py(Part.TRAINER_NAME),
                XenoMasterMenuConfig.partColor(Part.TRAINER_NAME));
    }

    private void renderBody(GuiGraphics graphics) {
        int panelX = bodyX + px(Part.MASTER_PANEL);
        int panelY = panelsY + py(Part.MASTER_PANEL);
        renderHeader(graphics, panelX, panelY, XenoDmzHdAtlas.INFO_HEADER,
                XenoDmzHdAtlas.KANJI_CIRCLE, XenoDmzHdAtlas.INFO_PANEL.width(),
                Part.MASTER_HEADER, Part.MASTER_HEADER_ICON, Part.MASTER_HEADER_LABEL, "MASTER");

        int x = panelX + 12 + px(Part.MASTER_BODY);
        int y = panelY + 38 + py(Part.MASTER_BODY);
        int bodyColour = XenoMasterMenuConfig.partColor(Part.MASTER_BODY);
        for (FormattedCharSequence line : bodyLines()) {
            graphics.drawString(this.font, line, x, y, bodyColour, false);
            y += BODY_LINE_STEP;
        }

        int boxX = closeBoxX();
        int boxY = closeBoxY();
        blitPart(graphics, XenoDmzHdAtlas.BOTTOM_BOX, boxX, boxY, Part.CLOSE_BOX);
        int boxCentre = boxX + Math.round(XenoDmzHdAtlas.BOTTOM_BOX.width()
                * XenoMasterMenuConfig.partScale(Part.CLOSE_BOX) / 2.0f);
        headerLabel(graphics, "Close", boxCentre, boxY + 12, Part.CLOSE_LABEL);
        headerLabel(graphics, "ESC", boxCentre, boxY + 26, Part.CLOSE_HINT);
    }

    private void renderForms(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelX = formsX + px(Part.FORMS_PANEL);
        int panelY = panelsY + py(Part.FORMS_PANEL);
        renderHeader(graphics, panelX, panelY, XenoDmzHdAtlas.STATS_HEADER,
                XenoDmzHdAtlas.STATS_BARS_ICON, XenoDmzHdAtlas.STATS_PANEL.width(),
                Part.FORMS_HEADER, Part.FORMS_HEADER_ICON, Part.FORMS_HEADER_LABEL, "FORMS");

        int rowX = panelX + ROW_INSET + px(Part.FORM_ROWS);
        int rowY = panelY + 36 + py(Part.FORM_ROWS);
        int clipLeft = uiToGui(panelX + PANEL_INSET);
        int clipTop = uiToGui(panelY + 32);
        int clipRight = uiToGui(panelX + XenoDmzHdAtlas.STATS_PANEL.width() - PANEL_INSET);
        int clipBottom = uiToGui(panelY + XenoDmzHdAtlas.STATS_PANEL.height() - PANEL_INSET);
        graphics.enableScissor(clipLeft, clipTop, clipRight, clipBottom);
        try {
            if (entries.isEmpty()) {
                graphics.drawString(this.font, "No forms offered", rowX + 6, rowY + 5, MUTED_COLOUR, false);
                return;
            }

            int visible = Math.min(MAX_ROWS, entries.size());
            float rowScale = XenoMasterMenuConfig.partScale(Part.FORM_ROWS);
            int rowWidth = Math.min(
                    Math.round(XenoDmzHdAtlas.STAT_ROW.width() * rowScale),
                    XenoDmzHdAtlas.STATS_PANEL.width() - ROW_INSET * 2);
            int rowHeight = Math.round(XenoDmzHdAtlas.STAT_ROW.height() * rowScale);
            for (int i = 0; i < visible; i++) {
                DmzTrainerMenu.Entry entry = entries.get(i);
                boolean hovered = mouseX >= rowX && mouseX < rowX + rowWidth
                        && mouseY >= rowY && mouseY < rowY + rowHeight;
                blitPart(graphics, XenoDmzHdAtlas.STAT_ROW, rowX, rowY, Part.FORM_ROWS);
                if (hovered) {
                    graphics.fill(rowX, rowY, rowX + rowWidth, rowY + rowHeight, 0x40FFFFFF);
                }
                renderIcon(graphics, entry,
                        rowX + 4 + px(Part.FORM_ICON), rowY + 3 + py(Part.FORM_ICON));
                graphics.drawString(this.font, trim(label(entry), 18),
                        rowX + 20 + px(Part.FORM_LABEL), rowY + 5 + py(Part.FORM_LABEL),
                        hovered ? XenoMasterMenuConfig.partColor(Part.FORMS_HEADER_LABEL)
                                : XenoMasterMenuConfig.partColor(Part.FORM_LABEL), false);
                rowY += ROW_STEP;
            }

            if (entries.size() > visible) {
                graphics.drawString(this.font, "+" + (entries.size() - visible) + " more",
                        rowX + 6, rowY + 2, MUTED_COLOUR, false);
            }
        } finally {
            graphics.disableScissor();
        }
    }

    private void renderIcon(GuiGraphics graphics, DmzTrainerMenu.Entry entry, int x, int y) {
        ResourceLocation icon = resolveIcon(entry);
        if (icon != null) {
            try {
                graphics.blit(icon, x, y, ICON_SIZE, ICON_SIZE, 0f, 0f,
                        ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
                return;
            } catch (RuntimeException ignored) {
                // Missing or undersized textures throw from NativeImage; fall back to the kit orb.
            }
        }
        blit(graphics, XenoDmzHdAtlas.ORB_BLUE, x, y);
    }

    /** Opaque fill so the world NPC cannot show through the kit's hollow frames. */
    private void fillPanelInterior(GuiGraphics graphics, int x, int y, Sprite panel) {
        graphics.fill(x + PANEL_INSET, y + PANEL_INSET,
                x + panel.width() - PANEL_INSET, y + panel.height() - PANEL_INSET, PANEL_FILL);
    }

    private void fillCluster(GuiGraphics graphics) {
        int left = Math.min(nameplateX, bodyX) - 4;
        int top = nameplateY - 3;
        int right = Math.max(nameplateX + XenoDmzHdAtlas.NAMEPLATE.width(),
                formsX + XenoDmzHdAtlas.STATS_PANEL.width()) + 4;
        int bottom = panelsY + XenoDmzHdAtlas.STATS_PANEL.height() + 4;
        graphics.fill(left, top, right, bottom, 0xE0080C12);
    }

    private boolean overClose(double mouseX, double mouseY) {
        if (XenoMasterMenuConfig.hidden(Part.CLOSE_BOX)) return false;
        int boxX = closeBoxX();
        int boxY = closeBoxY();
        float scale = XenoMasterMenuConfig.partScale(Part.CLOSE_BOX);
        int width = Math.round(XenoDmzHdAtlas.BOTTOM_BOX.width() * scale);
        int height = Math.round(XenoDmzHdAtlas.BOTTOM_BOX.height() * scale);
        return mouseX >= boxX && mouseX < boxX + width
                && mouseY >= boxY && mouseY < boxY + height;
    }

    private int closeBoxX() {
        return bodyX + px(Part.MASTER_PANEL) + 10 + px(Part.CLOSE_BOX);
    }

    private int closeBoxY() {
        return panelsY + py(Part.MASTER_PANEL)
                + XenoDmzHdAtlas.INFO_PANEL.height() - XenoDmzHdAtlas.BOTTOM_BOX.height() - 6
                + py(Part.CLOSE_BOX);
    }

    private static int px(int part) {
        return XenoMasterMenuConfig.partX(part);
    }

    private static int py(int part) {
        return XenoMasterMenuConfig.partY(part);
    }

    private List<FormattedCharSequence> bodyLines() {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (body.isBlank()) {
            return lines;
        }
        int width = XenoDmzHdAtlas.INFO_PANEL.width() - 24;
        for (FormattedCharSequence line : this.font.split(Component.literal(body), width)) {
            if (lines.size() >= MAX_BODY_LINES) {
                break;
            }
            lines.add(line);
        }
        return lines;
    }

    /** Player's locale, then the registry's {@code en_us}, then the sent label, then the id. */
    private static String label(DmzTrainerMenu.Entry entry) {
        DmzFormKind kind = kind(entry);
        String localized = DmzFormMetadataRegistry.displayName(kind, entry.race(), entry.group(),
                entry.formId(), locale());
        if (localized != null && !localized.isBlank()) {
            return localized;
        }
        if (entry.label() != null && !entry.label().isBlank()) {
            return entry.label();
        }
        return entry.formId() == null ? "" : entry.formId();
    }

    /** Per-form icon, then the configured form-type icon; {@code null} falls back to a kit orb. */
    private static ResourceLocation resolveIcon(DmzTrainerMenu.Entry entry) {
        DmzFormKind kind = kind(entry);
        ResourceLocation perForm = DmzFormMetadataRegistry.formIcon(kind, entry.race(), entry.group(),
                entry.formId());
        return perForm != null ? perForm : DmzFormMetadataRegistry.formTypeIcon(entry.formType());
    }

    private static DmzFormKind kind(DmzTrainerMenu.Entry entry) {
        if (entry.kind() != null) {
            for (DmzFormKind candidate : DmzFormKind.values()) {
                if (candidate.name().equalsIgnoreCase(entry.kind())) {
                    return candidate;
                }
            }
        }
        return DmzFormKind.NORMAL;
    }

    private static String locale() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft == null || minecraft.getLanguageManager() == null
                ? "en_us" : minecraft.getLanguageManager().getSelected();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "\u2026";
    }

    /** Inverse of {@code toUiX}: ScaledScreen draws UI space as {@code gui = ui * uiScale}. */
    private int uiToGui(int ui) {
        int uiWidth = getUiWidth();
        if (uiWidth <= 0 || this.width <= 0) return ui;
        return (int) Math.round(ui * (this.width / (double) uiWidth));
    }

    private void renderHeader(GuiGraphics graphics, int panelX, int panelY, Sprite bar, Sprite icon,
                              int panelWidth, int barPart, int iconPart, int labelPart, String label) {
        blitPart(graphics, bar, panelX + 15 + px(barPart), panelY + 5 + py(barPart), barPart);
        blitPart(graphics, icon,
                panelX + 6 + px(barPart) + px(iconPart),
                panelY + 8 + py(barPart) + py(iconPart),
                iconPart);
        headerLabel(graphics, label,
                panelX + panelWidth / 2 + 6 + px(barPart),
                panelY + 13 + py(barPart),
                labelPart);
    }

    private void blitPart(GuiGraphics graphics, Sprite sprite, int x, int y, int part) {
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

    private void headerLabel(GuiGraphics graphics, String text, int centreX, int y, int part) {
        if (XenoMasterMenuConfig.hidden(part)) return;
        Component styled = Component.literal(text).setStyle(Style.EMPTY
                .withBold(XenoMasterMenuConfig.partBold[part])
                .withFont(XenoMasterMenuConfig.partFontLocation(part)));
        float scale = XenoMasterMenuConfig.partScale(part);
        int width = Math.round(this.font.width(styled) * scale);
        graphics.pose().pushPose();
        graphics.pose().translate(centreX - width / 2.0f + px(part), y + py(part), 0);
        if (Math.abs(scale - 1.0f) > 0.001f) {
            graphics.pose().scale(scale, scale, 1f);
        }
        graphics.drawString(this.font, styled, 0, 0, XenoMasterMenuConfig.partColor(part), true);
        graphics.pose().popPose();
    }

    private void blit(GuiGraphics graphics, Sprite sprite, int x, int y) {
        graphics.blit(XenoDmzHdAtlas.TEXTURE, x, y, sprite.width(), sprite.height(),
                sprite.u(), sprite.v(), sprite.sourceWidth(), sprite.sourceHeight(),
                XenoDmzHdAtlas.ATLAS_WIDTH, XenoDmzHdAtlas.ATLAS_HEIGHT);
    }
}
