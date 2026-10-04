package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.stats.character.Character;
import net.bullettrain.xenopixelsmod.dmz.form.MakerFormPreviewContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Character.class, remap = false)
public abstract class DmzMakerFormPreviewMixin {
    @Inject(method = "getActiveFormData", at = @At("HEAD"), cancellable = true, require = 1)
    private void xenopixels$makerDraft(CallbackInfoReturnable<FormConfig.FormData> cir) {
        FormConfig.FormData draft = MakerFormPreviewContext.get((Character) (Object) this);
        if (draft != null) cir.setReturnValue(draft);
    }
}
