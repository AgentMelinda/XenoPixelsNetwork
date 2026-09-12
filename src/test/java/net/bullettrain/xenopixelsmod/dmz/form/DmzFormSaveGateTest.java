package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormSaveGateTest {
    private static final long DEBOUNCE = 600L;

    @Test
    void nothingIsSentUntilTypingStops() {
        DmzFormSaveGate gate = new DmzFormSaveGate(DEBOUNCE);
        assertFalse(gate.shouldSend(1_000L, false, true), "an untouched editor sends nothing");

        gate.touch(1_000L);
        assertFalse(gate.shouldSend(1_100L, false, true));
        assertTrue(gate.shouldSend(1_600L, false, true));
    }

    @Test
    void keystrokesInsideTheWindowCoalesceIntoOneSave() {
        DmzFormSaveGate gate = new DmzFormSaveGate(DEBOUNCE);
        gate.touch(1_000L);
        gate.touch(1_200L);
        gate.touch(1_500L);
        assertFalse(gate.shouldSend(1_900L, false, true), "the window restarts on each keystroke");
        assertTrue(gate.shouldSend(2_100L, false, true));

        gate.sent();
        assertFalse(gate.dirty());
        assertFalse(gate.shouldSend(9_000L, false, true), "a sent edit is not sent twice");
    }

    @Test
    void noSecondSaveGoesOutWhileOneIsStillInFlight() {
        DmzFormSaveGate gate = new DmzFormSaveGate(DEBOUNCE);
        gate.touch(1_000L);
        assertFalse(gate.shouldSend(2_000L, true, true),
                "a concurrent save would carry a stale revision");
        // The edit is still pending and goes out once the server answers.
        assertTrue(gate.dirty());
        assertTrue(gate.shouldSend(2_000L, false, true));
    }

    @Test
    void draftsThatDoNotExistOnTheServerYetAreNeverAutoSaved() {
        DmzFormSaveGate gate = new DmzFormSaveGate(DEBOUNCE);
        gate.touch(1_000L);
        assertFalse(gate.shouldSend(5_000L, false, false));
        assertTrue(gate.shouldSend(5_000L, false, true));
    }

    @Test
    void anEditMadeWhileASaveIsInFlightSurvivesTheSend() {
        DmzFormSaveGate gate = new DmzFormSaveGate(DEBOUNCE);
        gate.touch(1_000L);
        gate.sent();
        gate.touch(1_050L);
        assertTrue(gate.dirty());
        assertTrue(gate.shouldSend(1_700L, false, true));
    }
}
