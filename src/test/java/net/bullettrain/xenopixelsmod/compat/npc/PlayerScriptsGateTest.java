package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerScriptsGateTest {

    @AfterEach
    void resetStarted() throws Exception {
        var field = PlayerScriptsGate.class.getDeclaredField("serverStarted");
        field.setAccessible(true);
        field.setBoolean(null, false);
        var skip = PlayerScriptsGate.class.getDeclaredField("skipLogged");
        skip.setAccessible(true);
        skip.setBoolean(null, false);
    }

    @Test
    void deniesClientPlayerCopies() {
        PlayerScriptsGate.markServerStarted();
        assertFalse(PlayerScriptsGate.allow(true, true, true, true));
    }

    @Test
    void deniesBeforeStartWhenHasStartIsFalse() {
        assertFalse(PlayerScriptsGate.allow(false, true, true, false));
    }

    @Test
    void allowsWhenHasStartAndThisEnabled() {
        assertTrue(PlayerScriptsGate.allow(true, true, false, false));
    }

    @Test
    void allowsWhenHasStartAndGlobalEnabled() {
        assertTrue(PlayerScriptsGate.allow(true, false, true, false));
    }

    @Test
    void allowsAfterServerStartedEvenIfHasStartStuck() {
        PlayerScriptsGate.markServerStarted();
        assertTrue(PlayerScriptsGate.allow(false, false, true, false));
    }

    @Test
    void deniesWhenNeitherCopyIsEnabled() {
        PlayerScriptsGate.markServerStarted();
        assertFalse(PlayerScriptsGate.allow(true, false, false, false));
    }
}
