package net.bullettrain.xenopixelsmod.npc.script.api;

import net.bullettrain.xenopixelsmod.capability.XenoPlayerData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerScriptDataTest {
    @Test
    void questRewardMarkerSurvivesSaveAndDeathCopy() {
        XenoPlayerData original = new XenoPlayerData();
        original.scriptData().putDouble("xeno_quest_points_paid", 1);
        CompoundTag saved = new CompoundTag();
        original.saveNBT(saved);
        XenoPlayerData loaded = new XenoPlayerData();
        loaded.loadNBT(saved);
        assertEquals(1, loaded.scriptData().getDouble("xeno_quest_points_paid"));
        XenoPlayerData afterDeath = new XenoPlayerData();
        afterDeath.copyFrom(loaded);
        assertEquals(1, afterDeath.scriptData().getDouble("xeno_quest_points_paid"));
    }
}
