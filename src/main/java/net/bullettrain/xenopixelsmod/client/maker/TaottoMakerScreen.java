package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.features.taotto.TaottoBodyPart;
import net.bullettrain.xenopixelsmod.features.taotto.TaottoDocument;
import net.bullettrain.xenopixelsmod.network.taotto.TaottoNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Taotto — per-pixel tattoo painter, then scale/drag onto a selected body-part island.
 *
 * <p>Apply stores an additive XenoPixels overlay. DMZ {@code tattooType} is unchanged.
 */
public final class TaottoMakerScreen extends ScaledScreen {
    private static final String BANNER = "banner_top";
    private static final String PANEL = "xeno_maker_part_grid";
    private static final String PREVIEW = "xeno_maker_hair_preview";
    private static final String TOOL = "mynpcs_button_row";
    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;
    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int OK = 0xFF9AFFB0;
    private static final int WARN = 0xFFFF8A80;
    private static final int CELL = 6;

    private final Screen parent;
    private final TaottoDocument document;
    private final MakerPreviewController preview = new MakerPreviewController();
    private final InlineColorPicker colorPicker = new InlineColorPicker();

    private String paintHex = "#111111";
    private boolean eraser;
    private boolean previewDirty = true;
    private boolean painting;
    private boolean dragging;
    private String status = "Paint on the grid, pick a body part, drag to place, scale, then Apply.";
    private int statusColor = MUTED;

    private int originX;
    private int originY;
    private int bannerX;
    private int bannerY;
    private int bannerW;
    private int bannerH;
    private int canvasX;
    private int canvasY;
    private int placeX;
    private int placeY;
    private int placeW;
    private int placeH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private int footerY;
    private EditBox colorBox;

    public TaottoMakerScreen(Screen parent) {
        this(parent, TaottoClientOverlays.document(
                net.minecraft.client.Minecraft.getInstance().player == null ? null
                        : net.minecraft.client.Minecraft.getInstance().player.getUUID()));
    }

    public TaottoMakerScreen(Screen parent, TaottoDocument document) {
        super(Component.literal("Taotto"));
        this.parent = parent;
        this.document = document == null ? TaottoDocument.blank() : document;
    }

    @Override
    protected int getMinGuiWidth() {
        return 640;
    }

    @Override
    protected int getMinGuiHeight() {
        return 420;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);

        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        previewW = XenoAtlasSprites.get(PREVIEW).width();
        previewH = XenoAtlasSprites.get(PREVIEW).height();
        int gridW = XenoAtlasSprites.get(PANEL).width();
        int gridH = XenoAtlasSprites.get(PANEL).height();

        int canvasPx = document.size() * CELL;
        int contentW = canvasPx + 16 + 160 + 8 + previewW;
        originX = Math.max(8, (getUiWidth() - contentW) / 2);
        originY = Math.max(4, (getUiHeight() - (bannerH + gridH + 48)) / 2);
        bannerX = originX + (contentW - bannerW) / 2;
        bannerY = originY;
        canvasX = originX;
        canvasY = bannerY + bannerH + 28;
        placeX = canvasX + canvasPx + 16;
        placeY = canvasY;
        placeW = 140;
        placeH = 140;
        previewX = placeX + placeW + 8;
        previewY = canvasY;
        footerY = Math.max(canvasY + canvasPx, previewY + previewH) + 8;

        clearWidgets();

        addRenderableWidget(new AtlasButton(originX, canvasY - 24,
                Component.literal(eraser ? "Eraser" : "Pen"), TOOL, b -> {
                    eraser = !eraser;
                    status = eraser ? "Eraser" : "Pen";
                    statusColor = MUTED;
                    rebuild();
                }));

        colorBox = new EditBox(font, originX + AtlasButton.nativeWidth(TOOL) + 8, canvasY - 24, 70, 16,
                Component.literal("paint"));
        colorBox.setMaxLength(9);
        colorBox.setValue(paintHex);
        colorBox.setResponder(v -> paintHex = v == null ? "#111111" : v);
        addRenderableWidget(colorBox);
        addRenderableWidget(new ColorSwatch(originX + AtlasButton.nativeWidth(TOOL) + 80, canvasY - 24,
                () -> paintHex,
                () -> colorPicker.open(colorBox, colorBox.getX() + colorBox.getWidth() + ColorSwatch.W + 6,
                        colorBox.getY(), getUiWidth(), getUiHeight(), paintHex, hex -> {
                            paintHex = hex;
                            colorBox.setValue(hex);
                        }),
                () -> colorPicker.isOpenFor(colorBox)));

