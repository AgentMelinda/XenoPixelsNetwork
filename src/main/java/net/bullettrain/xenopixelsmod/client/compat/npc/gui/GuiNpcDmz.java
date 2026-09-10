package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import com.dragonminez.common.config.FormConfig;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcFormLookup;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.SubGuiColorSelector;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * CustomNPCs wand editor tab for {@link NpcCombatProfile}. Uses the same
 * {@link GuiNPCInterface2} shell as Display/Stats/AI. Does not invent a CNPC {@code EnumGuiType}.
 */
public final class GuiNpcDmz extends GuiNPCInterface2 implements ITextfieldListener {
    private static final int ID_RACE = 1;
    private static final int ID_STR = 2;
    private static final int ID_SKP = 3;
    private static final int ID_DEF = 4;
    private static final int ID_VIT = 5;
    private static final int ID_PWR = 6;
    private static final int ID_ENE = 7;
    private static final int ID_GROUP = 8;
    private static final int ID_FORM = 9;
    private static final int ID_AURA_COLOR = 10;
    private static final int ID_AURA_SCALE = 11;
    private static final int ID_CHARGE = 12;
    private static final int ID_MASTERY = 13;
    private static final int ID_TECH = 14;
    private static final int ID_KI_COLOR = 15;
    private static final int ID_HAIR_CODE = 16;
    private static final int ID_HAIR_COLOR = 17;
    private static final int ID_AURA = 20;
    private static final int ID_HAIR = 21;
    private static final int ID_TRANSFORM = 22;
    private static final int ID_DESCEND = 23;
    private static final int ID_CUSTOMIZE = 24;
    private static final int ID_STACKS = 25;
    private static final int ID_STACK_APPLY = 26;
    private static final int ID_KNOCKABLE = 27;
    private static final int ID_PUNCHABLE = 28;
    private static final int ID_GROUP_PREV = 30;
    private static final int ID_GROUP_NEXT = 31;
    private static final int ID_FORM_PREV = 32;
    private static final int ID_FORM_NEXT = 33;
    private static final int ID_STACK_GROUP_PREV = 34;
    private static final int ID_STACK_GROUP_NEXT = 35;
    private static final int ID_STACK_PREV = 36;
    private static final int ID_STACK_NEXT = 37;
    private static final int PICKER_OFFSET = 500;

    private int colorTarget = -1;

