package net.bullettrain.xenopixelsmod.mixin.client;

import com.mojang.blaze3d.platform.GlDebug;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Optional one-shot diagnostic for a depth-format mismatch from the synchronous OpenGL debug callback.
 * Enable with {@code -Dxeno.traceGlDepthMismatch=true}; default behavior and logging remain unchanged.
 */
@Mixin(GlDebug.class)
public abstract class GlDebugDepthMismatchTraceMixin {
    private static final AtomicBoolean XENO_DEPTH_MISMATCH_RECORDED = new AtomicBoolean();

    @Inject(method = "printDebugLog(IIIIIJJ)V", at = @At("HEAD"))
    private static void xeno$traceDepthMismatch(int source, int type, int id, int severity, int messageLength,
                                                long messageAddress, long userParam, CallbackInfo ci) {
        if (!Boolean.getBoolean("xeno.traceGlDepthMismatch")
                || type != org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR
                || XENO_DEPTH_MISMATCH_RECORDED.get()) return;
        String message = org.lwjgl.opengl.GLDebugMessageCallback.getMessage(messageLength, messageAddress);
        if (message == null || !message.contains("Depth formats do not match")
                || !XENO_DEPTH_MISMATCH_RECORDED.compareAndSet(false, true)) return;
        net.bullettrain.xenopixelsmod.XenoPixelsMod.LOGGER.error(
                "Captured first OpenGL depth-format mismatch: source={}, type={}, id={}, severity={}, message={}",
                source, type, id, severity, message, new Throwable("OpenGL mismatch caller stack"));
    }
}
