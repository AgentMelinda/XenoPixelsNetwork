package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.compat.npc.NpcKiAttackDispatcher;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2026-09-28 bug: xenopixels_ecma_full.js's rangedLaunched hook ran a kiblast command, which
 * fired rangedLaunched again. In 1.21 a command issued inside a command is queued and runs after
 * the thread-local guard was cleared, so the chain ran to vanilla's 65536-command limit.
 */
class ScriptCommandLoopTest {
    @Test
    void theRangedLaunchedHookRunsAtMostOncePerNpcPerTick() {
        UUID npc = UUID.randomUUID();
        assertTrue(NpcKiAttackDispatcher.firstRangedHookThisTick(npc, 500L));
        assertFalse(NpcKiAttackDispatcher.firstRangedHookThisTick(npc, 500L),
                "a blast launched by a queued script command in the same tick does not re-run the hook");
        assertTrue(NpcKiAttackDispatcher.firstRangedHookThisTick(npc, 501L), "the next tick's launch does");
        assertTrue(NpcKiAttackDispatcher.firstRangedHookThisTick(UUID.randomUUID(), 500L), "other NPCs are independent");
    }

    @Test
    void scriptCommandsAreBudgetedPerNpcPerTick() {
        UUID npc = UUID.randomUUID();
        int allowed = 0;
        for (int i = 0; i < 1000; i++) {
            if (NpcScriptCommandBudget.tryConsume(npc, 700L)) allowed++;
        }
        assertEquals(NpcScriptCommandBudget.PER_TICK, allowed, "a looping script is cut off long before 65536");
        assertTrue(NpcScriptCommandBudget.tryConsume(npc, 701L), "the budget refills next tick");
        assertTrue(NpcScriptCommandBudget.tryConsume(UUID.randomUUID(), 700L), "per NPC");
    }
}