    public GuiNpcDmz(EntityNPCInterface npc) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
    }

    @Override
    public void init() {
        super.init();
        NpcCombatProfile p = editorProfile();
        ensureStackSelection(p);
        // GuiNPCInterface2 shell is 420x200. The right column carries thirteen rows now that
        // stack group/form sit under Group/Form, so the pitch is 16 rather than 18 and the boxes
        // are 14 tall; at the old pitch the last two rows fell off the panel entirely.
        int row = 16;
        int boxH = 14;
        int labelW = 72;
        int boxW = 110;
        int left = guiLeft + 8;
        int right = guiLeft + 206;
        int y = guiTop + 4;
        int y2 = y;

        y = field(left, y, row, labelW, boxW, boxH, "Race", ID_RACE, p.raceId, false);
        y = num(left, y, row, labelW, boxW, boxH, "STR", ID_STR, p.strength);
        y = num(left, y, row, labelW, boxW, boxH, "SKP", ID_SKP, p.strikePower);
        y = num(left, y, row, labelW, boxW, boxH, "DEF", ID_DEF, p.resistance);
        y = num(left, y, row, labelW, boxW, boxH, "VIT", ID_VIT, p.vitality);
        y = num(left, y, row, labelW, boxW, boxH, "PWR", ID_PWR, p.kiPower);
        y = num(left, y, row, labelW, boxW, boxH, "ENE", ID_ENE, p.energy);

        // Three across instead of two: Stack is Transform's counterpart for the stack rows below.
        addButton(new GuiButtonNop(this, ID_TRANSFORM, left, y + 2, 58, 18, "Transform"));
        addButton(new GuiButtonNop(this, ID_DESCEND, left + 61, y + 2, 58, 18, "Descend"));
        addButton(new GuiButtonNop(this, ID_STACK_APPLY, left + 122, y + 2, 58, 18, "Stack"));

        y2 = cycleRow(right, y2, row, "Group", ID_GROUP_PREV, ID_GROUP_NEXT, clip(p.selectedFormGroup, 18));
        y2 = cycleRow(right, y2, row, "Form", ID_FORM_PREV, ID_FORM_NEXT, clip(p.selectedFormId, 18));
        y2 = cycleRow(right, y2, row, "Stack Grp", ID_STACK_GROUP_PREV, ID_STACK_GROUP_NEXT,
                clip(p.selectedStackGroup, 18));
        y2 = cycleRow(right, y2, row, "Stack", ID_STACK_PREV, ID_STACK_NEXT,
                clip(p.selectedStackId, 18));
        y2 = colorField(right, y2, row, labelW, boxW, boxH, "Ki color", ID_KI_COLOR,
                p.kiColor == 0 ? "" : NpcCombatProfile.formatHex(p.kiColor), false);
        y2 = colorField(right, y2, row, labelW, boxW, boxH, "DMZ Aura", ID_AURA_COLOR,
                p.auraColorHex == null || p.auraColorHex.isBlank() ? "" : p.auraColorHex, false);
        y2 = field(right, y2, row, labelW, boxW, boxH, "Aura scale", ID_AURA_SCALE,
                Float.toString(p.auraScale), true);
        y2 = num(right, y2, row, labelW, boxW, boxH, "Charge %", ID_CHARGE, p.kiChargePercent);
        y2 = field(right, y2, row, labelW, boxW, boxH, "Mastery %", ID_MASTERY, "100", true);
        y2 = field(right, y2, row, labelW, boxW, boxH, "Techs", ID_TECH,
                String.join(",", p.techniques), false);

        addButton(new GuiButtonYesNo(this, ID_AURA, right, y2, 50, boxH, p.auraOn));
        addLabel(new GuiLabel(120, "Aura", right + 54, y2 + 3, 0xFFFFFF));
        addButton(new GuiButtonYesNo(this, ID_HAIR, right + 92, y2, 50, boxH, p.hairEnabled));
        addLabel(new GuiLabel(121, "Hair", right + 146, y2 + 3, 0xFFFFFF));
        y2 += row;

        addButton(new GuiButtonNop(this, ID_CUSTOMIZE, left, y + 24, 180, 18, "Customize DMZ Appearance"));
        addButton(new GuiButtonNop(this, ID_STACKS, left, y + 46, 180, 18, "DMZ Stack Forms"));

        int combatY = y + 68;
        addLabel(new GuiLabel(122, "Knock", left, combatY + 3, 0xFFFFFF));
        addButton(new GuiButtonYesNo(this, ID_KNOCKABLE, left + 42, combatY, 44, boxH, p.knockable));
        addLabel(new GuiLabel(123, "Damage", left + 94, combatY + 3, 0xFFFFFF));
        addButton(new GuiButtonYesNo(this, ID_PUNCHABLE, left + 140, combatY, 44, boxH, p.punchable));

        addLabel(new GuiLabel(ID_HAIR_COLOR + 200, "Color", right, y2 + 3, 0xFFFFFF));
        textBox(ID_HAIR_COLOR, right + 40, y2, 70, boxH, p.hairColor, 32);
        addButton(new GuiButtonNop(this, ID_HAIR_COLOR + PICKER_OFFSET,
                right + 114, y2, 24, boxH, "..."));
        y2 += row;

        addLabel(new GuiLabel(ID_HAIR_CODE + 200, "Code", right, y2 + 3, 0xFFFFFF));
        // CNPC GuiTextFieldNop caps at 500 in its constructor — setMaxLength BEFORE setValue.
        textBox(ID_HAIR_CODE, right + 32, y2, 166, boxH, p.hairCode, 262144);
    }



    private NpcCombatProfile editorProfile() {
        NpcCombatProfile p = NpcCombatProfile.read(npc);
        NpcAppearanceClient.State appearance = NpcAppearanceClient.get(((Entity) npc).getUUID());
        if (appearance != null) {
            if (!appearance.race().isBlank()) {
                p.raceId = appearance.race();
            }
            // Do not copy committed transform group/form into the picker — Hair/Aura
            // saves were snapping Group back to appearance and hiding Xeno groups.
            // Stats only ever reach the client through this packet (the client entity's own
            // persistent data is never server-synced), so this is the sole source for them.
            p.strength = appearance.strength();
            p.strikePower = appearance.strikePower();
            p.resistance = appearance.resistance();
            p.vitality = appearance.vitality();
            p.kiPower = appearance.kiPower();
            p.energy = appearance.energy();
            p.auraColor = appearance.auraColor();
            p.auraScale = appearance.auraScale();
            p.appearance = appearance.appearance().copy();
            // applyVisualOptions also overlays the draft selectedFormGroup/Id (and stack
            // equivalents) from the last-received network snapshot -- which cannot possibly
            // reflect a Group/Form cycle click from earlier in this same tick. Without this,
            // every button handler (they all rebuild via editorProfile()) snapped the picker's
            // selection back to the pre-click value, and Transform could commit into it.
            // Preserve whatever this client already had selected locally *this session*; only
            // let the network value through on a cold/first open where nothing's picked yet.
            String localSelFormGroup = p.selectedFormGroup;
            String localSelFormId = p.selectedFormId;
            String localSelStackGroup = p.selectedStackGroup;
            String localSelStackId = p.selectedStackId;
            p.applyVisualOptions(appearance.visualOptions());
            if (!localSelFormGroup.isBlank() || !localSelFormId.isBlank()) {
                p.selectedFormGroup = localSelFormGroup;
                p.selectedFormId = localSelFormId;
            }
            if (!localSelStackGroup.isBlank() || !localSelStackId.isBlank()) {
                p.selectedStackGroup = localSelStackGroup;
                p.selectedStackId = localSelStackId;
            }
            p.hairEnabled = appearance.hairEnabled() || p.hairEnabled;
            if (appearance.hairCode() != null
                    && appearance.hairCode().length() >= (p.hairCode == null ? 0 : p.hairCode.length())) {
                p.hairCode = appearance.hairCode();
            }
            if (appearance.hairColor() != null && !appearance.hairColor().isBlank()) {
                p.hairColor = appearance.hairColor();
            }
        }
        NpcHairBridge.fillProfile(npc, p);
        return p;
    }

    private void textBox(int id, int x, int y, int w, int h, String value, int max) {
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x, y, w, h, "");
        box.setMaxLength(max);
        box.setValue(value == null ? "" : value);
        addTextField(box);
    }

    private int cycleRow(int x, int y, int row, String label, int prevId, int nextId, String value) {
        addLabel(new GuiLabel(prevId + 200, label, x, y + 3, 0xFFFFFF));
        addButton(new GuiButtonNop(this, prevId, x + 72, y, 16, 14, "<"));
        addLabel(new GuiLabel(nextId + 200, value, x + 92, y + 3, 0xFFFFFF));
        addButton(new GuiButtonNop(this, nextId, x + 186, y, 16, 14, ">"));
        return y + row;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private int field(int x, int y, int row, int labelW, int boxW, int boxH,
                      String label, int id, String value, boolean floats) {
        addLabel(new GuiLabel(id + 200, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x + labelW, y, boxW, boxH,
                value == null ? "" : value);
        if (floats) {
            box.setFloatsOnly();
        }
        addTextField(box);
        return y + row;
    }

    private int colorField(int x, int y, int row, int labelW, int boxW, int boxH,
                           String label, int id, String value, boolean floats) {
        addLabel(new GuiLabel(id + 200, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x + labelW, y,
                Math.max(40, boxW - 28), boxH, value == null ? "" : value);
        box.setMaxLength(9);
        if (floats) box.setFloatsOnly();
        addTextField(box);
        addButton(new GuiButtonNop(this, id + PICKER_OFFSET,
                x + labelW + boxW - 24, y, 24, boxH, "..."));
        return y + row;
    }

    private int num(int x, int y, int row, int labelW, int boxW, int boxH,
                    String label, int id, int value) {
        addLabel(new GuiLabel(id + 200, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x + labelW, y, boxW, boxH,
                Integer.toString(value));
        box.setNumbersOnly();
        addTextField(box);
        return y + row;
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pullFromFields(editorProfile()).write(npc);
        send(NpcProfileSavePacket.Action.SAVE);
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        NpcCombatProfile p = pullFromFields(editorProfile());
        ensureStackSelection(p);
        int pickerTarget = button.id - PICKER_OFFSET;
        if (pickerTarget == ID_KI_COLOR || pickerTarget == ID_AURA_COLOR
                || pickerTarget == ID_HAIR_COLOR) {
            colorTarget = pickerTarget;
            int fallback = pickerTarget == ID_KI_COLOR ? p.kiColor
                    : pickerTarget == ID_AURA_COLOR ? p.auraColor : 0xFFFFFF;
            int current = NpcCombatProfile.parseHexColor(text(pickerTarget, ""))
                    .orElse(fallback == 0 ? 0xFFFFFF : fallback);
            setSubGui(new NpcColorPicker(current));
            return;
        }
        if (button.id == ID_AURA && button instanceof GuiButtonYesNo yes) {
            p.auraOn = yes.getBoolean();
        } else if (button.id == ID_HAIR && button instanceof GuiButtonYesNo yes) {
            p.hairEnabled = yes.getBoolean();
        } else if (button.id == ID_KNOCKABLE && button instanceof GuiButtonYesNo yes) {
            p.knockable = yes.getBoolean();
        } else if (button.id == ID_PUNCHABLE && button instanceof GuiButtonYesNo yes) {
            p.punchable = yes.getBoolean();
        } else if (button.id == ID_GROUP_PREV || button.id == ID_GROUP_NEXT) {
            cycleGroup(p, button.id == ID_GROUP_NEXT ? 1 : -1);
        } else if (button.id == ID_FORM_PREV || button.id == ID_FORM_NEXT) {
            cycleForm(p, button.id == ID_FORM_NEXT ? 1 : -1);
        } else if (button.id == ID_STACK_GROUP_PREV || button.id == ID_STACK_GROUP_NEXT) {
            cycleStackGroup(p, button.id == ID_STACK_GROUP_NEXT ? 1 : -1);
        } else if (button.id == ID_STACK_PREV || button.id == ID_STACK_NEXT) {
            cycleStackForm(p, button.id == ID_STACK_NEXT ? 1 : -1);
        }
        if (button.id == ID_TRANSFORM) {
            NpcFormLookup.grantMastery(p, p.selectedFormGroup, p.selectedFormId);
        } else if (button.id == ID_STACK_APPLY) {
            grantStackMastery(p);
        }
        p.write(npc);
        if (button.id == ID_GROUP_PREV || button.id == ID_GROUP_NEXT
                || button.id == ID_FORM_PREV || button.id == ID_FORM_NEXT
                || button.id == ID_STACK_GROUP_PREV || button.id == ID_STACK_GROUP_NEXT
                || button.id == ID_STACK_PREV || button.id == ID_STACK_NEXT) {
            send(NpcProfileSavePacket.Action.SAVE);
            init();
            return;
        }
        if (button.id == ID_TRANSFORM) {
            send(NpcProfileSavePacket.Action.TRANSFORM);
        } else if (button.id == ID_STACK_APPLY) {
            send(NpcProfileSavePacket.Action.STACK);
        } else if (button.id == ID_DESCEND) {
            send(NpcProfileSavePacket.Action.DESCEND);
        } else if (button.id == ID_CUSTOMIZE) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new GuiNpcDmzAppearance(npc, p));
        } else if (button.id == ID_STACKS) {
            net.minecraft.client.Minecraft.getInstance().setScreen(new GuiNpcDmzStack(npc, p));
        } else {
            send(NpcProfileSavePacket.Action.SAVE);
        }
    }

    @Override
    public void subGuiClosed(Screen subGui) {
        if (subGui instanceof SubGuiColorSelector selector && colorTarget >= 0) {
            GuiTextFieldNop field = getTextField(colorTarget);
            if (field != null) field.setValue(NpcCombatProfile.formatHex(selector.color));
            NpcCombatProfile profile = pullFromFields(editorProfile());
            profile.write(npc);
            send(NpcProfileSavePacket.Action.SAVE);
            colorTarget = -1;
            init();
        }
    }

    private void cycleGroup(NpcCombatProfile p, int dir) {
        java.util.List<String> groups = NpcFormLookup.groups(p.raceId);
        p.selectedFormGroup = NpcFormLookup.step(groups, p.selectedFormGroup, dir);
        java.util.List<String> forms = NpcFormLookup.forms(p.raceId, p.selectedFormGroup);
        if (!forms.isEmpty() && (p.selectedFormId == null || !forms.contains(p.selectedFormId))) {
            p.selectedFormId = forms.get(0);
        }
        NpcFormLookup.grantMastery(p, p.selectedFormGroup, p.selectedFormId);
    }

    private void cycleForm(NpcCombatProfile p, int dir) {
        java.util.List<String> forms = NpcFormLookup.forms(p.raceId, p.selectedFormGroup);
        p.selectedFormId = NpcFormLookup.step(forms, p.selectedFormId, dir);
        NpcFormLookup.grantMastery(p, p.selectedFormGroup, p.selectedFormId);
    }

    private void cycleStackGroup(NpcCombatProfile p, int dir) {
        p.selectedStackGroup = NpcFormLookup.step(NpcFormLookup.stackGroups(), p.selectedStackGroup, dir);
        // A stack id only means anything inside its own group, so drop it and let
        // ensureStackSelection pick the new group's first entry.
        p.selectedStackId = "";
        ensureStackSelection(p);
    }

    private void cycleStackForm(NpcCombatProfile p, int dir) {
        p.selectedStackId = NpcFormLookup.step(
                NpcFormLookup.stackForms(p.selectedStackGroup), p.selectedStackId, dir);
        grantStackMastery(p);
    }

    /**
     * Defaults the stack picker to the first group/stack DMZ has loaded, and clears a selection
     * that no longer exists in the config. Same contract as {@code GuiNpcDmzStack.ensureSelection}
     * — the two screens edit the same {@code selectedStack*} profile fields, so a stack picked in
     * one shows up in the other.
     */
    private void ensureStackSelection(NpcCombatProfile p) {
        java.util.List<String> groups = NpcFormLookup.stackGroups();
        if (!groups.isEmpty() && !groups.contains(p.selectedStackGroup)) {
            p.selectedStackGroup = groups.get(0);
        }
        java.util.List<String> forms = NpcFormLookup.stackForms(p.selectedStackGroup);
        if (!forms.isEmpty() && !forms.contains(p.selectedStackId)) {
            p.selectedStackId = forms.get(0);
        }
        grantStackMastery(p);
    }

    /**
     * Gives the selected stack full mastery the first time it is picked. Stacks are not levelled
     * from this tab (that is {@code GuiNpcDmzStack}'s Mastery % field), so an unmastered one would
     * otherwise apply at zero effect; an already-mastered stack is left at whatever it holds.
     */
    private void grantStackMastery(NpcCombatProfile p) {
        FormConfig.FormData data = NpcFormLookup.stackForm(p.selectedStackGroup, p.selectedStackId);
        if (data == null) {
            return;
        }
        double max = NpcFormLookup.maxMastery(data);
        if (p.stackMasteries.getMastery(p.selectedStackGroup, p.selectedStackId) <= 0.0) {
            p.stackMasteries.setMastery(p.selectedStackGroup, p.selectedStackId, max, max);
        }
    }

    @Override
    public void save() {
        pullFromFields(editorProfile()).write(npc);
        send(NpcProfileSavePacket.Action.SAVE);
    }

    private NpcCombatProfile pullFromFields(NpcCombatProfile p) {
        p.raceId = text(ID_RACE, p.raceId);
        p.strength = integer(ID_STR, p.strength);
        p.strikePower = integer(ID_SKP, p.strikePower);
        p.resistance = integer(ID_DEF, p.resistance);
        p.vitality = integer(ID_VIT, p.vitality);
        p.kiPower = integer(ID_PWR, p.kiPower);
        p.energy = integer(ID_ENE, p.energy);
        p.kiColor = color(ID_KI_COLOR, p.kiColor);
        p.setAuraColor(text(ID_AURA_COLOR, p.auraColorHex));
        p.auraScale = NpcCombatProfile.clampAuraScale(flo(ID_AURA_SCALE, p.auraScale));
        p.kiChargePercent = Math.max(1, integer(ID_CHARGE, p.kiChargePercent));
        p.hairCode = hairCodeFromField(p.hairCode);
        String typedColor = text(ID_HAIR_COLOR, "");
        if (!typedColor.isBlank()) {
            p.hairColor = NpcCombatProfile.canonicalizeHairColor(typedColor);
        }
        GuiButtonNop hairBtn = getButton(ID_HAIR);
        if (hairBtn instanceof GuiButtonYesNo hairYes) {
            p.hairEnabled = hairYes.getBoolean();
        }
        GuiButtonNop auraBtn = getButton(ID_AURA);
        if (auraBtn instanceof GuiButtonYesNo auraYes) {
            p.auraOn = auraYes.getBoolean();
        }
        GuiButtonNop knockableBtn = getButton(ID_KNOCKABLE);
        if (knockableBtn instanceof GuiButtonYesNo yes) p.knockable = yes.getBoolean();
        GuiButtonNop punchableBtn = getButton(ID_PUNCHABLE);
        if (punchableBtn instanceof GuiButtonYesNo yes) p.punchable = yes.getBoolean();
        if (p.hairCode != null && !p.hairCode.isBlank()) {
            p.hairEnabled = true;
        }
        String techs = text(ID_TECH, "");
        p.techniques.clear();
        if (!techs.isBlank()) {
            for (String part : techs.split(",")) {
                p.addTechnique(part.trim().toLowerCase(Locale.ROOT));
            }
        }
        String group = p.selectedFormGroup;
        String form = p.selectedFormId;
        if (!group.isBlank() && !form.isBlank()) {
            double max = 100.0;
            double percent = flo(ID_MASTERY, 100f);
            p.masteries.setMastery(group, form, max * (percent / 100.0), max);
        }
        return p;
    }

    private void send(NpcProfileSavePacket.Action action) {
        NpcCombatProfile p = NpcCombatProfile.read(npc);
        Entity entity = npc;
        ModNetwork.sendToServer(new NpcProfileSavePacket(
                entity.getId(), p.toTag(), action,
                action == NpcProfileSavePacket.Action.STACK || action == NpcProfileSavePacket.Action.UNSTACK
                        ? p.selectedStackGroup : p.selectedFormGroup,
                action == NpcProfileSavePacket.Action.STACK || action == NpcProfileSavePacket.Action.UNSTACK
                        ? p.selectedStackId : p.selectedFormId));
    }

    private String hairCodeFromField(String stored) {
        String typed = text(ID_HAIR_CODE, "");
        if (typed.isBlank()) {
            return stored == null ? "" : stored;
        }
        if (stored != null && stored.length() > typed.length() && stored.startsWith(typed) && typed.length() <= 500) {
            return stored;
        }
        return typed;
    }

    private String text(int id, String fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return fallback == null ? "" : fallback;
        }
        String value = field.getValue();
        return value == null ? "" : value.trim();
    }

    private int integer(int id, int fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null || !field.isInteger()) {
            return fallback;
        }
        return field.getInteger();
    }

    private float flo(int id, float fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null) {
            return fallback;
        }
        if (field.isFloat()) {
            return field.getFloat();
        }
        try {
            return Float.parseFloat(field.getValue().trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int color(int id, int fallback) {
        String raw = text(id, "");
        if (raw.isBlank()) {
            return 0;
        }
        OptionalInt parsed = NpcCombatProfile.parseHexColor(raw);
        return parsed.isPresent() ? parsed.getAsInt() : fallback;
    }
}
