package net.bullettrain.xenopixelsmod.client.compat.npc.gui;

import com.dragonminez.client.util.TextureCounter;
import com.dragonminez.common.config.ConfigManager;
import com.dragonminez.common.config.RaceCharacterConfig;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.compat.npc.NpcHairBridge;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import net.bullettrain.xenopixelsmod.compat.npc.NpcDmzAppearance;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import noppes.npcs.client.gui.util.GuiNPCInterface2;
import noppes.npcs.client.gui.SubGuiColorSelector;
import net.minecraft.client.gui.screens.Screen;
import noppes.npcs.entity.EntityNPCInterface;
import noppes.npcs.shared.client.gui.components.GuiButtonNop;
import noppes.npcs.shared.client.gui.components.GuiButtonYesNo;
import noppes.npcs.shared.client.gui.components.GuiLabel;
import noppes.npcs.shared.client.gui.components.GuiTextFieldNop;
import noppes.npcs.shared.client.gui.listeners.ITextfieldListener;

import java.util.OptionalInt;
import java.util.Locale;

/** NPC-safe counterpart to DMZ's CharacterCustomizationScreen. */
public final class GuiNpcDmzAppearance extends GuiNPCInterface2 implements ITextfieldListener, NpcPreviewOwner {
    private static final int TAB_BODY = 0;
    private static final int TAB_FACE = 1;
    private static final int TAB_STYLE = 2;
    private static final int ID_TAB_BODY = 100;
    private static final int ID_TAB_FACE = 101;
    private static final int ID_TAB_STYLE = 102;
    private static final int ID_APPLY = 110;
    private static final int ID_CANCEL = 111;
    private static final int ID_MODE = 120;
    private static final int ID_GENDER = 121;
    private static final int ID_TAIL = 122;
    private static final int ID_HAIR_BASE = 123;
    private static final int ID_TAIL_INHERIT = 124;
    private static final int ID_BODY_PREV = 130;
    private static final int ID_BODY_NEXT = 131;
    private static final int ID_EYES_PREV = 132;
    private static final int ID_EYES_NEXT = 133;
    private static final int ID_NOSE_PREV = 134;
    private static final int ID_NOSE_NEXT = 135;
    private static final int ID_MOUTH_PREV = 136;
    private static final int ID_MOUTH_NEXT = 137;
    private static final int ID_TATTOO_PREV = 138;
    private static final int ID_TATTOO_NEXT = 139;
    private static final int ID_BROWS_PREV = 140;
    private static final int ID_BROWS_NEXT = 141;

    private static final int ID_CLASS = 200;
    private static final int ID_BODY_TYPE = 201;
    private static final int ID_BOOB_SCALE = 202;
    private static final int ID_BODY_1 = 203;
    private static final int ID_BODY_2 = 204;
    private static final int ID_BODY_3 = 205;
    private static final int ID_TAIL_COLOR = 206;
    private static final int ID_EYES = 210;
    private static final int ID_NOSE = 211;
    private static final int ID_MOUTH = 212;
    private static final int ID_TATTOO = 213;
    private static final int ID_EYE_1 = 214;
    private static final int ID_EYE_2 = 215;
    private static final int ID_HEAD_BONE = 220;
    private static final int ID_HAIR_COLOR = 221;
    private static final int ID_AURA_COLOR = 222;
    private static final int ID_AURA_SCALE = 223;
    private static final int ID_HALO = 224;
    private static final int ID_AURA_ON = 225;
    private static final int ID_ROCKS = 226;
    private static final int ID_SPARKING = 227;
    private static final int ID_LIGHTNING = 228;
    private static final int ID_AURA_DETAILS = 229;
    private static final int ID_KI_WEAPON_ON = 230;
    private static final int ID_KI_WEAPON_TYPE = 231;
    private static final int ID_GROUND_RING = 232;
    private static final int ID_FLY_ON = 233;
    private static final int ID_FLY_LEVEL = 234;
    private static final int ID_SKILLS = 235;
    private static final int ID_HAIR_STYLE = 236;
    private static final int PICKER_OFFSET = 500;
    private static final int PREVIEW_X = 278;
    /** Right edge of the appearance panel's usable width; the preview panel starts past it. */
    private static final int HAIR_ROW_END = 270;
    private static final int PREVIEW_Y = 28;
    private static final int PREVIEW_W = 134;
    private static final int PREVIEW_H = 140;

