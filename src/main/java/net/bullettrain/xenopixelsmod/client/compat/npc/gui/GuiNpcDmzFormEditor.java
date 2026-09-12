package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraResolver;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraStyle;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormAutobind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormPreviewOverrides;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormSaveGate;
import net.bullettrain.xenopixelsmod.dmz.form.DmzHairTypes;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.SubGuiColorSelector;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Paged editor over every serialised DragonMineZ 2.1.3 form setting plus the XenoPixels
 * metadata (names, icons, TP costs, masters) that travels beside it.
 *
 * <p>Saving is server-authoritative. A draft is not written at all until its identity fields
 * validate and the operator presses Create; after that, toggles and pickers save immediately
 * while typed fields save on focus loss or after {@link #SAVE_DEBOUNCE_MS}. The local revision
 * only ever advances to the value the server acknowledged, so a rejected save leaves the editor
 * able to retry rather than permanently out of step.
 */
public final class GuiNpcDmzFormEditor extends GuiNPCInterface2
        implements ITextfieldListener, NpcPreviewOwner {
    /** Quiet period after the last keystroke before a typed field is sent. */
    public static final long SAVE_DEBOUNCE_MS = 600L;

    private static final int PAGE_PREV = 1;
    private static final int PAGE_NEXT = 2;
    private static final int SAVE = 3;
    private static final int PREVIEW = 4;
    private static final int BACK = 5;
    private static final int MASTER = 6;
    private static final int ANY_MASTER = 7;
    private static final int BUY_MASTER = 8;
    private static final int TRAINERS = 9;
    private static final int AURA_ON = 10;
    private static final int AURA_COLOR = 11;
    private static final int AURA_SCALE = 12;
    private static final int AURA_DETAILS = 13;
    private static final int HAIR_STYLE = 14;
    private static final int EYES_NEXT = 15;
    private static final int BROWS_NEXT = 16;
    private static final int EYE_COLOR = 17;
    private static final int COPY_AS_NEW = 18;
    private static final int HAIR_TYPE_CYCLE = 19;
    private static final int NOSE_NEXT = 20;
    private static final int MOUTH_NEXT = 21;
    private static final int TATTOO_NEXT = 22;
    private static final int FIELD_BASE = 1000;
    private static final int BOOL_BASE = 2000;
    private static final int COLOR_BASE = 3000;
    /**
     * Field rows per page. Six, not seven: rows start at {@code guiTop + 24} on a 22px pitch, so a
     * seventh row would end at {@code guiTop + 174} and be drawn behind the button bar at
     * {@code guiTop + 164}.
     */
    private static final int ROWS = 6;
    /** Label column width; 142px fits the longest DragonMineZ field name without truncating. */
    private static final int LABEL_W = 142;
    private static final int FIELD_X = LABEL_W + 4;
    private static final int PREVIEW_GAP = 8;
    private static final int PREVIEW_W = 150;
    private static final int PREVIEW_H = 200;

    private final DmzFormDocument document;
    private final String sessionId;
    private final CompoundTag originalProfile;
    /** Local-only presentation knobs for the adjacent visualizer; never sent to either server. */
    private NpcCombatProfile previewProfile;
    private final List<DmzFormDocument.Field> visible = new ArrayList<>();
    private final DmzFormSaveGate saveGate = new DmzFormSaveGate(SAVE_DEBOUNCE_MS);
    private int page;
    private boolean previewing;
    private int seenResult = DmzFormEditorClientState.sequence();
    private int colorTarget = -1;
    private String localStatus = "";
    private float previewYaw = 180.0F;
    private float previewPitch;
    private float previewZoom = 1.0F;
    private boolean previewDragging;
    private boolean handingOff;

    public GuiNpcDmzFormEditor(EntityNPCInterface npc, DmzFormDocument document) {
        this(npc, document, UUID.randomUUID().toString());
    }

    /** Reopens the editor keeping one backup session across the trainer sub-screen. */
    public GuiNpcDmzFormEditor(EntityNPCInterface npc, DmzFormDocument document, String sessionId) {
        this(npc, document, sessionId, null, false);
    }

    GuiNpcDmzFormEditor(EntityNPCInterface npc, DmzFormDocument document, String sessionId,
                        NpcCombatProfile previewProfile, boolean previewing) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.document = document;
        this.sessionId = sessionId;
        this.originalProfile = NpcCombatProfile.read(npc).toTag();
        this.previewProfile = previewProfile == null
                ? NpcCombatProfile.fromTag(originalProfile.copy()) : previewProfile;
        if (previewProfile == null) this.previewProfile.auraOn = true;
        this.previewing = previewing;
        refreshPreview();
        if (previewProfile == null) DmzFormEditorClientState.reset();
    }

    public String sessionId() {
        return sessionId;
    }

    @Override
    public void init() {
        super.init();
        visible.clear();
        List<DmzFormDocument.Field> fields = document.fields();
        int pages = Math.max(1, (fields.size() + ROWS - 1) / ROWS);
        page = Math.max(0, Math.min(page, pages - 1));
        int x = guiLeft + 8;
        int y = guiTop + 4;
        addLabel(new GuiLabel(100, (document.created() ? "Edit " : "Create ")
                + (document.kind() == DmzFormKind.STACK ? "Stack" : "Normal") + " Form",
                x, y, 0xFFE2C078));
        addLabel(new GuiLabel(101, "Page " + (page + 1) + "/" + pages, x + 300, y, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, TRAINERS, x + 210, y - 5, 82, 18, "Trainers..."));
        y += 20;
        int start = page * ROWS;
        int end = Math.min(fields.size(), start + ROWS);
        for (int i = start; i < end; i++) {
            DmzFormDocument.Field field = fields.get(i);
            visible.add(field);
            int slot = visible.size() - 1;
            addLabel(new GuiLabel(200 + i, clip(field.label(), 23), x, y + 4, 0xFFFFFFFF));
            if (field.kind() == DmzFormDocument.Kind.BOOL) {
                addButton(new GuiButtonYesNo(this, BOOL_BASE + slot, x + FIELD_X, y, 56, 18,
                        Boolean.parseBoolean(field.value())));
            } else {
                boolean hairType = "hairType".equals(field.key());
                int boxWidth = field.kind() == DmzFormDocument.Kind.COLOR ? 196
                        : hairType ? 196 : 250;
                GuiTextFieldNop box = new GuiTextFieldNop(FIELD_BASE + slot, this, x + FIELD_X, y,
                        boxWidth, 18, "");
                box.setMaxLength(4096);
                box.setValue(field.value());
                addTextField(box);
                if (field.kind() == DmzFormDocument.Kind.COLOR) {
                    addButton(new GuiButtonNop(this, COLOR_BASE + slot, x + 346, y, 34, 18, "Pick"));
                } else if (hairType) {
                    addButton(new GuiButtonNop(this, HAIR_TYPE_CYCLE, x + 346, y, 52, 18,
                            hairTypeLabel(field.value())));
                }
            }
            y += 22;
        }
        addButton(new GuiButtonNop(this, PAGE_PREV, x, guiTop + 164, 48, 18, "< Page"));
        addButton(new GuiButtonNop(this, PAGE_NEXT, x + 52, guiTop + 164, 48, 18, "Page >"));
        addButton(new GuiButtonYesNo(this, MASTER, x + 106, guiTop + 164, 48, 18,
                document.metadata().masterLearningEnabled));
        addLabel(new GuiLabel(300, "Master", x + 157, guiTop + 168, 0xFFFFFFFF));
        addButton(new GuiButtonYesNo(this, ANY_MASTER, x + 202, guiTop + 164, 48, 18,
                document.metadata().anyNativeMaster));
        addLabel(new GuiLabel(301, "Any", x + 253, guiTop + 168, 0xFFFFFFFF));
        addButton(new GuiButtonYesNo(this, BUY_MASTER, x + 278, guiTop + 164, 48, 18,
                document.metadata().buyFromMaster));
        addLabel(new GuiLabel(302, "Buy", x + 329, guiTop + 168, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, SAVE, x, guiTop + 185, 82, 18,
                document.created() ? "Save Now" : "Create"));
        addButton(new GuiButtonNop(this, PREVIEW, x + 88, guiTop + 185, 82, 18,
                previewing ? "Stop Preview" : "Preview"));
        // Stay inside the 420px body. x+484 used to place Copy as New / Back on top of the
        // visualizer (and its "Drag rotate" hint), which is what the overlapping labels were.
        int barRight = guiLeft + 412;
        int backWidth = buttonWidth("Back");
        addButton(new GuiButtonNop(this, BACK, barRight - backWidth, guiTop + 185, backWidth, 18,
                "Back"));
        // Offered only once the source exists: a not-yet-created draft has nothing to duplicate.
        // The clone is unprotected because its group id is freshly generated.
        if (document.created()) {
            String copyLabel = "Copy as New";
            int copyWidth = buttonWidth(copyLabel);
            addButton(new GuiButtonNop(this, COPY_AS_NEW, barRight - backWidth - 4 - copyWidth,
                    guiTop + 185, copyWidth, 18, copyLabel));
        }
        addLabel(new GuiLabel(303, clip(status(), 18), x + 176, guiTop + 189, statusColor()));

        int previewLeft = previewLeft();
        NpcDmzAppearance face = previewAppearance();
        addButton(new GuiButtonYesNo(this, AURA_ON, previewLeft + 6, guiTop + 128, 44, 16,
                previewProfile.auraOn));
        addLabel(new GuiLabel(304, "Aura", previewLeft + 53, guiTop + 132, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, AURA_COLOR, previewLeft + 78, guiTop + 128, 50, 16,
                previewAuraColor()));
        addButton(new GuiButtonNop(this, EYE_COLOR, previewLeft + 130, guiTop + 128, 18, 16,
                "Eye"));
        addButton(new GuiButtonNop(this, AURA_SCALE, previewLeft + 6, guiTop + 144, 66, 16,
                "Scale " + formatScale(previewProfile.auraScale)));
        addButton(new GuiButtonNop(this, AURA_DETAILS, previewLeft + 76, guiTop + 144, 68, 16,
                "Style..."));
        addButton(new GuiButtonNop(this, HAIR_STYLE, previewLeft + 6, guiTop + 160, 46, 16,
                "H" + Math.max(0, previewProfile.hairStyleId)));
        addButton(new GuiButtonNop(this, EYES_NEXT, previewLeft + 54, guiTop + 160, 44, 16,
                "Eye " + face.eyesType));
        addButton(new GuiButtonNop(this, BROWS_NEXT, previewLeft + 100, guiTop + 160, 44, 16,
                "Brw " + face.eyebrowsType));
        addButton(new GuiButtonNop(this, NOSE_NEXT, previewLeft + 6, guiTop + 176, 44, 16,
                "Nse " + face.noseType));
        addButton(new GuiButtonNop(this, MOUTH_NEXT, previewLeft + 52, guiTop + 176, 46, 16,
                "Mth " + face.mouthType));
        addButton(new GuiButtonNop(this, TATTOO_NEXT, previewLeft + 100, guiTop + 176, 44, 16,
                "Tat " + face.tattooType));
    }

    private String status() {
        if (!localStatus.isBlank()) return localStatus;
        if (!document.created()) {
            String identity = document.identityError();
            return identity != null ? identity : "Ready to create";
        }
        String message = DmzFormEditorClientState.message();
        return message.isBlank() ? "" : message;
    }

    private int statusColor() {
        if (!localStatus.isBlank()) return 0xFFFF7777;
        if (!document.created()) {
            return document.identityError() != null ? 0xFFFFD36A : 0xFF55FF55;
        }
        return DmzFormEditorClientState.success() ? 0xFF55FF55 : 0xFFFF7777;
    }

    @Override
    public void tick() {
        super.tick();
        int sequence = DmzFormEditorClientState.sequence();
        if (sequence != seenResult) {
            seenResult = sequence;
            if (DmzFormEditorClientState.success()) {
                document.revision(DmzFormEditorClientState.revision());
                document.markCreated();
            }
            init();
        }
        if (saveGate.shouldSend(System.currentTimeMillis(),
                DmzFormEditorClientState.inFlight(), document.created())) {
            saveNow();
        }
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id >= COLOR_BASE && button.id < COLOR_BASE + ROWS) {
            pullVisible();
            openColorPicker(button.id - COLOR_BASE);
            return;
        }
        if (button.id >= BOOL_BASE && button.id < BOOL_BASE + ROWS
                && button instanceof GuiButtonYesNo toggle) {
            applyField(button.id - BOOL_BASE, Boolean.toString(toggle.getBoolean()));
            saveIfCreated();
            return;
        }
        pullVisible();
        if (button.id == AURA_ON && button instanceof GuiButtonYesNo yes) {
            previewProfile.auraOn = yes.getBoolean();
            refreshPreview();
        } else if (button.id == AURA_COLOR) {
            colorTarget = AURA_COLOR;
            setSubGui(new NpcColorPicker(NpcCombatProfile.parseHexColor(previewAuraColor())
                    .orElse(0xFFFFFF)));
        } else if (button.id == AURA_SCALE) {
            previewProfile.auraScale = nextAuraScale(previewProfile.auraScale);
            refreshPreview();
            init();
        } else if (button.id == HAIR_TYPE_CYCLE) {
            cycleHairType();
        } else if (button.id == HAIR_STYLE) {
            previewProfile.hairStyleId = NpcHairBridge.cycleStyle(previewProfile.hairStyleId, 1,
                    NpcHairBridge.presetCount());
            refreshPreview();
            init();
        } else if (button.id == EYES_NEXT) {
            previewAppearance().eyesType = Math.floorMod(previewAppearance().eyesType + 1,
                    maxEyesType() + 1);
            refreshPreview();
            init();
        } else if (button.id == BROWS_NEXT) {
            previewAppearance().eyebrowsType = NpcDmzAppearance.cycleEyebrowType(
                    previewAppearance().eyebrowsType, 1, maxEyesType());
            refreshPreview();
            init();
        } else if (button.id == NOSE_NEXT) {
            previewAppearance().noseType = Math.floorMod(previewAppearance().noseType + 1,
                    maxNoseType() + 1);
            refreshPreview();
            init();
        } else if (button.id == MOUTH_NEXT) {
            previewAppearance().mouthType = Math.floorMod(previewAppearance().mouthType + 1,
                    maxMouthType() + 1);
            refreshPreview();
            init();
        } else if (button.id == TATTOO_NEXT) {
            previewAppearance().tattooType = Math.floorMod(previewAppearance().tattooType + 1,
                    maxTattooType() + 1);
            refreshPreview();
            init();
        } else if (button.id == EYE_COLOR) {
            colorTarget = EYE_COLOR;
            setSubGui(new NpcColorPicker(NpcCombatProfile.parseHexColor(
                    previewAppearance().eye1Color).orElse(0xFFFFFF)));
        } else if (button.id == AURA_DETAILS) {
            handingOff = true;
            Minecraft.getInstance().setScreen(new GuiNpcDmzFormAuraPreview(npc, document,
                    sessionId, previewProfile, previewing));
        } else if (button.id == PAGE_PREV || button.id == PAGE_NEXT) {
            page += button.id == PAGE_NEXT ? 1 : -1;
            saveIfCreated();
            init();
        } else if (button.id == MASTER && button instanceof GuiButtonYesNo yes) {
            document.metadata().masterLearningEnabled = yes.getBoolean();
            saveIfCreated();
        } else if (button.id == ANY_MASTER && button instanceof GuiButtonYesNo yes) {
            document.metadata().anyNativeMaster = yes.getBoolean();
            saveIfCreated();
        } else if (button.id == BUY_MASTER && button instanceof GuiButtonYesNo yes) {
            document.metadata().buyFromMaster = yes.getBoolean();
            saveIfCreated();
        } else if (button.id == TRAINERS) {
            handingOff = true;
            Minecraft.getInstance().setScreen(new GuiNpcDmzTrainerPicker(npc, document, sessionId));
        } else if (button.id == SAVE) {
            saveNow();
            init();
        } else if (button.id == PREVIEW) {
            previewing = !previewing;
            if (previewing) applyPreview(); else restorePreview();
            init();
        } else if (button.id == BACK) {
            restorePreview();
            Minecraft.getInstance().setScreen(new GuiNpcDmzForms(npc));
        } else if (button.id == COPY_AS_NEW) {
            DmzFormDocument copy = document.copyAsNewDraft();
            restorePreview();
            handingOff = true;
            Minecraft.getInstance().setScreen(
                    new GuiNpcDmzFormEditor(npc, copy, UUID.randomUUID().toString()));
        }
    }

    private void openColorPicker(int slot) {
        if (slot < 0 || slot >= visible.size()) return;
        colorTarget = slot;
        GuiTextFieldNop field = getTextField(FIELD_BASE + slot);
        String value = field == null ? "" : field.getValue();
        setSubGui(new NpcColorPicker(NpcCombatProfile.parseHexColor(value).orElse(0xFFFFFF)));
    }

    @Override
    public void subGuiClosed(Screen subGui) {
        if (subGui instanceof SubGuiColorSelector selector && colorTarget >= 0) {
            String selected = NpcCombatProfile.formatHex(selector.color);
            if (colorTarget == AURA_COLOR) {
                NpcAuraStyle style = previewAuraStyle();
                style.enabled = true;
                style.primaryColor = selected;
                refreshPreview();
                colorTarget = -1;
                init();
            } else if (colorTarget == EYE_COLOR) {
                // Both iris layers move together so the previewed eye keeps one coherent colour.
                previewAppearance().eye1Color = selected;
                previewAppearance().eye2Color = selected;
                refreshPreview();
                colorTarget = -1;
                init();
            } else {
                GuiTextFieldNop field = getTextField(FIELD_BASE + colorTarget);
                if (field != null) field.setValue(selected);
                applyField(colorTarget, selected);
                colorTarget = -1;
                saveIfCreated();
                if (previewing) applyPreview();
            }
        }
        super.subGuiClosed(subGui);
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        updateField(field);
        saveIfCreated();
        if (previewing) applyPreview();
    }

    private void pullVisible() {
        for (int i = 0; i < visible.size(); i++) {
            GuiTextFieldNop field = getTextField(FIELD_BASE + i);
            if (field != null) updateField(field);
        }
    }

    private void updateField(GuiTextFieldNop field) {
        applyField(field.id - FIELD_BASE, field.getValue());
    }

    private void applyField(int index, String value) {
        if (index < 0 || index >= visible.size()) return;
        DmzFormDocument.Field field = visible.get(index);
        try {
            document.update(field.key(), value);
            localStatus = "";
            saveGate.touch(System.currentTimeMillis());
            refreshPreview();
        } catch (Exception exception) {
            localStatus = "Invalid " + field.label() + ": " + message(exception);
        }
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private void saveIfCreated() {
        saveGate.touch(System.currentTimeMillis());
        if (document.created()) saveNow();
    }

    private void saveNow() {
        if (!localStatus.isBlank()) return;
        String identity = document.identityError();
        if (identity != null) {
            localStatus = identity;
            return;
        }
        if (DmzFormEditorClientState.inFlight()) return;
        bindWandNpcAsMaster();
        saveGate.sent();
        DmzFormEditorClientState.begin();
        FormEditorNetwork.save(document.kind(), document.race(), document.group(),
                document.formJson(), document.metadataJson(), document.revision(), sessionId);
    }

    /**
     * When this form is created on an NPC that already has the skill-master role, list that NPC
     * as a trainer so the new form is offered from its master menu.
     */
    private void bindWandNpcAsMaster() {
        if (npc == null || npc.role == null || !DmzSkillMaster.isSentinel(npc.role.getType())) {
            return;
        }
        Entity entity = (Entity) npc;
        DmzFormAutobind.ensureMaster(document.metadata(), entity.getUUID(),
                entity.getName().getString(), entity.level().dimension().location().toString());
    }

    private void cycleHairType() {
        pullVisible();
        String current = "";
        for (DmzFormDocument.Field field : visible) {
            if ("hairType".equals(field.key())) {
                current = field.value();
                break;
            }
        }
        try {
            document.update("hairType", DmzHairTypes.cycle(current, 1));
            localStatus = "";
            saveIfCreated();
            refreshPreview();
            init();
        } catch (Exception exception) {
            localStatus = "Invalid Hair Type: " + message(exception);
        }
    }

    private static String hairTypeLabel(String value) {
        String current = value == null || value.isBlank() ? "base" : value;
        return current.length() <= 6 ? current : current.substring(0, 6);
    }

    private void applyPreview() {
        DmzFormPreviewOverrides.clear(document.kind());
        DmzFormPreviewOverrides.set(document.kind(), document.race(), document.group(),
                document.form(), document.previewData());
        applyFormSelection(previewProfile);
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), previewProfile);
    }

    private void refreshPreview() {
        DmzFormPreviewOverrides.clear(document.kind());
        DmzFormPreviewOverrides.set(document.kind(), document.race(), document.group(),
                document.form(), document.previewData());
        applyFormSelection(previewProfile);
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), previewProfile);
    }

    private void applyFormSelection(NpcCombatProfile profile) {
        if (document.kind() == DmzFormKind.STACK) {
            profile.stackGroup = document.group();
            profile.stackId = document.form();
            profile.selectedStackGroup = document.group();
            profile.selectedStackId = document.form();
        } else {
            profile.raceId = document.race();
            profile.formGroup = document.group();
            profile.formId = document.form();
            profile.selectedFormGroup = document.group();
            profile.selectedFormId = document.form();
            NpcFormLookup.grantMastery(profile, document.group(), document.form());
        }
    }

    private void restorePreview() {
        previewing = false;
        DmzFormPreviewOverrides.clear(document.kind());
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(),
                NpcCombatProfile.fromTag(originalProfile.copy()));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // Native 1.21 Screen.renderBackground blurs the framebuffer. Anything painted before
        // super.render() (the old translucent fill) stayed blurred while widgets drawn inside
        // super.render() stayed crisp. Paint the visualizer after that pass, opaque, then put
        // the preview controls back on top of the fill.
        renderVisualizerBackground(graphics);
        renderVisualizerText(graphics);
        renderVisualizerModel(graphics, partialTick);
        renderVisualizerControls(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < visible.size(); i++) {
            if (visible.get(i).kind() != DmzFormDocument.Kind.COLOR) continue;
            GuiTextFieldNop field = getTextField(FIELD_BASE + i);
            if (field == null) continue;
            // Live swatch to the right of the Pick button, inside the 420px panel.
            int swatch = NpcCombatProfile.parseHexColor(field.getValue()).orElse(-1);
            int left = guiLeft + 8 + 384;
            int top = field.getY() + 3;
            graphics.fill(left - 1, top - 1, left + 13, top + 13, 0xFF101418);
            if (swatch >= 0) graphics.fill(left, top, left + 12, top + 12, 0xFF000000 | swatch);
        }
    }

    private void renderVisualizerBackground(GuiGraphics graphics) {
        if (hasSubGui()) return;
        int left = previewLeft();
        int top = guiTop;
        int right = left + PREVIEW_W;
        int bottom = top + PREVIEW_H;
        graphics.fill(left, top, right, bottom, 0xFF101218);
        graphics.fill(left, top, right, top + 1, 0xFFB98235);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFB98235);
        graphics.fill(left, top, left + 1, bottom, 0xFFB98235);
        graphics.fill(right - 1, top, right, bottom, 0xFFB98235);
    }

    /** Preview-column widgets were already drawn in super.render(); the opaque fill covers them. */
    private void renderVisualizerControls(GuiGraphics graphics, int mouseX, int mouseY,
                                          float partialTick) {
        if (hasSubGui()) return;
        int[] ids = {AURA_ON, AURA_COLOR, EYE_COLOR, AURA_SCALE, AURA_DETAILS,
                HAIR_STYLE, EYES_NEXT, BROWS_NEXT, NOSE_NEXT, MOUTH_NEXT, TATTOO_NEXT};
        for (int id : ids) {
            GuiButtonNop button = getButton(id);
            if (button != null) button.render(graphics, mouseX, mouseY, partialTick);
        }
        GuiLabel aura = getLabel(304);
        if (aura != null) aura.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderVisualizerText(GuiGraphics graphics) {
        if (hasSubGui()) return;
        int left = previewLeft();
        int top = guiTop;
        graphics.drawCenteredString(getFontRenderer(), "Live Form",
                left + PREVIEW_W / 2, top + 5, 0xFFE2C078);
        NpcAuraResolver.Resolved aura = NpcAuraResolver.resolve(previewProfile);
        String style = aura.layers().isEmpty() ? "Aura: inherited"
                : "Aura: " + aura.layers().get(aura.layers().size() - 1).type()
                + "  L" + aura.layers().get(aura.layers().size() - 1).index();
        graphics.drawCenteredString(getFontRenderer(), clip(style, 24),
                left + PREVIEW_W / 2, top + 116, 0xFFAAAAAA);
    }

    /** Sizes a button to its label so text never truncates as localisation grows. */
    private int buttonWidth(String label) {
        return Math.max(40, getFontRenderer().width(label) + 16);
    }

    private void renderVisualizerModel(GuiGraphics graphics, float partialTick) {
        if (hasSubGui()) return;
        int left = previewLeft();
        int top = guiTop;
        int right = left + PREVIEW_W;
        int modelScale = Math.max(20, Math.round(54.0F * previewZoom));
        graphics.enableScissor(left + 2, top + 15, right - 2, top + 114);
        try {
            NpcFullDmzRenderer.renderPreview(npc, graphics, left + PREVIEW_W / 2,
                    top + 118, modelScale, previewYaw, previewPitch, partialTick,
                    previewProfile.auraOn);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Widgets get the click first. The preview panel rect covers the preview control rows, so
        // claiming the click before super() swallowed every button drawn inside those bounds.
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0 && insidePreview(mouseX, mouseY)) {
            previewDragging = true;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (button == 0 && previewDragging) {
            previewYaw += (float) dragX * 1.5F;
            previewPitch = Mth.clamp(previewPitch - (float) dragY, -30.0F, 30.0F);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && previewDragging) {
            previewDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (insidePreview(mouseX, mouseY)) {
            previewZoom = Mth.clamp(previewZoom + (float) scrollY * 0.08F, 0.65F, 1.75F);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * Left edge of the preview column, pulled back inside the viewport when the 420px body leaves
     * no room beside it. At the default GUI scale a centred body runs to the screen edge, so an
     * unclamped column drew the visualizer and its hair/eye buttons entirely off-screen.
     */
    private int previewLeft() {
        int desired = guiLeft + 420 + PREVIEW_GAP;
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) return desired;
        int screenWidth = minecraft.getWindow().getGuiScaledWidth();
        return Math.max(2, Math.min(desired, screenWidth - PREVIEW_W - 2));
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        int left = previewLeft();
        return mouseX >= left && mouseX < left + PREVIEW_W
                && mouseY >= guiTop && mouseY < guiTop + PREVIEW_H;
    }

    private NpcDmzAppearance previewAppearance() {
        if (previewProfile.appearance == null) previewProfile.appearance = new NpcDmzAppearance();
        return previewProfile.appearance;
    }

    /** Preview-only eye range for the race this draft belongs to. */
    private int maxEyesType() {
        return Math.max(0, TextureCounter.getMaxEyesTypes(effectiveModelBase()));
    }

    private int maxNoseType() {
        return Math.max(0, TextureCounter.getMaxNoseTypes(effectiveModelBase()));
    }

    private int maxMouthType() {
        return Math.max(0, TextureCounter.getMaxMouthTypes(effectiveModelBase()));
    }

    private int maxTattooType() {
        return Math.max(0, TextureCounter.getMaxTattooTypes(effectiveModelBase()));
    }

    /**
     * Same resolution the DMZ appearance tab uses: a race with a custom model reads that model's
     * textures, every other race reads its own id.
     */
    private String effectiveModelBase() {
        String race = document.kind() == DmzFormKind.STACK ? "" : document.race();
        if (race == null || race.isBlank()) {
            race = previewProfile.raceId == null ? "" : previewProfile.raceId;
        }
        race = race.trim().toLowerCase(Locale.ROOT);
        if (race.isBlank()) race = "human";
        RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
        if (config != null && Boolean.TRUE.equals(config.hasCustomModel())
                && config.getCustomModel() != null && !config.getCustomModel().isBlank()) {
            return config.getCustomModel().toLowerCase(Locale.ROOT);
        }
        return race;
    }

    private String previewAuraColor() {
        String override = previewAuraStyle().primaryColor;
        if (override != null && !override.isBlank()) return override;
        return NpcCombatProfile.formatHex(NpcAuraResolver.resolve(previewProfile).particleRgb());
    }

    private NpcAuraStyle previewAuraStyle() {
        return previewProfile.auraStyle(document.kind() == DmzFormKind.STACK,
                document.group(), document.form(), true);
    }

    private static float nextAuraScale(float scale) {
        float next = (float) (Math.floor(NpcCombatProfile.clampAuraScale(scale) * 4.0F + 0.01F)
                + 1.0F) / 4.0F;
        return next > 3.0F ? 0.5F : NpcCombatProfile.clampAuraScale(next);
    }

    private static String formatScale(float scale) {
        return String.format(java.util.Locale.ROOT, "%.2f", scale);
    }

    @Override
    public void removed() {
        if (!handingOff) restorePreview();
        super.removed();
    }

    @Override
    public void save() {
        pullVisible();
        saveIfCreated();
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }
}
