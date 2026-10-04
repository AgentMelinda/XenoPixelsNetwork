package net.bullettrain.xenopixelsmod.npc.brain.v6;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XenoNpcSchedulerTest {
    @Test
    void selectsHighestPriorityReadyAction() {
        XenoNpcScheduler scheduler = new XenoNpcScheduler(4);
        scheduler.register(new XenoNpcDecision(XenoNpcActionKind.IDLE_LOOK, 10, 20), tick -> true);
        scheduler.register(new XenoNpcDecision(XenoNpcActionKind.COMBAT_APPROACH, 1, 5), tick -> true);
        assertEquals(XenoNpcActionKind.COMBAT_APPROACH, scheduler.select(0));
        assertEquals(XenoNpcActionKind.IDLE_LOOK, scheduler.select(0));
    }

    @Test
    void respectsCooldownAndBudget() {
        XenoNpcScheduler scheduler = new XenoNpcScheduler(1);
        scheduler.register(new XenoNpcDecision(XenoNpcActionKind.FOLLOW_OWNER, 1, 10), tick -> true);
        assertEquals(XenoNpcActionKind.FOLLOW_OWNER, scheduler.select(0));
        assertNull(scheduler.select(0));
        assertEquals(XenoNpcActionKind.FOLLOW_OWNER, scheduler.select(10));
    }

    @Test
    void skipsUnmetConditions() {
        XenoNpcScheduler scheduler = new XenoNpcScheduler(4);
        scheduler.register(new XenoNpcDecision(XenoNpcActionKind.RETURN_ANCHOR, 1, 0), tick -> false);
        assertNull(scheduler.select(0));
        assertEquals(1, scheduler.lastSkipped());
    }
}