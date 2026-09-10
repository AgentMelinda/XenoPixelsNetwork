package net.bullettrain.xenopixelsmod.client.effect;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XenoEffectRailLayoutTest {

    @Test
    void laysEffectsVerticallyToTheRightOfInventory() {
        List<XenoEffectRailLayout.Cell> cells = XenoEffectRailLayout.layout(500, 100, 900, 500, 4);

        assertEquals(504, cells.getFirst().x());
        assertEquals(100, cells.getFirst().y());
        assertEquals(504, cells.getLast().x());
        assertEquals(181, cells.getLast().y());
    }

    @Test
    void wrapsIntoAnotherColumnOnlyWhenHeightIsExhausted() {
        List<XenoEffectRailLayout.Cell> cells = XenoEffectRailLayout.layout(500, 100, 900, 152, 3);

        assertEquals(504, cells.get(0).x());
        assertEquals(100, cells.get(0).y());
        assertEquals(531, cells.get(1).x());
        assertEquals(100, cells.get(1).y());
        assertEquals(558, cells.get(2).x());
        assertEquals(100, cells.get(2).y());
    }

    @Test
    void clampsRailIntoNarrowAndShortScreens() {
        List<XenoEffectRailLayout.Cell> cells = XenoEffectRailLayout.layout(190, 80, 200, 100, 3);

        for (XenoEffectRailLayout.Cell cell : cells) {
            assertTrue(cell.x() >= 4);
            assertTrue(cell.x() + XenoEffectRailLayout.CELL_SIZE <= 196);
            assertTrue(cell.y() >= 4);
            assertTrue(cell.y() + XenoEffectRailLayout.CELL_SIZE <= 96);
        }
    }
}
