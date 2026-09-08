package net.bullettrain.xenopixelsmod.client.pad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PadModeTest {
    @Test
    void missingAndInvalidValuesDefaultToBt3() {
        assertEquals(PadMode.BT3, PadMode.parse(null));
        assertEquals(PadMode.BT3, PadMode.parse("unknown"));
    }

    @Test
    void persistedNamesAreCaseInsensitive() {
        assertEquals(PadMode.NORMAL, PadMode.parse("normal"));
        assertEquals(PadMode.BT3, PadMode.parse("BT3"));
    }
}
