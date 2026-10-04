package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.hair.HairApplyService;
import net.bullettrain.xenopixelsmod.hair.HairMakerDocument;
import net.bullettrain.xenopixelsmod.hair.HairPresetImport;
import net.bullettrain.xenopixelsmod.hair.HairStrandModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Xeno Hair Studio — from-scratch UI matching DMZ 2.2 editor design ideas
 * (outliner + viewport tools + inspector) while staying on pinned DMZ 2.1.3 cube hair.
 *
 * <p>Layout: Outliner | Viewport (GROW/ROTATE/CURVE) | Inspector. No DMZ 2.2 code or assets.
 * Apply uses {@link HairApplyService} on 2.1.3 {@code UpdateCustomHairC2S}. Pixel paint is PR B.
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class HairMakerScreen extends ScaledScreen {
    private static final String BANNER = "banner_top";
    private static final String STYLE_LIST = "xeno_maker_form_list";
    private static final String SETTINGS = "xeno_maker_form_settings";
    private static final String PREVIEW = "xeno_maker_hair_preview";
    /** Compact 64px face so tool + footer rows stay inside the studio width. */
    private static final String TOOL = "mynpcs_button_row";

    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int WARN = 0xFFFF8A80;
    private static final int OK = 0xFF9AFFB0;
    private static final int GLOW = 0x9900C853;

    /** Display labels for {@link HairMakerDocument#STYLE_NAMES} (Images 5–6 vocabulary). */
    public static final List<String> STYLE_DISPLAY_NAMES = List.of(
            "Default Hair Style",
            "Super Saiyan",
            "Super Saiyan 2",
            "Super Saiyan 3"
    );

    private final Screen parent;
    private final HairMakerDocument document;
    private final MakerPreviewController preview = new MakerPreviewController();
    private final InlineColorPicker colorPicker = new InlineColorPicker();
    private final HairOutlinerPanel outliner = new HairOutlinerPanel();
    private final HairEditHistory history = new HairEditHistory();

    private HairViewportTool viewportTool = HairViewportTool.ROTATE;
    private boolean toolDragActive;
    private boolean appearanceDirty = true;

    private String status = "Hair Studio (2.1.3) — Outliner | Grow/Rotate/Curve | Inspector. "
            + "Ctrl+Z undo. Pixel paint = next PR.";
    private int statusColor = MUTED;
    private int styleScroll;
    private int strandScroll;
    private String selectedPresetId = "";
    /** Parsed DMZ {@link com.dragonminez.common.hair.HairManager} preset id, or -1. */
    private int selectedPresetNumeric = -1;
    private List<String> presetIds = List.of();
    private List<String> presetLabels = List.of();
    private boolean syncingFields;

    private int originX;
    private int originY;
    private int bannerX;
    private int bannerY;
    private int bannerW;
    private int bannerH;
    private int listX;
    private int listY;
    private int listW;
    private int listH;
    private int settingsX;
    private int settingsY;
    private int settingsW;
    private int settingsH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    private EditBox lengthBox;
    private EditBox lengthScaleBox;
    private EditBox rotXBox;
    private EditBox rotYBox;
    private EditBox rotZBox;
    private EditBox scaleXBox;
    private EditBox scaleYBox;
    private EditBox scaleZBox;
    private EditBox cubeWBox;
    private EditBox cubeHBox;
    private EditBox cubeDBox;
    private EditBox curveXBox;
    private EditBox curveYBox;
    private EditBox curveZBox;
    private EditBox hairColorBox;
    private EditBox extraColorBox;

    public HairMakerScreen(Screen parent) {
        this(parent, HairMakerDocument.oneStrandDemo());
    }

    public HairMakerScreen(Screen parent, HairMakerDocument document) {
        super(Component.literal("Hair Editor"));
        this.parent = parent;
        this.document = document == null ? new HairMakerDocument() : document;
    }

    @Override
    protected int getMinGuiWidth() {
        return 720;
    }

    @Override
    protected int getMinGuiHeight() {
        return 400;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);

        // Studio chrome: Outliner | Viewport | Inspector (2.2 design idea; Xeno layout math).
        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        listW = XenoAtlasSprites.get(STYLE_LIST).width();
        listH = XenoAtlasSprites.get(STYLE_LIST).height();
        previewW = XenoAtlasSprites.get(PREVIEW).width();
        previewH = XenoAtlasSprites.get(PREVIEW).height();
        settingsW = XenoAtlasSprites.get(SETTINGS).width();
        settingsH = XenoAtlasSprites.get(SETTINGS).height();

        int gap = MakerPreviewLayout.GAP;
        int contentW = listW + gap + previewW + gap + settingsW;
        int columnH = Math.max(listH, Math.max(previewH, settingsH));
        int toolStripH = 52;
        int contentH = bannerH + 8 + toolStripH + columnH + 56;
        originX = Math.max(8, (getUiWidth() - contentW) / 2);
        originY = Math.max(4, (getUiHeight() - contentH) / 2);
        bannerX = originX + (contentW - bannerW) / 2;
        bannerY = originY;
        int cycleY = bannerY + bannerH + 4;
        listX = originX;
        listY = cycleY + toolStripH;
        previewX = listX + listW + gap;
        previewY = listY;
        settingsX = previewX + previewW + gap;
        settingsY = listY;
        int footerY = listY + columnH + MakerPreviewLayout.FOOTER_GAP;

        refreshPresets();
        clearWidgets();

        // Style + Preset stacked over the Outliner so they never sit under Grow.
        List<String> styleLabels = STYLE_DISPLAY_NAMES;
        int styleIndex = Math.max(0, HairMakerDocument.STYLE_NAMES.indexOf(document.style()));
        addRenderableWidget(new AtlasCycle(listX, cycleY,
                Component.literal("Style"), styleLabels, styleIndex, this::onStyleIndexChanged));
        if (!presetLabels.isEmpty()) {
            int presetIndex = Math.max(0, presetIds.indexOf(selectedPresetId));
            if (presetIndex >= presetLabels.size()) {
                presetIndex = 0;
            }
            addRenderableWidget(new AtlasCycle(listX, cycleY + 26,
                    Component.literal("Preset"), presetLabels, presetIndex, this::onPresetCycle));
        }

        // Compact 64px tools in two rows over the preview column only.
        int btnW = AtlasButton.nativeWidth(TOOL);
        int toolY = cycleY;
        addRenderableWidget(new AtlasButton(previewX, toolY,
                Component.literal("Grow"), TOOL, b -> setViewportTool(HairViewportTool.GROW)));
        addRenderableWidget(new AtlasButton(previewX + btnW + 4, toolY,
                Component.literal("Rotate"), TOOL, b -> setViewportTool(HairViewportTool.ROTATE)));
        addRenderableWidget(new AtlasButton(previewX + (btnW + 4) * 2, toolY,
                Component.literal("Curve"), TOOL, b -> setViewportTool(HairViewportTool.CURVE)));
        addRenderableWidget(new AtlasButton(previewX + (btnW + 4) * 3, toolY,
                Component.literal("Spin L"), TOOL, b -> {
                    preview.addYaw(-25f);
                    status = "Character yaw " + Math.round(preview.yaw());
                    statusColor = MUTED;
                }));
        int toolY2 = toolY + 26;
        addRenderableWidget(new AtlasButton(previewX, toolY2,
                Component.literal("Spin R"), TOOL, b -> {
                    preview.addYaw(25f);
                    status = "Character yaw " + Math.round(preview.yaw());
                    statusColor = MUTED;
                }));
        addRenderableWidget(new AtlasButton(previewX + btnW + 4, toolY2,
                Component.literal("Zoom −"), TOOL, b -> {
                    preview.addZoom(-1f);
                    status = "Zoom " + Math.round(preview.zoom() * 100) + "% · pick r="
                            + Math.round(preview.pickHitRadiusPx()) + "px";
                    statusColor = MUTED;
                }));
        addRenderableWidget(new AtlasButton(previewX + (btnW + 4) * 2, toolY2,
                Component.literal("Zoom +"), TOOL, b -> {
                    preview.addZoom(1f);
                    status = "Zoom " + Math.round(preview.zoom() * 100) + "% · pick r="
                            + Math.round(preview.pickHitRadiusPx()) + "px";
                    statusColor = MUTED;
                }));

        addSegmentEditors();
        addColorEditors();

        // One footer row, compact 64px faces so Del stays on-screen.
        int compact = AtlasButton.nativeWidth(TOOL);
        int g = 4;
        addRenderableWidget(new AtlasButton(originX, footerY,
                Component.literal("Export"), TOOL, b -> exportNow()));
        AtlasButton apply = new AtlasButton(originX + compact + g, footerY,
                Component.literal("Apply"), TOOL, b -> applyNow());
        apply.active = document.isApplyEnabled();
        apply.setTooltip(Tooltip.create(Component.literal(HairMakerDocument.APPLY_ENABLED_TOOLTIP)));
        addRenderableWidget(apply);
        addRenderableWidget(new AtlasButton(originX + (compact + g) * 2, footerY,
                Component.literal("Undo"), TOOL, b -> undoEdit()));
        addRenderableWidget(new AtlasButton(originX + (compact + g) * 3, footerY,
                Component.literal("Close"), TOOL, b -> onClose()));

        int right = originX + contentW - compact;
        addRenderableWidget(new AtlasButton(right - (compact + g) * 3, footerY,
                Component.literal("Create"), TOOL, b -> onCreateSegment()));
        AtlasButton weld = new AtlasButton(right - (compact + g) * 2, footerY,
                Component.literal("Weld"), TOOL, b -> onWeldSegment());
        weld.setTooltip(Tooltip.create(Component.literal(
                "Weld (offset copy): activate empty slot, copy parent with tip offset. "
                        + "DMZ has no parent NBT.")));
        addRenderableWidget(weld);
        addRenderableWidget(new AtlasButton(right - (compact + g), footerY,
                Component.literal("Dup"), TOOL, b -> onDuplicateSegment()));
        addRenderableWidget(new AtlasButton(right, footerY,
                Component.literal("Del"), TOOL, b -> onDeleteSegment()));

        syncFieldsFromSelected();
        refreshGlow();
        appearanceDirty = true;
        schedulePreview();
    }

    private void setViewportTool(HairViewportTool tool) {
        viewportTool = tool == null ? HairViewportTool.ROTATE : tool;
        status = "Tool " + viewportTool.label()
                + " — select in Outliner, drag in well to edit. Wheel = zoom. Right/Alt-drag = spin.";
        statusColor = MUTED;
    }

    private void onStyleIndexChanged(int index) {
        if (index < 0 || index >= HairMakerDocument.STYLE_NAMES.size()) {
            return;
        }
        pushHistory();
        ensurePresetNumericSynced();
        String styleId = HairMakerDocument.STYLE_NAMES.get(index);
        document.style(styleId);
        boolean loaded = false;
        if (selectedPresetNumeric >= 0) {
            loaded = HairPresetImport.loadIntoDocument(document, selectedPresetNumeric, styleId);
        }
        // Always tint SSJ slots gold so Base↔SSJ is obvious even when the preset is not a
        // full-set (same cube geometry for every style in HairManager).
        applyStyleTint(styleId);
        syncColorBoxesFromDocument();
        syncFieldsFromSelected();
        appearanceDirty = true;
        schedulePreview();
        status = loaded
                ? ("Loaded preset #" + selectedPresetNumeric + " / " + styleId
                + " (" + document.visibleCount() + " strands) — edit then Apply")
                : ("Style " + STYLE_DISPLAY_NAMES.get(index)
                + " — no DMZ preset geometry (id=" + selectedPresetNumeric
                + ", count=" + HairPresetImport.presetCountSafe() + ")");
        statusColor = loaded ? OK : WARN;
        refreshGlow();
    }

    /** Gold/black tint for Style cycler; Base keeps preset colour when present. */
    private void applyStyleTint(String styleId) {
        if (styleId == null || "Base".equals(styleId)) {
            return;
        }
        document.globalColor(defaultColorForStyle(styleId));
    }

    private void ensurePresetNumericSynced() {
        if (selectedPresetNumeric >= 0) {
            return;
        }
        if (selectedPresetId == null || selectedPresetId.isEmpty()) {
            if (!presetIds.isEmpty()) {
                selectedPresetId = presetIds.get(0);
            }
        }
        selectedPresetNumeric = HairPresetImport.parsePresetId(selectedPresetId);
    }

    private static String defaultColorForStyle(String styleId) {
        return switch (styleId == null ? "" : styleId) {
            case "SSJ" -> "#f5c842";
            case "SSJ2" -> "#ffe777";
            case "SSJ3" -> "#f2c84b";
            default -> "#171717";
        };
    }

    private void syncColorBoxesFromDocument() {
        if (hairColorBox == null) {
            return;
        }
        syncingFields = true;
        try {
            hairColorBox.setValue(document.globalColor());
            if (extraColorBox != null) {
                String extra = selectedExtraColor();
                extraColorBox.setValue(extra == null ? "" : extra);
            }
        } finally {
            syncingFields = false;
        }
    }

    private void pushHistory() {
        history.push(document);
    }

    private void undoEdit() {
        if (!history.undo(document)) {
            status = "Nothing to undo";
            statusColor = MUTED;
            return;
        }
        syncFieldsFromSelected();
        refreshGlow();
        appearanceDirty = true;
        schedulePreview();
        status = "Undo (" + history.undoSize() + " left)";
        statusColor = OK;
        rebuild();
    }

    private void redoEdit() {
        if (!history.redo(document)) {
            status = "Nothing to redo";
            statusColor = MUTED;
            return;
        }
        syncFieldsFromSelected();
        refreshGlow();
        appearanceDirty = true;
        schedulePreview();
        status = "Redo";
        statusColor = OK;
        rebuild();
    }

    private void onCreateSegment() {
        pushHistory();
        int idx = document.createSegment();
        if (idx < 0) {
            history.undo(document);
            status = "Face " + document.face() + " is full ("
                    + document.faceCapacity(document.face()) + " slots).";
            statusColor = WARN;
            return;
        }
        status = "Created " + document.face() + "[" + idx + "]";
        statusColor = OK;
        appearanceDirty = true;
        rebuild();
    }

    private void onWeldSegment() {
        pushHistory();
        int idx = document.weldSegment();
        if (idx < 0) {
            history.undo(document);
            status = "Weld needs a visible parent and an empty slot on this face.";
            statusColor = WARN;
            return;
        }
        status = "Welded child " + document.face() + "[" + idx + "] (offset copy)";
        statusColor = OK;
        appearanceDirty = true;
        rebuild();
    }

    private void onDuplicateSegment() {
        pushHistory();
        int idx = document.duplicateSegment();
        if (idx < 0) {
            history.undo(document);
            status = "Duplicate needs a visible strand and an empty slot.";
            statusColor = WARN;
            return;
        }
        status = "Duplicated to " + document.face() + "[" + idx + "]";
        statusColor = OK;
        appearanceDirty = true;
        rebuild();
    }

    private void onDeleteSegment() {
        pushHistory();
        document.deleteSegment();
        status = "Cleared " + document.face() + "[" + document.strandIndex() + "]";
        statusColor = MUTED;
        appearanceDirty = true;
        rebuild();
    }

    private void addSegmentEditors() {
        int fieldX = settingsX + 88;
        int fieldY = settingsY + 26;
        int boxW = 40;
        int boxH = 16;
        int gap = 4;

        lengthBox = field(fieldX, fieldY, boxW, boxH, "len", this::onLengthEdited);
        lengthScaleBox = field(fieldX + boxW + gap, fieldY, boxW, boxH, "ls", this::onLengthScaleEdited);
        // Compact +/- as EditBox-sized text buttons would overflow atlas chrome; use length box.

        rotXBox = field(fieldX, fieldY + 22, boxW, boxH, "rx",
                v -> onFloatEdited(v, HairStrandModel::rotationX));
        rotYBox = field(fieldX + boxW + gap, fieldY + 22, boxW, boxH, "ry",
                v -> onFloatEdited(v, HairStrandModel::rotationY));
        rotZBox = field(fieldX + (boxW + gap) * 2, fieldY + 22, boxW, boxH, "rz",
                v -> onFloatEdited(v, HairStrandModel::rotationZ));

        scaleXBox = field(fieldX, fieldY + 44, boxW, boxH, "sx",
                v -> onFloatEdited(v, HairStrandModel::scaleX));
        scaleYBox = field(fieldX + boxW + gap, fieldY + 44, boxW, boxH, "sy",
                v -> onFloatEdited(v, HairStrandModel::scaleY));
        scaleZBox = field(fieldX + (boxW + gap) * 2, fieldY + 44, boxW, boxH, "sz",
                v -> onFloatEdited(v, HairStrandModel::scaleZ));

        cubeWBox = field(fieldX, fieldY + 66, boxW, boxH, "cw",
                v -> onFloatEdited(v, HairStrandModel::cubeWidth));
        cubeHBox = field(fieldX + boxW + gap, fieldY + 66, boxW, boxH, "ch",
                v -> onFloatEdited(v, HairStrandModel::cubeHeight));
        cubeDBox = field(fieldX + (boxW + gap) * 2, fieldY + 66, boxW, boxH, "cd",
                v -> onFloatEdited(v, HairStrandModel::cubeDepth));

        curveXBox = field(fieldX, fieldY + 88, boxW, boxH, "cx",
                v -> onFloatEdited(v, HairStrandModel::curveX));
        curveYBox = field(fieldX + boxW + gap, fieldY + 88, boxW, boxH, "cy",
                v -> onFloatEdited(v, HairStrandModel::curveY));
        curveZBox = field(fieldX + (boxW + gap) * 2, fieldY + 88, boxW, boxH, "cz",
                v -> onFloatEdited(v, HairStrandModel::curveZ));
    }

    private void addColorEditors() {
        int colorY = settingsY + 140;
        int boxW = 56;
        int boxX = settingsX + 88;

        hairColorBox = colorField(boxX, colorY, boxW, document.globalColor(), v -> {
            document.globalColor(v);
            appearanceDirty = true;
            schedulePreview();
        });
        addRenderableWidget(hairColorBox);
        addRenderableWidget(new ColorSwatch(boxX + boxW + 2, colorY,
                () -> document.globalColor(),
                () -> openColorPicker(hairColorBox, h -> {
                    document.globalColor(h);
                    hairColorBox.setValue(h);
                    appearanceDirty = true;
                    schedulePreview();
                }),
                () -> colorPicker.isOpenFor(hairColorBox)));

        String extra = selectedExtraColor();
        extraColorBox = colorField(boxX, colorY + 22, boxW, extra, this::onExtraColorEdited);
        addRenderableWidget(extraColorBox);
        addRenderableWidget(new ColorSwatch(boxX + boxW + 2, colorY + 22,
                this::selectedExtraColor,
                () -> openColorPicker(extraColorBox, h -> {
                    onExtraColorEdited(h);
                    extraColorBox.setValue(h == null ? "" : h);
                }),
                () -> colorPicker.isOpenFor(extraColorBox)));
    }

    private EditBox field(int x, int y, int w, int h, String name,
                          java.util.function.Consumer<String> responder) {
        EditBox box = new EditBox(font, x, y, w, h, Component.literal(name));
        box.setMaxLength(12);
        box.setResponder(responder);
        addRenderableWidget(box);
        return box;
    }

    private EditBox colorField(int x, int y, int w, String initial,
                               java.util.function.Consumer<String> sink) {
        EditBox box = new EditBox(font, x, y, w, 16, Component.literal("colour"));
        box.setMaxLength(9);
        box.setValue(initial == null ? "" : initial);
        box.setResponder(text -> {
            if (!syncingFields && text != null) {
                sink.accept(text);
            }
        });
        return box;
    }

    private void openColorPicker(EditBox box, java.util.function.Consumer<String> onConfirm) {
        colorPicker.open(box, box.getX() + box.getWidth() + ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), onConfirm);
    }

    private void refreshPresets() {
        presetIds = MakerPresetCatalog.partIds(RaceMakerParts.Category.HAIR, "saiyan", "male");
        presetLabels = MakerPresetCatalog.labels(RaceMakerParts.Category.HAIR, "saiyan", "male");
        if (!selectedPresetId.isEmpty() && !presetIds.contains(selectedPresetId)) {
            selectedPresetId = "";
            selectedPresetNumeric = -1;
        }
        if (selectedPresetId.isEmpty() && !presetIds.isEmpty()) {
            selectedPresetId = presetIds.get(0);
        }
        // Seed editable base on first open: cycler used to show Hair N while numeric stayed -1,
        // so Style/SSJ never called HairManager and the 3D well never updated.
        boolean needsSeed = selectedPresetNumeric < 0;
        int parsed = HairPresetImport.parsePresetId(selectedPresetId);
        if (parsed >= 0) {
            selectedPresetNumeric = parsed;
        }
        if (needsSeed && selectedPresetNumeric >= 0) {
            boolean loaded = HairPresetImport.loadIntoDocument(
                    document, selectedPresetNumeric, document.style());
            applyStyleTint(document.style());
            if (loaded) {
                status = "Base = preset #" + selectedPresetNumeric + " / " + document.style()
                        + " (" + document.visibleCount() + " strands) — cycle Preset/Style to swap";
                statusColor = OK;
            } else {
                status = "Preset #" + selectedPresetNumeric + " empty in HairManager (count="
                        + HairPresetImport.presetCountSafe() + ") — cycle Preset";
                statusColor = WARN;
            }
            appearanceDirty = true;
        }
    }

    private void onFaceChanged(String next) {
        document.face(next);
        status = "Face " + document.face();
        statusColor = MUTED;
        rebuild();
    }

    private void onStrandLabelChanged(String label) {
        selectStrand(parseStrandIndex(label), true);
    }

    private void onPresetCycle(int index) {
        if (index < 0 || index >= presetIds.size()) {
            return;
        }
        pushHistory();
        selectedPresetId = presetIds.get(index);
        selectedPresetNumeric = HairPresetImport.parsePresetId(selectedPresetId);
        String label = index < presetLabels.size() ? presetLabels.get(index) : selectedPresetId;
        String styleId = document.style();
        boolean loaded = false;
        if (selectedPresetNumeric >= 0) {
            loaded = HairPresetImport.loadIntoDocument(document, selectedPresetNumeric, styleId);
        }
        // Fallback labels like "SSJ-style" when live catalog is empty: still flip style colour.
        if (!loaded) {
            String lower = label.toLowerCase(Locale.ROOT);
            if (lower.contains("ssj3")) {
                document.style("SSJ3");
                document.globalColor(defaultColorForStyle("SSJ3"));
            } else if (lower.contains("ssj2")) {
                document.style("SSJ2");
                document.globalColor(defaultColorForStyle("SSJ2"));
            } else if (lower.contains("ssj")) {
                document.style("SSJ");
                document.globalColor(defaultColorForStyle("SSJ"));
            }
        } else {
            applyStyleTint(styleId);
        }
        syncColorBoxesFromDocument();
        syncFieldsFromSelected();
        boolean live = MakerPresetCatalog.isLive(RaceMakerParts.Category.HAIR, "saiyan", "male");
        int count = HairPresetImport.presetCountSafe();
        status = loaded
                ? ("Base = preset #" + selectedPresetNumeric + " / " + document.style()
                + " (" + document.visibleCount() + " strands) — edit then Apply")
                : ("Preset " + label + " failed to load (id=" + selectedPresetNumeric
                + ", HairManager count=" + count + ", " + (live ? "live" : "FALLBACK") + ")");
        statusColor = loaded ? OK : WARN;
        refreshGlow();
        appearanceDirty = true;
        schedulePreview();
    }

    private void selectStyle(String styleId) {
        int idx = HairMakerDocument.STYLE_NAMES.indexOf(styleId);
        if (idx >= 0) {
            onStyleIndexChanged(idx);
            return;
        }
        document.style(styleId);
        appearanceDirty = true;
        schedulePreview();
        status = "Style " + displayStyle(document.style());
        statusColor = OK;
        refreshGlow();
    }

    private void selectStrand(int index, boolean rebuildToSyncCycle) {
        document.strandIndex(index);
        syncFieldsFromSelected();
        status = "Strand " + document.face() + "[" + document.strandIndex() + "]";
        statusColor = OK;
        refreshGlow();
        schedulePreview();
        if (rebuildToSyncCycle) {
            rebuild();
        }
    }

    private void refreshGlow() {
        HairStrandModel selected = document.selected();
        preview.setHairSegmentHighlight(document.face(), document.strandIndex(), selected);
    }

    private void onLengthEdited(String raw) {
        if (syncingFields) {
            return;
        }
        HairStrandModel strand = document.selected();
        if (strand == null) {
            return;
        }
        try {
            strand.length(Integer.parseInt(raw.trim()));
            appearanceDirty = true;
            schedulePreview();
        } catch (NumberFormatException ignored) {
            // leave previous value until a parseable edit
        }
    }

    private void onLengthScaleEdited(String raw) {
        if (syncingFields) {
            return;
        }
        HairStrandModel strand = document.selected();
        if (strand == null) {
            return;
        }
        try {
            strand.lengthScale(Float.parseFloat(raw.trim()));
            appearanceDirty = true;
            schedulePreview();
        } catch (NumberFormatException ignored) {
        }
    }

    @FunctionalInterface
    private interface FloatSetter {
        void set(HairStrandModel strand, float value);
    }

    private void onFloatEdited(String raw, FloatSetter setter) {
        if (syncingFields) {
            return;
        }
        HairStrandModel strand = document.selected();
        if (strand == null) {
            return;
        }
        try {
            setter.set(strand, Float.parseFloat(raw.trim()));
            appearanceDirty = true;
            schedulePreview();
        } catch (NumberFormatException ignored) {
        }
    }

    private void onExtraColorEdited(String raw) {
        if (syncingFields) {
            return;
        }
        HairStrandModel strand = document.selected();
        if (strand == null) {
            return;
        }
        strand.color(raw == null || raw.isBlank() ? null : raw.trim());
        appearanceDirty = true;
        schedulePreview();
    }

    private String selectedExtraColor() {
        HairStrandModel strand = document.selected();
        if (strand == null || strand.color() == null) {
            return "";
        }
        return strand.color();
    }

    private void schedulePreview() {
        // Always rebuild appearance from the document when dirty so style/preset loads show.
        if (appearanceDirty) {
            preview.setAppearance(MakerPreviewAppearance.fromHair(document));
            appearanceDirty = false;
        } else {
            // Still nudge a redraw (yaw / glow) without reallocating CustomHair.
            preview.markDirty();
        }
    }

    private void rebuild() {
        colorPicker.close();
        clearWidgets();
        init();
    }

    private void syncFieldsFromSelected() {
        HairStrandModel s = document.selected();
        syncingFields = true;
        try {
            if (s == null) {
                setBox(lengthBox, "");
                setBox(lengthScaleBox, "");
                setBox(rotXBox, "");
                setBox(rotYBox, "");
                setBox(rotZBox, "");
                setBox(scaleXBox, "");
                setBox(scaleYBox, "");
                setBox(scaleZBox, "");
                setBox(cubeWBox, "");
                setBox(cubeHBox, "");
                setBox(cubeDBox, "");
                setBox(curveXBox, "");
                setBox(curveYBox, "");
                setBox(curveZBox, "");
                setBox(extraColorBox, "");
                return;
            }
            setBox(lengthBox, Integer.toString(s.length()));
            setBox(lengthScaleBox, format(s.lengthScale()));
            setBox(rotXBox, format(s.rotationX()));
            setBox(rotYBox, format(s.rotationY()));
            setBox(rotZBox, format(s.rotationZ()));
            setBox(scaleXBox, format(s.scaleX()));
            setBox(scaleYBox, format(s.scaleY()));
            setBox(scaleZBox, format(s.scaleZ()));
            setBox(cubeWBox, format(s.cubeWidth()));
            setBox(cubeHBox, format(s.cubeHeight()));
            setBox(cubeDBox, format(s.cubeDepth()));
            setBox(curveXBox, format(s.curveX()));
            setBox(curveYBox, format(s.curveY()));
            setBox(curveZBox, format(s.curveZ()));
            setBox(extraColorBox, s.color() == null ? "" : s.color());
            setBox(hairColorBox, document.globalColor());
        } finally {
            syncingFields = false;
        }
    }

    private static void setBox(EditBox box, String value) {
        if (box != null) {
            box.setValue(value);
        }
    }

    private void exportNow() {
        try {
            Path dir = FMLPaths.GAMEDIR.get().resolve("xenopixelsmod").resolve("hair-exports");
            Files.createDirectories(dir);
            String stamp = LocalDate.now().toString();
            Path out = dir.resolve("hair-export-" + stamp + "-" + System.currentTimeMillis() + ".json");
            Files.writeString(out, document.toExportJson(stamp));
            status = "Exported " + out.getFileName()
                    + " (codes empty; apply=" + HairMakerDocument.APPLY_STATUS + ").";
            statusColor = OK;
        } catch (Exception e) {
            status = "Export failed: " + e.getMessage();
            statusColor = WARN;
        }
    }

    private void applyNow() {
        if (!document.isApplyEnabled()) {
            status = HairMakerDocument.APPLY_DISABLED_TOOLTIP;
            statusColor = WARN;
            return;
        }
        try {
            HairApplyService.applyClient(document);
            status = "Sent replace-current-style for " + displayStyle(document.style())
                    + " (full slot overwrite; " + HairMakerDocument.APPLY_STATUS + ").";
            statusColor = OK;
        } catch (Exception e) {
            status = "Apply failed: " + e.getMessage();
            statusColor = WARN;
        }
    }

    private List<String> strandLabels(String face) {
        List<HairStrandModel> strands = document.faceStrands(face);
        List<String> labels = new ArrayList<>(strands.size());
        for (int i = 0; i < strands.size(); i++) {
            HairStrandModel s = strands.get(i);
            labels.add(i + " id" + s.id() + (s.visible() ? "*" : ""));
        }
        if (labels.isEmpty()) {
            labels.add("0");
        }
        return labels;
    }

    private static int parseStrandIndex(String label) {
        if (label == null || label.isBlank()) {
            return 0;
        }
        int space = label.indexOf(' ');
        String head = space < 0 ? label : label.substring(0, space);
        try {
            return Integer.parseInt(head.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String displayStyle(String styleId) {
        int idx = HairMakerDocument.STYLE_NAMES.indexOf(styleId);
        if (idx >= 0 && idx < STYLE_DISPLAY_NAMES.size()) {
            return STYLE_DISPLAY_NAMES.get(idx);
        }
        return styleId == null ? "?" : styleId;
    }

    public static String styleIdAt(int index) {
        if (index < 0 || index >= HairMakerDocument.STYLE_NAMES.size()) {
            return HairMakerDocument.STYLE_NAMES.get(0);
        }
        return HairMakerDocument.STYLE_NAMES.get(index);
    }

    private static String format(float value) {
        if (value == (long) value) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
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
            graphics.drawCenteredString(font, "HAIR EDITOR",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            XenoAtlasSprites.setTheme(INNER);
            XenoAtlasSprites.blit(graphics, STYLE_LIST, INNER, listX, listY);
            XenoAtlasSprites.blit(graphics, PREVIEW, INNER, previewX, previewY);
            XenoAtlasSprites.blit(graphics, SETTINGS, INNER, settingsX, settingsY);

            outliner.render(graphics, font, document, listX, listY, listW, listH);

            graphics.drawString(font, "Viewport · " + viewportTool.label()
                            + " · " + Math.round(preview.zoom() * 100) + "%",
                    previewX + 10, previewY + 8, GOLD, false);
            graphics.drawString(font, displayStyle(document.style()) + " · " + document.face()
                            + "[" + document.strandIndex() + "]",
                    previewX + 10, previewY + 20, MUTED, false);

            graphics.drawString(font, "Inspector", settingsX + 10, settingsY + 8, GOLD, false);
            renderSettingLabels(graphics);

            int footerY = Math.max(listY + listH, Math.max(previewY + previewH, settingsY + settingsH)) + 8;
            graphics.drawString(font, status, originX, footerY + 26, statusColor, false);

            super.render(graphics, uiMx, uiMy, partialTick);
            // Player model in the well (scissored). Outliner redrawn after so the left list
            // is never covered by hair — Outliner panel ≠ green strand edge outline.
            preview.render(graphics, previewX + 8, previewY + 34,
                    previewW - 16, previewH - 46, partialTick);
            outliner.render(graphics, font, document, listX, listY, listW, listH);
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void renderSettingLabels(GuiGraphics graphics) {
        int y = settingsY + 28;
        graphics.drawString(font, "len / ls", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, "rot XYZ", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, "scale XYZ", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, "cube WHD", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, "curve XYZ", settingsX + 10, y, MUTED, false);
        y += 28;
        graphics.drawString(font, "Hair Color", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, "Extra Color", settingsX + 10, y, MUTED, false);
        y += 22;
        graphics.drawString(font, HairMakerDocument.CONNECTED_LABEL,
                settingsX + 10, y, MUTED, false);
    }

    private void renderStyleList(GuiGraphics graphics, int mouseX, int mouseY) {
        int rowH = 18;
        int maxVisible = Math.max(1, (listH - 28) / rowH);
        List<String> styles = HairMakerDocument.STYLE_NAMES;
        if (styleScroll > Math.max(0, styles.size() - maxVisible)) {
            styleScroll = Math.max(0, styles.size() - maxVisible);
        }
        int y = listY + 26;
        for (int i = styleScroll; i < styles.size() && (i - styleScroll) < maxVisible; i++) {
            String id = styles.get(i);
            boolean selected = id.equals(document.style());
            boolean hover = mouseX >= listX + 6 && mouseX < listX + listW - 6
                    && mouseY >= y - 2 && mouseY < y + rowH - 2;
            if (selected) {
                graphics.fill(listX + 4, y - 2, listX + listW - 4, y + rowH - 2, GLOW);
            }
            int color = selected ? OK : (hover ? GOLD : LIGHT);
            graphics.drawString(font, displayStyle(id), listX + 12, y, color, false);
            y += rowH;
        }
    }

    private void renderStrandList(GuiGraphics graphics, int mouseX, int mouseY) {
        List<HairStrandModel> strands = document.faceStrands(document.face());
        if (strands.isEmpty()) {
            return;
        }
        int rowH = 14;
        int areaTop = settingsY + 200;
        int areaBottom = settingsY + settingsH - 8;
        int maxVisible = Math.max(1, (areaBottom - areaTop) / rowH);
        if (strandScroll > Math.max(0, strands.size() - maxVisible)) {
            strandScroll = Math.max(0, strands.size() - maxVisible);
        }
        graphics.drawString(font, "Strands", settingsX + 10, areaTop - 12, GOLD, false);
        int y = areaTop;
        for (int i = strandScroll; i < strands.size() && (i - strandScroll) < maxVisible; i++) {
            HairStrandModel s = strands.get(i);
            boolean selected = i == document.strandIndex();
            boolean hover = mouseX >= settingsX + 6 && mouseX < settingsX + settingsW - 6
                    && mouseY >= y - 1 && mouseY < y + rowH - 1;
            if (selected) {
                graphics.fill(settingsX + 4, y - 1, settingsX + settingsW - 4, y + rowH - 1, GLOW);
            }
            int color = selected ? OK : (hover ? GOLD : LIGHT);
            String label = i + " id" + s.id() + (s.visible() ? "*" : "")
                    + " L" + s.length();
            graphics.drawString(font, label, settingsX + 10, y, color, false);
            y += rowH;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        // Outliner first — never let the 3D well steal left-list clicks.
        if (button == 0) {
            if (outliner.mouseClicked(document, uiMx, uiMy, listX, listY, listW, listH, () -> {
                syncFieldsFromSelected();
                refreshGlow();
                appearanceDirty = true;
                schedulePreview();
                status = "Outliner " + document.face() + "[" + document.strandIndex() + "]";
                statusColor = OK;
            })) {
                toolDragActive = false;
                return true;
            }
        }

        boolean inWell = preview.containsUiPoint((int) uiMx, (int) uiMy);
        if (!inWell) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        // Right-click in well starts orbit (drag continues in mouseDragged).
        if (button == 1) {
            toolDragActive = false;
            return true;
        }

        if (button == 0) {
            // Viewport click-to-pick is off. Select in the Outliner, then drag in the well
            // to edit the current strand (Grow/Rotate/Curve).
            if (document.selected() != null && document.selected().visible()) {
                toolDragActive = true;
                pushHistory();
                status = "Editing " + document.face() + "[" + document.strandIndex()
                        + "] with " + viewportTool.label() + " — pick strands in the Outliner";
                statusColor = MUTED;
            } else {
                toolDragActive = false;
                status = "Select a strand in the Outliner first";
                statusColor = WARN;
            }
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
        boolean inWell = preview.containsUiPoint((int) uiMx, (int) uiMy)
                || (uiMx >= previewX && uiMx < previewX + previewW
                && uiMy >= previewY && uiMy < previewY + previewH);
        if (!inWell) {
            return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        }
        // Right-drag or Alt+left-drag = spin character. Left-drag alone never yaws.
        boolean yawMode = button == 1 || (button == 0 && Screen.hasAltDown());
        if (yawMode) {
            preview.addYaw((float) dx * 0.75f);
            status = "Character yaw " + Math.round(preview.yaw());
            statusColor = MUTED;
            return true;
        }
        if (button == 0 && toolDragActive) {
            HairStrandModel strand = document.selected();
            if (strand != null) {
                viewportTool.applyDrag(strand, dx, dy);
                syncFieldsFromSelected();
                appearanceDirty = true;
                schedulePreview();
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        toolDragActive = false;
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
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (uiMx >= listX && uiMx < listX + listW && uiMy >= listY && uiMy < listY + listH) {
            outliner.scrollBy(scrollY > 0 ? -1 : 1);
            return true;
        }
        // Wheel over the preview well = zoom toward the mouse (pan keeps focus under cursor).
        if (preview.containsUiPoint((int) uiMx, (int) uiMy)
                || (uiMx >= previewX && uiMx < previewX + previewW
                && uiMy >= previewY && uiMy < previewY + previewH)) {
            preview.addZoomAt((float) scrollY, (float) uiMx, (float) uiMy);
            status = "Zoom " + Math.round(preview.zoom() * 100) + "% @ mouse · pick r="
                    + Math.round(preview.pickHitRadiusPx()) + "px";
            statusColor = MUTED;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (colorPicker.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        // Ctrl+Z / Ctrl+Y — GLFW key codes via Minecraft InputConstants would be ideal;
        // use common Z/Y with control modifier from Screen.
        if (Screen.hasControlDown()) {
            if (keyCode == 90) { // Z
                undoEdit();
                return true;
            }
            if (keyCode == 89) { // Y
                redoEdit();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return colorPicker.charTyped(codePoint, modifiers) || super.charTyped(codePoint, modifiers);
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

    public MakerPreviewController previewController() {
        return preview;
    }

    public HairMakerDocument document() {
        return document;
    }

    /** Test / command helper. */
    public static HairMakerScreen create(Screen parent) {
        return new HairMakerScreen(parent);
    }

    /** Opens from client command binding. */
    public static void openFromCommand() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(new HairMakerScreen(mc.screen));
    }
}
