package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.store.XenoNpcScripts;

import javax.annotation.Nullable;

/**
 * Runs a stored script and reports what happened, without ever throwing at the caller.
 *
 * <p>This is the runtime consumer that makes {@code SCRIPTS} a live store category rather than a
 * folder of text: an entry only matters because something resolves it and hands it to an engine.
 *
 * <p><b>Who may call this.</b> Every present call site is gated to permission level 2, which is the
 * same bar as writing the script in the first place - a non-operator can neither author nor run one.
 * That is not a sandbox, and it is worth being precise about the difference: op-gating means nobody
 * with less authority than a server console gains anything, while a sandbox would mean somebody
 * <em>with</em> that authority could not use it to bring the server down. The second does not exist
 * here, and cannot be built on top of a JSR-223 engine without engine-specific host-access
 * configuration. Which is exactly why nothing in this mod runs a script on a non-op's behalf, or on
 * an NPC's own schedule, yet - see the open decisions in {@link NpcScriptEngine}.
 *
 * <p><b>No timeout.</b> Neither Nashorn nor GraalJS can be interrupted mid-script through plain
 * JSR-223, so a loop that never ends is a server thread that never returns. Op-only execution keeps
 * that in the hands of someone who could stop the server anyway; it does not make it safe to expose
 * to authored content.
 */
public final class XenoScriptRunner {

    private XenoScriptRunner() {
    }

    /**
     * A run's two outputs: what the script returned, and what it printed.
     *
     * @param result the engine's verdict
     * @param output lines the script passed to {@code log.line(...)}, newline-joined
     */
    public record Outcome(NpcScriptResult result, String output) {

        public boolean ok() {
            return result.ok();
        }

        /** True when nothing ran because this server has no engine. */
        public boolean engineMissing() {
            return result.engineMissing();
        }

        /** One line for the editor's status row. */
        public String describe() {
            return result.describe();
        }

        /** Multi-line text for the editor's output pane: printed lines, then the verdict. */
        public String transcript() {
            if (output.isBlank()) {
                return describe();
            }
            return output + System.lineSeparator() + describe();
        }
    }

    /**
     * Runs one stored script by id.
     *
     * @param scriptId   the world-store script id, from an NPC's {@code ScriptId} or a picker
     * @param entrypoint global function to call after evaluating, or blank to just evaluate
     * @param extra      caller's own bindings; {@code log} and {@code script} are added on top
     */
    public static Outcome run(@Nullable String scriptId, String entrypoint, NpcScriptScope extra) {
        XenoNpcScripts.Script script = XenoNpcScripts.load(scriptId);
        if (script == null) {
            return unavailable("no script stored under id " + safe(scriptId));
        }
        if (!script.enabled()) {
            return unavailable("script " + script.id() + " is disabled");
        }
        return runText(script, entrypoint, extra);
    }

    /** Runs the script bound to a profile, or reports that the NPC has none. */
    public static Outcome runForProfile(@Nullable String profileScriptId, String entrypoint,
                                        NpcScriptScope extra) {
        if (profileScriptId == null || profileScriptId.isBlank()) {
            return unavailable("this NPC has no script selected");
        }
        return run(profileScriptId, entrypoint, extra);
    }

    /**
     * Runs script text that is already in hand - the editor's "Run" button, which must be able to
     * try unsaved changes before committing them to the store.
     */
    public static Outcome runText(@Nullable XenoNpcScripts.Script script, String entrypoint,
                                  @Nullable NpcScriptScope extra) {
        NpcScriptLog.Collector log = new NpcScriptLog.Collector();
        if (script == null || script.script().isBlank()) {
            return new Outcome(NpcScriptResult.failure(NoScriptEngine.NAME,
                    "script has no source text", -1), log.joined());
        }
        NpcScriptEngine engine = NpcScriptEngines.forLanguage(script.language());
        if (!engine.available()) {
            return new Outcome(engine.evaluate(script.script(), NpcScriptScope.EMPTY), log.joined());
        }
        NpcScriptResult result = engine.run(script.script(), scopeFor(script, log, extra), entrypoint);
        return new Outcome(result, log.joined());
    }

    /** The bindings every run gets, with the caller's own values folded in first. */
    private static NpcScriptScope scopeFor(XenoNpcScripts.Script script, NpcScriptLog log,
                                           @Nullable NpcScriptScope extra) {
        NpcScriptScope.Builder builder = NpcScriptScope.builder();
        if (extra != null) {
            for (java.util.Map.Entry<String, Object> binding : extra.asMap().entrySet()) {
                builder.put(binding.getKey(), binding.getValue());
            }
        }
        return builder
                .put("log", log)
                .putString("script", script.id())
                .putString("scriptName", script.name())
                .build();
    }

    private static Outcome unavailable(String reason) {
        return new Outcome(NpcScriptResult.failure(NoScriptEngine.NAME, reason, -1), "");
    }

    private static String safe(@Nullable String value) {
        return value == null ? "" : value;
    }
}
