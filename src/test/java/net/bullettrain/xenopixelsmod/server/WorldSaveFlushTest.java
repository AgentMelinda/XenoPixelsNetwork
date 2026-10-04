package net.bullettrain.xenopixelsmod.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldSaveFlushTest {

    @Test
    void linearModIdMatchesTheInstalledJar() {
        assertEquals("linearreader", WorldSaveFlush.LINEAR_MOD_ID);
    }

    @Test
    void resultOkWhenNoError() {
        assertTrue(new WorldSaveFlush.Result(true, 2, null).ok());
        assertTrue(new WorldSaveFlush.Result(true, 0, "").ok());
        assertFalse(new WorldSaveFlush.Result(false, 0, "disk full").ok());
    }
}
