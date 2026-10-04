package net.bullettrain.xenopixelsmod.npc.script;

import net.bullettrain.xenopixelsmod.npc.script.api.ForgeScriptEvent;
import net.bullettrain.xenopixelsmod.npc.store.XenoNpcStoreCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** 2026-09-28 owner request: working Forge (world event) scripts, next to player scripts. */
class ForgeScriptHostTest {
    @Test
    void forgeScriptsAreAppendedSoExistingWireOrdinalsStay() {
        XenoNpcStoreCategory[] all = XenoNpcStoreCategory.values();
        assertEquals(XenoNpcStoreCategory.FORGE_SCRIPTS, all[all.length - 1]);
        assertEquals(XenoNpcStoreCategory.PLAYER_SCRIPTS, all[all.length - 2]);
        assertEquals("forge_scripts", XenoNpcStoreCategory.FORGE_SCRIPTS.folder());
        assertTrue(XenoNpcStoreCategory.FORGE_SCRIPTS.hasRuntimeConsumer());
    }

    @Test
    void hooksCoverTheCuratedServerEvents() {
        for (String hook : new String[] {"playerLogin", "playerLogout", "playerRespawn", "playerChangedDimension",
                "livingDeath", "livingHurt", "entityJoin", "blockBreak", "blockPlace", "serverChat",
                "rightClickBlock", "rightClickItem", "leftClickBlock", "entityInteract", "serverTick", "explosion"}) {
            assertTrue(ForgeScriptHost.HOOKS.contains(hook), hook);
        }
    }

    @Test
    void cancelOnlyTakesEffectOnCancellableEvents() {
        ForgeScriptEvent fixed = new ForgeScriptEvent("serverTick", false);
        fixed.setCanceled(true);
        assertFalse(fixed.isCanceled());
        ForgeScriptEvent open = new ForgeScriptEvent("blockBreak", true);
        open.cancel();
        assertTrue(open.isCanceled());
        assertTrue(open.isCancelable());
    }

    @Test
    void aTabThatKeepsFailingIsParkedUntilTheNextReload() {
        ForgeScriptHost.Breaker breaker = new ForgeScriptHost.Breaker();
        for (int i = 0; i < ForgeScriptHost.Breaker.LIMIT - 1; i++) assertFalse(breaker.fail());
        assertTrue(breaker.fail());
        assertTrue(breaker.parked());
        ForgeScriptHost.Breaker healthy = new ForgeScriptHost.Breaker();
        healthy.fail();
        healthy.ok();
        assertFalse(healthy.parked());
    }
}
