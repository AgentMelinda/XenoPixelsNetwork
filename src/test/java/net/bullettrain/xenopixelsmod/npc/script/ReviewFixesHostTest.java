package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.script.api.ScriptTimers;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Final-review findings 2 and 4 (2026-09-27). */
class ReviewFixesHostTest {
    @Test
    void anNpcLeavingItsLevelDropsPendingStateBoundToThatEntity() {
        UUID npc = UUID.randomUUID();
        var before = NpcScriptHost.sharedState(npc, ScriptTimers::new);
        NpcScriptHost.leftLevel(npc);
        assertNull(NpcScriptHost.takePending(npc), "pending state bound to the unloaded entity is gone");
        assertNotSame(before, NpcScriptHost.sharedState(npc, ScriptTimers::new));
        NpcScriptHost.forget(npc);
    }

    @Test
    void aClonedPlayerRebindsTimersToTheNewPlayerData() {
        UUID player = UUID.randomUUID();
        CompoundTag oldData = new CompoundTag();
        CompoundTag newData = new CompoundTag();
        ScriptTimers first = PlayerScriptTimers.of(player, () -> oldData);
        first.forceStart(1, 20, false, 0);
        PlayerScriptTimers.cloned(player);
        ScriptTimers second = PlayerScriptTimers.of(player, () -> newData);
        assertNotSame(first, second);
        second.forceStart(2, 20, false, 0);
        assertTrue(newData.contains("XenoScriptTimers"), "writes land in the new player's data");
        PlayerScriptTimers.forget(player);
    }
}
