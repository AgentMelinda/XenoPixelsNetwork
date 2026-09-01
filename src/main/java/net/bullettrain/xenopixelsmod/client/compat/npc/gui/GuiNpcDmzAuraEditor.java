package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcAuraStyle;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

/** Per-NPC base, normal-form, and stack-form aura layer editor. */
public final class GuiNpcDmzAuraEditor extends GuiNPCInterface2 implements ITextfieldListener {
    public enum Target { BASE, FORM, STACK;
        Target next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private static final int TARGET = 1;
    private static final int OVERRIDE = 2;
    private static final int PRIMARY_COLOR = 10;
    private static final int PRIMARY_TYPE = 11;
    private static final int PRIMARY_LAYER = 12;
    private static final int EXTRA_CONFIGURED = 20;
    private static final int EXTRA_ENABLED = 21;
    private static final int EXTRA_COLOR = 22;
    private static final int EXTRA_TYPE = 23;
    private static final int EXTRA_LAYER = 24;
    private static final int LIGHTNING_CONFIGURED = 30;
    private static final int LIGHTNING_ENABLED = 31;
    private static final int LIGHTNING_COLOR = 32;
    private static final int APPLY = 40;
    private static final int CANCEL = 41;

    private final NpcCombatProfile original;
    private NpcCombatProfile draft;
    private Target target;
    private final boolean returnToStack;
    private final NpcAuraStyle unavailableStyle = new NpcAuraStyle();

    public GuiNpcDmzAuraEditor(EntityNPCInterface npc, NpcCombatProfile source, Target target) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.original = copy(source);
        this.draft = copy(source);
        this.target = target == null ? Target.BASE : target;
        this.returnToStack = this.target == Target.STACK;
        ensureSelections();
    }

    @Override
    public void init() {
        super.init();
        ensureSelections();
        NpcAuraStyle style = style(true);
        int x = guiLeft + 10;
        int y = guiTop + 7;
        addLabel(new GuiLabel(100, "Aura Details", x, y + 4, 0xFFD36A));
        addButton(new GuiButtonNop(this, TARGET, x + 82, y, 88, 18, target.name()));
        addButton(new GuiButtonYesNo(this, OVERRIDE, x + 176, y, 50, 18, style.enabled));
        addLabel(new GuiLabel(101, "Override", x + 230, y + 4, 0xFFFFFF));
        addLabel(new GuiLabel(102, targetName(), x + 294, y + 4, 0xFFE2C078));

        int left = x;
        int right = x + 205;
        addLabel(new GuiLabel(110, "Primary aura", left, y + 28, 0xFFD36A));
        field(PRIMARY_COLOR, "Color", left, y + 44, style.primaryColor);
        field(PRIMARY_TYPE, "Type", left, y + 64, style.primaryType);
        field(PRIMARY_LAYER, "Layer (-1 inherit)", left, y + 84, Integer.toString(style.primaryLayer));

        addLabel(new GuiLabel(120, "Secondary / extra aura", right, y + 28, 0xFFD36A));
        yesNo(EXTRA_CONFIGURED, "Custom", right, y + 44, style.extraConfigured);
        yesNo(EXTRA_ENABLED, "Visible", right + 100, y + 44, style.extraEnabled);
        field(EXTRA_COLOR, "Color", right, y + 64, style.extraColor);
        field(EXTRA_TYPE, "Type", right, y + 84, style.extraType);
        field(EXTRA_LAYER, "Layer", right, y + 104, Integer.toString(style.extraLayer));

        addLabel(new GuiLabel(130, "Lightning", left, y + 110, 0xFFD36A));
        yesNo(LIGHTNING_CONFIGURED, "Custom", left, y + 126, style.lightningConfigured);
        yesNo(LIGHTNING_ENABLED, "Visible", left + 100, y + 126, style.lightningEnabled);
        field(LIGHTNING_COLOR, "Color", left, y + 146, style.lightningColor);

        addButton(new GuiButtonNop(this, APPLY, guiLeft + 300, guiTop + 174, 52, 18, "Apply"));
        addButton(new GuiButtonNop(this, CANCEL, guiLeft + 356, guiTop + 174, 56, 18, "Cancel"));
    }

