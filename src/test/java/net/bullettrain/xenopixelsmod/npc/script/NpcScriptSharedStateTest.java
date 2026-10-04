package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** State handed to XenoAPI adapters before a script host exists has exactly one owner. */
class NpcScriptSharedStateTest {
    private final UUID npc = UUID.randomUUID();

    @AfterEach
    void cleanup() {
        NpcScriptHost.forget(npc);
    }

    @Test
    void stateBeforeAnyHostIsCreatedOnceAndReused() {
        AtomicInteger loads = new AtomicInteger();
        var first = NpcScriptHost.sharedState(npc, () -> { loads.incrementAndGet(); return new ScriptTimers(); });
        first.temp().put("k", 1);
        var second = NpcScriptHost.sharedState(npc, () -> { loads.incrementAndGet(); return new ScriptTimers(); });
        assertSame(first.temp(), second.temp());
        assertSame(first.timers(), second.timers());
        assertEquals(1, loads.get(), "saved timers are loaded once");
    }

    @Test
    void firstHostBuildTakesThePendingStateSoItHasOneOwner() {
        var pending = NpcScriptHost.sharedState(npc, ScriptTimers::new);
        assertSame(pending, NpcScriptHost.takePending(npc));
        assertNull(NpcScriptHost.takePending(npc), "pending state is handed over only once");
    }

    @Test
    void forgetAndServerStopDropPendingState() {
        var before = NpcScriptHost.sharedState(npc, ScriptTimers::new);
        NpcScriptHost.forget(npc);
        assertNotSame(before, NpcScriptHost.sharedState(npc, ScriptTimers::new));
        var again = NpcScriptHost.sharedState(npc, ScriptTimers::new);
        NpcScriptHost.clearAll();
        assertNotSame(again, NpcScriptHost.sharedState(npc, ScriptTimers::new));
    }
}
