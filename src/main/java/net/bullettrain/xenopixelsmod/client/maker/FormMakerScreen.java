package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzFormEditor;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.ColorSwatch;
import net.bullettrain.xenopixelsmod.client.ui.atlas.InlineColorPicker;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormSaveGate;
import net.bullettrain.xenopixelsmod.dmz.form.RaceFormGroupGuard;
import net.bullettrain.xenopixelsmod.dmz.race.RacePackService;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Exact-layout Form Maker (PR-D6e / KD15 r3 Images 2–4) + Task-10 chrome upgrade.
 *
 * <p>Chrome: gold {@code banner_top}; green Task-9 panels {@code xeno_maker_form_list},
 * {@code xeno_maker_form_settings}, right-column {@code xeno_maker_hair_preview}. Controls:
 * {@link AtlasCycle} race/group, {@link ColorSwatch}+{@link InlineColorPicker} for verified
 * colour fields, true-player {@link MakerPreviewController} with live hair/aura override.
 * Save via existing {@link FormEditorNetwork#save} only — no invented form schema keys.
 *
 * <p>Parts sub-screen: {@link FormMakerPartsScreen}.
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class FormMakerScreen extends ScaledScreen {
    private static final String BANNER = "banner_top";
    private static final String FORM_LIST = "xeno_maker_form_list";
    private static final String FORM_SETTINGS = "xeno_maker_form_settings";
    private static final String PREVIEW_BODY = "xeno_maker_hair_preview";
    private static final String TOOL = "mynpcs_button_row";
    private static final XenoAtlasSprites.Theme CHROME = XenoAtlasSprites.Theme.GOLD;
    private static final XenoAtlasSprites.Theme INNER = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int OK = 0xFF9AFFB0;
    private static final int WARN = 0xFFFF8A80;
    private static final int CYAN = 0xFF80D8FF;
    private static final int GLOW = 0x9900C853;

    /**
     * Primary Form Settings keys — must exist on {@link DmzFormDocument#fields()} /
     * FormConfig.FormData. Labels come from the document; never invent schema keys.
     */
    public static final List<String> SETTINGS_KEYS = List.of(
            "$race",
            "$formType",
            "transformationAnimation",
            "auraType",
            "auraColor",
            "hairColor",
            DmzFormDocument.SCALE_UNIFORM
    );

    private final Screen parent;
    private final MakerPreviewController preview = new MakerPreviewController();
    private final InlineColorPicker colorPicker = new InlineColorPicker();
    private final String sessionId = "xeno-form-maker-" + UUID.randomUUID();
    private final DmzFormSaveGate saveGate =
            new DmzFormSaveGate(GuiNpcDmzFormEditor.SAVE_DEBOUNCE_MS);

    private String race = "saiyan";
    private String group = "";
    private String selectedForm = "";
    private DmzFormDocument document;
    private List<String> formIds = List.of();
    private String status = "Select race/group/form. Save = FormEditorNetwork.";
    private int statusColor = MUTED;
    private int seenResult = DmzFormEditorClientState.sequence();
    private int formScroll;

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
    private int footerY;

    public FormMakerScreen(Screen parent) {
        super(Component.literal("Form Maker"));
        this.parent = parent;
    }

    @Override
    protected int getMinGuiWidth() {
        return 680;
    }

    @Override
    protected int getMinGuiHeight() {
        return 400;
    }

    @Override
    protected void init() {
        super.init();
        preview.bindLocalPlayer(minecraft);

        MakerPreviewLayout.Columns layout = MakerPreviewLayout.compute(
                getUiWidth(), getUiHeight(), FORM_LIST, FORM_SETTINGS);
        originX = layout.originX();
        originY = layout.originY();
        bannerX = layout.bannerX();
        bannerY = layout.bannerY();
        bannerW = layout.bannerW();
        bannerH = layout.bannerH();
        listX = layout.listX();
        listY = layout.listY();
        listW = layout.listW();
        listH = layout.listH();
        settingsX = layout.settingsX();
        settingsY = layout.settingsY();
        settingsW = layout.settingsW();
        settingsH = layout.settingsH();
        previewX = layout.previewX();
        previewY = layout.previewY();
        previewW = layout.previewW();
        previewH = layout.previewH();
        footerY = layout.footerY();

        refreshGroupsAndForms();
        reloadDocument();

        clearWidgets();

        List<String> races = raceIds();
        int raceIndex = Math.max(0, races.indexOf(race));
        addRenderableWidget(new AtlasCycle(originX, bannerY + bannerH + 6,
                Component.literal("Race"), races, raceIndex, this::onRaceChanged));

        List<String> groups = groupsFor(race);
        if (groups.isEmpty()) {
            groups = List.of(group.isEmpty() ? "(none)" : group);
        }
        int groupIndex = Math.max(0, groups.indexOf(group.isEmpty() ? groups.get(0) : group));
        addRenderableWidget(new AtlasCycle(settingsX, bannerY + bannerH + 6,
                Component.literal("Group"), groups, groupIndex,
                this::onGroupChanged));

        addSettingsWidgets();

        int compact = AtlasButton.nativeWidth(TOOL);
        int g = 4;
        addRenderableWidget(new AtlasButton(originX, footerY,
                Component.literal("Parts"), TOOL, b -> openParts()));
        addRenderableWidget(new AtlasButton(originX + compact + g, footerY,
                Component.literal("Hair Editor"), TOOL, b -> {
                    if (minecraft != null) {
                        minecraft.setScreen(new HairMakerScreen(this));
                    }
                }));
        addRenderableWidget(new AtlasButton(originX + (compact + g) * 2, footerY,
                Component.literal("New Form"), TOOL, b -> createNewForm()));
        addRenderableWidget(new AtlasButton(originX + (compact + g) * 3, footerY,
                Component.literal("Save"), TOOL, b -> saveNow()));
        addRenderableWidget(new AtlasButton(originX + (compact + g) * 4, footerY,
                Component.literal("Close"), TOOL, b -> onClose()));

        if (document != null && !selectedForm.isEmpty()) {
            preview.setGlow(MakerPreviewController.GlowTarget.FORM_ROW, selectedForm);
        }
        schedulePreview();
    }

    private void schedulePreview() {
        MakerPreviewAppearance appearance = new MakerPreviewAppearance();
        String hair = fieldValue("hairColor");
        String aura = fieldValue("auraColor");
        if (hair != null && !hair.isBlank()) {
            appearance.hairColor(hair);
        }
        if (aura != null && !aura.isBlank()) {
            appearance.auraColor(aura);
        }
        if (group != null && !group.isBlank() && selectedForm != null && !selectedForm.isBlank()) {
            appearance.activeForm(group, selectedForm);
        }
        preview.setAppearance(appearance);
        preview.markDirty();
    }

    private void addSettingsWidgets() {
        if (document == null) {
            return;
        }
        int y = settingsY + 22;
        int labelW = 78;
        int boxX = settingsX + 10 + labelW;
        int boxW = Math.max(48, settingsW - labelW - 36);
        for (String key : SETTINGS_KEYS) {
            DmzFormDocument.Field field = fieldOf(key);
            if (field == null) {
                continue;
            }
            if (y + 20 > settingsY + settingsH - 8) {
                break;
            }
            if (field.kind() == DmzFormDocument.Kind.COLOR) {
                EditBox box = new EditBox(font, boxX, y, boxW - ColorSwatch.W - 4, 16,
                        Component.literal(field.label()));
                box.setMaxLength(9);
                box.setValue(field.value() == null ? "" : field.value());
                String fieldKey = field.key();
                box.setResponder(raw -> applyVerifiedField(fieldKey, raw));
                addRenderableWidget(box);
                addRenderableWidget(new ColorSwatch(boxX + boxW - ColorSwatch.W, y,
                        box::getValue,
                        () -> openColorPicker(box, h -> {
                            box.setValue(h);
                            applyVerifiedField(fieldKey, h);
                        }),
                        () -> colorPicker.isOpenFor(box)));
            } else if (field.kind() == DmzFormDocument.Kind.BOOL) {
                boolean on = "true".equalsIgnoreCase(field.value()) || "1".equals(field.value());
                String fieldKey = field.key();
                addRenderableWidget(new AtlasCycle(boxX, y, Component.empty(),
                        List.of("No", "Yes"), on ? 1 : 0, (java.util.function.IntConsumer) i -> {
                    applyVerifiedField(fieldKey, i == 1 ? "true" : "false");
                    rebuild();
                }).narrationLabel(Component.literal(field.label())));
            } else {
                EditBox box = new EditBox(font, boxX, y, boxW, 16, Component.literal(field.label()));
                box.setMaxLength(field.kind() == DmzFormDocument.Kind.JSON ? 8192 : 256);
                box.setValue(field.value() == null ? "" : field.value());
                String fieldKey = field.key();
                box.setResponder(raw -> applyVerifiedField(fieldKey, raw));
                addRenderableWidget(box);
            }
            y += 22;
        }
    }

    private void openColorPicker(EditBox box, java.util.function.Consumer<String> onConfirm) {
        colorPicker.open(box, box.getX() + box.getWidth() + ColorSwatch.W + 6, box.getY(),
                getUiWidth(), getUiHeight(), box.getValue(), onConfirm);
    }

    /**
     * Updates only keys present on the loaded document's verified field list.
     * Unknown keys are ignored (no invented schema writes).
     */
    public boolean applyVerifiedField(String key, String raw) {
        if (document == null || key == null || key.isBlank()) {
            return false;
        }
        if (!knownFieldKeys(document).contains(key)) {
            status = "Ignored unknown field: " + key;
            statusColor = WARN;
            return false;
        }
        try {
            document.update(key, raw == null ? "" : raw);
        } catch (RuntimeException ex) {
            status = "Invalid value for " + key + ": " + ex.getMessage();
            statusColor = WARN;
            return false;
        }
        saveGate.touch(System.currentTimeMillis());
        schedulePreview();
        status = "Edited " + key + " — live preview ≤50 ms; save via FormEditorNetwork.";
        statusColor = MUTED;
        return true;
    }

    public static Set<String> knownFieldKeys(DmzFormDocument document) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        if (document == null) {
            return keys;
        }
        for (DmzFormDocument.Field field : document.fields()) {
            if (field != null && field.key() != null) {
                keys.add(field.key());
            }
        }
        return keys;
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

    private void onRaceChanged(String next) {
        race = next == null ? "saiyan" : next.trim().toLowerCase(Locale.ROOT);
        group = firstGroup(race);
        selectedForm = "";
        formScroll = 0;
        status = "Race " + race;
        statusColor = MUTED;
        rebuild();
    }

    private void onGroupChanged(String next) {
        if ("(none)".equals(next)) {
            return;
        }
        group = next == null ? "" : next.trim().toLowerCase(Locale.ROOT);
        selectedForm = "";
        formScroll = 0;
        status = "Group " + group;
        statusColor = MUTED;
        rebuild();
    }

    private void selectForm(String formId) {
        selectedForm = formId == null ? "" : formId;
        reloadDocument();
        preview.setGlow(MakerPreviewController.GlowTarget.FORM_ROW, selectedForm);
        status = "Form " + selectedForm;
        statusColor = OK;
        schedulePreview();
        rebuild();
    }

    private void refreshGroupsAndForms() {
        if (group == null || group.isBlank() || "(none)".equals(group)) {
            group = firstGroup(race);
        }
        formIds = formsFor(race, group);
        if (!selectedForm.isEmpty() && !formIds.contains(selectedForm)) {
            selectedForm = "";
        }
        if (selectedForm.isEmpty() && !formIds.isEmpty()) {
            selectedForm = formIds.get(0);
        }
    }

    private void reloadDocument() {
        refreshGroupsAndForms();
        if (selectedForm == null || selectedForm.isBlank()) {
            document = null;
            return;
        }
        RaceFormGroupGuard.Result raceResult = RaceFormGroupGuard.validateRace(race);
        if (!raceResult.ok()) {
            // Still allow RacePackService-known races that pass pack listing but not the
            // form-group guard's fixed set — load when ConfigManager has the group.
            document = tryLoad(race, group, selectedForm);
            return;
        }
        document = tryLoad(race, group, selectedForm);
    }

    private static DmzFormDocument tryLoad(String race, String group, String form) {
        try {
            return DmzFormDocument.load(DmzFormKind.NORMAL, race, group, form);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void createNewForm() {
        DmzFormDocument draft;
        try {
            if (document != null) {
                draft = document.copyAsNewDraft();
            } else {
                draft = DmzFormDocument.create(DmzFormKind.NORMAL, race);
            }
        } catch (RuntimeException ex) {
            status = "Could not draft a new form: " + ex.getMessage();
            statusColor = WARN;
            return;
        }
        document = draft;
        race = draft.race().isBlank() ? race : draft.race();
        group = draft.group();
        selectedForm = draft.form();
        status = "New form " + group + "/" + selectedForm + " — edit Parts/hair then Save.";
        statusColor = OK;
        schedulePreview();
        rebuild();
    }

    private void openParts() {
        if (document == null) {
            status = "Load a form before opening Parts.";
            statusColor = WARN;
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(new FormMakerPartsScreen(this, document, preview));
        }
    }

    private void saveNow() {
        if (document == null) {
            status = "Nothing to save.";
            statusColor = WARN;
            return;
        }
        String identity = document.identityError();
        if (identity != null) {
            status = identity;
            statusColor = WARN;
            return;
        }
        if (DmzFormEditorClientState.inFlight()) {
            status = "Waiting for the server...";
            statusColor = GOLD;
            return;
        }
        DmzFormEditorClientState.begin();
        FormEditorNetwork.save(document.kind(), document.race(), document.group(),
                document.formJson(), document.metadataJson(), document.revision(), sessionId);
        saveGate.sent();
        status = "Save sent on FormEditorNetwork path.";
        statusColor = GOLD;
    }

    private void rebuild() {
        colorPicker.close();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        int seq = DmzFormEditorClientState.sequence();
        if (seq != seenResult) {
            seenResult = seq;
            if (DmzFormEditorClientState.success()) {
                if (document != null) {
                    document.revision(DmzFormEditorClientState.revision());
                    document.markCreated();
                }
                status = "Saved via FormEditorNetwork. Revision "
                        + (document == null ? "?" : document.revision()) + ".";
                statusColor = OK;
            } else {
                String message = DmzFormEditorClientState.message();
                status = message == null || message.isBlank()
                        ? "The server rejected the save." : message;
                statusColor = WARN;
            }
        }
        if (DmzFormEditorClientState.inFlight()) {
            status = "Waiting for the server...";
            statusColor = GOLD;
        }
        long now = System.currentTimeMillis();
        if (document != null
                && saveGate.shouldSend(now, DmzFormEditorClientState.inFlight(), document.created())) {
            saveNow();
        }
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
            graphics.drawCenteredString(font, "FORM MAKER",
                    bannerX + bannerW / 2, bannerY + bannerH / 2 - 4, GOLD);

            XenoAtlasSprites.setTheme(INNER);
            XenoAtlasSprites.blit(graphics, FORM_LIST, INNER, listX, listY);
            XenoAtlasSprites.blit(graphics, FORM_SETTINGS, INNER, settingsX, settingsY);
            XenoAtlasSprites.blit(graphics, PREVIEW_BODY, INNER, previewX, previewY);

            graphics.drawString(font, "Forms", listX + 10, listY + 8, GOLD, false);
            renderFormList(graphics, uiMx, uiMy);

            graphics.drawString(font, "Form Settings", settingsX + 10, settingsY + 8, GOLD, false);
            renderSettingLabels(graphics);

            graphics.drawString(font, "Preview", previewX + 10, previewY + 8, GOLD, false);
            renderAuraSummary(graphics);

            graphics.drawString(font, status, originX, footerY + 24, statusColor, false);

            super.render(graphics, uiMx, uiMy, partialTick);
            // Player model last so atlas widgets never cover it.
            preview.render(graphics, previewX + 8, previewY + 34,
                    previewW - 16, previewH - 46, partialTick);
            colorPicker.render(graphics, uiMx, uiMy, partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
    }

    private void renderSettingLabels(GuiGraphics graphics) {
        if (document == null) {
            graphics.drawWordWrap(font, Component.literal("No form loaded for this race/group."),
                    settingsX + 10, settingsY + 28, settingsW - 20, MUTED);
            return;
        }
        int y = settingsY + 22;
        for (String key : SETTINGS_KEYS) {
            DmzFormDocument.Field field = fieldOf(key);
            if (field == null) {
                continue;
            }
            if (y + 20 > settingsY + settingsH - 8) {
                break;
            }
            graphics.drawString(font, shortLabel(field.label()), settingsX + 10, y + 4, MUTED, false);
            y += 22;
        }
    }

    private void renderAuraSummary(GuiGraphics graphics) {
        // Compact summary under the Preview title, above the live model well.
        int y = previewY + 20;
        if (document == null) {
            graphics.drawString(font, "—", previewX + 10, y, MUTED, false);
            return;
        }
        graphics.drawString(font, clip(document.race() + "/" + document.group() + "/" + document.form(), 28),
                previewX + 10, y, CYAN, false);
        graphics.drawString(font, "aura " + clip(fieldValue("auraColor"), 10)
                        + "  hair " + clip(fieldValue("hairColor"), 10),
                previewX + 10, y + 11, MUTED, false);
    }

    private void renderFormList(GuiGraphics graphics, int mouseX, int mouseY) {
        if (formIds.isEmpty()) {
            graphics.drawWordWrap(font,
                    Component.literal("No forms for this race/group (ConfigManager empty)."),
                    listX + 10, listY + 28, listW - 20, MUTED);
            return;
        }
        int rowH = 18;
        int maxVisible = Math.max(1, (listH - 28) / rowH);
        if (formScroll > Math.max(0, formIds.size() - maxVisible)) {
            formScroll = Math.max(0, formIds.size() - maxVisible);
        }
        int y = listY + 26;
        for (int i = formScroll; i < formIds.size() && (i - formScroll) < maxVisible; i++) {
            String id = formIds.get(i);
            boolean selected = id.equals(selectedForm);
            boolean hover = mouseX >= listX + 6 && mouseX < listX + listW - 6
                    && mouseY >= y - 2 && mouseY < y + rowH - 2;
            if (selected) {
                graphics.fill(listX + 4, y - 2, listX + listW - 4, y + rowH - 2, GLOW);
            }
            int color = selected ? OK : (hover ? GOLD : LIGHT);
            graphics.drawString(font, displayForm(id), listX + 12, y, color, false);
            y += rowH;
        }
    }

    private String fieldValue(String key) {
        DmzFormDocument.Field field = fieldOf(key);
        return field == null || field.value() == null ? "" : field.value();
    }

    private static String shortLabel(String label) {
        if (label == null) {
            return "";
        }
        return label.length() <= 12 ? label : label.substring(0, 11) + "…";
    }

    private static String clip(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, Math.max(0, max - 1)) + "…";
    }

    static String displayForm(String id) {
        if (id == null || id.isBlank()) {
            return "?";
        }
        String raw = id.replace('_', ' ');
        return raw.isEmpty() ? id
                : Character.toUpperCase(raw.charAt(0)) + raw.substring(1);
    }

    private static List<String> raceIds() {
        LinkedHashSet<String> ids = new LinkedHashSet<>(RacePackService.knownRaceIds());
        ids.addAll(RaceFormGroupGuard.knownRaces());
        try {
            ids.addAll(NpcFormLookup.races());
        } catch (Throwable ignored) {
            // ConfigManager may be unavailable outside a running client.
        }
        List<String> sorted = new ArrayList<>(ids);
        sorted.sort((a, b) -> {
            boolean ac = RacePackService.isCustomPack(a);
            boolean bc = RacePackService.isCustomPack(b);
            if (ac != bc) {
                return ac ? -1 : 1;
            }
            return a.compareTo(b);
        });
        if (sorted.isEmpty()) {
            return List.of("saiyan", "human", "namekian", "frostdemon", "majin", "bioandroid");
        }
        return sorted;
    }

    private static String firstGroup(String race) {
        List<String> groups = groupsFor(race);
        return groups.isEmpty() ? "" : groups.get(0);
    }

    private static List<String> groupsFor(String race) {
        LinkedHashSet<String> groups = new LinkedHashSet<>(RaceFormGroupGuard.installedGroups(race));
        try {
            List<String> live = NpcFormLookup.groups(race);
            if (live != null) {
                groups.addAll(live);
            }
        } catch (Throwable ignored) {
            // ConfigManager may be unavailable outside a running client.
        }
        List<String> sorted = new ArrayList<>(groups);
        sorted.sort(String::compareTo);
        return sorted;
    }

    private static List<String> formsFor(String race, String group) {
        try {
            List<String> live = NpcFormLookup.forms(race, group);
            if (live != null && !live.isEmpty()) {
                return live;
            }
        } catch (Throwable ignored) {
            // ConfigManager may be unavailable outside a running client.
        }
        return List.of();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (colorPicker.isOpen() && colorPicker.mouseClicked(toUiX(mouseX), toUiY(mouseY), button)) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (button == 0 && !formIds.isEmpty()) {
            int rowH = 18;
            int maxVisible = Math.max(1, (listH - 28) / rowH);
            int y = listY + 26;
            for (int i = formScroll; i < formIds.size() && (i - formScroll) < maxVisible; i++) {
                if (uiMx >= listX + 6 && uiMx < listX + listW - 6
                        && uiMy >= y - 2 && uiMy < y + rowH - 2) {
                    selectForm(formIds.get(i));
                    return true;
                }
                y += rowH;
            }
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
        if (button == 0 && uiMx >= previewX && uiMx < previewX + previewW
                && uiMy >= previewY && uiMy < previewY + previewH) {
            preview.addYaw((float) dx * 0.6f);
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
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (colorPicker.isOpen()) {
            return true;
        }
        double uiMx = toUiX(mouseX);
        double uiMy = toUiY(mouseY);
        if (uiMx >= listX && uiMx < listX + listW && uiMy >= listY && uiMy < listY + listH) {
            int rowH = 18;
            int maxVisible = Math.max(1, (listH - 28) / rowH);
            int maxScroll = Math.max(0, formIds.size() - maxVisible);
            if (scrollY > 0) {
                formScroll = Math.max(0, formScroll - 1);
            } else if (scrollY < 0) {
                formScroll = Math.min(maxScroll, formScroll + 1);
            }
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

    public DmzFormDocument document() {
        return document;
    }

    public String selectedForm() {
        return selectedForm;
    }

    public String race() {
        return race;
    }

    public String group() {
        return group;
    }

    /** Called when returning from {@link FormMakerPartsScreen}. */
    void onPartsClosed() {
        schedulePreview();
        saveGate.touch(System.currentTimeMillis());
        status = "Parts edits applied to document — save via FormEditorNetwork.";
        statusColor = MUTED;
        rebuild();
    }

    public static FormMakerScreen create(Screen parent) {
        return new FormMakerScreen(parent);
    }
}
