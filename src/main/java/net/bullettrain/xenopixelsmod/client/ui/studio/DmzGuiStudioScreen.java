package net.bullettrain.xenopixelsmod.client.ui.studio;

import com.dragonminez.client.gui.buttons.ColorSlider;
import com.dragonminez.client.gui.buttons.CustomTextureButton;
import com.dragonminez.client.gui.buttons.TexturedTextButton;
import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.XenoHudSnapshotFactory;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiActions;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRenderer;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRuntime;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiSnapshotBindings;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiLayoutEngine;
import net.bullettrain.xenopixelsmod.ui.StudioPreview;
import net.bullettrain.xenopixelsmod.ui.UiNode;
import net.bullettrain.xenopixelsmod.ui.UiNodeType;
import net.bullettrain.xenopixelsmod.ui.UiStudioSession;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;
import java.util.ArrayList;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

/**
 * DragonMineZ-native GUI Studio. Uses TechniqueCreator widgets and textures.
 * Does not send {@code CreateTechniqueC2S}.
 */
@OnlyIn(Dist.CLIENT)
public final class DmzGuiStudioScreen extends ScaledScreen {
    private static final ResourceLocation MENU_NPC = ResourceLocation.fromNamespaceAndPath(
            "dragonminez", "textures/gui/menu/menunpc.png");
    private static final ResourceLocation BUTTONS_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            "dragonminez", "textures/gui/buttons/characterbuttons.png");
    private static final int PANEL_W = 345;
    private static final int PANEL_H = 273;
    private static final int BTN_W = 74;
    private static final int BTN_H = 20;
    private static final int HANDLE = 6;
    private static final int LAYERS_W = 150;
    private static final int LAYER_ROW_H = 12;
    private static final String[][] TYPES = {
            {"Panel", "PANEL"}, {"Text", "TEXT"}, {"Bar", "PROGRESS_BAR"}, {"Btn", "BUTTON"},
            {"Img", "IMAGE"}, {"Icon", "ICON"}, {"Face", "PORTRAIT"}, {"HBox", "HBOX"},
            {"VBox", "VBOX"}, {"Scrl", "SCROLL"}, {"Tab", "NAV_TAB"}, {"Row", "STAT_ROW"},
            {"Slot", "SKILL_SLOT"}
    };

    private final UiStudioSession session;
    private int panelX;
    private int panelY;
    private int canvasW;
    private int canvasH;
    private UiLaidOut laidOut;
    private boolean dragging;
    private boolean resizing;
    private int dragOffX;
    private int dragOffY;
    private int dragLastDesignX;
    private int dragLastDesignY;
    private ResizeHandle resizeHandle = ResizeHandle.NONE;
    private int resizeStartX;
    private int resizeStartY;
    private int resizeNodeX;
    private int resizeNodeY;
    private int resizeNodeW;
    private int resizeNodeH;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox wBox;
    private EditBox hBox;
    private EditBox colorBox;
    private EditBox textBox;
    private EditBox bindBox;
    private EditBox actionBox;
    private EditBox idBox;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;
    private ColorSlider hueSlider;
    private ColorSlider saturationSlider;
    private ColorSlider valueSlider;

    public DmzGuiStudioScreen() {
        super(Component.literal("DMZ GUI Studio"));
        UiRuntime.reload();
        this.session = new UiStudioSession(new UiDocument());
        this.session.newMenu(UiRuntime.documents().keySet());
    }

    @Override
    protected int getMinGuiWidth() {
        return 720;
    }

    @Override
    protected int getMinGuiHeight() {
        return 420;
    }

    @Override
    protected void init() {
        super.init();
        rebuildChrome();
    }

    private void rebuildChrome() {
        clearWidgets();
        int uiW = getUiWidth();
        int uiH = getUiHeight();
        panelX = uiW - PANEL_W;
        panelY = Math.max(0, (uiH - PANEL_H) / 2);
        canvasW = Math.max(1, panelX - LAYERS_W);
        canvasH = Math.max(1, uiH);
        UiDocument doc = session.document();
        previewW = StudioPreview.previewW(doc.kind, canvasW, doc.canvasW);
        previewH = StudioPreview.previewH(doc.kind, canvasH, doc.canvasH);
        int[] origin = StudioPreview.origin(doc.kind, canvasW, canvasH, doc.canvasW, doc.canvasH);
        previewX = LAYERS_W + origin[0];
        previewY = origin[1];
        int x = panelX + 8;
        int y = panelY + 22;
        int col = 0;
        for (String[] type : TYPES) {
            addPlateButton(x + col * 80, y, type[0], b -> {
                session.addNode(type[1]);
                rebuildChrome();
            });
            col++;
            if (col == 4) {
                col = 0;
                y += 22;
            }
        }
        if (col != 0) {
            col = 0;
            y += 22;
        }
        addPlateButton(x, y, "SAVE", b -> save());
        addPlateButton(x + 80, y, "LOAD", b -> {
            UiRuntime.reload();
            UiDocument loaded = UiRuntime.document(session.document().id);
            if (loaded != null) {
                session.loadDocument(loaded);
            }
            rebuildChrome();
        });
        addPlateButton(x + 160, y, UiRuntime.enabled() ? "HUD ON" : "HUD OFF", b -> {
            UiRuntime.setEnabled(!UiRuntime.enabled());
            rebuildChrome();
        });
        addPlateButton(x + 240, y, "UNDO", b -> {
            session.undo();
            rebuildChrome();
        });
        y += 22;
        addPlateButton(x, y, "REDO", b -> {
            session.redo();
            rebuildChrome();
        });
        addPlateButton(x + 80, y, "DEL", b -> {
            session.deleteSelected();
            rebuildChrome();
        });
        addPlateButton(x + 160, y, "SNAP" + session.snap(), b -> {
            session.cycleSnap();
            rebuildChrome();
        });
        addPlateButton(x + 240, y, "APPLY", b -> {
            applyInspector();
            rebuildChrome();
        });
        y += 22;
        addPlateButton(x, y, "NEW", b -> {
            session.newMenu(takenIds());
            rebuildChrome();
        });
        addPlateButton(x + 80, y, "HUD", b -> {
            session.newHud(takenIds());
            rebuildChrome();
        });
        addPlateButton(x + 160, y, "AS", b -> {
            if (idBox != null) {
                session.saveAs(idBox.getValue());
                if (session.canSave()) {
                    UiRuntime.put(session.document());
                }
            }
            rebuildChrome();
        });
        addPlateButton(x + 240, y, "MENU", b -> {
            UiRuntime.reload();
            UiDocument stats = UiRuntime.document("xeno_stats");
            session.loadDocument(stats);
            rebuildChrome();
        });
        y += 22;
        idBox = box(x, y, 150, session.document().id == null ? "" : session.document().id);
        addPlateButton(x + 160, y - 4, "PAGE", b -> {
            session.cyclePage(1);
            rebuildChrome();
        });
        y += 20;
        UiNode selected = session.selected();
        xBox = box(x, y, 50, num(selected, "x"));
        yBox = box(x + 52, y, 50, num(selected, "y"));
        wBox = box(x + 104, y, 50, num(selected, "w"));
        hBox = box(x + 156, y, 50, num(selected, "h"));
        y += 16;
        colorBox = box(x, y, 90, selected == null || selected.color == null ? "" : selected.color);
        textBox = box(x + 92, y, 140, selected == null || selected.text == null ? "" : selected.text);
        y += 16;
        bindBox = box(x, y, 110, selected == null || selected.bind == null ? "" : selected.bind);
        actionBox = box(x + 112, y, 210, selected == null || selected.action == null ? "" : selected.action);
        y += 18;
        hueSlider = new ColorSlider.Builder()
                .position(x, y)
                .size(90, 10)
                .range(0, 360)
                .value(0)
                .message(Component.literal("Hue"))
                .onValueChange(v -> syncColorFromSliders())
                .build();
        saturationSlider = new ColorSlider.Builder()
                .position(x, y + 12)
                .size(90, 10)
                .range(100, 0)
                .value(100)
                .message(Component.literal("Saturation"))
                .onValueChange(v -> syncColorFromSliders())
                .build();
        valueSlider = new ColorSlider.Builder()
                .position(x, y + 24)
                .size(90, 10)
                .range(100, 0)
                .value(100)
                .message(Component.literal("Value"))
                .onValueChange(v -> syncColorFromSliders())
                .build();
        addRenderableWidget(hueSlider);
        addRenderableWidget(saturationSlider);
        addRenderableWidget(valueSlider);
        addRenderableWidget(createArrowButton(x + 250, y, true, btn -> {
            session.cyclePage(-1);
            rebuildChrome();
        }));
        addRenderableWidget(createArrowButton(x + 310, y, false, btn -> {
            session.cyclePage(1);
            rebuildChrome();
        }));
    }

    private void addPlateButton(int x, int y, String label, net.minecraft.client.gui.components.Button.OnPress onPress) {
        addRenderableWidget(new TexturedTextButton.Builder()
                .position(x, y)
                .size(BTN_W, BTN_H)
                .texture(BUTTONS_TEXTURE)
                .textureCoords(0, 28, 0, 48)
                .textureSize(BTN_W, BTN_H)
                .message(Component.literal(label))
                .onPress(onPress)
                .build());
    }

    private CustomTextureButton createArrowButton(int x, int y, boolean left,
                                                  net.minecraft.client.gui.components.Button.OnPress onPress) {
        return new CustomTextureButton.Builder()
                .position(x, y - 4)
                .size(10, 15)
                .texture(BUTTONS_TEXTURE)
                .textureCoords(left ? 32 : 20, 0, left ? 32 : 20, 14)
                .textureSize(8, 14)
                .message(Component.empty())
                .onPress(onPress)
                .build();
    }

    private EditBox box(int x, int y, int w, String value) {
        EditBox edit = new EditBox(font, x, y, w, 12, Component.empty());
        edit.setValue(value == null ? "" : value);
        edit.setMaxLength(128);
        addRenderableWidget(edit);
        return edit;
    }

    private static String num(UiNode node, String field) {
        if (node == null) {
            return "0";
        }
        return switch (field) {
            case "x" -> Integer.toString(node.x);
            case "y" -> Integer.toString(node.y);
            case "w" -> Integer.toString(node.w);
            case "h" -> Integer.toString(node.h);
            default -> "0";
        };
    }

    private void applyInspector() {
        UiNode selected = session.selected();
        session.applyInspector(new UiStudioSession.InspectorPatch(
                xBox.getValue(), yBox.getValue(), wBox.getValue(), hBox.getValue(),
                selected == null ? "0" : Integer.toString(selected.minW),
                selected == null ? "0" : Integer.toString(selected.minH),
                selected == null ? "0" : Integer.toString(selected.maxW),
                selected == null ? "0" : Integer.toString(selected.maxH),
                selected == null || selected.anchor == null ? "TOP_LEFT" : selected.anchor,
                selected == null ? "true" : Boolean.toString(selected.visible),
                colorBox.getValue(),
                selected == null || selected.texture == null ? "" : selected.texture,
                textBox.getValue(), bindBox.getValue(), actionBox.getValue()));
    }

    private java.util.Set<String> takenIds() {
        java.util.HashSet<String> ids = new java.util.HashSet<>(UiRuntime.documents().keySet());
        if (session.document().id != null) {
            ids.add(session.document().id);
        }
        return ids;
    }

    private void save() {
        applyInspector();
        if (!session.canSave()) {
            return;
        }
        UiRuntime.put(session.document());
    }

    private void syncColorFromSliders() {
        if (hueSlider == null || colorBox == null) {
            return;
        }
        colorBox.setValue(String.format("#%06X", hsvToRgb(
                hueSlider.getValue() / 360.0f,
                saturationSlider.getValue() / 100.0f,
                valueSlider.getValue() / 100.0f)));
    }

    private static int hsvToRgb(float h, float s, float v) {
        float hh = ((h % 1.0f) + 1.0f) % 1.0f * 6.0f;
        int i = (int) Math.floor(hh);
        float f = hh - i;
        float p = v * (1.0f - s);
        float q = v * (1.0f - f * s);
        float t = v * (1.0f - (1.0f - f) * s);
        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((Math.round(r * 255) & 0xFF) << 16)
                | ((Math.round(g * 255) & 0xFF) << 8)
                | (Math.round(b * 255) & 0xFF);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int uiMouseX = (int) Math.round(toUiX(mouseX));
        int uiMouseY = (int) Math.round(toUiY(mouseY));
        beginUiScale(graphics);
        graphics.fill(LAYERS_W, 0, LAYERS_W + canvasW, canvasH, 0xE6080B12);
        laidOut = UiLayoutEngine.layout(session.document(), previewW, previewH);
        graphics.pose().pushPose();
        graphics.pose().translate(previewX, previewY, 0);
        UiRenderer.draw(graphics, laidOut,
                new UiSnapshotBindings(XenoHudSnapshotFactory.capture(minecraft)), true);
        for (String selectedId : session.selectedIds()) {
            UiLaidOut selected = findLaid(laidOut, selectedId);
            if (selected == null) continue;
            int color = selectedId.equals(session.selectedId()) ? 0xFFFFC14A : 0xFF58B7FF;
            graphics.fill(selected.x - 1, selected.y - 1, selected.x + selected.w + 1, selected.y, color);
            graphics.fill(selected.x - 1, selected.y + selected.h, selected.x + selected.w + 1, selected.y + selected.h + 1, color);
            graphics.fill(selected.x - 1, selected.y, selected.x, selected.y + selected.h, color);
            graphics.fill(selected.x + selected.w, selected.y, selected.x + selected.w + 1, selected.y + selected.h, color);
            if (session.selectedIds().size() == 1) {
                drawResizeHandles(graphics, selected, color);
            }
        }
        graphics.pose().popPose();
        renderLayers(graphics);
        graphics.blit(MENU_NPC, panelX, panelY, 0.0F, 0.0F, PANEL_W, PANEL_H, 512, 512);
        graphics.drawString(font, "DMZ GUI Studio", panelX + 12, panelY + 8, 0xFFFFC14A, false);
        graphics.drawString(font, session.notice(), panelX + 140, panelY + 8, 0xFFFFC14A, false);
        String page = session.document().page == null ? "" : session.document().page;
        graphics.drawString(font, page, panelX + 270, panelY + 8, 0xFF8AA4B8, false);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0, 0.0, 400.0);
        super.render(graphics, uiMouseX, uiMouseY, partialTick);
        graphics.pose().popPose();
        endUiScale(graphics);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int absoluteUiX = (int) Math.round(toUiX(mouseX));
        int absoluteUiY = (int) Math.round(toUiY(mouseY));
        if (button == 0 && absoluteUiX >= 0 && absoluteUiX < LAYERS_W) {
            if (clickLayer(absoluteUiX, absoluteUiY)) return true;
        }
        double uiX = toUiX(mouseX) - previewX;
        double uiY = toUiY(mouseY) - previewY;
        if (button == 0 && uiX >= 0 && uiY >= 0 && uiX < previewW && uiY < previewH) {
            UiLaidOut selected = findLaid(laidOut, session.selectedId());
            ResizeHandle hitHandle = session.selectedIds().size() == 1 && selected != null
                    ? resizeHandle(selected, uiX, uiY) : ResizeHandle.NONE;
            if (hitHandle != ResizeHandle.NONE) {
                resizing = true;
                resizeHandle = hitHandle;
                session.pushUndo();
                float sx = previewW / (float) Math.max(1, session.document().canvasW);
                float sy = previewH / (float) Math.max(1, session.document().canvasH);
                resizeStartX = Math.round((float) uiX / sx);
                resizeStartY = Math.round((float) uiY / sy);
                UiNode node = session.selected();
                resizeNodeX = node.x;
                resizeNodeY = node.y;
                resizeNodeW = node.w;
                resizeNodeH = node.h;
                return true;
            }
            if (laidOut != null) {
                UiLaidOut hit = selectableHit(laidOut, (int) uiX, (int) uiY);
                if (hit != null && hit.source != null) {
                    boolean additive = hasShiftDown() || hasControlDown();
                    session.select(hit.source.id, additive);
                    if (!session.selectedIds().contains(hit.source.id)) {
                        rebuildChrome();
                        return true;
                    }
                    dragging = true;
                    session.pushUndo();
                    dragOffX = (int) uiX - hit.x;
                    dragOffY = (int) uiY - hit.y;
                    float sx = previewW / (float) Math.max(1, session.document().canvasW);
                    float sy = previewH / (float) Math.max(1, session.document().canvasH);
                    dragLastDesignX = Math.round((float) uiX / sx);
                    dragLastDesignY = Math.round((float) uiY / sy);
                    rebuildChrome();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        UiNode node = session.selected();
        if (node == null || button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        double uiX = toUiX(mouseX) - previewX;
        double uiY = toUiY(mouseY) - previewY;
        float sx = previewW / (float) Math.max(1, session.document().canvasW);
        float sy = previewH / (float) Math.max(1, session.document().canvasH);
        if (resizing) {
            int dx = Math.round((float) uiX / sx) - resizeStartX;
            int dy = Math.round((float) uiY / sy) - resizeStartY;
            int x = resizeNodeX;
            int y = resizeNodeY;
            int w = resizeNodeW;
            int h = resizeNodeH;
            if (resizeHandle.left) { x += dx; w -= dx; }
            if (resizeHandle.right) w += dx;
            if (resizeHandle.top) { y += dy; h -= dy; }
            if (resizeHandle.bottom) h += dy;
            session.transformSelected(x, y, w, h);
            return true;
        }
        if (dragging) {
            int designX = Math.round((float) uiX / sx);
            int designY = Math.round((float) uiY / sy);
            session.moveSelectionBy(designX - dragLastDesignX, designY - dragLastDesignY);
            dragLastDesignX = designX;
            dragLastDesignY = designY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (dragging || resizing)) {
            dragging = false;
            resizing = false;
            resizeHandle = ResizeHandle.NONE;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiNode node = session.selected();
        if (node != null && UiNodeType.byName(node.type) == UiNodeType.SCROLL) {
            node.scroll = Math.max(0, node.scroll - (int) Math.round(scrollY * 8));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int step = hasShiftDown() ? session.snap() : 1;
        int dx = keyCode == GLFW.GLFW_KEY_LEFT ? -step : keyCode == GLFW.GLFW_KEY_RIGHT ? step : 0;
        int dy = keyCode == GLFW.GLFW_KEY_UP ? -step : keyCode == GLFW.GLFW_KEY_DOWN ? step : 0;
        if (dx != 0 || dy != 0) {
            session.pushUndo();
            session.moveSelectionBy(dx, dy);
            rebuildChrome();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void renderLayers(GuiGraphics graphics) {
        graphics.fill(0, 0, LAYERS_W, canvasH, 0xF0121721);
        graphics.drawString(font, "Layers  " + session.selectedIds().size() + " selected", 6, 5, 0xFFFFC14A, false);
        List<LayerRow> rows = layerRows();
        for (int index = 0; index < rows.size(); index++) {
            int y = 18 + index * LAYER_ROW_H;
            if (y + LAYER_ROW_H > canvasH) break;
            LayerRow row = rows.get(index);
            if (session.selectedIds().contains(row.node.id)) graphics.fill(2, y, LAYERS_W - 2, y + LAYER_ROW_H, 0x80406C91);
            int color = row.node.editorHidden ? 0xFF66717C : row.node.editorLocked ? 0xFFFF9F43 : 0xFFE7EDF3;
            String name = row.node.id == null || row.node.id.isBlank() ? row.node.type : row.node.id;
            graphics.drawString(font, name, 6 + row.depth * 8, y + 2, color, false);
            graphics.drawString(font, row.node.editorHidden ? "H" : "V", 122, y + 2, 0xFF8AA4B8, false);
            graphics.drawString(font, row.node.editorLocked ? "L" : "-", 138, y + 2, 0xFF8AA4B8, false);
        }
    }

    private boolean clickLayer(int x, int y) {
        int index = (y - 18) / LAYER_ROW_H;
        List<LayerRow> rows = layerRows();
        if (y < 18 || index < 0 || index >= rows.size()) return false;
        UiNode node = rows.get(index).node;
        if (x >= 134) session.setEditorLocked(node.id, !node.editorLocked);
        else if (x >= 118) session.setEditorHidden(node.id, !node.editorHidden);
        else session.select(node.id, hasShiftDown() || hasControlDown());
        rebuildChrome();
        return true;
    }

    private List<LayerRow> layerRows() {
        List<LayerRow> rows = new ArrayList<>();
        collectLayers(session.document().root, 0, rows);
        return rows;
    }

    private static void collectLayers(UiNode node, int depth, List<LayerRow> rows) {
        if (node == null) return;
        rows.add(new LayerRow(node, depth));
        if (node.children != null) for (UiNode child : node.children) collectLayers(child, depth + 1, rows);
    }

    private UiLaidOut selectableHit(UiLaidOut root, int x, int y) {
        if (root == null || !root.contains(x, y)) return null;
        for (int index = root.children.size() - 1; index >= 0; index--) {
            UiLaidOut hit = selectableHit(root.children.get(index), x, y);
            if (hit != null) return hit;
        }
        return root.source != null && session.isCanvasSelectable(root.source.id) ? root : null;
    }

    private record LayerRow(UiNode node, int depth) {}

    private static ResizeHandle resizeHandle(UiLaidOut box, double mouseX, double mouseY) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            if (handle == ResizeHandle.NONE) continue;
            int x = handle.left ? box.x : handle.right ? box.x + box.w : box.x + box.w / 2;
            int y = handle.top ? box.y : handle.bottom ? box.y + box.h : box.y + box.h / 2;
            if (Math.abs(mouseX - x) <= HANDLE && Math.abs(mouseY - y) <= HANDLE) return handle;
        }
        return ResizeHandle.NONE;
    }

    private static void drawResizeHandles(GuiGraphics graphics, UiLaidOut box, int color) {
        for (ResizeHandle handle : ResizeHandle.values()) {
            if (handle == ResizeHandle.NONE) continue;
            int x = handle.left ? box.x : handle.right ? box.x + box.w : box.x + box.w / 2;
            int y = handle.top ? box.y : handle.bottom ? box.y + box.h : box.y + box.h / 2;
            graphics.fill(x - 2, y - 2, x + 3, y + 3, color);
        }
    }

    private enum ResizeHandle {
        NONE(false, false, false, false),
        NORTH_WEST(true, false, true, false),
        NORTH(false, false, true, false),
        NORTH_EAST(false, true, true, false),
        WEST(true, false, false, false),
        EAST(false, true, false, false),
        SOUTH_WEST(true, false, false, true),
        SOUTH(false, false, false, true),
        SOUTH_EAST(false, true, false, true);

        final boolean left;
        final boolean right;
        final boolean top;
        final boolean bottom;

        ResizeHandle(boolean left, boolean right, boolean top, boolean bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }
    }

    private static UiLaidOut findLaid(UiLaidOut root, String id) {
        if (root == null) {
            return null;
        }
        if (root.source != null && id != null && id.equals(root.source.id)) {
            return root;
        }
        List<UiLaidOut> children = root.children;
        if (children == null) {
            return null;
        }
        for (UiLaidOut child : children) {
            UiLaidOut found = findLaid(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
