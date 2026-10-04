package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzHairTypes;
import net.bullettrain.xenopixelsmod.hair.HairPresetImport;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Form Maker parts sub-screen (PR-D6e Image 2 owner note). Edits verified
 * {@link DmzFormDocument} / FormConfig.FormData appearance fields only — reuses
 * {@link RaceMakerParts} category labels + {@link MakerPresetCatalog} presentation patterns
 * for chrome, not invented part-write APIs.
 *
 * <p>Shared {@link MakerPreviewController} from the parent Form Maker; glow on selected
 * category; {@code markDirty} on every change. Save remains on the parent via
 * {@code FormEditorNetwork}.
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class FormMakerPartsScreen extends ScaledScreen {
    private static final String BANNER = "banner_top";
    private static final String CATEGORY_COL = "xeno_maker_category_col";
    private static final String PART_GRID = "xeno_maker_part_grid";
    private static final String PREVIEW = "xeno_maker_hair_preview";
    private static final String PRIMARY = "pill_button";
    private static final String TOOL = "mynpcs_button_row";
    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int OK = 0xFF9AFFB0;
    private static final int WARN = 0xFFFF8A80;
    private static final int GLOW = 0x9900C853;

    /** Verified FormData appearance keys grouped like RaceMakerParts categories. */
    public enum PartCategory {
        HAIR("Hair", List.of("hairType", "hairColor")),
        BODY("Body", List.of("bodyColor1", "bodyColor2", "bodyColor3")),
        EYES("Eyes", List.of("eye1Color", "eye2Color")),
        EXTRA("Extra", List.of("extraFormLayer", "extraFormColor", "extraAuraType", "extraAuraColor"));

        private final String label;
        private final List<String> keys;

        PartCategory(String label, List<String> keys) {
            this.label = label;
            this.keys = List.copyOf(keys);
        }

        public String label() {
            return label;
        }

        public List<String> keys() {
            return keys;
        }

        public static PartCategory fromLabel(String label) {
            if (label == null) {
                return HAIR;
            }
            for (PartCategory c : values()) {
                if (c.label.equalsIgnoreCase(label.trim())) {
                    return c;
                }
            }
            return HAIR;
        }
    }

    private final FormMakerScreen parent;
    private final DmzFormDocument document;
    private final MakerPreviewController preview;
    private final InlineColorPicker colorPicker = new InlineColorPicker();

    private PartCategory category = PartCategory.HAIR;
    private String selectedKey = "";
    private int hairPreset = 1;
    private String status = "Edit verified form appearance fields. Save on Form Maker.";
    private int statusColor = MUTED;

    private int originX;
    private int originY;
    private int bannerX;
    private int bannerY;
    private int bannerW;
    private int bannerH;
    private int categoryX;
    private int categoryY;
    private int categoryW;
    private int categoryH;
    private int gridX;
    private int gridY;
    private int gridW;
    private int gridH;
    private int previewX;
    private int previewY;
    private int previewW;
    private int previewH;

    public FormMakerPartsScreen(FormMakerScreen parent, DmzFormDocument document,
                                MakerPreviewController preview) {
        super(Component.literal("Form Parts"));
        this.parent = parent;
        this.document = document;
        this.preview = preview == null ? new MakerPreviewController() : preview;
    }

    @Override
    protected int getMinGuiWidth() {
        return 640;
    }

    @Override
    protected int getMinGuiHeight() {
        return 360;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);

        bannerW = XenoAtlasSprites.get(BANNER).width();
        bannerH = XenoAtlasSprites.get(BANNER).height();
        categoryW = XenoAtlasSprites.get(CATEGORY_COL).width();
        categoryH = XenoAtlasSprites.get(CATEGORY_COL).height();
        gridW = XenoAtlasSprites.get(PART_GRID).width();
        gridH = XenoAtlasSprites.get(PART_GRID).height();
        previewW = XenoAtlasSprites.get(PREVIEW).width();
        previewH = XenoAtlasSprites.get(PREVIEW).height();

        int extraCycleRows = category == PartCategory.HAIR ? 2 : 1;
        int cycleBlockH = extraCycleRows * 26 + 8;
        int contentW = categoryW + 8 + gridW + 8 + previewW;
        int contentH = bannerH + cycleBlockH + Math.max(categoryH, Math.max(gridH, previewH)) + 48;
        originX = Math.max(8, (getUiWidth() - contentW) / 2);
        originY = Math.max(4, (getUiHeight() - contentH) / 2);
        bannerX = originX + (contentW - bannerW) / 2;
        bannerY = originY;
        int cycleY1 = bannerY + bannerH + 4;
        int cycleY2 = cycleY1 + 26;
        categoryX = originX;
        categoryY = bannerY + bannerH + cycleBlockH;
        gridX = categoryX + categoryW + 8;
        gridY = categoryY;
        previewX = gridX + gridW + 8;
        previewY = categoryY;

        List<String> present = presentKeys(category);
        if (selectedKey.isEmpty() || !present.contains(selectedKey)) {
            selectedKey = present.isEmpty() ? "" : present.get(0);
        }

        clearWidgets();

        List<String> catLabels = categoryLabels();
        int catIndex = Math.max(0, catLabels.indexOf(category.label()));
        addRenderableWidget(new AtlasCycle(originX, cycleY1,
                Component.literal("Category"), catLabels, catIndex, this::onCategoryCycle));

        if (!present.isEmpty()) {
            List<String> labels = new ArrayList<>();
            for (String key : present) {
                labels.add(fieldLabel(key));
            }
            int fieldIndex = Math.max(0, present.indexOf(selectedKey));
            addRenderableWidget(new AtlasCycle(gridX, cycleY1,
                    Component.literal("Field"), labels, fieldIndex, this::onFieldCycle));
        }

        addRenderableWidget(new AtlasButton(previewX, cycleY1,
                Component.literal("Hair Editor"), TOOL, b -> openFormHairEditor()));

        if (category == PartCategory.HAIR) {
            List<String> hairLabels = MakerPresetCatalog.labels(
                    RaceMakerParts.Category.HAIR, documentRace(), "male");
            List<String> hairIds = MakerPresetCatalog.partIds(
                    RaceMakerParts.Category.HAIR, documentRace(), "male");
            if (!hairLabels.isEmpty()) {
                int hi = Math.max(0, hairIds.indexOf("hair:" + hairPreset));
                addRenderableWidget(new AtlasCycle(originX, cycleY2,
                        Component.literal("Hair preset"), hairLabels, hi, this::onHairPresetCycle));
            }
            int typeIndex = DmzHairTypes.indexOf(fieldValue("hairType"));
            addRenderableWidget(new AtlasCycle(gridX, cycleY2,
                    Component.literal("hairType"), DmzHairTypes.VALUES, typeIndex,
                    this::onHairTypeCycle));
        }

        addFieldEditors(present);

        int footerY = Math.max(categoryY + categoryH, previewY + previewH) + 8;
        addRenderableWidget(new AtlasButton(originX, footerY,
                Component.literal("Back"), PRIMARY, b -> onClose()));

        if (!selectedKey.isEmpty()) {
            preview.setGlow(MakerPreviewController.GlowTarget.PART_CATEGORY, selectedKey);
        }
        scheduleAppearance();
    }

    private void openFormHairEditor() {
        if (minecraft == null) {
            return;
        }
        minecraft.setScreen(new HairMakerScreen(this));
    }

    private void addFieldEditors(List<String> present) {
        int y = gridY + 28;
        int boxX = gridX + 90;
        int boxW = Math.max(48, gridW - 110);
        for (String key : present) {
            DmzFormDocument.Field field = fieldOf(key);
            if (field == null) {
                continue;
            }
            if (y + 20 > gridY + gridH - 8) {
                break;
            }
            boolean selected = key.equals(selectedKey);
            if (field.kind() == DmzFormDocument.Kind.COLOR) {
                EditBox box = new EditBox(font, boxX, y, boxW - ColorSwatch.W - 4, 16,
                        Component.literal(field.label()));
                box.setMaxLength(9);
                box.setValue(field.value() == null ? "" : field.value());
                String fieldKey = field.key();
                box.setResponder(raw -> {
                    selectKey(fieldKey, false);
                    apply(fieldKey, raw);
                });
                addRenderableWidget(box);
                addRenderableWidget(new ColorSwatch(boxX + boxW - ColorSwatch.W, y,
                        box::getValue,
                        () -> openColorPicker(box, h -> {
                            box.setValue(h);
                            selectKey(fieldKey, false);
                            apply(fieldKey, h);
                        }),
                        () -> colorPicker.isOpenFor(box)));
            } else {
                EditBox box = new EditBox(font, boxX, y, boxW, 16, Component.literal(field.label()));
                box.setMaxLength(256);
                box.setValue(field.value() == null ? "" : field.value());
                String fieldKey = field.key();
                box.setResponder(raw -> {
                    selectKey(fieldKey, false);
                    apply(fieldKey, raw);
                });
                addRenderableWidget(box);
            }
            if (selected) {
                // glow drawn in render
            }
            y += 22;
        }
    }

    private void openColorPicker(EditBox box, java.util.function.Consumer<String> onConfirm) {
        colorPicker.open(box, box.getX() + box.getWidth() + ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), onConfirm);
    }

    private void apply(String key, String raw) {
        if (!FormMakerScreen.knownFieldKeys(document).contains(key)) {
            status = "Ignored unknown field: " + key;
            statusColor = WARN;
            return;
        }
        try {
            document.update(key, raw == null ? "" : raw);
        } catch (RuntimeException ex) {
            status = "Invalid " + key + ": " + ex.getMessage();
            statusColor = WARN;
            return;
        }
        scheduleAppearance();
        status = "Part field " + key + " live on preview.";
        statusColor = MUTED;
    }

    private void onHairPresetCycle(int index) {
        List<String> ids = MakerPresetCatalog.partIds(
                RaceMakerParts.Category.HAIR, documentRace(), "male");
        if (index < 0 || index >= ids.size()) {
            return;
        }
        int parsed = HairPresetImport.parsePresetId(ids.get(index));
        if (parsed > 0) {
            hairPreset = parsed;
        }
        scheduleAppearance();
        status = "Form hair preset #" + hairPreset + " live on preview.";
        statusColor = OK;
    }

    private void onHairTypeCycle(int index) {
        if (index < 0 || index >= DmzHairTypes.VALUES.size()) {
            return;
        }
        apply("hairType", DmzHairTypes.VALUES.get(index));
    }

    private void scheduleAppearance() {
        MakerPreviewAppearance appearance = new MakerPreviewAppearance().race(documentRace());
        String hairColor = fieldValue("hairColor");
        String auraColor = fieldValue("auraColor");
        if (hairColor != null && !hairColor.isBlank()) {
            appearance.hairColor(hairColor);
        }
        if (auraColor != null && !auraColor.isBlank()) {
            appearance.auraColor(auraColor);
        }
        String body = fieldValue("bodyColor1");
        if (body != null && !body.isBlank()) {
            appearance.bodyColor(body);
        }
        if (hairPreset > 0) {
            String slot = fieldValue("hairType");
            if (slot == null || slot.isBlank()) {
                slot = "Base";
            }
            String style = switch (slot.toLowerCase(java.util.Locale.ROOT)) {
                case "ssj" -> "SSJ";
                case "ssj2" -> "SSJ2";
                case "ssj3" -> "SSJ3";
                default -> "Base";
            };
            var hair = HairPresetImport.fetchPreset(hairPreset, style);
            if (hair != null) {
                appearance.hair(hair);
            }
        }
        if (document != null) {
            appearance.activeForm(document.group(), document.form()).formData(document.previewData());
        }
        preview.setAppearance(appearance);
        preview.markDirty();
    }

    private String documentRace() {
        return document == null || document.race() == null || document.race().isBlank()
                ? "saiyan" : document.race();
    }

    private String fieldValue(String key) {
        DmzFormDocument.Field field = fieldOf(key);
        return field == null || field.value() == null ? "" : field.value();
    }

    private void onCategoryCycle(int index) {
        PartCategory[] cats = PartCategory.values();
        if (index >= 0 && index < cats.length) {
            selectCategory(cats[index]);
        }
    }

    private void onFieldCycle(int index) {
        List<String> present = presentKeys(category);
        if (index >= 0 && index < present.size()) {
            selectKey(present.get(index), true);
        }
    }

    private void selectCategory(PartCategory next) {
        category = next == null ? PartCategory.HAIR : next;
        selectedKey = "";
        List<String> present = presentKeys(category);
        if (!present.isEmpty()) {
            selectedKey = present.get(0);
            preview.setGlow(MakerPreviewController.GlowTarget.PART_CATEGORY, selectedKey);
        }
        status = category.label() + " — verified FormData keys only"
                + (present.isEmpty() ? " (none present on document)" : "");
        statusColor = present.isEmpty() ? WARN : MUTED;
        preview.markDirty();
        rebuild();
    }

    private void selectKey(String key, boolean rebuildUi) {
        selectedKey = key == null ? "" : key;
        preview.setGlow(MakerPreviewController.GlowTarget.PART_CATEGORY, selectedKey);
        preview.markDirty();
        if (rebuildUi) {
            rebuild();
        }
    }

    private void rebuild() {
        colorPicker.close();
        init();
    }

    private List<String> presentKeys(PartCategory cat) {
        List<String> out = new ArrayList<>();
        for (String key : cat.keys()) {
            if (fieldOf(key) != null) {
                out.add(key);
            }
        }
        return out;
    }

    private DmzFormDocument.Field fieldOf(String key) {
        if (document == null) {
            return null;
        }
        for (DmzFormDocument.Field field : document.fields()) {
            if (key.equals(field.key())) {
                return field;
            }
        }
        return null;
    }

    private String fieldLabel(String key) {
        DmzFormDocument.Field field = fieldOf(key);
        if (field != null && field.label() != null && !field.label().isBlank()) {
            return field.label();
        }
        return key;
    }

    public static List<String> categoryLabels() {
        List<String> labels = new ArrayList<>();
        for (PartCategory c : PartCategory.values()) {
            labels.add(c.label());
        }
        return labels;
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
            graphics.drawCenteredString(font, "FORM PARTS",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            XenoAtlasSprites.setTheme(INNER);
            XenoAtlasSprites.blit(graphics, CATEGORY_COL, INNER, categoryX, categoryY);
            XenoAtlasSprites.blit(graphics, PART_GRID, INNER, gridX, gridY);
            XenoAtlasSprites.blit(graphics, PREVIEW, INNER, previewX, previewY);

            renderCategories(graphics, uiMx, uiMy);
            graphics.drawString(font, category.label(), gridX + 10, gridY + 8, GOLD, false);
            renderFieldLabels(graphics);

            graphics.drawString(font, "Preview", previewX + 10, previewY + 8, GOLD, false);

            int footerY = Math.max(categoryY + categoryH, previewY + previewH) + 8;
            graphics.drawString(font, status, originX, footerY + 24, statusColor, false);
            String id = document == null ? ""
                    : document.race() + "/" + document.group() + "/" + document.form();
            graphics.drawString(font, id, originX + 120, footerY + 8, MUTED, false);

            super.render(graphics, uiMx, uiMy, partialTick);
            // Player model last so atlas widgets never cover it.
            preview.render(graphics, previewX + 8, previewY + 24,
                    previewW - 16, previewH - 36, partialTick);
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        int y = categoryY + 12;
        for (PartCategory cat : PartCategory.values()) {
            boolean selected = cat == category;
            boolean hover = mouseX >= categoryX + 6 && mouseX < categoryX + categoryW - 6
                    && mouseY >= y - 2 && mouseY < y + 14;
            if (selected) {
                graphics.fill(categoryX + 4, y - 2, categoryX + categoryW - 4, y + 14, GLOW);
            }
            int color = selected ? OK : (hover ? GOLD : LIGHT);
            graphics.drawString(font, cat.label(), categoryX + 12, y, color, false);
            y += 18;
        }
    }

    private void renderFieldLabels(GuiGraphics graphics) {
        List<String> present = presentKeys(category);
        if (present.isEmpty()) {
            graphics.drawWordWrap(font,
                    Component.literal("No verified appearance keys present on this form document."),
                    gridX + 10, gridY + 28, gridW - 20, MUTED);
            return;
        }
        int y = gridY + 28;
        for (String key : present) {
            if (y + 20 > gridY + gridH - 8) {
                break;
            }
            boolean selected = key.equals(selectedKey);
            if (selected) {
                graphics.fill(gridX + 6, y - 2, gridX + 86, y + 18, GLOW);
            }
            graphics.drawString(font, shortLabel(fieldLabel(key)), gridX + 10, y + 4,
                    selected ? OK : MUTED, false);
            y += 22;
        }
    }

    private static String shortLabel(String label) {
        if (label == null) {
            return "";
        }
        String trimmed = label.replace("Color", "").trim();
        if (trimmed.isEmpty()) {
            trimmed = label;
        }
        return trimmed.length() <= 10 ? trimmed : trimmed.substring(0, 9) + "…";
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (button == 0) {
            int cy = categoryY + 12;
            for (PartCategory cat : PartCategory.values()) {
                if (uiMx >= categoryX + 6 && uiMx < categoryX + categoryW - 6
                        && uiMy >= cy - 2 && uiMy < cy + 14) {
                    selectCategory(cat);
                    return true;
                }
                cy += 18;
            }
            List<String> present = presentKeys(category);
            int y = gridY + 28;
            for (String key : present) {
                if (y + 20 > gridY + gridH - 8) {
                    break;
                }
                if (uiMx >= gridX + 6 && uiMx < gridX + 86
                        && uiMy >= y - 2 && uiMy < y + 18) {
                    selectKey(key, true);
                    return true;
                }
                y += 22;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (colorPicker.isOpen() && colorPicker.mouseDragged(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseReleased(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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
    public void onClose() {
        parent.onPartsClosed();
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public PartCategory selectedCategory() {
        return category;
    }

    public String selectedKey() {
        return selectedKey;
    }
}