    private final NpcCombatProfile original;
    private NpcCombatProfile draft;
    private int tab;
    private int colorTarget = -1;
    private float previewYaw = 180.0f;
    private float previewPitch;
    private float previewZoom = 1.0f;
    private boolean previewDragging;

    public GuiNpcDmzAppearance(EntityNPCInterface npc, NpcCombatProfile source) {
        super(npc, GuiNpcDmzMenuButton.MENU_ID);
        this.original = copy(source);
        this.draft = copy(source);
    }

    @Override
    public void init() {
        super.init();
        int x = guiLeft + 8;
        int y = guiTop + 4;
        addButton(new GuiButtonNop(this, ID_TAB_BODY, x, y, 72, 18, "Body"));
        addButton(new GuiButtonNop(this, ID_TAB_FACE, x + 76, y, 72, 18, "Face"));
        addButton(new GuiButtonNop(this, ID_TAB_STYLE, x + 152, y, 92, 18, "Hair / Aura"));
        addButton(new GuiButtonNop(this, ID_APPLY, guiLeft + 300, guiTop + 174, 52, 18, "Apply"));
        addButton(new GuiButtonNop(this, ID_CANCEL, guiLeft + 356, guiTop + 174, 56, 18, "Cancel"));

        NpcDmzAppearance a = appearance();
        addLabel(new GuiLabel(900, "Appearance Mode", x, y + 29, 0xFFD36A));
        addButton(new GuiButtonNop(this, ID_MODE, x + 106, y + 24, 92, 18, a.mode.name()));
        if (tab == TAB_BODY) initBody(x, y + 50, a);
        else if (tab == TAB_FACE) initFace(x, y + 50, a);
        else initStyle(x, y + 50, a);
        if (NpcAppearanceClient.get(((Entity) npc).getUUID()) == null) preview(draft);
    }

    private void initBody(int x, int y, NpcDmzAppearance a) {
        addButton(new GuiButtonNop(this, ID_GENDER, x, y, 92, 18, "Gender: " + a.gender));
        addButton(new GuiButtonYesNo(this, ID_TAIL, x + 98, y, 60, 18, a.saiyanTail));
        addLabel(new GuiLabel(901, "Tail", x + 162, y + 4, 0xFFFFFF));
        field(ID_CLASS, "Class", x, y + 24, a.characterClass, false);
        choiceRow("Body type", ID_BODY_PREV, ID_BODY_NEXT, x, y + 44,
                a.bodyType, maxBodyType(a));
        field(ID_BOOB_SCALE, "Bust scale", x, y + 64, Float.toString(a.boobScale), false);
        colorField(ID_BODY_1, "Body color 1", x, y + 84, a.bodyColor);
        colorField(ID_BODY_2, "Body color 2", x, y + 104, a.bodyColor2);
        colorField(ID_BODY_3, "Body color 3", x, y + 124, a.bodyColor3);
        addLabel(new GuiLabel(907, "Tail color", x + 202, y + 65, 0xFFFFFF));
        addButton(new GuiButtonNop(this, ID_TAIL_COLOR + PICKER_OFFSET,
                x + 202, y + 80, 58, 16, "..."));
        addButton(new GuiButtonNop(this, ID_TAIL_INHERIT,
                x + 202, y + 100, 58, 16, a.tailUseRaceColor ? "Tail: Race" : "Tail: Custom"));
    }