    private void field(int id, String label, int x, int y, String value) {
        addLabel(new GuiLabel(id + 200, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop field = new GuiTextFieldNop(id, this, x + 88, y, 100, 16,
                value == null ? "" : value);
        field.setMaxLength(64);
        addTextField(field);
    }

    private void yesNo(int id, String label, int x, int y, boolean value) {
        addButton(new GuiButtonYesNo(this, id, x, y, 48, 16, value));
        addLabel(new GuiLabel(id + 300, label, x + 52, y + 3, 0xFFFFFF));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pull();
        if (button.id == TARGET) {
            target = target.next();
            ensureSelections();
            init();
            return;
        }
        NpcAuraStyle style = style(true);
        if (button.id == OVERRIDE && button instanceof GuiButtonYesNo yes) style.enabled = yes.getBoolean();
        else if (button.id == EXTRA_CONFIGURED && button instanceof GuiButtonYesNo yes) style.extraConfigured = yes.getBoolean();
        else if (button.id == EXTRA_ENABLED && button instanceof GuiButtonYesNo yes) style.extraEnabled = yes.getBoolean();
        else if (button.id == LIGHTNING_CONFIGURED && button instanceof GuiButtonYesNo yes) style.lightningConfigured = yes.getBoolean();
        else if (button.id == LIGHTNING_ENABLED && button instanceof GuiButtonYesNo yes) style.lightningEnabled = yes.getBoolean();
        else if (button.id == APPLY) {
            persist(draft);
            goBack(draft);
            return;
        } else if (button.id == CANCEL) {
            NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), original);
            goBack(original);
            return;
        }
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), draft);
        init();
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), draft);
    }

    private void pull() {
        NpcAuraStyle style = style(true);
        style.primaryColor = colorText(PRIMARY_COLOR, style.primaryColor);
        style.primaryType = text(PRIMARY_TYPE, style.primaryType);
        style.primaryLayer = NpcAuraStyle.clampLayer(integer(PRIMARY_LAYER, style.primaryLayer));
        style.extraColor = colorText(EXTRA_COLOR, style.extraColor);
        style.extraType = text(EXTRA_TYPE, style.extraType);
        style.extraLayer = NpcAuraStyle.clampLayer(integer(EXTRA_LAYER, style.extraLayer));
        style.lightningColor = colorText(LIGHTNING_COLOR, style.lightningColor);
        GuiButtonNop override = getButton(OVERRIDE);
        if (override instanceof GuiButtonYesNo yes) style.enabled = yes.getBoolean();
        GuiButtonNop configured = getButton(EXTRA_CONFIGURED);
        if (configured instanceof GuiButtonYesNo yes) style.extraConfigured = yes.getBoolean();
        GuiButtonNop extra = getButton(EXTRA_ENABLED);
        if (extra instanceof GuiButtonYesNo yes) style.extraEnabled = yes.getBoolean();
        GuiButtonNop lightningConfigured = getButton(LIGHTNING_CONFIGURED);
        if (lightningConfigured instanceof GuiButtonYesNo yes) style.lightningConfigured = yes.getBoolean();
        GuiButtonNop lightning = getButton(LIGHTNING_ENABLED);
        if (lightning instanceof GuiButtonYesNo yes) style.lightningEnabled = yes.getBoolean();
    }

    private NpcAuraStyle style(boolean create) {
        if (target == Target.BASE) return draft.baseAuraStyle;
        NpcAuraStyle found = draft.auraStyle(target == Target.STACK,
                target == Target.STACK ? draft.selectedStackGroup : draft.selectedFormGroup,
                target == Target.STACK ? draft.selectedStackId : draft.selectedFormId, create);
        return found == null ? unavailableStyle : found;
    }

    private void ensureSelections() {
        java.util.List<String> groups = NpcFormLookup.groups(draft.raceId);
        if (!groups.isEmpty() && !groups.contains(draft.selectedFormGroup)) draft.selectedFormGroup = groups.get(0);
        java.util.List<String> forms = NpcFormLookup.forms(draft.raceId, draft.selectedFormGroup);
        if (!forms.isEmpty() && !forms.contains(draft.selectedFormId)) draft.selectedFormId = forms.get(0);
        java.util.List<String> stackGroups = NpcFormLookup.stackGroups();
        if (!stackGroups.isEmpty() && !stackGroups.contains(draft.selectedStackGroup)) draft.selectedStackGroup = stackGroups.get(0);
        java.util.List<String> stackForms = NpcFormLookup.stackForms(draft.selectedStackGroup);
        if (!stackForms.isEmpty() && !stackForms.contains(draft.selectedStackId)) draft.selectedStackId = stackForms.get(0);
    }

    private String targetName() {
        return switch (target) {
            case BASE -> "Base aura";
            case FORM -> clip(draft.selectedFormGroup + "/" + draft.selectedFormId, 19);
            case STACK -> clip(draft.selectedStackGroup + "/" + draft.selectedStackId, 19);
        };
    }

    private void persist(NpcCombatProfile profile) {
        profile.write(npc);
        ModNetwork.sendToServer(new NpcProfileSavePacket(((Entity) npc).getId(), profile.toTag(),
                NpcProfileSavePacket.Action.SAVE, profile.selectedFormGroup, profile.selectedFormId));
    }

    private void goBack(NpcCombatProfile profile) {
        Minecraft.getInstance().setScreen(returnToStack
                ? new GuiNpcDmzStack(npc, profile)
                : new GuiNpcDmzAppearance(npc, profile));
    }

    private String text(int id, String fallback) {
        GuiTextFieldNop field = getTextField(id);
        return field == null || field.getValue() == null ? (fallback == null ? "" : fallback)
                : field.getValue().trim();
    }

    private int integer(int id, int fallback) {
        try { return Integer.parseInt(text(id, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String colorText(int id, String fallback) {
        String raw = text(id, fallback);
        if (raw.isBlank()) return "";
        return NpcCombatProfile.parseHexColor(raw).isPresent()
                ? NpcCombatProfile.formatHex(NpcCombatProfile.parseHexColor(raw).getAsInt())
                : fallback;
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
        // Apply/Cancel own this editor transaction.
    }
}
