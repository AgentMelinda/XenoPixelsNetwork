package net.bullettrain.xenopixelsmod.npc.script;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import static org.junit.jupiter.api.Assertions.*;

class ProjectileScriptContextTest {
    public static final class CaptureProbe {
        ProjectileScriptContext.Token captured;
        public void capture() { captured = ProjectileScriptContext.capture(); }
    }
    private static final class Tab implements NpcScriptEngine.Instance {
        int calls;
        Object seen;
        boolean ok = true;
        boolean fails;
        BiConsumer<String, Object> callback = (hook, event) -> {};
        public boolean ok() { return ok; }
        public NpcScriptResult loadResult() { return NpcScriptResult.success("test", null); }
        public boolean hasFunction(String hook) { return true; }
        public NpcScriptResult call(String hook, Object... args) {
            calls++; seen = args[0]; callback.accept(hook, seen);
            return !fails ? NpcScriptResult.success("test", null) : NpcScriptResult.failure("test", "broken", -1);
        }
    }
    @Test void exactTabReceivesImpactObjectWithoutBroadcast() {
        Tab first = new Tab(), other = new Tab();
        var token = new ProjectileScriptContext.Token(() -> true, error -> fail(error));
        token.bind(first);
        var impact = new xenoapi.npcs.api.event.ProjectileEvent.ImpactEvent(null, 1, "block");
        token.deliver(UUID.randomUUID(), "projectileImpact", impact);
        assertEquals(1, first.calls); assertEquals(0, other.calls); assertSame(impact, first.seen);
        assertEquals(1, impact.type); assertEquals("block", impact.target);
        assertFalse(impact instanceof net.neoforged.bus.api.ICancellableEvent);
    }
    @Test void nestedContextsRestoreAfterFailureAndCaptureRequiresContext() {
        var first = new ProjectileScriptContext.Token(() -> true, error -> {});
        var second = new ProjectileScriptContext.Token(() -> true, error -> {});
        assertThrows(IllegalStateException.class, ProjectileScriptContext::capture);
        try (var outer = ProjectileScriptContext.enter(first)) {
            assertSame(first, ProjectileScriptContext.capture());
            assertThrows(IllegalArgumentException.class, () -> {
                try (var inner = ProjectileScriptContext.enter(second)) {
                    assertSame(second, ProjectileScriptContext.capture());
                    throw new IllegalArgumentException();
                }
            });
            assertSame(first, ProjectileScriptContext.capture());
        }
        assertThrows(IllegalStateException.class, ProjectileScriptContext::capture);
    }
    @Test void reloadInvalidatesExistingBindingAndFailedLoadCannotDeliver() {
        AtomicBoolean current = new AtomicBoolean(true);
        Tab tab = new Tab();
        var token = new ProjectileScriptContext.Token(current::get, error -> fail(error));
        assertFalse(token.valid()); token.bind(tab); assertTrue(token.valid());
        current.set(false); token.deliver(UUID.randomUUID(), "projectileTick", new Object());
        assertEquals(0, tab.calls);
        current.set(true); tab.ok = false; assertFalse(token.valid());
    }
    @Test void recursiveSameProjectileIsSuppressedButAnotherProjectileCanRun() {
        Tab tab = new Tab();
        var token = new ProjectileScriptContext.Token(() -> true, error -> fail(error));
        token.bind(tab);
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        tab.callback = (hook, event) -> {
            assertSame(token, ProjectileScriptContext.capture());
            token.deliver(first, hook, event);
            if (tab.calls == 1) token.deliver(second, hook, event);
        };
        token.deliver(first, "projectileTick", new Object());
        assertEquals(2, tab.calls);
        assertThrows(IllegalStateException.class, ProjectileScriptContext::capture);
    }
    @Test void realNashornReceivesTypedProjectileFields() {
        var tab = NpcScriptEngines.current().instantiate(
                "var seen = ''; function projectileImpact(e) { seen = e.type + ':' + e.target; } function read() { return seen; }",
                NpcScriptScope.EMPTY);
        assertTrue(tab.ok(), tab.loadResult().describe());
        var token = new ProjectileScriptContext.Token(() -> true, error -> fail(error));
        token.bind(tab);
        token.deliver(UUID.randomUUID(), "projectileImpact",
                new xenoapi.npcs.api.event.ProjectileEvent.ImpactEvent(null, 0, "target"));
        assertEquals("0:target", tab.call("read").value());
    }
    @Test void threeFailuresDetachAndWorldBindingRejectsAnotherLevel() {
        Tab tab = new Tab(); tab.fails = true;
        Object level = new Object();
        int[] errors = {0};
        var token = new ProjectileScriptContext.Token(() -> true, error -> errors[0]++, level);
        token.bind(tab);
        assertTrue(token.matchesLevel(level)); assertFalse(token.matchesLevel(new Object()));
        for (int i = 0; i < 5; i++) token.deliver(UUID.randomUUID(), "projectileTick", new Object());
        assertEquals(3, tab.calls); assertEquals(3, errors[0]); assertFalse(token.valid());
    }
    @Test void fingerprintPreservesTabAndLoadedScriptBoundaries() {
        var first = new NpcScriptContainer();
        first.setTabs(java.util.List.of(new NpcScriptContainer.Tab("b", java.util.List.of("a")),
                new NpcScriptContainer.Tab("c", java.util.List.of())));
        var second = new NpcScriptContainer();
        second.setTabs(java.util.List.of(new NpcScriptContainer.Tab("a", java.util.List.of()),
                new NpcScriptContainer.Tab("c", java.util.List.of("b"))));
        assertEquals(first.referencedIds(), second.referencedIds());
        assertNotEquals(NpcScriptHost.fingerprint(first), NpcScriptHost.fingerprint(second));
    }
    @Test void topLevelNashornCaptureBindsOnlyAfterSuccessfulEvaluation() {
        var token = new ProjectileScriptContext.Token(() -> true, error -> fail(error));
        CaptureProbe probe = new CaptureProbe();
        NpcScriptEngine.Instance tab;
        try (var scope = ProjectileScriptContext.enter(token)) {
            tab = NpcScriptEngines.current().instantiate(
                    "probe.capture(); var count = 0; function projectileTick(e) { count++; } function read() { return count; }",
                    NpcScriptScope.builder().put("probe", probe).build());
            assertSame(token, probe.captured); assertFalse(token.valid());
        }
        assertTrue(tab.ok(), tab.loadResult().describe()); token.bind(tab);
        token.deliver(UUID.randomUUID(), "projectileTick", new Object());
        assertEquals(1.0, Double.parseDouble(tab.call("read").value()));
    }
    @Test void unexpectedCallbackExceptionRestoresContextAndReentryGuard() {
        Tab tab = new Tab();
        var token = new ProjectileScriptContext.Token(() -> true, error -> {});
        token.bind(tab); UUID projectile = UUID.randomUUID();
        tab.callback = (hook, event) -> { throw new IllegalArgumentException("callback"); };
        assertThrows(IllegalArgumentException.class, () -> token.deliver(projectile, "projectileTick", new Object()));
        assertThrows(IllegalStateException.class, ProjectileScriptContext::capture);
        tab.callback = (hook, event) -> {};
        token.deliver(projectile, "projectileTick", new Object());
        assertEquals(2, tab.calls);
    }
}
