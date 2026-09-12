package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcCuriosInventoryTest {

    @Test
    void extraSlotsAreOneEquipAndOneCosmeticPerMappedType() {
        assertEquals(NpcCuriosInventory.SLOT_IDS.length * 2, NpcCuriosInventory.extraSlotCount());
        assertEquals(20, NpcCuriosInventory.extraSlotCount());
    }

    @Test
    void gridStaysAboveThePlayerInventoryRow() {
        int last = NpcCuriosInventory.extraSlotCount() - 1;
        assertEquals(108, NpcCuriosInventory.slotX(0));
        assertEquals(8, NpcCuriosInventory.slotY(0));
        assertTrue(NpcCuriosInventory.slotY(last) + NpcCuriosInventory.SLOT_SIZE <= 113,
                "curios wells must not cover the player-inventory row at y=113");
    }

    @Test
    void pairedCosmeticSlotSitsImmediatelyRightOfEquip() {
        assertEquals(NpcCuriosInventory.slotX(0) + NpcCuriosInventory.SLOT_SIZE
                + NpcCuriosInventory.COL_GAP, NpcCuriosInventory.slotX(1));
        assertEquals(NpcCuriosInventory.slotY(0), NpcCuriosInventory.slotY(1));
        int secondBank = 5 * 2;
        assertEquals(NpcCuriosInventory.slotX(0) + 2 * (NpcCuriosInventory.SLOT_SIZE
                + NpcCuriosInventory.COL_GAP), NpcCuriosInventory.slotX(secondBank));
        assertEquals(NpcCuriosInventory.slotY(0), NpcCuriosInventory.slotY(secondBank));
    }
}
