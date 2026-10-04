package net.bullettrain.xenopixelsmod.hair;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * In-memory hair maker document for PR-D7b/c. Export builds Task 9
 * {@code xenopixels.hair.export.v1} JSON. DMZ codec bytes are not produced in Java (lab Node
 * codec remains source of truth); codes are left empty with a note. In-game Apply path cites
 * {@link HairApplyService} → {@code UpdateCustomHairC2S#handle} (path READY; runtime unverified).
 *
 * <p>Apply is a <b>full replace of the current style slot</b> from this document — it does not
 * merge with the player's existing {@code CustomHair}. No load-from-Character into the document.
 *
 * <p>No {@code connected} schema field — UI label only.
 */
public final class HairMakerDocument {
    public static final String EXPORT_SCHEMA = "xenopixels.hair.export.v1";
    /** Export label: write path cited; in-game success not verified. */
    public static final String APPLY_STATUS = "path_ready_runtime_unverified";
    public static final String APPLY_NOTE =
            "PR-D7c path READY / runtime unverified: send uses UpdateCustomHairC2S#handle "
                    + "(not CustomizationManager hair_style_*). Full replace of the selected "
                    + "style slot from this document (no load/merge from Character). Export "
                    + "codes still empty — lab encode for DMZ1/DMZF1.";
    /**
     * Tooltip when Apply is enabled. Crystal-clear replace-current-style wipe risk;
     * path READY does not mean runtime-proven.
     */
    public static final String APPLY_ENABLED_TOOLTIP =
            "Replace-current-style: overwrites the selected style slot (Base/SSJ/SSJ2/SSJ3) "
                    + "with this document's geometry — does not merge existing player hair. "
                    + "Path: UpdateCustomHairC2S#handle (path READY / runtime unverified).";
    /** Retained for older copy / tests that mention the D7c gate. */
    public static final String APPLY_DISABLED_TOOLTIP =
            "Apply disabled: PR-D7c evidence not READY (missing verified DMZ/Xeno hair write path).";
    /** Parenting is geometry-only — never a toggle or boolean field. */
    public static final String CONNECTED_LABEL = "Connected (parented cubes)";

    public static final List<String> FACE_NAMES =
            List.of("FRONT", "BACK", "LEFT", "RIGHT", "TOP");
    public static final List<String> STYLE_NAMES =
            List.of("Base", "SSJ", "SSJ2", "SSJ3");

    private static final Map<String, int[]> FACE_CONFIG = Map.of(
            "FRONT", new int[]{4, -90, 0, 0},
            "BACK", new int[]{16, 90, 0, 0},
            "LEFT", new int[]{16, 0, 0, 90},
            "RIGHT", new int[]{16, 0, 0, -90},
            "TOP", new int[]{16, 0, 0, 0}
    );

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String style = "Base";
    private String face = "TOP";
    private int strandIndex;
    private String name = "Custom Base";
    private String globalColor = "#171717";
    private final Map<String, List<HairStrandModel>> faces = new LinkedHashMap<>();

    public HairMakerDocument() {
        resetDefaultStrands();
    }

    /** Empty Base hair with one TOP[0] demo strand matching the one-strand golden field set. */
    public static HairMakerDocument oneStrandDemo() {
        HairMakerDocument doc = new HairMakerDocument();
        doc.name = "One Strand";
        HairStrandModel strand = doc.strandAt("TOP", 0);
        strand.length(4);
        strand.lengthScale(1.25f);
        strand.rotationX(10f);
        strand.rotationY(-5f);
        strand.rotationZ(15f);
        strand.scaleX(1.1f);
        strand.scaleY(1f);
        strand.scaleZ(0.9f);
        strand.cubeWidth(2.5f);
        strand.cubeHeight(2f);
        strand.cubeDepth(1.5f);
        strand.curveX(2f);
        strand.curveY(-1f);
        strand.curveZ(3f);
        strand.color("#ffaa00");
        doc.face = "TOP";
        doc.strandIndex = 0;
        return doc;
    }

    public String style() {
        return style;
    }

    public void style(String style) {
        if (style != null && STYLE_NAMES.contains(style)) {
            this.style = style;
        }
    }

    public String face() {
        return face;
    }

    public void face(String face) {
        if (face == null) {
            return;
        }
        String next = face.trim().toUpperCase(Locale.ROOT);
        if (!FACE_NAMES.contains(next)) {
            return;
        }
        this.face = next;
        int max = faceStrands(this.face).size();
        if (strandIndex >= max) {
            strandIndex = Math.max(0, max - 1);
        }
    }

    public int strandIndex() {
        return strandIndex;
    }

    public void strandIndex(int index) {
        List<HairStrandModel> list = faceStrands(face);
        if (list.isEmpty()) {
            strandIndex = 0;
            return;
        }
        strandIndex = Math.floorMod(index, list.size());
    }

    public String name() {
        return name;
    }

    public void name(String name) {
        this.name = name == null || name.isBlank() ? "Custom" : name.trim();
    }

    public String globalColor() {
        return globalColor;
    }

    public void globalColor(String globalColor) {
        this.globalColor = globalColor == null || globalColor.isBlank() ? "#171717" : globalColor.trim();
    }

    public List<HairStrandModel> faceStrands(String faceName) {
        return faces.getOrDefault(faceName, List.of());
    }

    public HairStrandModel selected() {
        List<HairStrandModel> list = faceStrands(face);
        if (list.isEmpty()) {
            return null;
        }
        return list.get(strandIndex);
    }

    public HairStrandModel strandAt(String faceName, int index) {
        List<HairStrandModel> list = faceStrands(faceName);
        return list.get(index);
    }

    public int faceCapacity(String faceName) {
        int[] cfg = FACE_CONFIG.get(faceName == null ? "" : faceName.trim().toUpperCase(Locale.ROOT));
        return cfg == null ? 0 : cfg[0];
    }

    /**
     * Enable the next empty slot on the current face (length was 0). Returns the new index,
     * or {@code -1} when the face is full (DMZ max: FRONT 4 / others 16).
     */
    public int createSegment() {
        List<HairStrandModel> list = faceStrands(face);
        for (int i = 0; i < list.size(); i++) {
            HairStrandModel slot = list.get(i);
            if (!slot.visible()) {
                slot.length(4);
                slot.lengthScale(1f);
                slot.cubeWidth(2f);
                slot.cubeHeight(2f);
                slot.cubeDepth(2f);
                slot.color(null);
                strandIndex = i;
                return i;
            }
        }
        return -1;
    }

    /**
     * DMZ-compatible weld: activate an empty slot and copy parent transforms with a tip offset
     * (geometry parenting — no invented parent NBT). Returns child index or {@code -1}.
     */
    public int weldSegment() {
        HairStrandModel parent = selected();
        if (parent == null || !parent.visible()) {
            return -1;
        }
        int parentIndex = strandIndex;
        int childIndex = createSegment();
        if (childIndex < 0) {
            return -1;
        }
        HairStrandModel child = selected();
        child.length(Math.max(1, parent.length() / 2));
        child.lengthScale(parent.lengthScale());
        child.scaleX(parent.scaleX());
        child.scaleY(parent.scaleY());
        child.scaleZ(parent.scaleZ());
        child.cubeWidth(parent.cubeWidth());
        child.cubeHeight(parent.cubeHeight());
        child.cubeDepth(parent.cubeDepth());
        child.color(parent.color());
        // Offset from parent tip — lab hair-geometry joint chain approximation.
        child.rotationX(parent.rotationX() + 12f);
        child.rotationY(parent.rotationY() + 8f);
        child.rotationZ(parent.rotationZ());
        child.curveX(parent.curveX() + 1.5f);
        child.curveY(parent.curveY());
        child.curveZ(parent.curveZ() + parent.lengthScale());
        strandIndex = childIndex;
        // Keep parent selected context in status via return; selection is the child.
        if (parentIndex == childIndex) {
            return -1;
        }
        return childIndex;
    }

    /** Duplicate selected visible strand into the next empty slot. */
    public int duplicateSegment() {
        HairStrandModel src = selected();
        if (src == null || !src.visible()) {
            return -1;
        }
        HairStrandModel copy = src.copy();
        int dest = createSegment();
        if (dest < 0) {
            return -1;
        }
        HairStrandModel slot = selected();
        slot.length(copy.length());
        slot.lengthScale(copy.lengthScale());
        slot.rotationX(copy.rotationX());
        slot.rotationY(copy.rotationY());
        slot.rotationZ(copy.rotationZ());
        slot.scaleX(copy.scaleX());
        slot.scaleY(copy.scaleY());
        slot.scaleZ(copy.scaleZ());
        slot.cubeWidth(copy.cubeWidth());
        slot.cubeHeight(copy.cubeHeight());
        slot.cubeDepth(copy.cubeDepth());
        slot.curveX(copy.curveX());
        slot.curveY(copy.curveY());
        slot.curveZ(copy.curveZ());
        slot.color(copy.color());
        return dest;
    }

    /** Clear selected strand (length 0); slot remains for Create. */
    public void deleteSegment() {
        HairStrandModel s = selected();
        if (s != null) {
            s.length(0);
        }
    }

    public int visibleCount() {
        int total = 0;
        for (List<HairStrandModel> list : faces.values()) {
            for (HairStrandModel strand : list) {
                if (strand.visible()) {
                    total++;
                }
            }
        }
        return total;
    }

    /**
     * Debug / test summary lines for the document. The Hair Editor screen preview uses
     * {@code MakerPreviewController} (true local player), not these lines.
     */
    public List<String> previewLines() {
        List<String> lines = new ArrayList<>();
        lines.add(style + " / " + face + "[" + strandIndex + "]");
        lines.add("name: " + name + "  gc: " + globalColor);
        lines.add("visible strands: " + visibleCount());
        lines.add(CONNECTED_LABEL);
        HairStrandModel s = selected();
        if (s == null) {
            lines.add("no strand selected");
            lines.add("Preview: MakerPreviewController (true player)");
            return List.copyOf(lines);
        }
        lines.add("id " + s.id() + "  len " + s.length() + "  ls " + format(s.lengthScale()));
        lines.add("rot " + format(s.rotationX()) + "," + format(s.rotationY()) + "," + format(s.rotationZ()));
        lines.add("scale " + format(s.scaleX()) + "," + format(s.scaleY()) + "," + format(s.scaleZ()));
        lines.add("curve " + format(s.curveX()) + "," + format(s.curveY()) + "," + format(s.curveZ()));
        lines.add("Preview: MakerPreviewController (true player)");
        return List.copyOf(lines);
    }

    /**
     * Task 9 export envelope. Codes stay empty until lab Node encode; {@code apply} is
     * {@link #APPLY_STATUS} ({@code path_ready_runtime_unverified}) after PR-D7c
     * (in-game write uses {@link HairApplyService}; runtime unverified).
     */
    public String toExportJson(String capturedAt) {
        JsonObject root = new JsonObject();
        root.addProperty("schema", EXPORT_SCHEMA);
        root.addProperty("capturedAt", capturedAt == null || capturedAt.isBlank()
                ? "1970-01-01" : capturedAt);
        root.addProperty("apply", APPLY_STATUS);
        root.addProperty("applyNote", APPLY_NOTE);
        root.addProperty("applyPath", HairApplyService.WRITE_PATH);
        root.add("project", toProjectJson());
        JsonObject codes = new JsonObject();
        codes.addProperty("single", "");
        codes.addProperty("singleStyle", style);
        codes.addProperty("full", "");
        root.add("codes", codes);
        root.addProperty("note",
                "DMZ1/DMZF1 codes empty in-game; encode via tools/dmz-hair-builder-site "
                        + "npm run export:hair after saving this envelope project.");
        JsonArray strandFields = new JsonArray();
        for (String field : List.of(
                "id", "visible", "length", "lengthScale",
                "rotationX", "rotationY", "rotationZ",
                "scaleX", "scaleY", "scaleZ",
                "cubeWidth", "cubeHeight", "cubeDepth",
                "curveX", "curveY", "curveZ", "color")) {
            strandFields.add(field);
        }
        root.add("strandFields", strandFields);
        JsonArray forbidden = new JsonArray();
        forbidden.add("connected");
        forbidden.add("movable");
        root.add("forbiddenFields", forbidden);
        return GSON.toJson(root) + "\n";
    }

    public JsonObject toProjectJson() {
        JsonObject project = new JsonObject();
        project.addProperty("version", 2);
        project.addProperty("screen", "editor");
        project.addProperty("selectedPresetId", -1);
        project.addProperty("presetColor", globalColor);
        project.addProperty("style", style);
        project.addProperty("face", face);
        project.addProperty("strandIndex", strandIndex);
        project.addProperty("selectedCubeIndex", 0);
        project.addProperty("mirror", false);
        project.addProperty("physics", false);
        project.addProperty("showBase", true);

        JsonObject hairSet = new JsonObject();
        for (String styleName : STYLE_NAMES) {
            // Editor holds one editable geometry; other styles export as empty defaults.
            if (styleName.equals(style)) {
                hairSet.add(styleName, toCustomHairJson(name, globalColor, true));
            } else {
                hairSet.add(styleName, toCustomHairJson(
                        name + " " + styleName, defaultStyleColor(styleName), false));
            }
        }
        project.add("hairSet", hairSet);
        return project;
    }

    /** Apply enabled — PR-D7c evidence READY ({@link HairApplyService}). */
    public boolean isApplyEnabled() {
        return true;
    }

    /** Catalog payload: current-style CustomHair JSON (no export envelope). */
    public JsonObject toCatalogJson() {
        return toCustomHairJson(name, globalColor, true);
    }

    public static HairMakerDocument fromCatalogJson(JsonObject hair) {
        HairMakerDocument doc = new HairMakerDocument();
        if (hair == null) {
            return doc;
        }
        if (hair.has("name") && !hair.get("name").isJsonNull()) {
            doc.name(hair.get("name").getAsString());
        }
        if (hair.has("globalColor") && !hair.get("globalColor").isJsonNull()) {
            doc.globalColor(hair.get("globalColor").getAsString());
        }
        if (!hair.has("faces") || !hair.get("faces").isJsonObject()) {
            return doc;
        }
        JsonObject faceMap = hair.getAsJsonObject("faces");
        doc.faces.clear();
        for (int faceIdx = 0; faceIdx < FACE_NAMES.size(); faceIdx++) {
            String faceName = FACE_NAMES.get(faceIdx);
            List<HairStrandModel> list = new ArrayList<>();
            if (faceMap.has(faceName) && faceMap.get(faceName).isJsonArray()) {
                JsonArray arr = faceMap.getAsJsonArray(faceName);
                for (int i = 0; i < arr.size(); i++) {
                    if (arr.get(i).isJsonObject()) {
                        list.add(fromStrandJson(arr.get(i).getAsJsonObject(), faceIdx * 100 + i));
                    }
                }
            }
            if (list.isEmpty()) {
                int[] cfg = FACE_CONFIG.get(faceName);
                for (int i = 0; i < cfg[0]; i++) {
                    HairStrandModel strand = new HairStrandModel(faceIdx * 100 + i);
                    strand.rotationX(cfg[1]);
                    strand.rotationY(cfg[2]);
                    strand.rotationZ(cfg[3]);
                    list.add(strand);
                }
            }
            doc.faces.put(faceName, list);
        }
        doc.face = "TOP";
        int firstVisible = 0;
        List<HairStrandModel> top = doc.faceStrands("TOP");
        for (int i = 0; i < top.size(); i++) {
            if (top.get(i).visible()) {
                firstVisible = i;
                break;
            }
        }
        doc.strandIndex = firstVisible;
        return doc;
    }

    public static HairMakerDocument fromProjectJson(JsonObject project) {
        if (project == null) {
            return new HairMakerDocument();
        }
        String style = project.has("style") ? project.get("style").getAsString() : "Base";
        JsonObject hairSet = project.has("hairSet") && project.get("hairSet").isJsonObject()
                ? project.getAsJsonObject("hairSet") : null;
        JsonObject hair = null;
        if (hairSet != null && hairSet.has(style) && hairSet.get(style).isJsonObject()) {
            hair = hairSet.getAsJsonObject(style);
        } else if (hairSet != null && hairSet.has("Base") && hairSet.get("Base").isJsonObject()) {
            hair = hairSet.getAsJsonObject("Base");
        }
        HairMakerDocument doc = fromCatalogJson(hair);
        doc.style(style);
        if (project.has("presetColor") && !project.get("presetColor").isJsonNull()) {
            doc.globalColor(project.get("presetColor").getAsString());
        }
        if (project.has("face") && !project.get("face").isJsonNull()) {
            doc.face(project.get("face").getAsString());
        }
        return doc;
    }

    private static HairStrandModel fromStrandJson(JsonObject o, int fallbackId) {
        int id = o.has("id") ? o.get("id").getAsInt() : fallbackId;
        HairStrandModel strand = new HairStrandModel(id);
        if (o.has("length")) {
            strand.length(o.get("length").getAsInt());
        }
        if (o.has("lengthScale")) {
            strand.lengthScale(o.get("lengthScale").getAsFloat());
        }
        if (o.has("rotationX")) {
            strand.rotationX(o.get("rotationX").getAsFloat());
        }
        if (o.has("rotationY")) {
            strand.rotationY(o.get("rotationY").getAsFloat());
        }
        if (o.has("rotationZ")) {
            strand.rotationZ(o.get("rotationZ").getAsFloat());
        }
        if (o.has("scaleX")) {
            strand.scaleX(o.get("scaleX").getAsFloat());
        }
        if (o.has("scaleY")) {
            strand.scaleY(o.get("scaleY").getAsFloat());
        }
        if (o.has("scaleZ")) {
            strand.scaleZ(o.get("scaleZ").getAsFloat());
        }
        if (o.has("cubeWidth")) {
            strand.cubeWidth(o.get("cubeWidth").getAsFloat());
        }
        if (o.has("cubeHeight")) {
            strand.cubeHeight(o.get("cubeHeight").getAsFloat());
        }
        if (o.has("cubeDepth")) {
            strand.cubeDepth(o.get("cubeDepth").getAsFloat());
        }
        if (o.has("curveX")) {
            strand.curveX(o.get("curveX").getAsFloat());
        }
        if (o.has("curveY")) {
            strand.curveY(o.get("curveY").getAsFloat());
        }
        if (o.has("curveZ")) {
            strand.curveZ(o.get("curveZ").getAsFloat());
        }
        if (o.has("color") && !o.get("color").isJsonNull()) {
            strand.color(o.get("color").getAsString());
        }
        return strand;
    }

    private JsonObject toCustomHairJson(String hairName, String color, boolean useCurrentFaces) {
        JsonObject hair = new JsonObject();
        hair.addProperty("version", 5);
        hair.addProperty("name", hairName);
        hair.addProperty("globalColor", color);
        JsonObject faceMap = new JsonObject();
        for (int faceIdx = 0; faceIdx < FACE_NAMES.size(); faceIdx++) {
            String faceName = FACE_NAMES.get(faceIdx);
            JsonArray arr = new JsonArray();
            if (useCurrentFaces) {
                for (HairStrandModel strand : faceStrands(faceName)) {
                    arr.add(toStrandJson(strand));
                }
            } else {
                int[] cfg = FACE_CONFIG.get(faceName);
                for (int i = 0; i < cfg[0]; i++) {
                    HairStrandModel strand = new HairStrandModel(faceIdx * 100 + i);
                    strand.rotationX(cfg[1]);
                    strand.rotationY(cfg[2]);
                    strand.rotationZ(cfg[3]);
                    arr.add(toStrandJson(strand));
                }
            }
            faceMap.add(faceName, arr);
        }
        hair.add("faces", faceMap);
        return hair;
    }

    private static JsonObject toStrandJson(HairStrandModel strand) {
        JsonObject o = new JsonObject();
        o.addProperty("id", strand.id());
        o.addProperty("visible", strand.visible());
        o.addProperty("length", strand.length());
        o.addProperty("lengthScale", strand.lengthScale());
        o.addProperty("rotationX", strand.rotationX());
        o.addProperty("rotationY", strand.rotationY());
        o.addProperty("rotationZ", strand.rotationZ());
        o.addProperty("scaleX", strand.scaleX());
        o.addProperty("scaleY", strand.scaleY());
        o.addProperty("scaleZ", strand.scaleZ());
        o.addProperty("cubeWidth", strand.cubeWidth());
        o.addProperty("cubeHeight", strand.cubeHeight());
        o.addProperty("cubeDepth", strand.cubeDepth());
        o.addProperty("curveX", strand.curveX());
        o.addProperty("curveY", strand.curveY());
        o.addProperty("curveZ", strand.curveZ());
        if (strand.color() == null) {
            o.add("color", JsonNull.INSTANCE);
        } else {
            o.addProperty("color", strand.color());
        }
        return o;
    }

    private void resetDefaultStrands() {
        faces.clear();
        for (int faceIdx = 0; faceIdx < FACE_NAMES.size(); faceIdx++) {
            String faceName = FACE_NAMES.get(faceIdx);
            int[] cfg = FACE_CONFIG.get(faceName);
            int count = cfg[0];
            List<HairStrandModel> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                HairStrandModel strand = new HairStrandModel(faceIdx * 100 + i);
                strand.rotationX(cfg[1]);
                strand.rotationY(cfg[2]);
                strand.rotationZ(cfg[3]);
                list.add(strand);
            }
            faces.put(faceName, list);
        }
        strandIndex = 0;
    }

    private static String defaultStyleColor(String styleName) {
        return switch (styleName) {
            case "SSJ" -> "#f5c842";
            case "SSJ2" -> "#ffe777";
            case "SSJ3" -> "#f2c84b";
            default -> "#171717";
        };
    }

    /**
     * Replace all face geometry from a DMZ {@link com.dragonminez.common.hair.CustomHair}
     * (preset or player hair). Sets style + global colour from the hair when present.
     */
    public void loadFromCustomHair(com.dragonminez.common.hair.CustomHair hair, String styleName) {
        if (hair == null) {
            return;
        }
        if (styleName != null && STYLE_NAMES.contains(styleName)) {
            this.style = styleName;
        }
        String gc = hair.getGlobalColor();
        if (gc != null && !gc.isBlank()) {
            this.globalColor = gc.trim();
        }
        String hairName = hair.getName();
        if (hairName != null && !hairName.isBlank()) {
            this.name = hairName.trim();
        }
        for (String faceName : FACE_NAMES) {
            com.dragonminez.common.hair.CustomHair.HairFace face;
            try {
                face = com.dragonminez.common.hair.CustomHair.HairFace.valueOf(faceName);
            } catch (IllegalArgumentException e) {
                continue;
            }
            HairPresetImport.copyFace(hair, face, faceStrands(faceName));
        }
        // Prefer first visible strand on current face.
        List<HairStrandModel> list = faceStrands(face);
        int firstVisible = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).visible()) {
                firstVisible = i;
                break;
            }
        }
        strandIndex = firstVisible;
    }

    /**
     * Deep copy for undo/history. Original Xeno snapshot — not a DMZ codec round-trip.
     */
    public HairMakerDocument snapshot() {
        HairMakerDocument copy = new HairMakerDocument();
        copy.name = this.name;
        copy.style = this.style;
        copy.face = this.face;
        copy.strandIndex = this.strandIndex;
        copy.globalColor = this.globalColor;
        copy.faces.clear();
        for (String faceName : FACE_NAMES) {
            List<HairStrandModel> src = faceStrands(faceName);
            List<HairStrandModel> dst = new ArrayList<>(src.size());
            for (HairStrandModel strand : src) {
                dst.add(strand.copy());
            }
            copy.faces.put(faceName, dst);
        }
        return copy;
    }

    /** Restore geometry/selection from {@link #snapshot()}. */
    public void restoreSnapshot(HairMakerDocument snap) {
        if (snap == null) {
            return;
        }
        this.name = snap.name;
        this.style = snap.style;
        this.face = snap.face;
        this.strandIndex = snap.strandIndex;
        this.globalColor = snap.globalColor;
        this.faces.clear();
        for (String faceName : FACE_NAMES) {
            List<HairStrandModel> src = snap.faceStrands(faceName);
            List<HairStrandModel> dst = new ArrayList<>(src.size());
            for (HairStrandModel strand : src) {
                dst.add(strand.copy());
            }
            this.faces.put(faceName, dst);
        }
    }

    private static String format(float value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
