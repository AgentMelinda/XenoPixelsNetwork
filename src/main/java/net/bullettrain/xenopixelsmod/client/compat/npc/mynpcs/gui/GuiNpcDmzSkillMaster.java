package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewPanel;
import net.bullettrain.xenopixelsmod.client.compat.npc.gui.NpcPreviewOwner;

import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormAutobind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormDocument;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormEditorClientState;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormKind;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadata;
import net.bullettrain.xenopixelsmod.dmz.form.DmzFormMetadataRegistry;
import net.bullettrain.xenopixelsmod.dmz.form.DmzSkillMaster;
import net.bullettrain.xenopixelsmod.dmz.form.DmzTrainerMenu;
import net.bullettrain.xenopixelsmod.network.form.FormEditorNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.client.gui.mainmenu.GuiNpcAdvanced;
import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Role-edit screen for {@code RoleDmzSkillMaster}.
 *
 * <p>CustomNPCs' Advanced tab Edit button opens a native {@code GuiRole*} for every stock role
 * and does nothing for the sentinel, because that id matches none of the branches. This screen
 * is that missing editor: title, body, and which form groups this NPC teaches, written through
 * the existing form-editor channel.
 */
public final class GuiNpcDmzSkillMaster extends GuiNPCInterface2
        implements ITextfieldListener, NpcPreviewOwner {
    private static final int GROUP_PREV = 1;
    private static final int GROUP_NEXT = 2;
    private static final int BIND = 3;
    private static final int FORM_PREV = 4;
    private static final int FORM_NEXT = 5;
    private static final int FORM_TOGGLE = 6;
    private static final int SAVE = 7;
    private static final int BACK = 8;
    private static final int TITLE_FIELD = 200;
    private static final int BODY_FIELD = 201;

    private final NpcPreviewPanel previewPanel = new NpcPreviewPanel(npc, null);
    private final List<GroupRef> groups = new ArrayList<>();
    private int groupIndex;
    private int formIndex;
    private int seenResult = DmzFormEditorClientState.sequence();
    private String status = "";

    public GuiNpcDmzSkillMaster(EntityNPCInterface npc) {
        super(npc, 5);
        refreshGroups();
    }

    @Override
    public void init() {
        super.init();
        refreshGroups();
        previewPanel.profile(net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc));
        int x = guiLeft + 8;
        int y = guiTop + 6;
        addLabel(new GuiLabel(100, "DMZ Skill Master", x, y, 0xFFE2C078));
        addLabel(new GuiLabel(101, clip(((Entity) npc).getName().getString(), 28), x + 160, y, 0xFFFFD36A));
        y += 18;

        GroupRef current = currentGroup();
        addLabel(new GuiLabel(102, "Form group", x, y + 4, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, GROUP_PREV, x + 80, y, 18, 18, "<"));
        addLabel(new GuiLabel(103, current == null ? "No DMZ forms loaded" : clip(current.label(), 28),
                x + 102, y + 4, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, GROUP_NEXT, x + 300, y, 18, 18, ">"));
        y += 22;

        DmzFormMetadata.TrainerRef ref = current == null ? null : current.ref(npcUuid());
        boolean bound = ref != null && ref.skillMaster;
        addButton(new GuiButtonNop(this, BIND, x, y, 100, 18, bound ? "Unbind" : "Bind"));
        addLabel(new GuiLabel(104, bound
                ? "This NPC teaches the selected group"
                : "Bind to offer this group's forms", x + 108, y + 4, 0xFFAAAAAA));
        y += 24;

        addLabel(new GuiLabel(105, "Title", x, y + 4, 0xFFFFFFFF));
        GuiTextFieldNop title = new GuiTextFieldNop(TITLE_FIELD, this, x + 48, y, 320, 18, "");
        title.setMaxLength(DmzTrainerMenu.MAX_TITLE_LENGTH);
        title.setValue(ref == null ? "" : safe(ref.menuTitle));
        addTextField(title);
        y += 22;

        addLabel(new GuiLabel(106, "Body", x, y + 4, 0xFFFFFFFF));
        GuiTextFieldNop body = new GuiTextFieldNop(BODY_FIELD, this, x + 48, y, 320, 18, "");
        body.setMaxLength(DmzTrainerMenu.MAX_BODY_LENGTH);
        body.setValue(ref == null || ref.menuBody == null ? "" : safe(ref.menuBody.get("en_us")));
        addTextField(body);
        y += 22;

        List<String> forms = current == null ? List.of() : current.forms();
        String form = forms.isEmpty() ? "" : forms.get(Math.floorMod(formIndex, forms.size()));
        addLabel(new GuiLabel(107, "Offers", x, y + 4, 0xFFFFFFFF));
        addButton(new GuiButtonNop(this, FORM_PREV, x + 48, y, 18, 18, "<"));
        addLabel(new GuiLabel(108, form.isBlank() ? "no forms" : clip(form, 22),
                x + 70, y + 4, 0xFFFFD36A));
        addButton(new GuiButtonNop(this, FORM_NEXT, x + 250, y, 18, 18, ">"));
        addButton(new GuiButtonNop(this, FORM_TOGGLE, x + 274, y, 94, 18,
                ref != null && ref.offeredForms != null && ref.offeredForms.contains(form)
                        ? "Remove" : "Add"));
        y += 20;
        addLabel(new GuiLabel(109, ref == null || ref.offeredForms == null || ref.offeredForms.isEmpty()
                ? "Empty offer list = every form in the group"
                : "Offering " + ref.offeredForms.size() + " form(s)",
                x, y, 0xFFAAAAAA));

        addLabel(new GuiLabel(110, clip(status(), 40), x, guiTop + 176,
                statusColor()));
        addButton(new GuiButtonNop(this, SAVE, x + 220, guiTop + 176, 70, 18, "Save"));
        addButton(new GuiButtonNop(this, BACK, x + 296, guiTop + 176, 70, 18, "Back"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        GroupRef current = currentGroup();
        if (button.id == GROUP_PREV || button.id == GROUP_NEXT) {
            commitFields();
            groupIndex += button.id == GROUP_NEXT ? 1 : -1;
        } else if (button.id == BIND && current != null) {
            commitFields();
            toggleBind(current);
        } else if (button.id == FORM_PREV || button.id == FORM_NEXT) {
            formIndex += button.id == FORM_NEXT ? 1 : -1;
        } else if (button.id == FORM_TOGGLE && current != null) {
            commitFields();
            toggleForm(current);
        } else if (button.id == SAVE) {
            commitFields();
            saveCurrent();
        } else if (button.id == BACK) {
            Minecraft.getInstance().setScreen(new GuiNpcAdvanced(npc));
            return;
        }
        init();
    }

    @Override
    public void tick() {
        super.tick();
        int sequence = DmzFormEditorClientState.sequence();
        if (sequence == seenResult) return;
        seenResult = sequence;
        status = DmzFormEditorClientState.message();
        if (DmzFormEditorClientState.success() && currentGroup() != null) {
            currentGroup().document.revision(DmzFormEditorClientState.revision());
            currentGroup().document.markCreated();
        }
        init();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        commitFields();
    }

    @Override
    public void save() {
        commitFields();
        saveCurrent();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hasSubGui()) {
            previewPanel.render(graphics, getFontRenderer(), guiLeft, guiTop, partialTick);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && previewPanel.mouseClicked(guiLeft, guiTop, mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                double dragX, double dragY) {
        if (!hasSubGui() && previewPanel.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!hasSubGui() && previewPanel.mouseReleased(button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!hasSubGui() && previewPanel.mouseScrolled(guiLeft, guiTop, mouseX, mouseY, scrollY)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void refreshGroups() {
        groups.clear();
        String race = npc == null ? "human"
                : net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile.read(npc).raceId;
        if (race == null || race.isBlank()) race = "human";
        for (String group : NpcFormLookup.groups(race)) {
            addGroup(DmzFormKind.NORMAL, race, group);
        }
        for (String group : NpcFormLookup.stackGroups()) {
            addGroup(DmzFormKind.STACK, "", group);
        }
        if (groups.isEmpty()) {
            for (DmzFormMetadataRegistry.TrainerOffering offering : DmzFormMetadataRegistry.all()) {
                addGroup(offering.kind(), offering.metadata().race, offering.metadata().group);
            }
        }
        groupIndex = groups.isEmpty() ? 0 : Math.floorMod(groupIndex, groups.size());
    }

    private void addGroup(DmzFormKind kind, String race, String group) {
        List<String> forms = kind == DmzFormKind.STACK
                ? NpcFormLookup.stackForms(group) : NpcFormLookup.forms(race, group);
        if (forms.isEmpty()) return;
        DmzFormDocument document = DmzFormDocument.load(kind, race, group, forms.get(0));
        groups.add(new GroupRef(document, forms));
    }

    private GroupRef currentGroup() {
        return groups.isEmpty() ? null : groups.get(Math.floorMod(groupIndex, groups.size()));
    }

    private void toggleBind(GroupRef current) {
        UUID id = ((Entity) npc).getUUID();
        DmzFormMetadata.TrainerRef ref = DmzSkillMaster.trainerRef(current.document.metadata(), id);
        if (ref != null && ref.skillMaster) {
            ref.skillMaster = false;
            status = "Unbound from " + current.document.group();
        } else {
            DmzFormAutobind.ensureMaster(current.document.metadata(), id,
                    ((Entity) npc).getName().getString(),
                    ((Entity) npc).level().dimension().location().toString());
            status = "Bound to " + current.document.group();
        }
        saveCurrent();
    }

    private void toggleForm(GroupRef current) {
        DmzFormMetadata.TrainerRef ref = DmzFormAutobind.ensureMaster(current.document.metadata(),
                ((Entity) npc).getUUID(), ((Entity) npc).getName().getString(),
                ((Entity) npc).level().dimension().location().toString());
        if (ref == null || current.forms.isEmpty()) return;
        String form = current.forms.get(Math.floorMod(formIndex, current.forms.size()));
        if (ref.offeredForms == null) ref.offeredForms = new ArrayList<>();
        if (!ref.offeredForms.remove(form)) ref.offeredForms.add(form);
        saveCurrent();
    }

    private void commitFields() {
        GroupRef current = currentGroup();
        if (current == null) return;
        DmzFormMetadata.TrainerRef ref = DmzSkillMaster.trainerRef(
                current.document.metadata(), ((Entity) npc).getUUID());
        if (ref == null) return;
        GuiTextFieldNop title = getTextField(TITLE_FIELD);
        if (title != null) {
            ref.menuTitle = clip(safe(title.getValue()).trim(), DmzTrainerMenu.MAX_TITLE_LENGTH);
        }
        GuiTextFieldNop body = getTextField(BODY_FIELD);
        if (body != null) {
            if (ref.menuBody == null) ref.menuBody = new LinkedHashMap<>();
            String value = clip(safe(body.getValue()).trim(), DmzTrainerMenu.MAX_BODY_LENGTH);
            if (value.isEmpty()) ref.menuBody.remove("en_us");
            else ref.menuBody.put("en_us", value);
        }
    }

    private void saveCurrent() {
        GroupRef current = currentGroup();
        if (current == null || !current.document.created()) {
            status = current == null ? "No form group to save" : "Load a created form first";
            return;
        }
        if (DmzFormEditorClientState.inFlight()) return;
        DmzFormEditorClientState.begin();
        FormEditorNetwork.save(current.document.kind(), current.document.race(),
                current.document.group(), current.document.formJson(),
                current.document.metadataJson(), current.document.revision(),
                UUID.randomUUID().toString());
    }

    private String status() {
        if (!status.isBlank()) return status;
        String message = DmzFormEditorClientState.message();
        return message == null ? "" : message;
    }

    private int statusColor() {
        if (!DmzFormEditorClientState.message().isBlank() && !DmzFormEditorClientState.success()) {
            return 0xFFFF7777;
        }
        return 0xFFAAAAAA;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String clip(String value, int max) {
        if (value == null) return "";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private UUID npcUuid() {
        return ((Entity) npc).getUUID();
    }

    private record GroupRef(DmzFormDocument document, List<String> forms) {
        String label() {
            String kind = document.kind() == DmzFormKind.STACK ? "stack" : document.race();
            return kind + "/" + document.group();
        }

        DmzFormMetadata.TrainerRef ref(UUID trainerId) {
            return DmzSkillMaster.trainerRef(document.metadata(), trainerId);
        }
    }
}
