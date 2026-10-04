package net.bullettrain.xenopixelsmod.mixin.compat.mynpcs;

import espi.mynpcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.script.ScriptEngine;

/**
 * {@code setEngine} copies {@code ScriptContainer.Data} only when the language changes.
 * An engine created before {@code XenoPixels} was installed stays without it. Re-put
 * on every {@code setEngine} return, including the language-match early exit.
 */
@Mixin(targets = "espi.mynpcs.controllers.ScriptContainer", remap = false)
public abstract class ScriptContainerXenoBindingMixin {

    @Shadow
    private ScriptEngine engine;

    @Inject(method = "setEngine(Ljava/lang/String;)V", at = @At("TAIL"), remap = false, require = 1)
    private void xenopixels$bindApi(String language, CallbackInfo ci) {
        if (this.engine == null) {
            return;
        }
        Object api = ScriptContainer.Data.get("XenoPixels");
        if (api != null) {
            this.engine.put("XenoPixels", api);
        }
    }
}