        List<String> partLabels = new ArrayList<>();
        for (TaottoBodyPart part : TaottoBodyPart.values()) {
            partLabels.add(part.label());
        }
        int partIndex = document.part().ordinal();
        addRenderableWidget(new AtlasCycle(placeX, placeY + placeH + 8, Component.literal("Part"),
                partLabels, partIndex, this::onPartCycle));

        int compact = AtlasButton.nativeWidth(TOOL);
        addRenderableWidget(new AtlasButton(placeX, placeY + placeH + 32,
                Component.literal("Scale −"), TOOL, b -> {
                    document.scale(document.scale() / 1.25f);
                    previewDirty = true;
                    status = "Scale " + formatScale();
                    statusColor = MUTED;
                }));
        addRenderableWidget(new AtlasButton(placeX + compact + 4, placeY + placeH + 32,
                Component.literal("Scale +"), TOOL, b -> {
                    document.scale(document.scale() * 1.25f);
                    previewDirty = true;
                    status = "Scale " + formatScale();
                    statusColor = MUTED;
                }));

        addRenderableWidget(new AtlasButton(originX, footerY,
                Component.literal("Apply"), TOOL, b -> applyNow()));
        addRenderableWidget(new AtlasButton(originX + compact + 4, footerY,
                Component.literal("Clear"), TOOL, b -> {
                    document.clear();
                    previewDirty = true;
                    status = "Canvas cleared.";
                    statusColor = MUTED;
                }));
        AtlasButton applyTip = new AtlasButton(originX + (compact + 4) * 2, footerY,
                Component.literal("Close"), TOOL, b -> onClose());
        applyTip.setTooltip(Tooltip.create(Component.literal(
                "Apply writes an additive overlay. DMZ tattoo presets stay as they are.")));
        addRenderableWidget(applyTip);

