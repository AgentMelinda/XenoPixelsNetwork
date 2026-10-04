package net.bullettrain.xenopixelsmod.npc.script;

/**
 * The engine that is present when none is.
 *
 * <p>This exists so the answer to "did my script run?" is never silence. Every operation fails with
 * {@link NpcScriptEngine#NO_ENGINE}, which {@link NpcScriptResult#engineMissing()} turns into a
 * distinct state the editor can word as "this server has no script runtime" instead of making the
 * author hunt a syntax error that is not there.
 *
 * <p>It is also the reason the feature can ship at all: the storage, the editor, and the run path
 * are all real and testable without an engine on the classpath, so bundling one stays a separate
 * decision from having somewhere to put scripts.
 */
public final class NoScriptEngine implements NpcScriptEngine {
    public static final String NAME = "none";

    private final String reason;

    public NoScriptEngine() {
        this(NO_ENGINE);
    }

    public NoScriptEngine(String reason) {
        this.reason = reason == null || reason.isBlank() ? NO_ENGINE : reason;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public String unavailableReason() {
        return reason;
    }

    @Override
    public NpcScriptResult compile(String source, String label) {
        return NpcScriptResult.failure(NAME, reason, -1);
    }

    @Override
    public NpcScriptResult evaluate(String source, NpcScriptScope scope) {
        return NpcScriptResult.failure(NAME, reason, -1);
    }

    @Override
    public NpcScriptResult run(String source, NpcScriptScope scope, String entrypoint) {
        return NpcScriptResult.failure(NAME, reason, -1);
    }

    @Override
    public Instance instantiate(String source, NpcScriptScope scope) {
        NpcScriptResult failure = NpcScriptResult.failure(NAME, reason, -1);
        return new Instance() {
            @Override
            public boolean ok() {
                return false;
            }

            @Override
            public NpcScriptResult loadResult() {
                return failure;
            }

            @Override
            public boolean hasFunction(String name) {
                return false;
            }

            @Override
            public NpcScriptResult call(String name, Object... args) {
                return failure;
            }
        };
    }
}
