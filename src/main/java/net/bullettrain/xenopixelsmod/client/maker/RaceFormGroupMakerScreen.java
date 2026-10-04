package net.bullettrain.xenopixelsmod.client.maker;

import com.dragonminez.client.gui.character.util.ScaledScreen;
import net.bullettrain.xenopixelsmod.client.npc.DmzFormMakerScreen;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasButton;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasCycle;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasNotice;
import net.bullettrain.xenopixelsmod.client.ui.atlas.AtlasPanel;
import net.bullettrain.xenopixelsmod.client.ui.atlas.XenoAtlasSprites;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.RaceFormGroupGuard;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Green-atlas shell for race form-group making (PR-D6b / KD15).
 *
 * <p>Gates open with {@link RaceFormGroupGuard}. Full field editing is deferred to the existing
 * {@link DmzFormMakerScreen} / Form Studio path ({@link FormEditorNetwork#save}) rather than
 * rewriting {@code GuiNpcDmzFormEditor}. Live preview here is a <b>document summary</b>
 * (auraColor and related fields) refreshed through {@link PreviewDebounce} at ≤50 ms — not a
 * Gecko/DMZ stand-in renderer (no invented preview API).
 *
 * <p><b>Not verified in a running game.</b>
 */
public final class RaceFormGroupMakerScreen extends ScaledScreen {
    private static final String FRAME = "xeno_editor_panel";
    private static final String PREVIEW = "panel_wide";
    private static final String PRIMARY = "pill_button";
    private static final XenoAtlasSprites.Theme THEME = XenoAtlasSprites.Theme.GREEN;

    private static final int GOLD = 0xFFFFC14A;
    private static final int LIGHT = 0xFFE7EDF3;
    private static final int MUTED = 0xFF6E9680;
    private static final int WARN = 0xFFFF8A80;
    private static final int OK = 0xFF9AFFB0;

    private final Screen parent;
    private final PreviewDebounce previewDebounce = new PreviewDebounce();
    private final String sessionId = "race-form-group-maker-" + UUID.randomUUID();

    private String race;
    private String group;
    private String form = "";
    private String refuseMessage;
    private DmzFormDocument document;
    private List<String> previewLines = List.of();
    private String status = "Select a known race/group, then Open Form Editor.";
    private int statusColor = MUTED;

    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;
    private int previewX;
    private int previewY;
    private EditBox auraBox;
    private int seenResult = DmzFormEditorClientState.sequence();

    public RaceFormGroupMakerScreen(Screen parent) {
        this(parent, "saiyan", firstGroup("saiyan"), null);
    }

    /**
     * Opens the maker for {@code race}/{@code group}. Unknown race shows a refuse banner and
     * disables editor/save actions.
     */
    public RaceFormGroupMakerScreen(Screen parent, String race, String group) {
        this(parent, race, group, null);
    }

    private RaceFormGroupMakerScreen(Screen parent, String race, String group, String refuseOverride) {
        super(Component.literal("Race Form Group Maker"));
        this.parent = parent;
        this.race = race == null ? "" : race.trim().toLowerCase(Locale.ROOT);
        this.group = group == null ? "" : group.trim().toLowerCase(Locale.ROOT);
        RaceFormGroupGuard.Result raceResult = RaceFormGroupGuard.validateRace(this.race);
        if (!raceResult.ok()) {
            this.refuseMessage = refuseOverride != null ? refuseOverride : raceResult.message();
            this.status = this.refuseMessage;
            this.statusColor = WARN;
        } else {
            RaceFormGroupGuard.Result groupResult = RaceFormGroupGuard.validateGroup(this.race, this.group);
            if (!groupResult.ok() && !this.group.isEmpty()) {
                this.refuseMessage = groupResult.message();
                this.status = this.refuseMessage;
                this.statusColor = WARN;
            } else if (this.group.isEmpty()) {
                this.group = firstGroup(this.race);
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        int canvasW = getUiWidth();
        int canvasH = getUiHeight();
        int[] fitted = XenoAtlasSprites.fittedSize(FRAME, canvasW - 8, canvasH - 8);
        frameW = fitted[0];
        frameH = fitted[1];
        frameX = (canvasW - frameW) / 2;
        frameY = (canvasH - frameH) / 2;
        previewX = frameX + frameW - XenoAtlasSprites.get(PREVIEW).width() - 24;
        previewY = frameY + 78;

        clearWidgets();
        if (refused()) {
            addRenderableWidget(new AtlasButton(frameX + 24, frameY + frameH - 40,
                    Component.literal("Close"), PRIMARY, b -> onClose()));
            return;
        }

        List<String> races = new ArrayList<>(RaceFormGroupGuard.knownRaces());
        races.sort(String::compareTo);
        int raceIndex = Math.max(0, races.indexOf(race));
        if (races.isEmpty()) {
            races = List.of(race.isEmpty() ? "saiyan" : race);
            raceIndex = 0;
        }
        addRenderableWidget(new AtlasCycle(frameX + 24, frameY + 72,
                Component.literal("Race"), races, raceIndex, this::onRaceChanged));

        List<String> groups = groupsFor(race);
        int groupIndex = Math.max(0, groups.indexOf(group));
        if (groups.isEmpty()) {
            groups = List.of(group.isEmpty() ? "(none)" : group);
            groupIndex = 0;
        }
        addRenderableWidget(new AtlasCycle(frameX + 24, frameY + 102,
                Component.literal("Group"), groups, groupIndex, this::onGroupChanged));

        List<String> forms = formsFor(race, group);
        if (forms.isEmpty()) {
            forms = List.of("(new)");
        }
        if (form.isBlank() || !forms.contains(form)) {
            form = "(new)".equals(forms.get(0)) ? "" : forms.get(0);
        }
        int formIndex = Math.max(0, forms.indexOf(form.isBlank() ? forms.get(0) : form));
        addRenderableWidget(new AtlasCycle(frameX + 24, frameY + 132,
                Component.literal("Form"), forms, formIndex, this::onFormChanged));

        auraBox = new EditBox(font, frameX + 24, frameY + 168, 120, 18, Component.literal("auraColor"));
        auraBox.setMaxLength(9);
        auraBox.setResponder(this::onAuraEdited);
        addRenderableWidget(auraBox);

        addRenderableWidget(new AtlasButton(frameX + 24, frameY + frameH - 40,
                Component.literal("Open Editor"), PRIMARY, b -> openFormEditor()));
        addRenderableWidget(new AtlasButton(frameX + 24 + AtlasButton.nativeWidth(PRIMARY) + 8,
                frameY + frameH - 40, Component.literal("Save"), PRIMARY, b -> saveNow()));
        addRenderableWidget(new AtlasButton(frameX + 24 + (AtlasButton.nativeWidth(PRIMARY) + 8) * 2,
                frameY + frameH - 40, Component.literal("Close"), PRIMARY, b -> onClose()));

        reloadDocument();
        rebuildPreviewFromDocument();
        if (auraBox != null) {
            auraBox.setValue(fieldValue("auraColor"));
        }
    }

    private void onRaceChanged(String next) {
        race = next;
        group = firstGroup(race);
        form = "";
        refuseMessage = null;
        RaceFormGroupGuard.Result result = RaceFormGroupGuard.validateRace(race);
        if (!result.ok()) {
            refuseMessage = result.message();
            status = refuseMessage;
            statusColor = WARN;
            document = null;
            previewLines = List.of();
            rebuild();
            return;
        }
        status = "Race " + race;
        statusColor = MUTED;
        rebuild();
    }

    private void onGroupChanged(String next) {
        if ("(none)".equals(next)) {
            return;
        }
        group = next;
        form = "";
        RaceFormGroupGuard.Result result = RaceFormGroupGuard.validateGroup(race, group);
        if (!result.ok()) {
            refuseMessage = result.message();
            status = refuseMessage;
            statusColor = WARN;
            document = null;
            previewLines = List.of();
            rebuild();
            return;
        }
        refuseMessage = null;
        status = "Group " + group;
        statusColor = MUTED;
        rebuild();
    }

    private void onFormChanged(String next) {
        form = "(new)".equals(next) ? "" : next;
        schedulePreview();
        reloadDocument();
        if (auraBox != null) {
            auraBox.setValue(fieldValue("auraColor"));
        }
        rebuildPreviewFromDocument();
    }

    private void onAuraEdited(String raw) {
        if (document == null || refused()) {
            return;
        }
        document.update("auraColor", raw);
        schedulePreview();
    }

    private void schedulePreview() {
        previewDebounce.schedule(System.currentTimeMillis());
    }

    private void rebuild() {
        // Re-run layout without re-entering Screen.open plumbing.
        clearWidgets();
        init();
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (previewDebounce.shouldFire(now)) {
            previewDebounce.clear();
            rebuildPreviewFromDocument();
        }
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
    }

    /** Text summary from the loaded document — not a Gecko entity preview. */
    void rebuildPreviewFromDocument() {
        if (document == null) {
            previewLines = List.of("No document loaded",
                    refused() ? refuseMessage : "Pick a known race/group");
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(document.race() + " / " + document.group() + " / " + document.form());
        lines.add("auraColor: " + fieldValue("auraColor"));
        lines.add("extraAuraColor: " + fieldValue("extraAuraColor"));
        lines.add("hairColor: " + fieldValue("hairColor"));
        lines.add(document.created() ? "revision " + document.revision() : "new draft");
        lines.add("Preview: summary only (no Gecko stand-in)");
        previewLines = List.copyOf(lines);
    }

    private void reloadDocument() {
        if (refused()) {
            document = null;
            return;
        }
        RaceFormGroupGuard.Result raceOnly = RaceFormGroupGuard.validateRace(race);
        if (!raceOnly.ok()) {
            document = null;
            return;
        }
        if (form == null || form.isBlank()) {
            // New draft under a fresh custom_group — do not rename onto an owned installed group
            // (DmzFormProtection / identityError would reject that Create path).
            document = DmzFormDocument.create(DmzFormKind.NORMAL, race);
            return;
        }
        RaceFormGroupGuard.Result result = RaceFormGroupGuard.validateGroup(race, group);
        if (!result.ok()) {
            document = null;
            return;
        }
        document = DmzFormDocument.load(DmzFormKind.NORMAL, race, group, form);
    }

    private void openFormEditor() {
        if (refused()) {
            status = refuseMessage;
            statusColor = WARN;
            return;
        }
        RaceFormGroupGuard.Result raceResult = RaceFormGroupGuard.validateRace(race);
        if (!raceResult.ok()) {
            status = raceResult.message();
            statusColor = WARN;
            return;
        }
        if (form != null && !form.isBlank()) {
            RaceFormGroupGuard.Result result = RaceFormGroupGuard.validateGroup(race, group);
            if (!result.ok()) {
                status = result.message();
                statusColor = WARN;
                return;
            }
        }
        if (document == null) {
            reloadDocument();
        }
        if (document == null) {
            status = "Could not load form document.";
            statusColor = WARN;
            return;
        }
        if (minecraft != null) {
            minecraft.setScreen(new DmzFormMakerScreen(document, this));
        }
    }

    private void saveNow() {
        if (refused()) {
            status = refuseMessage;
            statusColor = WARN;
            return;
        }
        RaceFormGroupGuard.Result raceResult = RaceFormGroupGuard.validateRace(race);
        if (!raceResult.ok()) {
            status = raceResult.message();
            statusColor = WARN;
            return;
        }
        if (form != null && !form.isBlank()) {
            RaceFormGroupGuard.Result result = RaceFormGroupGuard.validateGroup(race, group);
            if (!result.ok()) {
                status = result.message();
                statusColor = WARN;
                return;
            }
        }
        if (document == null) {
            reloadDocument();
        }
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
        status = "Save sent on FormEditorNetwork path.";
        statusColor = GOLD;
    }

    private String fieldValue(String key) {
        if (document == null) {
            return "";
        }
        for (DmzFormDocument.Field field : document.fields()) {
            if (key.equals(field.key())) {
                return field.value() == null ? "" : field.value();
            }
        }
        return "";
    }

    private boolean refused() {
        return refuseMessage != null && !refuseMessage.isBlank();
    }

    private static String firstGroup(String race) {
        List<String> groups = groupsFor(race);
        return groups.isEmpty() ? "" : groups.get(0);
    }

    private static List<String> groupsFor(String race) {
        List<String> installed = new ArrayList<>(RaceFormGroupGuard.installedGroups(race));
        installed.sort(String::compareTo);
        if (!installed.isEmpty()) {
            return installed;
        }
        try {
            List<String> live = NpcFormLookup.groups(race);
            return live == null ? List.of() : live;
        } catch (Throwable ignored) {
            return List.of();
        }
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
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xCC030712);

        XenoAtlasSprites.Theme previous = XenoAtlasSprites.theme();
        XenoAtlasSprites.setTheme(THEME);
        try {
            beginUiScale(graphics);
            AtlasPanel.fittedInto(FRAME, frameX, frameY, getUiWidth() - 8, getUiHeight() - 8)
                    .render(graphics);

            graphics.drawString(font, "RACE FORM GROUP", frameX + 24, frameY + 18, OK, false);
            graphics.drawString(font, status, frameX + 24, frameY + 48, statusColor, false);

            if (refused()) {
                new AtlasNotice(refuseMessage, frameX + 24, frameY + 90).render(graphics, font);
                graphics.drawString(font, "Form-group maker targets existing races only (KD15).",
                        frameX + 24, frameY + 130, MUTED, false);
            } else {
                graphics.drawString(font, "Race", frameX + 24, frameY + 62, GOLD, false);
                graphics.drawString(font, "Group", frameX + 24, frameY + 92, GOLD, false);
                graphics.drawString(font, "Form", frameX + 24, frameY + 122, GOLD, false);
                graphics.drawString(font, "auraColor (preview)", frameX + 24, frameY + 156, GOLD, false);

                XenoAtlasSprites.blit(graphics, PREVIEW, THEME, previewX, previewY);
                graphics.drawString(font, "Live preview", previewX + 12, previewY + 10, GOLD, false);
                int y = previewY + 28;
                for (String line : previewLines) {
                    graphics.drawString(font, line, previewX + 12, y, LIGHT, false);
                    y += 12;
                }
            }

            super.render(graphics, (int) toUiX(mouseX), (int) toUiY(mouseY), partialTick);
            endUiScale(graphics);
        } finally {
            XenoAtlasSprites.setTheme(previous);
        }
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

    /** Test / command helper: open with guard; unknown race shows refuse UI. */
    public static RaceFormGroupMakerScreen create(Screen parent, String race, String group) {
        return new RaceFormGroupMakerScreen(parent, race, group);
    }
}
