package net.bullettrain.xenopixelsmod.npc.script.api;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ScriptTimersTest {
    @Test
    void oneShotAndRepeatingTimersFireAtTheirDueTick() {
        ScriptTimers timers = new ScriptTimers();
        var fired = new ArrayList<Integer>();
        assertTrue(timers.forceStart(1, 2, false, 10));
        assertTrue(timers.forceStart(2, 2, true, 10));
        timers.tick(11, fired::add);
        assertTrue(fired.isEmpty());
        timers.tick(12, fired::add);
        assertTrue(fired.contains(1));
        assertTrue(fired.contains(2));
        assertFalse(timers.has(1));
        assertTrue(timers.has(2));
        fired.clear();
        timers.tick(14, fired::add);
        assertEquals(java.util.List.of(2), fired);
    }

    @Test
    void timersResumeFromNpcPersistentData() {
        var tag = new net.minecraft.nbt.CompoundTag();
        ScriptTimers first = new ScriptTimers(tag);
        assertTrue(first.forceStart(9201, 20, false, 100));
        ScriptTimers afterReload = new ScriptTimers(tag);
        var fired = new ArrayList<Integer>();
        afterReload.tick(119, fired::add);
        assertTrue(fired.isEmpty());
        afterReload.tick(120, fired::add);
        assertEquals(java.util.List.of(9201), fired);
        assertFalse(new ScriptTimers(tag).has(9201));
    }
}
