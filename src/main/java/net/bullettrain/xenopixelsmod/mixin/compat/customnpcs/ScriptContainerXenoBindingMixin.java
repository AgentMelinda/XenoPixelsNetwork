package net.bullettrain.xenopixelsmod.mixin.compat.customnpcs;

import noppes.npcs.controllers.ScriptContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import javax.script.ScriptEngine;

/**
 * CustomNPCs has no {@code setEngine}. {@code Jsr223Executor.initialize} copies
 * {@code ScriptContainer.Data} only when a new executor is created. An engine
 * created before {@code XenoPixels} was installed stays without it. Re-put on
 * every {@code run} so player scripts see the bridge.
 */
@Mixin(targets = "noppes.npcs.controllers.Jsr223Executor", remap = false)
public abstract class ScriptContainerXenoBindingMixin {

    @Shadow
    private ScriptEngine engine;

    @Inject(method = "run(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/String;",
            at = @At("HEAD"), remap = false, require = 1)
    private void xenopixels$bindApi(String function, Object event,
                                    CallbackInfoReturnable<String> cir) {
        if (this.engine == null) {
            return;
        }
        Object api = ScriptContainer.Data.get("XenoPixels");
        if (api != null) {
            this.engine.put("XenoPixels", api);
        }
    }
}
