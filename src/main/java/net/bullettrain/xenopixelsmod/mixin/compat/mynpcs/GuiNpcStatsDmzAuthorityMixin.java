package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.mainmenu.GuiNpcStats;
import espi.mynpcs.client.gui.util.GuiNPCInterface;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import espi.mynpcs.shared.client.gui.components.GuiButtonNop;
import espi.mynpcs.shared.client.gui.components.GuiButtonYesNo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Disables native health controls when a MyNPCs entity uses authoritative DMZ stats. */
@Mixin(value = GuiNpcStats.class, remap = false)
public abstract class GuiNpcStatsDmzAuthorityMixin extends GuiNPCInterface {
    private static final int MAX_HEALTH = 0;
    private static final int HEALTH_REGEN = 14;
    private static final int COMBAT_REGEN = 16;
    private static final int KI_WEAPON = 9100;
    private static final int KI_WEAPON_LABEL = 9101;

    protected GuiNpcStatsDmzAuthorityMixin(EntityNPCInterface npc) {
        super(npc);
    }

    @Inject(method = "init", at = @At("RETURN"), require = 1)
    private void xenopixels$lockDmzManagedHealth(CallbackInfo ci) {
        if (!XenoServerClientState.npcDmzStatsAuthoritative()
                || npc == null) {
            return;
        }
        NpcAppearanceClient.State state = NpcAppearanceClient.get(((Entity) npc).getUUID());
        if (state == null || !state.authoritative()) return;
        NpcCombatProfile profile = NpcCombatProfile.read((Entity) npc);
        lockHealth(profile);
        lock(HEALTH_REGEN, "Health Regen (DMZ)");
        lock(COMBAT_REGEN, "Combat Regen (DMZ)");
        addKiWeaponRow(profile);
    }

    /**
     * Places the KI Weapon toggle one row under the Health Regen field.
     *
     * <p>Anchored to that widget at runtime rather than to fixed coordinates: a hardcoded
     * {@code guiTop + 168} landed on top of the Combat Regen row (seen 2026-09-11), and the two
     * NPC mods are free to move their own rows. If the anchor is missing, or the extra row would
     * fall outside the panel, the control is simply not added rather than drawn over something.
     */
    private void addKiWeaponRow(NpcCombatProfile profile) {
        GuiTextFieldNop anchor = getTextField(HEALTH_REGEN);
        if (anchor == null) return;
        int y = anchor.getY() + anchor.getHeight() + 8;
        if (y + 18 > guiTop + imageHeight - 4) return;
        addLabel(new GuiLabel(KI_WEAPON_LABEL, "KI Weapon (DMZ)", anchor.getX() - 104, y + 5));
        addButton(new GuiButtonYesNo(this, KI_WEAPON, anchor.getX(), y, 56, 18,
                profile.kiWeaponOn));
    }

    private void lockHealth(NpcCombatProfile profile) {
        lock(MAX_HEALTH, "Health (DMZ)");
        GuiTextFieldNop field = getTextField(MAX_HEALTH);
        if (field != null) {
            field.setValue(Integer.toString(
                    net.bullettrain.xenopixelsmod.compat.npc.NpcVitalitySync.displayedMaxHealth(profile)));
        }
    }

    private void lock(int id, String label) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) field.setEditable(false);
        GuiLabel guiLabel = getLabel(id);
        if (guiLabel != null) guiLabel.setMessage(Component.literal(label));
    }

    @Inject(method = "save", at = @At("HEAD"), require = 0)
    private void xenopixels$writeCalculatedHealth(CallbackInfo ci) {
        if (npc == null || !XenoServerClientState.npcDmzStatsAuthoritative()) return;
        NpcCombatProfile profile = NpcCombatProfile.read((Entity) npc);
        if (!profile.authoritative) return;
        GuiTextFieldNop field = getTextField(MAX_HEALTH);
        if (field != null) {
            field.setValue(Integer.toString(
                    net.bullettrain.xenopixelsmod.compat.npc.NpcVitalitySync.displayedMaxHealth(profile)));
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$toggleKiWeapon(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || button.id != KI_WEAPON || npc == null
                || !(button instanceof GuiButtonYesNo yes)) {
            return;
        }
        Entity entity = (Entity) npc;
        // Rebuild the payload from the NPC's complete current profile so this toggle cannot
        // clobber appearance, transformation, mastery or combat values owned by other screens.
        NpcCombatProfile profile = NpcCombatProfile.fromTag(NpcCombatProfile.withKiWeapon(
                NpcCombatProfile.read(entity).toTag(), yes.getBoolean()));
        profile.write(entity);
        NpcAppearanceClient.applyProfile(entity.getUUID(), profile);
        ModNetwork.sendToServer(new NpcProfileSavePacket(entity.getId(), profile.toTag(),
                NpcProfileSavePacket.Action.SAVE, "", ""));
        ci.cancel();
    }
}
