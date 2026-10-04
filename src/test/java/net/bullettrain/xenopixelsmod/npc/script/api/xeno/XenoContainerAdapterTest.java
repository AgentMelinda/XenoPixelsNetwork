package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class XenoContainerAdapterTest {
    @Test
    void containerReadsAndBoundedWritesReachTheBackingInventory() {
        SimpleContainer inventory = new SimpleContainer(3);
        inventory.setItem(1, new ItemStack(Items.STONE, 4));
        var container = XenoContainerAdapter.of(inventory);
        assertEquals(3, container.getSize());
        assertEquals(4, container.getSlot(1).getStackSize());
        assertEquals(4, container.count(XenoApiAdapters.wrap(new ItemStack(Items.STONE)), true, true));
        container.setSlot(0, XenoApiAdapters.wrap(new ItemStack(Items.DIRT, 2)));
        assertEquals(Items.DIRT, inventory.getItem(0).getItem());
        assertThrows(IllegalArgumentException.class, () -> container.setSlot(3, null));
        assertThrows(IllegalArgumentException.class, () -> container.getSlot(-1));
        assertEquals(3, container.getItems().length);
        assertThrows(UnsupportedOperationException.class, container::getMCInventory);
        assertNull(XenoContainerAdapter.of((net.minecraft.world.Container) null));
    }
}
