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
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("XenoPixels.setAuraStyle(")));
        assertTrue(calls.stream().anyMatch(call -> call.startsWith("XenoPixels.listAnimations(")));
        assertFalse(calls.stream().anyMatch(call -> call.contains(".getClass(") || call.contains(".unwrap(")));
        assertEquals(calls.size(), new java.util.HashSet<>(calls).size());
    }
}
