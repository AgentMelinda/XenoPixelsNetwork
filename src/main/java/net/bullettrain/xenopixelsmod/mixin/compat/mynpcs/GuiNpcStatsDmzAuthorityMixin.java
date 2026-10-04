package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.mainmenu.GuiNpcStats;
import espi.mynpcs.client.gui.util.GuiNPCInterface;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.compat.npc.mynpcs.gui.GuiNpcDmzBrain;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.bullettrain.xenopixelsmod.compat.npc.NpcCombatProfile;
import net.bullettrain.xenopixelsmod.network.ModNetwork;
import net.bullettrain.xenopixelsmod.network.packet.NpcProfileSavePacket;
import net.minecraft.client.Minecraft;
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
    private static final int BRAIN = 9110;
    private static final int BRAIN_OPEN = 9112;
    private static final int BRAIN_VERSION = 9113;

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
     * Places the KI Weapon toggle one row under the Health Regen field, with Brain… on the
     * same row (master on/off lives on the Brain screen).
     *
     * <p>Anchored to that widget at runtime rather than to fixed coordinates: a hardcoded
     * {@code guiTop + 168} landed on top of the Combat Regen row (seen 2026-09-11), and the two
     * NPC mods are free to move their own rows. If the extra row would fall outside the panel,
     * KI Weapon is skipped but Brain… is still added so Stats always has an opener.
     */
    private void addKiWeaponRow(NpcCombatProfile profile) {
        GuiTextFieldNop anchor = getTextField(HEALTH_REGEN);
        if (anchor == null) return;
        int y = anchor.getY() + anchor.getHeight() + 8;
        boolean rowFits = y + 18 <= guiTop + imageHeight - 4;
        if (rowFits) {
            addLabel(new GuiLabel(KI_WEAPON_LABEL, "KI Weapon (DMZ)", anchor.getX() - 104, y + 5));
            addButton(new GuiButtonYesNo(this, KI_WEAPON, anchor.getX(), y, 56, 18,
                    profile.kiWeaponOn));
            addButton(new GuiButtonNop(this, BRAIN_OPEN, anchor.getX() + 60, y, 56, 18, "Brain..."));
            addButton(new GuiButtonNop(this, BRAIN_VERSION, anchor.getX() + 120, y, 28, 18,
                    profile.brainVersion.label()));
            return;
        }
        addButton(new GuiButtonNop(this, BRAIN_OPEN, anchor.getX(), y, 56, 18, "Brain..."));
        addButton(new GuiButtonNop(this, BRAIN_VERSION, anchor.getX() + 60, y, 28, 18,
                profile.brainVersion.label()));
    }

    private void lockHealth(NpcCombatProfile profile) {
        lock(MAX_HEALTH, "Health (DMZ)");
        GuiTextFieldNop field = getTextField(MAX_HEALTH);
        if (field != null) {
            field.setValue(
                    net.bullettrain.xenopixelsmod.compat.npc.NpcVitalitySync.displayedMaxHealthText(profile));
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
                    net.bullettrain.xenopixelsmod.compat.npc.NpcVitalitySync.npcStatsMaxHealth(profile)));
        }
    }

    @Inject(method = "buttonEvent", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$toggleKiWeapon(GuiButtonNop button, CallbackInfo ci) {
        if (button == null || npc == null) {
            return;
        }
        Entity entity = (Entity) npc;
        if (button.id == BRAIN_VERSION) {
            NpcCombatProfile current = NpcCombatProfile.read(entity);
            NpcCombatProfile profile = NpcCombatProfile.fromTag(NpcCombatProfile.withBrainVersion(
                    current.toTag(), current.brainVersion.next()));
            profile.write(entity);
            NpcAppearanceClient.applyProfile(entity.getUUID(), profile);
            ModNetwork.sendToServer(new NpcProfileSavePacket(entity.getId(), profile.toTag(),
                    NpcProfileSavePacket.Action.SAVE, "", ""));
            button.setMessage(Component.literal(profile.brainVersion.label()));
            ci.cancel();
            return;
        }
        if (button.id == BRAIN_OPEN) {
            NpcCombatProfile profile = NpcCombatProfile.read(entity);
            Minecraft.getInstance().setScreen(new GuiNpcDmzBrain(npc, profile, GuiNpcDmzBrain.Origin.STATS));
            ci.cancel();
            return;
        }
        if (button.id == BRAIN && button instanceof GuiButtonYesNo yes) {
            NpcCombatProfile profile = NpcCombatProfile.fromTag(NpcCombatProfile.withCombatBrain(
                    NpcCombatProfile.read(entity).toTag(), yes.getBoolean()));
            profile.write(entity);
            NpcAppearanceClient.applyProfile(entity.getUUID(), profile);
            ModNetwork.sendToServer(new NpcProfileSavePacket(entity.getId(), profile.toTag(),
                    NpcProfileSavePacket.Action.SAVE, "", ""));
            ci.cancel();
            return;
        }
        if (button.id != KI_WEAPON || !(button instanceof GuiButtonYesNo yes)) {
            return;
        }
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
