package net.bullettrain.xenopixelsmod.npc.script.api.xeno;

import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.event.NpcEvent;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

/** Final-review findings 5 and 6 (2026-09-27). */
class ReviewFixesAdapterTest {
    private static final Runnable OFF_THREAD = () -> { throw new IllegalStateException("XenoAPI mutations must run on the server thread"); };

    @AfterEach
    void reset() {
        NpcScriptEngines.reset();
    }

    @Test
    void containerWritesCheckTheThreadOnEveryCallBeforeMutating() {
        SimpleContainer inventory = new SimpleContainer(1);
        var container = XenoContainerAdapter.of(inventory, OFF_THREAD);
        assertThrows(IllegalStateException.class, () -> container.setSlot(0, XenoApiAdapters.wrap(new ItemStack(Items.DIRT))));
        assertTrue(inventory.getItem(0).isEmpty());
    }

    @Test
    void aRetainedDataViewChecksTheThreadOnEveryOperation() {
        var map = new HashMap<String, Object>();
        var data = XenoDataAdapter.ofView(() -> XenoBoundedData.temp(map), OFF_THREAD);
        assertThrows(IllegalStateException.class, () -> data.put("k", 1));
        assertThrows(IllegalStateException.class, () -> data.get("k"));
        assertTrue(map.isEmpty());
    }

    @Test
    void aRetainedTimersViewChecksTheThreadOnEveryOperation() {
        ScriptTimers timers = new ScriptTimers();
        var api = XenoTimersAdapter.forTimers(timers, () -> 0L, OFF_THREAD);
        assertThrows(IllegalStateException.class, () -> api.forceStart(1, 10, false));
        assertFalse(timers.has(1));
    }

    @Test
    void aScriptThatAssignsDamageDirectlyKeepsItsEdit() {
        var damaged = new NpcEvent.DamagedEvent(null, null, 8.0f, null);
        var event = new ScriptEvent("damaged", null, null, null, null, 8.0f, damaged);
        var tab = NpcScriptEngines.current().instantiate("function damaged(e) { e.damage = 1; }", NpcScriptScope.EMPTY);
        assertTrue(tab.call("damaged", event).ok());
        event.pushToXeno();       // what the host does before Java listeners run
        event.syncFromXeno();
        assertEquals(1.0f, event.getDamage(), "an old-style field write must survive read-back");
        assertEquals(1.0f, damaged.damage);
    }
}
