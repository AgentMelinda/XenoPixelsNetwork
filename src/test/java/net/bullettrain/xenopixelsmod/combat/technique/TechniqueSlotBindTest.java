package net.bullettrain.xenopixelsmod.combat.technique;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TechniqueSlotBindTest {

    private static String[] bar(String... ids) {
        String[] slots = new String[8];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i < ids.length && ids[i] != null ? ids[i] : "";
        }
        return slots;
    }

    @Test
    void emptyIdClearsOnlyTheTargetSlot() {
        String[] slots = bar("a", "b", "", "c");
        TechniqueSlotBind.place(slots, 1, "");
        assertArrayEquals(bar("a", "", "", "c"), slots);
    }

    @Test
    void emptyIdDoesNotSwapWithTheFirstHole() {
        String[] slots = bar("a", "b", "", "");
        TechniqueSlotBind.place(slots, 0, "");
        assertEquals("", slots[0]);
        assertEquals("b", slots[1]);
        assertEquals("", slots[2]);
    }

    @Test
    void placingOntoAnOccupiedSlotUnequipsTheOccupant() {
        String[] slots = bar("old", "", "other");
        TechniqueSlotBind.place(slots, 0, "new");
        assertArrayEquals(bar("new", "", "other"), slots);
    }

    @Test
    void movingAnEquippedTechniqueClearsItsOldSlotAndDoesNotTeleportTheOccupant() {
        String[] slots = bar("b", "", "a");
        TechniqueSlotBind.place(slots, 2, "b");
        assertEquals("", slots[0]);
        assertEquals("", slots[1]);
        assertEquals("b", slots[2]);
    }

    @Test
    void bindingOntoTheSameSlotIsANoOp() {
        String[] slots = bar("a", "b");
        TechniqueSlotBind.place(slots, 0, "a");
        assertArrayEquals(bar("a", "b"), slots);
    }

    @Test
    void nullArrayOrOutOfRangeIsIgnored() {
        TechniqueSlotBind.place(null, 0, "a");
        String[] slots = bar("a");
        TechniqueSlotBind.place(slots, -1, "b");
        TechniqueSlotBind.place(slots, 99, "b");
        assertArrayEquals(bar("a"), slots);
    }

    @Test
    void nullIdClearsTheTargetLikeEmpty() {
        String[] slots = bar("a", "b");
        TechniqueSlotBind.place(slots, 1, null);
        assertArrayEquals(bar("a", ""), slots);
    }

    @Test
    void unbindEmptiesOnlyThatSlotAndDoesNotRelocateTheTechnique() {
        // DMZ's equipOrSwapTechnique would have moved "a" into the first vacant slot instead of
        // removing it, which is why unbinding through DMZ appeared to do nothing.
        String[] slots = bar("a", "", "b");
        TechniqueSlotBind.unbind(slots, 0);
        assertArrayEquals(bar("", "", "b"), slots);
    }

    @Test
    void unbindOnAnAlreadyEmptySlotChangesNothing() {
        String[] slots = bar("a", "", "b");
        TechniqueSlotBind.unbind(slots, 1);
        assertArrayEquals(bar("a", "", "b"), slots);
    }
}
