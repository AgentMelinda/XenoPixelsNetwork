package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.event.NpcEvent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Final-review findings 1, 3 and 10 (2026-09-27). */
class ReviewFixesEventTest {
    @Test
    void aPartlyReturnedTossGivesBackOnlyWhatFitsAndReportsTheRest() {
        SimpleContainer inventory = new SimpleContainer(1);
        inventory.setItem(0, new ItemStack(Items.DIRT, 24));      // room for 40 more
        ItemStack snapshot = new ItemStack(Items.DIRT, 64);
        ItemStack remainder = PlayerXenoEvents.returnTossed(inventory::addItem, snapshot);
        assertEquals(64, inventory.getItem(0).getCount(), "40 went back");
        assertEquals(24, remainder.getCount(), "24 still have to drop, not 64");
        assertEquals(64, snapshot.getCount(), "the snapshot is never mutated");
    }

    @Test
    void aFullyReturnedTossLeavesNothingToDrop() {
        SimpleContainer inventory = new SimpleContainer(2);
        ItemStack remainder = PlayerXenoEvents.returnTossed(inventory::addItem, new ItemStack(Items.DIAMOND, 3));
        assertTrue(remainder.isEmpty());
        assertEquals(3, inventory.getItem(0).getCount());
    }

    @Test
    void aListenerErrorDoesNotEscapeIntoTheGameTick() {
        XenoEventBus bus = new XenoEventBus(BusBuilder.builder().build());
        bus.addListener(NpcEvent.InitEvent.class, e -> { throw new AbstractMethodError("addon built against an old API"); });
        var event = new NpcEvent.InitEvent(null);
        assertSame(event, XenoEventDispatch.post(bus, event));
    }

    @Test
    void oneClickPostsOneInteractPerTick() {
        UUID player = UUID.randomUUID();
        assertTrue(PlayerXenoEvents.firstInteract(player, 100L), "block/entity interact is posted");
        assertFalse(PlayerXenoEvents.firstInteract(player, 100L), "the follow-up item use on the same tick is not");
        assertTrue(PlayerXenoEvents.firstInteract(player, 101L), "a new tick is a new click");
    }
}
