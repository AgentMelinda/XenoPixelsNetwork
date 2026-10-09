package net.bullettrain.xenopixelsmod.npc.script;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Transient ownership of projectile callbacks by the exact currently executing script tab. */
public final class ProjectileScriptContext {
    private static final ThreadLocal<Token> CURRENT = new ThreadLocal<>();
    private static final ThreadLocal<Set<String>> ACTIVE = ThreadLocal.withInitial(HashSet::new);
    private ProjectileScriptContext() {}

    public static final class Token {
        private WeakReference<NpcScriptEngine.Instance> instance = new WeakReference<>(null);
        private final BooleanSupplier admissible;
        private final Consumer<String> errors;
        private final WeakReference<Object> level;
        private final boolean worldBound;
        private int failures;
        public Token(BooleanSupplier admissible, Consumer<String> errors) {
            this(admissible, errors, null);
        }
        public Token(BooleanSupplier admissible, Consumer<String> errors, Object level) {
            this.admissible = admissible;
            this.errors = errors;
            this.level = new WeakReference<>(level);
            worldBound = level != null;
        }
        public boolean matchesLevel(Object candidate) { return !worldBound || level.get() == candidate; }
        public void bind(NpcScriptEngine.Instance instance) { this.instance = new WeakReference<>(instance); }
        NpcScriptEngine.Instance boundInstance() { return instance.get(); }
        public boolean valid() {
            NpcScriptEngine.Instance tab = instance.get();
            return failures < 3 && tab != null && tab.ok() && admissible.getAsBoolean();
        }
        public void deliver(UUID projectile, String hook, Object event) {
            NpcScriptEngine.Instance tab = instance.get();
            if (!valid() || tab == null || !tab.hasFunction(hook)) return;
            String key = projectile + "|" + hook;
            if (!ACTIVE.get().add(key)) return;
            try (Scope ignored = enter(this)) {
                NpcScriptResult result = tab.call(hook, event);
                if (!result.ok()) { failures++; errors.accept(hook + ": " + result.describe()); }
            } finally {
                ACTIVE.get().remove(key);
                if (ACTIVE.get().isEmpty()) ACTIVE.remove();
            }
        }
    }
    public static Token capture() {
        Token token = CURRENT.get();
        if (token == null) throw new IllegalStateException("enableEvents requires an executing NPC script tab");
        return token;
    }
    public static Token captureOptional() { return CURRENT.get(); }
    public static Scope enter(Token token) {
        Token previous = CURRENT.get();
        CURRENT.set(token);
        return new Scope(previous);
    }
    public static final class Scope implements AutoCloseable {
        private final Token previous;
        private Scope(Token previous) { this.previous = previous; }
        @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
    }
}
