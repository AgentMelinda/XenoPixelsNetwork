package net.bullettrain.xenopixelsmod.npc.script.api;

import net.minecraft.nbt.CompoundTag;

import java.util.Map;

/** Opens ScriptNpc's entity-free test seam to tests in other packages. */
public final class ScriptNpcTestAccess {
    private ScriptNpcTestAccess() {}

    public static ScriptNpc npc(CompoundTag persistentData, Map<String, Object> temp, ScriptTimers timers) {
        return new ScriptNpc(persistentData, temp, timers);
    }
}
