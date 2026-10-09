package net.bullettrain.xenopixelsmod.npc.script;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScriptFunctionCatalogTest {
    @Test void catalogIncludesBoundEffectsAndAppearanceCallsWithoutObjectOrRawEntityAccess() {
        var calls = ScriptFunctionCatalog.calls();
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.setAuraScale(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.getDisplay().setSize(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.playSound(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.playAnimation(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("world.spawnClone(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.setMaxHealth(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.despawn(")));
        assertTrue(calls.contains("npc.isKilled()"));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("XenoPixels.setAuraStyle(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("XenoPixels.listAnimations(")));
        assertFalse(calls.stream().anyMatch(call -> call.contains(".getClass(") || call.contains(".unwrap(")));
        assertEquals(calls.size(), new java.util.HashSet<>(calls).size());
    }

    @Test void typedApiAndNestedPathsAreDiscoverableWithoutAdvertisingRawJavaAccess() {
        var calls = ScriptFunctionCatalog.calls();
        assertTrue(calls.contains("npc.getAPI()"));
        assertTrue(calls.contains("world.getAPI()"));
        assertTrue(calls.contains("event.getAPI()"));
        assertTrue(calls.contains("player.getAPI()"));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.getAPI().getStats().setMaxHealth(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.getAPI().getDisplay().setSize(")));
        assertFalse(calls.stream().anyMatch(call -> call.startsWith("npc.getDisplay().setModel(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("npc.getAPI().getTimers().start(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("world.getAPI().spawnClone(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("XenoAPI.getClones().get(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("event.getAPI().getClones().spawn(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("player.getAPI().getStoreddata().put(")));
        assertFalse(calls.stream().anyMatch(call -> call.contains(".getMC")
                || call.contains(".registerScriptEvent(") || call.contains(".getGlobalDir(")
                || call.contains(".getLevelDir(") || call.contains(".getClass(")));
    }

    @Test void bridgeAddsTypedAccessWithoutChangingLegacyReturnContracts() throws Exception {
        var npc = net.bullettrain.xenopixelsmod.npc.script.api.ScriptNpc.class;
        var world = net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld.class;
        assertEquals(xenoapi.npcs.api.entity.ICustomNpc.class, npc.getMethod("getAPI").getReturnType());
        assertEquals(xenoapi.npcs.api.IWorld.class, world.getMethod("getAPI").getReturnType());
        assertEquals(xenoapi.npcs.api.entity.IPlayer.class,
                net.bullettrain.xenopixelsmod.npc.script.api.ScriptPlayer.class.getMethod("getAPI").getReturnType());
        assertEquals(xenoapi.npcs.api.NpcAPI.class,
                net.bullettrain.xenopixelsmod.npc.script.api.ScriptEvent.class.getMethod("getAPI").getReturnType());
        assertEquals(String.class, npc.getMethod("getType").getReturnType());
        assertEquals(String.class, world.getMethod("getDimension").getReturnType());
        assertThrows(IllegalStateException.class,
                () -> new net.bullettrain.xenopixelsmod.npc.script.api.ScriptWorld(null).getAPI());
    }
}
