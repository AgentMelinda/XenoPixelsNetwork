package net.bullettrain.xenopixelsmod.dmz.form;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DmzFormProtectionTest {
    @Test
    void explicitBundledInventoryIsProtectedWithoutPrefixGuessing() {
        assertTrue(DmzFormProtection.isBundled(DmzFormKind.NORMAL, "saiyan", "xenopixels_fan_ss"));
        assertTrue(DmzFormProtection.isBundled(DmzFormKind.NORMAL, "frostdemon", "xenopixels_dark_frieza"));
        assertFalse(DmzFormProtection.isBundled(DmzFormKind.NORMAL, "saiyan", "xenopixels_user_form"));
        assertFalse(DmzFormProtection.isBundled(DmzFormKind.STACK, "", "xenopixels_fan_ss"));
    }

    @Test
    void existingGroupsNeedEditorOwnershipMetadata() {
        assertTrue(DmzFormProtection.isProtected(DmzFormKind.NORMAL, "human", "native", true, false));
        assertFalse(DmzFormProtection.isProtected(DmzFormKind.NORMAL, "human", "studio", true, true));
        assertFalse(DmzFormProtection.isProtected(DmzFormKind.NORMAL, "human", "new_group", false, false));
    }

    @Test
    void bundledInventoryWinsOverAccidentalEditorMetadata() {
        assertTrue(DmzFormProtection.isProtected(
                DmzFormKind.NORMAL, "saiyan", "xenopixels_gods_forms", true, true));
    }
}
