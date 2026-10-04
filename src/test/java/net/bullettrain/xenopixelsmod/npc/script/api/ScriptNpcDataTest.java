package net.bullettrain.xenopixelsmod.npc.script.api;

import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngine;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptEngines;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptResult;
import net.bullettrain.xenopixelsmod.npc.script.NpcScriptScope;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScriptNpcDataTest {
    @Test
    void mynpcsDataObjectsPersistAndExposeAllSixOperations() {
        CompoundTag root = new CompoundTag();
        ScriptNpc npc = new ScriptNpc(root, new HashMap<>());
        ScriptNpc.Data stored = npc.getStoreddata();
        stored.put("quest", 3);
        assertEquals(3.0, stored.get("quest"));
        assertTrue(stored.has("quest"));
        assertEquals("quest", stored.getKeys()[0]);
        assertEquals(3.0, new ScriptNpc(root, new HashMap<>()).getStoreddata().get("quest"));
        stored.remove("quest");
        assertFalse(stored.has("quest"));
        stored.put("other", "yes");
        stored.clear();
        assertEquals(0, stored.getKeys().length);

        ScriptNpc.Data temp = npc.getTempdata();
        temp.put("wave", true);
        assertEquals(true, temp.get("wave"));
        assertEquals("wave", temp.getKeys()[0]);
        temp.clear();
        assertFalse(temp.has("wave"));
    }

    @Test
    void actualNashornHookCanReadEventNpcStoreddata() {
        ScriptNpc npc = new ScriptNpc(new CompoundTag(), new HashMap<>());
        ScriptEvent event = new ScriptEvent("interact", npc, null, null, null, 0);
        NpcScriptEngine.Instance instance = NpcScriptEngines.current().instantiate(
                "function interact(event) { var npc = event.npc;"
                        + " var data = npc.getStoreddata(); data.put('visits', 2);"
                        + " return data.get('visits'); }",
                NpcScriptScope.EMPTY);
        assertTrue(instance.ok(), instance.loadResult().describe());
        NpcScriptResult result = instance.call("interact", event);
        assertTrue(result.ok(), result.describe());
        assertEquals(2.0, Double.parseDouble(result.value()));
    }

    @Test
    void meleeAttackCanChangeDamageAndCancel() {
        ScriptEvent event = new ScriptEvent("meleeAttack", null, null, null, null, 5);
        NpcScriptEngine.Instance instance = NpcScriptEngines.current().instantiate(
                "function meleeAttack(event) { event.damage = 7; event.setCanceled(true); }",
                NpcScriptScope.EMPTY);
        assertTrue(instance.ok(), instance.loadResult().describe());
        assertTrue(instance.call("meleeAttack", event).ok());
        assertEquals(7.0f, event.getDamage());
        assertTrue(event.isCanceled());
    }
}