    private void initFace(int x, int y, NpcDmzAppearance a) {
        choiceRow("Eyes", ID_EYES_PREV, ID_EYES_NEXT, x, y,
                a.eyesType, maxEyesType());
        if (supportsSeparateBrows()) {
            choiceRow("Eyebrows", ID_BROWS_PREV, ID_BROWS_NEXT, x, y + 20,
                    a.eyebrowsType, maxEyesType());
        } else {
            addLabel(new GuiLabel(906, "Eyebrows: linked by DMZ race", x, y + 23, 0xAAAAAA));
        }
        choiceRow("Nose", ID_NOSE_PREV, ID_NOSE_NEXT, x, y + 40,
                a.noseType, maxNoseType());
        choiceRow("Mouth", ID_MOUTH_PREV, ID_MOUTH_NEXT, x, y + 60,
                a.mouthType, maxMouthType());
        choiceRow("Tattoo", ID_TATTOO_PREV, ID_TATTOO_NEXT, x, y + 80,
                a.tattooType, maxTattooType());
        colorField(ID_EYE_1, "Eye color 1", x, y + 104, a.eye1Color);
        colorField(ID_EYE_2, "Eye color 2", x, y + 124, a.eye2Color);
    }

    private void initStyle(int x, int y, NpcDmzAppearance a) {
        field(ID_HEAD_BONE, "Head parts", x, y, a.activeHeadBone, false);
        addButton(new GuiButtonNop(this, ID_AURA_DETAILS, x + 202, y, 58, 16, "Details"));
        addButton(new GuiButtonNop(this, ID_SKILLS, x + 202, y + 18, 58, 16, "Skills"));
        hairRow(x, y + 20, draft.hairColor, draft.hairStyleId);
        colorField(ID_AURA_COLOR, "Base aura", x, y + 40,
                draft.auraColorHex == null || draft.auraColorHex.isBlank() ? "" : draft.auraColorHex);
        field(ID_AURA_SCALE, "Aura scale", x, y + 60, Float.toString(draft.auraScale), false);
        addLabel(new GuiLabel(908, "Ki weapon", x + 202, y + 23, 0xFFFFFF));
        addButton(new GuiButtonYesNo(this, ID_KI_WEAPON_ON,
                x + 202, y + 36, 58, 18, draft.kiWeaponOn));
        addButton(new GuiButtonNop(this, ID_KI_WEAPON_TYPE,
                x + 202, y + 58, 58, 18, kiWeaponLabel(draft.kiWeaponType)));
        // Three toggle columns. The panel is 420x200 with Apply/Cancel occupying the bottom
        // right from y+174 down, so nothing may sit below the y+120 row -- a fourth row would
        // render outside the background.
        //
        // The usable width ends at x+270, not x+300 as this comment used to say: the NPC preview
        // is drawn at guiLeft+278 and x is guiLeft+8. Anything past x+270 renders underneath it.
        // The third column's "Rings" and "Fly" labels already run a few pixels into the preview
        // for that reason.
        toggle(ID_HAIR_BASE, "Base hair", x, y + 80, a.renderHairBase);
        toggle(ID_HALO, "Halo", x + 108, y + 80, draft.haloOn);
        toggle(ID_GROUND_RING, "Rings", x + 202, y + 80, draft.auraGroundRing);
        toggle(ID_AURA_ON, "Aura", x, y + 100, draft.auraOn);
        toggle(ID_ROCKS, "Rocks", x + 108, y + 100, draft.auraRocks);
        toggle(ID_FLY_ON, "Fly", x + 202, y + 100, draft.flySkillOn);
        toggle(ID_SPARKING, "Sparking", x, y + 120, draft.auraSparking);
        toggle(ID_LIGHTNING, "Lightning", x + 108, y + 120, draft.auraLightning);
        addLabel(new GuiLabel(909, "Fly lv", x + 202, y + 125, 0xFFFFFF));
        GuiTextFieldNop flyLevel = new GuiTextFieldNop(ID_FLY_LEVEL, this, x + 232, y + 121,
                28, 16, Integer.toString(draft.flySkillLevel));
        flyLevel.setNumbersOnly();
        flyLevel.setMaxLength(2);
        addTextField(flyLevel);
    }

