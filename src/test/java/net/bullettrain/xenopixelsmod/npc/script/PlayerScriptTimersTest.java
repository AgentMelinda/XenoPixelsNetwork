package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.bullettrain.xenopixelsmod.npc.script.api.xeno.XenoTimersAdapter;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import xenoapi.npcs.api.CustomNPCsException;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class PlayerScriptTimersTest {
    @Test
    void playerTimersSurviveSaveAndLoadSeparatelyFromStoredData() {
        XenoPlayerData data = new XenoPlayerData();
        ScriptTimers timers = new ScriptTimers(data.scriptTimers());
        assertTrue(timers.forceStart(7, 20, false, 100));
        CompoundTag saved = new CompoundTag();
        data.saveNBT(saved);
        XenoPlayerData loaded = new XenoPlayerData();
        loaded.loadNBT(saved);
        assertTrue(new ScriptTimers(loaded.scriptTimers()).has(7));
        assertEquals(0, loaded.scriptData().size(), "timers never appear as stored script data");
    }

    @Test
    void theXenoApiViewUsesTheSameTimersAndRefusesLikeNpcTimers() {
        ScriptTimers timers = new ScriptTimers();
        long[] now = {50};
        XenoTimersAdapter api = XenoTimersAdapter.forTimers(timers, () -> now[0]);
        api.start(3, 10, false);
        assertThrows(CustomNPCsException.class, () -> api.start(3, 10, false));
        var fired = new ArrayList<Integer>();
        timers.tick(60, fired::add);
        assertEquals(java.util.List.of(3), fired);
    }
}
