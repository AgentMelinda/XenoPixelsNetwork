package net.bullettrain.xenopixelsmod.client.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Debounced autosave: a change saves after a short quiet period, never while one is in flight. */
class AutoSaveSchedulerTest {
    @Test
    void aChangeSavesOnlyAfterTheQuietPeriod() {
        AutoSaveScheduler s = new AutoSaveScheduler(10);
        assertFalse(s.due(0), "nothing changed");
        s.markChanged(5);
        assertFalse(s.due(14), "still typing");
        s.markChanged(12);
        assertFalse(s.due(21), "each keystroke restarts the wait");
        assertTrue(s.due(22));
    }

    @Test
    void noSecondSaveWhileOneIsInFlightAndChangesDuringItAreKept() {
        AutoSaveScheduler s = new AutoSaveScheduler(10);
        s.markChanged(0);
        assertTrue(s.due(10));
        s.sent();
        assertFalse(s.due(100), "in flight");
        s.markChanged(101);                 // edited while the save travelled
        s.acknowledged();
        assertFalse(s.due(105));
        assertTrue(s.due(111), "the later edit still saves");
        assertTrue(s.pending());
    }

    @Test
    void anAcknowledgedSaveWithNoNewChangesIsIdle() {
        AutoSaveScheduler s = new AutoSaveScheduler(10);
        s.markChanged(0);
        s.sent();
        s.acknowledged();
        assertFalse(s.pending());
        assertFalse(s.due(1000));
    }

    @Test
    void aFailedSaveRetriesAfterTheQuietPeriod() {
        AutoSaveScheduler s = new AutoSaveScheduler(10);
        s.markChanged(0);
        s.sent();
        s.failed(50);
        assertFalse(s.due(55));
        assertTrue(s.due(60));
    }
}
