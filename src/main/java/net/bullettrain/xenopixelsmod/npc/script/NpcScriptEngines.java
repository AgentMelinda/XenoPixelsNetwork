package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.XenoPixelsMod;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.ServiceConfigurationError;

/**
 * Finds the script engine this server has, once, and hands out the same instance.
 *
 * <p>Resolution order, first hit wins:
 * <ol>
 *   <li>the nested standalone Nashorn, built by {@link NashornSandbox} with {@code --no-java} and a
 *       class filter that refuses every host class;</li>
 *   <li>JSR-223 service lookup by name, for a pack that ships some other engine - logged as not
 *       sandboxed.</li>
 * </ol>
 *
 * <p>If both miss, {@link NoScriptEngine} is returned. Callers never see null.
 *
 * <p>{@value #NASHORN_ARGS} may add further Nashorn options (for example {@code --strict}); the
 * sandbox options are always applied and cannot be removed through it.
 */
public final class NpcScriptEngines {

    /** System property forwarded to Nashorn's factory as engine arguments. */
    public static final String NASHORN_ARGS = "xenopixels.nashorn.args";

    static final String NASHORN_FACTORY = "org.openjdk.nashorn.api.scripting.NashornScriptEngineFactory";

    /** Names engines answer to; My NPCs asks for "ecmascript", so that is first. */
    private static final String[] ENGINE_NAMES = {
            "ecmascript", "javascript", "js", "nashorn", "graal.js", "graaljs", "chakri"
    };

    private static volatile NpcScriptEngine cached;

    private NpcScriptEngines() {
    }

    /** The engine to run scripts with; {@link NoScriptEngine} when the pack ships none. */
    public static NpcScriptEngine current() {
        NpcScriptEngine engine = cached;
        if (engine == null) {
            synchronized (NpcScriptEngines.class) {
                engine = cached;
                if (engine == null) {
                    engine = discover();
                    cached = engine;
                    logChoice(engine);
                }
            }
        }
        return engine;
    }

    /** One line for a status command: which engine answered and what it is called. */
    public static String describe() {
        NpcScriptEngine engine = current();
        return engine.available()
                ? "script engine: " + engine.name()
                : "script engine: none (" + engine.unavailableReason() + ")";
    }

    /**
     * Drops the cached engine so the next {@link #current()} searches again.
     *
     * <p>For tests, and for a future "reload scripts" admin action. Resolving is cheap but not free
     * - service lookup scans the classpath - which is why it is cached in the first place.
     */
    public static void reset() {
        cached = null;
    }

    /** Test seam: force the answer, including forcing {@link NoScriptEngine}. */
    public static void useForTesting(NpcScriptEngine engine) {
        cached = engine;
    }

    private static NpcScriptEngine discover() {
        // The bundled engine first, sandboxed. Only if it fails to link (a repackaged jar, a
        // stripped server) is JSR-223 service lookup consulted - and an engine found that way is
        // whatever the pack installed, with its own defaults, which is logged.
        ScriptEngine bundled = NashornSandbox.create(classLoader());
        if (bundled != null) {
            return new Jsr223ScriptEngine(bundled, "nashorn");
        }
        ScriptEngineManager manager = new ScriptEngineManager(classLoader());
        for (String name : ENGINE_NAMES) {
            ScriptEngine engine = lookup(manager, name);
            if (engine != null) {
                XenoPixelsMod.LOGGER.warn("ModNetwork.script: bundled Nashorn unavailable; using the "
                        + "pack's {} engine, which is NOT sandboxed", name);
                return new Jsr223ScriptEngine(engine, name);
            }
        }
        return new NoScriptEngine();
    }

    /**
     * The engine to use for one script's declared language.
     *
     * <p>Every language this mod can serve is an ECMAScript one, so a script that asks for
     * {@code lua} or {@code kotlin} gets a refusal naming its language rather than the generic
     * "no engine" answer - those are different problems for the operator, and only one of them is
     * fixed by adding a jar.
     */
    public static NpcScriptEngine forLanguage(String language) {
        String wanted = language == null ? "" : language.trim().toLowerCase(java.util.Locale.ROOT);
        if (wanted.isEmpty() || ECMASCRIPT_NAMES.contains(wanted)) {
            return current();
        }
        return new NoScriptEngine("no engine bundled for language " + wanted);
    }

    /** Language names that mean "give us whatever ECMAScript engine is here". */
    private static final java.util.Set<String> ECMASCRIPT_NAMES = java.util.Set.of(
            "ecmascript", "javascript", "js", "nashorn", "graal.js", "graaljs", "es5", "es6");

    private static ScriptEngine lookup(ScriptEngineManager manager, String name) {
        try {
            return manager.getEngineByName(name);
        } catch (RuntimeException | ServiceConfigurationError e) {
            // A broken engine jar on the classpath must not take the server down; treat it as absent.
            XenoPixelsMod.LOGGER.warn("ModNetwork.script: engine name {} could not be probed: {}",
                    name, e.toString());
            return null;
        }
    }

    private static ClassLoader classLoader() {
        ClassLoader loader = NpcScriptEngines.class.getClassLoader();
        return loader == null ? ClassLoader.getSystemClassLoader() : loader;
    }

    private static void logChoice(NpcScriptEngine engine) {
        if (engine.available()) {
            XenoPixelsMod.LOGGER.info("ModNetwork.script: using {} ({})", engine.name(),
                    "nashorn".equals(engine.name()) ? "--no-java, all host classes filtered"
                            : "pack-provided, not sandboxed");
            return;
        }
        XenoPixelsMod.LOGGER.info("ModNetwork.script: no engine answered any JSR-223 name lookup and "
                + "{} is not on the classpath; NPC scripts are stored but cannot run",
                NASHORN_FACTORY);
    }
}
