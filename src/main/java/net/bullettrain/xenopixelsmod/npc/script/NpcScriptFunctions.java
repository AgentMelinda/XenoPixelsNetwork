package net.bullettrain.xenopixelsmod.npc.script;

/**
 * Tells a script function apart from any other global value.
 *
 * <p>JSR-223 has no way to ask "is this callable"; Nashorn answers through its {@code JSObject}
 * mirror. That type lives in the nested Nashorn jar, so it is only touched here, and a missing jar
 * degrades to "any non-null value might be a function" rather than a linkage failure.
 */
final class NpcScriptFunctions {
    private NpcScriptFunctions() {}

    static boolean isFunction(Object value) {
        if (value == null) {
            return false;
        }
        try {
            return value instanceof org.openjdk.nashorn.api.scripting.JSObject js && js.isFunction();
        } catch (LinkageError missingNashorn) {
            return true;
        }
    }
}
