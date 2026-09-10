package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import espi.mynpcs.api.wrapper.gui.CustomGuiButtonWrapper;
import espi.mynpcs.api.wrapper.gui.CustomGuiLabelWrapper;
import espi.mynpcs.api.wrapper.gui.CustomGuiTextFieldWrapper;
import espi.mynpcs.client.gui.custom.GuiCreationNewParts;
import espi.mynpcs.client.gui.custom.GuiCustom;
import espi.mynpcs.client.gui.custom.components.CustomGuiButton;
import espi.mynpcs.client.gui.custom.components.CustomGuiLabel;
import espi.mynpcs.client.gui.custom.components.CustomGuiTextField;
import espi.mynpcs.entity.EntityCustomNpc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a "DMZ Attachments" section (race, form group/id, aura) to CustomNPCs-Unofficial's
 * Parts editor ({@code GuiCreationNewParts}). Hair is edited only on the wand DMZ tab.
 *
 * <p>Reads/writes {@link NpcCombatProfile} directly -- the addon-independent home for this data.
 *
 * <p>The My NPCs twin of the CustomNPCs mixin of the same name. My NPCs is CustomNPCs with
 * its root package renamed, so the two are identical but for the types they name; this one
 * is gated on the {@code mynpcs} mod id and its twin on {@code customnpcs}, so exactly one
 * applies. Fix bugs in both.
 */
@Mixin(value = GuiCreationNewParts.class, remap = false)
public abstract class GuiCreationNewPartsDmzPanelMixin {
    @Shadow
    private GuiCustom parent;
    @Shadow
    private EntityCustomNpc npc;

    private static final int ID_HEADER = 9100;
    private static final int ID_RACE_LABEL = 9101;
    private static final int ID_RACE_FIELD = 9102;
    private static final int ID_FORM_GROUP_LABEL = 9103;
    private static final int ID_FORM_GROUP_FIELD = 9104;
    private static final int ID_FORM_LABEL = 9105;
    private static final int ID_FORM_FIELD = 9106;
    private static final int ID_AURA_BUTTON = 9107;

    @Inject(method = "init", at = @At("RETURN"))
    private void xenopixels$addDmzPanel(CallbackInfo ci) {
        if (this.npc == null || this.parent == null) {
            return;
        }
        EntityCustomNpc entity = this.npc;
        NpcCombatProfile profile = NpcCombatProfile.read(entity);

        // Positioned to the right of the parts list; may need visual tuning once tested live.
        int x = this.parent.getTotalGuiLeft() + 300;
        int y = this.parent.getTotalGuiTop() + 10;
        int rowHeight = 18;

        this.parent.add(new CustomGuiLabel(this.parent,
                new CustomGuiLabelWrapper(ID_HEADER, "DMZ Attachments", x, y, 140, 12)));
        y += 14;

        this.parent.add(new CustomGuiLabel(this.parent,
                new CustomGuiLabelWrapper(ID_RACE_LABEL, "Race", x, y, 60, 12)));
        CustomGuiTextFieldWrapper raceWrapper = new CustomGuiTextFieldWrapper(ID_RACE_FIELD, x + 60, y, 80, 14);
        raceWrapper.setText(profile.raceId);
        raceWrapper.setOnFocusLost((gui, field) -> {
            NpcCombatProfile p = NpcCombatProfile.read(entity);
            String value = field.getText();
            p.raceId = (value == null || value.isBlank()) ? "human" : value.trim();
            p.write(entity);
        });
        this.parent.add(new CustomGuiTextField(this.parent, raceWrapper));
        y += rowHeight;

        this.parent.add(new CustomGuiLabel(this.parent,
                new CustomGuiLabelWrapper(ID_FORM_GROUP_LABEL, "Form Group", x, y, 60, 12)));
        CustomGuiTextFieldWrapper formGroupWrapper =
                new CustomGuiTextFieldWrapper(ID_FORM_GROUP_FIELD, x + 60, y, 80, 14);
        formGroupWrapper.setText(profile.formGroup);
        formGroupWrapper.setOnFocusLost((gui, field) -> {
            NpcCombatProfile p = NpcCombatProfile.read(entity);
            p.formGroup = field.getText() == null ? "" : field.getText().trim();
            p.write(entity);
        });
        this.parent.add(new CustomGuiTextField(this.parent, formGroupWrapper));
        y += rowHeight;

        this.parent.add(new CustomGuiLabel(this.parent,
                new CustomGuiLabelWrapper(ID_FORM_LABEL, "Form", x, y, 60, 12)));
        CustomGuiTextFieldWrapper formWrapper = new CustomGuiTextFieldWrapper(ID_FORM_FIELD, x + 60, y, 80, 14);
        formWrapper.setText(profile.formId);
        formWrapper.setOnFocusLost((gui, field) -> {
            NpcCombatProfile p = NpcCombatProfile.read(entity);
            p.formId = field.getText() == null ? "" : field.getText().trim();
            p.write(entity);
        });
        this.parent.add(new CustomGuiTextField(this.parent, formWrapper));
        y += rowHeight;

        CustomGuiButtonWrapper auraWrapper = new CustomGuiButtonWrapper(
                ID_AURA_BUTTON, auraLabel(profile.auraOn), x, y, 140, 16);
        auraWrapper.setOnPress((gui, button) -> {
            NpcCombatProfile p = NpcCombatProfile.read(entity);
            p.auraOn = !p.auraOn;
            p.write(entity);
            auraWrapper.setLabel(auraLabel(p.auraOn));
        });
        this.parent.add(new CustomGuiButton(this.parent, auraWrapper));
    }

    private static String auraLabel(boolean on) {
        return "Aura: " + (on ? "Yes" : "No");
    }
}
