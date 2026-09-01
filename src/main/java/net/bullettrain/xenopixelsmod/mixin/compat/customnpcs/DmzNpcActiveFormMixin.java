package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.client.compat.npc.NpcFullDmzRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Supplies DMZ layers with the exact group/form data resolved for a synthetic NPC player. */
@Mixin(value = Character.class, remap = false)
public abstract class DmzNpcActiveFormMixin {
    @Inject(method = "getActiveFormData", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$useNpcActiveForm(
            CallbackInfoReturnable<FormConfig.FormData> callback) {
        Character character = (Character) (Object) this;
        if (NpcFullDmzRenderer.isRenderingCharacter(character)) {
            callback.setReturnValue(NpcFullDmzRenderer.activeForm(character));
        }
    }

    @Inject(method = "getActiveStackFormData", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$useNpcActiveStackForm(
            CallbackInfoReturnable<FormConfig.FormData> callback) {
        Character character = (Character) (Object) this;
        if (NpcFullDmzRenderer.isRenderingCharacter(character)) {
            callback.setReturnValue(NpcFullDmzRenderer.activeStackForm(character));
        }
    }
}
