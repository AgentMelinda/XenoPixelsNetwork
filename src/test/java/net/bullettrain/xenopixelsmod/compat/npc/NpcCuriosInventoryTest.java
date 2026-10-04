package net.bullettrain.xenopixelsmod.compat.npc;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.SlotItemHandler;
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

    @Test
    void missingCapabilitySlotsStayModifiableForContainerSync() {
        List<Slot> slots = new ArrayList<>();
        NpcCuriosInventory.addSlots(null, slots::add);
        assertEquals(NpcCuriosInventory.extraSlotCount(), slots.size());
        for (Slot slot : slots) {
            assertTrue(slot instanceof SlotItemHandler);
            assertTrue(((SlotItemHandler) slot).getItemHandler() instanceof IItemHandlerModifiable,
                    "SlotItemHandler.set casts to IItemHandlerModifiable on container sync");
        }
    }
}