        preview.markDirty();
    }

    private void rebuild() {
        colorPicker.close();
        init();
    }

    private void onPartCycle(int index) {
        TaottoBodyPart[] parts = TaottoBodyPart.values();
        if (index >= 0 && index < parts.length) {
            document.part(parts[index]);
            previewDirty = true;
            status = "Placing on " + parts[index].label();
            statusColor = MUTED;
        }
    }

    private void applyNow() {
        try {
            TaottoNetwork.apply(document);
            status = "Applied Taotto overlay on " + document.part().label()
                    + " · scale " + formatScale() + ".";
            statusColor = OK;
        } catch (Exception e) {
            status = "Apply failed: " + e.getMessage();
            statusColor = WARN;
        }
    }

    private String formatScale() {
        return String.format(java.util.Locale.ROOT, "%.2f", document.scale());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);
        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        try {
            beginUiScale(graphics);
            int uiMx = (int) toUiX(mouseX);
            int uiMy = (int) toUiY(mouseY);
            XenoAtlasSprites.setTheme(CHROME);
            XenoAtlasSprites.blit(graphics, BANNER, CHROME, bannerX, bannerY);
            graphics.drawCenteredString(font, "TAOTTO",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            XenoAtlasSprites.setTheme(INNER);
            drawCanvas(graphics);
            drawPlacement(graphics);
            XenoAtlasSprites.blit(graphics, PREVIEW, INNER, previewX, previewY);
            graphics.drawString(font, "Preview", previewX + 10, previewY + 8, GOLD, false);
            graphics.drawString(font, clip(status, 90), originX, footerY + 24, statusColor, false);
            graphics.drawString(font, document.part().label() + " · scale " + formatScale()
                    + " · " + document.paintedCount() + " px", originX, footerY + 36, MUTED, false);

            super.render(graphics, uiMx, uiMy, partialTick);
            if (previewDirty) {
                TaottoClientOverlays.updatePreview(document);
                previewDirty = false;
            }
            TaottoClientOverlays.withPreview(minecraft.player == null ? null : minecraft.player.getUUID(),
                    () -> preview.render(graphics, previewX + 8, previewY + 24,
                            previewW - 16, previewH - 32, partialTick));
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void drawCanvas(GuiGraphics graphics) {
        int size = document.size();
        graphics.fill(canvasX - 1, canvasY - 1, canvasX + size * CELL + 1, canvasY + size * CELL + 1, 0xFF1A1A1A);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int px = document.pixel(x, y);
                int color = ((px >>> 24) & 0xFF) == 0 ? (((x + y) & 1) == 0 ? 0xFF3A3A3A : 0xFF2A2A2A) : px;
                graphics.fill(canvasX + x * CELL, canvasY + y * CELL,
                        canvasX + (x + 1) * CELL, canvasY + (y + 1) * CELL, color);
            }
        }
        graphics.drawString(font, "Paint", canvasX, canvasY - 12, MUTED, false);
    }

    private void drawPlacement(GuiGraphics graphics) {
        graphics.fill(placeX, placeY, placeX + placeW, placeY + placeH, 0xFF102018);
        graphics.fill(placeX + 2, placeY + 2, placeX + placeW - 2, placeY + placeH - 2, 0xFF0B2A18);
        TaottoBodyPart.UvIsland island = document.part().front();
        float sx = (placeW - 16f) / Math.max(1, island.w());
        float sy = (placeH - 16f) / Math.max(1, island.h());
        float scale = Math.min(sx, sy);
        int ix = placeX + 8;
        int iy = placeY + 8;
        int iw = Math.round(island.w() * scale);
        int ih = Math.round(island.h() * scale);
        graphics.fill(ix, iy, ix + iw, iy + ih, 0xFF3D5A45);
        int[] overlay = document.bakeOverlay(64);
        for (int y = 0; y < island.h(); y++) {
            for (int x = 0; x < island.w(); x++) {
                int color = overlay[(island.v() + y) * 64 + island.u() + x];
                if ((color >>> 24) == 0) continue;
                graphics.fill(ix + Math.round(x * scale), iy + Math.round(y * scale),
                        ix + Math.round((x + 1) * scale), iy + Math.round((y + 1) * scale), color);
            }
        }
        graphics.drawString(font, "Drag", placeX, placeY - 12, MUTED, false);
    }

    private void paintAt(double uiMx, double uiMy) {
        if (!inCanvas(uiMx, uiMy)) return;
        int x = (int) ((uiMx - canvasX) / CELL);
        int y = (int) ((uiMy - canvasY) / CELL);
        previewDirty = true;
        if (eraser) {
            document.clearPixel(x, y);
        } else {
            document.setPixel(x, y, parseHex(paintHex));
        }
    }

    private static int parseHex(String hex) {
        return net.bullettrain.xenopixelsmod.dmz.race.RaceBodyPng.parseHex(hex, 0xFF111111);
    }

    private boolean inCanvas(double uiMx, double uiMy) {
        int size = document.size() * CELL;
        return uiMx >= canvasX && uiMx < canvasX + size && uiMy >= canvasY && uiMy < canvasY + size;
    }

    private boolean inPlace(double uiMx, double uiMy) {
        return uiMx >= placeX && uiMx < placeX + placeW && uiMy >= placeY && uiMy < placeY + placeH;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (button == 0 && inCanvas(uiMx, uiMy)) {
            painting = true;
            paintAt(uiMx, uiMy);
            return true;
        }
        if (button == 0 && inPlace(uiMx, uiMy)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (colorPicker.isOpen() && colorPicker.mouseDragged(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (painting) {
            paintAt(uiMx, uiMy);
            return true;
        }
        if (dragging) {
            TaottoBodyPart.UvIsland island = document.part().front();
            float scaleX = (placeW - 16f) / Math.max(1, island.w());
            float scaleY = (placeH - 16f) / Math.max(1, island.h());
            float scale = Math.min(scaleX, scaleY);
            previewDirty = true;
            document.dragBy((float) (toUiX(dx) / Math.max(0.01f, scale)), (float) (toUiY(dy) / Math.max(0.01f, scale)));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        painting = false;
        dragging = false;
        if (colorPicker.isOpen() && colorPicker.mouseReleased(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) {
            return true;
        }
        if (inPlace(toUiX(mouseX), toUiY(mouseY))) {
            document.scale(document.scale() * (scrollY > 0 ? 1.25f : 0.8f));
            previewDirty = true;
            status = "Scale " + formatScale();
            statusColor = MUTED;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return colorPicker.keyPressed(keyCode, scanCode, modifiers)
                || super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return colorPicker.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
    }

    @Override
    public void removed() {
        TaottoClientOverlays.clearPreview();
        previewDirty = true;
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public TaottoDocument document() {
        return document;
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }
}
