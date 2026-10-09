package net.bullettrain.xenopixelsmod.mixin.client;

import com.mojang.blaze3d.platform.InputConstants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * An unbound key is not held down, and asking about one is not an error.
 *
 * <p>Target: {@code InputConstants.isKeyDown(long window, int key)}, which is
 * {@code GLFW.glfwGetKey(window, key) == 1} (NeoForge 21.1.248 sources, line 185).
 *
 * <p>Reason: an unbound mapping's key code is -1. Many mods ask whether one of their mappings is
 * held by passing its key code straight to this method without first asking whether it is bound.
 * GLFW answers "not pressed" for -1, which is the right answer, but it also raises error 65539
 * "Invalid key -1" on the way. On the render thread that is noise. On any other thread
 * Minecraft's error handler throws, and Minecraft does build item tooltips off the render thread,
 * for the creative search index. So one mod's unbound hold-key, read from one item's tooltip, is
 * a crash on the first letter typed into the creative search:
 * {@code IllegalStateException: Encountered GL error off-thread @ Render: 65539: Invalid key -1}.
 *
 * <p>The XenoPixels keybind cleanup unbinds other mods' keys by design, which made that latent
 * crash easy to reach (it happened with Create's Shift modifier, 2026-10-06). The cleanup no
 * longer unbinds held modifiers, but a player can still unbind any key in Controls, and this
 * makes that safe whoever the mapping belongs to.
 *
 * <p>The return value is unchanged: GLFW already reports an invalid key as not pressed. Only the
 * error is gone. Valid keys are untouched.
 *
 * <p>{@code require = 0}: this is a safety net, not something the game needs in order to run. If
 * it ever fails to attach, the game must still start; the cleanup rule above is what actually
 * keeps Create's modifiers bound.
 *
 * <p>Version: NeoForge 1.21.1. Side: client.
 */
@Mixin(InputConstants.class)
public abstract class InputConstantsUnboundKeyMixin {

    @Inject(method = "isKeyDown", at = @At("HEAD"), cancellable = true, require = 0)
    private static void xenopixels$unboundKeyIsNotDown(long window, int key, CallbackInfoReturnable<Boolean> cir) {
        if (key < 0) cir.setReturnValue(false);
    }
}
