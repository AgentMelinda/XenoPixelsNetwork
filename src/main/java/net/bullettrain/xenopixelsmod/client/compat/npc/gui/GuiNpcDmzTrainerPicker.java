package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import com.dragonminez.common.config.ConfigManager;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCounterpartSync;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormSaveGate;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GuiNpcDmzTrainerPicker extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    private static final int MASTER_TOGGLE = 1;
    private static final int ANY_TOGGLE = 2;
    private static final int NATIVE_PREV = 3;
    private static final int NATIVE_NEXT = 4;
    private static final int NATIVE_ADD = 5;
    private static final int NPC_PREV = 6;
    private static final int NPC_NEXT = 7;
    private static final int NPC_ADD = 8;
    private static final int BACK = 9;
    private static final int MENU_PAGE = 10;
    private static final int SOURCES_PAGE = 11;
    private static final int SKILL_MASTER = 12;
    private static final int BODY_PREV = 13;
    private static final int BODY_NEXT = 14;
    private static final int BODY_SET = 15;
    private static final int OFFERED_PREV = 16;
    private static final int OFFERED_NEXT = 17;
    private static final int OFFERED_ADD = 18;
    private static final int TITLE_FIELD = 200;
    private static final int LOCALE_FIELD = 201;
    private static final int BODY_FIELD = 202;

    private final DmzFormDocument document;
    /** Shared with the owning editor so one editing session produces one config backup. */
    private final String sessionId;
    private List<String> nativeMasters = List.of();
    private List<LivingEntity> npcTrainers = List.of();
    private int nativeIndex;
    private int npcIndex;
    /** 0 = master sources, 1 = the per-trainer master menu. */
    private int page;
    private int bodyIndex;
    private int offeredIndex;
    private int seenResult = DmzFormEditorClientState.sequence();
    private final DmzFormSaveGate saveGate = new DmzFormSaveGate(GuiNpcDmzFormEditor.SAVE_DEBOUNCE_MS);

    public GuiNpcDmzTrainerPicker(EntityNPCInterface npc, DmzFormDocument document) {
        this(npc, document, UUID.randomUUID().toString());
    }

    public GuiNpcDmzTrainerPicker(EntityNPCInterface npc, DmzFormDocument document, String sessionId) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.document = document;
        this.sessionId = sessionId;
    }

    @Override
    public void init() {
        super.init();
        refreshLists();
        if (page == 1) {
            initMenuPage();
            return;
        }
        int x = guiLeft + 20;
        int y = guiTop + 18;
        addLabel(new GuiLabel(100, "Form Master Sources", x, y, 0xFFE2C078));
        addButton(new GuiButtonYesNo(this, MASTER_TOGGLE, x + 210, y - 5, 52, 18,
                document.metadata().masterLearningEnabled));
        addLabel(new GuiLabel(101, "Enabled", x + 266, y, 0xFFFFFFFF));
        addButton(new GuiButtonYesNo(this, ANY_TOGGLE, x + 320, y - 5, 52, 18,
                document.metadata().anyNativeMaster));
        addLabel(new GuiLabel(102, "Any", x + 375, y, 0xFFFFFFFF));

        y += 38;
        String nativeMaster = currentNative();
        addLabel(new GuiLabel(103, "Native DMZ Master", x, y, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, NATIVE_PREV, x, y + 18, 24, 18, "<"));
        addLabel(new GuiLabel(104, nativeMaster.isBlank() ? "None configured" : nativeMaster,
                x + 34, y + 22, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, NATIVE_NEXT, x + 260, y + 18, 24, 18, ">"));
        addButton(new GuiButtonNop(this, NATIVE_ADD, x + 292, y + 18, 80, 18,
                hasNative(nativeMaster) ? "Remove" : "Add"));

        y += 60;
        LivingEntity trainer = currentNpc();
        String trainerName = trainer == null ? "No loaded NPCs" : trainer.getName().getString();
        addLabel(new GuiLabel(105, "CustomNPC Trainer", x, y, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, NPC_PREV, x, y + 18, 24, 18, "<"));
        addLabel(new GuiLabel(106, trainerName, x + 34, y + 22, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, NPC_NEXT, x + 260, y + 18, 24, 18, ">"));
        addButton(new GuiButtonNop(this, NPC_ADD, x + 292, y + 18, 80, 18,
                hasNpc(trainer) ? "Remove" : "Add"));

        addLabel(new GuiLabel(107, "Selected native: " + document.metadata().nativeMasters.size()
                + "  CustomNPC: " + document.metadata().customNpcTrainers.size(),
                x, guiTop + 160, 0xFFAAAAAA));
        addButton(new GuiButtonNop(this, MENU_PAGE, x + 300, guiTop + 156, 80, 18, "Menu..."));
        addLabel(new GuiLabel(108, document.created()
                ? DmzFormEditorClientState.message()
                : "Create the form before assigning masters", x, guiTop + 176,
                document.created() && !DmzFormEditorClientState.success() ? 0xFFFF7777 : 0xFFAAAAAA));
        addButton(new GuiButtonNop(this, BACK, x + 292, guiTop + 176, 80, 18, "Back"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        if (button.id == MASTER_TOGGLE && button instanceof GuiButtonYesNo yes) {
            document.metadata().masterLearningEnabled = yes.getBoolean();
            saveMetadata();
        } else if (button.id == ANY_TOGGLE && button instanceof GuiButtonYesNo yes) {
            document.metadata().anyNativeMaster = yes.getBoolean();
            saveMetadata();
        } else if (button.id == NATIVE_PREV || button.id == NATIVE_NEXT) {
            nativeIndex = step(nativeIndex, nativeMasters.size(), button.id == NATIVE_NEXT ? 1 : -1);
        } else if (button.id == NATIVE_ADD) {
            toggleNative(currentNative());
            saveMetadata();
        } else if (button.id == NPC_PREV || button.id == NPC_NEXT) {
            npcIndex = step(npcIndex, npcTrainers.size(), button.id == NPC_NEXT ? 1 : -1);
        } else if (button.id == NPC_ADD) {
            toggleNpc(currentNpc());
            saveMetadata();
        } else if (button.id == MENU_PAGE) {
            commitMenuFields();
            page = 1;
            init();
            return;
        } else if (button.id == SOURCES_PAGE) {
            commitMenuFields();
            page = 0;
            init();
            return;
        } else if (button.id == SKILL_MASTER && button instanceof GuiButtonYesNo yes) {
            DmzFormMetadata.TrainerRef ref = ensureRef(currentNpc());
            if (ref != null) {
                ref.skillMaster = yes.getBoolean();
                if (yes.getBoolean()) document.metadata().masterLearningEnabled = true;
                saveMetadata();
                init();
            }
        } else if (button.id == BODY_PREV || button.id == BODY_NEXT) {
            bodyIndex += button.id == BODY_NEXT ? 1 : -1;
            init();
            return;
        } else if (button.id == BODY_SET) {
            commitBody(ensureRef(currentNpc()));
        } else if (button.id == OFFERED_PREV || button.id == OFFERED_NEXT) {
            offeredIndex += button.id == OFFERED_NEXT ? 1 : -1;
            init();
            return;
        } else if (button.id == OFFERED_ADD) {
            toggleOffered(ensureRef(currentNpc()));
        } else if (button.id == BACK) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzFormEditor(npc, document, sessionId));
            return;
        }
        init();
    }

    private void refreshLists() {
        List<String> masters = new ArrayList<>(ConfigManager.getSkillsConfig().getSkillOfferings().keySet());
        masters.removeIf(value -> value == null || value.isBlank() || "default".equalsIgnoreCase(value));
        masters.sort(String::compareTo);
        nativeMasters = masters;
        List<LivingEntity> trainers = new ArrayList<>();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            for (Entity entity : minecraft.level.entitiesForRendering()) {
                if (entity instanceof LivingEntity living && NpcCounterpartSync.isCustomNpc(living)) {
                    trainers.add(living);
                }
            }
        }
        trainers.sort(Comparator.comparing(entity -> entity.getName().getString()));
        npcTrainers = trainers;
        nativeIndex = step(nativeIndex, nativeMasters.size(), 0);
        npcIndex = step(npcIndex, npcTrainers.size(), 0);
    }

    private String currentNative() {
        return nativeMasters.isEmpty() ? "" : nativeMasters.get(nativeIndex);
    }

    private LivingEntity currentNpc() {
        return npcTrainers.isEmpty() ? null : npcTrainers.get(npcIndex);
    }

    private boolean hasNative(String value) {
        return value != null && document.metadata().nativeMasters.contains(value);
    }

    private boolean hasNpc(LivingEntity trainer) {
        if (trainer == null) return false;
        return document.metadata().customNpcTrainers.stream()
                .anyMatch(entry -> trainer.getUUID().toString().equalsIgnoreCase(entry.uuid));
    }

    private void toggleNative(String value) {
        if (value == null || value.isBlank()) return;
        if (!document.metadata().nativeMasters.remove(value)) document.metadata().nativeMasters.add(value);
    }

    private void toggleNpc(LivingEntity trainer) {
        if (trainer == null) return;
        String uuid = trainer.getUUID().toString();
        if (document.metadata().customNpcTrainers.removeIf(entry -> uuid.equalsIgnoreCase(entry.uuid))) return;
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = uuid;
        ref.name = trainer.getName().getString();
        ref.dimension = trainer.level().dimension().location().toString();
        document.metadata().customNpcTrainers.add(ref);
    }

    private void saveMetadata() {
        saveGate.touch(System.currentTimeMillis());
        if (!document.created() || DmzFormEditorClientState.inFlight()) return;
        flushMetadata();
    }

    private void flushMetadata() {
        if (!document.created() || DmzFormEditorClientState.inFlight()) return;
        saveGate.sent();
        DmzFormEditorClientState.begin();
        FormEditorNetwork.save(document.kind(), document.race(), document.group(),
                document.formJson(), document.metadataJson(), document.revision(), sessionId);
    }

    @Override
    public void tick() {
        super.tick();
        if (saveGate.shouldSend(System.currentTimeMillis(),
                DmzFormEditorClientState.inFlight(), document.created())) {
            flushMetadata();
        }
        int sequence = DmzFormEditorClientState.sequence();
        if (sequence == seenResult) return;
        seenResult = sequence;
        if (DmzFormEditorClientState.success()) document.revision(DmzFormEditorClientState.revision());
        init();
    }

    /** The per-trainer master menu: designation, heading, locale body and offered forms. */
    private void initMenuPage() {
        int x = guiLeft + 20;
        int y = guiTop + 18;
        LivingEntity trainer = currentNpc();
        DmzFormMetadata.TrainerRef ref = trainerRef(trainer);
        addLabel(new GuiLabel(100, "Trainer Menu", x, y, 0xFFE2C078));
        addLabel(new GuiLabel(101, trainer == null ? "No loaded NPCs"
                : clip(trainer.getName().getString(), 28), x + 110, y, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, SOURCES_PAGE, x + 300, y - 5, 80, 18, "Sources"));
        y += 28;

        addButton(new GuiButtonYesNo(this, SKILL_MASTER, x, y, 52, 18,
                ref != null && ref.skillMaster));
        addLabel(new GuiLabel(102, "Skill Master", x + 58, y + 4, 0xFFFFFFFF));
        addLabel(new GuiLabel(103, ref == null
                ? "Cycle to an NPC on the Sources page first"
                : (DmzSkillMaster.isSkillMaster(document.metadata(), trainer.getUUID())
                        ? "Active: the master menu opens on interact"
                        : "Inactive: master learning off, or no skill costs"),
                x, y + 22, 0xFFAAAAAA));
        y += 44;

        addLabel(new GuiLabel(104, "Title", x, y + 5, 0xFFFFFFFF));
        GuiTextFieldNop titleField = new GuiTextFieldNop(TITLE_FIELD, this, x + 72, y, 308, 18, "");
        titleField.setValue(ref == null ? "" : safe(ref.menuTitle));
        titleField.setMaxLength(DmzTrainerMenu.MAX_TITLE_LENGTH);
        addTextField(titleField);
        y += 26;

        List<String> locales = bodyLocales(ref);
        String locale = locales.isEmpty() ? "" : locales.get(Math.floorMod(bodyIndex, locales.size()));
        addLabel(new GuiLabel(105, "Body", x, y + 5, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, BODY_PREV, x + 72, y, 24, 18, "<"));
        addLabel(new GuiLabel(106, locale.isBlank() ? "none" : locale, x + 102, y + 5, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, BODY_NEXT, x + 182, y, 24, 18, ">"));
        addButton(new GuiButtonNop(this, BODY_SET, x + 210, y, 60, 18, "Set"));
        GuiTextFieldNop localeField = new GuiTextFieldNop(LOCALE_FIELD, this, x + 276, y, 104, 18, "");
        localeField.setValue(locale);
        localeField.setMaxLength(DmzTrainerMenu.MAX_LOCALE_LENGTH);
        addTextField(localeField);
        y += 22;
        GuiTextFieldNop bodyField = new GuiTextFieldNop(BODY_FIELD, this, x + 72, y, 308, 18, "");
        bodyField.setValue(ref == null || ref.menuBody == null ? ""
                : safe(ref.menuBody.getOrDefault(locale, "")));
        bodyField.setMaxLength(DmzTrainerMenu.MAX_BODY_LENGTH);
        addTextField(bodyField);
        addLabel(new GuiLabel(107, "Text", x, y + 5, 0xFFFFFFFF));
        y += 30;

        List<String> forms = offeredCandidates();
        String form = forms.isEmpty() ? "" : forms.get(Math.floorMod(offeredIndex, forms.size()));
        addLabel(new GuiLabel(108, "Offers", x, y + 5, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, OFFERED_PREV, x + 72, y, 24, 18, "<"));
        addLabel(new GuiLabel(109, form.isBlank() ? "no forms in group" : clip(form, 24),
                x + 102, y + 5, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, OFFERED_NEXT, x + 248, y, 24, 18, ">"));
        addButton(new GuiButtonNop(this, OFFERED_ADD, x + 276, y, 80, 18,
                ref != null && ref.offeredForms != null && ref.offeredForms.contains(form)
                        ? "Remove" : "Add"));
        y += 22;
        addLabel(new GuiLabel(110, ref == null ? ""
                : (ref.offeredForms == null || ref.offeredForms.isEmpty()
                        ? "No restriction: every form in the group is offered"
                        : "Offering " + ref.offeredForms.size() + " of "
                                + document.metadata().forms.size() + " forms"),
                x, y, 0xFFAAAAAA));
        addLabel(new GuiLabel(111, document.created()
                ? DmzFormEditorClientState.message()
                : "Create the form before editing the menu", x, guiTop + 184,
                document.created() && !DmzFormEditorClientState.success() ? 0xFFFF7777 : 0xFFAAAAAA));
        addButton(new GuiButtonNop(this, BACK, x + 300, guiTop + 184, 80, 18, "Back"));
    }

    /** The trainer record for {@code trainer}, or {@code null} when it is not designated yet. */
    private DmzFormMetadata.TrainerRef trainerRef(LivingEntity trainer) {
        if (trainer == null) return null;
        return DmzSkillMaster.trainerRef(document.metadata(), trainer.getUUID());
    }

    /** The trainer record for {@code trainer}, designating it first when it is not yet listed. */
    private DmzFormMetadata.TrainerRef ensureRef(LivingEntity trainer) {
        if (trainer == null) return null;
        DmzFormMetadata.TrainerRef existing = trainerRef(trainer);
        if (existing != null) return existing;
        DmzFormMetadata.TrainerRef ref = new DmzFormMetadata.TrainerRef();
        ref.uuid = trainer.getUUID().toString();
        ref.name = trainer.getName().getString();
        ref.dimension = trainer.level().dimension().location().toString();
        document.metadata().customNpcTrainers.add(ref);
        return ref;
    }

    /** Sorted locale keys of one trainer's body map, so cycling is deterministic. */
    private static List<String> bodyLocales(DmzFormMetadata.TrainerRef ref) {
        if (ref == null || ref.menuBody == null || ref.menuBody.isEmpty()) return List.of();
        List<String> locales = new ArrayList<>(ref.menuBody.keySet());
        locales.sort(String::compareTo);
        return locales;
    }

    /** Sorted form ids of the edited group; the offered-form cycler walks these. */
    private List<String> offeredCandidates() {
        List<String> forms = new ArrayList<>(document.metadata().forms.keySet());
        forms.removeIf(value -> value == null || value.isBlank());
        forms.sort(String::compareTo);
        return forms;
    }

    private void commitMenuFields() {
        DmzFormMetadata.TrainerRef ref = ensureRef(currentNpc());
        commitTitle(ref);
        commitBody(ref);
    }

    private void commitTitle(DmzFormMetadata.TrainerRef ref) {
        if (ref == null) return;
        GuiTextFieldNop field = getTextField(TITLE_FIELD);
        if (field == null) return;
        String value = clip(safe(field.getValue()).trim(), DmzTrainerMenu.MAX_TITLE_LENGTH);
        if (value.equals(safe(ref.menuTitle))) return;
        ref.menuTitle = value;
        saveMetadata();
    }

    private void commitBody(DmzFormMetadata.TrainerRef ref) {
        if (ref == null) return;
        GuiTextFieldNop localeField = getTextField(LOCALE_FIELD);
        GuiTextFieldNop bodyField = getTextField(BODY_FIELD);
        if (localeField == null || bodyField == null) return;
        String locale = normalizeLocale(localeField.getValue());
        if (locale.isEmpty()) return;
        String value = clip(safe(bodyField.getValue()).trim(), DmzTrainerMenu.MAX_BODY_LENGTH);
        if (ref.menuBody == null) ref.menuBody = new LinkedHashMap<>();
        if (value.isEmpty()) {
            if (ref.menuBody.remove(locale) == null) return;
        } else {
            if (value.equals(ref.menuBody.get(locale))) return;
            if (!ref.menuBody.containsKey(locale)
                    && ref.menuBody.size() >= DmzTrainerMenu.MAX_LOCALES) {
                return;
            }
            ref.menuBody.put(locale, value);
        }
        saveMetadata();
    }

    private void toggleOffered(DmzFormMetadata.TrainerRef ref) {
        if (ref == null) return;
        List<String> forms = offeredCandidates();
        if (forms.isEmpty()) return;
        String form = forms.get(Math.floorMod(offeredIndex, forms.size()));
        if (ref.offeredForms == null) ref.offeredForms = new ArrayList<>();
        if (!ref.offeredForms.remove(form)) ref.offeredForms.add(form);
        saveMetadata();
    }

    private static String normalizeLocale(String locale) {
        return clip(safe(locale).trim().toLowerCase(Locale.ROOT).replace('-', '_'),
                DmzTrainerMenu.MAX_LOCALE_LENGTH);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static int step(int current, int size, int direction) {
        return size <= 0 ? 0 : Math.floorMod(current + direction, size);
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        if (page != 1) return;
        if (field.id == TITLE_FIELD) {
            commitTitle(ensureRef(currentNpc()));
        } else if (field.id == BODY_FIELD) {
            commitBody(ensureRef(currentNpc()));
        }
    }

    @Override
    public void save() {
        saveMetadata();
    }
}
