package net.bullettrain.xenopixelsmod.combat.targeting;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImmediateLockTest {
    @Test void zeroSecondsIsAlreadyLockedBeforeFirstTick() {
        assertEquals(1, TargetLockManager.initialProgress(0));
        assertEquals(0, TargetLockManager.initialProgress(.5));
        assertEquals(0, TargetLockManager.initialProgress(2));
    }
}
