package net.bullettrain.xenopixelsmod.mixin.compat.dmz;

import com.dragonminez.common.config.FormConfig;
import com.dragonminez.common.util.TransformationsHelper;
import net.bullettrain.xenopixelsmod.dmz.form.IkariStackRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * DragonMineZ asks {@code areFormsCompatible} wherever a form and a stack meet: starting the stack,
 * choosing it from the menu, and changing form with it on (an incompatible stack is then removed).
 * For the Ikari stack the answer is {@link IkariStackRules}: Super Saiyan 1 to 3 only. Verified
 * against dragonminez-2.1.3: {@code areFormsCompatible(FormData, String, FormData, String)}.
 */
@Mixin(value = TransformationsHelper.class, remap = false)
public abstract class DmzIkariStackCompatMixin {

    @Inject(method = "areFormsCompatible", at = @At("HEAD"), cancellable = true)
    private static void xenopixels$ikariOnSuperSaiyanOnly(FormConfig.FormData baseForm, String baseGroup,
                                                          FormConfig.FormData stackForm, String stackGroup,
                                                          CallbackInfoReturnable<Boolean> cir) {
        if (stackForm == null || !IkariStackRules.isIkari(stackGroup)) return;
        cir.setReturnValue(baseForm != null && IkariStackRules.allows(baseGroup, baseForm.getName()));
    }
}
