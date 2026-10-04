package net.bullettrain.xenopixelsmod.ui;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Minecraft-free write path for the DMZ GUI Studio. Screens mutate documents only through here.
 */
public final class UiStudioSession {
    private static final int[] SNAPS = {1, 2, 4, 8, 16};

    private UiDocument document;
    private String selectedId = "root";
    private final LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
    private String notice = "";
    private int snapIndex = 3;
    private final ArrayDeque<String> undo = new ArrayDeque<>();
    private final ArrayDeque<String> redo = new ArrayDeque<>();

    public UiStudioSession(UiDocument document) {
        this.document = document != null ? document : new UiDocument();
        if (this.document.root == null) {
            this.document.root = new UiNode();
            this.document.root.id = "root";
            this.document.root.type = "ROOT";
        }
        this.selectedId = this.document.root.id;
        this.selectedIds.add(this.selectedId);
    }

    public UiDocument document() {
        return document;
    }

    public String selectedId() {
        return selectedId;
    }

    public Set<String> selectedIds() {
        return Set.copyOf(selectedIds);
    }

    public String notice() {
        return notice;
    }

    public int snap() {
        return SNAPS[snapIndex];
    }

    public void cycleSnap() {
        snapIndex = (snapIndex + 1) % SNAPS.length;
    }

    public void select(String id) {
        select(id, false);
    }

    public void select(String id, boolean additive) {
        if (find(document.root, id) != null) {
            if (!additive) selectedIds.clear();
            if (additive && selectedIds.remove(id)) {
                if (selectedIds.isEmpty()) selectedIds.add(document.root.id);
                selectedId = selectedIds.stream().reduce((first, second) -> second).orElse(document.root.id);
                return;
            }
            selectedIds.add(id);
            selectedId = id;
        }
    }

    public void selectAll(Collection<String> ids) {
        selectedIds.clear();
        if (ids != null) for (String id : ids) if (find(document.root, id) != null) selectedIds.add(id);
        if (selectedIds.isEmpty()) selectedIds.add(document.root.id);
        selectedId = selectedIds.stream().reduce((first, second) -> second).orElse(document.root.id);
    }

    public UiNode selected() {
        return find(document.root, selectedId);
    }

    public List<UiNode> flatten() {
        List<UiNode> out = new ArrayList<>();
        walk(document.root, out);
        return out;
    }

    public void loadDocument(UiDocument loaded) {
        if (loaded == null || loaded.root == null) {
            notice = "template missing";
            return;
        }
        pushUndo();
        document = loaded;
        resetSelection();
        notice = "Loaded " + (document.id == null ? "document" : document.id);
    }

    public static final String MENU_NPC = "dragonminez:textures/gui/menu/menunpc.png";

    public void newMenu(Collection<String> existingIds) {
        pushUndo();
        document = blankMenu(uniqueDocId("dmz_menu", existingIds));
        resetSelection();
        notice = "New DMZ menu " + document.id;
    }

    public void newHud(Collection<String> existingIds) {
        pushUndo();
        document = blankHud(uniqueDocId("dmz_hud", existingIds));
        resetSelection();
        notice = "New DMZ overlay " + document.id;
    }

    public boolean saveAs(String rawId) {
        String id = sanitizeId(rawId);
        if (id.isEmpty()) {
            notice = "id must be letters, numbers, or underscore";
            return false;
        }
        pushUndo();
        document.id = id;
        List<String> errors = validate();
        notice = errors.isEmpty() ? "Save as " + id : String.join("; ", errors);
        return errors.isEmpty();
    }

    public static UiDocument blankMenu(String id) {
        UiDocument doc = new UiDocument();
        doc.id = id == null || id.isBlank() ? "dmz_menu_1" : id;
        doc.kind = "dmz_menu";
        doc.page = "custom";
        doc.canvasW = 365;
        doc.canvasH = 293;
        doc.root = node("root", "ROOT", 0, 0, 365, 293, "TOP_LEFT");
        doc.root.color = "#00000000";
        UiNode plate = node("plate", "IMAGE", 10, 10, 345, 273, "TOP_LEFT");
        plate.texture = MENU_NPC;
        UiNode title = node("title", "TEXT", 24, 22, 160, 12, "TOP_LEFT");
        title.text = "NEW MENU";
        title.color = "#FFD54A";
        UiNode tab = node("tab_stats", "NAV_TAB", 24, 40, 48, 14, "TOP_LEFT");
        tab.text = "Stats";
        tab.action = "dmz_page:stats";
        UiNode row = node("row", "STAT_ROW", 24, 62, 200, 14, "TOP_LEFT");
        row.text = "Name";
        row.bind = "player.name";
        doc.root.children = new ArrayList<>(List.of(plate, title, tab, row));
        return doc;
    }

