package net.bullettrain.xenopixelsmod.compat.npc;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NpcScriptTickCompat {
    private static final Map<String, Method> HOOKS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private NpcScriptTickCompat() {}

    public static void run(Object npc, String hookClassName, String npcClassName) {
        if (npc == null) return;
        try {
            Method hook = HOOKS.computeIfAbsent(hookClassName, ignored -> resolve(hookClassName, npcClassName));
            if (hook != null) hook.invoke(null, npc);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            if (WARNED.add(hookClassName)) {
                XenoPixelsMod.LOGGER.warn("Could not accelerate {} scripted NPC ticks: {}",
                        hookClassName, failure.toString());
            }
        }
    }

    private static Method resolve(String hookClassName, String npcClassName) {
        try {
            Class<?> npcClass = Class.forName(npcClassName);
            return Class.forName(hookClassName).getMethod("onNPCTick", npcClass);
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }
}
