package net.bullettrain.xenopixelsmod.combat;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StickyLockRulesTest {

    @Test void aLiveLockIsNeverRePinned() {
        assertFalse(StickyLockRules.restore(true, true, 0, 1500, false));
        assertFalse(StickyLockRules.restore(true, false, 0, 1500, false));
    }

    @Test void aFreshDropOfAValidTargetIsRestored() {
        assertTrue(StickyLockRules.restore(false, true, 10, 1500, false));
        assertTrue(StickyLockRules.restore(false, true, 1500, 1500, false));
    }

    @Test void theGraceWindowExcludesStaleTargetsAndZeroDisables() {
        assertFalse(StickyLockRules.restore(false, true, 1501, 1500, false));
        assertFalse(StickyLockRules.restore(false, true, 10, 0, false));
    }

    @Test void anInvalidTargetIsLeftUnlocked() {
        assertFalse(StickyLockRules.restore(false, false, 10, 1500, false));
    }

    @Test void anExplicitUnlockIsNeverOverridden() {
        assertFalse(StickyLockRules.restore(false, true, 10, 1500, true));
    }
}
