package net.bullettrain.xenopixelsmod.npc.script;

/**
 * The seam between this mod's script storage and the ECMAScript runtime.
 *
 * <p><b>Engine.</b> Java 15 removed Nashorn from the JDK, so this mod now nests standalone
 * {@code org.openjdk.nashorn:nashorn-core} in both distributions and builds it directly
 * ({@link NpcScriptEngines}). {@link NoScriptEngine} remains the answer when that jar fails to
 * link, and says so out loud rather than pretending a script ran.
 *
 * <p><b>Host access.</b> The bundled engine is built with {@code --no-java} and a class filter
 * that refuses every class, so scripts cannot reach {@code Java.type}, {@code Packages} or
 * reflection. What they can touch is the curated objects bound into their scope
 * ({@code NpcScriptBindings}). There is still no statement budget or timeout: a script that loops
 * forever stalls the server thread, exactly as it does under CustomNPCs. Authoring and running
 * scripts is therefore operator-only.
 *
 * <p><b>Threading.</b> Implementations are called from the server thread only. JSR-223 engines are
 * not thread-safe, so no fan-out happens here.
 */
public interface NpcScriptEngine {

    /** The reason every operation failed because no runtime exists; callers match on this string. */
    String NO_ENGINE = "no JavaScript engine bundled with this mod";

    /** Name for logs and for the editor's status row, e.g. {@code nashorn} or {@code none}. */
    String name();

    /** False only for {@link NoScriptEngine}; a present engine is assumed usable. */
    boolean available();

    /** Human-readable why-not, or null when {@link #available()}. */
    String unavailableReason();

    /**
     * Parse the source without running it.
     *
     * <p>Engines that implement {@code javax.script.Compilable} answer this properly. An engine that
     * cannot parse without executing reports that as a failure rather than running the script to
     * find out, so a syntax check never has side effects.
     *
     * @param label caller's name for the source, used in nothing but the failure text
     */
    NpcScriptResult compile(String source, String label);

    /** Evaluate the source with the given bindings and report the value of its last expression. */
    NpcScriptResult evaluate(String source, NpcScriptScope scope);

    /**
     * Evaluate the source, then call {@code entrypoint} as a global function with no arguments.
     *
     * <p>This is the shape every scripted hook wants: define {@code function onInteract()} in the
     * script, and the caller invokes it. When the entrypoint is blank this behaves like
     * {@link #evaluate}.
     */
    NpcScriptResult run(String source, NpcScriptScope scope, String entrypoint);

    /**
     * A script evaluated once into its own global scope, kept alive so hooks can be called on it
     * many times. This is how CustomNPCs/MyNPCs scripts behave: top-level {@code var}s persist
     * between {@code tick}, {@code interact} and the rest, and each script tab has its own globals.
     */
    interface Instance {
        /** True when the script evaluated without error and functions can be called on it. */
        boolean ok();

        /** The evaluation verdict (the error, when {@link #ok()} is false). */
        NpcScriptResult loadResult();

        /** True when the script defines a global function with this name. */
        boolean hasFunction(String name);

        /** Calls a global function with arguments; a missing function is a failure result. */
        NpcScriptResult call(String name, Object... args);
    }

    /** Evaluates {@code source} into a fresh, private global scope pre-filled with {@code scope}. */
    Instance instantiate(String source, NpcScriptScope scope);
}
