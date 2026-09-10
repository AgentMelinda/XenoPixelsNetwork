package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.client.gui.mainmenu.GuiNpcStats;
import espi.mynpcs.client.gui.util.GuiNPCInterface;
import espi.mynpcs.entity.EntityNPCInterface;
import espi.mynpcs.shared.client.gui.components.GuiLabel;
import espi.mynpcs.shared.client.gui.components.GuiTextFieldNop;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcAppearanceClient;
import net.bullettrain.xenopixelsmod.client.XenoServerClientState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
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
        lock(MAX_HEALTH, "Health (DMZ)");
        lock(HEALTH_REGEN, "Health Regen (DMZ)");
        lock(COMBAT_REGEN, "Combat Regen (DMZ)");
    }

    private void lock(int id, String label) {
        GuiTextFieldNop field = getTextField(id);
        if (field != null) field.enabled = false;
        GuiLabel guiLabel = getLabel(id);
        if (guiLabel != null) guiLabel.setMessage(Component.literal(label));
    }
}
