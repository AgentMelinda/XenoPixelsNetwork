package net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui;

import com.dragonminez.common.config.FormConfig;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.client.gui.util.GuiNPCInterface2;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import espi.mynpcs.shared.client.gui.listeners.ITextfieldListener;

/** Selects and activates DMZ stack forms (Kaioken and config-added stacks) for an NPC. */
public final class GuiNpcDmzStack extends GuiNPCInterface2 implements ITextfieldListener {
    private static final int PREV_GROUP = 1;
    private static final int NEXT_GROUP = 2;
    private static final int PREV_FORM = 3;
    private static final int NEXT_FORM = 4;
    private static final int MASTERY = 5;
    private static final int APPLY_STACK = 6;
    private static final int REMOVE_STACK = 7;
    private static final int AURA_STYLE = 8;
    private static final int BACK = 9;

    private NpcCombatProfile draft;

    public GuiNpcDmzStack(EntityNPCInterface npc, NpcCombatProfile source) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.draft = copy(source);
        ensureSelection();
    }

    @Override
    public void init() {
        super.init();
        ensureSelection();
        int x = guiLeft + 18;
        int y = guiTop + 14;
        addLabel(new GuiLabel(100, "DMZ Stack Forms", x, y, 0xFFD36A));
        addLabel(new GuiLabel(101, "Includes Kaioken and every stack form loaded by DMZ.", x, y + 16, 0xAAAAAA));

        cycleRow("Group", PREV_GROUP, NEXT_GROUP, x, y + 42, draft.selectedStackGroup);
        cycleRow("Stack", PREV_FORM, NEXT_FORM, x, y + 66, draft.selectedStackId);

        FormConfig.FormData data = NpcFormLookup.stackForm(draft.selectedStackGroup, draft.selectedStackId);
        double max = NpcFormLookup.maxMastery(data);
        double current = draft.stackMasteries.getMastery(draft.selectedStackGroup, draft.selectedStackId);
        addLabel(new GuiLabel(102, "Mastery %", x, y + 95, 0xFFFFFF));
        GuiTextFieldNop mastery = new GuiTextFieldNop(MASTERY, this, x + 82, y + 90, 72, 18,
                String.format(java.util.Locale.ROOT, "%.1f", max <= 0.0 ? 100.0 : current * 100.0 / max));
        mastery.setFloatsOnly();
        addTextField(mastery);

        String active = draft.stackGroup == null || draft.stackGroup.isBlank()
                ? "None" : draft.stackGroup + " / " + draft.stackId;
        addLabel(new GuiLabel(103, "Active: " + clip(active, 42), x, y + 120, 0xFFE2C078));
        addButton(new GuiButtonNop(this, APPLY_STACK, x, y + 142, 88, 18, "Stack"));
        addButton(new GuiButtonNop(this, REMOVE_STACK, x + 94, y + 142, 88, 18, "Unstack"));
        addButton(new GuiButtonNop(this, AURA_STYLE, x + 190, y + 142, 104, 18, "Stack Aura..."));
        addButton(new GuiButtonNop(this, BACK, guiLeft + 350, guiTop + 174, 58, 18, "Back"));
    }

    private void cycleRow(String label, int previous, int next, int x, int y, String value) {
        addLabel(new GuiLabel(previous + 200, label, x, y + 4, 0xFFFFFF));
        addButton(new GuiButtonNop(this, previous, x + 82, y, 22, 18, "<"));
        addLabel(new GuiLabel(previous + 220, clip(value, 27), x + 116, y + 4, 0xFFD36A));
        addButton(new GuiButtonNop(this, next, x + 286, y, 22, 18, ">"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pullMastery();
        if (button.id == PREV_GROUP || button.id == NEXT_GROUP) {
            draft.selectedStackGroup = NpcFormLookup.step(NpcFormLookup.stackGroups(),
                    draft.selectedStackGroup, button.id == NEXT_GROUP ? 1 : -1);
            draft.selectedStackId = "";
            ensureSelection();
            save(NpcProfileSavePacket.Action.SAVE);
            init();
            return;
        }
        if (button.id == PREV_FORM || button.id == NEXT_FORM) {
            draft.selectedStackId = NpcFormLookup.step(
                    NpcFormLookup.stackForms(draft.selectedStackGroup), draft.selectedStackId,
                    button.id == NEXT_FORM ? 1 : -1);
            grantSelected();
            save(NpcProfileSavePacket.Action.SAVE);
            init();
            return;
        }
        if (button.id == APPLY_STACK) {
            grantSelected();
            save(NpcProfileSavePacket.Action.STACK);
        } else if (button.id == REMOVE_STACK) {
            save(NpcProfileSavePacket.Action.UNSTACK);
        } else if (button.id == AURA_STYLE) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzAuraEditor(npc, draft,
                    GuiNpcDmzAuraEditor.Target.STACK));
            return;
        } else if (button.id == BACK) {
            save(NpcProfileSavePacket.Action.SAVE);
            Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
            return;
        }
        init();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pullMastery();
        save(NpcProfileSavePacket.Action.SAVE);
    }

    private void ensureSelection() {
        java.util.List<String> groups = NpcFormLookup.stackGroups();
        if (!groups.isEmpty() && !groups.contains(draft.selectedStackGroup)) draft.selectedStackGroup = groups.get(0);
        java.util.List<String> forms = NpcFormLookup.stackForms(draft.selectedStackGroup);
        if (!forms.isEmpty() && !forms.contains(draft.selectedStackId)) draft.selectedStackId = forms.get(0);
        grantSelected();
    }

    private void grantSelected() {
        FormConfig.FormData data = NpcFormLookup.stackForm(draft.selectedStackGroup, draft.selectedStackId);
        if (data == null) return;
        double max = NpcFormLookup.maxMastery(data);
        if (draft.stackMasteries.getMastery(draft.selectedStackGroup, draft.selectedStackId) <= 0.0) {
            draft.stackMasteries.setMastery(draft.selectedStackGroup, draft.selectedStackId, max, max);
        }
    }

    private void pullMastery() {
        GuiTextFieldNop field = getTextField(MASTERY);
        FormConfig.FormData data = NpcFormLookup.stackForm(draft.selectedStackGroup, draft.selectedStackId);
        if (field == null || data == null) return;
        try {
            double percent = Double.parseDouble(field.getValue().trim());
            double max = NpcFormLookup.maxMastery(data);
            draft.stackMasteries.setMastery(draft.selectedStackGroup, draft.selectedStackId,
                    max * Math.max(0.0, percent) / 100.0, max);
        } catch (NumberFormatException ignored) {
        }
    }

    private void save(NpcProfileSavePacket.Action action) {
        draft.write(npc);
        ModNetwork.sendToServer(new NpcProfileSavePacket(((Entity) npc).getId(), draft.toTag(), action,
                draft.selectedStackGroup, draft.selectedStackId));
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= max ? value : value.substring(0, max);
    }

    private static NpcCombatProfile copy(NpcCombatProfile source) {
        return source == null ? new NpcCombatProfile() : NpcCombatProfile.fromTag(source.toTag());
    }

    @Override
    public void save() {
        pullMastery();
        save(NpcProfileSavePacket.Action.SAVE);
    }
}
