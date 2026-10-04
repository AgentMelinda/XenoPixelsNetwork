package net.bullettrain.xenopixelsmod.npc.script;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NpcScriptTypedAliasTest {
    @Test
    void theContractNameForRangedLaunchedIsAnAdditionalHook() {
        assertEquals("rangedAttack", NpcScriptHost.typedAlias("rangedLaunched"));
        assertNull(NpcScriptHost.typedAlias("damaged"));
        assertTrue(NpcScriptHost.HOOKS.contains("rangedAttack"));
        assertTrue(NpcScriptHost.HOOKS.contains("rangedLaunched"), "the native name keeps working");
    }
}
