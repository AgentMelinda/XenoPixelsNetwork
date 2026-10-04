package net.bullettrain.xenopixelsmod.compat.npc;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NpcScriptListsTest {
    @Test
    void joinsJavaArrayListAndNull() {
        assertEquals("", NpcScriptLists.joinNames(null));
        assertEquals("A, B", NpcScriptLists.joinNames(new String[]{"A", "B"}));
        assertEquals("A | B", NpcScriptLists.joinNames(List.of("A", "B"), " | "));
        assertEquals("solo", NpcScriptLists.joinNames("solo"));
        assertEquals("A, C", NpcScriptLists.joinNames(new String[]{"A", null, "C"}));
    }
}
