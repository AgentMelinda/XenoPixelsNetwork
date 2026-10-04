package net.bullettrain.xenopixelsmod.client.ui.studio;

import net.bullettrain.xenopixelsmod.client.XenoHudSnapshotFactory;
import net.bullettrain.xenopixelsmod.client.screen.UnblurredScreen;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiActions;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRenderer;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiRuntime;
import net.bullettrain.xenopixelsmod.client.ui.runtime.UiSnapshotBindings;
import net.bullettrain.xenopixelsmod.ui.UiDocument;
import net.bullettrain.xenopixelsmod.ui.UiDocumentIO;
import net.bullettrain.xenopixelsmod.ui.UiDocumentValidator;
import net.bullettrain.xenopixelsmod.ui.UiLaidOut;
import net.bullettrain.xenopixelsmod.ui.UiLayoutEngine;
import net.bullettrain.xenopixelsmod.ui.UiNode;
import net.bullettrain.xenopixelsmod.ui.UiNodeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Vanilla chrome editor. Not the concept PNG docking IDE. Saves JSON under
 * {@code config/xenopixelsmod/ui/}.
 */
@OnlyIn(Dist.CLIENT)
public final class UiStudioScreen extends UnblurredScreen {
    private static final int[] SNAPS = {1, 2, 4, 8, 16};
    private static final int HANDLE = 6;
    private static final int INSPECTOR_H = 132;
    /** Native height of the generated ui_chip family; toolbar rows are pitched to it. */
    private static final int CHIP_H = 18;

    private UiDocument document;
    private String selectedId = "root";
    private String notice = "DMZ-style menu editor — save JSON under config/xenopixelsmod/ui/";
    private UiLaidOut laidOut;
    private EditBox textBox;
    private EditBox bindBox;
    private EditBox actionBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox wBox;
    private EditBox hBox;
    private EditBox minWBox;
    private EditBox minHBox;
    private EditBox maxWBox;
    private EditBox maxHBox;
    private EditBox anchorBox;
    private EditBox visibleBox;
    private EditBox colorBox;
    private EditBox textureBox;
    private boolean dragging;
    private boolean resizing;
    private int dragOffX;
    private int dragOffY;
    private int snapIndex = 3;
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();

    public UiStudioScreen() {
        super(Component.literal("DMZ Menu Studio"));
        UiRuntime.reload();
        UiDocument loaded = UiRuntime.hudDocument();
        this.document = loaded != null ? loaded : new UiDocument();
        if (this.document.id == null || this.document.id.isBlank()) {
            this.document.id = "demo_hud";
            this.document.kind = "hud";
        }
        this.selectedId = this.document.root.id;
    }

    @Override
    protected void init() {
        rebuildChrome();
    }

    private int snap() {
        return SNAPS[snapIndex];
    }