    public static UiDocument blankHud(String id) {
        UiDocument doc = new UiDocument();
        doc.id = id == null || id.isBlank() ? "dmz_hud_1" : id;
        doc.kind = "hud";
        doc.page = "";
        doc.canvasW = 1920;
        doc.canvasH = 1080;
        doc.root = node("root", "ROOT", 0, 0, 1920, 1080, "TOP_LEFT");
        doc.root.color = "#00000000";
        UiNode plate = node("plate", "IMAGE", 8, 8, 345, 90, "TOP_LEFT");
        plate.texture = MENU_NPC;
        UiNode face = node("face", "PORTRAIT", 16, 16, 36, 36, "TOP_LEFT");
        UiNode hp = node("hp", "PROGRESS_BAR", 60, 20, 160, 10, "TOP_LEFT");
        hp.bind = "player.hpPercent";
        hp.text = "HP";
        UiNode ki = node("ki", "PROGRESS_BAR", 60, 36, 160, 10, "TOP_LEFT");
        ki.bind = "player.kiPercent";
        ki.text = "KI";
        doc.root.children = new ArrayList<>(List.of(plate, face, hp, ki));
        return doc;
    }

    static String uniqueDocId(String prefix, Collection<String> existing) {
        Set<String> taken = existing == null ? Set.of() : Set.copyOf(existing);
        int i = 1;
        String id;
        do {
            id = prefix + "_" + i;
            i++;
        } while (taken.contains(id));
        return id;
    }

