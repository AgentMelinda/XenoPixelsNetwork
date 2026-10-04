package net.bullettrain.xenopixelsmod.npc.script;

import javax.script.Bindings;
import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.Invocable;
import javax.script.ScriptContext;
import javax.script.SimpleScriptContext;
import javax.script.ScriptException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Adapter over a JSR-223 {@code javax.script.ScriptEngine} - Nashorn, GraalJS, or anything else a
 * pack registers.
 *
 * <p>Only the JDK's own {@code java.scripting} module is compiled against, so this class loads and
 * links whether or not an engine jar is present; the engine arrives as an already-constructed
 * object. That is the whole trick behind shipping scripting support without a scripting dependency.
 *
 * <p>Each evaluation gets its own {@link ScriptContext} with fresh engine-scope bindings, so two
 * scripts never share globals. The engine object itself is shared and is locked around calls that
 * swap its context.
 */
public final class Jsr223ScriptEngine implements NpcScriptEngine {

    /** A global function name a script may be asked to call: an identifier, nothing else. */
    private static final Pattern ENTRYPOINT = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{0,63}");

    private final javax.script.ScriptEngine engine;
    private final String name;

    public Jsr223ScriptEngine(javax.script.ScriptEngine engine, String name) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.name = name == null || name.isBlank() ? defaultName(engine) : name;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String unavailableReason() {
        return null;
    }

    @Override
    public NpcScriptResult compile(String source, String label) {
        if (source == null || source.isBlank()) {
            return NpcScriptResult.failure(name, "nothing to compile", -1);
        }
        if (!(engine instanceof Compilable compilable)) {
            // Running the script to discover whether it parses would be a side effect the caller
            // asked us not to have, so the honest answer is that this engine cannot check.
            return NpcScriptResult.failure(name,
                    label + ": " + name + " cannot check syntax without running the script", -1);
        }
        try {
            CompiledScript compiled = compilable.compile(source);
            return compiled == null
                    ? NpcScriptResult.failure(name, label + ": engine returned no compiled script", -1)
                    : NpcScriptResult.success(name, "syntax ok");
        } catch (ScriptException e) {
            return NpcScriptResult.failure(name, e.getMessage(), e.getLineNumber());
        } catch (RuntimeException e) {
            return NpcScriptResult.failure(name, describe(e), -1);
        }
    }

    @Override
    public NpcScriptResult evaluate(String source, NpcScriptScope scope) {
        if (source == null || source.isBlank()) {
            return NpcScriptResult.failure(name, "nothing to run", -1);
        }
        ScriptContext context = contextFor(scope);
        try {
            return NpcScriptResult.success(name, engine.eval(source, context));
        } catch (ScriptException e) {
            return NpcScriptResult.failure(name, e.getMessage(), e.getLineNumber());
        } catch (RuntimeException | StackOverflowError e) {
            return NpcScriptResult.failure(name, describe(e), -1);
        }
    }

    @Override
    public NpcScriptResult run(String source, NpcScriptScope scope, String entrypoint) {
        if (entrypoint == null || entrypoint.isBlank()) {
            return evaluate(source, scope);
        }
        if (!ENTRYPOINT.matcher(entrypoint).matches()) {
            return NpcScriptResult.failure(name, "not a function name: " + entrypoint, -1);
        }
        Instance instance = instantiate(source, scope);
        if (!instance.ok()) {
            return instance.loadResult();
        }
        return instance.call(entrypoint);
    }

    @Override
    public Instance instantiate(String source, NpcScriptScope scope) {
        ScriptContext context = contextFor(scope);
        NpcScriptResult loaded;
        if (source == null || source.isBlank()) {
            loaded = NpcScriptResult.failure(name, "nothing to run", -1);
        } else {
            try {
                loaded = NpcScriptResult.success(name, engine.eval(source, context));
            } catch (ScriptException e) {
                loaded = NpcScriptResult.failure(name, e.getMessage(), e.getLineNumber());
            } catch (RuntimeException | StackOverflowError e) {
                loaded = NpcScriptResult.failure(name, describe(e), -1);
            }
        }
        return new ContextInstance(context, loaded);
    }

    /**
     * One script's private globals. {@code invokeFunction} resolves names in the engine's
     * <em>current</em> context, not in the bindings a script was evaluated with - the earlier
     * version evaluated into fresh bindings and then invoked against the default context, so a
     * named entrypoint was never found. Swapping the context in for the call (under the engine's
     * lock, since JSR-223 engines are not thread-safe) is the portable fix.
     */
    private final class ContextInstance implements Instance {
        private final ScriptContext context;
        private final NpcScriptResult loaded;

        ContextInstance(ScriptContext context, NpcScriptResult loaded) {
            this.context = context;
            this.loaded = loaded;
        }

        @Override
        public boolean ok() {
            return loaded.ok();
        }

        @Override
        public NpcScriptResult loadResult() {
            return loaded;
        }

        @Override
        public boolean hasFunction(String function) {
            if (!loaded.ok() || function == null || !ENTRYPOINT.matcher(function).matches()) {
                return false;
            }
            Object value = context.getBindings(ScriptContext.ENGINE_SCOPE).get(function);
            return NpcScriptFunctions.isFunction(value);
        }

        @Override
        public NpcScriptResult call(String function, Object... args) {
            if (!loaded.ok()) {
                return loaded;
            }
            if (function == null || !ENTRYPOINT.matcher(function).matches()) {
                return NpcScriptResult.failure(name, "not a function name: " + function, -1);
            }
            if (!(engine instanceof Invocable invocable)) {
                return NpcScriptResult.failure(name, name + " cannot call a function by name", -1);
            }
            synchronized (engine) {
                ScriptContext previous = engine.getContext();
                engine.setContext(context);
                try {
                    return NpcScriptResult.success(name,
                            invocable.invokeFunction(function, args == null ? new Object[0] : args));
                } catch (ScriptException e) {
                    return NpcScriptResult.failure(name, e.getMessage(), e.getLineNumber());
                } catch (NoSuchMethodException e) {
                    return NpcScriptResult.failure(name, "no function " + function + "() in the script", -1);
                } catch (RuntimeException | StackOverflowError e) {
                    return NpcScriptResult.failure(name, describe(e), -1);
                } finally {
                    engine.setContext(previous);
                }
            }
        }
    }

    private ScriptContext contextFor(NpcScriptScope scope) {
        SimpleScriptContext context = new SimpleScriptContext();
        Bindings bindings = engine.createBindings();
        if (scope != null) {
            bindings.putAll(scope.asMap());
        }
        context.setBindings(bindings, ScriptContext.ENGINE_SCOPE);
        return context;
    }

    private static String describe(Throwable e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String defaultName(javax.script.ScriptEngine engine) {
        try {
            String shortName = engine.getFactory().getEngineName();
            return shortName == null || shortName.isBlank() ? "js" : shortName.toLowerCase(java.util.Locale.ROOT);
        } catch (RuntimeException e) {
            return "js";
        }
    }
}
