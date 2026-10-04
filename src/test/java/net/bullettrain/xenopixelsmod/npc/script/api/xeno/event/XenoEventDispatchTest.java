package net.bullettrain.xenopixelsmod.npc.script.api.xeno.event;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent;
import net.neoforged.bus.api.BusBuilder;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.event.NpcEvent;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class XenoEventDispatchTest {
    @Test
    void theBusCountsRegistrationsAndForwards() {
        XenoEventBus bus = new XenoEventBus(BusBuilder.builder().build());
        assertFalse(bus.hasListeners());
        int[] seen = {0};
        bus.addListener(NpcEvent.InitEvent.class, e -> seen[0]++);
        assertTrue(bus.hasListeners());
        bus.post(new NpcEvent.InitEvent(null));
        assertEquals(1, seen[0]);
    }

    @Test
    void cancelAndDamageAccessorsFollowTheTypedEvent() {
        var damaged = new NpcEvent.DamagedEvent(null, null, 4.0f, null);
        assertEquals(4.0f, XenoEventDispatch.damage(damaged));
        XenoEventDispatch.setDamage(damaged, 9.0f);
        assertEquals(9.0f, damaged.damage);
        XenoEventDispatch.setCanceled(damaged, true);
        assertTrue(XenoEventDispatch.isCanceled(damaged));
        var init = new NpcEvent.InitEvent(null);
        assertNull(XenoEventDispatch.damage(init));
        XenoEventDispatch.setCanceled(init, true);
        assertFalse(XenoEventDispatch.isCanceled(init), "a non-cancellable event cannot be cancelled");
    }

    @Test
    void scriptEventEditsWriteThroughAndReadBack() {
        var damaged = new NpcEvent.DamagedEvent(null, null, 4.0f, null);
        var event = new ScriptEvent("damaged", null, null, null, null, 4.0f, damaged);
        assertSame(damaged, event.xeno);
        event.setDamage(2.0f);
        event.setCanceled(true);
        assertEquals(2.0f, damaged.damage);
        assertTrue(damaged.isCanceled());
        damaged.setCanceled(false);   // a Java listener overrides the script
        damaged.damage = 7.0f;
        event.syncFromXeno();
        assertFalse(event.isCanceled());
        assertEquals(7.0f, event.getDamage());
    }

    @Test
    void oldHooksKeepTheirOwnCancelWhenTheTypedEventIsNotCancellable() {
        var collide = new NpcEvent.CollideEvent(null, null);
        var event = new ScriptEvent("collide", null, null, null, null, 0.0f, collide);
        event.setCanceled(true);
        event.syncFromXeno();
        assertTrue(event.isCanceled(), "collide's typed event is not cancellable, so the old flag stands");
    }

    @Test
    void reentryIsRefusedUntilExit() {
        UUID id = UUID.randomUUID();
        assertTrue(XenoEventDispatch.enter(id, "target"));
        assertFalse(XenoEventDispatch.enter(id, "target"));
        XenoEventDispatch.exit(id, "target");
        assertTrue(XenoEventDispatch.enter(id, "target"));
        XenoEventDispatch.exit(id, "target");
    }

    @Test
    void aThrowingListenerDoesNotEscapePost() {
        XenoEventBus bus = new XenoEventBus(BusBuilder.builder().build());
        bus.addListener(NpcEvent.InitEvent.class, e -> { throw new IllegalStateException("boom"); });
        var event = new NpcEvent.InitEvent(null);
        assertSame(event, XenoEventDispatch.post(bus, event));
    }
}
