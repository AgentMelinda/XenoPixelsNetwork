package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * My NPCs' player-script GUI edits a detached {@code PlayerScriptData}, but
 * {@code getEnabled}/{@code getLanguage} read {@code ScriptController.Instance.playerScripts}.
 * The Enabled Yes/No button therefore always shows No and snaps back after {@code init()}.
 *
 * <p>{@code setEnabled}, {@code save} and {@code load} already use this instance. Leave
 * {@code isEnabled()} alone — per-player runtime copies correctly gate on the global switch.
 */
@Mixin(targets = "espi.mynpcs.controllers.data.PlayerScriptData", remap = false)
public abstract class PlayerScriptDataEnabledMixin {

    @Shadow
    private boolean enabled;

    @Shadow
    private String scriptLanguage;

    @Inject(method = "getEnabled()Z", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void xenopixels$readThisEnabled(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.enabled);
    }

    @Inject(method = "getLanguage()Ljava/lang/String;", at = @At("HEAD"), cancellable = true,
            remap = false, require = 0)
    private void xenopixels$readThisLanguage(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(this.scriptLanguage == null ? "" : this.scriptLanguage);
    }
}
