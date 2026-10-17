package net.bullettrain.xenopixelsmod.space.dragonball;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBallDiagnosticLatchTest {
    @Test void repeatedSignatureReportsOnlyOnceUntilItChanges() {
        var latch = new DragonBallDiagnosticLatch();
        assertTrue(latch.shouldReport("scan", "unreadable:4"));
        assertFalse(latch.shouldReport("scan", "unreadable:4"));
        assertTrue(latch.shouldReport("scan", "unreadable:5"));
        assertFalse(latch.shouldReport("scan", "unreadable:5"));
    }

    @Test void clearingARecoveredConditionAllowsItToBeReportedAgain() {
        var latch = new DragonBallDiagnosticLatch();
        assertTrue(latch.shouldReport("slot:2", "duplicate:a:b"));
        assertTrue(latch.clear("slot:2"));
        assertFalse(latch.clear("slot:2"));
        assertTrue(latch.shouldReport("slot:2", "duplicate:a:b"));
    }

    @Test void keysHaveIndependentDiagnosticLifecycles() {
        var latch = new DragonBallDiagnosticLatch();
        assertTrue(latch.shouldReport("slot:1", "conflict"));
        assertTrue(latch.shouldReport("slot:2", "conflict"));
        latch.clearAll();
        assertTrue(latch.shouldReport("slot:1", "conflict"));
    }
}