    /**
     * Hair colour and hair style on one row.
     *
     * <p>A row of its own rather than a {@link #colorField} plus a button, because the panel's
     * usable width ends at {@code x + 270} -- the NPC preview starts there -- and every other
     * column on this tab is already spoken for down to {@code y + 120}. Squeezing the style button
     * in beside the colour keeps the whole control inside the same budget the colour field already
     * had, instead of pushing it under the preview.
     */
    private void hairRow(int x, int y, String color, int styleId) {
        addLabel(new GuiLabel(ID_HAIR_COLOR + 1000, "Hair color", x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(ID_HAIR_COLOR, this, x + 92, y, 60, 16,
                color == null ? "" : color);
        box.setMaxLength(9);
        addTextField(box);
        addButton(new GuiButtonNop(this, ID_HAIR_COLOR + PICKER_OFFSET, x + 154, y, 20, 16, "..."));
        // DragonMineZ's own character-creation hair styles; H0 hands back to the hair code.
        // Cycles forward and wraps -- NpcHairBridge.cycleStyle wraps through 0, so one button
        // still reaches every style.
        String styleLabel = hairStyleLabel(styleId);
        addButton(new GuiButtonNop(this, ID_HAIR_STYLE, x + 176, y,
                hairStyleButtonWidth(styleLabel, HAIR_ROW_END - 176), 16, styleLabel));
    }

    /**
     * "H0" is the custom hair code; "H1"+ are DragonMineZ's own character-creation styles. The
     * short form is the one the DMZ appearance tab uses, so the two screens read the same.
     */
    private static String hairStyleLabel(int styleId) {
        return "H" + Math.max(0, styleId);
    }

    /**
     * The style button is as wide as its own label, so "H0" does not draw a 66px button around a
     * two-character caption and a two-digit style is never squeezed. {@code maxWidth} keeps the
     * control inside the row, which ends where the preview panel begins.
     */
    static int hairStyleButtonWidth(String label, int maxWidth) {
        Minecraft client = Minecraft.getInstance();
        int text = client == null || client.font == null
                ? 6 * label.length()
                : client.font.width(label);
        return Mth.clamp(text + 12, 24, Math.max(24, maxWidth));
    }

    private void toggle(int id, String label, int x, int y, boolean value) {
        addButton(new GuiButtonYesNo(this, id, x, y, 48, 18, value));
        addLabel(new GuiLabel(id + 1700, label, x + 52, y + 4, 0xFFFFFF));
    }

    private void field(int id, String label, int x, int y, String value, boolean integer) {
        addLabel(new GuiLabel(id + 1000, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x + 92, y, 106, 16, value == null ? "" : value);
        if (integer) box.setNumbersOnly();
        box.setMaxLength(128);
        addTextField(box);
    }

    private void colorField(int id, String label, int x, int y, String value) {
        addLabel(new GuiLabel(id + 1000, label, x, y + 3, 0xFFFFFF));
        GuiTextFieldNop box = new GuiTextFieldNop(id, this, x + 92, y, 82, 16, value == null ? "" : value);
        box.setMaxLength(9);
        addTextField(box);
        addButton(new GuiButtonNop(this, id + PICKER_OFFSET, x + 176, y, 22, 16, "...") );
    }

    private void choiceRow(String label, int previousId, int nextId, int x, int y,
                           int value, int maximum) {
        addLabel(new GuiLabel(previousId + 1000, label, x, y + 3, 0xFFFFFF));
        addButton(new GuiButtonNop(this, previousId, x + 92, y, 20, 16, "<"));
        addLabel(new GuiLabel(previousId + 1100,
                Integer.toString(Math.max(0, Math.min(maximum, value))), x + 124, y + 3, 0xFFD36A));
        addLabel(new GuiLabel(previousId + 1200, "/ " + maximum, x + 145, y + 3, 0xAAAAAA));
        addButton(new GuiButtonNop(this, nextId, x + 178, y, 20, 16, ">"));
    }

    @Override
    public void buttonEvent(GuiButtonNop button) {
        pull();
        NpcDmzAppearance a = appearance();
        if (button.id == ID_TAB_BODY) tab = TAB_BODY;
        else if (button.id == ID_TAB_FACE) tab = TAB_FACE;
        else if (button.id == ID_TAB_STYLE) tab = TAB_STYLE;
        else if (button.id == ID_MODE) a.mode = a.mode.next();
        else if (button.id == ID_GENDER) a.gender = "female".equals(a.gender) ? "male" : "female";
        else if (button.id == ID_TAIL && button instanceof GuiButtonYesNo yes) a.saiyanTail = yes.getBoolean();
        else if (button.id == ID_TAIL_INHERIT) {
            // A toggle, not a clear. The colour is kept either way so switching back restores it.
            a.tailUseRaceColor = !a.tailUseRaceColor;
        }
        else if (button.id == ID_HAIR_BASE && button instanceof GuiButtonYesNo yes) a.renderHairBase = yes.getBoolean();
        else if (button.id == ID_HALO && button instanceof GuiButtonYesNo yes) draft.haloOn = yes.getBoolean();
        else if (button.id == ID_AURA_ON && button instanceof GuiButtonYesNo yes) draft.auraOn = yes.getBoolean();
        else if (button.id == ID_ROCKS && button instanceof GuiButtonYesNo yes) draft.auraRocks = yes.getBoolean();
        else if (button.id == ID_SPARKING && button instanceof GuiButtonYesNo yes) draft.auraSparking = yes.getBoolean();
        else if (button.id == ID_LIGHTNING && button instanceof GuiButtonYesNo yes) draft.auraLightning = yes.getBoolean();
        else if (button.id == ID_GROUND_RING && button instanceof GuiButtonYesNo yes) draft.auraGroundRing = yes.getBoolean();
        else if (button.id == ID_FLY_ON && button instanceof GuiButtonYesNo yes) draft.flySkillOn = yes.getBoolean();
        else if (button.id == ID_HAIR_STYLE) {
            draft.hairStyleId = NpcHairBridge.cycleStyle(draft.hairStyleId, 1,
                    NpcHairBridge.presetCount());
        }
        else if (button.id == ID_KI_WEAPON_ON && button instanceof GuiButtonYesNo yes) draft.kiWeaponOn = yes.getBoolean();
        else if (button.id == ID_KI_WEAPON_TYPE) draft.kiWeaponType = nextKiWeaponType(draft.kiWeaponType);
        else if (button.id == ID_BODY_PREV) a.bodyType = cycle(a.bodyType, -1, maxBodyType(a));
        else if (button.id == ID_BODY_NEXT) a.bodyType = cycle(a.bodyType, 1, maxBodyType(a));
        else if (button.id == ID_EYES_PREV) a.eyesType = cycle(a.eyesType, -1, maxEyesType());
        else if (button.id == ID_EYES_NEXT) a.eyesType = cycle(a.eyesType, 1, maxEyesType());
        else if (button.id == ID_BROWS_PREV) a.eyebrowsType =
                NpcDmzAppearance.cycleEyebrowType(a.eyebrowsType, -1, maxEyesType());
        else if (button.id == ID_BROWS_NEXT) a.eyebrowsType =
                NpcDmzAppearance.cycleEyebrowType(a.eyebrowsType, 1, maxEyesType());
        else if (button.id == ID_NOSE_PREV) a.noseType = cycle(a.noseType, -1, maxNoseType());
        else if (button.id == ID_NOSE_NEXT) a.noseType = cycle(a.noseType, 1, maxNoseType());
        else if (button.id == ID_MOUTH_PREV) a.mouthType = cycle(a.mouthType, -1, maxMouthType());
        else if (button.id == ID_MOUTH_NEXT) a.mouthType = cycle(a.mouthType, 1, maxMouthType());
        else if (button.id == ID_TATTOO_PREV) a.tattooType = cycle(a.tattooType, -1, maxTattooType());
        else if (button.id == ID_TATTOO_NEXT) a.tattooType = cycle(a.tattooType, 1, maxTattooType());
        else if (button.id == ID_SKILLS) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzSkills(npc, draft));
            return;
        }
        else if (button.id == ID_AURA_DETAILS) {
            Minecraft.getInstance().setScreen(new GuiNpcDmzAuraEditor(npc, draft,
                    GuiNpcDmzAuraEditor.Target.BASE));
            return;
        }
        else if (button.id >= PICKER_OFFSET + ID_CLASS) {
            colorTarget = button.id - PICKER_OFFSET;
            String value = colorTarget == ID_TAIL_COLOR
                    ? (a.tailColor.isBlank() ? a.bodyColor2 : a.tailColor)
                    : text(colorTarget, "#FFFFFF");
            int current = NpcCombatProfile.parseHexColor(value).orElse(0xFFFFFF);
            setSubGui(new NpcColorPicker(current));
            return;
        }
        else if (button.id == ID_APPLY) {
            preview(draft);
            ModNetwork.sendToServer(new NpcProfileSavePacket(((Entity) npc).getId(), draft.toTag(),
                    NpcProfileSavePacket.Action.SAVE, draft.formGroup, draft.formId));
            Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
            return;
        } else if (button.id == ID_CANCEL) {
            preview(original);
            Minecraft.getInstance().setScreen(new GuiNpcDmz(npc));
            return;
        }
        preview(draft);
        init();
    }

    @Override
    public void subGuiClosed(Screen subGui) {
        if (subGui instanceof SubGuiColorSelector selector && colorTarget >= 0) {
            String selected = NpcCombatProfile.formatHex(selector.color);
            if (colorTarget == ID_TAIL_COLOR) {
                appearance().tailColor = selected;
                // Picking a colour is a request to use it.
                appearance().tailUseRaceColor = false;
            } else {
                GuiTextFieldNop field = getTextField(colorTarget);
                if (field != null) field.setValue(selected);
            }
            pull();
            preview(draft);
            colorTarget = -1;
            init();
        }
    }

    @Override
    public void unFocused(GuiTextFieldNop field) {
        pull();
        preview(draft);
    }

    private void pull() {
        NpcDmzAppearance a = appearance();
        a.characterClass = text(ID_CLASS, a.characterClass);
        a.boobScale = NpcDmzAppearance.clampBoobScale(decimal(ID_BOOB_SCALE, a.boobScale));
        a.bodyColor = colorText(ID_BODY_1, a.bodyColor);
        a.bodyColor2 = colorText(ID_BODY_2, a.bodyColor2);
        a.bodyColor3 = colorText(ID_BODY_3, a.bodyColor3);
        a.eye1Color = colorText(ID_EYE_1, a.eye1Color);
        a.eye2Color = colorText(ID_EYE_2, a.eye2Color);
        a.activeHeadBone = text(ID_HEAD_BONE, a.activeHeadBone);
        draft.hairColor = colorText(ID_HAIR_COLOR, draft.hairColor);
        String aura = text(ID_AURA_COLOR, draft.auraColorHex);
        draft.setAuraColor(aura);
        draft.auraScale = NpcCombatProfile.clampAuraScale(decimal(ID_AURA_SCALE, draft.auraScale));
        GuiButtonNop halo = getButton(ID_HALO);
        if (halo instanceof GuiButtonYesNo yes) draft.haloOn = yes.getBoolean();
        GuiButtonNop auraOn = getButton(ID_AURA_ON);
        if (auraOn instanceof GuiButtonYesNo yes) draft.auraOn = yes.getBoolean();
        GuiButtonNop rocks = getButton(ID_ROCKS);
        if (rocks instanceof GuiButtonYesNo yes) draft.auraRocks = yes.getBoolean();
        GuiButtonNop sparking = getButton(ID_SPARKING);
        if (sparking instanceof GuiButtonYesNo yes) draft.auraSparking = yes.getBoolean();
        GuiButtonNop lightning = getButton(ID_LIGHTNING);
        if (lightning instanceof GuiButtonYesNo yes) draft.auraLightning = yes.getBoolean();
        GuiButtonNop groundRing = getButton(ID_GROUND_RING);
        if (groundRing instanceof GuiButtonYesNo yes) draft.auraGroundRing = yes.getBoolean();
        GuiButtonNop fly = getButton(ID_FLY_ON);
        if (fly instanceof GuiButtonYesNo yes) draft.flySkillOn = yes.getBoolean();
        draft.flySkillLevel = NpcCombatProfile.clampFlySkillLevel(
                integer(ID_FLY_LEVEL, draft.flySkillLevel));
        GuiButtonNop kiWeapon = getButton(ID_KI_WEAPON_ON);
        if (kiWeapon instanceof GuiButtonYesNo yes) draft.kiWeaponOn = yes.getBoolean();
        draft.kiWeaponType = NpcCombatProfile.canonicalKiWeaponType(draft.kiWeaponType);
        normalizeChoices(a);
    }

    private static String nextKiWeaponType(String current) {
        String canonical = NpcCombatProfile.canonicalKiWeaponType(current);
        int index = NpcCombatProfile.KI_WEAPON_TYPES.indexOf(canonical);
        return NpcCombatProfile.KI_WEAPON_TYPES.get(
                (index + 1) % NpcCombatProfile.KI_WEAPON_TYPES.size());
    }

    private static String kiWeaponLabel(String type) {
        String canonical = NpcCombatProfile.canonicalKiWeaponType(type);
        return canonical.substring(0, 1).toUpperCase(Locale.ROOT) + canonical.substring(1);
    }

    private void normalizeChoices(NpcDmzAppearance a) {
        a.bodyType = Math.max(0, Math.min(maxBodyType(a), a.bodyType));
        a.eyesType = Math.max(0, Math.min(maxEyesType(), a.eyesType));
        if (supportsSeparateBrows()) {
            a.eyebrowsType = NpcDmzAppearance.sanitizeEyebrowType(
                    Math.max(0, Math.min(maxEyesType(), a.eyebrowsType)), a.eyesType);
        } else {
            a.eyebrowsType = a.eyesType;
        }
        a.noseType = Math.max(0, Math.min(maxNoseType(), a.noseType));
        a.mouthType = Math.max(0, Math.min(maxMouthType(), a.mouthType));
        a.tattooType = Math.max(0, Math.min(maxTattooType(), a.tattooType));
    }

    private int maxBodyType(NpcDmzAppearance a) {
        return Math.max(0, TextureCounter.getMaxBodyTypes(effectiveModelBase(), a.gender));
    }

    private int maxEyesType() { return Math.max(0, TextureCounter.getMaxEyesTypes(effectiveModelBase())); }
    private int maxNoseType() { return Math.max(0, TextureCounter.getMaxNoseTypes(effectiveModelBase())); }
    private int maxMouthType() { return Math.max(0, TextureCounter.getMaxMouthTypes(effectiveModelBase())); }
    private int maxTattooType() { return Math.max(0, TextureCounter.getMaxTattooTypes(effectiveModelBase())); }

    private boolean supportsSeparateBrows() {
        String model = effectiveModelBase();
        if ("human".equals(model) || "saiyan".equals(model) || "halfsaiyan".equals(model)
                || "humansaiyan".equals(model)) return true;
        String race = draft.raceId == null ? "" : draft.raceId.trim().toLowerCase(Locale.ROOT);
        return "human".equals(race) || "saiyan".equals(race) || "halfsaiyan".equals(race);
    }

    private String effectiveModelBase() {
        String race = draft.raceId == null ? "human" : draft.raceId.trim().toLowerCase(Locale.ROOT);
        if (race.isBlank()) race = "human";
        RaceCharacterConfig config = ConfigManager.getRaceCharacter(race);
        if (config != null && Boolean.TRUE.equals(config.hasCustomModel())
                && config.getCustomModel() != null && !config.getCustomModel().isBlank()) {
            return config.getCustomModel().toLowerCase(Locale.ROOT);
        }
        return race;
    }

    private static int cycle(int value, int direction, int maximum) {
        return Math.floorMod(value + direction, Math.max(0, maximum) + 1);
    }

    private NpcDmzAppearance appearance() {
        if (draft.appearance == null) draft.appearance = new NpcDmzAppearance();
        return draft.appearance;
    }

    private String text(int id, String fallback) {
        GuiTextFieldNop field = getTextField(id);
        if (field == null || field.getValue() == null) return fallback == null ? "" : fallback;
        return field.getValue().trim();
    }

    private int integer(int id, int fallback) {
        try { return Integer.parseInt(text(id, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private float decimal(int id, float fallback) {
        try { return Float.parseFloat(text(id, Float.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String colorText(int id, String fallback) {
        String value = text(id, fallback);
        String canonical = NpcCombatProfile.canonicalizeHairColor(value);
        return canonical.isBlank() ? fallback : canonical;
    }

    /**
     * The preview's caption: the NPC's own name, trimmed to the panel, or the generic label when it
     * has none.
     */
    private String previewTitle() {
        String fallback = tab == TAB_FACE ? "Face preview" : "NPC preview";
        String name;
        try {
            name = ((Entity) npc).getName().getString();
        } catch (Throwable ignored) {
            return fallback;
        }
        if (name == null || name.isBlank()) return fallback;
        name = name.trim();
        // Leave a margin either side of the 134px panel so the caption cannot touch its border.
        return getFontRenderer().plainSubstrByWidth(name, PREVIEW_W - 16);
    }

    private void preview(NpcCombatProfile profile) {
        NpcCombatProfile snapshot = copy(profile);
        NpcAppearanceClient.applyProfile(((Entity) npc).getUUID(), snapshot);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (hasSubGui()) return;

        int left = guiLeft + PREVIEW_X;
        int top = guiTop + PREVIEW_Y;
        int right = left + PREVIEW_W;
        int bottom = top + PREVIEW_H;
        graphics.fill(left, top, right, bottom, 0xFF101218);
        graphics.fill(left, top, right, top + 1, 0xFFB98235);
        graphics.fill(left, bottom - 1, right, bottom, 0xFFB98235);
        graphics.fill(left, top, left + 1, bottom, 0xFFB98235);
        graphics.fill(right - 1, top, right, bottom, 0xFFB98235);
        // Name the NPC rather than the panel. With several NPCs open in turn, "NPC preview" said
        // nothing about which one you were editing.
        graphics.drawCenteredString(getFontRenderer(), previewTitle(),
                left + PREVIEW_W / 2, top + 4, 0xFFE2C078);
        graphics.drawCenteredString(getFontRenderer(), "Drag rotate  •  Wheel zoom",
                left + PREVIEW_W / 2, bottom - 11, 0xFF888888);

        int modelX = left + PREVIEW_W / 2;
        int modelScale = Math.max(20, Math.round((tab == TAB_FACE ? 104.0f : 58.0f) * previewZoom));
        // Keep the head framed while zoom changes instead of moving it out of the scissor box.
        int modelY = tab == TAB_FACE ? top + 45 + Math.round(modelScale * 1.55f) : bottom - 5;
        graphics.enableScissor(left + 2, top + 15, right - 2, bottom - 13);
        try {
            NpcFullDmzRenderer.renderPreview(npc, graphics, modelX, modelY, modelScale,
                    previewYaw, previewPitch, partialTick, tab == TAB_STYLE);
        } finally {
            graphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && insidePreview(mouseX, mouseY)) {
            previewDragging = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && previewDragging) {
            previewYaw += (float) dragX * 1.5f;
            previewPitch = Mth.clamp(previewPitch - (float) dragY, -30.0f, 30.0f);
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
            previewZoom = Mth.clamp(previewZoom + (float) scrollY * 0.08f, 0.65f, 1.75f);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private boolean insidePreview(double mouseX, double mouseY) {
        int left = guiLeft + PREVIEW_X;
        int top = guiTop + PREVIEW_Y;
        return mouseX >= left && mouseX < left + PREVIEW_W
                && mouseY >= top && mouseY < top + PREVIEW_H;
    }

    private static NpcCombatProfile copy(NpcCombatProfile profile) {
        NpcCombatProfile out = new NpcCombatProfile();
        if (profile != null) {
            out = NpcCombatProfile.fromTag(profile.toTag());
        }
        return out;
    }

    @Override
    public void save() {
        // Apply/Cancel own the transaction; changing tabs must not write to the server.
    }
}