    static String sanitizeId(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "");
    }

    private static UiNode node(String id, String type, int x, int y, int w, int h, String anchor) {
        UiNode n = new UiNode();
        n.id = id;
        n.type = type;
        n.x = x;
        n.y = y;
        n.w = w;
        n.h = h;
        n.anchor = anchor;
        n.visible = true;
        return n;
    }

    public void cyclePage(int delta) {
        DmzMenuPage[] pages = DmzMenuPage.values();
        DmzMenuPage current = DmzMenuPage.parse(document.page);
        int index = current == null ? 0 : current.ordinal();
        int next = Math.floorMod(index + delta, pages.length);
        document.kind = "dmz_menu";
        document.page = pages[next].id();
        notice = "Page " + document.page;
    }

    public void addNode(String type) {
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
        if ("TEXT".equals(type)) {
            child.text = "Text";
        }
        if ("PROGRESS_BAR".equals(type)) {
            child.bind = "player.hpPercent";
        }
        if ("BUTTON".equals(type)) {
            child.text = "Open";
            child.action = "open_document:demo_screen";
        }
        if ("NAV_TAB".equals(type)) {
            child.text = "Stats";
            child.action = "dmz_page:stats";
        }
        if ("STAT_ROW".equals(type)) {
            child.text = "Stat";
            child.bind = "player.level";
        }
        if (parent.children == null) {
            parent.children = new ArrayList<>();
        }
        parent.children.add(child);
        selectedId = child.id;
        selectedIds.clear();
        selectedIds.add(selectedId);
        notice = "Added " + child.id;
    }

    public void deleteSelected() {
        if (document.root != null && selectedIds.size() == 1 && selectedIds.contains(document.root.id)) {
            notice = "Cannot delete root";
            return;
        }
        pushUndo();
        for (String id : List.copyOf(selectedIds)) {
            if (document.root.id.equals(id)) continue;
            UiNode parent = findParent(document.root, id);
            if (parent != null && parent.children != null) parent.children.removeIf(child -> id.equals(child.id));
        }
        resetSelection();
        notice = "Deleted";
    }

    public void applyInspector(InspectorPatch patch) {
        UiNode selected = find(document.root, selectedId);
        if (selected == null || patch == null) {
            return;
        }
        pushUndo();
        try {
            selected.x = snapTo(Integer.parseInt(patch.x.trim()));
            selected.y = snapTo(Integer.parseInt(patch.y.trim()));
            selected.w = Math.max(4, Integer.parseInt(patch.w.trim()));
            selected.h = Math.max(4, Integer.parseInt(patch.h.trim()));
            selected.minW = Math.max(0, Integer.parseInt(patch.minW.trim()));
            selected.minH = Math.max(0, Integer.parseInt(patch.minH.trim()));
            selected.maxW = Math.max(0, Integer.parseInt(patch.maxW.trim()));
            selected.maxH = Math.max(0, Integer.parseInt(patch.maxH.trim()));
        } catch (NumberFormatException ex) {
            notice = "numeric fields must be integers";
            return;
        }
        selected.anchor = patch.anchor.trim();
        selected.visible = !"false".equalsIgnoreCase(patch.visible.trim());
        selected.color = patch.color.trim();
        selected.texture = patch.texture.trim();
        selected.text = patch.text;
        selected.bind = patch.bind.trim();
        selected.action = patch.action.trim();
        List<String> errors = UiDocumentValidator.validate(document);
        notice = errors.isEmpty() ? "Inspector applied" : String.join("; ", errors);
    }

    public List<String> validate() {
        return UiDocumentValidator.validate(document);
    }

    public boolean canSave() {
        return validate().isEmpty();
    }

    public void undo() {
        if (undo.isEmpty()) {
            notice = "Nothing to undo";
            return;
        }
        redo.addLast(UiDocumentIO.toJson(document));
        restore(undo.removeLast());
        notice = "Undo";
    }

    public void redo() {
        if (redo.isEmpty()) {
            notice = "Nothing to redo";
            return;
        }
        undo.addLast(UiDocumentIO.toJson(document));
        restore(redo.removeLast());
        notice = "Redo";
    }

    public void moveSelected(int x, int y) {
        UiNode node = find(document.root, selectedId);
        if (node == null) {
            return;
        }
        node.x = snapTo(x);
        node.y = snapTo(y);
    }

    public void moveSelectionBy(int dx, int dy) {
        for (String id : selectedIds) {
            UiNode node = find(document.root, id);
            if (node == null || node == document.root || node.editorLocked || node.editorHidden) continue;
            node.x = snapTo(node.x + dx);
            node.y = snapTo(node.y + dy);
        }
    }

    public void setEditorLocked(String id, boolean locked) {
        UiNode node = find(document.root, id);
        if (node == null || node.editorLocked == locked) return;
        pushUndo();
        node.editorLocked = locked;
    }

    public void setEditorHidden(String id, boolean hidden) {
        UiNode node = find(document.root, id);
        if (node == null || node.editorHidden == hidden) return;
        pushUndo();
        node.editorHidden = hidden;
    }

    public boolean isCanvasSelectable(String id) {
        UiNode node = find(document.root, id);
        return node != null && !node.editorHidden && !node.editorLocked;
    }

    public void resizeSelected(int w, int h) {
        UiNode node = find(document.root, selectedId);
        if (node == null) {
            return;
        }
        node.w = Math.max(4, snapTo(w));
        node.h = Math.max(4, snapTo(h));
    }

    public void transformSelected(int x, int y, int w, int h) {
        UiNode node = find(document.root, selectedId);
        if (node == null || node.editorLocked || node.editorHidden) return;
        node.x = snapTo(x);
        node.y = snapTo(y);
        node.w = Math.max(4, snapTo(w));
        node.h = Math.max(4, snapTo(h));
    }

    public void pushUndo() {
        undo.addLast(UiDocumentIO.toJson(document));
        redo.clear();
        while (undo.size() > 32) {
            undo.removeFirst();
        }
    }

    public String toJson() {
        return UiDocumentIO.toJson(document);
    }

    private void restore(String json) {
        UiDocumentIO.UiLoadResult result = UiDocumentIO.fromJson(json);
        if (result.ok()) {
            document = result.document();
            resetSelection();
        }
    }

    private void resetSelection() {
        selectedIds.clear();
        selectedId = document.root.id;
        selectedIds.add(selectedId);
    }

    private String uniqueId(String type) {
        String base = type.toLowerCase().replace('_', '-');
        int i = 1;
        while (find(document.root, base + i) != null) {
            i++;
        }
        return base + i;
    }

    private int snapTo(int value) {
        int step = snap();
        return Math.round(value / (float) step) * step;
    }

    public static UiNode find(UiNode root, String id) {
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

    public record InspectorPatch(
            String x, String y, String w, String h,
            String minW, String minH, String maxW, String maxH,
            String anchor, String visible, String color, String texture,
            String text, String bind, String action) {
    }
}
