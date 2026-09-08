package net.bullettrain.xenopixelsmod.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class XenoPointsCommandsTest {
    @Test
    void acceptsWholeNonNegativeAmounts() {
        assertEquals(0.0f, XenoPointsCommands.parseAmount("0"));
        assertEquals(500.0f, XenoPointsCommands.parseAmount("500"));
    }

    @Test
    void rejectsValuesThatDmzPointsRejects() {
        assertNull(XenoPointsCommands.parseAmount("-1"));
        assertNull(XenoPointsCommands.parseAmount("1.5"));
        assertNull(XenoPointsCommands.parseAmount("abc"));
        assertNull(XenoPointsCommands.parseAmount("9999999999999999999999999999999999999999"));
    }

    @Test
    void operationsClampLikeDragonMineZ() {
        assertEquals(500.0f, XenoPointsCommands.Operation.ADD.apply(100.0f, 400.0f));
        assertEquals(Float.MAX_VALUE - 1.0f,
                XenoPointsCommands.Operation.ADD.apply(Float.MAX_VALUE - 1.0f, 500.0f));
        assertEquals(0.0f, XenoPointsCommands.Operation.REMOVE.apply(100.0f, 500.0f));
        assertEquals(500.0f, XenoPointsCommands.Operation.ADD.apply(Float.NaN, 500.0f));
        assertEquals(0.0f, XenoPointsCommands.Operation.REMOVE.apply(Float.POSITIVE_INFINITY, 500.0f));
        assertEquals(250.0f, XenoPointsCommands.Operation.SET.apply(999.0f, 250.0f));
    }
}
