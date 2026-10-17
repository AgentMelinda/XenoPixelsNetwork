package net.bullettrain.xenopixelsmod.client.space;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether an Iris shader pack is drawing this frame, and whether Iris is in its shadow pass. Reflective so
 * Iris stays optional: {@code net.irisshaders.iris.api.v0.IrisApi} (Iris 1.8.14-beta.1, verified with javap)
 * exposes {@code getInstance()}, {@code isShaderPackInUse()} and {@code isRenderingShadowPass()}.
 * Any failure reads as "no pack", which keeps the vanilla rendering path.
 */
final class IrisShaderState {
    private static final MethodHandle INSTANCE, PACK_IN_USE, SHADOW_PASS;
    static {
        MethodHandle instance = null, pack = null, shadow = null;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            var lookup = MethodHandles.publicLookup();
            instance = lookup.findStatic(api, "getInstance", MethodType.methodType(api));
            pack = lookup.findVirtual(api, "isShaderPackInUse", MethodType.methodType(boolean.class));
            shadow = lookup.findVirtual(api, "isRenderingShadowPass", MethodType.methodType(boolean.class));
        } catch (Throwable ignored) {
            // Iris absent or a different API: vanilla path.
        }
        INSTANCE = instance;
        PACK_IN_USE = pack;
        SHADOW_PASS = shadow;
    }
    private IrisShaderState() {}

    static boolean packInUse() { return call(PACK_IN_USE); }
    static boolean renderingShadows() { return call(SHADOW_PASS); }

    private static boolean call(MethodHandle method) {
        if (INSTANCE == null || method == null) return false;
        try {
            return (boolean) method.invoke(INSTANCE.invoke());
        } catch (Throwable ignored) {
            return false;
        }
    }
}