    private void rebuildChrome() {
        clearWidgets();
        int y = 6;
        addType("Panel", "PANEL", 8, y, 40);
        addType("Text", "TEXT", 50, y, 36);
        addType("Bar", "PROGRESS_BAR", 88, y, 32);
        addType("Btn", "BUTTON", 122, y, 32);
        addType("Img", "IMAGE", 156, y, 32);
        addType("Icon", "ICON", 190, y, 32);
        addType("Face", "PORTRAIT", 224, y, 36);
        addType("HBox", "HBOX", 262, y, 36);
        addType("VBox", "VBOX", 300, y, 36);
        addType("Scrl", "SCROLL", 338, y, 36);
        addType("Tab", "NAV_TAB", 376, y, 32);
        addType("Row", "STAT_ROW", 410, y, 32);
        addType("Slot", "SKILL_SLOT", 444, y, 32);
        addRenderableWidget(new AtlasButton(width - 28, y, 20, CHIP_H, Component.literal("X"),
                XenoAtlasSprites.chip(20), b -> onClose()));

        y = 24;
        addRenderableWidget(new AtlasButton(8, y, 40, CHIP_H, Component.literal("SAVE"),
                XenoAtlasSprites.chip(40), b -> save()));
        addRenderableWidget(new AtlasButton(50, y, 40, CHIP_H, Component.literal("LOAD"),
                XenoAtlasSprites.chip(40), b -> reload()));
        addRenderableWidget(new AtlasButton(92, y, 54, CHIP_H, Component.literal(UiRuntime.enabled() ? "HUD ON" : "HUD OFF"),
                XenoAtlasSprites.chip(54), b -> {
                    UiRuntime.setEnabled(!UiRuntime.enabled());
                    rebuildChrome();
                }));
        addRenderableWidget(new AtlasButton(148, y, 40, CHIP_H, Component.literal("UNDO"),
                XenoAtlasSprites.chip(40), b -> undo()));
        addRenderableWidget(new AtlasButton(190, y, 40, CHIP_H, Component.literal("REDO"),
                XenoAtlasSprites.chip(40), b -> redo()));
        addRenderableWidget(new AtlasButton(232, y, 32, CHIP_H, Component.literal("DEL"),
                XenoAtlasSprites.chip(32), b -> deleteSelected()));
        addRenderableWidget(new AtlasButton(266, y, 52, CHIP_H, Component.literal("SNAP" + snap()),
                XenoAtlasSprites.chip(52), b -> {
                    snapIndex = (snapIndex + 1) % SNAPS.length;
                    rebuildChrome();
                }));
        addRenderableWidget(new AtlasButton(320, y, 44, CHIP_H, Component.literal("APPLY"),
                XenoAtlasSprites.chip(44), b -> applyInspector()));
        addRenderableWidget(new AtlasButton(366, y, 40, CHIP_H, Component.literal("OPEN"),
                XenoAtlasSprites.chip(40), b -> {
                    UiNode selected = find(document.root, selectedId);
                    if (selected != null && selected.action != null && !selected.action.isBlank()) {
                        UiActions.fire(selected.action);
                    } else {
                        UiActions.open("demo_screen");
                    }
                }));
        addRenderableWidget(new AtlasButton(408, y, 40, CHIP_H, Component.literal("MENU"),
                XenoAtlasSprites.chip(40), b -> loadStatsTemplate()));

        int layerY = 44;
        for (UiNode node : flatten(document.root)) {
            String id = node.id;
            addRenderableWidget(new AtlasButton(width - 120, layerY, 112, CHIP_H, Component.literal(id.equals(selectedId) ? ">" + id : id),
                XenoAtlasSprites.chip(112), b -> {
                        selectedId = id;
                        rebuildChrome();
                    }));
            layerY += CHIP_H + 2;
            if (layerY > height - INSPECTOR_H - 20) {
                break;
            }
        }

        int ix = 8;
        int iy = height - INSPECTOR_H + 4;
        UiNode selected = find(document.root, selectedId);
        xBox = box(ix, iy, 44, num(selected, "x"));
        yBox = box(ix + 46, iy, 44, num(selected, "y"));
        wBox = box(ix + 92, iy, 44, num(selected, "w"));
        hBox = box(ix + 138, iy, 44, num(selected, "h"));
        minWBox = box(ix + 184, iy, 40, selected == null ? "0" : Integer.toString(selected.minW));
        minHBox = box(ix + 226, iy, 40, selected == null ? "0" : Integer.toString(selected.minH));
        maxWBox = box(ix + 268, iy, 40, selected == null ? "0" : Integer.toString(selected.maxW));
        maxHBox = box(ix + 310, iy, 40, selected == null ? "0" : Integer.toString(selected.maxH));
        anchorBox = box(ix, iy + 18, 90, selected == null || selected.anchor == null ? "TOP_LEFT" : selected.anchor);
        visibleBox = box(ix + 92, iy + 18, 40, selected == null ? "true" : Boolean.toString(selected.visible));
        colorBox = box(ix + 134, iy + 18, 90, selected == null || selected.color == null ? "" : selected.color);
        textureBox = box(ix + 226, iy + 18, 124, selected == null || selected.texture == null ? "" : selected.texture);
        textBox = box(ix, iy + 36, 160, selected == null || selected.text == null ? "" : selected.text);
        bindBox = box(ix + 164, iy + 36, 186, selected == null || selected.bind == null ? "" : selected.bind);
        actionBox = box(ix, iy + 54, 350, selected == null || selected.action == null ? "" : selected.action);
    }

