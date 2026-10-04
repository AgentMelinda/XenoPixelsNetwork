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
        add(calls, "XenoPixels", NativeXenoScriptApi.class);
        calls.add("npc.getDisplay().getSize()");
        calls.add("npc.getDisplay().setSize(size)");
        return List.copyOf(calls);
    }

    private static void add(TreeSet<String> calls, String binding, Class<?> api) {
        for (var method : api.getMethods()) {
            if (method.getDeclaringClass() == Object.class || Modifier.isStatic(method.getModifiers())
                    || method.isSynthetic() || method.isBridge() || method.getName().equals("unwrap")) continue;
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
