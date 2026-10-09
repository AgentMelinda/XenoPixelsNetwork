package net.bullettrain.xenopixelsmod.client.keybind;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The keybind cleanup keeps Minecraft's, DragonMineZ's and XenoPixels' bindings and unbinds every
 * other mod's. A wrong answer here silently strips a player's controls, so the rule is pinned.
 */
class KeybindRulesTest {

    @Test
    void minecraftDragonMineZAndXenoPixelsAreKept() {
        assertTrue(KeybindRules.keep("key.attack", true, List.of()));
        assertTrue(KeybindRules.keep("key.dragonminez.lock_on", false, List.of()));
        assertTrue(KeybindRules.keep("key.xenopixelsmod.bt3_guard", false, List.of()));
        assertTrue(KeybindRules.keep("key.xenopixelsmod.v2_light", false, List.of()));
    }

    @Test
    void anotherModsBindingIsNotKept() {
        assertFalse(KeybindRules.keep("key.create.toolmenu", false, List.of()));
        assertFalse(KeybindRules.keep("key.jei.toggleOverlay", false, null));
    }

    @Test
    void aModCannotPassAsMinecraftByItsName() {
        // Vanilla is decided by identity against Options, never by how the key is named.
        assertFalse(KeybindRules.keep("key.attack", false, List.of()));
        assertFalse(KeybindRules.keep("key.inventory", false, List.of()));
    }

    @Test
    void aLookalikePrefixIsNotDragonMineZ() {
        assertFalse(KeybindRules.keep("key.dragonminez_extras.thing", false, List.of()));
        assertFalse(KeybindRules.keep("key.xenopixelsmodx.thing", false, List.of()));
    }

    @Test
    void thePlayerCanKeepAnotherModsPrefix() {
        List<String> keep = List.of("key.controlify.", "  ", "KEY.JEI.");
        assertTrue(KeybindRules.keep("key.controlify.radial", false, keep));
        assertTrue(KeybindRules.keep("key.jei.toggleOverlay", false, keep));
        assertFalse(KeybindRules.keep("key.create.toolmenu", false, keep));
    }

    // ---- held modifiers ----

    private static final int LEFT_SHIFT = 340;
    private static final int LEFT_CONTROL = 341;
    private static final int LEFT_ALT = 342;
    private static final int RIGHT_SUPER = 347;
    private static final int KEY_W = 87;
    private static final int KEY_TAB = 258;
    private static final int KEY_F12 = 301;

    @Test
    void shiftControlAltAndSuperAreModifierKeysOnBothSides() {
        for (int key = LEFT_SHIFT; key <= RIGHT_SUPER; key++) {
            assertTrue(KeybindRules.isModifierKey(key), "key " + key);
        }
        assertFalse(KeybindRules.isModifierKey(LEFT_SHIFT - 1));
        assertFalse(KeybindRules.isModifierKey(RIGHT_SUPER + 1));
        assertFalse(KeybindRules.isModifierKey(KEY_W));
        assertFalse(KeybindRules.isModifierKey(-1), "an unbound key is not a modifier");
    }

    /**
     * The crash of 2026-10-06: the cleanup unbound Create's "Shift modifier", Create asked the
     * keyboard for key -1 from the creative search's worker thread, and the game died on the
     * first letter typed. A mapping whose default is a modifier key is never unbound.
     */
    @Test
    void anotherModsHeldModifierIsNeverUnbound() {
        for (int key : new int[]{LEFT_SHIFT, LEFT_CONTROL, LEFT_ALT}) {
            assertFalse(KeybindRules.mayUnbind("create.keyinfo.shift_modifier", false, List.of(), true, key),
                    "default key " + key);
        }
        assertFalse(KeybindRules.mayUnbind("create.keyinfo.toolmenu", false, List.of(), true, LEFT_ALT),
                "a hold key on Alt is a held modifier too");
    }

    @Test
    void anotherModsActionKeyIsStillUnbound() {
        assertTrue(KeybindRules.mayUnbind("key.ponder.ponder", false, List.of(), true, KEY_W));
        assertTrue(KeybindRules.mayUnbind("simulated.keyinfo.rotate_mode", false, List.of(), true, KEY_TAB));
        assertTrue(KeybindRules.mayUnbind("iris.keybind.shaderPackSelection", false, List.of(), true, KEY_F12));
    }

    /** A mouse button has the same small numbers as nothing in particular: only keyboard keys count. */
    @Test
    void aMouseDefaultIsNotAModifier() {
        assertFalse(KeybindRules.heldModifier(false, LEFT_SHIFT));
        assertTrue(KeybindRules.mayUnbind("some.mod.mouse_action", false, List.of(), false, LEFT_SHIFT));
    }

    @Test
    void whatWasAlwaysKeptIsStillKept() {
        assertFalse(KeybindRules.mayUnbind("key.attack", true, List.of(), false, 0));
        assertFalse(KeybindRules.mayUnbind("key.dragonminez.lock_on", false, List.of(), true, 90));
        assertFalse(KeybindRules.mayUnbind("key.xenopixelsmod.v2_vanish", false, List.of(), true, 66));
        assertFalse(KeybindRules.mayUnbind("key.controlify.radial", false, List.of("key.controlify."), true, KEY_W));
    }

    @Test
    void aMappingWithNoNameIsLeftAlone() {
        assertTrue(KeybindRules.keep(null, false, List.of()));
        assertTrue(KeybindRules.keep("", false, List.of()));
    }

    @Test
    void backupNamesAreOursAndCarryNoPath() {
        String name = KeybindRules.backupFileName("20261005-142233");
        assertEquals("keybinds-20261005-142233.json", name);
        assertTrue(KeybindRules.isBackupFileName(name));
        assertEquals("keybinds-etcpasswd.json", KeybindRules.backupFileName("../../etc/passwd"));
        assertFalse(KeybindRules.isBackupFileName("../keybinds-1.json"));
        assertFalse(KeybindRules.isBackupFileName("keybinds-1.json/../../options.txt"));
        assertFalse(KeybindRules.isBackupFileName("options.txt"));
        assertFalse(KeybindRules.isBackupFileName(null));
    }

    /** R is the dash under every combat controller; a shader reload there rebuilds the chunks on each dash. */
    @Test
    void theShaderReloadNeverStaysOnTheDashKey() {
        assertTrue(KeybindRules.moveShaderReload(false, true, KeybindRules.KEY_R, true));
        assertTrue(KeybindRules.moveShaderReload(false, true, KeybindRules.KEY_R, false),
                "also after a backup that had it on R is restored");
        assertFalse(KeybindRules.moveShaderReload(false, true, KeybindRules.KEY_PAGE_UP, true));
        assertFalse(KeybindRules.moveShaderReload(false, false, KeybindRules.KEY_R, true), "mouse button 82 is not R");
    }

    @Test
    void anUnboundShaderReloadIsGivenAKeyOnceAndThenLeftAlone() {
        assertTrue(KeybindRules.moveShaderReload(true, true, -1, true));
        assertFalse(KeybindRules.moveShaderReload(true, true, -1, false), "unbound again is the player's choice");
    }
}