    private void addType(String label, String type, int x, int y, int w) {
        addRenderableWidget(new AtlasButton(x, y, w, CHIP_H, Component.literal(label),
                XenoAtlasSprites.chip(w), b -> addNode(type)));
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

    private EditBox box(int x, int y, int w, String value) {
        EditBox edit = new EditBox(font, x, y, w, 16, Component.literal("field"));
        edit.setValue(value);
        edit.setMaxLength(128);
        addRenderableWidget(edit);
        return edit;
    }

    private void pushUndo() {
        undo.addLast(UiDocumentIO.toJson(document));
        redo.clear();
        while (undo.size() > 32) {
            undo.removeFirst();
        }
    }

    private void undo() {
        if (undo.isEmpty()) {
            notice = "Nothing to undo";
            return;
        }
        redo.addLast(UiDocumentIO.toJson(document));
        restore(undo.removeLast());
        notice = "Undo";
    }

    private void redo() {
        if (redo.isEmpty()) {
            notice = "Nothing to redo";
            return;
        }
        undo.addLast(UiDocumentIO.toJson(document));
        restore(redo.removeLast());
        notice = "Redo";
    }

    private void restore(String json) {
        UiDocumentIO.UiLoadResult result = UiDocumentIO.fromJson(json);
        if (result.ok()) {
            document = result.document();
            rebuildChrome();
        }
    }

    private void addNode(String type) {
        pushUndo();
        UiNode parent = find(document.root, selectedId);
        if (parent == null) {
            parent = document.root;
        }
        UiNode child = new UiNode();
        child.id = uniqueId(type);
        child.type = type;
        child.x = snap();
        child.y = snap();
        child.w = switch (type) {
            case "PROGRESS_BAR" -> 160;
            case "PORTRAIT" -> 36;
            case "ICON" -> 16;
            case "SCROLL" -> 120;
            case "NAV_TAB" -> 48;
            case "SKILL_SLOT" -> 24;
            default -> 80;
        };
        child.h = switch (type) {
            case "BUTTON" -> 18;
            case "PORTRAIT" -> 36;
            case "ICON" -> 16;
            case "SCROLL" -> 80;
            case "NAV_TAB" -> 14;
            case "SKILL_SLOT" -> 24;
            default -> 14;
        };
        if (type.equals("TEXT")) {
            child.text = "Text";
        }
        if (type.equals("PROGRESS_BAR")) {
            child.bind = "player.hpPercent";
        }
        if (type.equals("BUTTON")) {
            child.text = "Open";
            child.action = "open_document:demo_screen";
        }
        if (type.equals("NAV_TAB")) {
            child.text = "Stats";
            child.action = "dmz_page:stats";
        }
        if (type.equals("STAT_ROW")) {
            child.text = "Stat";
            child.bind = "player.level";
        }
        if (parent.children == null) {
            parent.children = new ArrayList<>();
        }
        parent.children.add(child);
        selectedId = child.id;
        rebuildChrome();
        notice = "Added " + child.id;
    }

    private void deleteSelected() {
        if ("root".equals(selectedId) || document.root.id.equals(selectedId)) {
            notice = "Cannot delete root";
            return;
        }
        UiNode parent = findParent(document.root, selectedId);
        if (parent == null || parent.children == null) {
            notice = "No parent";
            return;
        }
        pushUndo();
        parent.children.removeIf(child -> selectedId.equals(child.id));
        selectedId = parent.id;
        rebuildChrome();
        notice = "Deleted";
    }

    private String uniqueId(String type) {
        String base = type.toLowerCase().replace('_', '-');
        int i = 1;
        while (find(document.root, base + i) != null) {
            i++;
        }
        return base + i;
    }

    private void applyInspector() {
        UiNode selected = find(document.root, selectedId);
        if (selected == null) {
            return;
        }
        pushUndo();
        try {
            selected.x = snapTo(Integer.parseInt(xBox.getValue().trim()));
            selected.y = snapTo(Integer.parseInt(yBox.getValue().trim()));
            selected.w = Math.max(4, Integer.parseInt(wBox.getValue().trim()));
            selected.h = Math.max(4, Integer.parseInt(hBox.getValue().trim()));
            selected.minW = Math.max(0, Integer.parseInt(minWBox.getValue().trim()));
            selected.minH = Math.max(0, Integer.parseInt(minHBox.getValue().trim()));
            selected.maxW = Math.max(0, Integer.parseInt(maxWBox.getValue().trim()));
            selected.maxH = Math.max(0, Integer.parseInt(maxHBox.getValue().trim()));
        } catch (NumberFormatException ex) {
            notice = "numeric fields must be integers";
            return;
        }
        selected.anchor = anchorBox.getValue().trim();
        selected.visible = !"false".equalsIgnoreCase(visibleBox.getValue().trim());
        selected.color = colorBox.getValue().trim();
        selected.texture = textureBox.getValue().trim();
        selected.text = textBox.getValue();
        selected.bind = bindBox.getValue().trim();
        selected.action = actionBox.getValue().trim();
        List<String> errors = UiDocumentValidator.validate(document);
        notice = errors.isEmpty() ? "Inspector applied" : String.join("; ", errors);
        rebuildChrome();
    }

    private void save() {
        applyInspector();
        List<String> errors = UiDocumentValidator.validate(document);
        if (!errors.isEmpty()) {
            notice = String.join("; ", errors);
            return;
        }
        UiRuntime.put(document);
        notice = "Saved " + document.id + " to config/xenopixelsmod/ui/";
    }

    private void loadStatsTemplate() {
        pushUndo();
        UiRuntime.reload();
        UiDocument loaded = UiRuntime.document("xeno_stats");
        if (loaded == null) {
            notice = "xeno_stats template missing";
            return;
        }
        document = loaded;
        selectedId = document.root.id;
        rebuildChrome();
        notice = "Loaded DMZ stats template";
    }

    private void reload() {
        UiRuntime.reload();
        UiDocument loaded = UiRuntime.hudDocument();
        if (loaded != null) {
            document = loaded;
            selectedId = document.root.id;
        }
        rebuildChrome();
        notice = UiRuntime.lastError().isBlank() ? "Reloaded" : UiRuntime.lastError();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        laidOut = UiLayoutEngine.layout(document, width, height);
        UiRenderer.draw(graphics, laidOut,
                new UiSnapshotBindings(XenoHudSnapshotFactory.capture(minecraft)), true);
        UiLaidOut selected = findLaid(laidOut, selectedId);
        if (selected != null) {
            graphics.fill(selected.x - 1, selected.y - 1, selected.x + selected.w + 1, selected.y, 0xFFFFC14A);
            graphics.fill(selected.x - 1, selected.y + selected.h, selected.x + selected.w + 1,
                    selected.y + selected.h + 1, 0xFFFFC14A);
            graphics.fill(selected.x + selected.w - HANDLE, selected.y + selected.h - HANDLE,
                    selected.x + selected.w + 1, selected.y + selected.h + 1, 0xFFFFC14A);
        }
        graphics.fill(0, 0, width, 42, 0xE6080B12);
        graphics.fill(0, height - INSPECTOR_H - 28, width, height, 0xE6080B12);
        graphics.drawString(font, "DMZ Menu Studio", 8, height - INSPECTOR_H - 40, 0xFFFFC14A, false);
        new AtlasNotice(notice, 8, height - INSPECTOR_H - 20).render(graphics, font);
        graphics.drawString(font, "Layers", width - 120, 32, 0xFF8AA4B8, false);
        graphics.drawString(font, "x y w h minW minH maxW maxH  /  anchor vis color texture  /  text bind action",
                8, height - INSPECTOR_H - 24, 0xFF8AA4B8, false);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseY > 42 && mouseY < height - INSPECTOR_H - 16 && mouseX < width - 124) {
            UiLaidOut selected = findLaid(laidOut, selectedId);
            if (selected != null && onHandle(selected, mouseX, mouseY)) {
                resizing = true;
                pushUndo();
                return true;
            }
            if (laidOut != null) {
                UiLaidOut hit = UiLayoutEngine.hit(laidOut, (int) mouseX, (int) mouseY);
                if (hit != null && hit.source != null) {
                    selectedId = hit.source.id;
                    dragging = true;
                    pushUndo();
                    dragOffX = (int) mouseX - hit.x;
                    dragOffY = (int) mouseY - hit.y;
                    rebuildChrome();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        UiNode node = find(document.root, selectedId);
        if (node == null || button != 0) {
            return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        }
        float sx = width / (float) Math.max(1, document.canvasW);
        float sy = height / (float) Math.max(1, document.canvasH);
        if (resizing) {
            node.w = Math.max(4, snapTo(Math.round((int) mouseX / sx) - node.x));
            node.h = Math.max(4, snapTo(Math.round((int) mouseY / sy) - node.y));
            if (wBox != null) {
                wBox.setValue(Integer.toString(node.w));
                hBox.setValue(Integer.toString(node.h));
            }
            return true;
        }
        if (dragging) {
            node.x = snapTo(Math.round(((int) mouseX - dragOffX) / sx));
            node.y = snapTo(Math.round(((int) mouseY - dragOffY) / sy));
            if (xBox != null) {
                xBox.setValue(Integer.toString(node.x));
                yBox.setValue(Integer.toString(node.y));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && (dragging || resizing)) {
            dragging = false;
            resizing = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiNode node = find(document.root, selectedId);
        if (node != null && UiNodeType.byName(node.type) == UiNodeType.SCROLL) {
            node.scroll = Math.max(0, node.scroll - (int) Math.round(scrollY * 8));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 && actionBox != null && actionBox.isFocused()) {
            UiActions.fire(actionBox.getValue());
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int snapTo(int value) {
        int step = snap();
        return Math.round(value / (float) step) * step;
    }

    private static boolean onHandle(UiLaidOut box, double mouseX, double mouseY) {
        return mouseX >= box.x + box.w - HANDLE && mouseX <= box.x + box.w + 2
                && mouseY >= box.y + box.h - HANDLE && mouseY <= box.y + box.h + 2;
    }

    private static UiNode find(UiNode root, String id) {
        if (root == null) {
            return null;
        }
        if (id != null && id.equals(root.id)) {
            return root;
        }
        if (root.children == null) {
            return null;
        }
        for (UiNode child : root.children) {
            UiNode found = find(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static UiNode findParent(UiNode root, String id) {
        if (root == null || root.children == null) {
            return null;
        }
        for (UiNode child : root.children) {
            if (id != null && id.equals(child.id)) {
                return root;
            }
            UiNode nested = findParent(child, id);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static UiLaidOut findLaid(UiLaidOut root, String id) {
        if (root == null) {
            return null;
        }
        if (root.source != null && id != null && id.equals(root.source.id)) {
            return root;
        }
        for (UiLaidOut child : root.children) {
            UiLaidOut found = findLaid(child, id);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<UiNode> flatten(UiNode root) {
        List<UiNode> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(UiNode node, List<UiNode> out) {
        if (node == null) {
            return;
        }
        out.add(node);
        if (node.children != null) {
            for (UiNode child : node.children) {
                walk(child, out);
            }
        }
    }
}
