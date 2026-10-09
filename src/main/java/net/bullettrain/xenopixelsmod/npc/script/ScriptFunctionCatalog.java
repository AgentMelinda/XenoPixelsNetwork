package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.script.api.NativeXenoScriptApi;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld;

import java.lang.reflect.Modifier;
import java.util.List;
import java.util.TreeSet;

/** Functions come from the bound native classes, so the editor cannot advertise invented calls. */
public final class ScriptFunctionCatalog {
    private ScriptFunctionCatalog() {}

    public static List<String> calls() {
        var calls = new TreeSet<String>();
        add(calls, "npc", ScriptNpc.class);
        add(calls, "player", ScriptPlayer.class);
        add(calls, "world", ScriptWorld.class);
        add(calls, "event", net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent.class);
        add(calls, "XenoPixels", NativeXenoScriptApi.class);
        addTyped(calls, "npc.getAPI()", xenoapi.npcs.api.entity.ICustomNpc.class, 3, new java.util.HashSet<>());
        addTyped(calls, "world.getAPI()", xenoapi.npcs.api.IWorld.class, 3, new java.util.HashSet<>());
        addTyped(calls, "XenoAPI", xenoapi.npcs.api.NpcAPI.class, 3, new java.util.HashSet<>());
        addTyped(calls, "event.getAPI()", xenoapi.npcs.api.NpcAPI.class, 3, new java.util.HashSet<>());
        addTyped(calls, "player.getAPI()", xenoapi.npcs.api.entity.IPlayer.class, 3, new java.util.HashSet<>());
        calls.add("npc.getDisplay().getSize()");
        calls.add("npc.getDisplay().setSize(size)");
        return List.copyOf(calls);
    }

    /** Follow only declared API interfaces, never arbitrary implementation or Minecraft classes. */
    private static void addTyped(TreeSet<String> calls, String binding, Class<?> api, int depth,
                                 java.util.Set<Class<?>> ancestors) {
        if (!ancestors.add(api)) return;
        add(calls, binding, api);
        if (depth > 0) for (var method : api.getMethods()) {
            Class<?> result = method.getReturnType();
            if (safe(method) && method.getParameterCount() == 0 && result.isInterface()
                    && result.getPackageName().startsWith("xenoapi.npcs.api")) {
                addTyped(calls, binding + "." + method.getName() + "()", result, depth - 1,
                        new java.util.HashSet<>(ancestors));
            }
        }
    }

    private static boolean safe(java.lang.reflect.Method method) {
        if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())
                || method.isSynthetic() || method.isBridge() || method.getName().equals("unwrap")
                || method.getName().startsWith("getMC")) return false;
        if (rawType(method.getReturnType())) return false;
        for (Class<?> parameter : method.getParameterTypes()) if (rawType(parameter)) return false;
        return true;
    }

    private static boolean rawType(Class<?> type) {
        if (type.isArray()) return rawType(type.componentType());
        return type == Class.class || type.getPackageName().startsWith("net.minecraft")
                || type.getPackageName().startsWith("java.io")
                || type.getPackageName().startsWith("net.neoforged");
    }

    private static void add(TreeSet<String> calls, String binding, Class<?> api) {
        for (var method : api.getMethods()) {
            if (!safe(method)) continue;
            var arguments = new java.util.ArrayList<String>();
            for (var parameter : method.getParameters()) {
                String name = parameter.isNamePresent() ? parameter.getName()
                        : parameter.getType() == ScriptNpc.class ? "npc"
                        : parameter.getType() == ScriptPlayer.class ? "player"
                        : parameter.getType() == String.class ? "text" : "value";
                arguments.add(name + (arguments.contains(name) ? arguments.size() : ""));
            }
            calls.add(binding + "." + method.getName() + "(" + String.join(", ", arguments) + ")");
        }
    }
}
